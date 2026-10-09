package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatInteractions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(PlayerEntity.class)
public abstract class PlayerInteractionMixin {
    @WrapMethod(method = "interact")
    private ActionResult tacticalcombat$entity(Entity target, Hand hand, Operation<ActionResult> original) {
        if ((Object) this instanceof ServerPlayerEntity p)
            return CombatInteractions.entity(p, target, () -> original.call(target, hand));
        return original.call(target, hand);
    }
}
