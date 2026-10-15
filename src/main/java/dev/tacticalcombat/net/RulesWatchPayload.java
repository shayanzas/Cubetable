package dev.tacticalcombat.net;
import java.util.UUID;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
/** Read-only subscription. Never carries a calculated statistic or mutation. */
public record RulesWatchPayload(UUID token,boolean library,UUID character,String observed,boolean close) implements CustomPayload {
    public static final Id<RulesWatchPayload> ID=new Id<>(Identifier.of("tacticalcombat","rules_watch_v1"));
    public static final PacketCodec<RegistryByteBuf,RulesWatchPayload> CODEC=new PacketCodec<>(){
        public RulesWatchPayload decode(RegistryByteBuf b){return new RulesWatchPayload(b.readUuid(),b.readBoolean(),b.readUuid(),b.readString(256),b.readBoolean());}
        public void encode(RegistryByteBuf b,RulesWatchPayload p){b.writeUuid(p.token);b.writeBoolean(p.library);b.writeUuid(p.character);b.writeString(p.observed,256);b.writeBoolean(p.close);}
    };
    public Id<? extends CustomPayload> getId(){return ID;}
}
