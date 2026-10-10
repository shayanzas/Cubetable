package dev.tacticalcombat.combat;

import dev.tacticalcombat.core.DamagePermit;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.ProjectileEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-thread action adapter for vanilla melee, spawned projectiles and creeper explosions. */
public final class CombatActions {
    private record Flight(DamagePermit permit, UUID effect, long expires) {}
    private static UUID resolvingEffect;
    private static boolean beamComponent;
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static final java.util.Set<UUID> IMPACTS = new java.util.HashSet<>();
    private static DamagePermit resolving;
    private CombatActions() {}

    public static boolean melee(LivingEntity actor, Entity target, Supplier<Boolean> effect) {
        if (CombatManager.blocksVanilla(actor) || CombatManager.blocksVanilla(target)) return false;
        Combat combat = CombatManager.get(actor);
        if (combat == null) return CombatManager.get(target) == null && effect.get();
        if (beamComponent && resolving != null && resolving.actor.equals(actor.getUuid())) {
            UUID component = resolving.newEffect();
            return component != null && inScope(resolving, component, effect);
        }
        if (!(target instanceof LivingEntity living) || !combat.legalMelee(actor, living)) return false;
        return resolve(actor, effect);
    }

    public static boolean resolve(LivingEntity actor, Supplier<Boolean> effect) {
        if (CombatManager.blocksVanilla(actor)) return false;
        Combat combat = CombatManager.get(actor);
        if (combat == null) return effect.get();
        if (resolving != null) return false; // no recursive additional action from a damage callback
        DamagePermit permit = combat.beginAction(actor);
        if (permit == null) return false;
        try { return inScope(permit, permit.action, effect); }
        finally { combat.sync(); }
    }

    public static boolean inScope(DamagePermit permit, UUID identity, Supplier<Boolean> effect) {
        DamagePermit previous = resolving;
        UUID previousEffect = resolvingEffect;
        resolving = permit; resolvingEffect = identity;
        try { return effect.get(); }
        finally { resolving = previous; resolvingEffect = previousEffect; }
    }

    /** One vanilla firing operation, including multishot, shares its accepted action. */
    public static boolean volley(LivingEntity actor, Supplier<Boolean> effect) {
        if (CombatManager.blocksVanilla(actor)) return false;
        if (resolving != null && resolving.actor.equals(actor.getUuid())) return effect.get();
        return resolve(actor, effect);
    }

    public static boolean beam(LivingEntity actor, LivingEntity target, Supplier<Boolean> effect) {
        if (CombatManager.blocksVanilla(actor) || CombatManager.blocksVanilla(target)) return false;
        Combat combat = CombatManager.get(actor);
        if (target == null) return false;
        if (combat == null) return CombatManager.get(target) == null && effect.get();
        if (!target.isAlive() || !combat.contains(target)
                || actor.squaredDistanceTo(target) > 32 * 32 || !CombatGeometry.clearAttack(actor, target)) return false;
        boolean previous = beamComponent;
        beamComponent = true;
        try { return resolve(actor, effect); }
        finally { beamComponent = previous; }
    }

    /** Called on entity load, after the projectile owner is set and before its first tick. */
    public static void projectileLoaded(Entity entity, long now) {
        if (!(entity instanceof ProjectileEntity projectile) || !(projectile.getOwner() instanceof LivingEntity actor)) return;
        if (CombatManager.blocksVanilla(actor)) { entity.discard(); return; }
        Combat combat = CombatManager.get(actor);
        if (combat == null) return;
        // A flight that was already authorized must not spend a second action on chunk reload.
        if (FLIGHTS.containsKey(entity.getUuid())) return;
        if (entity.getCommandTags().contains("tacticalcombat:accepted_projectile")) { entity.discard(); return; }
        if (FLIGHTS.size() >= 1024) { entity.discard(); return; }
        DamagePermit permit = resolving != null && resolving.actor.equals(actor.getUuid()) && resolving.encounter.equals(combat.id())
                ? resolving : combat.beginAction(actor);
        if (permit == null) { entity.discard(); return; }
        UUID effect = permit.newEffect();
        if (effect == null) { entity.discard(); return; }
        entity.addCommandTag("tacticalcombat:accepted_projectile");
        FLIGHTS.put(entity.getUuid(), new Flight(permit, effect, now + 200));
        combat.sync();
    }

