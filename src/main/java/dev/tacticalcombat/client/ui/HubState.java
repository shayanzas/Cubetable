package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;

/** Server-derived presentation only. Selecting an item never edits or binds a character. */
public final class HubState {
    public enum Status { LOADING, READY, EMPTY, ERROR, UNSUPPORTED }
    public record Choice(String id,String name,String detail,boolean enabled,String reason,long revision) {}
    public Status status=Status.LOADING;
    public String message="Loading server selection…",campaignName="No campaign selected",characterName="No character selected",profile="";
    public String campaign,character,bound;
    public boolean manager,inCombat,quickSupported;
    public void reset(boolean supported) {
        status=supported?Status.LOADING:Status.UNSUPPORTED;manager=false;campaign=null;character=null;bound=null;inCombat=false;
        campaignName="No campaign selected";characterName="No character selected";profile="";quickSupported=false;
        message=supported?"Loading server selection…":"Server UI unavailable. Local tabletop tools are available.";
    }
    public void active(JsonObject data) {campaign=optional(data,"campaign");character=optional(data,"character");bound=optional(data,"boundCharacter");inCombat=data.get("inCombat").getAsBoolean();}
    public void campaign(JsonObject data) {
        if(!Objects.equals(campaign,data.get("id").getAsString())) return;
        campaignName=data.get("name").getAsString();profile=profileName(data.get("profile").getAsString());manager=data.get("manager").getAsBoolean();
    }
    public void character(JsonObject data) {
        if(!Objects.equals(character==null?bound:character,data.get("id").getAsString())) return;
        characterName=(character==null?"Bound: ":"Selected: ")+data.get("name").getAsString()+" · "+(data.get("approved").getAsBoolean()?"approved":"awaiting approval")+(data.has("bound")?" · bound":" · unbound");
        if(campaign!=null && data.has("campaign") && !campaign.equals(data.get("campaign").getAsString())) characterName+=" · other campaign";
    }
    public boolean dmTools() {return status==Status.READY && campaign!=null && manager;}
    public String dmReason() {return status==Status.UNSUPPORTED?"Updated UI server required":campaign==null?"Select a campaign first":manager?"Campaign manager tools":"Only this campaign's DM or co-DM can open these tools";}
    public boolean canSwitch() {return status==Status.READY && !inCombat;}
    public static String profileName(String id) {return switch(id) {case "minecraft_tactical"->"Minecraft Tactical";case "manual_tabletop"->"Manual Tabletop";case "srd_5_1"->"D&D SRD 5.1";default->id;};}
    public static List<Choice> campaigns(JsonArray rows,boolean quick,boolean combat) {
        check(rows);var result=new ArrayList<Choice>();
        for(var value:rows) {var r=value.getAsJsonObject();boolean manager=r.get("manager").getAsBoolean();if(quick && !manager) continue;
            result.add(new Choice(r.get("id").getAsString(),r.get("name").getAsString(),profileName(r.get("profile").getAsString())+(manager?" · DM":" · member"),!combat,combat?"Selection is locked during an encounter":"",r.get("revision").getAsLong()));}
        return List.copyOf(result);
    }
    public static List<Choice> characters(JsonArray rows,String player,boolean combat) {
        check(rows);var result=new ArrayList<Choice>();
        for(var value:rows) {var r=value.getAsJsonObject();if(!r.get("privateView").getAsBoolean()) continue;
            boolean control=r.get("controller").getAsString().equals(player); // DM rows are marked separately by the caller's authoritative campaign role.
            result.add(new Choice(r.get("id").getAsString(),r.get("name").getAsString(),(r.get("approved").getAsBoolean()?"Approved":"Needs approval")+(r.has("bound")?" · bound":" · unbound"),!combat && control,
                    combat?"Selection is locked during an encounter":!control?"Assigned to another controller; ask your DM":"Selection changes only the indicator. Binding remains a DM operation.",r.get("revision").getAsLong()));}
        return List.copyOf(result);
    }
    public static List<Choice> library(JsonArray rows) {check(rows);var out=new ArrayList<Choice>();for(var e:rows) {var r=e.getAsJsonObject();out.add(new Choice(r.get("id").getAsString(),r.get("name").getAsString(),r.get("stage").getAsString()+" · "+profileName(r.get("profile").getAsString()),true,"Library drafts are independent from campaign characters",r.get("revision").getAsLong()));}return List.copyOf(out);}
    public static List<Choice> filter(List<Choice> rows,String query) {String q=query.strip().toLowerCase(Locale.ROOT);return rows.stream().filter(c->(c.name+" "+c.detail).toLowerCase(Locale.ROOT).contains(q)).toList();}
    public static List<Choice> allowManager(List<Choice> rows,boolean manager,boolean combat) {if(!manager || combat) return rows;return rows.stream().map(c->new Choice(c.id,c.name,c.detail,true,"Selection changes only the indicator; it does not bind an entity",c.revision)).toList();}
    public void failed(String code,String text) {status=Status.ERROR;manager=false;characterName="Character state unavailable";message=code+": "+text;}
    private static String optional(JsonObject o,String key) {return o.has(key)?o.get(key).getAsString():null;}
    private static void check(JsonArray a) {if(a.size()>8) throw new IllegalArgumentException("Server list exceeds page limit");}
}
