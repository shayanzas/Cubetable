package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.client.ui.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.net.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

public final class DmWorkspaceRedesignGameTests implements FabricGameTest {
    private static long sequence=1000;
    private static JsonObject read(ServerPlayerEntity p,UiApiService.Operation op,JsonObject args){var world=p.getServer().getOverworld();((net.minecraft.world.level.ServerWorldProperties)world.getLevelProperties()).setTime(world.getTime()+21);var reply=UiNetworking.handle(p,new UiRequestPayload(++sequence,op.name(),args.toString()));check(reply.code().equals("OK"),op+": "+reply.code()+" "+reply.message());return JsonParser.parseString(reply.json()).getAsJsonObject();}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void boundFirstPlayerHuskAndBatsUseCurrentDiscovery(TestContext ctx)throws Exception {
        var p=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(p);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);f.repo.invite(f.dm,f.campaign,2,p.getUuid(),true);CharacterService.select(p,f.campaign);p.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            var principal=CharacterService.principal(p);var pc=f.repo.submit(principal,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,pc,1);f.repo.bind(f.dm,pc,2,p.getUuid(),p.getUuid());
            var husk=EntityType.HUSK.create(ctx.getWorld());husk.setAiDisabled(true);husk.refreshPositionAndAngles(f.origin.getX()+4.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);ctx.getWorld().spawnEntity(husk);f.entities.add(husk);
            read(p,MONSTER_PREPARE,args("actor",husk.getUuid(),"source","srd51:monsters:zombie","operation",UUID.randomUUID()));
            var bats=new ArrayList<String>();for(int i=0;i<4;i++){var bat=EntityType.BAT.create(ctx.getWorld());bat.setAiDisabled(true);bat.refreshPositionAndAngles(f.origin.getX()+7.5,f.origin.getY()+2,f.origin.getZ()+2.5+i,0,0);ctx.getWorld().spawnEntity(bat);f.entities.add(bat);bats.add(bat.getUuidAsString());}
            var model=new DmWorkspaceModel();model.campaign(f.campaign.toString());model.tab(DmWorkspaceModel.Tab.ACTORS);
            var boundQuery=model.discovery();var bound=read(p,ENCOUNTER_DISCOVER,boundQuery.args());check(model.applyDiscovery(boundQuery,bound),"production response has matching campaign and query context");
            check(array(bound,"actors").size()==2&&bats.stream().noneMatch(id->DmWorkspaceModel.find(array(bound,"actors"),"actor",id)!=null),"Bound snapshot contains player/prepared Husk, not incidental bats");
            model.toggle(DmWorkspaceModel.find(array(bound,"actors"),"actor",p.getUuidAsString()));model.toggle(DmWorkspaceModel.find(array(bound,"actors"),"actor",husk.getUuidAsString()));check(model.startable(bound),"one manager selects legitimate player and prepared NPC");
            var old=args("discovery",model.discoveryToken,"actors",new Gson().toJsonTree(model.participants));
            var preparedRow=DmWorkspaceModel.find(array(bound,"actors"),"actor",husk.getUuidAsString());check(preparedRow.has("character")&&preparedRow.has("characterRevision"),"authorized discovery supplies stable binding provenance for stale-selection review");
            model.binding="UNBOUND";var unboundQuery=model.discovery();var unbound=read(p,ENCOUNTER_DISCOVER,unboundQuery.args());model.applyDiscovery(unboundQuery,unbound);check(bats.stream().allMatch(id->DmWorkspaceModel.find(array(unbound,"actors"),"actor",id)!=null),"fresh Unbound read exposes intentional preparation candidates");check(model.participants.size()==2&&!model.startable(unbound),"remembered actors cannot start from unrelated discovery");
            rejects(()->EncounterLauncher.handle(p,ENCOUNTER_START,old),"server rejects old token after browsing a different snapshot");
            model.binding="ALL";var allQuery=model.discovery();var all=read(p,ENCOUNTER_DISCOVER,allQuery.args());model.applyDiscovery(allQuery,all);check(model.startable(all)&&array(all,"actors").size()>=6,"All refresh includes prepared pair and incidental actors without auto selection");
            model.binding="BOUND";var returnQuery=model.discovery();var current=read(p,ENCOUNTER_DISCOVER,returnQuery.args());model.applyDiscovery(returnQuery,current);check(model.startable(current)&&!model.discoveryToken.equals(text(bound,"discovery")),"returning Bound revalidates both participants with fresh token");
            var started=read(p,ENCOUNTER_START,args("discovery",model.discoveryToken,"actors",new Gson().toJsonTree(model.participants)));var encounter=SrdNetworking.find(UUID.fromString(text(started,"encounter")));f.encounters.add(encounter);
            var state=encounter.presentation(principal);check(array(state,"actors").asList().stream().allMatch(v->flag(v.getAsJsonObject(),"controlled")),"same manager legitimately controls both actor types");check(SrdBattlefieldPresentationOrder(state),"workspace uses server-provided initiative order including pending ties");
            read(p,ENCOUNTER_WATCH,args("encounter",encounter.id()));read(p,ENCOUNTER_END,args("encounter",encounter.id()));check(!CombatManager.isInCombat(husk)&&!CombatManager.isInCombat(p),"explicit end returns claims without workspace movement or automatic start");
            var beforeRebind=model.discovery();model.applyDiscovery(beforeRebind,read(p,ENCOUNTER_DISCOVER,beforeRebind.args()));
            f.repo.unbind(f.dm,husk.getUuid());read(p,MONSTER_PREPARE,args("actor",husk.getUuid(),"source","srd51:monsters:zombie","operation",UUID.randomUUID()));
            var afterRebind=model.discovery();model.applyDiscovery(afterRebind,read(p,ENCOUNTER_DISCOVER,afterRebind.args()));check(!model.participants.contains(husk.getUuidAsString())&&model.selectionNotice.contains("Associated character changed"),"actual rebind on same entity cannot silently reuse participant selection");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid());rejects(()->EncounterLauncher.handle(p,ENCOUNTER_DISCOVER,boundQuery.args()),"revoked manager cannot fetch cached actor domain");
        }finally{UiNetworking.forget(p.getUuid());CharacterService.forget(p.getUuid());p.getServer().getPlayerManager().remove(p);}ctx.complete();
    }
    private static boolean SrdBattlefieldPresentationOrder(JsonObject state){var ordered=dev.tacticalcombat.client.SrdBattlefieldPresentation.initiative(state);var ids=array(state,"initiativeOrder");if(ids.isEmpty())return ordered.isEmpty();for(int i=0;i<ids.size();i++)if(!text(ordered.get(i),"id").equals(ids.get(i).getAsString()))return false;return true;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void discoveryScopeCannotRevivePriorCampaign(TestContext ctx)throws Exception {
        var p=ctx.createMockCreativeServerPlayerInWorld();try(var f=new Fixture(ctx)){
            capability(p);f.repo.invite(f.dm,f.campaign,1,p.getUuid(),true);CharacterService.select(p,f.campaign);p.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);f.actor(4,1,true);
            var model=new DmWorkspaceModel();model.campaign(f.campaign.toString());model.tab(DmWorkspaceModel.Tab.ACTORS);var old=model.discovery();
            var other=f.repo.createCampaign(f.dm,"Other scope");f.repo.invite(f.dm,other,1,p.getUuid(),true);CharacterService.select(p,other);
            var response=read(p,ENCOUNTER_DISCOVER,old.args());check(text(response,"campaign").equals(other.toString())&&!model.applyDiscovery(old,response),"actual server campaign stamp rejects new-scope data for old UI request");check(model.discoveryToken.isEmpty()&&model.participants.isEmpty(),"scope mismatch has no executable token or leaked selection");
        }finally{UiNetworking.forget(p.getUuid());CharacterService.forget(p.getUuid());p.getServer().getPlayerManager().remove(p);}ctx.complete();
    }
}
