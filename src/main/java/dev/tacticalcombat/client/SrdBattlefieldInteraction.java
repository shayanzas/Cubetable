package dev.tacticalcombat.client;

import dev.tacticalcombat.rules.srd.SrdMovement;
import java.util.Objects;

/** One contextual selection. No networking, rules calculation or implicit execution on reconciliation. */
public final class SrdBattlefieldInteraction {
    public record Scope(String encounter,long turn,String actor,long revision,String permissions) {}
    public enum Kind { IDLE, MOVE, ACTOR }
    private Scope scope;
    private Kind kind=Kind.IDLE;
    private SrdMovement.Cell destination;
    private String target="";
    private boolean consumed;
    private int anchorX,anchorY;
    public Kind kind(){return kind;}
    public String target(){return target;}
    public SrdMovement.Cell destination(){return destination;}
    public int x(){return anchorX;}
    public int y(){return anchorY;}
    public void clear(){kind=Kind.IDLE;destination=null;target="";consumed=false;}
    public void reconcile(Scope next){
        if(scope!=null&&(!scope.encounter.equals(next.encounter)||scope.turn!=next.turn||!scope.actor.equals(next.actor)||!scope.permissions.equals(next.permissions))){clear();}
        else if(scope!=null&&scope.revision!=next.revision&&kind==Kind.MOVE)clear();
        scope=next;
    }
    public boolean ground(Scope current,SrdMovement.Cell cell,SrdMovementPreview.Selection selected,boolean ready,int x,int y){
        reconcile(current);if(!ready||cell==null||selected.status()!=SrdMovement.Reason.OK||selected.costFeet().orElse(0)<=0)return false;
        clear();kind=Kind.MOVE;destination=cell;anchorX=x;anchorY=y;return true;
    }
    /** Return true only on deliberate input when exactly one choice may be previewed. */
    public boolean actor(Scope current,String id,int choices,boolean ready,int x,int y){
        reconcile(current);clear();kind=Kind.ACTOR;target=id;anchorX=x;anchorY=y;return ready&&choices==1;
    }
    public boolean consumeMove(Scope current,boolean ready){
        if(!ready||consumed||kind!=Kind.MOVE||!Objects.equals(scope,current))return false;
        consumed=true;return true;
    }
    public boolean matchesActor(String id){return kind==Kind.ACTOR&&target.equals(id);}
}
