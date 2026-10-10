package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.entity.*;
import net.minecraft.test.*;
import java.lang.management.ManagementFactory;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;

/** Identical deterministic fixtures for the frozen published geometry and the current search. */
public class SrdGridPerformanceGameTests implements FabricGameTest {
    record Scenario(String name,int side,int feet,int actors,String size,String terrain) {}
    static final List<Scenario> CASES=List.of(
        new Scenario("small20",12,20,2,"MEDIUM","flat"),new Scenario("small30",12,30,2,"MEDIUM","flat"),
        new Scenario("small60",12,60,2,"MEDIUM","flat"),new Scenario("medium30",24,30,4,"MEDIUM","flat"),
        new Scenario("medium60",24,60,4,"MEDIUM","flat"),new Scenario("large60",40,60,8,"MEDIUM","flat"),
        new Scenario("large120",40,120,8,"MEDIUM","flat"),new Scenario("walls30",24,30,4,"MEDIUM","walls"),
        new Scenario("walls60",24,60,4,"MEDIUM","walls"),new Scenario("stairs30",24,30,4,"MEDIUM","stairs"),
        new Scenario("stairs60",24,60,4,"MEDIUM","stairs"),new Scenario("uneven60",24,60,4,"MEDIUM","uneven"),
        new Scenario("largeBody30",24,30,4,"LARGE","flat"),new Scenario("largeBody60",24,60,4,"LARGE","flat"),
        new Scenario("occupied8",24,30,8,"MEDIUM","flat"),new Scenario("occupied16",24,60,16,"MEDIUM","flat"),
        new Scenario("weighted29",24,29,4,"MEDIUM","weighted"));
    static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    static long allocation() { return BEAN.isThreadAllocatedMemorySupported()?BEAN.getThreadAllocatedBytes(Thread.currentThread().threadId()):-1; }
    static long cpu() { return BEAN.isCurrentThreadCpuTimeSupported()?BEAN.getCurrentThreadCpuTime():-1; }
    static LivingEntity prepare(Fixture f,Scenario c) throws Exception {
        // An isolated arena above the shared flat test world prevents preexisting floors altering its boundary.
        for(int x=-1;x<=c.side;x++) for(int z=-1;z<=c.side;z++) {
            for(int y=1;y<=5;y++) f.ctx.getWorld().setBlockState(f.origin.add(x,y,z),Blocks.AIR.getDefaultState(),2);
            f.ctx.getWorld().setBlockState(f.origin.add(x,0,z),x>=0&&z>=0&&x<c.side&&z<c.side?Blocks.STONE.getDefaultState():Blocks.AIR.getDefaultState(),2);
            if(x==-1 || z==-1 || x==c.side || z==c.side) {
                for(int y=0;y<=4;y++) f.ctx.getWorld().setBlockState(f.origin.add(x,y,z),Blocks.STONE.getDefaultState(),2);
            }
            if(x<=1 || z<=1 || x>=c.side-1 || z>=c.side-1) continue;
            BlockState terrain=switch(c.terrain) {
                case "walls" -> x%5==0 && z%6!=0?Blocks.STONE.getDefaultState():null;
                case "stairs" -> x%4==0?Blocks.STONE_BRICK_STAIRS.getDefaultState():x%4==1?Blocks.STONE_SLAB.getDefaultState():null;
                case "uneven" -> (x+z)%4==0?Blocks.STONE.getDefaultState():null;
                case "weighted" -> x%3==0?Blocks.SOUL_SAND.getDefaultState():null;
                default -> null;
            };
            if(terrain!=null) {
                f.ctx.getWorld().setBlockState(f.origin.add(x,c.terrain.equals("weighted")?0:1,z),terrain,2);
                if(c.terrain.equals("walls")) f.ctx.getWorld().setBlockState(f.origin.add(x,2,z),terrain,2);
            }
        }
        LivingEntity first=null;
        for(int i=0;i<c.actors;i++) {
            int x=i==0?2:4+(i%4)*3,z=i==0?2:4+(i/4)*3;
            if(x>=c.side-1) x=c.side-2;if(z>=c.side-1) z=c.side-2;
            var actor=f.actor(x,z,false);
            var sheet=SrdAuthoring.template();var data=sheet.getAsJsonObject("collections").getAsJsonArray("srd_combat").get(0).getAsJsonObject();
            data.addProperty("speed_feet",c.feet);data.addProperty("size",(i==0?c.size:i%3==0?"LARGE":"MEDIUM").toLowerCase(Locale.ROOT));
            UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,actor.getUuid(),null);
            if(i==0) {
                int footprint=SrdTerrain.footprint(SrdCharacter.Size.valueOf(c.size));
                actor.setPosition(f.origin.getX()+2+footprint/2d,f.origin.getY()+1,f.origin.getZ()+2+footprint/2d);first=actor;
            }
            // All participants start on genuinely supported, clear footprints in every scenario.
            int width=SrdTerrain.footprint(SrdCharacter.Size.valueOf(i==0?c.size:i%3==0?"LARGE":"MEDIUM"));
            if(i!=0) actor.setPosition(f.origin.getX()+x+width/2d,f.origin.getY()+1,f.origin.getZ()+z+width/2d);
            for(int xx=x;xx<x+width;xx++) for(int zz=z;zz<z+width;zz++) {
                f.ctx.getWorld().setBlockState(f.origin.add(xx,0,zz),Blocks.STONE.getDefaultState(),2);
                for(int y=1;y<5;y++) f.ctx.getWorld().setBlockState(f.origin.add(xx,y,zz),Blocks.AIR.getDefaultState(),2);
            }
        }
        return first;
    }
    static SrdMovement.Preview search(LivingEntity actor,SrdCharacter stats,int feet,boolean reference) {
        if(reference) {var t=new ReferenceSrdTerrain(actor,stats);return ReferenceSrdSearch.search(t.currentCell(),feet,t);}
        var t=new SrdTerrain(actor,stats);return t.search(t.currentCell(),feet);
    }
    static int bytes(SrdMovement.Preview p) {
        var cells=new JsonArray();for(var n:p.reachable().values()) {
            var a=new JsonArray();a.add(n.cell().x());a.add(n.cell().z());a.add(n.cell().y());a.add(n.costFeet());
            if(n.parent()!=null){a.add(n.parent().x());a.add(n.parent().z());a.add(n.parent().y());}cells.add(a);
        }
        return cells.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cachesExpireBeforeTerrainOccupancyAndCostChanges(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(2,2,true);var stats=CharacterService.srdStats(actor,f.campaign).stats();var terrain=new SrdTerrain(actor,stats);
            var start=terrain.currentCell();var next=new SrdMovement.Cell(start.x()+1,start.z(),start.y());
            check(terrain.search(start,30).reachable().get(next).costFeet()==5,"initial cached search");
            // Same server tick, same adapter, no invalidation event needed: each search owns its cache.
            ctx.getWorld().setBlockState(f.origin.add(3,1,2),Blocks.STONE.getDefaultState());ctx.getWorld().setBlockState(f.origin.add(3,2,2),Blocks.STONE.getDefaultState());
            check(!terrain.search(start,30).reachable().containsKey(next),"new wall not hidden by prior cache");
            ctx.getWorld().setBlockState(f.origin.add(3,1,2),Blocks.AIR.getDefaultState());ctx.getWorld().setBlockState(f.origin.add(3,2,2),Blocks.AIR.getDefaultState());
            var blocker=f.actor(3,2,true);
            check(!terrain.search(start,30).reachable().containsKey(next),"new living occupant blocks next search");
            blocker.setPosition(blocker.getPos().add(5,0,0));
            check(terrain.search(start,30).reachable().containsKey(next),"moved occupant frees old square");
            ctx.getWorld().setBlockState(f.origin.add(3,0,2),Blocks.HONEY_BLOCK.getDefaultState());
            check(terrain.search(start,30).equals(search(actor,stats,30,true)),"changed weighted partial surface agrees with reference");
            ctx.getWorld().setBlockState(f.origin.add(3,0,2),Blocks.AIR.getDefaultState());
            check(!terrain.search(start,30).reachable().containsKey(next),"removed support never reused");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void searchEnforcesThreadChunksBorderAndOccupancyBound(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(2,2,true);var stats=CharacterService.srdStats(actor,f.campaign).stats();var terrain=new SrdTerrain(actor,stats);var start=terrain.currentCell();
            var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
            Thread worker=new Thread(()->{try {terrain.search(start,30);}catch(Throwable denied){failure.set(denied);}});
            worker.start();worker.join();check(failure.get() instanceof IllegalStateException,"off-thread search refused before live access");
            var remote=new SrdMovement.Cell(1000000,1000000,start.y());
            check(!ctx.getWorld().isChunkLoaded(62500,62500),"remote chunk initially absent");
            check(terrain.search(remote,20).equals(SrdMovement.search(remote,20,new ReferenceSrdTerrain(actor,stats))) && !ctx.getWorld().isChunkLoaded(62500,62500),"bounded entity capture and search do not load chunks");
            var border=ctx.getWorld().getWorldBorder();double x=border.getCenterX(),z=border.getCenterZ(),size=border.getSize();
            try {
                border.setCenter(actor.getX(),actor.getZ());border.setSize(2);
                check(terrain.search(start,30).equals(search(actor,stats,30,true)),"border checks preserved through cache");
                check(terrain.search(start,30).reachable().size()==1,"no border crossing");
            } finally {border.setCenter(x,z);border.setSize(size);}
            for(int i=0;i<257;i++) f.actor(7,7,false);
            rejects(()->terrain.search(start,30),"occupancy capture has a fail-closed hard cap");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=1200)
    public void searchLimitPreservesReferenceRoutesAndBoundedCache(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx,ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(512,65,512)))) {
            var actor=prepare(f,new Scenario("limit300",80,300,2,"MEDIUM","flat"));
            actor.setPosition(f.origin.getX()+40.5,f.origin.getY()+1,f.origin.getZ()+40.5);
            var stats=CharacterService.srdStats(actor,f.campaign).stats();var terrain=new SrdTerrain(actor,stats);
            var current=terrain.measuredSearch(terrain.currentCell(),300);var old=search(actor,stats,300,true);
            check(current.preview().equals(old) && old.truncated() && old.reachable().size()==4096,"exact search-limit routes and truncation preserved");
            check(current.metrics().occupancyQueries()==1 && current.metrics().retainedEntries()<=4*32768,"request caches and occupancy bounded");
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_LIMIT nodes={} truncated={} metrics={}",old.reachable().size(),old.truncated(),current.metrics());
            if(Boolean.getBoolean("cubetable.srd.benchmark")) {
                for(int i=0;i<3;i++) terrain.search(terrain.currentCell(),300);
                long[] wall=new long[12],allocated=new long[12];
                for(int i=0;i<12;i++) {
                    long a=allocation(),at=System.nanoTime();var p=terrain.search(terrain.currentCell(),300);
                    wall[i]=System.nanoTime()-at;allocated[i]=allocation()-a;check(p.equals(old),"repeated truncated result stable");
                }
                Arrays.sort(wall);Arrays.sort(allocated);
                dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_LIMIT_BENCH n=12 median_us={} p95_us={} max_us={} allocated_median={} grid_bytes={}",wall[6]/1000,wall[11]/1000,wall[11]/1000,allocated[6],bytes(old));
            }
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void simultaneousEncountersKeepIndependentPreviewsAndNoWorkQueue(TestContext ctx) throws Exception {
        var p=ctx.createMockCreativeServerPlayerInWorld();var q=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            int i=0;var encounters=new ArrayList<SrdEncounter>();
            for(var pc:List.of(p,q)) {
                SrdLiveGameTests.capability(pc);f.repo.invite(f.dm,f.campaign,++i,pc.getUuid(),false);
                UUID id=f.repo.submit(CharacterService.principal(pc),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());
                f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,pc.getUuid(),pc.getUuid());CharacterService.select(pc,f.campaign);
                pc.setPosition(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+i*3+.5);
                var npc=f.actor(4,i*3,true);encounters.add(SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),pc,npc));
            }
            long[] elapsed=new long[20];
            for(int sample=-3;sample<20;sample++) {
                long at=System.nanoTime();
                for(var e:encounters) {
                    var grid=e.grid(f.dm);var actor=e.current();var stats=CharacterService.srdStats(actor,f.campaign).stats();
                    check(grid.start().equals(new SrdTerrain(actor,stats).currentCell()) && grid.remainingFeet()==30,"independent active actors/budgets");
                }
                if(sample>=0) elapsed[sample]=System.nanoTime()-at;
            }
            Arrays.sort(elapsed);dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_MULTI two_encounter_grids n=20 median_us={} p95_us={} max_us={}",elapsed[10]/1000,elapsed[18]/1000,elapsed[19]/1000);
            for(int playerIndex=0;playerIndex<2;playerIndex++) {
                var pc=List.of(p,q).get(playerIndex);var e=encounters.get(playerIndex);
                var request=SrdLiveGameTests.request(e,"grid",new JsonObject());long at=System.nanoTime();var response=SrdNetworking.handle(pc,request);
                check(response.code().equals("GRID"),"real grid handler with authenticated mock player");
                dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_HANDLER grid_us={} response_json_bytes={}",(System.nanoTime()-at)/1000,response.json().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
                check(SrdNetworking.handle(pc,request).code().equals("REJECTED"),"duplicate computation suppressed by request identity");
                check(SrdNetworking.handle(pc,SrdLiveGameTests.request(e,"grid",new JsonObject())).code().equals("GRID"),"second grid admitted independently per player");
                for(int flood=0;flood<40;flood++) check(SrdNetworking.handle(pc,SrdLiveGameTests.request(e,"grid",new JsonObject())).code().equals("REJECTED"),"excessive requests rejected synchronously without pending jobs");
                check(!e.actionUsed() && e.grid(f.dm).remainingFeet()==30,"grid traffic never spends resources");
            }
        } finally {
            for(var pc:List.of(p,q)) {CharacterService.forget(pc.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(pc);}
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=1200)
    public void deterministicReferenceAgreementAndOptionalBenchmark(TestContext ctx) throws Exception {
        boolean benchmark=Boolean.getBoolean("cubetable.srd.benchmark");
        for(var c:CASES) try(var f=new Fixture(ctx,ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(512,65,512)))) {
            var actor=prepare(f,c);var stats=CharacterService.srdStats(actor,f.campaign).stats();
            var old=search(actor,stats,c.feet,true);var current=search(actor,stats,c.feet,false);
            check(old.equals(current),"Complete reachable costs, parents, boundaries and truncation agree: "+c.name);
            if(!benchmark) continue;
            for(int i=0;i<3;i++) {search(actor,stats,c.feet,true);search(actor,stats,c.feet,false);}
            int samples=12;long[][] wall=new long[2][samples],allocated=new long[2][samples],thread=new long[2][samples],serialization=new long[2][samples];
            for(int i=0;i<samples;i++) for(int order=0;order<2;order++) {
                int mode=(i+order)%2;long a=allocation(),t=cpu(),at=System.nanoTime();
                var p=search(actor,stats,c.feet,mode==0);
                wall[mode][i]=System.nanoTime()-at;thread[mode][i]=cpu()-t;allocated[mode][i]=allocation()-a;
                long encodingAt=System.nanoTime();bytes(p);serialization[mode][i]=System.nanoTime()-encodingAt;
                check(p.equals(old),"Warm measured search agrees: "+c.name);
            }
            var reference=new ReferenceSrdTerrain(actor,stats,true);long at=System.nanoTime();SrdMovement.search(reference.currentCell(),c.feet,reference);long profiled=System.nanoTime()-at;
            for(int mode=0;mode<2;mode++) {
                Arrays.sort(wall[mode]);Arrays.sort(allocated[mode]);Arrays.sort(thread[mode]);Arrays.sort(serialization[mode]);
                dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_BENCH case={} mode={} n={} median_us={} p95_us={} max_us={} cpu_median_us={} allocated_median={} nodes={} truncated={} grid_bytes={}",
                    c.name,mode==0?"reference":"current",samples,wall[mode][6]/1000,wall[mode][11]/1000,wall[mode][11]/1000,thread[mode][6]/1000,allocated[mode][6],old.reachable().size(),old.truncated(),bytes(old));
                dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_SERIALIZE case={} mode={} median_us={} p95_us={} max_us={}",c.name,mode==0?"reference":"current",serialization[mode][6]/1000,serialization[mode][11]/1000,serialization[mode][11]/1000);
            }
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_PROFILE case={} steps={} block_reads={} collision_calls={} occupancy_queries={} occupant_decodes={} collision_us={} occupancy_us={} total_us={}",
                c.name,reference.steps,reference.blocks,reference.collisions,reference.occupancyQueries,reference.decodes,reference.collisionNanos/1000,reference.occupancyNanos/1000,profiled/1000);
            var optimized=new SrdTerrain(actor,stats);
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_OPT_PROFILE case={} {}",c.name,optimized.measuredSearch(optimized.currentCell(),c.feet).metrics());
        }
        ctx.complete();
    }
}
