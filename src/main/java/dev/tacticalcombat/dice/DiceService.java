package dev.tacticalcombat.dice;

import dev.tacticalcombat.net.DiceRollPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Random;

/** Server side of every dice roll: the server picks the numbers, then encounter peers or nearby players see the animation. */
public final class DiceService {
	private static final dev.tacticalcombat.core.RateLimit LIMIT = new dev.tacticalcombat.core.RateLimit(4, 80);
    public static void forget(java.util.UUID id) { LIMIT.forget(id); }
    public static void clear() { LIMIT.clear(); }
    private static final Random RANDOM = new Random();

	private DiceService() {}

	/** mode: 0 normal, 1 advantage, 2 disadvantage (only meaningful for a single d20). */
	public static void roll(ServerPlayerEntity player, String label, DiceType type, int count, int modifier, int mode) {
		if (!LIMIT.allow(player.getUuid(), player.getServer().getOverworld().getTime())) return;
        count = Math.max(1, Math.min(DiceSpec.MAX_COUNT, count));
		modifier = Math.max(-999, Math.min(999, modifier));
		int keep = 0;
		if (type == DiceType.D20 && count == 1 && (mode == 1 || mode == 2)) {
			count = 2;
			keep = mode;
		}
		int[] results = new int[count];
		for (int i = 0; i < count; i++) {
			results[i] = type.roll(RANDOM);
		}
		if (label == null) label = "";
		if (label.length() > 48) label = label.substring(0, 48);

		DiceRollPayload payload = new DiceRollPayload(player.getGameProfile().getName(), label, type, modifier, keep, results);
		var recipients=new java.util.HashSet<java.util.UUID>();
        for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
			if (p.getWorld() == player.getWorld()
                    && (p == player || (dev.tacticalcombat.combat.CombatManager.encounter(player) != null
                        ? dev.tacticalcombat.combat.CombatManager.encounter(p) == dev.tacticalcombat.combat.CombatManager.encounter(player)
                        : p.squaredDistanceTo(player) <= 64 * 64))
                    && ServerPlayNetworking.canSend(p, DiceRollPayload.ID)) {ServerPlayNetworking.send(p, payload);recipients.add(p.getUuid());}
        }
        try {
            var campaign=dev.tacticalcombat.character.CharacterService.context(player).campaign();
            if(campaign!=null && dev.tacticalcombat.character.CharacterService.repository().member(dev.tacticalcombat.character.CharacterService.principal(player),campaign)) {
                var encounter=dev.tacticalcombat.combat.CombatManager.encounter(player);
                var roll=dev.tacticalcombat.uiapi.UiEvents.Roll.from(type.sides,java.util.Arrays.stream(results).boxed().toList(),keep,modifier,label,false);
                dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(java.util.UUID.randomUUID(),campaign,encounter==null?null:encounter.id(),0,dev.tacticalcombat.uiapi.UiEvents.Kind.DICE,java.util.List.of(player.getUuid()),"Manual dice roll",roll,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,recipients);
            }
        } catch(RuntimeException presentation) {dev.tacticalcombat.TacticalCombatMod.LOGGER.warn("Could not record manual roll presentation",presentation);}
    }
}
