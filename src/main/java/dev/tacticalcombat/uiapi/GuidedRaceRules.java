package dev.tacticalcombat.uiapi;
import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;
/** SRD 5.1 (2014) subraces only. Policy applies to new guided records, never retroactively. */
public final class GuidedRaceRules {
    private static final String KEY="ui_race_policy";
    public static final Map<String,List<String>> SUBRACES=Map.of("Elf",List.of("High Elf"),"Dwarf",List.of("Hill Dwarf"),"Halfling",List.of("Lightfoot Halfling"),"Gnome",List.of("Rock Gnome"));
    private GuidedRaceRules(){}
    public static void enroll(JsonObject sheet){var rows=new JsonArray();var policy=new JsonObject();policy.addProperty("version",1);rows.add(policy);sheet.getAsJsonObject("collections").add(KEY,rows);}
    public static boolean enrolled(JsonObject sheet){var c=sheet.getAsJsonObject("collections");if(c==null||!c.has(KEY))return false;var rows=c.getAsJsonArray(KEY);require(rows.size()==1&&rows.get(0).isJsonObject(),"Invalid race policy");var p=rows.get(0).getAsJsonObject();require(p.keySet().equals(Set.of("version"))&&p.get("version").getAsBigDecimal().intValueExact()==1,"Unsupported race policy");return true;}
    public static List<String> errors(JsonObject sheet){if(!enrolled(sheet))return List.of();String race=SheetSections.raw(sheet,"identity").get("species").getAsString();var structural=selectionErrors(race);if(!structural.isEmpty())return structural;return SUBRACES.containsKey(race)?List.of("Choose a supported "+race+" subrace in Identity, or explicitly record an authorized custom race."):List.of();}
    public static List<String> selectionErrors(String race){int separator=race.indexOf(" — ");if(separator<0)return List.of();String parent=race.substring(0,separator),subrace=race.substring(separator+3);for(var entry:SUBRACES.entrySet())if(entry.getValue().contains(subrace)&&!entry.getKey().equals(parent))return List.of(subrace+" requires parent race "+entry.getKey()+".");return List.of();}
    public static void transition(JsonObject before,JsonObject after){if(enrolled(before))require(enrolled(after),"Cannot remove guided race selection policy");else enrolled(after);if(enrolled(after)&&!SheetSections.raw(before,"identity").get("species").equals(SheetSections.raw(after,"identity").get("species")))require(selectionErrors(SheetSections.raw(after,"identity").get("species").getAsString()).isEmpty(),"Supported subrace has an incorrect parent race");}
}