    /** Splash effects need the same boundary checks as damage, including instant healing and conditions. */
    public static boolean canProjectileAffect(ProjectileEntity projectile, LivingEntity victim) {
        if (CombatManager.blocksVanilla(victim) || CombatManager.blocksVanilla(projectile.getOwner())) return false;
        Flight flight = FLIGHTS.get(projectile.getUuid());
        Combat victimCombat = CombatManager.get(victim);
        if (flight != null) return victimCombat != null && victimCombat.id().equals(flight.permit.encounter);
        if (projectile.getCommandTags().contains("tacticalcombat:accepted_projectile")) return false;
        Entity owner = projectile.getOwner();
        return victimCombat == null && (owner == null || CombatManager.get(owner) == null);
    }

    public static void arrowImpact(ProjectileEntity projectile, Entity target, Runnable original) {
        if (target instanceof LivingEntity living) {
            if (!canProjectileAffect(projectile, living)) return;
        } else if (projectile.getCommandTags().contains("tacticalcombat:accepted_projectile")
                || projectile.getOwner() != null && CombatManager.isInCombat(projectile.getOwner())) return;
        Flight flight = FLIGHTS.get(projectile.getUuid());
        if (flight == null) original.run();
        else inScope(flight.permit, flight.effect, () -> { original.run(); return true; });
    }

    /** One collision may apply several vanilla potion components, but cannot execute again. */
    public static void splashImpact(ProjectileEntity projectile, Runnable original) {
        Flight flight = FLIGHTS.get(projectile.getUuid());
        if (flight != null) {
            if (!IMPACTS.add(projectile.getUuid())) return;
            inScope(flight.permit, flight.effect, () -> { original.run(); return true; });
        } else if (!projectile.getCommandTags().contains("tacticalcombat:accepted_projectile")
                && (projectile.getOwner() == null || !CombatManager.isInCombat(projectile.getOwner()))) original.run();
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source) {
        // SRD HP never flows through vanilla damage, including environment, retaliation and old flights.
        if (CombatManager.blocksVanilla(victim) || CombatManager.blocksVanilla(source.getAttacker())
                || CombatManager.blocksVanilla(source.getSource())) return false;
        Combat victimCombat = CombatManager.get(victim);
        Entity attacker = source.getAttacker();
        Combat attackerCombat = attacker == null ? null : CombatManager.get(attacker);
        Flight flight = source.getSource() == null ? null : FLIGHTS.get(source.getSource().getUuid());
        if (flight == null && source.getSource() != null
                && source.getSource().getCommandTags().contains("tacticalcombat:accepted_projectile")) return false;
        DamagePermit permit = flight == null ? resolving : flight.permit;
        UUID effect = flight == null ? resolvingEffect : flight.effect;
        // The flight itself preserves identity even when vanilla can no longer resolve its dead owner.
        if (permit != null && (flight != null || attacker != null && permit.actor.equals(attacker.getUuid()))) {
            return victimCombat != null && permit.admit(victimCombat.id(), effect, victim.getUuid(), false);
        }
        // Vanilla thorns/guardian spikes are a consequence of the accepted melee, not another turn action.
        if (permit != null && source.isOf(net.minecraft.entity.damage.DamageTypes.THORNS)
                && permit.actor.equals(victim.getUuid()) && attackerCombat != null
                && attackerCombat.id().equals(permit.encounter)) {
            return permit.admit(permit.encounter, effect, attacker.getUuid(), true);
        }
        if (victimCombat == null && attackerCombat == null) return true;
        // Environmental/self damage remains vanilla in Minecraft Tactical until F18's time policy lands.
        if (attacker == null && source.getSource() instanceof ProjectileEntity) return false;
        if (attacker == null || attacker == victim) return true;
        // Outsiders and separate encounters cannot bypass participant action admission.
        return false;
    }

    public static void tick(long now) {
        FLIGHTS.entrySet().removeIf(e -> e.getValue().expires <= now);
        IMPACTS.retainAll(FLIGHTS.keySet());
    }
    public static void clear() { FLIGHTS.clear(); IMPACTS.clear(); resolving = null; resolvingEffect = null; beamComponent = false; }
}
