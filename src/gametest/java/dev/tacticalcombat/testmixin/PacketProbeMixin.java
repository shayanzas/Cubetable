package dev.tacticalcombat.testmixin;
import dev.tacticalcombat.combat.PacketProbe;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ServerCommonNetworkHandler.class)
public abstract class PacketProbeMixin {
    @Inject(method="sendPacket",at=@At("HEAD"))
    private void capture(Packet<?> packet,CallbackInfo ci) {
        if(packet instanceof CustomPayloadS2CPacket custom) PacketProbe.record(custom.payload());
    }
}
