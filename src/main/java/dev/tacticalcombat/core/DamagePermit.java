package dev.tacticalcombat.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-issued action with bounded, distinct effects. Clients never choose effect identities. */
public final class DamagePermit {
    public static final int MAX_EFFECTS = 64;
    public final UUID action = UUID.randomUUID();
    public final UUID encounter;
    public final UUID actor;
    private record Hit(UUID effect, UUID victim, boolean retaliation) {}
    private final Set<UUID> effects = new HashSet<>();
    private final Set<Hit> hits = new HashSet<>();
    public DamagePermit(UUID encounter, UUID actor) {
        this.encounter = encounter; this.actor = actor; effects.add(action);
    }
    public UUID newEffect() {
        if (effects.size() >= MAX_EFFECTS) return null;
        UUID id = UUID.randomUUID(); effects.add(id); return id;
    }
    public boolean admit(UUID encounter, UUID victim) { return admit(encounter, action, victim, false); }
    public boolean admit(UUID encounter, UUID effect, UUID victim, boolean retaliation) {
        return this.encounter.equals(encounter) && effects.contains(effect) && hits.size() < 8192
                && hits.add(new Hit(effect, victim, retaliation));
    }
}
