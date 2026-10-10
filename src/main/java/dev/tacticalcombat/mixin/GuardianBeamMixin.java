package dev.tacticalcombat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatActions;
import dev.tacticalcombat.combat.CombatManager;
import net.minecraft.entity.mob.GuardianEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(targets = "net.minecraft.entity.mob.GuardianEntity$FireBeamGoal")
public abstract class GuardianBeamMixin {
    @Shadow @Final private GuardianEntity guardian;
    @Shadow private int beamTicks;
    @WrapMethod(method = "tick")
    private void tacticalcombat$beam(Operation<Void> original) {
        if (CombatManager.blocksVanilla(guardian)) return;
        var combat = CombatManager.get(guardian);
        if (combat != null && (!combat.isTurnOf(guardian) || combat.get(guardian).actionUsed)) return;
        if (guardian.getTarget() == null) return;
        if (beamTicks + 1 >= guardian.getWarmupTime()) {
            CombatActions.beam(guardian, guardian.getTarget(), () -> { original.call(); return true; });
        } else original.call();
    }
}
