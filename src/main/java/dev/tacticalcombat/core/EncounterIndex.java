package dev.tacticalcombat.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread ownership. Release is conditional so a stale owner cannot evict a new one. */
public final class EncounterIndex<T> {
    private final Map<UUID, T> owners = new HashMap<>();
    public T get(UUID actor) { return owners.get(actor); }
    public boolean claim(UUID actor, T encounter) { return owners.putIfAbsent(actor, encounter) == null; }
    public void release(UUID actor, T encounter) { owners.remove(actor, encounter); }
    public void clear() { owners.clear(); }
}
