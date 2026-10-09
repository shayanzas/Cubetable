package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatInteractions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ArmorStandEntity.class)
public abstract class ArmorStandInteractionMixin {
    @WrapMethod(method = "interactAt")
    private ActionResult tacticalcombat$equipment(PlayerEntity player, Vec3d at, Hand hand, Operation<ActionResult> original) {
        if (player instanceof ServerPlayerEntity p)
            return CombatInteractions.entity(p, (ArmorStandEntity) (Object) this, () -> original.call(player, at, hand));
        return original.call(player, at, hand);
    }
}
