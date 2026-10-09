package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.CharacterRequestPayload;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

public class SrdCharacterLifecycleGameTests implements FabricGameTest {
    private static void check(boolean value,String why) { if(!value) throw new IllegalStateException(why); }
    private static final class Fixture implements AutoCloseable {
        final TestContext ctx; final Path path; final java.lang.reflect.Field field; final Object old;
        final List<ServerPlayerEntity> players=new ArrayList<>();
        Fixture(TestContext ctx) throws Exception {
            this.ctx=ctx; path=Files.createTempDirectory("cubetable-srd-lifecycle-").resolve("state.json");
            field=CharacterService.class.getDeclaredField("repository"); field.setAccessible(true); old=field.get(null);
            field.set(null,new CharacterRepository(path));
        }
        CharacterRepository repo() { return CharacterService.repository(); }
        ServerPlayerEntity player() { var player=ctx.createMockCreativeServerPlayerInWorld(); players.add(player); return player; }
        public void close() throws Exception {
            for(var player:players) { CharacterService.forget(player.getUuid()); ctx.getWorld().getServer().getPlayerManager().remove(player); }
            field.set(null,old);
            try(var files=Files.walk(path.getParent())) { for(Path file:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file); }
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void typedCreationUsesActualPrivateCharacterHandler(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var dm=f.player(); var owner=f.player(); var outsider=f.player();
            var admin=new CharacterRepository.Principal(dm.getUuid(),true);
            UUID campaign=f.repo().createCampaign(admin,"Authoring");
            f.repo().invite(admin,campaign,1,owner.getUuid(),false); f.repo().profile(admin,campaign,2,RulesProfiles.SRD);
            CharacterService.select(owner,campaign);
            var discovery=CharacterNetworking.handle(owner,new CharacterRequestPayload(1,"campaigns","{}"),100);
            check(JsonParser.parseString(discovery.json()).getAsJsonObject().get("srdAuthoringVersion").getAsInt()==1,"server advertises typed authoring capability");
            var request=new CharacterRequestPayload(2,"srd_create","{\"approved\":true,\"hp\":999}");
            var reply=CharacterNetworking.handle(owner,request,120);
            check(reply.code().equals("OK"),"typed character creation through production handler");
            JsonObject state=JsonParser.parseString(reply.json()).getAsJsonObject(); UUID id=UUID.fromString(state.get("id").getAsString());
            check(!state.get("approved").getAsBoolean() && state.getAsJsonObject("srd").get("hp").getAsInt()==10,"forged approval and derived inputs ignored");
            check(CharacterNetworking.handle(owner,request,140).code().equals("ALREADY_PROCESSED") && f.repo().state().characters().size()==1,"creation replay cannot duplicate character");
            var get=new JsonObject(); get.addProperty("character",id.toString());
            var privateRead=CharacterNetworking.handle(outsider,new CharacterRequestPayload(1,"get",get.toString()),160);
            check(privateRead.code().equals("FORBIDDEN") && privateRead.json().equals("{}"),"unrelated viewer cannot inspect SRD data");
            var form=SrdAuthoring.change(state.getAsJsonObject("sheet"),SrdAuthoring.fields().stream().filter(v->v.key().equals("speed_feet")).findFirst().orElseThrow(),"25");
            var changes=new JsonObject(); changes.add("collections.srd_combat",form.getAsJsonObject("collections").get("srd_combat"));
            var edit=new JsonObject(); edit.addProperty("character",id.toString()); edit.addProperty("revision",1); edit.addProperty("runtimeRevision",0); edit.add("changes",changes);
            check(CharacterNetworking.handle(owner,new CharacterRequestPayload(3,"edit",edit.toString()),180).code().equals("OK"),"typed form uses existing revision-checked edit protocol");
            var approval=new JsonObject(); approval.addProperty("character",id.toString()); approval.addProperty("revision",2);
            check(CharacterNetworking.handle(owner,new CharacterRequestPayload(4,"approve",approval.toString()),200).code().equals("FORBIDDEN"),"ordinary owner cannot approve");
            check(CharacterNetworking.handle(dm,new CharacterRequestPayload(1,"approve",approval.toString()),220).code().equals("OK"),"campaign DM approves through existing handler");
            CharacterService.bind(dm,id,3,owner);
            var fresh=CharacterNetworking.handle(owner,new CharacterRequestPayload(5,"get",get.toString()),240);
            var srd=JsonParser.parseString(fresh.json()).getAsJsonObject().getAsJsonObject("srd");
            check(srd.get("characterReady").getAsBoolean() && srd.get("speedFeet").getAsInt()==25,"fresh private snapshot reports approved bound SRD fields");
            check(!srd.get("worldCombatEnabled").getAsBoolean(),"eligible character alone never enables unfinished encounters");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void persistedSrdResourcesStaySeparateFromVanillaAndSurviveReload(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var dm=f.player(); var admin=new CharacterRepository.Principal(dm.getUuid(),true);
            UUID campaign=f.repo().createCampaign(admin,"Resources");
            UUID id=f.repo().submit(admin,campaign,RulesProfiles.SRD,SrdAuthoring.template().toString()); f.repo().approve(admin,id,1);
            var mob=ctx.spawnEntity(EntityType.ZOMBIE,new BlockPos(2,2,2)); mob.setAiDisabled(true);
            try {
                mob.setHealth(7); CharacterService.bind(dm,id,2,mob);
                var before=CharacterService.srdStats(mob,campaign);
                var after=f.repo().commitSrdHealth(before,new SrdHealth(4,10,2));
                check(mob.getHealth()==7 && mob.getMaxHealth()==20,"SRD persistence never applies vanilla damage or HP modifiers");
                mob.setHealth(1); CharacterService.captureEntity(mob);
                check(CharacterService.srdStats(mob,campaign).equals(after),"vanilla capture cannot reset D&D resources");
                f.field.set(null,new CharacterRepository(f.path)); CharacterService.apply(mob);
                check(CharacterService.srdStats(mob,campaign).equals(after),"normal reload keeps authoritative HP/temp and runtime revision");
                CharacterService.unbind(dm,mob); CharacterService.bind(dm,id,2,mob);
                check(CharacterService.srdStats(mob,campaign).equals(after),"unbind/rebind does not restore older sheet HP");
                var zero=f.repo().commitSrdHealth(after,new SrdHealth(0,10,3));
                check(mob.isAlive() && mob.getHealth()==1,"zero D&D HP does not kill the Minecraft entity");
                check(!new CharacterSyncService(f.repo()).snapshot(admin,id).getAsJsonObject("srd").get("characterReady").getAsBoolean(),"zero HP remains ineligible even with temporary HP");
                boolean rejected=false;
                try { f.repo().commitSrdHealth(after,new SrdHealth(10,10,0)); } catch(IllegalArgumentException expected) { rejected=true; }
                check(rejected && CharacterService.srdStats(mob,campaign).equals(zero),"stale runtime update cannot heal or apply twice");
                check(!CombatManager.isInCombat(mob) && !RulesProfiles.get(RulesProfiles.SRD).worldCombat(),"resource tests do not fabricate a playable encounter");
            } finally { mob.discard(); }
        }
        ctx.complete();
    }
}
