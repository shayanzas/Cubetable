package dev.tacticalcombat.combat;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Keeps track of every running {@link Combat}, starts new ones and exposes the rule checks used by the event hooks. */
public final class CombatManager {
	private static final List<Encounter> COMBATS = new ArrayList<>();
	private static final dev.tacticalcombat.core.EncounterIndex<Encounter> OWNERS = new dev.tacticalcombat.core.EncounterIndex<>();

	private CombatManager() {}

	// ---------------------------------------------------------------- lookup

	/** Tactical-only action adapter lookup. Never use this to test absence of encounter ownership. */
	public static Combat get(Entity entity) {
        return encounter(entity) instanceof Combat tactical ? tactical : null;
	}

    public static Encounter encounter(Entity entity) { return entity == null ? null : OWNERS.get(entity.getUuid()); }
    public static boolean blocksVanilla(Entity entity) {
        Encounter owner=encounter(entity);
        return owner!=null && !(owner instanceof Combat);
    }
    static boolean claim(Entity actor, Encounter encounter) { return OWNERS.claim(actor.getUuid(), encounter); }
    static void release(Entity actor, Encounter encounter) { OWNERS.release(actor.getUuid(), encounter); }
    static void schedule(Encounter encounter) { if(!COMBATS.contains(encounter)) COMBATS.add(encounter); }

	public static boolean isInCombat(java.util.UUID actor) { return OWNERS.get(actor) != null; }

	public static boolean isInCombat(Entity entity) {
		return encounter(entity) != null;
	}

	public static boolean isEligibleEnemy(MobEntity m) {
		return m instanceof Monster
				&& m.isAlive()
				&& !(m instanceof EnderDragonEntity)
				&& !(m instanceof WitherEntity)
                // These attacks need non-projectile or sonic effect adapters; keep them in vanilla world combat.
                && !(m instanceof net.minecraft.entity.mob.EvokerEntity)
                && !(m instanceof net.minecraft.entity.mob.WardenEntity);
	}

	// ---------------------------------------------------------------- tick / detection

	public static void tick(MinecraftServer server) {
        if(server.getTicks()%20==0) dev.tacticalcombat.character.CharacterService.capture(server);
		CombatActions.tick(server.getOverworld().getTime());
		Iterator<Encounter> it = COMBATS.iterator();
		while (it.hasNext()) {
			Encounter combat = it.next();
			if (combat.tick()) {
				combat.end();
				it.remove();
			}
		}

		if (server.getTicks() % 5 == 0) {
			detect(server);
		}
	}

	/** A hostile mob that has locked onto a player starts a fight. */
	private static void detect(MinecraftServer server) {
		for (ServerWorld world : server.getWorlds()) {
			for (ServerPlayerEntity player : new ArrayList<>(world.getPlayers())) {
				if (!canFight(player) || isInCombat(player)) continue;

				Box box = player.getBoundingBox().expand(
						CombatConfig.DETECT_RANGE, CombatConfig.JOIN_VERTICAL, CombatConfig.DETECT_RANGE);
				boolean triggered = !world.getEntitiesByClass(MobEntity.class, box, m ->
						isEligibleEnemy(m) && !isInCombat(m) && m.getTarget() == player
								&& isNear(m, player, CombatConfig.DETECT_RANGE, CombatConfig.JOIN_VERTICAL)).isEmpty();
				if (!triggered) continue;

				startAround(world, player, true);
			}
		}
	}

