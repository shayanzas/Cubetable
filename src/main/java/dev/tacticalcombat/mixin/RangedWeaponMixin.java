package dev.tacticalcombat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatActions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import java.util.List;

@Mixin(RangedWeaponItem.class)
public abstract class RangedWeaponMixin {
    @WrapMethod(method = "shootAll")
    private void tacticalcombat$volley(ServerWorld world, LivingEntity shooter, Hand hand, ItemStack weapon,
            List<ItemStack> projectiles, float speed, float divergence, boolean critical, LivingEntity target,
            Operation<Void> original) {
        if (projectiles.isEmpty()) return;
        CombatActions.volley(shooter, () -> {
            original.call(world, shooter, hand, weapon, projectiles, speed, divergence, critical, target);
            return true;
        });
    }
}
