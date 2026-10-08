package dev.tacticalcombat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatActions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(MobEntity.class)
public abstract class MobAttackMixin {
    @WrapMethod(method = "tryAttack")
    private boolean tacticalcombat$acceptedMelee(Entity target, Operation<Boolean> original) {
        return CombatActions.melee((MobEntity) (Object) this, target, () -> original.call(target));
    }
}
