package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.entity.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

public class CharacterSyncGameTests implements FabricGameTest {
    private static void check(boolean value,String why) { if(!value) throw new IllegalStateException(why); }
    private static final class Fixture implements AutoCloseable {
        final TestContext ctx; final Path dir; final java.lang.reflect.Field field; final Object original;
        final List<ServerPlayerEntity> players=new ArrayList<>();
        Fixture(TestContext ctx) throws Exception {
            this.ctx=ctx; dir=Files.createTempDirectory("cubetable-sync-world-");
            field=CharacterService.class.getDeclaredField("repository"); field.setAccessible(true); original=field.get(null);
            CharacterService.start(ctx.getWorld().getServer(),dir.resolve("state.json"));
        }
        CharacterRepository repo() { return CharacterService.repository(); }
        ServerPlayerEntity player() { var p=ctx.createMockCreativeServerPlayerInWorld(); players.add(p); return p; }
        public void close() throws Exception {
            for(var p:players) { CharacterService.forget(p.getUuid()); ctx.getWorld().getServer().getPlayerManager().remove(p); }
            field.set(null,original);
            try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void requestHandlerEnforcesOwnershipAcknowledgementsAndReplay(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var dm=f.player(); var player=f.player();
            check(!player.hasPermissionLevel(2),"mock ordinary player is not an operator");
            var admin=new CharacterRepository.Principal(dm.getUuid(),true);
            UUID campaign=f.repo().createCampaign(admin,"Network"), other=f.repo().createCampaign(admin,"Private");
            f.repo().invite(admin,campaign,1,player.getUuid(),false);
            var select=CharacterNetworking.handle(player,new CharacterRequestPayload(1,"select","{\"campaign\":\""+campaign+"\"}"),100);
            check(select.code().equals("OK"),"authenticated member can select campaign");
            var args=new JsonObject(); args.addProperty("sheet","{\"format\":\"generic_d20\",\"text\":{\"name\":\"Network hero\"}}"); args.addProperty("approved",true);
            var create=new CharacterRequestPayload(2,"create",args.toString());
            var result=CharacterNetworking.handle(player,create,120);
            check(result.code().equals("OK"),"submission has explicit acknowledgement");
            var snapshot=JsonParser.parseString(result.json()).getAsJsonObject(); UUID id=UUID.fromString(snapshot.get("id").getAsString());
            check(!snapshot.get("approved").getAsBoolean(),"packet approval flag cannot grant approval");
            check(CharacterNetworking.handle(player,create,140).code().equals("ALREADY_PROCESSED") && f.repo().state().characters().size()==1,"replay cannot create duplicate records");
            var approve=new JsonObject(); approve.addProperty("character",id.toString()); approve.addProperty("revision",1);
            check(CharacterNetworking.handle(player,new CharacterRequestPayload(3,"approve",approve.toString()),160).code().equals("FORBIDDEN"),"ordinary owner cannot self approve");
            UUID privateId=f.repo().submit(admin,other,RulesProfiles.TACTICAL,"{\"format\":\"generic_d20\",\"text\":{\"name\":\"DM secret\"}}");
            var denied=CharacterNetworking.handle(player,new CharacterRequestPayload(4,"get","{\"character\":\""+privateId+"\"}"),180);
            check(denied.code().equals("FORBIDDEN") && !denied.json().contains("secret"),"private character never returned to unrelated client");
            var approved=CharacterNetworking.handle(dm,new CharacterRequestPayload(1,"approve",approve.toString()),200);
            check(approved.code().equals("OK") && f.repo().character(id).approved(),"DM reviews and approves through same handler");
            var fresh=CharacterNetworking.handle(player,new CharacterRequestPayload(5,"get","{\"character\":\""+id+"\"}"),220);
            check(JsonParser.parseString(fresh.json()).getAsJsonObject().get("approved").getAsBoolean(),"owner refresh observes DM approval");
            var patch=new JsonObject(); patch.addProperty("character",id.toString()); patch.addProperty("revision",1); patch.addProperty("runtimeRevision",0);
            var changes=new JsonObject(); changes.addProperty("values.movement_blocks",4); patch.add("changes",changes);
            check(CharacterNetworking.handle(player,new CharacterRequestPayload(6,"edit",patch.toString()),240).code().equals("CONFLICT"),"handler reports stale edit explicitly");
            patch.addProperty("revision",2);
            check(CharacterNetworking.handle(player,new CharacterRequestPayload(7,"edit",patch.toString()),260).code().equals("OK"),"current patch updates existing draft");
            check(f.repo().state().characters().size()==2 && !f.repo().character(id).approved(),"edit does not duplicate or preserve unauthorized approval");
            var dispatcher=ctx.getWorld().getServer().getCommandManager().getDispatcher();
            CharacterService.select(dm,campaign);
            String policyCommand="tbc campaign policy 2 false false true text.*";
            check(dispatcher.execute(policyCommand,player.getCommandSource())==0,"ordinary player denied policy command");
            check(dispatcher.execute(policyCommand,dm.getCommandSource())==1,"campaign owner may set policy through command tree");
            check(f.repo().campaign(campaign).policy().ownerLocked(),"command policy persisted");
            // Old cached/replayed requests do not disclose a stale private snapshot.
            check(CharacterNetworking.handle(player,new CharacterRequestPayload(5,"get","{}"),280).json().equals("{}"),"replay acknowledgement contains no private cached data");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void lifecycleCaptureRestartReloadAndModifierCleanup(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var dm=f.player(); var admin=new CharacterRepository.Principal(dm.getUuid(),true);
            UUID campaign=f.repo().createCampaign(admin,"Lifecycle");
            UUID id=f.repo().submit(admin,campaign,RulesProfiles.TACTICAL,"{\"format\":\"generic_d20\",\"values\":{\"hp\":30,\"hp_max\":40}}");
            f.repo().approve(admin,id,1);
            var mob=ctx.spawnEntity(EntityType.ZOMBIE,new BlockPos(2,2,2)); mob.setAiDisabled(true);
            CharacterService.bind(dm,id,2,mob); CharacterService.apply(mob); CharacterService.apply(mob);
            check(mob.getMaxHealth()==40,"repeated application cannot duplicate health modifiers");
            mob.setHealth(13);
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.invoker().onUnload(mob,ctx.getWorld());
            check(f.repo().character(id).stats().get("hp")==13,"entity unload captures latest HP before the periodic tick");
            var nbt=new net.minecraft.nbt.NbtCompound(); mob.writeNbt(nbt);
            var copy=EntityType.ZOMBIE.create(ctx.getWorld()); copy.readNbt(nbt);
            CharacterService.stop(ctx.getWorld().getServer());
            CharacterService.start(ctx.getWorld().getServer(),f.dir.resolve("state.json"));
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.invoker().onLoad(copy,ctx.getWorld());
            check(copy.getMaxHealth()==40 && copy.getHealth()==13,"service restart plus NBT reload preserves one modifier and vanilla HP");
            check(f.repo().binding(id).equals(mob.getUuid()) && f.repo().character(id).runtimeRevision()>0,"binding and runtime revision survive service restart");
            mob.setHealth(7); CharacterService.unbind(dm,mob);
            check(f.repo().character(id).stats().get("hp")==7,"unbind captures final HP immediately");
            CharacterService.apply(copy);
            check(copy.getMaxHealth()==20,"returning stale NBT modifier removed after release");
            UUID playerId=f.repo().submit(admin,campaign,RulesProfiles.TACTICAL,"{\"format\":\"generic_d20\",\"values\":{\"hp\":30,\"hp_max\":40}}");
            f.repo().approve(admin,playerId,1); CharacterService.bind(dm,playerId,2,dm); dm.setHealth(9);
            var fresh=CharacterNetworking.handle(dm,new CharacterRequestPayload(1,"get","{\"character\":\""+playerId+"\"}"),500);
            check(fresh.code().equals("OK") && JsonParser.parseString(fresh.json()).getAsJsonObject().getAsJsonObject("stats").get("hp").getAsInt()==9,"refresh captures live HP before periodic persistence");
            CharacterService.forget(dm.getUuid());
            CharacterService.apply(dm);
            check(dm.getHealth()==9 && dm.getMaxHealth()==40,"join reconciliation does not heal or duplicate modifier");
            dm.setHealth(0); CharacterService.captureEntity(dm);
            check(f.repo().character(playerId).stats().get("hp")==0,"vanilla zero HP remains zero without D&D death-save substitution");
            var respawn=ctx.getWorld().getServer().getPlayerManager().respawnPlayer(dm,false,Entity.RemovalReason.KILLED); f.players.add(respawn);
            check(respawn.getMaxHealth()==40 && respawn.getHealth()>0,"actual vanilla respawn reapplies bound maximum health");
            CharacterService.unbind(respawn,respawn);
            check(respawn.getMaxHealth()==20,"respawned actor can release binding without stale modifier");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void profileOverridesSelectionAndLegacyStayIsolated(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var player=f.player(); var outsider=f.player();
            var admin=new CharacterRepository.Principal(player.getUuid(),true);
            UUID campaign=f.repo().createCampaign(admin,"Manual default");
            CharacterService.select(player,campaign); f.repo().profile(admin,campaign,1,RulesProfiles.MANUAL);
            check(!CharacterService.mayJoin(outsider,campaign,RulesProfiles.get(RulesProfiles.TACTICAL)),"unbound legacy player cannot cross campaign boundary");
            check(CharacterService.context(outsider).campaign()==null && CharacterService.context(outsider).profile().equals(RulesProfiles.TACTICAL),"unbound legacy remains tactical");
            var enemy=ctx.spawnEntity(EntityType.ZOMBIE,new BlockPos(2,2,2)); enemy.setAiDisabled(true);
            Combat combat=Combat.start(ctx.getWorld(),List.of(player),List.of(enemy),new CharacterService.Context(campaign,RulesProfiles.TACTICAL));
            try {
                check(combat.rules().worldCombat() && combat.rules().id().equals(RulesProfiles.TACTICAL),"explicit tactical override works for manual campaign");
                boolean denied=false; try { CharacterService.select(player,campaign); } catch(IllegalArgumentException e) { denied=true; }
                check(denied,"campaign selection cannot change during encounter");
                f.repo().profile(admin,campaign,2,RulesProfiles.SRD);
                check(combat.rules().id().equals(RulesProfiles.TACTICAL),"default change cannot mutate active encounter profile");
            } finally { combat.end(); }
            check(CombatManager.startAround(ctx.getWorld(),player,false)==null,"SRD default cannot run automated world combat");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void synchronizationCodecsRoundTripAndRejectInvalidBounds(TestContext ctx) {
        var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),ctx.getWorld().getRegistryManager());
        try {
            var request=new CharacterRequestPayload(1,"get","{}"); CharacterRequestPayload.CODEC.encode(buffer,request);
            check(request.equals(CharacterRequestPayload.CODEC.decode(buffer)) && !buffer.isReadable(),"request codec consumes entire message");
            var reply=new CharacterReplyPayload(1,"CONFLICT","Refresh required","{}"); CharacterReplyPayload.CODEC.encode(buffer,reply);
            check(reply.equals(CharacterReplyPayload.CODEC.decode(buffer)) && !buffer.isReadable(),"reply codec round trip");
            buffer.clear(); buffer.writeLong(0); buffer.writeString("get"); buffer.writeString("{}");
            boolean denied=false; try { CharacterRequestPayload.CODEC.decode(buffer); } catch(IllegalArgumentException e) { denied=true; }
            check(denied,"invalid sequence rejected at decoder");
            buffer.clear(); denied=false;
            try { CharacterRequestPayload.CODEC.encode(buffer,new CharacterRequestPayload(2,"edit","x".repeat(CharacterRequestPayload.LIMIT+1))); }
            catch(RuntimeException e) { denied=true; }
            check(denied,"oversized outbound request rejected");
            buffer.clear(); buffer.writeLong(2); buffer.writeString("get"); buffer.writeVarInt(CharacterRequestPayload.LIMIT*3+1);
            denied=false; try { CharacterRequestPayload.CODEC.decode(buffer); } catch(RuntimeException e) { denied=true; }
            check(denied,"oversized inbound string rejected before allocation");
        } finally { buffer.release(); }
        ctx.complete();
    }
}
