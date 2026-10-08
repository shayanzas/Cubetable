package dev.tacticalcombat.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Bounded deduplication of requests, scoped to an encounter and a specific turn. */
public final class TurnRequests {
    public static final int MAX_REQUESTS = 128;
    private final UUID encounter;
    private long turn;
    private final Set<UUID> seen = new HashSet<>();
    public TurnRequests(UUID encounter) { this.encounter = encounter; }
    public long turn() { return turn; }
    public void nextTurn() { turn++; seen.clear(); }
    public boolean accept(UUID encounter, long turn, UUID request) {
        return this.encounter.equals(encounter) && this.turn == turn
                && seen.size() < MAX_REQUESTS && seen.add(request);
    }
}
