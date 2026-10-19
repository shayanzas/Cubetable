package dev.tacticalcombat.client;

import com.google.gson.*;
import java.util.*;

/** A deliberate review is single-use and belongs to the exact authorized snapshot. No HP arithmetic. */
public final class SrdRecoveryReview {
    public record Scope(UUID encounter,long revision,boolean manager,String state) {}
    private Scope scope;private String operation,target;private int amount;
    public static boolean mayHeal(JsonObject state){return state.has("manager")&&state.get("manager").getAsBoolean()&&state.has("state")&&state.get("state").getAsString().equals("PAUSED")&&state.has("recovery")&&state.getAsJsonObject("recovery").get("canHeal").getAsBoolean();}
    public static int amount(String text){try{int value=Integer.parseInt(text);return value>=1&&value<=1024?value:0;}catch(NumberFormatException invalid){return 0;}}
    public void prepare(Scope scope,String operation,String target,int amount){
        if(scope.encounter()==null||!scope.manager()||!scope.state().equals("PAUSED")||!Set.of("heal","retry","end").contains(operation))throw new IllegalArgumentException("Current paused manager view required");
        if(operation.equals("heal")&&(target==null||amount<1||amount>1024))throw new IllegalArgumentException("Choose an actor and amount 1..1024");
        this.scope=scope;this.operation=operation;this.target=target;this.amount=amount;
    }
    public boolean current(Scope now){return scope!=null&&scope.equals(now);}
    public String operation(){return operation;}
    public JsonObject consume(Scope now,boolean pending){
        if(pending||!current(now))return null;
        var args=new JsonObject();if(operation.equals("heal")){args.addProperty("target",target);args.addProperty("amount",amount);args.addProperty("revision",scope.revision());}clear();return args;
    }
    public void clear(){scope=null;operation=target=null;amount=0;}
}
