package dev.tacticalcombat.mixin;
import dev.tacticalcombat.combat.CombatInteractions;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.network.packet.c2s.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerPlayNetworkHandler.class)
public abstract class InventoryAuthorityMixin {
    @Shadow public ServerPlayerEntity player;
    private static final String MAIN = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V";
    private void gate(CallbackInfo ci) {
        if (!CombatInteractions.equipmentAllowed(player)) {
            player.currentScreenHandler.syncState(); ci.cancel();
        }
    }
    @Inject(method = "onClickSlot", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$slots(ClickSlotC2SPacket p, CallbackInfo ci) { gate(ci); }
    @Inject(method = "onCreativeInventoryAction", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$creative(CreativeInventoryActionC2SPacket p, CallbackInfo ci) { gate(ci); }
    @Inject(method = "onUpdateSelectedSlot", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$selected(UpdateSelectedSlotC2SPacket p, CallbackInfo ci) {
        gate(ci);
        if (ci.isCancelled()) player.networkHandler.sendPacket(new net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket(player.getInventory().selectedSlot));
    }
    @Inject(method = "onPlayerAction", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$drop(PlayerActionC2SPacket p, CallbackInfo ci) {
        switch (p.getAction()) {
            case DROP_ITEM, DROP_ALL_ITEMS, SWAP_ITEM_WITH_OFFHAND -> gate(ci);
            default -> { }
        }
    }
    @Inject(method = "onPickFromInventory", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$pick(PickFromInventoryC2SPacket p, CallbackInfo ci) { gate(ci); }
    @Inject(method = "onSelectMerchantTrade", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$trade(SelectMerchantTradeC2SPacket p, CallbackInfo ci) { gate(ci); }
    @Inject(method = "onCraftRequest", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$craft(CraftRequestC2SPacket p, CallbackInfo ci) { gate(ci); }
    @Inject(method = "onButtonClick", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$button(ButtonClickC2SPacket p, CallbackInfo ci) { gate(ci); }
    @Inject(method = "onUpdateBeacon", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$beacon(UpdateBeaconC2SPacket p, CallbackInfo ci) {
        // World-effect configuration is outside the supported tactical interaction set, even for a pre-opened menu.
        if (dev.tacticalcombat.combat.CombatManager.isInCombat(player)) { player.currentScreenHandler.syncState(); ci.cancel(); }
    }
    @Inject(method = "onSlotChangedState", at = @At(value = "INVOKE", target = MAIN, shift = At.Shift.AFTER), cancellable = true)
    private void tacticalcombat$crafter(SlotChangedStateC2SPacket p, CallbackInfo ci) { gate(ci); }
}
