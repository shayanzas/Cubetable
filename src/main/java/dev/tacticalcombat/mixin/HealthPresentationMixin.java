package dev.tacticalcombat.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class HealthPresentationMixin {
    @WrapMethod(method="damage")
    private boolean cubetable$damage(DamageSource source,float amount,Operation<Boolean> original){
        var entity=(LivingEntity)(Object)this;var encounter=entity.getWorld().isClient?null:CombatManager.get(entity);
        float hp=entity.getHealth(),absorption=entity.getAbsorptionAmount();boolean result=original.call(source,amount);
        if(encounter!=null)CombatPresentation.health(encounter,entity,hp,absorption);return result;
    }
    @WrapMethod(method="heal")
    private void cubetable$healing(float amount,Operation<Void> original){
        var entity=(LivingEntity)(Object)this;var encounter=entity.getWorld().isClient?null:CombatManager.get(entity);
        float hp=entity.getHealth(),absorption=entity.getAbsorptionAmount();original.call(amount);
        if(encounter!=null)CombatPresentation.health(encounter,entity,hp,absorption);
    }
}
