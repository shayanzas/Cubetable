package dev.tacticalcombat.testmixin;
import net.minecraft.entity.mob.CreeperEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(CreeperEntity.class)
public interface CreeperAccess { @Invoker("explode") void explodeNow(); }
