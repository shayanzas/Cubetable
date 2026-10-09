package dev.tacticalcombat.character;

import java.util.*;
import dev.tacticalcombat.core.RateLimit;

/** Bounded per-connected-player replay state. Old sequences never become admissible again. */
public final class CharacterSessions {
    private final Map<UUID,Long> sequences=new HashMap<>();
    private final RateLimit rate=new RateLimit(4,20);
    public String admit(UUID player,long sequence,long tick) {
        if(sequence<1) return "REJECTED";
        if(!rate.allow(player,tick)) return "RATE_LIMIT";
        if(sequence<=sequences.getOrDefault(player,0L)) return "ALREADY_PROCESSED";
        sequences.put(player,sequence); return "OK";
    }
    public void forget(UUID id) { sequences.remove(id); rate.forget(id); }
    public void clear() { sequences.clear(); rate.clear(); }
}
