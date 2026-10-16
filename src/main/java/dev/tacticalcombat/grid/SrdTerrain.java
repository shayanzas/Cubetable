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
import java.util.*;

/** Conservative, fail-closed walking geometry. Separate from the unchanged Tactical Grid. */
public final class SrdTerrain implements dev.tacticalcombat.rules.srd.SrdMovement.Terrain {
    private static final double EPS=1e-5;
    private final LivingEntity actor;
    private final ServerWorld world;
    private final int footprint;
    private final SearchCache cache;
    public SrdTerrain(LivingEntity actor,SrdCharacter stats) {
        if(!(actor.getWorld() instanceof ServerWorld server)) throw new IllegalArgumentException("Server terrain required");
        this.actor=actor; world=server; footprint=footprint(stats.size()); cache=null;
    }
    private SrdTerrain(SrdTerrain source,SearchCache cache) {
        actor=source.actor;world=source.world;footprint=source.footprint;this.cache=cache;
    }
    public record Metrics(long blockReads,long collisionChecks,long occupancyQueries,long occupantDecodes,
                          long occupancyChecks,long steps,long cacheHits,int retainedEntries) {}
    public record SearchResult(Preview preview,Metrics metrics) {}
    private static final Set<Pending> PENDING=Collections.newSetFromMap(new IdentityHashMap<>());
    /** Called for actual chunk block mutations, including changes that suppress neighbor notifications. */
    public static void changed(net.minecraft.world.World world,BlockPos pos) {
        for(var pending:PENDING) if(pending.owner.world==world && pending.area.contains(Vec3d.ofCenter(pos))) pending.invalid=true;
    }
    public Pending pending(Cell start,int feet) {
        if(!world.getServer().isOnThread()) throw new IllegalStateException("SRD search requires server thread");
        if(PENDING.size()>=8) throw new IllegalArgumentException("Grid service busy; retry shortly");
        return new Pending(this,start,feet);
    }
    /** Server-thread-only job. Geometry memoization expires at each yield; execution never consumes this cache. */
    public static final class Pending implements AutoCloseable {
        private final SrdTerrain owner;
        private final Cell start;
        private final int feet;
        private final Box area,actorBox;
        private final dev.tacticalcombat.rules.srd.SrdMovement.Search search;
        private List<Occupant> occupants;
        private List<net.minecraft.world.chunk.WorldChunk> chunks;
        private List<Double> border;
        private boolean invalid,closed;
        private Pending(SrdTerrain owner,Cell start,int feet) {
            this.owner=owner;this.start=start;this.feet=feet;area=owner.body(start).expand(feet/5+6);
            actorBox=owner.actor.getBoundingBox();search=new dev.tacticalcombat.rules.srd.SrdMovement.Search(start,feet);PENDING.add(this);
        }
        public boolean advance(long deadline) {
            if(!owner.world.getServer().isOnThread()) throw new IllegalStateException("SRD search requires server thread");
            if(closed || invalid || !actorBox.equals(owner.actor.getBoundingBox())) throw new IllegalArgumentException("Grid cancelled: terrain or actor changed; refresh");
            var scope=owner.capture(start,feet);
            var currentChunks=new ArrayList<net.minecraft.world.chunk.WorldChunk>();
            for(int x=MathHelper.floor(area.minX)>>4;x<=MathHelper.floor(area.maxX)>>4;x++)
                for(int z=MathHelper.floor(area.minZ)>>4;z<=MathHelper.floor(area.maxZ)>>4;z++) currentChunks.add(owner.world.getChunkManager().getWorldChunk(x,z));
            var b=owner.world.getWorldBorder();var currentBorder=List.of(b.getBoundWest(),b.getBoundEast(),b.getBoundNorth(),b.getBoundSouth());
            if(occupants!=null && (!occupants.equals(scope.occupants) || !chunks.equals(currentChunks) || !border.equals(currentBorder)))
                throw new IllegalArgumentException("Grid cancelled: occupancy, chunks or border changed; refresh");
            occupants=List.copyOf(scope.occupants);chunks=currentChunks;border=currentBorder;
            return search.advance(new SrdTerrain(owner,scope),256,deadline);
        }
        public Preview result() {return search.result();}
        @Override public void close() {closed=true;PENDING.remove(this);occupants=null;chunks=null;border=null;}
    }
    private record Edge(Cell from,int dx,int dz) {}
    private record Occupant(Box physical,Box logical) {}
    /** All caches live only on this synchronous call's stack. Execution keeps using the uncached adapter. */
    public Preview search(Cell start,int feet) { return measuredSearch(start,feet).preview(); }
    public SearchResult measuredSearch(Cell start,int feet) {
        if(!world.getServer().isOnThread()) throw new IllegalStateException("SRD search requires server thread");
        if(feet<0 || feet>300) throw new IllegalArgumentException("Invalid movement allowance");
        var scope=capture(start,feet);
        var preview=dev.tacticalcombat.rules.srd.SrdMovement.search(start,feet,new SrdTerrain(this,scope));
        return new SearchResult(preview,new Metrics(scope.blockReads,scope.collisionChecks,scope.occupancyQueries,
                scope.occupantDecodes,scope.occupancyChecks,scope.steps,scope.cacheHits,
                scope.blocks.size()+scope.clear.size()+scope.landings.size()+scope.edges.size()));
    }
    private SearchCache capture(Cell start,int feet) {
        var scope=new SearchCache();
        // Include the extra neighbor explored at the budget boundary, ascent sweeps and the old query's 4-block margin.
        Box area=body(start).expand(feet/5+6);
        var nearby=new ArrayList<LivingEntity>();
        world.collectEntitiesByType(net.minecraft.util.TypeFilter.instanceOf(LivingEntity.class),area,
                e->e!=actor && e.isAlive() && !e.isSpectator(),nearby,257);
        scope.occupancyQueries++;
        if(nearby.size()>256) throw new IllegalArgumentException("SRD grid occupancy limit exceeded (256 nearby creatures)");
        for(var other:nearby) scope.occupants.add(new Occupant(other.getBoundingBox(),occupied(other,scope)));
        return scope;
    }
    private static final class SearchCache {
        // Stop retaining new entries at the cap; a miss still performs every check. No correctness-changing eviction.
        static final int LIMIT=32768;
        final Map<BlockPos,net.minecraft.block.BlockState> blocks=new HashMap<>();
        final Map<Box,Reason> clear=new HashMap<>();
        final Map<Cell,Reason> landings=new HashMap<>();
        final Map<Edge,Step> edges=new HashMap<>();
        final List<Occupant> occupants=new ArrayList<>();
        long blockReads,collisionChecks,occupancyQueries,occupantDecodes,occupancyChecks,steps,cacheHits;
        <K,V> V memo(Map<K,V> map,K key,java.util.function.Supplier<V> compute) {
            V saved=map.get(key);if(saved!=null){cacheHits++;return saved;}
            V value=compute.get();if(map.size()<LIMIT) map.put(key,value);return value;
        }
    }
    private net.minecraft.block.BlockState block(BlockPos p) {
        if(cache==null) return world.getBlockState(p);
        return cache.memo(cache.blocks,p.toImmutable(),()->{cache.blockReads++;return world.getBlockState(p);});
    }
    private boolean collision(Box box) {
        if(cache!=null) cache.collisionChecks++;
        return world.getBlockCollisions(actor,box).iterator().hasNext();
    }
    private Box occupied(LivingEntity other,SearchCache scope) {
        double width=other.getBoundingBox().getLengthX(),depth=other.getBoundingBox().getLengthZ();
        var record=CharacterService.bound(other);
        if(record!=null && record.profile().equals(RulesProfiles.SRD)) {
            try {
                if(scope!=null) scope.occupantDecodes++;
                int logical=footprint(CharacterService.srdStats(other,record.campaign()).stats().size());
                width=Math.max(width,logical);depth=Math.max(depth,logical);
            } catch(IllegalArgumentException invalid) {width=Math.max(width,4);depth=Math.max(depth,4);}
        }
        return new Box(other.getX()-width/2,other.getY(),other.getZ()-depth/2,
                other.getX()+width/2,other.getBoundingBox().maxY,other.getZ()+depth/2).contract(EPS);
    }
    public static int footprint(SrdCharacter.Size size) {
        return switch(size) { case TINY,SMALL,MEDIUM->1; case LARGE->2; case HUGE->3; case GARGANTUAN->4; };
    }
    public Cell currentCell() {
        return new Cell((int)Math.floor(actor.getX()-footprint/2d+EPS),
                (int)Math.floor(actor.getZ()-footprint/2d+EPS),actor.getY());
    }
    /** Closest same-height grid anchor; discovery never changes the entity. */
    public Cell admissionCell() {
        return new Cell((int)Math.floor(actor.getX()-footprint/2d+.5),
                (int)Math.floor(actor.getZ()-footprint/2d+.5),actor.getY());
    }
    public Reason alignment(Cell cell) {
        Vec3d to=position(cell),from=actor.getPos();
        if(Math.abs(to.x-from.x)>.50001||Math.abs(to.z-from.z)>.50001||Math.abs(to.y-from.y)>EPS) return Reason.BLOCKED;
        // A conservative full swept footprint: no wall crossing, unsupported floor or occupied space.
        Box destination=body(cell),source=destination.offset(from.subtract(to));
        Box swept=source.union(destination);Reason r=clear(swept);
        return r==Reason.OK?supported(swept,cell.y()):r;
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
        return cache==null?clearLive(box):cache.memo(cache.clear,box,()->clearLive(box));
    }
    private Reason clearLive(Box box) {
        Reason bound=bounds(box); if(bound!=Reason.OK) return bound;
        // Read blocks only AFTER all intersected chunks are known loaded.
        for(BlockPos p:BlockPos.iterate(BlockPos.ofFloored(box.minX,box.minY-.02,box.minZ),BlockPos.ofFloored(box.maxX,box.maxY,box.maxZ))) {
            var state=block(p);
            if(!state.getFluidState().isEmpty() || Grid.isHazard(state)) return Reason.UNSUPPORTED_ENVIRONMENT;
        }
        if(collision(box)) return Reason.CLEARANCE;
        // Search expanded for controlled spaces whose physical hitbox is outside the swept area.
        if(cache!=null) {
            Box query=box.expand(4);
            for(var occupant:cache.occupants) {
                cache.occupancyChecks++;
                if(query.intersects(occupant.physical) && box.intersects(occupant.logical)) return Reason.OCCUPIED;
            }
            return Reason.OK;
        }
        for(LivingEntity other:world.getEntitiesByClass(LivingEntity.class,box.expand(4),e->e!=actor && e.isAlive() && !e.isSpectator())) {
            if(box.intersects(occupied(other,null))) return Reason.OCCUPIED;
        }
        return Reason.OK;
    }
    public Reason landing(Cell cell) {
        return cache==null?landingLive(cell):cache.memo(cache.landings,cell,()->landingLive(cell));
    }
    private Reason landingLive(Cell cell) {
        Box box=body(cell); Reason result=clear(box); if(result!=Reason.OK) return result;
        return supported(box,cell.y());
    }
    private Reason supported(Box box,double height) {
        // Every intersected footprint column needs support at this height. Large uneven landings fail safely.
        for(int x=MathHelper.floor(box.minX);x<=MathHelper.floor(box.maxX);x++)
            for(int z=MathHelper.floor(box.minZ);z<=MathHelper.floor(box.maxZ);z++) {
                double minX=Math.max(box.minX,x+.01),maxX=Math.min(box.maxX,x+.99);
                double minZ=Math.max(box.minZ,z+.01),maxZ=Math.min(box.maxZ,z+.99);
                Box support=new Box(minX,height-.03,minZ,maxX,height-EPS,maxZ);
                if(bounds(support)!=Reason.OK) return bounds(support);
                if(!collision(support)) return Reason.NO_SUPPORT;
                BlockPos floor=BlockPos.ofFloored(x,height-.01,z);
                var state=block(floor);
                if(state.isIn(BlockTags.FENCES) || state.isIn(BlockTags.WALLS)) return Reason.BLOCKED;
            }
        return Reason.OK;
    }
    private Step cardinalOrDiagonal(Cell from,int dx,int dz) {
        return cache==null?edgeLive(from,dx,dz):cache.memo(cache.edges,new Edge(from,dx,dz),()->edgeLive(from,dx,dz));
    }
    private Step edgeLive(Cell from,int dx,int dz) {
        Cell guess=new Cell(from.x()+dx,from.z()+dz,from.y());
        Reason bound=bounds(body(guess).expand(0,1,0)); if(bound!=Reason.OK) return Step.denied(guess,bound);
        Reason failure=Reason.NO_SUPPORT;
        // Candidate floor surfaces include partial blocks. Prefer the closest height, then the lower surface.
        var heights=new java.util.TreeSet<Double>(java.util.Comparator.<Double>comparingDouble(y->Math.abs(y-from.y())).thenComparingDouble(y->y));
        for(int y=MathHelper.floor(from.y())-2;y<=MathHelper.floor(from.y())+1;y++) {
            BlockPos p=new BlockPos(guess.x(),y,guess.z()); var shape=block(p).getCollisionShape(world,p);
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
            var state=block(p);
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
        if(cache!=null) cache.steps++;
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
