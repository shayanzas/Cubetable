package dev.tacticalcombat.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Opaque open-screen token plus revocation bit; no private revisions, IDs, HP or rules values. */
public record RulesChangedPayload(UUID token,boolean revoked) implements CustomPayload {
    public static final Id<RulesChangedPayload> ID=new Id<>(Identifier.of("tacticalcombat","rules_changed_v1"));
    public static final PacketCodec<RegistryByteBuf,RulesChangedPayload> CODEC=new PacketCodec<>(){
        public RulesChangedPayload decode(RegistryByteBuf b){return new RulesChangedPayload(b.readUuid(),b.readBoolean());}
        public void encode(RegistryByteBuf b,RulesChangedPayload p){b.writeUuid(p.token);b.writeBoolean(p.revoked);}
    };
    public Id<? extends CustomPayload> getId(){return ID;}
}
