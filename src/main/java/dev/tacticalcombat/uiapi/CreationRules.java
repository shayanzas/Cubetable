package dev.tacticalcombat.uiapi;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** SRD 5.1 (2014) starting choices. Inert sheet grants, never combat inputs. */
public final class CreationRules {
    private CreationRules() {}
    public static final List<String> ABILITIES=List.of("str","dex","con","int","wis","cha");
    public static final Set<String> SKILLS=Set.of("Acrobatics","Animal Handling","Arcana","Athletics","Deception","History","Insight","Intimidation","Investigation","Medicine","Nature","Perception","Performance","Persuasion","Religion","Sleight of Hand","Stealth","Survival");
    public static final List<Integer> ARRAY=List.of(15,14,13,12,10,8);
    private static final Map<Integer,Integer> COST=Map.of(8,0,9,1,10,2,11,3,12,4,13,5,14,7,15,9);
    public record Choices(List<String> skills,List<String> expertise,String abilityMethod) {
        public Choices {require(skills!=null&&skills.size()<=18&&expertise!=null&&expertise.size()<=8,"Too many class choices");skills=List.copyOf(skills);expertise=List.copyOf(expertise);require(new HashSet<>(skills).size()==skills.size()&&new HashSet<>(expertise).size()==expertise.size(),"Duplicate class grant");require(Set.of("STANDARD_ARRAY","POINT_BUY","MANUAL","EXTERNAL_ROLLS").contains(abilityMethod),"Unknown ability method");for(String s:skills)require(SKILLS.contains(s),"Unknown class skill");for(String s:expertise)require(SKILLS.contains(s)||s.equals("Thieves' tools"),"Unknown expertise choice");}
    }
    public static int skillCount(String name){return switch(canonicalClass(name)){case "Bard","Ranger"->3;case "Rogue"->4;default->2;};}
    public static Set<String> skills(String name){String skills=switch(canonicalClass(name)){
        case "Barbarian"->"Animal Handling,Athletics,Intimidation,Nature,Perception,Survival";
        case "Bard"->String.join(",",SKILLS);
        case "Cleric"->"History,Insight,Medicine,Persuasion,Religion";
        case "Druid"->"Arcana,Animal Handling,Insight,Medicine,Nature,Perception,Religion,Survival";
        case "Fighter"->"Acrobatics,Animal Handling,Athletics,History,Insight,Intimidation,Perception,Survival";
        case "Monk"->"Acrobatics,Athletics,History,Insight,Religion,Stealth";
        case "Paladin"->"Athletics,Insight,Intimidation,Medicine,Persuasion,Religion";
        case "Ranger"->"Animal Handling,Athletics,Insight,Investigation,Nature,Perception,Stealth,Survival";
        case "Rogue"->"Acrobatics,Athletics,Deception,Insight,Intimidation,Investigation,Perception,Performance,Persuasion,Sleight of Hand,Stealth";
        case "Sorcerer"->"Arcana,Deception,Insight,Intimidation,Persuasion,Religion";
        case "Warlock"->"Arcana,Deception,History,Intimidation,Investigation,Nature,Religion";
        case "Wizard"->"Arcana,History,Insight,Investigation,Medicine,Religion";default->"";};return skills.isEmpty()?Set.of():Set.copyOf(Arrays.asList(skills.split(",")));}

