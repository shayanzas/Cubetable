package dev.tacticalcombat.client.ui;
import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import java.util.*;
/** Class choices only; historical/custom grants are never rewritten by this editor. */
public final class ProficiencyChoices {
    private ProficiencyChoices(){}
    public static List<String> expertiseOptions(JsonObject sheet){String clazz=CreationRules.clazz(sheet);if(CreationRules.expertiseCount(clazz,CreationRules.level(sheet))==0)return List.of();var effective=SheetSections.read(sheet,"proficiencies");var out=new ArrayList<String>();for(String skill:CreationRules.SKILLS.stream().sorted().toList())if(ProficiencyModel.contains(effective,"skills",skill))out.add(skill);if(clazz.equals("Rogue"))out.add("Thieves' tools");return List.copyOf(out);}
    public static JsonObject toggle(JsonObject sheet,JsonObject choices,String category,String key){var next=choices.deepCopy();var values=new ArrayList<>(DashboardModel.strings(next,category));if(!values.remove(key)){String clazz=CreationRules.clazz(sheet);int limit=category.equals("skills")?CreationRules.skillCount(clazz):CreationRules.expertiseCount(clazz,CreationRules.level(sheet));if(values.size()>=limit)throw new IllegalArgumentException("Remove a choice first; all "+limit+" selections are allocated.");var preview=SheetSections.update(sheet,"creation",next);if(category.equals("skills")?!CreationRules.skills(clazz).contains(key):!expertiseOptions(preview).contains(key))throw new IllegalArgumentException("This choice is not eligible.");values.add(key);}var a=new JsonArray();values.forEach(a::add);next.add(category,a);if(category.equals("skills")){var legacy=SheetSections.raw(sheet,"proficiencies");if(!ProficiencyModel.contains(legacy,"skills",key)&&!values.contains(key)){var experts=new JsonArray();for(var v:next.getAsJsonArray("expertise"))if(!v.getAsString().equals(key))experts.add(v);next.add("expertise",experts);}}return next;}
}
