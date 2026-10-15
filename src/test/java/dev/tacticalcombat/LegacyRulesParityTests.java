package dev.tacticalcombat;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdCharacter.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;
import static dev.tacticalcombat.rules.srd.SrdRules.*;

/** Captured J1 contract before changing any production read path. Expectations are fixed v1 results. */
public final class LegacyRulesParityTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    private static void rejects(Runnable r,String why){try{r.run();}catch(IllegalArgumentException expected){count++;return;}throw new AssertionError(why);}
    public static JsonObject fixture(){
        var sheet=SrdAuthoring.template();var d=SrdAuthoring.data(sheet);
        int[] scores={16,14,8,1,30,9};int i=0;for(var a:Ability.values())d.getAsJsonObject("abilities").addProperty(a.name().toLowerCase(Locale.ROOT),scores[i++]);
        d.addProperty("level",5);d.addProperty("initiative_bonus",-3);d.addProperty("hp_max",37);d.addProperty("hp",19);d.addProperty("temporary_hp",7);
        d.addProperty("attack_bonus",3);d.addProperty("damage_bonus",-1);d.addProperty("ac_bonus",1);
        d.addProperty("arrows",2);d.addProperty("bolts",1);d.addProperty("daggers",5);
        for(String key:List.of("weapons","weapon_proficiencies")){var rows=new JsonArray();for(var w:Weapon.values())rows.add(w.name().toLowerCase(Locale.ROOT));d.add(key,rows);}
        d.add("armor_proficiencies",JsonParser.parseString("[\"light\",\"medium\",\"heavy\"]"));d.addProperty("shield_proficient",true);
        d.add("resistances",JsonParser.parseString("[\"fire\",\"cold\"]"));d.add("immunities",JsonParser.parseString("[\"poison\"]"));d.add("vulnerabilities",JsonParser.parseString("[\"cold\",\"radiant\"]"));return sheet;
    }
    public static List<JsonObject> armorFixtures(){var out=new ArrayList<JsonObject>();for(var armor:Armor.values())for(int dex:new int[]{1,8,14,30})for(boolean shield:List.of(false,true)){var s=fixture();var d=SrdAuthoring.data(s);d.addProperty("armor",armor.name().toLowerCase(Locale.ROOT));d.addProperty("shield",shield);d.getAsJsonObject("abilities").addProperty("dex",dex);out.add(s);}return List.copyOf(out);}
    public static void run(){
        var s=fixture();var c=SrdCharacterCodec.parseSheet(s.toString());int[] scores={16,14,8,1,30,9},mods={3,2,-1,-5,10,-1};int i=0;
        for(var a:Ability.values()){check(c.abilities().get(a)==scores[i],"v1 score "+a);check(c.modifier(a)==mods[i++],"v1 modifier "+a);}
        check(c.proficiency()==3 && c.armorClass()==13 && c.walkingSpeedFeet()==30,"v1 level5 base outputs");
        check(initiative(c,n->10).total()==9,"v1 initiative includes signed typed override");
        check(c.hp()==19&&c.maximumHp()==37&&c.temporaryHp()==7,"v1 HP distinct resources");
        check(c.arrows()==2&&c.bolts()==1&&c.daggers()==5,"v1 virtual stock");
        // Rows correspond to NONE, LEATHER, STUDDED_LEATHER, CHAIN_SHIRT, SCALE_MAIL, CHAIN_MAIL, PLATE.
        // Columns are DEX 1,8,14,30; exclude shield and the explicit +1 override until assertion.
        int[][] ac={{5,9,12,20},{6,10,13,21},{7,11,14,22},{8,12,15,15},{9,13,16,16},{16,16,16,16},{18,18,18,18}};
        for(var row:armorFixtures()){var d=SrdAuthoring.data(row);var v=SrdCharacterCodec.parseSheet(row.toString());int col=List.of(1,8,14,30).indexOf(v.abilities().get(Ability.DEX));check(v.armorClass()==ac[v.armor().ordinal()][col]+1+(v.shield()?2:0),"fixed armor table "+v.armor());check(!v.armorDisadvantage(),"approved armor proficiency");d.add("armor_proficiencies",new JsonArray());d.addProperty("shield_proficient",false);var untrained=SrdCharacterCodec.parseSheet(row.toString());check(untrained.armorClass()==v.armorClass(),"proficiency does not alter AC");check(untrained.armorDisadvantage()==(v.armor()!=Armor.NONE||v.shield()),"v1 nonproficiency consequence");}
        // Weapon order fixed by v1 catalog; STR for melee/thrown non-finesse, DEX for ranged/finesse below.
        int[][] weapon={{0,0,3},{1,4,1},{1,8,2},{1,6,1},{2,6,2},{1,6,1},{1,8,1},{1,8,1}};
        for(var w:Weapon.values()){boolean dex=w.ranged||w.has(Property.FINESSE);var p=plan(c,w,w.ranged?AttackMode.RANGED:AttackMode.MELEE,dex?Ability.DEX:Ability.STR,w.has(Property.TWO_HANDED));int[] expected=weapon[w.ordinal()];check(p.attackModifier()==(dex?8:9),"fixed attack modifier "+w);check(p.dice()==expected[0]&&p.sides()==expected[1]&&p.flatDamage()==expected[2],"fixed weapon damage "+w);check(!p.disadvantage(),"normal approved weapon");}
        var untrained=fixture();SrdAuthoring.data(untrained).add("weapon_proficiencies",new JsonArray());var u=SrdCharacterCodec.parseSheet(untrained.toString());check(plan(u,Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false).attackModifier()==6,"no weapon proficiency");check(plan(u,Weapon.UNARMED,AttackMode.MELEE,Ability.STR,false).attackModifier()==9,"unarmed proficiency retained");
        check(plan(c,Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,true).sides()==10,"versatile v1 die");check(plan(c,Weapon.DAGGER,AttackMode.THROWN,Ability.STR,false).flatDamage()==2,"finesse permits STR thrown");
        var weak=fixture();var wd=SrdAuthoring.data(weak);wd.getAsJsonObject("abilities").addProperty("str",12);wd.addProperty("armor","chain_mail");wd.addProperty("speed_feet",29);check(SrdCharacterCodec.parseSheet(weak.toString()).walkingSpeedFeet()==19,"legacy heavy armor penalty");check(SrdCharacterCodec.parseSheet(weak.toString()).walkingSquares()==3,"whole squares");
        var damage=List.of(new Damage(DamageType.FIRE,9),new Damage(DamageType.POISON,20),new Damage(DamageType.COLD,9),new Damage(DamageType.RADIANT,3));var defended=defend(damage,c);check(defended.stream().mapToInt(Damage::amount).sum()==18,"static resistance immunity vulnerability rounding");check(SrdHealth.of(c).damage(damage,c).equals(new SrdHealth(8,37,0)),"temp HP absorbs before HP");
        var absent=fixture();var ad=SrdAuthoring.data(absent);for(String key:List.of("arrows","bolts","daggers"))ad.remove(key);var legacy=SrdCharacterCodec.parseSheet(absent.toString());check(legacy.arrows()==0&&legacy.bolts()==0&&legacy.daggers()==1,"legacy optional stock defaults");ad.add("weapons",new JsonArray());check(SrdCharacterCodec.parseSheet(absent.toString()).daggers()==0,"no implicit dagger without availability");
        rejects(()->plan(SrdCharacterCodec.parseSheet(absent.toString()),Weapon.DAGGER,AttackMode.MELEE,Ability.STR,false),"unavailable dagger");
        var shield=fixture();SrdAuthoring.data(shield).addProperty("shield",true);rejects(()->plan(SrdCharacterCodec.parseSheet(shield.toString()),Weapon.GREATSWORD,AttackMode.MELEE,Ability.STR,true),"shield hand conflict");
        var invalid=fixture();SrdAuthoring.data(invalid).addProperty("version",2);rejects(()->SrdCharacterCodec.parseSheet(invalid.toString()),"unknown combat version");
        System.out.println("J1 frozen legacy parity checks passed: "+count);
    }
}