    public static Set<String> saves(String name){return switch(canonicalClass(name)){case "Barbarian","Fighter"->Set.of("str","con");case "Bard"->Set.of("dex","cha");case "Cleric","Paladin","Warlock"->Set.of("wis","cha");case "Druid","Wizard"->Set.of("int","wis");case "Monk","Ranger"->Set.of("str","dex");case "Rogue"->Set.of("dex","int");case "Sorcerer"->Set.of("con","cha");default->Set.of();};}
    public static String canonicalClass(String name){for(String c:List.of("Barbarian","Bard","Cleric","Druid","Fighter","Monk","Paladin","Ranger","Rogue","Sorcerer","Warlock","Wizard"))if(c.equalsIgnoreCase(name.trim()))return c;return name.trim();}
    public static String clazz(JsonObject sheet){return canonicalClass(SheetSections.raw(sheet,"identity").get("characterClass").getAsString());}
    public static boolean enrolled(JsonObject sheet){var c=sheet.getAsJsonObject("collections");return c!=null&&c.has("ui_creation");}
    public static int level(JsonObject sheet){return SheetSections.raw(sheet,"progression").get("recordedLevel").getAsInt();}
    public static int expertiseCount(String name,int level){name=canonicalClass(name);return name.equals("Rogue")?(level>=6?4:2):name.equals("Bard")?(level>=10?4:level>=3?2:0):0;}
    public static Choices choices(JsonObject sheet){return SheetSections.parse(SheetSections.raw(sheet,"creation"),Choices.class);}
    public static JsonObject effective(JsonObject sheet,JsonObject raw){if(!enrolled(sheet))return raw;var out=raw.deepCopy();var choice=choices(sheet);String name=clazz(sheet);merge(out,"skills",choice.skills().stream().filter(skills(name)::contains).toList());merge(out,"saves",saves(name));var trained=new HashSet<String>();for(var v:out.getAsJsonArray("skills"))trained.add(grant("skills",v.getAsString()));if(name.equals("Rogue"))merge(out,"tools",List.of("Thieves' tools"));merge(out,"expertise",choice.expertise().stream().filter(v->expertiseCount(name,level(sheet))>0&&(trained.contains(v)||name.equals("Rogue")&&v.equals("Thieves' tools"))).toList());return out;}
    private static void merge(JsonObject out,String key,Collection<String> extra){var values=new LinkedHashMap<String,String>();for(var v:out.getAsJsonArray(key))values.putIfAbsent(grant(key,v.getAsString()).toLowerCase(Locale.ROOT),grant(key,v.getAsString()));for(String v:extra)values.putIfAbsent(grant(key,v).toLowerCase(Locale.ROOT),grant(key,v));var a=new JsonArray();values.values().forEach(a::add);out.add(key,a);}
    public static String grant(String category,String value){String s=value.trim();if(category.equals("saves")){String[] full={"Strength","Dexterity","Constitution","Intelligence","Wisdom","Charisma"};for(int i=0;i<6;i++)if(ABILITIES.get(i).equalsIgnoreCase(s)||full[i].equalsIgnoreCase(s))return ABILITIES.get(i);}if(category.equals("skills")||category.equals("expertise"))for(String known:SKILLS)if(known.equalsIgnoreCase(s))return known;return s;}
    public static List<String> choiceErrors(JsonObject sheet,boolean complete){var out=new ArrayList<String>();if(!enrolled(sheet))return out;String name=clazz(sheet);var c=choices(sheet);if(skills(name).isEmpty()){if(!c.skills().isEmpty()||!c.expertise().isEmpty())out.add("Custom class: remove former SRD class choices; keep custom entries separately for DM review.");return out;}if(c.skills().size()>skillCount(name)||complete&&c.skills().size()!=skillCount(name))out.add(name+" class skills: choose exactly "+skillCount(name)+" ("+c.skills().size()+" selected).");for(String v:c.skills())if(!skills(name).contains(v))out.add(v+" is not an eligible "+name+" class skill. Resolve it in Proficiencies.");int limit=expertiseCount(name,level(sheet));if(c.expertise().size()>limit||complete&&limit>0&&c.expertise().size()!=limit)out.add(name+" expertise: choose "+limit+" eligible proficiencies at recorded level "+level(sheet)+".");var prof=SheetSections.raw(sheet,"proficiencies");var trained=new HashSet<>(c.skills());for(var v:prof.getAsJsonArray("skills"))trained.add(grant("skills",v.getAsString()));for(String v:c.expertise())if(!trained.contains(v)&&!(name.equals("Rogue")&&v.equals("Thieves' tools")))out.add("Expertise requires proficiency in "+v+".");return List.copyOf(out);}
    public static List<String> warnings(JsonObject sheet){var out=new ArrayList<String>();var p=SheetSections.raw(sheet,"proficiencies");for(String key:List.of("skills","saves","expertise"))if(!p.getAsJsonArray(key).isEmpty())out.add("Retained "+key+": "+p.get(key)+". Legacy/background/custom grants require DM review; these do not consume class choices.");return List.copyOf(out);}
    public static int remaining(Map<String,Integer> scores){int total=0;for(int score:scores.values()){require(COST.containsKey(score),"Point-buy base scores must be 8–15");total+=COST.get(score);}return 27-total;}
    public static void validateScores(String method,Map<String,Integer> scores,boolean complete){require(scores.keySet().equals(Set.copyOf(ABILITIES)),"Six scores required");if(method.equals("STANDARD_ARRAY")){var values=new ArrayList<>(scores.values());values.sort(Comparator.reverseOrder());require(values.equals(ARRAY),"Assign every Standard Array value exactly once");}else if(method.equals("POINT_BUY")){int left=remaining(scores);require(left>=0,"Point Buy exceeds 27 points");}else for(int n:scores.values())require(n>=1&&n<=30,"Manual scores must be 1–30");}
    public static int unusedPoints(JsonObject sheet){if(!enrolled(sheet)||!choices(sheet).abilityMethod().equals("POINT_BUY"))return 0;var scores=new LinkedHashMap<String,Integer>();var data=SrdAuthoring.data(sheet).getAsJsonObject("abilities");for(String key:ABILITIES)scores.put(key,data.get(key).getAsInt());return remaining(scores);}
    public static void scores(JsonObject sheet,boolean complete){if(!enrolled(sheet))return;var data=SrdAuthoring.data(sheet).getAsJsonObject("abilities");var values=new LinkedHashMap<String,Integer>();for(String key:ABILITIES)values.put(key,data.get(key).getAsInt());validateScores(choices(sheet).abilityMethod(),values,complete);}
    /** Validate every write path, including direct character-sync edits. Class changes retain unresolved prior choices. */
    public static void transition(JsonObject before,JsonObject after){GuidedRaceRules.transition(before,after);if(enrolled(before))require(enrolled(after),"Cannot remove guided creation validation");if(!enrolled(after))return;if(!choices(before).skills().equals(choices(after).skills())||!choices(before).expertise().equals(choices(after).expertise())){var errors=choiceErrors(after,false);require(errors.isEmpty(),String.join(" ",errors));}
        if(enrolled(before)){var old=SheetSections.raw(before,"proficiencies");var next=SheetSections.raw(after,"proficiencies");if(GuidedRaceRules.enrolled(before)&&!skills(clazz(after)).isEmpty())for(String key:List.of("tools","languages"))for(var v:next.getAsJsonArray(key))require(old.getAsJsonArray(key).asList().stream().anyMatch(x->x.getAsString().equalsIgnoreCase(v.getAsString())),"Additional tools and languages require authorized custom records; guided automation is deferred");for(String key:List.of("skills","saves","expertise"))for(var v:next.getAsJsonArray(key))require(old.getAsJsonArray(key).asList().stream().anyMatch(x->x.getAsString().equalsIgnoreCase(v.getAsString()))||!key.equals("expertise")&&(skills(clazz(after)).isEmpty()||key.equals("skills")&&!SKILLS.contains(v.getAsString())),"Use guided class choices; additional "+key+" require a custom class record and DM review");}
        if(!SheetSections.raw(before,"creation").get("abilityMethod").equals(SheetSections.raw(after,"creation").get("abilityMethod"))||!Objects.equals(combat(before),combat(after)))scores(after,false);
    }
    private static JsonElement combat(JsonObject s){var c=s.getAsJsonObject("collections");return c==null?null:c.get("srd_combat");}
}
