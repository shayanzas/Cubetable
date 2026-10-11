package dev.tacticalcombat.combat;
import java.util.*;
import net.minecraft.network.packet.CustomPayload;
/** Development-only, explicitly scoped capture; never retains packets outside a test. */
public final class PacketProbe {
    public static boolean enabled;
    public static final List<CustomPayload> packets=new ArrayList<>();
    public static final List<Long> times=new ArrayList<>();
    public static void record(CustomPayload payload) {if(enabled && packets.size()<256) {packets.add(payload);times.add(System.nanoTime());}}
    public static void clear() {enabled=false;packets.clear();times.clear();}
}
