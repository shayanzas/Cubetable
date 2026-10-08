package dev.tacticalcombat.core;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A bounded burst per monotonic server tick window; call forget on disconnect and clear on stop. */
public final class RateLimit {
    private record Window(long start, int count) {}
    private final Map<UUID, Window> windows = new HashMap<>();
    private final int maximum;
    private final long ticks;
    public RateLimit(int maximum, long ticks) {
        if (maximum < 1 || ticks < 1) throw new IllegalArgumentException("Invalid rate limit");
        this.maximum = maximum; this.ticks = ticks;
    }
    public boolean allow(UUID actor, long now) {
        Window w = windows.get(actor);
        if (w == null || now < w.start || now - w.start >= ticks) {
            windows.put(actor, new Window(now, 1)); return true;
        }
        if (w.count >= maximum) return false;
        windows.put(actor, new Window(w.start, w.count + 1)); return true;
    }
    public void forget(UUID actor) { windows.remove(actor); }
    public void clear() { windows.clear(); }
}
