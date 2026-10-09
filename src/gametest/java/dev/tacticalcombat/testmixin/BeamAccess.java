package dev.tacticalcombat.testmixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.entity.mob.GuardianEntity$FireBeamGoal")
public interface BeamAccess { @Accessor("beamTicks") void beamTicks(int ticks); }
