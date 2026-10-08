package dev.tacticalcombat.card;

import dev.tacticalcombat.net.ShareCardPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/** Server side of "show": checks the size, puts the sender's real name on it and relays it to nearby players with the mod. */
public final class CardService {
	private static final dev.tacticalcombat.core.RateLimit LIMIT = new dev.tacticalcombat.core.RateLimit(1, 20);
	private static final AtomicLong NEXT_ID = new AtomicLong(System.currentTimeMillis() % 1_000_000L * 1000L);
	public static void clear() { LIMIT.clear(); }

	private CardService() {}

	public static void share(ServerPlayerEntity player, ShareCard card) {
        if (!LIMIT.allow(player.getUuid(), player.getServer().getOverworld().getTime())) return;

		ShareCardPayload payload = new ShareCardPayload(NEXT_ID.incrementAndGet(), player.getGameProfile().getName(), card.cleaned());
		for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
			if (p.getWorld() == player.getWorld() && p.squaredDistanceTo(player) <= 64 * 64 && ServerPlayNetworking.canSend(p, ShareCardPayload.ID)) ServerPlayNetworking.send(p, payload);
		}
	}

	public static void forget(UUID player) {
		LIMIT.forget(player);
	}
}
