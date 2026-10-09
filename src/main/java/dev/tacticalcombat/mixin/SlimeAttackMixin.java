package dev.tacticalcombat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatActions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.SlimeEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SlimeEntity.class)
public abstract class SlimeAttackMixin {
    @WrapMethod(method = "damage")
    private void tacticalcombat$contact(LivingEntity target, Operation<Void> original) {
        SlimeEntity slime = (SlimeEntity) (Object) this;
        if (!slime.isAlive() || !slime.isInAttackRange(target) || !slime.canSee(target)) return;
        CombatActions.melee(slime, target, () -> { original.call(target); return true; });
    }
}
