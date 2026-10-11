package dev.tacticalcombat.uiapi;
import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Explicit manual bookkeeping only. Caller must commit through library CAS or campaign edit policy. */
public final class RpgRecords {
    public enum Action { INSPIRATION, DEATH_SUCCESSES, DEATH_FAILURES, EXHAUSTION, HIT_DICE, SPELL_SLOT, RESOURCE }
    public record Change(Action action,String section,String key,int index,int value) {}
    private RpgRecords() {}
    public static JsonObject apply(JsonObject sheet,Change change) {
        require(change!=null && change.action()!=null,"Tracker action required");String section=change.section();
        var data=SheetSections.read(sheet,section);
        switch(change.action()) {
            case INSPIRATION -> {require(section.equals("trackers") && (change.value()==0 || change.value()==1),"Inspiration is a single manual state");data.addProperty("inspiration",change.value()==1);}
            case DEATH_SUCCESSES,DEATH_FAILURES,EXHAUSTION -> {
                require(section.equals("trackers"),"Tracker section required");data.addProperty(switch(change.action()) {case DEATH_SUCCESSES->"deathSuccesses";case DEATH_FAILURES->"deathFailures";default->"exhaustion";},change.value());
            }
            case HIT_DICE,SPELL_SLOT -> {
                require(section.equals(change.action()==Action.HIT_DICE?"trackers":"spellbook"),"Wrong resource section");
                var rows=data.getAsJsonArray(change.action()==Action.HIT_DICE?"hitDice":"slots");require(change.index()>=0 && change.index()<rows.size(),"Resource index unavailable");rows.get(change.index()).getAsJsonObject().addProperty("current",change.value());
            }
            case RESOURCE -> {
                require(Set.of("trackers","features").contains(section),"Resource section required");boolean found=false;
                for(var row:data.getAsJsonArray("resources")) if(row.getAsJsonObject().get("id").getAsString().equals(change.key())) {
                    require(row.getAsJsonObject().get("status").getAsString().equals("MANUAL"),"Resource not manually editable");row.getAsJsonObject().addProperty("current",change.value());found=true;break;
                }
                require(found,"Resource identifier unavailable");
            }
        }
        // Revalidates values and all cross-field invariants. No formula is evaluated.
        return SheetSections.read(SheetSections.update(sheet,section,data),section);
    }
    public static JsonObject catalog(String query,int page) {
        require(query!=null && query.length()<=80 && page>=0 && page<=128,"Invalid catalog filter");
        var out=new JsonObject();out.addProperty("version",1);out.addProperty("source","SRD 5.1 (2014)");out.addProperty("total",0);out.add("spells",new JsonArray());
        out.addProperty("reason","Standard definitions have not been imported and verified. Custom definitions remain in each character spellbook.");return out;
    }
}
