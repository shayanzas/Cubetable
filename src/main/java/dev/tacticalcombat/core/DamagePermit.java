package dev.tacticalcombat.core;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Accepted Minecraft Tactical action. One effect per victim; multi-victim effects share one cost. */
public final class DamagePermit {
    public final UUID action = UUID.randomUUID();
    public final UUID encounter;
    public final UUID actor;
    private final Set<UUID> victims = new HashSet<>();
    public DamagePermit(UUID encounter, UUID actor) { this.encounter = encounter; this.actor = actor; }
    public boolean admit(UUID encounter, UUID victim) {
        return this.encounter.equals(encounter) && victims.size() < 128 && victims.add(victim);
    }
}
