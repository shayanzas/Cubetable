package dev.tacticalcombat.testmixin;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(PersistentProjectileEntity.class)
public interface ArrowAccess { @Invoker("onEntityHit") void impact(EntityHitResult hit); }
