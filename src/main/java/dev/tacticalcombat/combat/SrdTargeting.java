package dev.tacticalcombat.combat;

import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.rules.srd.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;

/** Server geometry mapping, not a claim that the SRD defines voxel sampling. */
public final class SrdTargeting {
    public enum Cover { NONE(0), HALF(2), THREE_QUARTERS(5), TOTAL(0); public final int ac; Cover(int ac) {this.ac=ac;} }
    public record Target(double feet,Cover cover,boolean advantage,boolean disadvantage) {}
    private SrdTargeting() {}
    public static Target posture(Target base,boolean attackerProne,boolean defenderProne){
        return new Target(base.feet(),base.cover(),base.advantage()||defenderProne&&base.feet()<=5,
            base.disadvantage()||attackerProne||defenderProne&&base.feet()>5);
    }
    /** Conservative normal-sight mapping: loaded geometry plus Minecraft eye ray, no camera state.
     * Special senses and tabletop lighting are not inferred from descriptive monster traits. */
    public static boolean canSeeAttacker(LivingEntity defender,SrdCharacter d,LivingEntity attacker,SrdCharacter a){
        if(attacker.isInvisible() || defender.isSleeping() || defender.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)
                || defender.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.DARKNESS))return false;
        return inspect(defender,d,attacker,a).cover()!=Cover.TOTAL && defender.canSee(attacker);
    }
    public static double feet(LivingEntity a,SrdCharacter ca,LivingEntity b,SrdCharacter cb) {
        return feet(a.getPos(),ca,b.getPos(),cb);
    }
    public static double feet(Vec3d a,SrdCharacter ca,Vec3d b,SrdCharacter cb) {
        double widths=(SrdTerrain.footprint(ca.size())+SrdTerrain.footprint(cb.size()))/2d;
        double dx=Math.max(0,Math.abs(a.x-b.x)-widths+1);
        double dz=Math.max(0,Math.abs(a.z-b.z)-widths+1);
        return 5*Math.max(Math.max(dx,dz),Math.abs(a.y-b.y));
    }
    /** Sixteen target-face samples (4 across by 4 high); collision shapes, not camera fading.
     * Blocked fraction: <1/2 none, >=1/2 half, >=3/4 three-quarters, all total.
     * Other creatures supply at most half cover in this conservative initial mapping.
     */
    public static Target inspect(LivingEntity a,SrdCharacter ca,LivingEntity b,SrdCharacter cb) {
        if(!(a.getWorld() instanceof ServerWorld world) || b.getWorld()!=world) throw new IllegalArgumentException("Same server world required");
        double distance=feet(a,ca,b,cb);
        if(distance>600) throw new IllegalArgumentException("Targeting search exceeds supported range");
        double width=Math.max(SrdTerrain.footprint(cb.size()),b.getWidth());
        Vec3d direction=b.getPos().subtract(a.getPos()).multiply(1,0,1).normalize();
        Vec3d side=new Vec3d(-direction.z,0,direction.x);
        Vec3d face=b.getPos().subtract(direction.multiply(width/2-.02));
        int blocked=0,creatures=0;
        Box area=a.getBoundingBox().union(b.getBoundingBox()).expand(4);
        // Fail before ray casting through missing chunks. No targeting operation loads new terrain.
        for(int x=MathHelper.floor(area.minX)>>4;x<=MathHelper.floor(area.maxX)>>4;x++)
            for(int z=MathHelper.floor(area.minZ)>>4;z<=MathHelper.floor(area.maxZ)>>4;z++)
                if(!world.isChunkLoaded(x,z)) throw new IllegalArgumentException("Targeting crosses unloaded terrain");
        var occupants=world.getEntitiesByClass(LivingEntity.class,area,e->e!=a && e!=b && e.isAlive() && !e.isSpectator());
        if(occupants.size()>256) throw new IllegalArgumentException("Targeting occupancy limit reached");
        for(int horizontal=0;horizontal<4;horizontal++) for(int vertical=0;vertical<4;vertical++) {
            Vec3d end=face.add(side.multiply(((horizontal+.5)/4-.5)*width)).add(0,(vertical+.5)/4*b.getHeight(),0);
            boolean block=world.raycast(new RaycastContext(a.getEyePos(),end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,a)).getType()!=HitResult.Type.MISS;
            if(block) blocked++;
            else if(occupants.stream().anyMatch(e->e.getBoundingBox().raycast(a.getEyePos(),end).isPresent())) creatures++;
        }
        Cover cover=blocked==16?Cover.TOTAL:blocked>=12?Cover.THREE_QUARTERS:blocked>=8 || creatures>=8?Cover.HALF:Cover.NONE;
        // Controlled encounters disclose participant locations; invisibility changes sight, not that disclosure.
        return new Target(distance,cover,a.isInvisible(),b.isInvisible());
    }
}
