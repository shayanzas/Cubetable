package dev.tacticalcombat.rules.srd;

import com.google.gson.*;
import java.util.*;
import dev.tacticalcombat.character.CharacterRepository;
import static dev.tacticalcombat.rules.srd.SrdCharacter.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;

/** Explicit opt-in collection; old Generic D20 numbers are never reinterpreted as SRD statistics. */
public final class SrdCharacterCodec {
    private SrdCharacterCodec() {}
    public static final int VERSION=1;
    private static final Set<String> FIELDS=Set.of("version","abilities","level","initiative_bonus","hp","hp_max",
            "temporary_hp","speed_feet","size","reach_feet","armor","shield","armor_proficiencies","shield_proficient",
            "ac_bonus","weapons","weapon_proficiencies","attack_bonus","damage_bonus","resistances","immunities","vulnerabilities");
    private static final Set<String> RESOURCES=Set.of("arrows","bolts","daggers");
    /** Structural validation only, NOT approval or permission. Use the repository adapter for combat inputs. */
    public static SrdCharacter parseSheet(String sheet) {
        CharacterRepository.validateSheet(sheet);
        JsonObject root=JsonParser.parseString(sheet).getAsJsonObject();
        MonsterProfiles.validate(root);
        require(root.has("collections") && root.getAsJsonObject("collections").has("srd_combat"),
                "Missing collections.srd_combat; explicit SRD statistics are required");
        JsonArray entries=root.getAsJsonObject("collections").getAsJsonArray("srd_combat");
        require(entries.size()==1 && entries.get(0).isJsonObject(),"srd_combat must contain exactly one versioned object");
        JsonObject o=entries.get(0).getAsJsonObject();
        require(o.keySet().containsAll(FIELDS) && o.keySet().stream().allMatch(key->FIELDS.contains(key)||RESOURCES.contains(key)),
                "Missing or unsupported srd_combat fields; derived values and formulas are not accepted");
        require(number(o,"version")==VERSION,"Unsupported srd_combat version");
        require(o.get("abilities").isJsonObject(),"SRD abilities must be an object");
        JsonObject scores=o.getAsJsonObject("abilities");
        require(scores.keySet().equals(Set.of("str","dex","con","int","wis","cha")),"Exactly six SRD abilities are required");
        var abilities=new EnumMap<Ability,Integer>(Ability.class);
        for(Ability a:Ability.values()) abilities.put(a,number(scores,a.name().toLowerCase(Locale.ROOT)));
        return new SrdCharacter(abilities,number(o,"level"),number(o,"initiative_bonus"),number(o,"hp"),number(o,"hp_max"),
                number(o,"temporary_hp"),number(o,"speed_feet"),value(Size.class,o.get("size")),number(o,"reach_feet"),
                value(Armor.class,o.get("armor")),bool(o,"shield"),values(ArmorKind.class,o,"armor_proficiencies"),bool(o,"shield_proficient"),
                number(o,"ac_bonus"),values(Weapon.class,o,"weapons"),values(Weapon.class,o,"weapon_proficiencies"),
                number(o,"attack_bonus"),number(o,"damage_bonus"),values(DamageType.class,o,"resistances"),
                values(DamageType.class,o,"immunities"),values(DamageType.class,o,"vulnerabilities"),
                o.has("arrows")?number(o,"arrows"):0,o.has("bolts")?number(o,"bolts"):0,
                o.has("daggers")?number(o,"daggers"):values(Weapon.class,o,"weapons").contains(Weapon.DAGGER)?1:0);
    }
    private static int number(JsonObject o,String key) {
        JsonElement value=o.get(key);
        require(value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),"SRD "+key+" must be an integer");
        try { return value.getAsBigDecimal().intValueExact(); }
        catch(ArithmeticException|NumberFormatException e) { throw new IllegalArgumentException("SRD "+key+" must be a bounded integer",e); }
    }
    private static boolean bool(JsonObject o,String key) {
        JsonElement value=o.get(key);
        require(value!=null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(),"SRD "+key+" must be a boolean");
        return value.getAsBoolean();
    }
    private static <T extends Enum<T>> T value(Class<T> type,JsonElement element) {
        require(element!=null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString(),"SRD "+type.getSimpleName()+" must be a catalog name");
        String text=element.getAsString();
        require(text.matches("[a-z_]{1,32}"),"Invalid SRD catalog name");
        try { return Enum.valueOf(type,text.toUpperCase(Locale.ROOT)); }
        catch(IllegalArgumentException e) { throw new IllegalArgumentException("Unsupported SRD "+type.getSimpleName()+": "+text,e); }
    }
    private static <T extends Enum<T>> Set<T> values(Class<T> type,JsonObject o,String key) {
        require(o.get(key).isJsonArray(),"SRD "+key+" must be an array");
        JsonArray array=o.getAsJsonArray(key);
        require(array.size()<=type.getEnumConstants().length,"SRD "+key+" exceeds catalog limit");
        var result=EnumSet.noneOf(type);
        for(JsonElement e:array) require(result.add(value(type,e)),"Duplicate SRD "+key+" entry");
        return Set.copyOf(result);
    }
}
