package dev.tacticalcombat.client.ui;

/** Owns exactly one acquired clip; nesting and exceptional exits remain balanced. */
public final class ClipScope implements AutoCloseable {
    private final Runnable release;
    private boolean closed;
    private ClipScope(Runnable release) { this.release=release; }
    public static ClipScope open(Runnable acquire,Runnable release) {
        acquire.run();
        return new ClipScope(release);
    }
    @Override public void close() { if(!closed) {closed=true;release.run();} }
}
