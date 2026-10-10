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
        if(reference) {var t=new ReferenceSrdTerrain(actor,stats);return SrdMovement.search(t.currentCell(),feet,t);}
        var t=new SrdTerrain(actor,stats);return t.search(t.currentCell(),feet);
    }
    static int bytes(SrdMovement.Preview p) {
        var cells=new JsonArray();for(var n:p.reachable().values()) {
            var a=new JsonArray();a.add(n.cell().x());a.add(n.cell().z());a.add(n.cell().y());a.add(n.costFeet());
            if(n.parent()!=null){a.add(n.parent().x());a.add(n.parent().z());a.add(n.parent().y());}cells.add(a);
        }
        return cells.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
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
            int samples=12;long[][] wall=new long[2][samples],allocated=new long[2][samples],thread=new long[2][samples];
            for(int i=0;i<samples;i++) for(int order=0;order<2;order++) {
                int mode=(i+order)%2;long a=allocation(),t=cpu(),at=System.nanoTime();
                var p=search(actor,stats,c.feet,mode==0);
                wall[mode][i]=System.nanoTime()-at;thread[mode][i]=cpu()-t;allocated[mode][i]=allocation()-a;
                check(p.equals(old),"Warm measured search agrees: "+c.name);
            }
            var reference=new ReferenceSrdTerrain(actor,stats,true);long at=System.nanoTime();SrdMovement.search(reference.currentCell(),c.feet,reference);long profiled=System.nanoTime()-at;
            for(int mode=0;mode<2;mode++) {
                Arrays.sort(wall[mode]);Arrays.sort(allocated[mode]);Arrays.sort(thread[mode]);
                dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_BENCH case={} mode={} n={} median_us={} p95_us={} max_us={} cpu_median_us={} allocated_median={} nodes={} truncated={} grid_bytes={}",
                    c.name,mode==0?"reference":"current",samples,wall[mode][6]/1000,wall[mode][11]/1000,wall[mode][11]/1000,thread[mode][6]/1000,allocated[mode][6],old.reachable().size(),old.truncated(),bytes(old));
            }
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_PROFILE case={} steps={} block_reads={} collision_calls={} occupancy_queries={} occupant_decodes={} collision_us={} occupancy_us={} total_us={}",
                c.name,reference.steps,reference.blocks,reference.collisions,reference.occupancyQueries,reference.decodes,reference.collisionNanos/1000,reference.occupancyNanos/1000,profiled/1000);
            var optimized=new SrdTerrain(actor,stats);
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_OPT_PROFILE case={} {}",c.name,optimized.measuredSearch(optimized.currentCell(),c.feet).metrics());
        }
        ctx.complete();
    }
}
