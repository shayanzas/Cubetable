package dev.tacticalcombat.net;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
public record UiRequestPayload(long sequence,String operation,String json) implements CustomPayload {
    public static final int LIMIT=49152;
    public static final Id<UiRequestPayload> ID=new Id<>(Identifier.of("tacticalcombat","ui_request_v1"));
    public static final PacketCodec<RegistryByteBuf,UiRequestPayload> CODEC=new PacketCodec<>() {
        public UiRequestPayload decode(RegistryByteBuf b) {long s=b.readLong();if(s<1) throw new IllegalArgumentException("Invalid sequence");return new UiRequestPayload(s,b.readString(32),b.readString(LIMIT));}
        public void encode(RegistryByteBuf b,UiRequestPayload p) {b.writeLong(p.sequence);b.writeString(p.operation,32);b.writeString(p.json,LIMIT);}
    };
    public Id<? extends CustomPayload> getId() {return ID;}
}
