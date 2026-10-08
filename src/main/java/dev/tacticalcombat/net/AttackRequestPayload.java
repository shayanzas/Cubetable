package dev.tacticalcombat.net;

import dev.tacticalcombat.TacticalCombatMod;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: "use my action to melee this enemy". */
public record AttackRequestPayload(int entityId, RequestStamp stamp) implements CustomPayload {
	public static final Id<AttackRequestPayload> ID =
			new Id<>(Identifier.of(TacticalCombatMod.MOD_ID, "attack_request_v2"));

	public static final PacketCodec<RegistryByteBuf, AttackRequestPayload> CODEC = new PacketCodec<>() {
		@Override
		public AttackRequestPayload decode(RegistryByteBuf buf) {
			return new AttackRequestPayload(buf.readVarInt(), RequestStamp.read(buf));
		}

		@Override
		public void encode(RegistryByteBuf buf, AttackRequestPayload p) {
			buf.writeVarInt(p.entityId);
            p.stamp.write(buf);
		}
	};

	@Override
	public Id<? extends CustomPayload> getId() {
		return ID;
	}
}
