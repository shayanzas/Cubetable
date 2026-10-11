package dev.tacticalcombat.net;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record UiReplyPayload(long sequence,String code,String message,String json) implements CustomPayload {
    public static final int LIMIT=65536;
    public static final Id<UiReplyPayload> ID=new Id<>(Identifier.of("tacticalcombat","ui_reply_v1"));
    public static final PacketCodec<RegistryByteBuf,UiReplyPayload> CODEC=new PacketCodec<>() {
        public UiReplyPayload decode(RegistryByteBuf b) {return new UiReplyPayload(b.readLong(),b.readString(24),b.readString(256),b.readString(LIMIT));}
        public void encode(RegistryByteBuf b,UiReplyPayload p) {b.writeLong(p.sequence);b.writeString(p.code,24);b.writeString(p.message,256);b.writeString(p.json,LIMIT);}
    };
    public Id<? extends CustomPayload> getId() {return ID;}
}
