package dev.tacticalcombat.mixin;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.combat.CombatInteractions;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
@Mixin(ServerPlayerInteractionManager.class)
public abstract class ItemInteractionMixin {
    @WrapMethod(method = "interactItem")
    private ActionResult tacticalcombat$item(ServerPlayerEntity p, World w, ItemStack stack, Hand hand, Operation<ActionResult> original) {
        return CombatInteractions.item(p, stack, () -> original.call(p, w, stack, hand));
    }
    @WrapMethod(method = "interactBlock")
    private ActionResult tacticalcombat$block(ServerPlayerEntity p, World w, ItemStack stack, Hand hand, BlockHitResult hit, Operation<ActionResult> original) {
        return CombatInteractions.block(p, stack, hit, () -> original.call(p, w, stack, hand, hit));
    }
    @org.spongepowered.asm.mixin.Shadow protected ServerPlayerEntity player;
    @WrapMethod(method = "tryBreakBlock")
    private boolean tacticalcombat$mining(net.minecraft.util.math.BlockPos pos, Operation<Boolean> original) {
        return !dev.tacticalcombat.combat.CombatManager.isInCombat(player) && original.call(pos);
    }
}
