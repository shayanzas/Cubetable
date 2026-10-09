package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatActions;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;
@Mixin(PotionEntity.class)
public abstract class PotionBoundaryMixin {
    @WrapMethod(method="onCollision")
    private void tacticalcombat$impact(HitResult hit, Operation<Void> original) {
        PotionEntity potion=(PotionEntity)(Object)this;
        if (potion.getWorld().isClient) { original.call(hit); return; }
        CombatActions.splashImpact(potion,()->original.call(hit));
    }
    @ModifyExpressionValue(method={"applySplashPotion","applyWater"}, at=@At(value="INVOKE",target="Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private <T extends LivingEntity> List<T> tacticalcombat$targets(List<T> targets) { return filter(targets); }
    @ModifyExpressionValue(method="applyWater", at=@At(value="INVOKE",target="Lnet/minecraft/world/World;getEntitiesByClass(Ljava/lang/Class;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<LivingEntity> tacticalcombat$water(List<LivingEntity> targets) { return filter(targets); }
    private <T extends LivingEntity> List<T> filter(List<T> targets) {
        return targets.stream().filter(e->CombatActions.canProjectileAffect((PotionEntity)(Object)this,e)).toList();
    }
}
