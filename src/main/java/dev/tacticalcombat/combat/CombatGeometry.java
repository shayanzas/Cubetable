package dev.tacticalcombat.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Minecraft geometry only; SRD cover categories belong to the future rules profile. */
public final class CombatGeometry {
    private CombatGeometry() {}
    public static boolean clearAttack(LivingEntity actor, LivingEntity target) {
        return clearRay(actor, target.getEyePos()) || clearRay(actor, target.getBoundingBox().getCenter());
    }
    private static boolean clearRay(LivingEntity actor, Vec3d end) {
        return actor.getWorld().raycast(new RaycastContext(actor.getEyePos(), end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, actor))
                .getType() == HitResult.Type.MISS;
    }
    public static boolean validLanding(LivingEntity actor, Vec3d at) {
        var cell = net.minecraft.util.math.BlockPos.ofFloored(at.x, at.y + 0.05, at.z);
        return dev.tacticalcombat.grid.Grid.isStandable(actor.getWorld(), cell)
                && Math.abs(dev.tacticalcombat.grid.Grid.surfaceY(actor.getWorld(), cell) - at.y) < 0.05;
    }
    public static boolean clearSegment(LivingEntity actor, Vec3d from, Vec3d to) {
        int samples = Math.max(1, (int) Math.ceil(from.distanceTo(to) / 0.125));
        if (samples > 512) return false;
        Box shape = actor.getBoundingBox().offset(actor.getPos().negate());
        for (int i = 1; i <= samples; i++) {
            Vec3d at = from.lerp(to, (double) i / samples);
            Box box = shape.offset(at).contract(1.0E-5);
            if (!actor.getWorld().getWorldBorder().contains(box)
                    || !actor.getWorld().isChunkLoaded((int) Math.floor(box.minX) >> 4, (int) Math.floor(box.minZ) >> 4)
                    || !actor.getWorld().isChunkLoaded((int) Math.floor(box.maxX) >> 4, (int) Math.floor(box.maxZ) >> 4)
                    || actor.getWorld().getBlockCollisions(actor, box).iterator().hasNext()) return false;
            for (var cell : net.minecraft.util.math.BlockPos.iterate(
                    net.minecraft.util.math.BlockPos.ofFloored(box.minX, box.minY - 0.02, box.minZ),
                    net.minecraft.util.math.BlockPos.ofFloored(box.maxX, box.maxY, box.maxZ))) {
                var state = actor.getWorld().getBlockState(cell);
                if (dev.tacticalcombat.grid.Grid.isHazard(state) || !state.getFluidState().isEmpty()) return false;
            }
        }
        return true;
    }
}
