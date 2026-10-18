package dev.tacticalcombat.client;
import com.google.gson.*;
import java.util.*;

/** Only intent fields leave the UI. Server replies alone provide costs, dice, damage and HP. */
public final class SrdBattlefieldActions {
    private SrdBattlefieldActions() {}
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
    public static JsonObject confirmation(JsonObject serverPreview){
        if(serverPreview==null||!serverPreview.has("id"))throw new IllegalArgumentException("Request a server preview first");
        var args=new JsonObject();args.addProperty("intent",UUID.fromString(serverPreview.get("id").getAsString()).toString());return args;
    }
}
