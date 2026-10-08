package dev.tacticalcombat.net;

import net.minecraft.network.PacketByteBuf;
import java.util.UUID;

/** v2 combat commands are tied to a turn, never inferred from a delayed packet's arrival time. */
public record RequestStamp(UUID encounter, long turn, UUID request) {
    public static RequestStamp read(PacketByteBuf buf) {
        return new RequestStamp(buf.readUuid(), buf.readVarLong(), buf.readUuid());
    }
    public void write(PacketByteBuf buf) {
        buf.writeUuid(encounter); buf.writeVarLong(turn); buf.writeUuid(request);
    }
}
