package dev.tacticalcombat.grid;

import dev.tacticalcombat.character.CharacterService;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdCharacter;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.*;

/** Conservative, fail-closed walking geometry. Separate from the unchanged Tactical Grid. */
public final class SrdTerrain implements dev.tacticalcombat.rules.srd.SrdMovement.Terrain {
    private static final double EPS=1e-5;
    private final LivingEntity actor;
    private final ServerWorld world;
    private final int footprint;
    public SrdTerrain(LivingEntity actor,SrdCharacter stats) {
        if(!(actor.getWorld() instanceof ServerWorld server)) throw new IllegalArgumentException("Server terrain required");
        this.actor=actor; world=server; footprint=footprint(stats.size());
    }
    public static int footprint(SrdCharacter.Size size) {
        return switch(size) { case TINY,SMALL,MEDIUM->1; case LARGE->2; case HUGE->3; case GARGANTUAN->4; };
    }
    public Cell currentCell() {
        return new Cell((int)Math.floor(actor.getX()-footprint/2d+EPS),
                (int)Math.floor(actor.getZ()-footprint/2d+EPS),actor.getY());
    }
    public Vec3d position(Cell cell) { return new Vec3d(cell.x()+footprint/2d,cell.y(),cell.z()+footprint/2d); }
    private Box body(Cell cell) {
        Vec3d p=position(cell); double width=Math.max(footprint,actor.getBoundingBox().getLengthX());
        double depth=Math.max(footprint,actor.getBoundingBox().getLengthZ());
        return new Box(p.x-width/2,p.y,p.z-depth/2,p.x+width/2,p.y+actor.getBoundingBox().getLengthY(),p.z+depth/2).contract(EPS);
    }
    private Reason bounds(Box box) {
        if(box.minY<world.getBottomY() || box.maxY>=world.getTopY() || !world.getWorldBorder().contains(box)) return Reason.BORDER;
        for(int x=MathHelper.floor(box.minX)>>4;x<=MathHelper.floor(box.maxX)>>4;x++)
            for(int z=MathHelper.floor(box.minZ)>>4;z<=MathHelper.floor(box.maxZ)>>4;z++)
                if(!world.isChunkLoaded(x,z)) return Reason.UNLOADED;
        return Reason.OK;
    }
    private Reason clear(Box box) {
        Reason bound=bounds(box); if(bound!=Reason.OK) return bound;
        // Read blocks only AFTER all intersected chunks are known loaded.
        for(BlockPos p:BlockPos.iterate(BlockPos.ofFloored(box.minX,box.minY-.02,box.minZ),BlockPos.ofFloored(box.maxX,box.maxY,box.maxZ))) {
            var state=world.getBlockState(p);
            if(!state.getFluidState().isEmpty() || Grid.isHazard(state)) return Reason.UNSUPPORTED_ENVIRONMENT;
        }
        if(world.getBlockCollisions(actor,box).iterator().hasNext()) return Reason.CLEARANCE;
        // Search expanded for controlled spaces whose physical hitbox is outside the swept area.
        for(LivingEntity other:world.getEntitiesByClass(LivingEntity.class,box.expand(4),e->e!=actor && e.isAlive() && !e.isSpectator())) {
            double width=other.getBoundingBox().getLengthX(), depth=other.getBoundingBox().getLengthZ();
            var record=CharacterService.bound(other);
            if(record!=null && record.profile().equals(RulesProfiles.SRD)) {
                try {
                    int logical=footprint(CharacterService.srdStats(other,record.campaign()).stats().size());
                    width=Math.max(width,logical); depth=Math.max(depth,logical);
                } catch(IllegalArgumentException invalid) { width=Math.max(width,4); depth=Math.max(depth,4); }
            }
            Box occupied=new Box(other.getX()-width/2,other.getY(),other.getZ()-depth/2,
                    other.getX()+width/2,other.getBoundingBox().maxY,other.getZ()+depth/2).contract(EPS);
            if(box.intersects(occupied)) return Reason.OCCUPIED;
        }
        return Reason.OK;
    }
    public Reason landing(Cell cell) {
        Box box=body(cell); Reason result=clear(box); if(result!=Reason.OK) return result;
        // Every intersected footprint column needs support at this height. Large uneven landings fail safely.
        for(int x=MathHelper.floor(box.minX);x<=MathHelper.floor(box.maxX);x++)
            for(int z=MathHelper.floor(box.minZ);z<=MathHelper.floor(box.maxZ);z++) {
                double minX=Math.max(box.minX,x+.01),maxX=Math.min(box.maxX,x+.99);
                double minZ=Math.max(box.minZ,z+.01),maxZ=Math.min(box.maxZ,z+.99);
                Box support=new Box(minX,cell.y()-.03,minZ,maxX,cell.y()-EPS,maxZ);
                if(bounds(support)!=Reason.OK) return bounds(support);
                if(!world.getBlockCollisions(actor,support).iterator().hasNext()) return Reason.NO_SUPPORT;
                BlockPos floor=BlockPos.ofFloored(x,cell.y()-.01,z);
                var state=world.getBlockState(floor);
                if(state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS)) return Reason.BLOCKED;
            }
        return Reason.OK;
    }
    private Step cardinalOrDiagonal(Cell from,int dx,int dz) {
        Cell guess=new Cell(from.x()+dx,from.z()+dz,from.y());
        Reason bound=bounds(body(guess).expand(0,1,0)); if(bound!=Reason.OK) return Step.denied(guess,bound);
        Reason failure=Reason.NO_SUPPORT;
        // Candidate floor surfaces include partial blocks. Prefer the closest height, then the lower surface.
        var heights=new java.util.TreeSet<Double>(java.util.Comparator.<Double>comparingDouble(y->Math.abs(y-from.y())).thenComparingDouble(y->y));
        for(int y=MathHelper.floor(from.y())-2;y<=MathHelper.floor(from.y())+1;y++) {
            BlockPos p=new BlockPos(guess.x(),y,guess.z()); var shape=world.getBlockState(p).getCollisionShape(world,p);
            for(Box part:shape.getBoundingBoxes()) {
                double top=y+part.maxY;
                if(Math.abs(top-from.y())<=1+EPS) heights.add(top);
            }
        }
        for(double y:heights) {
            Cell to=new Cell(guess.x(),guess.z(),y); Reason landing=landing(to);
            if(landing!=Reason.OK) { failure=landing; continue; }
            Reason sweep=sweep(from,to);
            if(sweep!=Reason.OK) { failure=sweep; continue; }
            int feet=difficult(to)?10:5;
            return new Step(to,feet,Reason.OK);
        }
        return Step.denied(guess,failure);
    }
    private boolean difficult(Cell cell) {
        Box b=body(cell);
        for(BlockPos p:BlockPos.iterate(BlockPos.ofFloored(b.minX,cell.y()-.04,b.minZ),BlockPos.ofFloored(b.maxX,cell.y(),b.maxZ))) {
            var state=world.getBlockState(p);
            if(state.isOf(Blocks.SOUL_SAND) || state.isOf(Blocks.HONEY_BLOCK)) return true;
        }
        return false;
    }
    private Reason sweep(Cell from,Cell to) {
        double raised=Math.max(from.y(),to.y());
        Cell lift=new Cell(from.x(),from.z(),raised), across=new Cell(to.x(),to.z(),raised);
        // Each axis-aligned union is the entire swept volume. The diagonal union is deliberately conservative.
        for(Box box:java.util.List.of(body(from).union(body(lift)),body(lift).union(body(across)),body(across).union(body(to)))) {
            Reason r=clear(box); if(r!=Reason.OK) return r;
        }
        return Reason.OK;
    }
    @Override public Step step(Cell from,int dx,int dz) {
        if(dx < -1 || dx > 1 || dz < -1 || dz > 1 || dx==0 && dz==0) throw new IllegalArgumentException("Not an SRD neighbor");
        Reason source=landing(from); if(source!=Reason.OK) return Step.denied(from,source);
        Step result=cardinalOrDiagonal(from,dx,dz); if(result.reason()!=Reason.OK) return result;
        if(dx!=0 && dz!=0) {
            // Both orthogonal passages must exist, and the full diagonal swept footprint must also fit.
            if(cardinalOrDiagonal(from,dx,0).reason()!=Reason.OK || cardinalOrDiagonal(from,0,dz).reason()!=Reason.OK)
                return Step.denied(result.to(),Reason.CORNER);
        }
        return result;
    }
}
