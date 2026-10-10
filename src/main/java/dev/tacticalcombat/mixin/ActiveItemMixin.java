package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(LivingEntity.class)
public abstract class ActiveItemMixin {
    @WrapMethod(method = "consumeItem")
    private void tacticalcombat$consume(Operation<Void> original) {
        LivingEntity actor = (LivingEntity) (Object) this;
        if (actor.getWorld().isClient) { original.call(); return; }
        if (actor instanceof ServerPlayerEntity) CombatInteractions.finish(actor, () -> original.call());
        else if (!CombatActions.resolve(actor, () -> { original.call(); return true; })) actor.clearActiveItem();
    }
    @WrapMethod(method = "stopUsingItem")
    private void tacticalcombat$release(Operation<Void> original) {
        LivingEntity actor = (LivingEntity) (Object) this;
        if (actor.getWorld().isClient) { original.call(); return; }
        if (actor instanceof ServerPlayerEntity) CombatInteractions.finish(actor, () -> original.call());
        else {
            if (CombatManager.blocksVanilla(actor)) { actor.clearActiveItem(); return; }
            var combat = CombatManager.get(actor);
            if (combat == null || combat.isTurnOf(actor)) original.call();
            else actor.clearActiveItem();
        }
    }
}
