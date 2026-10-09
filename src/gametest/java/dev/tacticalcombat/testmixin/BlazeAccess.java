package dev.tacticalcombat.testmixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.minecraft.entity.mob.BlazeEntity$ShootFireballGoal")
public interface BlazeAccess {
    @Accessor("fireballsFired") void fired(int count);
    @Accessor("fireballCooldown") void cooldown(int ticks);
}
