package dev.tacticalcombat.combat;

/** SRD normal-action accounting and weapon-action reservation. Tactical's movement/action semantics are intentionally independent. */
public final class SrdActionBudget {
    public enum Action { AVAILABLE, RESERVED, SPENT }
    private Action action=Action.AVAILABLE;
    public Action action(){return action;}
    public boolean used(){return action!=Action.AVAILABLE;}
    public void reserve(){if(used())throw new IllegalArgumentException("Action already reserved or spent");action=Action.RESERVED;}
    public void committed(){if(action!=Action.RESERVED)throw new IllegalArgumentException("No reserved action");action=Action.SPENT;}
    /** Immediate turn-local actions have no persistent resource transaction. */
    public void spend(){if(used())throw new IllegalArgumentException("Action already reserved or spent");action=Action.SPENT;}
    public int supportedBonusActions(){return 0;}
    public int supportedReactions(){return 0;}
    public int supportedInteractions(){return 0;}
    // A new budget is created only by the server's beginTurn. Movement stays in SrdMovementTurn.
}
