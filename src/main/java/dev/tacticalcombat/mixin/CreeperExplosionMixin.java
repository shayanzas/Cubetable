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
    // Lingering encounter time has not been implemented. Do not leak a dead actor's cloud outside the boundary.
    @org.spongepowered.asm.mixin.injection.Inject(method="spawnEffectsCloud", at=@org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable=true)
    private void tacticalcombat$cloud(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (dev.tacticalcombat.combat.CombatManager.isInCombat((CreeperEntity)(Object)this)) ci.cancel();
    }
}
