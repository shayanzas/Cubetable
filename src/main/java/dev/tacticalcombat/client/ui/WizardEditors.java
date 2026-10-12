package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.MinecraftClient;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Section editors use full typed records and the existing library CAS operation. */
public final class WizardEditors {
    private WizardEditors() {}
    public static Screen canonicalName(Screen parent,JsonObject draft) {
        return form(parent,"Canonical character name",draft,List.of(new RecordFormScreen.Field("name","Character name",text(draft,"name"),80)),
            (state,v)->args("character",text(draft,"id"),"revision",revision(state),"section","basics","data",args("name",v.get("name"))),"The canonical library name; no duplicate identity name is created.");
    }
    public static Screen identity(Screen parent,JsonObject draft) {
        JsonObject d=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");var fields=new ArrayList<RecordFormScreen.Field>();
        fields.add(f("species","Species / race",d,128));fields.add(f("characterClass","Class",d,128));fields.add(f("subclass","Subclass",d,128));fields.add(f("background","Background",d,128));fields.add(f("alignment","Alignment",d,128));fields.add(f("age","Age",d,128));fields.add(f("height","Height / build",d,128));
        for(String k:List.of("appearance","biography","personality","ideals","bonds","flaws")) fields.add(f(k,label(k),d,2048));
        return section(parent,"Identity and narrative",draft,"identity",fields,(state,v)->{
            var current=SheetSections.read(state.getAsJsonObject("sheet"),"identity");for(var e:v.entrySet()) current.addProperty(e.getKey(),e.getValue());return current;
        },"Pronouns and weight lack separate backend fields; record them in biography/appearance if desired. Portrait remains an inert reference and remote downloads are not used.");
    }
    public static Screen progression(Screen parent,JsonObject draft) {
        JsonObject d=SheetSections.read(draft.getAsJsonObject("sheet"),"progression");var fields=List.of(
            f("recordedLevel","Recorded level (1–20)",d,2),f("xp","Recorded XP",d,10),f("mode","Advancement: XP, MILESTONE, DM_MANAGED",d,16),
            new RecordFormScreen.Field("classes","Class records (comma separated)",join(d,"classes"),512),new RecordFormScreen.Field("feats","Feat records (comma separated)",join(d,"feats"),512),new RecordFormScreen.Field("abilityImprovements","ASI records (comma separated)",join(d,"abilityImprovements"),512),new RecordFormScreen.Field("pendingChoices","Outstanding choices (comma separated)",join(d,"pendingChoices"),512));
        return section(parent,"Progression records",draft,"progression",fields,(state,v)->{
            var o=SheetSections.read(state.getAsJsonObject("sheet"),"progression");o.addProperty("recordedLevel",integer(v,"recordedLevel",1,20));o.addProperty("xp",longNumber(v,"xp",0,1_000_000_000L));
            String mode=v.get("mode").trim().toUpperCase(Locale.ROOT);if(!Set.of("XP","MILESTONE","DM_MANAGED").contains(mode)) throw new IllegalArgumentException("Advancement must be XP, MILESTONE or DM_MANAGED");o.addProperty("mode",mode);
            for(String k:List.of("classes","feats","abilityImprovements","pendingChoices")) o.add(k,array(v.get(k),k.equals("classes")?20:32));return o;
        },"Recorded level, XP, classes, feats and ASIs are informational. This does not level the authoritative combat character or grant a feat.");
    }
    public static Screen proficiencies(Screen parent,JsonObject draft) {
        JsonObject d=SheetSections.read(draft.getAsJsonObject("sheet"),"proficiencies");var fields=new ArrayList<RecordFormScreen.Field>();
        for(String k:List.of("skills","saves","tools","languages","expertise")) fields.add(new RecordFormScreen.Field(k,label(k)+" (comma separated)",join(d,k),1024));
        return section(parent,"Recorded proficiencies",draft,"proficiencies",fields,(state,v)->{
            var o=SheetSections.read(state.getAsJsonObject("sheet"),"proficiencies");o.add("skills",array(v.get("skills"),32));o.add("saves",array(v.get("saves"),6));o.add("tools",array(v.get("tools"),32));o.add("languages",array(v.get("languages"),32));o.add("expertise",array(v.get("expertise"),32));return o;
        },"These source-labelled records are informational. Existing SRD armor/weapon proficiency fields remain canonical for automated combat; duplicate-choice resolution is not automated.");
    }
    public static Screen spellSummary(Screen parent,JsonObject draft) {
        JsonObject d=SheetSections.read(draft.getAsJsonObject("sheet"),"spellbook");var fields=List.of(f("recordedAbility","Recorded spell ability",d,16),f("recordedSaveDc","Recorded save DC",d,3),f("recordedAttackModifier","Recorded attack modifier",d,4),new RecordFormScreen.Field("slots","Slots as level:current/maximum",slots(d),256));
        return section(parent,"Spellcasting summary",draft,"spellbook",fields,(state,v)->{var o=SheetSections.read(state.getAsJsonObject("sheet"),"spellbook");o.addProperty("recordedAbility",v.get("recordedAbility"));o.addProperty("recordedSaveDc",integer(v,"recordedSaveDc",0,100));o.addProperty("recordedAttackModifier",integer(v,"recordedAttackModifier",-100,100));o.add("slots",parseSlots(v.get("slots")));return o;},"Recorded values and slots are manual/informational; no spellcasting or effects are automated.");
    }
    private static RecordFormScreen form(Screen parent,String title,JsonObject draft,List<RecordFormScreen.Field> fields,java.util.function.BiFunction<JsonObject,Map<String,String>,JsonObject> build,String note) {
        return new RecordFormScreen(parent,title,LIBRARY_GET,args("character",text(draft,"id")),fields,build,LIBRARY_UPDATE,data->MinecraftClient.getInstance().setScreen(parent),note).expected(draft);
    }
    private static RecordFormScreen section(Screen parent,String title,JsonObject draft,String section,List<RecordFormScreen.Field> fields,java.util.function.BiFunction<JsonObject,Map<String,String>,JsonObject> data,String note) {
        return form(parent,title,draft,fields,(state,v)->args("character",text(draft,"id"),"revision",revision(state),"section",section,"data",data.apply(state,v)),note);
    }
    private static RecordFormScreen.Field f(String key,String label,JsonObject o,int limit) {return new RecordFormScreen.Field(key,label,text(o,key),limit);}
    private static String label(String key) {return switch(key) {case "characterClass"->"Class";case "abilityImprovements"->"Ability score improvements";default->Character.toUpperCase(key.charAt(0))+key.substring(1);};}
    private static String join(JsonObject o,String key) {var result=new ArrayList<String>();for(var e:ManagementState.array(o,key)) result.add(e.getAsString());return String.join(", ",result);}
    private static String slots(JsonObject o) {var result=new ArrayList<String>();for(var e:ManagementState.array(o,"slots")){var s=e.getAsJsonObject();result.add(s.get("level").getAsInt()+":"+s.get("current").getAsInt()+"/"+s.get("maximum").getAsInt());}return String.join(", ",result);}
    static JsonArray parseSlots(String value) {var out=new JsonArray();var levels=new HashSet<Integer>();if(value==null||value.isBlank())return out;for(String raw:value.split(",")){try{String[] halves=raw.trim().split(":",2),counts=halves[1].split("/",2);int level=Integer.parseInt(halves[0]),current=Integer.parseInt(counts[0]),maximum=Integer.parseInt(counts[1]);if(level<1||level>9||maximum<0||maximum>99||current<0||current>maximum||!levels.add(level))throw new IllegalArgumentException();var s=new JsonObject();s.addProperty("level",level);s.addProperty("current",current);s.addProperty("maximum",maximum);s.addProperty("source","manual");out.add(s);}catch(RuntimeException invalid){throw new IllegalArgumentException("Slots must use unique level:current/maximum entries, for levels 1–9");}}return out;}
    static JsonArray array(String text,int limit) {var a=new JsonArray();var seen=new LinkedHashSet<String>();if(text!=null && !text.isBlank()) for(String raw:text.split(",")) {String s=raw.trim();if(!s.isEmpty()) {if(s.length()>256 || !seen.add(s)) throw new IllegalArgumentException("Entries must be unique and at most 256 characters");a.add(s);}}if(a.size()>limit) throw new IllegalArgumentException("Too many entries (maximum "+limit+")");return a;}
    static int integer(Map<String,String> v,String key,int min,int max) {try {int n=Integer.parseInt(v.get(key).trim());if(n<min || n>max) throw new NumberFormatException();return n;}catch(RuntimeException e) {throw new IllegalArgumentException(key+" must be a whole number from "+min+" to "+max);}}
    static long longNumber(Map<String,String> v,String key,long min,long max) {try {long n=Long.parseLong(v.get(key).trim());if(n<min || n>max) throw new NumberFormatException();return n;}catch(RuntimeException e) {throw new IllegalArgumentException(key+" must be a whole number from "+min+" to "+max);}}
}
