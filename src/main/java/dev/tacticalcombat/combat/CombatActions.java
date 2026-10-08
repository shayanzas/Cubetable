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
    private record Flight(DamagePermit permit, long expires) {}
    private static final Map<UUID, Flight> FLIGHTS = new HashMap<>();
    private static DamagePermit resolving;
    private CombatActions() {}

    public static boolean melee(LivingEntity actor, Entity target, Supplier<Boolean> effect) {
        Combat combat = CombatManager.get(actor);
        if (combat == null) return CombatManager.get(target) == null && effect.get();
        if (!(target instanceof LivingEntity living) || !combat.legalMelee(actor, living)) return false;
        return resolve(actor, effect);
    }

    public static boolean resolve(LivingEntity actor, Supplier<Boolean> effect) {
        Combat combat = CombatManager.get(actor);
        if (combat == null) return effect.get();
        if (resolving != null) return false; // no recursive additional action from a damage callback
        DamagePermit permit = combat.beginAction(actor);
        if (permit == null) return false;
        resolving = permit;
        try { return effect.get(); }
        finally { resolving = null; combat.sync(); }
    }

    /** Called on entity load, after the projectile owner is set and before its first tick. */
    public static void projectileLoaded(Entity entity, long now) {
        if (!(entity instanceof ProjectileEntity projectile) || !(projectile.getOwner() instanceof LivingEntity actor)) return;
        Combat combat = CombatManager.get(actor);
        if (combat == null) return;
        // A flight that was already authorized must not spend a second action on chunk reload.
        if (FLIGHTS.containsKey(entity.getUuid())) return;
        if (entity.getCommandTags().contains("tacticalcombat:accepted_projectile")) { entity.discard(); return; }
        if (FLIGHTS.size() >= 1024) { entity.discard(); return; }
        DamagePermit permit = resolving != null && resolving.actor.equals(actor.getUuid())
                ? resolving : combat.beginAction(actor);
        if (permit == null) { entity.discard(); return; }
        entity.addCommandTag("tacticalcombat:accepted_projectile");
        FLIGHTS.put(entity.getUuid(), new Flight(permit, now + 200));
        combat.sync();
    }

    public static boolean allowDamage(LivingEntity victim, DamageSource source) {
        Combat victimCombat = CombatManager.get(victim);
        Entity attacker = source.getAttacker();
        Combat attackerCombat = attacker == null ? null : CombatManager.get(attacker);
        Flight flight = source.getSource() == null ? null : FLIGHTS.get(source.getSource().getUuid());
        if (flight == null && source.getSource() != null
                && source.getSource().getCommandTags().contains("tacticalcombat:accepted_projectile")) return false;
        DamagePermit permit = flight == null ? resolving : flight.permit;
        if (permit != null && attacker != null && permit.actor.equals(attacker.getUuid())) {
            // A completed/aborted encounter never leaks its accepted effects into the outside world.
            return victimCombat != null && permit.admit(victimCombat.id(), victim.getUuid());
        }
        if (victimCombat == null && attackerCombat == null) return true;
        // Environmental/self damage remains vanilla in Minecraft Tactical until F18's time policy lands.
        if (attacker == null || attacker == victim) return true;
        // Outsiders and separate encounters cannot bypass participant action admission.
        return false;
    }

    public static void tick(long now) { FLIGHTS.entrySet().removeIf(e -> e.getValue().expires <= now); }
    public static void clear() { FLIGHTS.clear(); resolving = null; }
}
