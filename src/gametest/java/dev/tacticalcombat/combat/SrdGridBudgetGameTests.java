package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdGridPerformanceGameTests.*;

public class SrdGridBudgetGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=1200)
    public void incrementalGeometryMatchesFrozenSearchAndMeasuresSlices(TestContext ctx) throws Exception {
        for(int feet:new int[]{30,60,120,300}) try(var f=new Fixture(ctx,ctx.getAbsolutePos(new net.minecraft.util.math.BlockPos(512,65,512)))) {
            var actor=prepare(f,new Scenario("budget"+feet,feet==300?80:40,feet,feet==300?2:4,"MEDIUM",feet==300?"flat":"weighted"));
            if(feet==300) actor.setPosition(f.origin.getX()+40.5,f.origin.getY()+1,f.origin.getZ()+40.5);
            var stats=CharacterService.srdStats(actor,f.campaign).stats();var terrain=new SrdTerrain(actor,stats);
            var expected=search(actor,stats,feet,true);
            int samples=Boolean.getBoolean("cubetable.srd.benchmark")?12:1;
            for(int sample=-3;sample<samples;sample++) {
                long syncAt=System.nanoTime();var sync=terrain.search(terrain.currentCell(),feet);long syncTime=System.nanoTime()-syncAt;
                check(sync.equals(expected),"synchronous fixture agrees with frozen search");
                var slices=new ArrayList<Long>();long a=allocation(),cpuAt=cpu(),at=System.nanoTime();int ticks=0;
                try(var job=terrain.pending(terrain.currentCell(),feet)) {
                    boolean done=false;
                    while(!done) {
                        long tickAt=System.nanoTime(),deadline=tickAt+SrdGridJobs.BUDGET_NANOS;
                        do {done=job.advance(Math.min(deadline,System.nanoTime()+1_000_000));} while(!done && System.nanoTime()<deadline);
                        slices.add(System.nanoTime()-tickAt);ticks++;
                        check(ticks<=100,"bounded search completes before expiry");
                    }
                    check(job.result().equals(expected),"yielded geometry exactly matches frozen weighted search");
                }
                long wall=System.nanoTime()-at,allocated=allocation()-a,thread=cpu()-cpuAt;
                long finalSlice=slices.getLast();slices.sort(Long::compare);
                if(sample>=0) dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_BUDGET feet={} sample={} sync_us={} work_us={} cpu_us={} allocated={} slices={} slice_median_us={} slice_p95_us={} slice_max_us={} scheduled_latency_ms={}",
                        feet,sample,syncTime/1000,wall/1000,thread/1000,allocated,ticks,slices.get(slices.size()/2)/1000,
                        slices.get(Math.min(slices.size()-1,(int)Math.ceil(slices.size()*.95)-1))/1000,slices.getLast()/1000,
                        (ticks-1)*50+finalSlice/1_000_000);
            }
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pendingSearchInvalidationAndBounds(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(2,2,true);var stats=CharacterService.srdStats(actor,f.campaign).stats();var t=new SrdTerrain(actor,stats);
            try(var job=t.pending(t.currentCell(),120)) {
                check(!job.advance(System.nanoTime()+1),"expired work deadline yields");
                ctx.getWorld().setBlockState(f.origin.add(5,1,5),Blocks.STONE.getDefaultState(),2);
                rejects(()->job.advance(Long.MAX_VALUE),"chunk mutation invalidates pending search even without neighbor notifications");
            }
            var other=f.actor(4,4,true);
            try(var job=t.pending(t.currentCell(),120)) {
                job.advance(System.nanoTime()+1);other.setPosition(other.getPos().add(1,0,0));
                rejects(()->job.advance(Long.MAX_VALUE),"current occupancy invalidates pending search");
            }
            var jobs=new ArrayList<SrdTerrain.Pending>();
            try {for(int i=0;i<8;i++) jobs.add(t.pending(t.currentCell(),300));rejects(()->t.pending(t.currentCell(),300),"global pending cap");}
            finally {jobs.forEach(SrdTerrain.Pending::close);}
            try(var job=t.pending(t.currentCell(),120)) {check(!job.advance(System.nanoTime()+1),"closed jobs release capacity");}
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void queueSupersessionCompletionAndTurnCancellation(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            SrdLiveGameTests.capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),true);
            var actor=prepare(f,new Scenario("queued",12,120,2,"MEDIUM","flat"));
            var other=f.entities.stream().filter(e->e!=actor).findFirst().orElseThrow();
            var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),actor,other);
            var replies=new ArrayList<dev.tacticalcombat.net.SrdStatePayload>();
            UUID first=UUID.randomUUID(),second=UUID.randomUUID();
            SrdGridJobs.submit(player,e,first);SrdGridJobs.submit(player,e,second);
            check(SrdGridJobs.size()==1,"superseded job removed rather than queued");
            for(int i=0;i<100 && SrdGridJobs.size()>0;i++) SrdGridJobs.tick((p,response)->replies.add(response));
            check(replies.size()==1 && replies.getFirst().code().equals("GRID") && replies.getFirst().json().contains(second.toString())
                    && !replies.getFirst().json().contains(first.toString()),"only latest request completes");
            SrdGridJobs.submit(player,e,UUID.randomUUID());e.endTurn(f.dm);
            SrdGridJobs.tick((p,response)->replies.add(response));
            check(SrdGridJobs.size()==0 && replies.getLast().code().equals("GRID_CANCELLED"),"turn change cancels old job");
            SrdGridJobs.submit(player,e,UUID.randomUUID());SrdGridJobs.cancel(player.getUuid());
            check(SrdGridJobs.size()==0,"disconnect/cancel path releases jobs");
            SrdGridJobs.submit(player,e,UUID.randomUUID());e.end();SrdGridJobs.tick((p,response)->replies.add(response));
            check(SrdGridJobs.size()==0 && replies.getLast().code().equals("GRID_CANCELLED"),"encounter cleanup cancels job");
        } finally {SrdGridJobs.clear();CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
}
