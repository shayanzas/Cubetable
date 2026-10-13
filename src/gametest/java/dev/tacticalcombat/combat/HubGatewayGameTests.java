package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.uiapi.UiNetworking;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;

public class HubGatewayGameTests implements FabricGameTest {
    private static JsonObject campaign(UUID id,boolean quick) {var a=new JsonObject();a.addProperty("campaign",id.toString());if(quick) a.addProperty("quick",true);return a;}
    private static UiReplyPayload send(net.minecraft.server.network.ServerPlayerEntity player,long seq,String op,JsonObject args) {return UiNetworking.handle(player,new UiRequestPayload(seq,op,args.toString()));}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void quickSwitchRequiresTargetDmWhileOrdinaryNavigationStillWorks(TestContext ctx) throws Exception {
        var member=ctx.createMockCreativeServerPlayerInWorld();var dm=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,member.getUuid(),false);f.repo.invite(f.dm,f.campaign,2,dm.getUuid(),true);var before=f.repo.state();
            var denied=send(member,1,"CAMPAIGN_SELECT",campaign(f.campaign,true));check(denied.code().equals("FORBIDDEN"),"forged hub quick-switch cannot grant a member DM privileges");
            check(send(member,2,"CAMPAIGN_SELECT",campaign(f.campaign,false)).code().equals("OK"),"member can use ordinary campaign browser selection");
            check(send(dm,1,"CAMPAIGN_SELECT",campaign(f.campaign,true)).code().equals("OK"),"authorized co-DM can quick-switch");
            check(CharacterService.selected(member).equals(f.campaign) && CharacterService.selected(dm).equals(f.campaign),"selections use authenticated requester context");
            check(before.equals(f.repo.state()),"hub selection changes no persistent stats, approvals or bindings");
            var active=JsonParser.parseString(send(member,3,"ACTIVE",new JsonObject()).json()).getAsJsonObject();check(active.get("campaign").getAsString().equals(f.campaign.toString()) && !active.get("inCombat").getAsBoolean(),"active readback acknowledges actual selection");
        } finally {CharacterService.forget(member.getUuid());CharacterService.forget(dm.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(member);ctx.getWorld().getServer().getPlayerManager().remove(dm);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void revokedDmRoleCannotUseStaleHubPermission(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),true);
            check(JsonParser.parseString(send(player,1,"CAMPAIGN_GET",campaign(f.campaign,false)).json()).getAsJsonObject().get("manager").getAsBoolean(),"manager read model comes from server role");
            f.repo.revokeDm(f.dm,f.campaign,2,player.getUuid());
            check(send(player,2,"CAMPAIGN_SELECT",campaign(f.campaign,true)).code().equals("FORBIDDEN"),"stale client manager display cannot authorize quick-switch");
            var refreshed=JsonParser.parseString(send(player,3,"CAMPAIGN_GET",campaign(f.campaign,false)).json()).getAsJsonObject();check(!refreshed.get("manager").getAsBoolean() && !refreshed.has("dms"),"refreshed role removes private manager fields");
        } finally {CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void campaignAndCharacterSelectionKeepActiveEncounterLocks(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            SrdLiveGameTests.capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),true);CharacterService.select(player,f.campaign);
            player.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var character=f.repo.submit(CharacterService.principal(player),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,character,1);f.repo.bind(f.dm,character,2,player.getUuid(),player.getUuid());
            var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),player,f.actor(5,2,true));var before=f.repo.state();long turn=e.turn();
            check(!send(player,1,"CAMPAIGN_SELECT",campaign(f.campaign,true)).code().equals("OK"),"DM campaign selection locked in encounter");
            var args=new JsonObject();args.addProperty("character",character.toString());args.addProperty("revision",2);
            check(!send(player,2,"CHARACTER_SELECT",args).code().equals("OK"),"active character selection locked in encounter");
            var active=JsonParser.parseString(send(player,3,"ACTIVE",new JsonObject()).json()).getAsJsonObject();check(active.get("inCombat").getAsBoolean() && active.get("boundCharacter").getAsString().equals(character.toString()),"readback exposes lock and current binding");
            check(before.equals(f.repo.state()) && e.turn()==turn && !e.actionUsed(),"rejected hub requests spend no action and change no resource or binding");
        } finally {CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void reconnectRestoresAuthorizedSelectionWithoutBinding(TestContext ctx)throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);
            UUID character=f.repo.submit(CharacterService.principal(player),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());
            check(send(player,1,"CAMPAIGN_SELECT",campaign(f.campaign,false)).code().equals("OK"),"select campaign");
            var args=new JsonObject();args.addProperty("character",character.toString());args.addProperty("revision",1);
            check(send(player,2,"CHARACTER_SELECT",args).code().equals("OK"),"select character");
            CharacterService.forget(player.getUuid());UiNetworking.forget(player.getUuid());
            var active=JsonParser.parseString(send(player,1,"ACTIVE",new JsonObject()).json()).getAsJsonObject();
            check(active.get("campaign").getAsString().equals(f.campaign.toString())&&active.get("character").getAsString().equals(character.toString()),"reconnect restores authorized IDs");
            check(f.repo.binding(character)==null&&!active.has("boundCharacter"),"reconnect does not physically bind");
            f.repo.removeMember(f.dm,f.campaign,2,player.getUuid());
            CharacterService.forget(player.getUuid());UiNetworking.forget(player.getUuid());
            active=JsonParser.parseString(send(player,1,"ACTIVE",new JsonObject()).json()).getAsJsonObject();
            check(!active.has("campaign")&&!active.has("character"),"revoked membership prevents restoration");
        }finally{CharacterService.forget(player.getUuid());UiNetworking.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void libraryDeletionChecksAuthenticatedOwnerAndRevision(TestContext ctx)throws Exception {
        var owner=ctx.createMockCreativeServerPlayerInWorld();var peer=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            var create=new JsonObject();create.addProperty("profile",RulesProfiles.SRD);create.addProperty("mode","MANUAL");create.add("sheet",JsonNull.INSTANCE);
            var reply=send(owner,1,"LIBRARY_CREATE",create);check(reply.code().equals("OK"),"create private source");var source=JsonParser.parseString(reply.json()).getAsJsonObject();
            var deletion=new JsonObject();deletion.addProperty("character",source.get("id").getAsString());deletion.addProperty("revision",1);
            check(send(peer,1,"LIBRARY_DELETE",deletion).code().equals("FORBIDDEN"),"another authenticated player cannot delete source");
            deletion.addProperty("revision",0);check(send(owner,2,"LIBRARY_DELETE",deletion).code().equals("CONFLICT"),"stale deletion rejected");
            deletion.addProperty("revision",1);var before=f.repo.state();check(send(owner,3,"LIBRARY_DELETE",deletion).code().equals("OK"),"owner confirmed deletion");check(f.repo.state().equals(before),"source deletion never mutates campaign data or bindings");
        }finally{CharacterService.forget(owner.getUuid());CharacterService.forget(peer.getUuid());UiNetworking.forget(owner.getUuid());UiNetworking.forget(peer.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(owner);ctx.getWorld().getServer().getPlayerManager().remove(peer);}
        ctx.complete();
    }

}
