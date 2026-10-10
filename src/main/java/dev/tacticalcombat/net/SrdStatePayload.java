package dev.tacticalcombat.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

/** Private viewer-specific replies/snapshots. Grid publication is separately bounded to 4096 cells. */
public record SrdStatePayload(UUID encounter,long revision,String code,String message,String json) implements CustomPayload {
    public static final int LIMIT=262144;
    public static final Id<SrdStatePayload> ID=new Id<>(Identifier.of("tacticalcombat","srd_state_v1"));
    public static final PacketCodec<RegistryByteBuf,SrdStatePayload> CODEC=new PacketCodec<>() {
        public SrdStatePayload decode(RegistryByteBuf b) { return new SrdStatePayload(b.readUuid(),b.readLong(),b.readString(24),b.readString(256),b.readString(LIMIT)); }
        public void encode(RegistryByteBuf b,SrdStatePayload p) { b.writeUuid(p.encounter); b.writeLong(p.revision); b.writeString(p.code,24); b.writeString(p.message,256); b.writeString(p.json,LIMIT); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
