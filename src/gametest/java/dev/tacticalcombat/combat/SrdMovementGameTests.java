package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.*;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SrdMovementGameTests implements FabricGameTest {
    private static void check(boolean value,String why) { if(!value) throw new IllegalStateException(why); }
    private static final class Fixture implements AutoCloseable {
        final TestContext ctx; final Path dir; final java.lang.reflect.Field field; final Object old;
        final CharacterRepository repo; final CharacterRepository.Principal dm;
        final UUID campaign,encounter=UUID.randomUUID(); final BlockPos origin;
        final List<LivingEntity> entities=new ArrayList<>(); final JsonObject sheet;
        Fixture(TestContext ctx) throws Exception {
            this.ctx=ctx; dir=Files.createTempDirectory("cubetable-srd-move-");
            field=CharacterService.class.getDeclaredField("repository"); field.setAccessible(true); old=field.get(null);
            repo=new CharacterRepository(dir.resolve("state.json")); field.set(null,repo);
            dm=new CharacterRepository.Principal(UUID.randomUUID(),true); campaign=repo.createCampaign(dm,"Movement");
            origin=ctx.getAbsolutePos(new BlockPos(0,1,0));
            for(int x=0;x<=9;x++) for(int z=0;z<=9;z++) {
                block(x,0,z,Blocks.STONE.getDefaultState());
                for(int y=1;y<=6;y++) block(x,y,z,Blocks.AIR.getDefaultState());
            }
            try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/srd-test-character.json"))) {
                sheet=JsonParser.parseString(new String(in.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
        JsonObject data() { return sheet.getAsJsonObject("collections").getAsJsonArray("srd_combat").get(0).getAsJsonObject(); }
        void block(int x,int y,int z,BlockState state) { ctx.getWorld().setBlockState(origin.add(x,y,z),state,3); }
        <T extends MobEntity> T actor(EntityType<T> type,int x,int z) throws Exception {
            T actor=type.create(ctx.getWorld());
            actor.refreshPositionAndAngles(origin.getX()+x+.5,origin.getY()+1,origin.getZ()+z+.5,0,0);
            actor.setAiDisabled(true); ctx.getWorld().spawnEntity(actor); entities.add(actor);
            UUID id=repo.submit(dm,campaign,RulesProfiles.SRD,sheet.toString()); repo.approve(dm,id,1); repo.bind(dm,id,2,actor.getUuid(),null);
            return actor;
        }
        SrdTerrain terrain(LivingEntity actor) { return new SrdTerrain(actor,CharacterService.srdStats(actor,campaign).stats()); }
        SrdMovementTurn.Frame frame(LivingEntity actor,long turn) { return new SrdMovementTurn.Frame(encounter,campaign,RulesProfiles.SRD,turn,actor.getUuid()); }
        SrdMovementTurn.Request request(LivingEntity actor,Cell destination,long turn,UUID id) {
            return new SrdMovementTurn.Request(encounter,campaign,RulesProfiles.SRD,turn,actor.getUuid(),id,destination);
        }
        Cell cell(int x,int z,double relativeY) { return new Cell(origin.getX()+x,origin.getZ()+z,origin.getY()+relativeY); }
        public void close() throws Exception {
            for(var entity:entities) entity.discard(); field.set(null,old);
            try(var paths=Files.walk(dir)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void eightDirectionsAndSixSquareBudget(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(EntityType.ZOMBIE,3,3); var terrain=f.terrain(actor); Cell start=terrain.currentCell();
            check(Grid.reachable(ctx.getWorld(),Grid.cellOf(actor),1,Set.of()).nodes.size()==5,"Tactical grid retains only four neighbors");
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(dx!=0 || dz!=0) {
                Step step=terrain.step(start,dx,dz); check(step.reason()==Reason.OK && step.feet()==5,"all eight neighbors normal cost: "+step);
            }
            var turn=new SrdMovementTurn(actor,()->f.frame(actor,1));
            var preview=turn.preview(f.dm);
            check(preview.reachable().get(f.cell(9,3,1)).costFeet()==30,"approved 30-foot budget gives six squares");
            check(preview.remainingFeet()==30 && preview.pathTo(f.cell(9,3,1)).size()==6,"preview route and budget");
            var request=f.request(actor,f.cell(9,3,1),1,UUID.randomUUID());
            check(turn.request(f.dm,request).accepted(),"server destination accepted");
            for(int i=0;i<6;i++) check(turn.tick().accepted(),"validated grid step executes");
            check(turn.spentFeet()==30 && turn.remainingFeet()==0 && actor.getPos().equals(terrain.position(f.cell(9,3,1))),"grid cost charged exactly");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void ascentDescentSlabsStairsAndHeadroom(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(EntityType.ZOMBIE,3,3); var terrain=f.terrain(actor); var start=terrain.currentCell();
            f.block(4,1,3,Blocks.STONE.getDefaultState());
            Step up=terrain.step(start,1,0); check(up.reason()==Reason.OK && up.feet()==5 && up.to().y()==start.y()+1,"ordinary ascent");
            Step down=terrain.step(up.to(),-1,0); check(down.reason()==Reason.OK && down.feet()==5 && down.to().equals(start),"ordinary descent");
            f.block(4,1,3,Blocks.STONE_SLAB.getDefaultState().with(SlabBlock.TYPE,SlabType.BOTTOM));
            Step slab=terrain.step(start,1,0); check(slab.reason()==Reason.OK && slab.to().y()==start.y()+.5,"bottom slab");
            f.block(4,1,3,Blocks.STONE_BRICK_STAIRS.getDefaultState());
            Step stairs=terrain.step(start,1,0); check(stairs.reason()==Reason.OK && stairs.feet()==5,"stair surface");
            f.block(3,3,3,Blocks.STONE.getDefaultState());
            check(terrain.step(start,1,0).reason()!=Reason.OK,"rise needs source headroom");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void diagonalCornersWallsFencesAndRemovedFloors(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(EntityType.ZOMBIE,3,3); var terrain=f.terrain(actor); var start=terrain.currentCell();
            f.block(4,1,3,Blocks.STONE.getDefaultState()); f.block(4,2,3,Blocks.STONE.getDefaultState());
            check(terrain.step(start,1,1).reason()!=Reason.OK,"cannot clip blocked diagonal corner");
            check(terrain.step(start,1,0).reason()!=Reason.OK,"two-block wall is not ordinary walking");
            f.block(4,2,3,Blocks.AIR.getDefaultState()); f.block(4,1,3,Blocks.OAK_FENCE.getDefaultState());
            check(terrain.step(start,1,0).reason()!=Reason.OK,"cannot stand on fence as walking");
            f.block(4,1,3,Blocks.AIR.getDefaultState());
            for(int y=0;y>=-2;y--) f.block(4,y,3,Blocks.AIR.getDefaultState());
            check(terrain.step(start,1,0).reason()!=Reason.OK,"removed floor cannot become unsupported falling route");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void largeFootprintsHeightAndNarrowCorridors(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            f.data().addProperty("size","large"); var actor=f.actor(EntityType.ZOMBIE,3,3);
            actor.refreshPositionAndAngles(f.origin.getX()+4,f.origin.getY()+1,f.origin.getZ()+4,0,0);
            var terrain=f.terrain(actor); var start=terrain.currentCell();
            check(terrain.landing(start)==Reason.OK && terrain.step(start,1,0).reason()==Reason.OK,"large logical footprint works on open floor");
            f.block(5,1,4,Blocks.STONE.getDefaultState()); f.block(5,2,4,Blocks.STONE.getDefaultState());
            check(terrain.step(start,1,0).reason()!=Reason.OK,"logical footprint stops a small physical zombie clipping at its far edge");
            f.block(5,1,4,Blocks.AIR.getDefaultState()); f.block(5,2,4,Blocks.AIR.getDefaultState());
            for(int y=0;y>=-2;y--) f.block(5,y,4,Blocks.AIR.getDefaultState());
            check(terrain.step(start,1,0).reason()!=Reason.OK,"every large footprint column requires support");
            actor.discard(); f.data().addProperty("size","medium"); var tall=f.actor(EntityType.ENDERMAN,2,2);
            f.block(3,3,2,Blocks.STONE.getDefaultState());
            check(f.terrain(tall).step(f.terrain(tall).currentCell(),1,0).reason()!=Reason.OK,"physical tall-entity headroom respected");
            tall.discard();
            var wide=f.actor(EntityType.RAVAGER,2,6); var wideTerrain=f.terrain(wide);
            f.block(4,1,5,Blocks.STONE.getDefaultState()); f.block(4,2,5,Blocks.STONE.getDefaultState());
            check(wideTerrain.step(wideTerrain.currentCell(),1,0).reason()!=Reason.OK,"physical width larger than logical footprint still checked");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void movingOccupantsInvalidateRouteAndControlledSpace(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(EntityType.ZOMBIE,1,3); var blocker=f.actor(EntityType.ZOMBIE,7,7);
            var turn=new SrdMovementTurn(actor,()->f.frame(actor,1)); var terrain=f.terrain(actor);
            Cell destination=f.cell(4,3,1);
            check(turn.request(f.dm,f.request(actor,destination,1,UUID.randomUUID())).accepted(),"initial unoccupied route");
            Vec3d occupied=terrain.position(destination); blocker.refreshPositionAndAngles(occupied.x,occupied.y,occupied.z,0,0);
            check(!turn.tick().accepted() && turn.spentFeet()==0,"current occupancy invalidates whole remaining route before spending");
            check(terrain.landing(destination)==Reason.OCCUPIED,"occupied destination classified");
            blocker.discard(); f.data().addProperty("size","large"); var large=f.actor(EntityType.ZOMBIE,4,3);
            large.refreshPositionAndAngles(f.origin.getX()+5,f.origin.getY()+1,f.origin.getZ()+4,0,0);
            check(terrain.landing(f.cell(4,4,1))==Reason.OCCUPIED,"large controlled space blocks beyond literal hitbox");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void interruptedRoutesKeepCompletedCostsWithoutPartialRefunds(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(EntityType.ZOMBIE,1,3); var turn=new SrdMovementTurn(actor,()->f.frame(actor,1));
            var request=f.request(actor,f.cell(5,3,1),1,UUID.randomUUID());
            check(turn.request(f.dm,request).accepted() && turn.tick().accepted(),"first square completes");
            check(turn.spentFeet()==5 && turn.remainingFeet()==25,"only completed step charged");
            f.block(5,1,3,Blocks.STONE.getDefaultState()); f.block(5,2,3,Blocks.STONE.getDefaultState());
            check(!turn.tick().accepted() && turn.spentFeet()==5,"late wall cancels without untraveled charges");
            check(turn.cancel(f.dm).accepted() && turn.spentFeet()==5,"cancel cannot refund completed step");
            check(!turn.request(f.dm,request).accepted(),"cancel cannot replay old identity");
            f.block(5,1,3,Blocks.AIR.getDefaultState()); f.block(5,2,3,Blocks.AIR.getDefaultState());
            check(turn.request(f.dm,f.request(actor,f.cell(5,3,1),1,UUID.randomUUID())).accepted(),"new route uses remaining allowance");
            Vec3d previous=actor.getPos(); actor.refreshPositionAndAngles(previous.x+.2,previous.y+.3,previous.z,0,0);
            check(!turn.tick().accepted() && actor.getPos().equals(previous) && turn.spentFeet()==5,"unauthorized partial displacement rolls back without refund");
            var preview=turn.preview(f.dm); var path=preview.pathTo(f.cell(5,3,1)); Cell intermediate=path.getFirst();
            check(turn.request(f.dm,f.request(actor,f.cell(5,3,1),1,UUID.randomUUID())).accepted(),"route before support removal");
            for(int dy=0;dy>=-2;dy--) ctx.getWorld().setBlockState(BlockPos.ofFloored(intermediate.x(),intermediate.y()-1+dy,intermediate.z()),Blocks.AIR.getDefaultState(),3);
            check(!turn.tick().accepted() && turn.spentFeet()==5,"removed intermediate support cancels pending route");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void identityTurnCampaignAndRulesetAreRechecked(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(EntityType.ZOMBIE,3,3); var frame=new java.util.concurrent.atomic.AtomicReference<>(f.frame(actor,1));
            var turn=new SrdMovementTurn(actor,frame::get); Cell to=f.cell(4,3,1); UUID id=UUID.randomUUID();
            var stranger=new CharacterRepository.Principal(UUID.randomUUID(),false);
            check(!turn.request(stranger,f.request(actor,to,1,id)).accepted(),"unauthorized requester rejected");
            check(!turn.request(f.dm,f.request(actor,to,0,id)).accepted(),"stale turn rejected");
            check(!turn.request(f.dm,new SrdMovementTurn.Request(f.encounter,UUID.randomUUID(),RulesProfiles.SRD,1,actor.getUuid(),id,to)).accepted(),"cross campaign rejected");
            check(!turn.request(f.dm,new SrdMovementTurn.Request(UUID.randomUUID(),f.campaign,RulesProfiles.SRD,1,actor.getUuid(),id,to)).accepted(),"cross encounter rejected");
            check(!turn.request(f.dm,new SrdMovementTurn.Request(f.encounter,f.campaign,RulesProfiles.TACTICAL,1,actor.getUuid(),id,to)).accepted(),"wrong ruleset rejected");
            check(!turn.request(f.dm,new SrdMovementTurn.Request(f.encounter,f.campaign,RulesProfiles.SRD,1,UUID.randomUUID(),id,to)).accepted(),"forged actor rejected");
            check(turn.request(f.dm,f.request(actor,to,1,id)).accepted(),"correct request accepted");
            check(!turn.request(f.dm,f.request(actor,to,1,id)).accepted(),"duplicate rejected");
            frame.set(new SrdMovementTurn.Frame(f.encounter,f.campaign,RulesProfiles.SRD,1,UUID.randomUUID()));
            check(!turn.tick().accepted() && turn.spentFeet()==0,"out-of-turn queued route never executes");
            check(!turn.request(f.dm,f.request(actor,to,1,UUID.randomUUID())).accepted(),"closed stale controller cannot restart");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void speedArmorAndDifficultTerrain(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            f.data().addProperty("speed_feet",4); var slow=f.actor(EntityType.ZOMBIE,3,3);
            var turn=new SrdMovementTurn(slow,()->f.frame(slow,1));
            check(turn.preview(f.dm).reachable().size()==1 && !turn.request(f.dm,f.request(slow,f.cell(4,3,1),1,UUID.randomUUID())).accepted(),"four feet cannot finish a square");
            slow.discard(); f.data().addProperty("speed_feet",0); var stopped=f.actor(EntityType.ZOMBIE,3,3);
            check(new SrdMovementTurn(stopped,()->f.frame(stopped,1)).preview(f.dm).reachable().size()==1,"zero speed");
            stopped.discard(); f.data().addProperty("speed_feet",30); f.data().addProperty("armor","chain_mail"); f.data().getAsJsonObject("abilities").addProperty("str",10);
            var heavy=f.actor(EntityType.ZOMBIE,3,3); var heavyTurn=new SrdMovementTurn(heavy,()->f.frame(heavy,1));
            check(heavyTurn.remainingFeet()==20,"Stage A armor speed penalty retained");
            f.block(4,0,3,Blocks.SOUL_SAND.getDefaultState());
            var terrain=f.terrain(heavy); Step difficult=terrain.step(terrain.currentCell(),1,0);
            check(difficult.reason()==Reason.OK && difficult.feet()==10,"soul sand passable difficult terrain, not a blocked hazard");
            check(terrain.step(difficult.to(),-1,0).feet()==5,"exit into normal terrain costs five");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void authenticatedPlayerMovementAndBindingLoss(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var player=ctx.createMockCreativeServerPlayerInWorld();
            try {
                player.refreshPositionAndAngles(f.origin.getX()+3.5,f.origin.getY()+1,f.origin.getZ()+3.5,45,10);
                var principal=CharacterService.principal(player);
                check(!principal.admin(),"test connection is an ordinary player");
                f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);
                UUID id=f.repo.submit(principal,f.campaign,RulesProfiles.SRD,f.sheet.toString());
                f.repo.approve(f.dm,id,1); f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
                var turn=new SrdMovementTurn(player,()->f.frame(player,1));
                check(turn.request(principal,f.request(player,f.cell(4,3,1),1,UUID.randomUUID())).accepted(),"authenticated owner may move own bound actor");
                check(turn.tick().accepted() && turn.spentFeet()==5,"actual server player teleport spends one step");
                check(player.getYaw()==45 && player.getPitch()==10,"movement preserves camera look");
                player.getAbilities().flying=true;
                check(!turn.request(principal,f.request(player,f.cell(5,3,1),1,UUID.randomUUID())).accepted(),"unsupported flying cannot enter walking path");
                player.getAbilities().flying=false;
                check(turn.request(principal,f.request(player,f.cell(5,3,1),1,UUID.randomUUID())).accepted(),"second route accepted");
                f.repo.unbind(f.dm,player.getUuid());
                check(!turn.tick().accepted() && turn.spentFeet()==5,"binding loss closes queued movement without refund");
                f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
                UUID other=f.repo.createCampaign(f.dm,"Other"); f.repo.invite(f.dm,other,1,player.getUuid(),false);
                CharacterService.select(player,other);
                boolean denied=false;
                try { new SrdMovementTurn(player,()->f.frame(player,2)); } catch(IllegalArgumentException expected) { denied=true; }
                check(denied,"selected campaign mismatch cannot start a movement turn");
            } finally { CharacterService.forget(player.getUuid()); ctx.getWorld().getServer().getPlayerManager().remove(player); }
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void environmentBorderAndUnloadedChunksFailExplicitly(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(EntityType.ZOMBIE,3,3); var terrain=f.terrain(actor); var start=terrain.currentCell();
            for(Block hazard:List.of(Blocks.WATER,Blocks.LAVA,Blocks.FIRE,Blocks.COBWEB,Blocks.POWDER_SNOW)) {
                f.block(4,1,3,hazard.getDefaultState());
                check(terrain.step(start,1,0).reason()==Reason.UNSUPPORTED_ENVIRONMENT,"unsupported environment explicitly labelled: "+hazard);
            }
            f.block(4,1,3,Blocks.AIR.getDefaultState());
            var border=ctx.getWorld().getWorldBorder(); double oldX=border.getCenterX(),oldZ=border.getCenterZ(),size=border.getSize();
            try {
                border.setCenter(actor.getX(),actor.getZ()); border.setSize(2);
                check(terrain.step(start,1,0).reason()==Reason.BORDER,"entire footprint must fit border");
            } finally { border.setCenter(oldX,oldZ); border.setSize(size); }
            Cell remote=new Cell(1000000,1000000,start.y());
            check(!ctx.getWorld().isChunkLoaded(62500,62500),"test destination initially unloaded");
            check(terrain.landing(remote)==Reason.UNLOADED && !ctx.getWorld().isChunkLoaded(62500,62500),"validation does not load remote chunks");
            check(!RulesProfiles.get(RulesProfiles.SRD).worldCombat() && !RulesProfiles.get(RulesProfiles.MANUAL).worldCombat(),"unready and manual engines remain disabled");
            check(RulesProfiles.get(RulesProfiles.TACTICAL).movement(Map.of(),true)==8,"Tactical allowance unchanged");
        }
        ctx.complete();
    }
}
