package dev.tacticalcombat.net;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Draft submission only. Campaign, ownership, approval and binding never come from this packet. */
public record SubmitCharacterPayload(java.util.UUID request,String sheet) implements CustomPayload {
    public static final Id<SubmitCharacterPayload> ID=new Id<>(Identifier.of("tacticalcombat","submit_character_v1"));
    public static final PacketCodec<RegistryByteBuf,SubmitCharacterPayload> CODEC=new PacketCodec<>() {
        public SubmitCharacterPayload decode(RegistryByteBuf b) { return new SubmitCharacterPayload(b.readUuid(),b.readString(32768)); }
        public void encode(RegistryByteBuf b,SubmitCharacterPayload p) { b.writeUuid(p.request); b.writeString(p.sheet,32768); }
    };
    public Id<? extends CustomPayload> getId() { return ID; }
}
