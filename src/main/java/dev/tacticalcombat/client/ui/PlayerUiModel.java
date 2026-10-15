package dev.tacticalcombat.client.ui;
import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
/** Player labels and route eligibility from actual server records, never client permissions. */
public final class PlayerUiModel {
 private PlayerUiModel(){}
 public static boolean creator(JsonObject d){return text(d,"profile").equals("srd_5_1");}
 public static boolean complete(JsonObject d){return text(d,"stage").equals("COMPLETE");}
 public static boolean sheet(JsonObject d){return creator(d)&&complete(d)&&(!d.has("dashboardSupported")||flag(d,"dashboardSupported"));}
 public static String primary(JsonObject d){return complete(d)?"Open Character Sheet":"Continue Creating";}
 public static String status(JsonObject d){return flag(d,"needsAttention")?"Needs Attention":complete(d)?"Complete":"Draft";}
 public static JsonObject identity(JsonObject d){return d.has("summary")?d.getAsJsonObject("summary"):d.has("sheet")?SheetSections.raw(d.getAsJsonObject("sheet"),"identity"):new JsonObject();}
 public static String raceLabel(String species){for(var entry:dev.tacticalcombat.uiapi.GuidedRaceRules.SUBRACES.entrySet())for(String sub:entry.getValue())if(species.equals(entry.getKey()+" — "+sub))return sub;return species.isBlank()?"Race not chosen":species;}
 public static String race(JsonObject d){String s=text(identity(d),"species");return s.isBlank()?"Race not chosen":s;}
 public static String clazz(JsonObject d){var i=identity(d);String s=text(i,"characterClass"),sub=text(i,"subclass");return s.isBlank()?"Class not chosen":s+(sub.isBlank()?"":" · "+sub);}
 public static String level(JsonObject d){if(d.has("summary")&&d.getAsJsonObject("summary").has("level"))return "Level "+d.getAsJsonObject("summary").get("level").getAsInt();return d.has("sheet")&&creator(d)?"Level "+DashboardModel.xp(d.getAsJsonObject("sheet")).level():"";}
 public static String statusLine(JsonObject d){String level=level(d);return status(d)+(level.isBlank()?"":" · "+level);}
 public static String description(JsonObject d){return race(d)+"\n"+clazz(d)+"\n"+level(d)+" · "+status(d);}
 public static String icon(JsonObject d){String c=text(identity(d),"characterClass").toLowerCase(Locale.ROOT);return CreatorIcons.REGIONS.containsKey(c)?c:"custom";}
 public static boolean matches(JsonObject d,String query){return (text(d,"name")+" "+description(d)+" "+HubState.profileName(text(d,"profile"))).toLowerCase(Locale.ROOT).contains(query.strip().toLowerCase(Locale.ROOT));}
 public static String primaryReason(JsonObject d){return complete(d)?"This ruleset has no supported character sheet here. Use Tabletop Tools for manual packs.":"This creator supports D&D SRD 5.1. Manage Character can edit this record's name.";}
}
