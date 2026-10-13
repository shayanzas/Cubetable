package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Read-only sheet presentation; no local recalculation changes saved statistics. */
public final class CreatorSheet {
    public record Section(String title,String text) {}
    private CreatorSheet() {}
    public static List<Section> sections(JsonObject draft,JsonObject validation) {
        var result=new ArrayList<Section>();var sheet=draft.getAsJsonObject("sheet");var i=SheetSections.read(sheet,"identity");
        result.add(new Section(text(draft,"name"),text(i,"species")+"\n"+text(i,"characterClass")+"\n"+text(i,"background")+"\n"+DashboardModel.xp(sheet).label()));
        result.add(new Section("Combat inputs",statLines(DashboardModel.combat(draft,true))));
        result.add(new Section("Abilities",statLines(DashboardModel.abilities(draft))));
        var p=SheetSections.read(sheet,"proficiencies");var prof=new StringBuilder();for(String key:List.of("skills","saves","expertise","tools","languages"))prof.append(key).append(": ").append(String.join(", ",DashboardModel.strings(p,key))).append('\n');result.add(new Section("Proficiencies · recorded",prof.toString()));
        var inventory=SheetSections.read(sheet,"inventory");var items=new StringBuilder();for(var e:array(inventory,"items")){var item=e.getAsJsonObject();items.append(text(item,"name")).append(" × ").append(item.get("quantity").getAsInt()).append(flag(item,"equipped")?" · equipped":" · carried").append('\n').append(text(item,"notes")).append('\n');}for(String coin:List.of("copper","silver","electrum","gold","platinum"))items.append(coin).append(": ").append(inventory.get(coin).getAsLong()).append("  ");result.add(new Section("Equipment · manual",items.toString()));
        var features=new StringBuilder();for(var e:array(SheetSections.read(sheet,"features"),"entries")){var f=e.getAsJsonObject();features.append(text(f,"name")).append("\n").append(text(f,"description")).append('\n');}result.add(new Section("Features & Feats · manual",features.isEmpty()?"No features recorded.":features.toString()));
        var spells=new StringBuilder();var book=SheetSections.read(sheet,"spellbook");for(var e:array(book,"customDefinitions")){var s=e.getAsJsonObject();spells.append(text(s,"name")).append("\n").append(text(s,"description")).append('\n');}for(var e:array(book,"selections")){var s=e.getAsJsonObject();spells.append(text(s,"spell")).append(flag(s,"prepared")?" · prepared":" · known").append('\n');}result.add(new Section("Spells · manual",spells.isEmpty()?"No custom spells recorded. Standard catalog and casting are not implemented.":spells.toString()));
        var story=new StringBuilder();for(String key:List.of("alignment","age","height","appearance","biography","personality","ideals","bonds","flaws"))if(!text(i,key).isBlank())story.append(key).append("\n").append(text(i,key)).append("\n\n");result.add(new Section("Character Details",story.isEmpty()?"Optional story details have not been filled in.":story.toString()));
        if(validation!=null&&!array(validation,"errors").isEmpty()){var issues=new StringBuilder();for(var e:array(validation,"errors")){var v=e.getAsJsonObject();issues.append(CreatorFlow.errorStep(text(v,"section")).title).append(": ").append(text(v,"message")).append('\n');}result.add(new Section("Please correct",issues.toString()));}
        result.add(new Section("Before you finish","Finish checks supported fields and saves to My Characters. Campaign approval and binding are separate. Race, class, feature and spell benefits require manual review."));
        return List.copyOf(result);
    }
    private static String statLines(List<DashboardModel.Stat> stats){var out=new StringBuilder();for(var s:stats)out.append(s.label()).append("  ").append(s.value()).append('\n');return out.toString();}
    public static String summary(JsonObject draft,boolean full,JsonObject validation){
        var sheet=draft.getAsJsonObject("sheet");var identity=SheetSections.read(sheet,"identity");
        var out=new StringBuilder(text(draft,"name")+"\n"+text(identity,"species")+"\n"+text(identity,"characterClass")+"\n"+text(identity,"background")+"\n\n"+DashboardModel.xp(sheet).label()+"\n");
        out.append("\nABILITIES\n");for(var stat:DashboardModel.abilities(draft))out.append(stat.label()).append("  ").append(stat.value()).append('\n');
        if(!full)return out.toString();
        out.append("\nCOMBAT INPUTS\n");for(var stat:DashboardModel.combat(draft,true))out.append(stat.label()).append("  ").append(stat.value()).append('\n');
        var prof=SheetSections.read(sheet,"proficiencies");out.append("\nPROFICIENCIES · RECORDED\n");for(String key:List.of("skills","saves","expertise","tools","languages"))out.append(key).append(": ").append(String.join(", ",DashboardModel.strings(prof,key))).append('\n');
        out.append("\nEQUIPMENT · MANUAL\n");for(var e:SheetSections.read(sheet,"inventory").getAsJsonArray("items")){var i=e.getAsJsonObject();out.append(text(i,"name")).append(" × ").append(i.get("quantity").getAsInt()).append(flag(i,"equipped")?" · equipped":" · carried").append('\n');}
        out.append("\nFEATURES & FEATS · MANUAL\n");for(var e:SheetSections.read(sheet,"features").getAsJsonArray("entries")){var f=e.getAsJsonObject();out.append(text(f,"name")).append(" — ").append(text(f,"description")).append('\n');}
        out.append("\nSPELLBOOK · MANUAL\n");var book=SheetSections.read(sheet,"spellbook");for(var e:book.getAsJsonArray("customDefinitions")){var s=e.getAsJsonObject();out.append(text(s,"name")).append(" — ").append(text(s,"description")).append('\n');}
        out.append("\nCHARACTER DETAILS\n");for(String key:List.of("alignment","age","height","appearance","biography","personality","ideals","bonds","flaws"))if(!text(identity,key).isBlank())out.append(key).append(": ").append(text(identity,key)).append('\n');
        if(validation!=null&&!array(validation,"errors").isEmpty()){out.append("\nPLEASE CORRECT\n");for(var e:array(validation,"errors")){var v=e.getAsJsonObject();out.append(CreatorFlow.errorStep(text(v,"section")).title).append(": ").append(text(v,"message")).append('\n');}}
        out.append("\nFinish checks supported fields and saves to My Characters. Campaign approval and binding are separate. Class, race, feature and spell benefits require manual review.");
        return out.toString();
    }
}
