package dev.tacticalcombat.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Connection-scoped monotonic sequence prevents replay even after an acknowledgement is lost. */
public record CharacterRequestPayload(long sequence,String operation,String json) implements CustomPayload {
    public static final int LIMIT=65536;
    public static final Id<CharacterRequestPayload> ID=new Id<>(Identifier.of("tacticalcombat","character_request_v2"));
    public static final PacketCodec<RegistryByteBuf,CharacterRequestPayload> CODEC=new PacketCodec<>() {
        public CharacterRequestPayload decode(RegistryByteBuf b) {
            long sequence=b.readLong(); if(sequence<1) throw new IllegalArgumentException("Invalid sequence");
            return new CharacterRequestPayload(sequence,b.readString(16),b.readString(LIMIT));
        }
        public void encode(RegistryByteBuf b,CharacterRequestPayload p) { b.writeLong(p.sequence); b.writeString(p.operation,16); b.writeString(p.json,LIMIT); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
