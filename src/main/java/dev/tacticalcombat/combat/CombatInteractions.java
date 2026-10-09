package dev.tacticalcombat.combat;

import dev.tacticalcombat.core.DamagePermit;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.MerchantEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.block.*;
import net.minecraft.text.Text;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/** Explicit Minecraft Tactical policy. No D&D item rules or client-supplied costs. */
public final class CombatInteractions {
    private record Use(Combat combat, long turn, ItemStack stack, DamagePermit permit) {}
    private static final Map<UUID, Use> USES = new HashMap<>();
    private CombatInteractions() {}

    public static boolean equipmentAllowed(LivingEntity actor) {
        Combat combat = CombatManager.get(actor);
        return combat == null || combat.isTurnOf(actor) && !combat.get(actor).moving() && !actor.isUsingItem();
    }
    private static ActionResult denied(ServerPlayerEntity player) {
        player.sendMessage(Text.literal("This interaction is unavailable now in Minecraft Tactical combat."), true);
        return ActionResult.FAIL;
    }
    public static ActionResult item(ServerPlayerEntity player, ItemStack stack, Supplier<ActionResult> original) {
        Combat combat = CombatManager.get(player);
        if (combat == null) return original.get();
        if (!Registries.ITEM.getId(stack.getItem()).getNamespace().equals("minecraft")) return denied(player);
        if (stack.isOf(Items.WRITTEN_BOOK) || stack.isOf(Items.WRITABLE_BOOK)) return original.get();
        if (!equipmentAllowed(player)) return denied(player);
        if (stack.getItem() instanceof ArmorItem) return original.get();
        boolean supported = stack.contains(DataComponentTypes.FOOD) || stack.getItem() instanceof PotionItem
                && !(stack.getItem() instanceof ThrowablePotionItem) || stack.isOf(Items.MILK_BUCKET)
                || stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem
                || stack.isOf(Items.SNOWBALL) || stack.isOf(Items.EGG);
        if (!supported || stack.isOf(Items.CHORUS_FRUIT)) return denied(player);
        DamagePermit permit = combat.beginAction(player);
        if (permit == null) return denied(player);
        ActionResult[] result = {ActionResult.FAIL};
        CombatActions.inScope(permit, permit.action, () -> { result[0] = original.get(); return true; });
        // The allowlisted vanilla methods return PASS/FAIL without performing their use.
        if (!result[0].isAccepted()) combat.get(player).actionUsed = false;
        else if (player.isUsingItem()) USES.put(player.getUuid(), new Use(combat, combat.commandStamp().turn(), player.getActiveItem(), permit));
        combat.sync();
        return result[0];
    }
    public static void finish(LivingEntity actor, Runnable original) {
        Combat combat = CombatManager.get(actor);
        if (combat == null) { original.run(); return; }
        Use use = USES.remove(actor.getUuid());
        if (use == null || use.combat != combat || use.turn != combat.commandStamp().turn()
                || !combat.isTurnOf(actor) || actor.getActiveItem() != use.stack) {
            actor.clearActiveItem(); return;
        }
        CombatActions.inScope(use.permit, use.permit.action, () -> { original.run(); return true; });
    }
    public static void cancel(LivingEntity actor) {
        USES.remove(actor.getUuid());
        actor.clearActiveItem();
    }
    public static void clear() { USES.clear(); }

    public static ActionResult block(ServerPlayerEntity player, ItemStack stack, BlockHitResult hit, Supplier<ActionResult> original) {
        if (CombatManager.get(player) == null) return original.get();
        Block block = player.getWorld().getBlockState(hit.getBlockPos()).getBlock();
        if (!stack.isEmpty() || !Registries.BLOCK.getId(block).getNamespace().equals("minecraft")) return denied(player);
        // UI opening is free; slot mutations are independently gated on the server.
        if (block instanceof AbstractChestBlock<?> || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock)
            return original.get();
        if (block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock) return action(player, original);
        return denied(player);
    }
    public static ActionResult entity(ServerPlayerEntity player, Entity target, Supplier<ActionResult> original) {
        Combat combat = CombatManager.get(player), other = CombatManager.get(target);
        if (combat == null && other == null) return original.get();
        if (other != null && other != combat) return denied(player);
        if (!equipmentAllowed(player)) return denied(player);
        if (target instanceof MerchantEntity && player.getMainHandStack().isEmpty() && player.getOffHandStack().isEmpty())
            return original.get();
        if (target instanceof ArmorStandEntity) return original.get();
        return denied(player);
    }
    private static ActionResult action(ServerPlayerEntity player, Supplier<ActionResult> original) {
        Combat combat = CombatManager.get(player);
        DamagePermit permit = combat.beginAction(player);
        if (permit == null) return denied(player);
        ActionResult result = original.get();
        if (!result.isAccepted()) combat.get(player).actionUsed = false;
        combat.sync();
        return result;
    }
}
