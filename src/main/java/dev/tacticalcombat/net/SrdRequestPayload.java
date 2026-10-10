package dev.tacticalcombat.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Versioned SRD intent only. No client dice, HP, costs, approval or derived combat values. */
public record SrdRequestPayload(UUID encounter,long turn,UUID request,String operation,String json) implements CustomPayload {
    public static final int LIMIT=4096;
    public static final Id<SrdRequestPayload> ID=new Id<>(Identifier.of("tacticalcombat","srd_request_v1"));
    public static final PacketCodec<RegistryByteBuf,SrdRequestPayload> CODEC=new PacketCodec<>() {
        public SrdRequestPayload decode(RegistryByteBuf b) { return new SrdRequestPayload(b.readUuid(),b.readLong(),b.readUuid(),b.readString(16),b.readString(LIMIT)); }
        public void encode(RegistryByteBuf b,SrdRequestPayload p) { b.writeUuid(p.encounter); b.writeLong(p.turn); b.writeUuid(p.request); b.writeString(p.operation,16); b.writeString(p.json,LIMIT); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
