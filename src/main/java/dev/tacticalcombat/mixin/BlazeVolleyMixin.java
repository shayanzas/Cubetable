package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.*;
import dev.tacticalcombat.core.DamagePermit;
import net.minecraft.entity.mob.BlazeEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import org.spongepowered.asm.mixin.*;
@Mixin(targets="net.minecraft.entity.mob.BlazeEntity$ShootFireballGoal")
public abstract class BlazeVolleyMixin {
    @Shadow @Final private BlazeEntity blaze;
    @Shadow private int fireballsFired;
    @Shadow private int fireballCooldown;
    @Unique private DamagePermit tacticalcombat$permit;
    @Unique private long tacticalcombat$turn = -1;
    @Unique private java.util.UUID tacticalcombat$encounter;
    @WrapMethod(method="tick")
    private void tacticalcombat$volley(Operation<Void> original) {
        Combat combat=CombatManager.get(blaze);
        if (combat==null) { tacticalcombat$permit=null; original.call(); return; }
        if (!combat.isTurnOf(blaze)) return;
        long turn=combat.commandStamp().turn();
        if (turn!=tacticalcombat$turn || !combat.id().equals(tacticalcombat$encounter)) {
            tacticalcombat$permit=null;
            if (tacticalcombat$turn!=-1) { fireballsFired=0; fireballCooldown=0; }
            tacticalcombat$turn=turn; tacticalcombat$encounter=combat.id();
        }
        var target=blaze.getTarget();
        if (target==null || !combat.contains(target)) return;
        if (tacticalcombat$permit==null && combat.get(blaze).actionUsed) return;
        double distance=blaze.squaredDistanceTo(target);
        double range=blaze.getAttributeValue(EntityAttributes.GENERIC_FOLLOW_RANGE);
        if (fireballCooldown<=1 && fireballsFired>=1 && fireballsFired<=3 && distance>=4
                && distance<range*range && CombatGeometry.clearAttack(blaze,target)) {
            if (tacticalcombat$permit==null) tacticalcombat$permit=combat.beginAction(blaze);
            if (tacticalcombat$permit==null) return;
        }
        if (tacticalcombat$permit!=null) {
            CombatActions.inScope(tacticalcombat$permit,tacticalcombat$permit.action,()->{original.call();return true;});
            combat.sync();
            if (fireballsFired>=4) tacticalcombat$permit=null;
        } else original.call();
    }
}
