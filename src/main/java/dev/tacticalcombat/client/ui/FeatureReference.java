package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Versioned, read-only UI reference. Never a source of combat effects or character grants. */
public final class FeatureReference {
    public record Entry(String id,String name,String source,int level,String prerequisites,String action,String uses,String recovery,String formula,String effects,String status,String description) {
        public String tooltip(){return name+" · "+status.toLowerCase(Locale.ROOT)+"\n"+description.substring(0,Math.min(160,description.length()));}
        public String detail(){return name+"\n\n"+description+"\n\nSource: "+source+"\nLevel: "+(level==0?"Not level-gated / recorded":level)+"\nPrerequisites: "+(prerequisites.isBlank()?"See description":prerequisites)+"\nAction: "+action+"\nUses: "+(uses.isBlank()?"See description / none tracked":uses)+"\nRecovery: "+(recovery.isBlank()?"See description / none tracked":recovery)+"\nFormula: "+(formula.isBlank()?"See description / none defined":formula)+"\nEffects: "+effects+"\n\n"+status+": reference only; no effect is applied.\nReference ID: "+id;}
    }
    private static final JsonObject ROOT;
    static {try(var in=FeatureReference.class.getResourceAsStream("/assets/tacticalcombat/ui/feature-reference-v1.json")){if(in==null)throw new IllegalStateException("Missing feature reference");ROOT=JsonParser.parseReader(new InputStreamReader(in,StandardCharsets.UTF_8)).getAsJsonObject();if(ROOT.get("schema").getAsInt()!=1)throw new IllegalStateException("Unsupported feature reference");}catch(IOException error){throw new ExceptionInInitializerError(error);}}
    private FeatureReference() {}
    public static List<Entry> classFeatures(String id){var c=ROOT.getAsJsonObject("classes").getAsJsonObject(id);return c==null?List.of():entries(c.getAsJsonArray("features"));}
    public static String classProficiencies(String id){var c=ROOT.getAsJsonObject("classes").getAsJsonObject(id);return c==null?"Custom class": "Armor: "+text(c,"armor")+"\nWeapons: "+text(c,"weapons");}
    public static List<Entry> raceTraits(String race,String subrace){var c=ROOT.getAsJsonObject("races").getAsJsonObject(race);if(c==null)return List.of();var list=new ArrayList<>(entries(c.getAsJsonArray("features")));var subs=c.getAsJsonObject("subraces");if(subs.has(subrace))list.addAll(entries(subs.getAsJsonArray(subrace)));return List.copyOf(list);}
    private static List<Entry> entries(JsonArray array){var list=new ArrayList<Entry>();for(var e:array){var o=e.getAsJsonObject();list.add(new Entry(text(o,"id"),text(o,"name"),text(o,"source"),o.get("level").getAsInt(),text(o,"prerequisites"),text(o,"action"),text(o,"uses"),text(o,"recovery"),text(o,"formula"),text(o,"effects"),text(o,"status"),text(o,"description")));}return List.copyOf(list);}
    public static Entry recorded(JsonObject feature){var uses=feature.has("uses")&&feature.get("uses").isJsonObject()?feature.getAsJsonObject("uses"):new JsonObject();String pool=uses.has("current")?text(uses,"current")+" / "+text(uses,"maximum")+" · recorded pool":"";String description=text(feature,"description")+(feature.has("durationRounds")&&!feature.get("durationRounds").isJsonNull()?"\nRecorded duration: "+text(feature,"durationRounds")+" rounds (manual).":"");return new Entry(text(feature,"id"),text(feature,"name"),text(feature,"source"),0,"","Recorded/manual",pool,text(uses,"recharge"),"",text(feature,"description"),text(feature,"status"),description);}
}
