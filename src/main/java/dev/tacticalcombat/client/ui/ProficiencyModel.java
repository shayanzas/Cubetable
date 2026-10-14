package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Tabletop choices; one proficiency plus optional expertise, never stacked duplicate grants. */
public final class ProficiencyModel {
    private ProficiencyModel() {}
    public static List<DashboardModel.Check> checks(JsonObject snapshot,boolean saves){var existing=DashboardModel.checks(snapshot,saves);if(!existing.isEmpty())return existing;var p=dev.tacticalcombat.uiapi.SheetSections.read(snapshot.getAsJsonObject("sheet"),"proficiencies");var out=new ArrayList<DashboardModel.Check>();if(saves){for(String key:CreatorDraftState.ABILITIES)out.add(new DashboardModel.Check(AbilityWorkspace.NAMES.get(key),key,0,0,0,contains(p,"saves",key),false));}else DashboardModel.SKILLS.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->out.add(new DashboardModel.Check(e.getKey(),e.getValue(),0,0,0,contains(p,"skills",e.getKey()),contains(p,"expertise",e.getKey()))));return List.copyOf(out);}
    public static boolean hasScores(JsonObject snapshot){return DashboardModel.checks(snapshot,true).size()==6;}
    public static boolean contains(JsonObject section,String category,String key){return array(section,category).asList().stream().anyMatch(v->canonical(category,v.getAsString()).equalsIgnoreCase(canonical(category,key)));}
    private static String canonical(String category,String value){String clean=value.trim();if(category.equals("saves")){for(String key:CreatorDraftState.ABILITIES)if(clean.equalsIgnoreCase(key)||clean.equalsIgnoreCase(AbilityWorkspace.NAMES.get(key)))return key;}else for(String skill:DashboardModel.SKILLS.keySet())if(clean.equalsIgnoreCase(skill))return skill;return clean;}
    public static JsonObject normalized(JsonObject section){var out=section.deepCopy();for(String category:List.of("skills","saves","expertise")){var unique=new LinkedHashMap<String,String>();for(var e:array(section,category)){String key=canonical(category,e.getAsString());if(!key.isBlank())unique.putIfAbsent(key.toLowerCase(Locale.ROOT),key);}var values=new JsonArray();unique.values().forEach(values::add);out.add(category,values);}for(var e:array(out,"expertise")){String name=e.getAsString();if(DashboardModel.SKILLS.containsKey(name)&&!contains(out,"skills",name))out.getAsJsonArray("skills").add(name);}return out;}
    public static JsonObject toggle(JsonObject section,String category,String key){var out=normalized(section);var values=new ArrayList<>(DashboardModel.strings(out,category));String name=canonical(category,key);boolean removed=values.removeIf(v->v.equalsIgnoreCase(name));if(!removed)values.add(name);var a=new JsonArray();values.forEach(a::add);out.add(category,a);if(category.equals("expertise")&&!removed&&!contains(out,"skills",name)){var skills=out.getAsJsonArray("skills");skills.add(name);}if(category.equals("skills")&&removed){var expert=new JsonArray();for(var v:array(out,"expertise"))if(!v.getAsString().equalsIgnoreCase(name))expert.add(v);out.add("expertise",expert);}return out;}
}
