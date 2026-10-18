package dev.tacticalcombat.client;
import com.google.gson.*;
/** Formats only authorized fields. Raw rolled damage is not implied to be target HP loss. */
public final class SrdAttackFeedback {
    private SrdAttackFeedback() {}
    private static String name(JsonArray actors,String id){for(var a:actors)if(a.getAsJsonObject().get("id").getAsString().equals(id))return a.getAsJsonObject().get("name").getAsString();return "Actor";}
    public static String describe(JsonObject r,JsonArray actors){
        String text=(r.has("actor")?name(actors,r.get("actor").getAsString())+" → "+name(actors,r.get("target").getAsString())+": ":"")
            +(r.get("critical").getAsBoolean()?"critical hit":r.get("hit").getAsBoolean()?"hit":"miss")+"; attack "+r.get("total");
        if(r.has("modifier"))text+=" (d20 "+r.get("chosen")+", modifier "+r.get("modifier")+")";
        if(r.has("rolledDamage"))text+="; rolled damage "+r.get("rolledDamage")+" "+r.get("damageType").getAsString().toLowerCase(java.util.Locale.ROOT);
        if(r.has("hpBefore"))text+="; target HP "+r.get("hpBefore")+" → "+r.get("hpAfter")+"; temp "+r.get("temporaryBefore")+" → "+r.get("temporaryAfter")+(r.get("zeroHp").getAsBoolean()?"; ZERO HP — paused for DM, no automatic death":"");
        else text+="; target HP private";
        return text;
    }
}
