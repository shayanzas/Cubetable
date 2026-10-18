package dev.tacticalcombat.client;
import com.google.gson.*;
/** Formats only authorized fields. Raw rolled damage is not implied to be target HP loss. */
public final class SrdAttackFeedback {
    private SrdAttackFeedback() {}
    private static String name(JsonArray actors,String id){for(var a:actors)if(a.getAsJsonObject().get("id").getAsString().equals(id))return a.getAsJsonObject().get("name").getAsString();return "Actor";}
    public static String summary(JsonObject r,JsonArray actors){
        String actor=r.has("actor")?name(actors,r.get("actor").getAsString()):"Actor",target=r.has("target")?name(actors,r.get("target").getAsString()):"Target";
        actor=actor.substring(0,Math.min(18,actor.length()));target=target.substring(0,Math.min(18,target.length()));
        String mode=r.has("keep")?(r.get("keep").getAsInt()==1?"Advantage":r.get("keep").getAsInt()==2?"Disadvantage":"Normal"):"";
        String text=actor+" → "+target+"\n"+(r.get("critical").getAsBoolean()?"CRITICAL HIT":r.get("hit").getAsBoolean()?"HIT":"MISS")+" · "+mode+" · attack "+r.get("total");
        if(r.has("hpBefore"))text+="\nHP loss "+Math.max(0,r.get("hpBefore").getAsInt()-r.get("hpAfter").getAsInt())+" · HP "+r.get("hpAfter")+(r.get("zeroHp").getAsBoolean()?" · DM review":"");
        else if(r.has("rolledDamage"))text+="\nRolled damage "+r.get("rolledDamage")+" · HP private";
        return text;
    }
    public static String describe(JsonObject r,JsonArray actors){
        String text=(r.has("actor")?name(actors,r.get("actor").getAsString())+" → "+name(actors,r.get("target").getAsString())+": ":"")
            +(r.get("critical").getAsBoolean()?"critical hit":r.get("hit").getAsBoolean()?"hit":"miss")+"; attack "+r.get("total");
        if(r.has("keep"))text+="; "+(r.get("keep").getAsInt()==1?"Advantage":r.get("keep").getAsInt()==2?"Disadvantage":"Normal")+(r.has("faces")?" · d20 "+r.get("faces")+" · kept "+r.get("chosen"):"");
        if(r.has("modifier"))text+=" (d20 "+r.get("chosen")+", modifier "+r.get("modifier")+")";
        if(r.has("rolledDamage"))text+="; rolled damage "+r.get("rolledDamage")+" "+r.get("damageType").getAsString().toLowerCase(java.util.Locale.ROOT);
        if(r.has("hpBefore"))text+="; target HP "+r.get("hpBefore")+" → "+r.get("hpAfter")+"; temp "+r.get("temporaryBefore")+" → "+r.get("temporaryAfter")+(r.get("zeroHp").getAsBoolean()?"; ZERO HP — paused for DM, no automatic death":"");
        else text+="; target HP private";
        if(r.has("hpBefore"))text+="; HP loss "+Math.max(0,r.get("hpBefore").getAsInt()-r.get("hpAfter").getAsInt())+"; temp absorbed "+Math.max(0,r.get("temporaryBefore").getAsInt()-r.get("temporaryAfter").getAsInt());
        return text;
    }
}
