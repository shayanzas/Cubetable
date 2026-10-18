package dev.tacticalcombat.client;
import com.google.gson.*;

/** Presentation capabilities come from a viewer-filtered snapshot, never the local player's entity ID. */
public final class SrdViewPolicy {
    private SrdViewPolicy() {}
    public static JsonObject current(JsonObject state){
        if(!state.has("active")||!state.has("actors"))return null;
        for(var value:state.getAsJsonArray("actors")){var a=value.getAsJsonObject();if(a.get("id").getAsString().equals(state.get("active").getAsString()))return a;}
        return null;
    }
    public static boolean participant(JsonObject state,String uuid){
        return state.has("state")&&!state.get("state").getAsString().equals("ENDED")&&state.has("actors")&&state.getAsJsonArray("actors").asList().stream().anyMatch(a->a.getAsJsonObject().get("id").getAsString().equals(uuid));
    }
    public static String permissions(JsonObject state){
        if(!state.has("actors"))return "";
        return state.getAsJsonArray("actors").asList().stream().map(a->a.getAsJsonObject().get("id")+":"+a.getAsJsonObject().get("controlled")).sorted().collect(java.util.stream.Collectors.joining(";"));
    }
    public static boolean canControl(JsonObject state){var a=current(state);return state.has("state")&&state.get("state").getAsString().equals("ACTIVE")&&a!=null&&a.has("controlled")&&a.get("controlled").getAsBoolean();}
    public static boolean cameraEligible(JsonObject state,String dimension,boolean tactical){return !tactical&&current(state)!=null&&state.has("state")&&!state.get("state").getAsString().equals("ENDED")&&state.has("dimension")&&state.get("dimension").getAsString().equals(dimension);}
}
