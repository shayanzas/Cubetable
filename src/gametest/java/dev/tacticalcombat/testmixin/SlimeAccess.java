package dev.tacticalcombat.testmixin;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(SlimeEntity.class)
public interface SlimeAccess { @Invoker("damage") void contact(LivingEntity target); }
