package dev.tacticalcombat.testmixin;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(LivingEntity.class)
public interface ConsumeAccess { @Invoker("consumeItem") void consumeNow();
    @org.spongepowered.asm.mixin.gen.Accessor("itemUseTimeLeft") void remainingUseTicks(int ticks); }
