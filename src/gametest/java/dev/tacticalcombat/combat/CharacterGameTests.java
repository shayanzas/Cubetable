package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.*;
import java.util.*;
import java.nio.file.*;

public class CharacterGameTests implements FabricGameTest {
    private static void check(boolean b,String why) { if(!b) throw new IllegalStateException(why); }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void approvedCharacterControlsCombatAndRejectsForgedChanges(TestContext ctx) throws Exception {
        // Isolate repository data; use the production service/world adapter and real mock server player.
        var field=CharacterService.class.getDeclaredField("repository"); field.setAccessible(true);
        Object old=field.get(null); Path dir=Files.createTempDirectory("cubetable-character-world-");
        var repo=new CharacterRepository(dir.resolve("state.json")); field.set(null,repo);
        var player=ctx.createMockCreativeServerPlayerInWorld();
        var enemy=ctx.spawnEntity(EntityType.ZOMBIE,new BlockPos(3,2,2)); enemy.setAiDisabled(true);
        var start=ctx.getAbsolute(new Vec3d(1.5,2,2.5)); player.refreshPositionAndAngles(start.x,start.y,start.z,0,0);
        Combat combat=null;
        try {
            var admin=new CharacterRepository.Principal(player.getUuid(),true);
            UUID campaign=repo.createCampaign(admin,"Integration"); CharacterService.select(player,campaign);
            UUID other=repo.createCampaign(admin,"Separate campaign");
            check(!CharacterService.mayJoin(player,other,RulesProfiles.get(RulesProfiles.TACTICAL)),"selected campaigns isolate players");
            UUID id=repo.submit(admin,campaign,RulesProfiles.TACTICAL,"{\"format\":\"generic_d20\",\"values\":{\"hp\":30,\"hp_max\":40,\"movement_blocks\":3,\"initiative_bonus\":100}}");
            repo.approve(admin,id,1);
            // The production service authenticates the requesting campaign owner.
            CharacterService.bind(player,id,2,player);
            check(player.getHealth()==30,"binding applies approved current HP");
            check(player.getMaxHealth()==40,"binding updates vanilla max HP");
            check(!CharacterService.mayJoin(player,campaign,RulesProfiles.get(RulesProfiles.SRD)),"incompatible bound profiles are rejected");
            combat=Combat.start(ctx.getWorld(),List.of(player),List.of(enemy));
            check(combat.rules().id().equals(RulesProfiles.TACTICAL) && combat.campaign().equals(campaign),"encounter snapshots profile/campaign");
            check(combat.isTurnOf(player) && combat.get(player).initiative>=101,"initiative comes from approved server stats");
            check(combat.current().moveBudget==3,"approved movement affects actual turn resources");
            boolean denied=false;
            try { repo.stat(new CharacterRepository.Principal(UUID.randomUUID(),false),id,2,"movement_blocks",30); }
            catch(IllegalArgumentException expected) { denied=true; }
            check(denied && combat.current().moveBudget==3,"forged principal cannot affect active combat");
            denied=false;
            try { CharacterService.release(player,id,2); }
            catch(IllegalArgumentException expected) { denied=true; }
            check(denied && CharacterService.bound(player)!=null,"cannot release binding during combat");
            player.setHealth(12); CharacterService.capture(ctx.getWorld().getServer());
            check(repo.character(id).stats().get("hp")==12,"vanilla damage state captured into server character");
            repo.profile(admin,campaign,1,RulesProfiles.MANUAL);
            check(combat.rules().worldCombat(),"changing campaign default does not mutate active encounter rules");
            combat.end(); combat=null;
            check(CombatManager.startAround(ctx.getWorld(),player,false)==null,"manual default disables new automatic world combat");
            CharacterService.unbind(player,player);
            check(player.getMaxHealth()==20,"unbind removes only character max HP modifier");
            check(new CharacterRepository(dir.resolve("state.json")).character(id).stats().get("hp")==12,"server health survives repository reload");
            UUID missingActor=UUID.randomUUID();
            repo.bind(admin,id,repo.character(id).revision(),missingActor,null);
            CharacterService.release(player,id,repo.character(id).revision());
            check(!repo.state().bindings().containsKey(missingActor),"DM can release removed or offline actor binding");
        } finally {
            if(combat!=null) combat.end();
            CharacterService.forget(player.getUuid()); ctx.getWorld().getServer().getPlayerManager().remove(player);
            field.set(null,old);
            try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void submissionCodecHasNoPrivilegeFields(TestContext ctx) {
        var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),ctx.getWorld().getRegistryManager());
        try {
            var request=new dev.tacticalcombat.net.SubmitCharacterPayload(UUID.randomUUID(),"{\"format\":\"percentile\"}");
            dev.tacticalcombat.net.SubmitCharacterPayload.CODEC.encode(buffer,request);
            check(request.equals(dev.tacticalcombat.net.SubmitCharacterPayload.CODEC.decode(buffer)),"draft/request identity codec round trip");
            check(!buffer.isReadable(),"entire payload consumed");
        } finally { buffer.release(); }
        ctx.complete();
    }
}
