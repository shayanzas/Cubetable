package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

public class SrdTraversalGameTests implements FabricGameTest {
    static Cell cell(Fixture f,int x,int z){return new Cell(f.origin.getX()+x,f.origin.getZ()+z,f.origin.getY()+1);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void grassAlignmentStraightPathsAndRejectionReasons(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            for(int x=0;x<10;x++)for(int z=0;z<8;z++)ctx.getWorld().setBlockState(f.origin.add(x,0,z),Blocks.GRASS_BLOCK.getDefaultState());
            ctx.getWorld().setBlockState(f.origin.add(3,1,2),Blocks.SHORT_GRASS.getDefaultState());
            var a=f.actor(2,2,true);var b=f.actor(8,6,true);var e=begin(f,new Dice(20,1),a,b);var terrain=new SrdTerrain(a,CharacterService.srdStats(a,f.campaign).stats());var grid=e.grid(f.dm);
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0){
                var target=cell(f,2+dx,2+dz);check(grid.reachable().containsKey(target)&&terrain.position(target).equals(a.getPos().add(dx,0,dz)),"grass cell/physical center alignment in eight directions");
            }
            var straight=grid.pathTo(cell(f,7,2));check(straight.size()==5,"five-square straight route");for(int i=0;i<5;i++)check(straight.get(i).equals(cell(f,3+i,2)),"short grass does not block or zigzag cardinal path");
            var diagonal=grid.pathTo(cell(f,6,6));for(int i=0;i<4;i++)check(diagonal.get(i).equals(cell(f,3+i,3+i)),"clear diagonal route coherent");
            check(!grid.reachable().containsKey(cell(f,8,6)),"bound creature logical space blocks destination");
            var wire=SrdGridJobs.grid(grid);check(wire.has("gridBoundaries")&&wire.getAsJsonArray("gridBoundaries").size()<=256,"bounded rejection reasons survive serialization");
            check(wire.getAsJsonArray("gridBoundaries").asList().stream().anyMatch(v->v.getAsJsonArray().get(5).getAsString().equals("OCCUPIED")),"occupied approach reason present on wire");
            ctx.getWorld().setBlockState(f.origin.add(3,1,2),Blocks.STONE.getDefaultState());ctx.getWorld().setBlockState(f.origin.add(3,2,2),Blocks.STONE.getDefaultState());
            check(terrain.step(terrain.currentCell(),1,0).reason()!=Reason.OK,"two-block walking ascent rejected");
            check(terrain.step(terrain.currentCell(),1,1).reason()!=Reason.OK,"cannot cut diagonal corner beside two-block wall");
            ctx.getWorld().setBlockState(f.origin.add(3,2,2),Blocks.AIR.getDefaultState());var up=terrain.step(terrain.currentCell(),1,0);
            check(up.reason()==Reason.OK&&up.to().y()==terrain.currentCell().y()+1&&up.feet()==5,"one-block ascent remains supported and charged normally");
            check(terrain.step(up.to(),-1,0).to().equals(terrain.currentCell()),"one-block descent returns exact anchor");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void committedNpcStepsAllPacesAndCancellation(TestContext ctx)throws Exception{
        for(int pace:new int[]{1,2,4,6})try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(8,6,true);var e=begin(f,new Dice(20,1),a,b);
            check(!e.snapshot(f.dm).has("motion"),"no historical animation at turn start");
            check(e.move(f.dm,UUID.randomUUID(),cell(f,3,3),pace).accepted(),"diagonal paced route accepted");
            check(!e.snapshot(f.dm).has("motion"),"route acceptance does not invent uncommitted visual steps");
            e.tick();var first=e.snapshot(f.dm).getAsJsonObject("motion");check(first.get("sequence").getAsLong()==1&&first.get("ticks").getAsInt()==pace,"first completed step includes actual cadence");
            check(a.getPos().equals(new net.minecraft.util.math.Vec3d(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5)),"server still commits exact square without fractional authority");
            for(int i=1;i<pace;i++)e.tick();check(e.snapshot(f.dm).getAsJsonObject("motion").get("sequence").getAsLong()==1,"pace wait does not duplicate committed movement");
            e.tick();check(e.grid(f.dm).remainingFeet()==20&&e.snapshot(f.dm).getAsJsonObject("motion").get("sequence").getAsLong()==2,"preview/execution budget remains ten feet for two diagonals");
            check(e.move(f.dm,UUID.randomUUID(),cell(f,4,3),pace).accepted(),"split route accepted");e.cancelMove(f.dm);e.tick();
            check(e.snapshot(f.dm).getAsJsonObject("motion").get("sequence").getAsLong()==2&&e.grid(f.dm).remainingFeet()==20,"cancel before step has no visual step or spending");
            e.endTurn(f.dm);check(!e.snapshot(f.dm).has("motion"),"next actor has independent step identity");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void localPlayerUsesSameConfirmedStepContract(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);CharacterService.select(player,f.campaign);var who=CharacterService.principal(player);
            UUID id=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
            player.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);var b=f.actor(7,6,true);var e=begin(f,new Dice(20,1),player,b);
            check(e.move(who,UUID.randomUUID(),cell(f,2,1),4).accepted(),"player explicit route accepted");e.tick();var motion=e.snapshot(who).getAsJsonObject("motion");
            check(motion.get("sequence").getAsLong()==1&&motion.get("ticks").getAsInt()==4,"player teleport publishes same render-only segment as NPC");
            check(motion.getAsJsonArray("from").get(0).getAsDouble()==f.origin.getX()+1.5&&motion.getAsJsonArray("to").get(0).getAsDouble()==player.getX(),"player segment ends at true authoritative position");
            check(e.grid(who).remainingFeet()==25,"player cost remains five");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
}
