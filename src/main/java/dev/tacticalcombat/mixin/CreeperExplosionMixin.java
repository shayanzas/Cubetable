package dev.tacticalcombat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatActions;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CreeperEntity.class)
public abstract class CreeperExplosionMixin {
    @WrapMethod(method = "explode")
    private void tacticalcombat$acceptedExplosion(Operation<Void> original) {
        CombatActions.resolve((CreeperEntity) (Object) this, () -> { original.call(); return true; });
    }
}
