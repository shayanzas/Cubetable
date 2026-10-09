package dev.tacticalcombat.testmixin;
import net.minecraft.entity.projectile.thrown.PotionEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(PotionEntity.class)
public interface PotionAccess { @Invoker("onCollision") void impact(HitResult hit); }
