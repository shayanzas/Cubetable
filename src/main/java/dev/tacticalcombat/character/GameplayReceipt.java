package dev.tacticalcombat.character;

import java.util.*;

/** Retained permanently within the bounded store; cancellation is an audited terminal status. */
public record GameplayReceipt(int version,GameplayActionPlan plan,Status status,UUID resolvedBy,String resolution) {
    public enum Status { PREPARED, COMMITTED, CANCELLED }
    public GameplayReceipt {
        if(version!=1)throw new UnsupportedOperationException("Unknown gameplay receipt version");
        Objects.requireNonNull(plan);Objects.requireNonNull(status);Objects.requireNonNull(resolution);
        if(resolution.length()>240||status==Status.CANCELLED&&(resolvedBy==null||resolution.strip().length()<5)||status!=Status.CANCELLED&&(resolvedBy!=null||!resolution.isEmpty()))throw new IllegalArgumentException("Invalid receipt resolution");
    }
    public static GameplayReceipt prepared(GameplayActionPlan plan){return new GameplayReceipt(1,plan,Status.PREPARED,null,"");}
    public GameplayReceipt committed(){return new GameplayReceipt(1,plan,Status.COMMITTED,null,"");}
    public GameplayReceipt cancelled(UUID manager,String reason){return new GameplayReceipt(1,plan,Status.CANCELLED,manager,reason);}
}
