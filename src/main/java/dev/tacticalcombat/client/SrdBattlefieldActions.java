package dev.tacticalcombat.client;
import com.google.gson.*;
import java.util.*;

/** Only intent fields leave the UI. Server replies alone provide costs, dice, damage and HP. */
public final class SrdBattlefieldActions {
    private SrdBattlefieldActions() {}
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
