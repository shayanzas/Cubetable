package dev.tacticalcombat.combat;

/** Encounter-local resource; refreshed only at this participant's next turn start. */
public final class SrdReactionBudget {
    public enum State { AVAILABLE, RESERVED, SPENT }
    private State state=State.AVAILABLE;
    public State state(){return state;}
    public boolean available(){return state==State.AVAILABLE;}
    public void reserve(){if(!available())throw new IllegalArgumentException("Reaction unavailable");state=State.RESERVED;}
    public void commit(){if(state!=State.RESERVED)throw new IllegalArgumentException("No reaction reservation");state=State.SPENT;}
    public void refresh(){if(state==State.RESERVED)throw new IllegalArgumentException("Unresolved reaction reservation");state=State.AVAILABLE;}
    public static boolean leavesReach(double before,double after,double reach){return before<=reach+1e-6&&after>reach+1e-6;}
}
