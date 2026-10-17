package dev.tacticalcombat.rules.srd;

import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;
import static dev.tacticalcombat.rules.srd.SrdCharacter.*;

/** Shared typed form model. Client validation is advisory; the server repeats the codec validation. */
public final class SrdAuthoring {
    private SrdAuthoring() {}
    public record Field(String key,String label,List<String> choices,boolean multiple) {
        public Field { choices=List.copyOf(choices); }
    }
    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(v->v.name().toLowerCase(Locale.ROOT)).toList();
    }
    public static List<Field> fields() {
        var fields=new ArrayList<Field>();
        fields.add(new Field("name","Character name",List.of(),false));
        for(Ability a:Ability.values()) fields.add(new Field("abilities."+a.name().toLowerCase(Locale.ROOT),a.name()+" score",List.of(),false));
        for(String key:List.of("level","initiative_bonus","hp_max","hp","temporary_hp","speed_feet","reach_feet","ac_bonus","attack_bonus","damage_bonus"))
            fields.add(new Field(key,key.replace('_',' '),List.of(),false));
        for(String key:List.of("arrows","bolts","daggers")) fields.add(new Field(key,"Virtual "+key+" remaining",List.of(),false));
        fields.add(new Field("size","Creature size",names(Size.values()),false));
        fields.add(new Field("armor","Armor",names(Armor.values()),false));
        fields.add(new Field("shield","Shield equipped",List.of("false","true"),false));
        fields.add(new Field("shield_proficient","Shield proficiency",List.of("false","true"),false));
        fields.add(new Field("armor_proficiencies","Armor proficiencies",names(ArmorKind.values()),true));
        fields.add(new Field("weapons","Available weapons",names(java.util.Arrays.stream(Weapon.values()).filter(w->w!=Weapon.SLAM).toArray(Weapon[]::new)),true));
        fields.add(new Field("weapon_proficiencies","Weapon proficiencies",names(java.util.Arrays.stream(Weapon.values()).filter(w->w!=Weapon.SLAM).toArray(Weapon[]::new)),true));
        for(String key:List.of("resistances","immunities","vulnerabilities")) fields.add(new Field(key,key,names(DamageType.values()),true));
        return List.copyOf(fields);
    }
    public static JsonObject template() {
        var sheet=new JsonObject(); sheet.addProperty("format","generic_d20");
        var text=new JsonObject(); text.addProperty("name","New SRD character"); sheet.add("text",text);
        var data=new JsonObject(); data.addProperty("version",1);
        var abilities=new JsonObject(); for(Ability ability:Ability.values()) abilities.addProperty(ability.name().toLowerCase(Locale.ROOT),10);
        data.add("abilities",abilities);
        for(var entry:Map.of("level",1,"hp",10,"hp_max",10,"temporary_hp",0,"speed_feet",30,"reach_feet",5,
                "initiative_bonus",0,"ac_bonus",0,"attack_bonus",0,"damage_bonus",0).entrySet()) data.addProperty(entry.getKey(),entry.getValue());
        data.addProperty("size","medium"); data.addProperty("armor","none");
        data.addProperty("shield",false); data.addProperty("shield_proficient",false);
        for(String key:List.of("armor_proficiencies","weapons","weapon_proficiencies","resistances","immunities","vulnerabilities")) data.add(key,new JsonArray());
        var collection=new JsonArray(); collection.add(data); var collections=new JsonObject(); collections.add("srd_combat",collection); sheet.add("collections",collections);
        return sheet;
    }
    public static JsonObject data(JsonObject sheet) {
        if(!sheet.has("collections") || !sheet.getAsJsonObject("collections").has("srd_combat"))
            throw new IllegalArgumentException("Missing SRD data; explicitly initialize the SRD form first");
        var array=sheet.getAsJsonObject("collections").getAsJsonArray("srd_combat");
        if(array.size()!=1 || !array.get(0).isJsonObject()) throw new IllegalArgumentException("SRD collection must contain one object");
        return array.get(0).getAsJsonObject();
    }
    public static String value(JsonObject sheet,Field field) {
        if(field.key.equals("name")) return sheet.has("text") && sheet.getAsJsonObject("text").has("name")?sheet.getAsJsonObject("text").get("name").getAsString():"";
        JsonObject data=data(sheet); String key=field.key;
        JsonElement result=key.startsWith("abilities.")?data.getAsJsonObject("abilities").get(key.substring(10)):data.get(key);
        if(result==null && Set.of("arrows","bolts","daggers").contains(key)) {
            var stats=SrdCharacterCodec.parseSheet(sheet.toString());
            return Integer.toString(key.equals("arrows")?stats.arrows():key.equals("bolts")?stats.bolts():stats.daggers());
        }
        if(result==null) return "";
        if(field.multiple) {
            var values=new ArrayList<String>(); for(JsonElement e:result.getAsJsonArray()) values.add(e.getAsString()); return String.join(",",values);
        }
        return result.getAsString();
    }
    /** Stages one typed field without requiring the whole draft to be complete until Save. */
    public static JsonObject change(JsonObject original,Field field,String input) {
        if(!fields().contains(field)) throw new IllegalArgumentException("Unknown SRD form field");
        JsonObject sheet=original.deepCopy(); JsonElement value;
        if(field.key.equals("name")) {
            if(input.isBlank() || input.length()>80) throw new IllegalArgumentException("Name must be 1–80 characters");
            if(!sheet.has("text")) sheet.add("text",new JsonObject()); sheet.getAsJsonObject("text").addProperty("name",input); return sheet;
        }
        if(field.multiple) {
            var array=new JsonArray(); var unique=new HashSet<String>();
            if(!input.isBlank()) for(String part:input.split(",",-1)) {
                String option=part.trim();
                if(!field.choices.contains(option) || !unique.add(option)) throw new IllegalArgumentException("Invalid/duplicate choice: "+option);
                array.add(option);
            }
            value=array;
        } else if(!field.choices.isEmpty()) {
            if(!field.choices.contains(input)) throw new IllegalArgumentException("Choose a catalog value");
            value=field.key.equals("shield") || field.key.equals("shield_proficient")?new JsonPrimitive(Boolean.parseBoolean(input)):new JsonPrimitive(input);
        } else {
            try { value=new JsonPrimitive(Integer.parseInt(input)); }
            catch(NumberFormatException e) { throw new IllegalArgumentException("Enter a whole number, not a formula"); }
        }
        JsonObject data=data(sheet);
        if(field.key.startsWith("abilities.")) data.getAsJsonObject("abilities").add(field.key.substring(10),value);
        else data.add(field.key,value);
        return sheet;
    }
}
