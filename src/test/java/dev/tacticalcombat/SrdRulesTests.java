package dev.tacticalcombat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdCharacter.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;
import static dev.tacticalcombat.rules.srd.SrdRules.*;

/** Executable SRD examples, deterministic dice, real repository permissions and persistence. */
public final class SrdRulesTests {
    private static int checks;
    private static String fixture;
    private static void check(boolean condition,String why) { checks++; if(!condition) throw new AssertionError(why); }
    @FunctionalInterface private interface Task { void run() throws Exception; }
    private static void rejects(Task operation,String why) throws Exception {
        try { operation.run(); } catch(IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(why);
    }
    private static JsonObject sheet() { return JsonParser.parseString(fixture).getAsJsonObject(); }
    private static JsonObject data(JsonObject sheet) { return sheet.getAsJsonObject("collections").getAsJsonArray("srd_combat").get(0).getAsJsonObject(); }
    private static SrdCharacter character(String key,String jsonValue) {
        var sheet=sheet(); data(sheet).add(key,JsonParser.parseString(jsonValue));
        return SrdCharacterCodec.parseSheet(sheet.toString());
    }
    private static final class FixedDice implements Dice {
        private final int[] faces; private int position;
        FixedDice(int... faces) { this.faces=faces; }
        public int roll(int sides) {
            if(position>=faces.length) throw new AssertionError("Unexpected extra die (d"+sides+")");
            return faces[position++];
        }
        void exhausted() { check(position==faces.length,"exact number of dice consumed"); }
    }
    public static void run() throws Exception {
        fixture=Files.readString(Path.of("src/gametest/resources/srd-test-character.json"));
        SrdCharacter c=SrdCharacterCodec.parseSheet(fixture);
        var policy=(SrdRulesProfile)RulesProfiles.get(RulesProfiles.SRD);
        check(policy.version()==1 && !policy.worldCombat(),"versioned typed SRD policy remains gated");
        check(policy.movementSquares(c)==6 && policy.initiative(c,new FixedDice(10)).total()==12,"typed policy calculates from explicit SRD data");
        rejects(()->policy.movement(Map.of("movement_blocks",30d),true),"SRD cannot use generic movement stats");
        rejects(()->policy.initiative(20,Map.of("initiative_bonus",100d)),"SRD cannot use generic initiative stats");
        for(int score=1;score<=30;score++) check(abilityModifier(score)==(int)Math.floor((score-10)/2d),"ability table "+score);
        for(int level=1;level<=20;level++) check(proficiency(level)==(level<=4?2:level<=8?3:level<=12?4:level<=16?5:6),"level table "+level);
        rejects(()->abilityModifier(0),"no score zero"); rejects(()->abilityModifier(31),"score cap");
        rejects(()->proficiency(0),"no level zero"); rejects(()->proficiency(21),"level cap");
        check(c.armorClass()==13 && c.proficiency()==2,"leather plus Dex; proficiency derived");
        check(character("shield","true").armorClass()==15,"one shield adds two once");
        check(character("ac_bonus","-1").armorClass()==12,"signed explicit AC bonus");
        for(int dex=-5;dex<=10;dex++) {
            check(Armor.NONE.ac(dex)==10+dex,"unarmored AC");
            check(Armor.LEATHER.ac(dex)==11+dex,"light AC");
            check(Armor.CHAIN_SHIRT.ac(dex)==13+Math.min(2,dex),"medium AC retains negative Dex");
            check(Armor.PLATE.ac(dex)==18,"heavy AC ignores Dex, including negatives");
        }
        check(c.walkingSquares()==6 && character("speed_feet","25").walkingSquares()==5 && character("speed_feet","40").walkingSquares()==8,"feet are distinct from blocks");
        var weak=sheet(); data(weak).addProperty("armor","chain_mail"); data(weak).getAsJsonObject("abilities").addProperty("str",12);
        check(SrdCharacterCodec.parseSheet(weak.toString()).walkingSpeedFeet()==20,"heavy armor Strength penalty");
        check(character("speed_feet","29").walkingSquares()==5,"only complete five-foot squares");
        check(initiative(c,new FixedDice(1)).total()==3,"initiative natural one is a check");
        check(initiative(c,new FixedDice(20)).total()==22,"initiative natural twenty is a check");
        check(initiative(character("initiative_bonus","-5"),new FixedDice(1)).total()==-2,"negative initiative allowed");
        check(initiative(character("initiative_bonus","7"),new FixedDice(12)).total()==21,"positive initiative bonus");
        var a=UUID.randomUUID(); var b=UUID.randomUUID(); var d=UUID.randomUUID();
        var groups=initiativeGroups(Map.of(a,15,b,15,d,3));
        check(groups.size()==2 && Set.copyOf(groups.get(0)).equals(Set.of(a,b)) && groups.get(1).equals(List.of(d)),"ties retained for adjudication");
        check(initiativeGroups(Map.of(d,30)).equals(List.of(List.of(d))) && groups.get(0).size()==2,"independent initiative calculations");
        for(boolean advantage:List.of(false,true)) for(boolean disadvantage:List.of(false,true)) {
            var mode=rollMode(advantage,disadvantage);
            var dice=new FixedDice(advantage==disadvantage?new int[]{8}:new int[]{8,19});
            var roll=d20(dice,3,mode);
            check(roll.total()==(advantage && !disadvantage?22:11),"advantage cancellation and selection"); dice.exhausted();
        }
        rejects(()->d20(new FixedDice(0),0,RollMode.NORMAL),"bad die face rejected");
        rejects(()->d20(new FixedDice(21),0,RollMode.NORMAL),"oversized die face rejected");
        var sword=plan(c,Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false);
        check(sword.attackModifier()==5 && sword.flatDamage()==3 && sword.dice()==1 && sword.sides()==8,"longsword formula");
        check(sword.minimumDamage()==4 && sword.maximumDamage()==11,"preview damage bounds");
        check(plan(c,Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,true).sides()==10,"versatile die");
        check(plan(c,Weapon.DAGGER,AttackMode.THROWN,Ability.DEX,false).attackModifier()==4,"thrown finesse Dex");
        check(plan(c,Weapon.DAGGER,AttackMode.THROWN,Ability.STR,false).attackModifier()==5,"thrown finesse Strength");
        check(plan(c,Weapon.SHORTSWORD,AttackMode.MELEE,Ability.DEX,false).flatDamage()==2,"same finesse modifier for damage");
        check(plan(c,Weapon.SHORTBOW,AttackMode.RANGED,Ability.DEX,true).normalRangeFeet()==80,"shortbow ranges");
        check(plan(c,Weapon.LONGBOW,AttackMode.RANGED,Ability.DEX,true).longRangeFeet()==600,"longbow ranges");
        check(Weapon.LIGHT_CROSSBOW.has(Property.LOADING),"loading catalog metadata");
        check(plan(character("weapon_proficiencies","[]"),Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false).attackModifier()==3,"nonproficient weapon no proficiency");
        check(plan(character("weapon_proficiencies","[]"),Weapon.UNARMED,AttackMode.MELEE,Ability.STR,false).attackModifier()==5,"unarmed always proficient");
        check(plan(character("size","\"small\""),Weapon.GREATSWORD,AttackMode.MELEE,Ability.STR,true).disadvantage(),"2014 heavy Small penalty");
        check(plan(character("armor_proficiencies","[]"),Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false).disadvantage(),"armor nonproficiency attack penalty");
        var shield=sheet(); data(shield).addProperty("shield",true); data(shield).addProperty("shield_proficient",false);
        check(plan(SrdCharacterCodec.parseSheet(shield.toString()),Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false).disadvantage(),"shield nonproficiency attack penalty");
        rejects(()->plan(c,Weapon.LONGSWORD,AttackMode.MELEE,Ability.DEX,false),"nonfinesse cannot choose Dex");
        rejects(()->plan(c,Weapon.LONGBOW,AttackMode.RANGED,Ability.STR,true),"ranged requires Dex");
        rejects(()->plan(c,Weapon.LONGSWORD,AttackMode.THROWN,Ability.STR,false),"improvised throws unsupported");
        rejects(()->plan(c,Weapon.SHORTBOW,AttackMode.MELEE,Ability.DEX,true),"improvised melee unsupported");
        rejects(()->plan(c,Weapon.GREATSWORD,AttackMode.MELEE,Ability.STR,false),"two hands required");
        rejects(()->plan(character("shield","true"),Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,true),"shield hand cannot double count");
        rejects(()->plan(character("weapons","[]"),Weapon.DAGGER,AttackMode.MELEE,Ability.STR,false),"unavailable weapon");
        rejects(()->plan(character("hp","0"),Weapon.UNARMED,AttackMode.MELEE,Ability.STR,false),"zero HP test policy");
        for(int face=1;face<=20;face++) {
            FixedDice dice=new FixedDice(face==20?new int[]{20,4,6}:face>=10?new int[]{face,4}:new int[]{face});
            var attack=attack(c,Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false,15,false,false,dice);
            check(attack.hit()==(face>=10),"attack vs AC equality table");
            check(attack.critical()==(face==20),"critical only on selected 20");
            check(attack.damage().amount()==(face==20?13:face>=10?7:0),"critical doubles dice not flat"); dice.exhausted();
        }
        var naturalOne=attack(c,Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false,0,false,false,new FixedDice(1));
        check(!naturalOne.hit(),"natural one misses even AC zero");
        check(attack(c,Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false,100,false,false,new FixedDice(20,1,1)).damage().amount()==5,"natural twenty hits any supported AC");
        check(attack(c,Weapon.GREATSWORD,AttackMode.MELEE,Ability.STR,true,15,false,false,new FixedDice(20,1,2,3,4)).damage().amount()==13,"2d6 critical becomes 4d6");
        var unarmedDice=new FixedDice(20);
        check(attack(c,Weapon.UNARMED,AttackMode.MELEE,Ability.STR,false,15,false,false,unarmedDice).damage().amount()==4,"unarmed critical does not double flat damage"); unarmedDice.exhausted();
        check(attack(c,Weapon.DAGGER,AttackMode.MELEE,Ability.DEX,false,15,true,false,new FixedDice(1,20,2,3)).damage().amount()==7,"advantage chooses natural twenty");
        check(!attack(c,Weapon.DAGGER,AttackMode.MELEE,Ability.DEX,false,15,false,true,new FixedDice(20,1)).hit(),"disadvantage chooses natural one");
        check(attack(character("damage_bonus","-100"),Weapon.LONGSWORD,AttackMode.MELEE,Ability.STR,false,15,false,false,new FixedDice(15,1)).damage().amount()==0,"damage cannot become healing");
        check(defend(List.of(new Damage(DamageType.FIRE,7)),character("resistances","[\"fire\"]")).get(0).amount()==3,"resistance rounds down");
        check(defend(List.of(new Damage(DamageType.FIRE,7)),character("vulnerabilities","[\"fire\"]")).get(0).amount()==14,"vulnerability doubles");
        check(defend(List.of(new Damage(DamageType.FIRE,7)),character("immunities","[\"fire\"]")).get(0).amount()==0,"immunity");
        var both=sheet(); data(both).add("resistances",JsonParser.parseString("[\"fire\"]")); data(both).add("vulnerabilities",JsonParser.parseString("[\"fire\"]"));
        check(defend(List.of(new Damage(DamageType.FIRE,7)),SrdCharacterCodec.parseSheet(both.toString())).get(0).amount()==6,"resistance before vulnerability, not cancellation");
        check(defend(List.of(new Damage(DamageType.FIRE,3),new Damage(DamageType.FIRE,3)),character("resistances","[\"fire\"]")).get(0).amount()==3,"aggregate same type before rounding");
        var mixed=defend(List.of(new Damage(DamageType.FIRE,7),new Damage(DamageType.COLD,9)),character("resistances","[\"fire\"]"));
        check(mixed.stream().mapToInt(Damage::amount).sum()==12,"different types retain independent defenses");
        validateMalformedInputs(); repositoryChecks();
        System.out.println("SRD core rules checks passed: "+checks);
    }
    private static void validateMalformedInputs() throws Exception {
        for(String field:List.copyOf(data(sheet()).keySet())) {
            var value=sheet(); data(value).remove(field);
            rejects(()->SrdCharacterCodec.parseSheet(value.toString()),"missing required field "+field);
        }
        for(String field:List.of("approved","ac","proficiency","dex_modifier","formula")) {
            var value=sheet(); data(value).addProperty(field,100);
            rejects(()->SrdCharacterCodec.parseSheet(value.toString()),"forged derived/unknown field "+field);
        }
        for(String value:List.of("1.5","1e100","\"1\"","null","true","{}","[]"))
            rejects(()->character("level",value),"strict numeric type "+value);
        rejects(()->character("version","2"),"future version rejected");
        rejects(()->character("hp","21"),"HP cannot exceed maximum");
        rejects(()->character("hp","-1"),"negative HP rejected");
        rejects(()->character("speed_feet","301"),"speed bounded");
        rejects(()->character("shield","\"true\""),"strict boolean");
        rejects(()->character("armor","\"diamond\""),"no implicit Minecraft armor mapping");
        rejects(()->character("resistances","[\"fire\",\"fire\"]"),"no duplicate defense stacking");
        rejects(()->character("immunities","[\"nonmagical\"]"),"conditional defenses not silently interpreted");
        rejects(()->character("damage_bonus","\"str_mod+2\""),"no formula execution");
        rejects(()->SrdCharacterCodec.parseSheet("{\"format\":\"generic_d20\",\"values\":{\"dex\":18}}"),"no legacy value reinterpretation");
        var multiple=sheet(); multiple.getAsJsonObject("collections").getAsJsonArray("srd_combat").add(data(sheet()));
        rejects(()->SrdCharacterCodec.parseSheet(multiple.toString()),"exactly one stat block");
    }
    private static void repositoryChecks() throws Exception {
        Path dir=Files.createTempDirectory("cubetable-srd-core-");
        try {
            var repo=new CharacterRepository(dir.resolve("state.json"));
            var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);
            var owner=new CharacterRepository.Principal(UUID.randomUUID(),false);
            UUID campaign=repo.createCampaign(dm,"SRD tests"); repo.invite(dm,campaign,1,owner.id(),false);
            UUID id=repo.submit(owner,campaign,RulesProfiles.SRD,fixture), actor=UUID.randomUUID();
            rejects(()->repo.bind(dm,id,1,actor,null),"unapproved SRD cannot bind");
            rejects(()->repo.approve(owner,id,1),"owner cannot self-approve SRD");
            rejects(()->repo.srdSnapshot(actor,campaign),"unbound SRD cannot get combat stats");
            repo.approve(dm,id,1); repo.bind(dm,id,2,actor,null);
            var snapshot=repo.srdSnapshot(actor,campaign);
            check(snapshot.stats().hp()==12 && snapshot.stats().armorClass()==13,"approved bound explicit statistics");
            check(snapshot.character().equals(id) && snapshot.revision()==2,"snapshot preserves identity and revision");
            rejects(()->repo.srdSnapshot(actor,UUID.randomUUID()),"cross-campaign combat input rejected");
            rejects(()->repo.visible(new CharacterRepository.Principal(UUID.randomUUID(),false),id),"no private SRD sheet inspection");
            rejects(()->repo.stat(owner,id,2,"dex",30),"bound stats cannot be changed");
            repo.profile(dm,campaign,2,RulesProfiles.MANUAL);
            check(repo.srdSnapshot(actor,campaign).stats().equals(snapshot.stats()),"campaign default does not convert character");
            check(new CharacterRepository(dir.resolve("state.json")).srdSnapshot(actor,campaign).equals(snapshot),"SRD identity and explicit inputs survive reload");
            repo.unbind(dm,actor);
            var changes=new JsonObject(); var revised=sheet(); data(revised).addProperty("hp",11);
            changes.add("collections.srd_combat",revised.getAsJsonObject("collections").get("srd_combat"));
            repo.edit(owner,id,2,0,changes);
            check(!repo.character(id).approved(),"SRD field changes invalidate approval");
            rejects(()->repo.edit(owner,id,2,0,changes),"SRD stale edit rejected");
            UUID tactical=repo.submit(dm,campaign,RulesProfiles.TACTICAL,fixture);
            repo.approve(dm,tactical,1); repo.bind(dm,tactical,2,actor,null);
            rejects(()->repo.srdSnapshot(actor,campaign),"SRD-shaped data in Tactical is not SRD authority");
            check(!RulesProfiles.get(RulesProfiles.SRD).worldCombat(),"calculation foundation does not enable unsafe world combat");
        } finally {
            try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
}
