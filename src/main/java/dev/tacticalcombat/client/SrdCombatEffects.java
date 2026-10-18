package dev.tacticalcombat.client;
import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
/** Brief local feedback from a new authorized committed result only. No damage calls or broadcasts. */
public final class SrdCombatEffects {
    private static String target="";private static long expires;
    public static void clear(){target="";expires=0;}
    public static boolean hit(String id,long tick){return tick<expires&&target.equals(id);}
    public static void accept(JsonObject result,long tick){
        var mc=MinecraftClient.getInstance();if(mc.world==null||!result.has("actor"))return;
        for(var value:ClientSrdCombat.actors()){
            var row=value.getAsJsonObject();if(!row.get("id").equals(result.get("actor")))continue;
            var entity=mc.world.getEntityById(row.get("entity").getAsInt());
            if(entity instanceof LivingEntity living&&entity.getUuidAsString().equals(result.get("actor").getAsString()))living.swingHand(Hand.MAIN_HAND,false);
        }
        if(result.get("hit").getAsBoolean()){target=result.get("target").getAsString();expires=tick+14;}
    }
}