	/**
	 * True if {@code a} and {@code b} are in the same area: within {@code horizontal} blocks on the ground
	 * and within {@code vertical} blocks of each other in height (so a cave far below does not count).
	 */
	public static boolean isNear(Entity a, Entity b, double horizontal, double vertical) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz <= horizontal * horizontal && Math.abs(a.getY() - b.getY()) <= vertical;
	}

	static boolean canFight(ServerPlayerEntity p) {
		return dev.tacticalcombat.character.CharacterService.available() && p.isAlive() && !p.isSpectator()
                && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(p,
                        dev.tacticalcombat.net.CombatStatePayload.ID);
	}

	/**
	 * Starts a combat around {@code center}.
	 * @param onlyAggroed only take hostiles that currently target a player (automatic start); the command passes false
	 * @return the new combat, or null if there was nobody to fight
	 */
	public static Combat startAround(ServerWorld world, ServerPlayerEntity center, boolean onlyAggroed) {
        var context=dev.tacticalcombat.character.CharacterService.context(center);
        return startAround(world,center,onlyAggroed,context);
    }
    public static Combat startAround(ServerWorld world, ServerPlayerEntity center, boolean onlyAggroed,
            dev.tacticalcombat.character.CharacterService.Context context) {
        var rules=dev.tacticalcombat.rules.RulesProfiles.get(context.profile());
        if(!rules.worldCombat() || !dev.tacticalcombat.character.CharacterService.mayJoin(center,context.campaign(),rules)) return null;
		if (!canFight(center) || center.getWorld() != world || isInCombat(center)) return null;
        Box area = center.getBoundingBox().expand(
				CombatConfig.JOIN_RANGE, CombatConfig.JOIN_VERTICAL, CombatConfig.JOIN_RANGE);

		List<MobEntity> enemies = world.getEntitiesByClass(MobEntity.class, area, m ->
				isEligibleEnemy(m) && !isInCombat(m)
						&& (!onlyAggroed || m.getTarget() instanceof PlayerEntity)
						&& isNear(m, center, CombatConfig.JOIN_RANGE, CombatConfig.JOIN_VERTICAL));
		if (enemies.isEmpty()) return null;

		Set<ServerPlayerEntity> party = new LinkedHashSet<>();
		party.add(center);
		party.addAll(world.getPlayers(p -> canFight(p) && !isInCombat(p)
				&& isNear(p, center, CombatConfig.JOIN_RANGE, CombatConfig.JOIN_VERTICAL)));

		Combat combat = Combat.start(world, party, enemies,context);
		if (combat != null) COMBATS.add(combat);
		return combat;
	}

	public static void endCombat(Encounter combat) {
		combat.end();
		COMBATS.remove(combat);
	}

	public static void clear() {
		for (Encounter c : COMBATS) c.end();
		COMBATS.clear();
        OWNERS.clear();
        CombatActions.clear();
        CombatInteractions.clear();
	}

	// ---------------------------------------------------------------- rule hooks

	/** ALLOW_DAMAGE hook: gate attacks of combatants by turn and action economy. */
	public static boolean allowDamage(LivingEntity victim, DamageSource source, float amount) {
        return CombatActions.allowDamage(victim, source);
    }

	/** World interactions require explicit action adapters while enrolled in combat. */
	public static boolean canActNow(PlayerEntity player) {
		return !isInCombat(player); // world interactions during combat need explicit accepted-action adapters
	}

	public static void notifyNotYourTurn(PlayerEntity player) {
		if (player instanceof ServerPlayerEntity p) {
			p.sendMessage(Text.translatable("tacticalcombat.msg.not_your_turn"), true);
		}
	}

	/** A player clicked a grid square to walk to. */
	public static void requestMove(ServerPlayerEntity player, BlockPos target, dev.tacticalcombat.net.RequestStamp stamp) {
		Combat combat = get(player);
		if (combat != null && combat.acceptRequest(player, stamp)) combat.requestMove(player, target);
	}

	/** A player clicked an enemy to attack it. */
	public static void requestAttack(ServerPlayerEntity player, int entityId, dev.tacticalcombat.net.RequestStamp stamp) {
		Combat combat = get(player);
		if (combat != null && combat.acceptRequest(player, stamp)) combat.requestAttack(player, entityId);
	}

	/** Called when a player presses the End Turn key or runs /tbc endturn. */
	public static void requestEndTurn(ServerPlayerEntity player, dev.tacticalcombat.net.RequestStamp stamp) {
		Combat combat = get(player);
		if (combat != null && combat.acceptRequest(player, stamp)) {
			combat.endTurn();
		}
	}
}
