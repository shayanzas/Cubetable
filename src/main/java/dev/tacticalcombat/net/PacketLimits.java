package dev.tacticalcombat.net;

/** Shared encoder/decoder bounds. Malformed lengths are rejected, never clamped. */
public final class PacketLimits {
    public static final int PARTICIPANTS = 128;
    public static final int GRID_CELLS = 8192;
    public static final int THREAT_CELLS = 4000;
    private PacketLimits() {}
    public static int count(int value, int max) {
        if (value < 0 || value > max) throw new IllegalArgumentException("Invalid payload count: " + value);
        return value;
    }
}
