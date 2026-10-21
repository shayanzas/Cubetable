package dev.tacticalcombat.client;
import com.google.gson.*;
import java.util.*;

/** Only intent fields leave the UI. Server replies alone provide costs, dice, damage and HP. */
public final class SrdBattlefieldActions {
    private SrdBattlefieldActions() {}
    public static boolean canPosture(JsonObject state,String pending){
        return pending.isEmpty()&&SrdViewPolicy.canControl(state)&&state.has("canPosture")&&state.get("canPosture").getAsBoolean()&&!state.get("moving").getAsBoolean();
    }
    public static JsonObject posture(JsonObject state,long revision,String pending){
        if(!canPosture(state,pending))throw new IllegalArgumentException("Posture change unavailable");
        var args=new JsonObject();args.addProperty("campaign",state.get("campaign").getAsString());args.addProperty("actor",state.get("active").getAsString());args.addProperty("revision",revision);return args;
    }
    public static boolean canDisengage(JsonObject state,String pending){return pending.isEmpty()&&SrdViewPolicy.canControl(state)&&state.has("canDisengage")&&state.get("canDisengage").getAsBoolean();}
    public static JsonObject disengage(JsonObject state,long revision,String pending){
        if(!canDisengage(state,pending))throw new IllegalArgumentException("Disengage unavailable");
        var a=new JsonObject();a.addProperty("campaign",state.get("campaign").getAsString());a.addProperty("actor",state.get("active").getAsString());a.addProperty("revision",revision);return a;
    }
    public static boolean canDodge(JsonObject state,String pending){
        return pending.isEmpty()&&SrdViewPolicy.canControl(state)&&state.has("canDodge")&&state.get("canDodge").getAsBoolean()
            &&!state.get("moving").getAsBoolean()&&!state.get("actionUsed").getAsBoolean();
    }
    public static JsonObject dodge(JsonObject state,long revision,String pending){
        if(!canDodge(state,pending))throw new IllegalArgumentException("Dodge unavailable for this actor/action state");
        var args=new JsonObject();args.addProperty("campaign",state.get("campaign").getAsString());args.addProperty("actor",state.get("active").getAsString());args.addProperty("revision",revision);return args;
    }
    public static boolean canDash(JsonObject state,String pending){
        return pending.isEmpty() && SrdViewPolicy.canControl(state) && state.has("dashFeet") && state.get("dashFeet").getAsInt()>0
            && !state.get("actionUsed").getAsBoolean() && !state.get("moving").getAsBoolean();
    }
    public static JsonObject dash(JsonObject state,long revision,String pending){
        if(!canDash(state,pending))throw new IllegalArgumentException("Dash requires your available normal action and no pending route/request");
        var args=new JsonObject();args.addProperty("campaign",state.get("campaign").getAsString());
        args.addProperty("actor",state.get("active").getAsString());args.addProperty("revision",revision);return args;
    }
    public static JsonObject preview(JsonObject state,String target,SrdAttackChoices.Choice choice){
        var actor=SrdViewPolicy.current(state);
        if(!SrdViewPolicy.canControl(state)||actor==null||!actor.has("weapons")
                ||state.get("actionUsed").getAsBoolean()||state.get("moving").getAsBoolean()
                ||target.equals(state.get("active").getAsString())||SrdBattlefieldModel.actor(state.getAsJsonArray("actors"),target)==null)throw new IllegalArgumentException("No eligible controlled attack/target");
        boolean permitted=false;
        for(var weapon:actor.getAsJsonArray("weapons"))if(SrdAttackChoices.forWeapon(weapon.getAsString(),actor.has("monsterAbility")?actor.get("monsterAbility").getAsString():null).contains(choice))permitted=true;
        if(!permitted)throw new IllegalArgumentException("Attack choice is no longer available");
        var args=new JsonObject();args.addProperty("target",target);args.addProperty("weapon",choice.weapon().name());args.addProperty("ability",choice.ability().name());args.addProperty("mode",choice.mode().name());args.addProperty("twoHands",choice.twoHands());return args;
    }
    public static boolean matchesPreview(JsonObject state,JsonObject preview,String target,long revision){
        return preview!=null&&preview.has("actor")&&preview.has("target")&&preview.has("turn")&&preview.has("revision")&&state.has("active")&&state.has("turn")
            &&preview.get("actor").equals(state.get("active"))&&preview.get("target").getAsString().equals(target)&&preview.get("turn").equals(state.get("turn"))&&preview.get("revision").getAsLong()==revision;
    }
    public static boolean canConfirm(JsonObject state,JsonObject preview,String target,long revision,String pending){return pending.isEmpty()&&SrdViewPolicy.canControl(state)&&!state.get("moving").getAsBoolean()&&!state.get("actionUsed").getAsBoolean()&&matchesPreview(state,preview,target,revision);}
    public static JsonObject confirmation(JsonObject serverPreview){
        if(serverPreview==null||!serverPreview.has("id"))throw new IllegalArgumentException("Request a server preview first");
        var args=new JsonObject();args.addProperty("intent",UUID.fromString(serverPreview.get("id").getAsString()).toString());return args;
    }
}
