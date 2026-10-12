package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;

/** Pure presentation rules. Server checks remain authoritative at mutation time. */
public final class ManagementState {
    private ManagementState() {}
    public static JsonObject args(Object... pairs) {
        var a=new JsonObject();for(int i=0;i<pairs.length;i+=2) {
            Object v=pairs[i+1];String k=pairs[i].toString();
            if(v==null) a.add(k,JsonNull.INSTANCE);else if(v instanceof JsonElement e) a.add(k,e.deepCopy());
            else if(v instanceof Boolean b) a.addProperty(k,b);else if(v instanceof Number n) a.addProperty(k,n);else a.addProperty(k,v.toString());
        }return a;
    }
    public static String text(JsonObject o,String key) {return o.has(key) && !o.get(key).isJsonNull()?o.get(key).getAsString():"";}
    public static boolean flag(JsonObject o,String key) {return o.has(key) && !o.get(key).isJsonNull() && o.get(key).getAsBoolean();}
    public static long revision(JsonObject o) {return o.get("revision").getAsLong();}
    public static JsonArray array(JsonObject o,String key) {return o.has(key) && o.get(key).isJsonArray()?o.getAsJsonArray(key):new JsonArray();}
    public static String player(JsonObject data,String id) {
        var names=data.getAsJsonObject("playerNames");return names!=null && names.has(id)?names.get(id).getAsString():"Unknown/offline player";
    }
    public static String profile(String value) {return switch(value) {case "srd_5_1"->"SRD 5.1";case "minecraft_tactical"->"Minecraft Tactical";case "manual_tabletop"->"Manual Tabletop";default->value;};}
    public static boolean manager(JsonObject data) {return flag(data,"manager");}
    public static boolean canManageCharacter(JsonObject data) {return manager(data) && text(data,"bound").isEmpty();}
    public static boolean canSelect(JsonObject data,String self,String campaign,boolean combat) {return !combat && text(data,"campaign").equals(campaign) && (manager(data) || text(data,"controller").equals(self));}
    public static boolean canApprove(JsonObject data,boolean approve) {return canManageCharacter(data) && flag(data,"approved")!=approve;}
    public static boolean needsEditReview(JsonObject original,JsonObject latest) {return original!=null && original.has("revision") && latest.has("revision") && revision(original)!=revision(latest);}
    public static int formCapacity(int frameHeight) {return Math.max(1,(frameHeight-124)/36);}
    public static boolean canAnswer(JsonObject assignment,String self) {return text(assignment,"decision").equals("PENDING") && text(assignment,"offered").equals(self) && !flag(assignment,"bound");}
    public static boolean selectablePlayer(JsonObject row,boolean assignment) {return !assignment || flag(row,"member") && !flag(row,"inCombat");}
    public static boolean roleChange(JsonObject campaign,String player) {return flag(campaign,"canManageRoles") && !text(campaign,"owner").equals(player);}
    public static List<HubState.Choice> campaigns(JsonArray data,String selected) {
        var out=new ArrayList<HubState.Choice>();for(var e:data) {var o=e.getAsJsonObject();String id=text(o,"id");
            out.add(new HubState.Choice(id,text(o,"name"),profile(text(o,"profile"))+" · "+(manager(o)?"DM/manager":"Member")+(id.equals(selected)?" · selected":""),true,"Authorized membership; open details",revision(o)));
        }return out;
    }
    public static String validation(JsonObject data) {
        var errors=array(data,"errors");if(errors.isEmpty()) return "Supported fields valid. Full guided build legality is not verified.";
        var lines=new ArrayList<String>();for(var e:errors) {var o=e.getAsJsonObject();lines.add(text(o,"section")+": "+text(o,"message"));}return String.join("\n",lines);
    }
    public static UiLayout.Rect listArea(UiLayout.Rect frame,boolean paged) {return new UiLayout.Rect(frame.x()+12,frame.y()+62,frame.width()-24,Math.max(38,frame.height()-(paged?124:100)));}
}
