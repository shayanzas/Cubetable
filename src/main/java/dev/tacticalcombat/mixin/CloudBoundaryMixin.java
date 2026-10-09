package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.tacticalcombat.combat.CombatManager;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;
/** Persistent clouds are outside the supported encounter timing model; never apply them to combat actors. */
@Mixin(AreaEffectCloudEntity.class)
public abstract class CloudBoundaryMixin {
    @ModifyExpressionValue(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/world/World;getNonSpectatingEntities(Ljava/lang/Class;Lnet/minecraft/util/math/Box;)Ljava/util/List;"))
    private List<LivingEntity> tacticalcombat$targets(List<LivingEntity> targets) {
        LivingEntity owner=((AreaEffectCloudEntity)(Object)this).getOwner();
        if(owner!=null && CombatManager.isInCombat(owner)) return List.of();
        return targets.stream().filter(e->!CombatManager.isInCombat(e)).toList();
    }
}
