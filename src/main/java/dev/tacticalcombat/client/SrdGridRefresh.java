package dev.tacticalcombat.client;

import java.util.UUID;

/** Bounded discovery scheduler. Never retries a mutating operation or a rejected grid at the same revision. */
public final class SrdGridRefresh {
    private UUID encounter;
    private long revision=-1,turn=-1,next;
    public boolean due(UUID encounter,long revision,long turn,boolean eligible,long tick) {
        return eligible && encounter!=null && tick>=next
                && (!encounter.equals(this.encounter)||revision!=this.revision||turn!=this.turn);
    }
    public void requested(UUID encounter,long revision,long turn,long tick) {
        this.encounter=encounter;this.revision=revision;this.turn=turn;next=tick+22;
    }
    public void clear(){encounter=null;revision=turn=-1;next=0;}
}
