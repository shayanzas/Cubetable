package dev.tacticalcombat.net;

import dev.tacticalcombat.TacticalCombatMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "I am done with my turn". */
public record EndTurnPayload(RequestStamp stamp) implements CustomPayload {

	public static final Id<EndTurnPayload> ID =
			new Id<>(Identifier.of(TacticalCombatMod.MOD_ID, "end_turn_v2"));

	public static final PacketCodec<RegistryByteBuf, EndTurnPayload> CODEC = new PacketCodec<>() {
        public EndTurnPayload decode(RegistryByteBuf buf) { return new EndTurnPayload(RequestStamp.read(buf)); }
        public void encode(RegistryByteBuf buf, EndTurnPayload p) { p.stamp.write(buf); }
    };

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
