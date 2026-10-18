package dev.tacticalcombat.client;
import com.google.gson.*;
import java.util.UUID;
/** Correlates a deliberate preview request; cancelled, switched and historical replies cannot reopen a popup. */
public final class SrdPreviewGate {
    private UUID request;private String encounter,actor,target;private long turn;
    public void clear(){request=null;}
    public void expect(UUID request,String encounter,long turn,String actor,String target){this.request=request;this.encounter=encounter;this.turn=turn;this.actor=actor;this.target=target;}
    public boolean accepts(String encounter,long turn,String actor,String request,JsonObject preview){
        return this.request!=null&&this.request.toString().equals(request)&&this.encounter.equals(encounter)&&this.turn==turn&&this.actor.equals(actor)
            &&preview.has("actor")&&preview.get("actor").getAsString().equals(actor)&&preview.get("target").getAsString().equals(target)&&preview.get("turn").getAsLong()==turn;
    }
}
