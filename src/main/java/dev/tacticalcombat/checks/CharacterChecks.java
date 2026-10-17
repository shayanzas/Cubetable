package dev.tacticalcombat.checks;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.*;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Ordinary SRD checks only. Recorded grants are never merged into executable choices. */
public final class CharacterChecks {
    private CharacterChecks() {}
    public enum Category { ABILITY, SKILL, SAVE }
    public enum Mode { NORMAL, ADVANTAGE, DISADVANTAGE }
    public record Key(Category category,String id) {
        public Key {Objects.requireNonNull(category);require(category==Category.SKILL?SKILLS.containsKey(id):CreationRules.ABILITIES.contains(id),"Unknown check identifier");}
        public String ability(){return category==Category.SKILL?SKILLS.get(id):id;}
    }
    public static final Map<String,String> SKILLS;
    static {var m=new LinkedHashMap<String,String>();String[] names={"Acrobatics","Animal Handling","Arcana","Athletics","Deception","History","Insight","Intimidation","Investigation","Medicine","Nature","Perception","Performance","Persuasion","Religion","Sleight of Hand","Stealth","Survival"};String[] abilities={"dex","wis","int","str","cha","int","wis","cha","int","wis","int","wis","cha","cha","int","dex","dex","wis"};for(int i=0;i<names.length;i++)m.put(names[i],abilities[i]);SKILLS=Collections.unmodifiableMap(m);}
    public record Modifier(String ability,int abilityModifier,int proficiencyBonus,int multiplier,int total,String source) {}
    public static List<Key> keys(){var out=new ArrayList<Key>();for(String id:CreationRules.ABILITIES)out.add(new Key(Category.ABILITY,id));for(String id:SKILLS.keySet())out.add(new Key(Category.SKILL,id));for(String id:CreationRules.ABILITIES)out.add(new Key(Category.SAVE,id));return List.copyOf(out);}
    public static Modifier resolve(String raw,Key key) {
        var rules=CharacterRules.read(raw);var sheet=JsonParser.parseString(raw).getAsJsonObject();
        int base=rules.modifiers().get(SrdCharacter.Ability.valueOf(key.ability().toUpperCase(Locale.ROOT))),multiple=0;
        var monster=dev.tacticalcombat.rules.srd.MonsterProfiles.identity(sheet);
        if(monster!=null){String source=monster.get("source").getAsString();int bonus=key.category()==Category.SAVE&&key.id().equals("wis")&&source.endsWith(":zombie")?2:key.category()==Category.SKILL&&key.id().equals("Stealth")&&source.endsWith(":goblin")?4:0;
            return new Modifier(key.ability(),base,2,bonus/2,base+bonus,"Reviewed monster adapter v1: "+source);}
        if(key.category()!=Category.ABILITY){
            require(CreationRules.enrolled(sheet),"Historical/manual proficiency provenance unavailable; use an ordinary ability or Manual Roll");
            String clazz=CreationRules.clazz(sheet);
            require(!CreationRules.skills(clazz).isEmpty(),"Custom class checks require manual adjudication");
            require(CreationRules.level(sheet)==rules.inputs().level(),"Recorded and effective levels differ; review before rolling skills or saves");
            require(SheetSections.raw(sheet,"progression").getAsJsonArray("classes").isEmpty(),"Recorded multiclass progression is not executable");
            require(CreationRules.choiceErrors(sheet,true).isEmpty(),"Complete valid guided class choices before rolling skills or saves");
            var p=SheetSections.raw(sheet,"proficiencies");
            // Any manual adjustment is ambiguous (custom targets have no executable grammar).
            require(p.getAsJsonArray("modifiers").isEmpty(),"Recorded check adjustments require manual adjudication");
            String group=key.category()==Category.SAVE?"saves":"skills";
            for(var e:p.getAsJsonArray(group))require(!CreationRules.grant(group,e.getAsString()).equalsIgnoreCase(key.id()),"Historical proficiency for this check requires manual adjudication");
            if(key.category()==Category.SKILL)for(var e:p.getAsJsonArray("expertise"))require(!CreationRules.grant("expertise",e.getAsString()).equalsIgnoreCase(key.id()),"Historical Expertise for this check requires manual adjudication");
            var choices=CreationRules.choices(sheet);
            if(key.category()==Category.SAVE)multiple=CreationRules.saves(clazz).contains(key.id())?1:0;
            else {multiple=choices.skills().contains(key.id())?1:0;if(choices.expertise().contains(key.id())){require(multiple==1,"Expertise requires verified class skill proficiency");multiple=2;}}
        }
        return new Modifier(key.ability(),base,rules.proficiency(),multiple,base+multiple*rules.proficiency(),multiple==0?"J1 effective ability; no proficiency applied":"Validated guided class choices; J1 effective level; proficiency applied once");
    }
}
