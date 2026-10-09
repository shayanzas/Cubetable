package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatActions;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(PersistentProjectileEntity.class)
public abstract class ArrowImpactMixin {
    @WrapMethod(method="onEntityHit")
    private void tacticalcombat$impact(EntityHitResult hit, Operation<Void> original) {
        var arrow=(PersistentProjectileEntity)(Object)this;
        if (arrow.getWorld().isClient) { original.call(hit); return; }
        CombatActions.arrowImpact(arrow,hit.getEntity(),()->original.call(hit));
    }
}
