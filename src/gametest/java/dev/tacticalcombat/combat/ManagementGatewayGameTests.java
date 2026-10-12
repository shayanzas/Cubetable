package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.client.ui.ManagementState.args;

public final class ManagementGatewayGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hudSharedRollReadbackKeepsEventIdentityAndPrivateRecipients(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();var manager=ctx.createMockCreativeServerPlayerInWorld();var peer=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);f.repo.invite(f.dm,f.campaign,2,manager.getUuid(),true);f.repo.invite(f.dm,f.campaign,3,peer.getUuid(),false);
            UUID event=UUID.randomUUID();var roll=UiEvents.Roll.from(20,List.of(18,7),1,5,"Private attack",false);
            UiEvents.SESSION.publish(event,f.campaign,null,1,UiEvents.Kind.ATTACK,List.of(player.getUuid()),"Committed roll",roll,UiEvents.Visibility.RECIPIENTS_AND_DM,Set.of(player.getUuid()));
            var a=data(send(player,1,"EVENTS",args("campaign",f.campaign,"cursor",0,"kind",null)));var b=data(send(manager,1,"EVENTS",args("campaign",f.campaign,"cursor",0,"kind",null)));var hidden=data(send(peer,1,"EVENTS",args("campaign",f.campaign,"cursor",0,"kind",null)));
            check(a.get("session").equals(b.get("session")) && a.getAsJsonArray("events").get(0).getAsJsonObject().get("id").getAsString().equals(event.toString()) && b.getAsJsonArray("events").get(0).getAsJsonObject().get("id").getAsString().equals(event.toString()),"authorized clients receive one shared event identity/session");
            var visible=a.getAsJsonArray("events").get(0).getAsJsonObject().getAsJsonObject("roll");check(visible.get("total").getAsInt()==23 && visible.getAsJsonArray("faces").get(0).getAsInt()==18,"HUD sees unchanged server raw face and total");check(hidden.getAsJsonArray("events").isEmpty(),"ordinary unauthorized peer gets no hidden faces or modifiers");
            check(data(send(player,2,"EVENTS",args("campaign",f.campaign,"cursor",a.get("cursor"),"kind",null))).getAsJsonArray("events").isEmpty(),"event cursor does not retransmit an already received roll");
        } finally {cleanup(ctx,player,manager,peer);}ctx.complete();
    }
    private static UiReplyPayload send(ServerPlayerEntity p,long sequence,String op,JsonObject args) {return UiNetworking.handle(p,new UiRequestPayload(sequence,op,args.toString()));}
    private static JsonObject data(UiReplyPayload reply) {check(reply.code().equals("OK"),reply.code()+": "+reply.message());return JsonParser.parseString(reply.json()).getAsJsonObject();}
    private static void cleanup(TestContext ctx,ServerPlayerEntity... players) {for(var p:players) {CharacterService.forget(p.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(p);}}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void managementDiscoveryUsesAuthenticatedRolesAndAuthorizedNames(TestContext ctx) throws Exception {
        var manager=ctx.createMockCreativeServerPlayerInWorld();var member=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,manager.getUuid(),true);f.repo.invite(f.dm,f.campaign,2,member.getUuid(),false);
            var cap=data(send(manager,1,"CAPABILITIES",args()));check(!cap.get("canCreateCampaign").getAsBoolean(),"co-DM is not server administrator");
            var campaign=data(send(manager,2,"CAMPAIGN_GET",args("campaign",f.campaign)));check(campaign.get("manager").getAsBoolean() && !campaign.get("canManageRoles").getAsBoolean(),"manager cannot forge owner role");
            check(campaign.getAsJsonObject("playerNames").has(member.getUuidAsString()),"authorized membership IDs resolve to player names");
            var memberView=data(send(member,1,"CAMPAIGN_GET",args("campaign",f.campaign)));check(!memberView.has("members") && !memberView.has("playerNames"),"ordinary member gets no private roster identities");
            check(send(manager,3,"CAMPAIGN_CREATE",args("name","Forged admin")).code().equals("FORBIDDEN"),"UI role display cannot grant campaign creation");
            var players=data(send(manager,4,"ELIGIBLE_PLAYERS",args("campaign",f.campaign)));check(players.has("players"),"manager can discover online invitations/control candidates");
        } finally {cleanup(ctx,manager,member);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void discoveredEntityIsRevalidatedForBindingAndCannotBeAssignedWhileBound(TestContext ctx) throws Exception {
        var manager=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,manager.getUuid(),true);manager.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var npc=f.actor(3,2,false);var id=f.repo.submit(f.dm,f.campaign,"srd_5_1",SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);
            var candidates=data(send(manager,1,"ELIGIBLE_ENTITIES",args("character",id))).getAsJsonArray("entities");
            boolean found=false;for(var e:candidates) if(e.getAsJsonObject().get("id").getAsString().equals(npc.getUuidAsString())) found=e.getAsJsonObject().get("eligible").getAsBoolean();check(found,"approved NPC candidate found by name/identity readback");
            data(send(manager,2,"BIND",args("character",id,"revision",2,"entity",npc.getUuid())));check(f.repo.binding(id).equals(npc.getUuid()),"binding committed through CharacterService");
            check(!send(manager,3,"ASSIGNMENT_OFFER",args("character",id,"revision",2,"assignmentRevision",0,"player",manager.getUuid())).code().equals("OK"),"binding prevents unsafe control shortcut");
            data(send(manager,4,"UNBIND",args("character",id,"revision",2)));check(f.repo.binding(id)==null && f.repo.character(id).owner().equals(f.dm.id()),"release preserves owner and removes binding");
        } finally {cleanup(ctx,manager);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void staleCandidatesAndUnapprovedCharactersCannotBypassBindingValidation(TestContext ctx) throws Exception {
        var manager=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,manager.getUuid(),true);manager.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var npc=f.actor(3,2,false);var id=f.repo.submit(f.dm,f.campaign,"srd_5_1",SrdAuthoring.template().toString());
            var unapproved=data(send(manager,1,"ELIGIBLE_ENTITIES",args("character",id))).getAsJsonArray("entities");for(var e:unapproved) check(!e.getAsJsonObject().get("eligible").getAsBoolean(),"unapproved candidate is disabled");
            check(!send(manager,2,"BIND",args("character",id,"revision",1,"entity",npc.getUuid())).code().equals("OK"),"client cannot bind unapproved character");
            f.repo.approve(f.dm,id,1);data(send(manager,3,"ELIGIBLE_ENTITIES",args("character",id)));npc.discard();var before=f.repo.state();
            check(!send(manager,4,"BIND",args("character",id,"revision",2,"entity",npc.getUuid())).code().equals("OK"),"entity removed after preview cannot bind");check(before.equals(f.repo.state()),"rejected stale candidate publishes no state");
        } finally {cleanup(ctx,manager);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void offerReadbackDoesNotRevealPrivateSheetBeforeRecipientAcceptance(TestContext ctx) throws Exception {
        var recipient=ctx.createMockCreativeServerPlayerInWorld();var outsider=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,recipient.getUuid(),false);f.repo.invite(f.dm,f.campaign,2,outsider.getUuid(),false);
            var id=f.repo.submit(f.dm,f.campaign,"srd_5_1",SrdAuthoring.template().toString());f.repo.offerControl(f.dm,id,1,0,recipient.getUuid());
            var pending=data(send(recipient,1,"ASSIGNMENT_LIST",args("campaign",f.campaign,"page",0))).getAsJsonArray("assignments");check(pending.size()==1 && pending.get(0).getAsJsonObject().has("name") && !pending.get(0).getAsJsonObject().has("sheet"),"offered recipient sees character label but no private sheet");
            check(send(recipient,2,"CHARACTER_GET",args("character",id)).code().equals("FORBIDDEN"),"pending offer does not grant private read access");
            check(data(send(outsider,1,"ASSIGNMENT_LIST",args("campaign",f.campaign,"page",0))).getAsJsonArray("assignments").isEmpty(),"unrelated member cannot inspect assignments");
            check(!send(outsider,2,"ASSIGNMENT_ANSWER",args("character",id,"revision",1,"accept",true)).code().equals("OK"),"other recipient cannot answer offer");
            data(send(recipient,3,"ASSIGNMENT_ANSWER",args("character",id,"revision",1,"accept",true)));data(send(recipient,4,"CHARACTER_GET",args("character",id)));check(f.repo.character(id).owner().equals(f.dm.id()) && f.repo.binding(id)==null,"acceptance grants read/control without ownership transfer or automatic binding");
        } finally {cleanup(ctx,recipient,outsider);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void presentationPolicyControlsUseTheirOwnRevisionAndManagerAuthority(TestContext ctx) throws Exception {
        var manager=ctx.createMockCreativeServerPlayerInWorld();var member=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,manager.getUuid(),true);f.repo.invite(f.dm,f.campaign,2,member.getUuid(),false);var before=f.repo.state();
            var initial=data(send(manager,1,"PARTY",args("campaign",f.campaign,"page",0))).getAsJsonObject("policy");long revision=initial.get("revision").getAsLong();
            check(send(member,1,"PRESENTATION_POLICY",args("campaign",f.campaign,"revision",revision,"hp","EXACT","radius",32)).code().equals("FORBIDDEN"),"ordinary member cannot change party sharing");
            data(send(manager,2,"PRESENTATION_POLICY",args("campaign",f.campaign,"revision",revision,"hp","BARS","radius",24)));
            var changed=data(send(manager,3,"PARTY",args("campaign",f.campaign,"page",0))).getAsJsonObject("policy");check(changed.get("radius").getAsInt()==24 && changed.get("hp").getAsString().equals("BARS"),"confirmed sharing/radius readback uses existing workflow policy");
            check(send(manager,4,"PRESENTATION_POLICY",args("campaign",f.campaign,"revision",revision,"hp","EXACT","radius",1)).code().equals("CONFLICT"),"stale policy revision cannot overwrite concurrent change");
            check(before.equals(f.repo.state()),"visibility/radius settings do not mutate character approvals, HP or encounter data");
        } finally {cleanup(ctx,manager,member);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void offlineMemberOffersRemainPendingWithoutBindingOrNameCache(TestContext ctx) throws Exception {
        var manager=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            var offline=UUID.randomUUID();f.repo.invite(f.dm,f.campaign,1,manager.getUuid(),true);f.repo.invite(f.dm,f.campaign,2,offline,false);
            var id=f.repo.submit(f.dm,f.campaign,"srd_5_1",SrdAuthoring.template().toString());
            var campaign=data(send(manager,1,"CAMPAIGN_GET",args("campaign",f.campaign)));check(campaign.getAsJsonObject("playerNames").get(offline.toString()).getAsString().equals("Unknown offline player"),"missing headless account cache produces a safe unknown label; GUI must wait for a known name");
            var online=data(send(manager,2,"ELIGIBLE_PLAYERS",args("campaign",f.campaign))).getAsJsonArray("players");for(var e:online) check(!e.getAsJsonObject().get("id").getAsString().equals(offline.toString()),"offline member is distinguished from online entity candidates");
            data(send(manager,3,"ASSIGNMENT_OFFER",args("character",id,"revision",1,"assignmentRevision",0,"player",offline)));
            var offers=data(send(manager,4,"ASSIGNMENT_LIST",args("campaign",f.campaign,"page",0))).getAsJsonArray("assignments");check(offers.size()==1 && f.repo.assignment(id).decision()==CharacterRepository.Decision.PENDING,"offline offer remains pending for explicit future acceptance");
            check(f.repo.controller(id).equals(f.dm.id()) && f.repo.binding(id)==null && f.repo.character(id).owner().equals(f.dm.id()),"offline offer never transfers ownership, current control or binding");
        } finally {cleanup(ctx,manager);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dashboardReadbackKeepsBoundManualTrackersReadOnly(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);var principal=CharacterService.principal(player);
            var id=f.repo.submit(principal,f.campaign,"srd_5_1",SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
            var dashboard=data(send(player,1,"CHARACTER_GET",args("character",id)));check(dashboard.getAsJsonObject("srd").get("valid").getAsBoolean() && dashboard.has("sheet") && dashboard.has("runtimeRevision"),"dashboard reads authoritative snapshot and structured sheet together");
            var change=new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"trackers","",0,1);var before=f.repo.state();var update=args("scope","CAMPAIGN","character",id,"revision",dashboard.get("revision"),"runtimeRevision",dashboard.get("runtimeRevision"),"change",SheetSections.JSON.toJsonTree(change));
            check(send(player,2,"MANUAL_UPDATE",update).code().equals("FORBIDDEN"),"bound dashboard manual tracker remains server-refused");check(f.repo.state().equals(before),"refused dashboard update publishes no character or binding changes");
        } finally {cleanup(ctx,player);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dashboardCampaignSectionEditUsesCasAndApprovalPolicy(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();var outsider=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);var principal=CharacterService.principal(player);var id=f.repo.submit(principal,f.campaign,"srd_5_1",SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);
            var snapshot=data(send(player,1,"CHARACTER_GET",args("character",id)));var identity=SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity");identity.addProperty("biography","Dashboard edit");
            var edit=args("character",id,"revision",snapshot.get("revision"),"runtimeRevision",snapshot.get("runtimeRevision"),"section","identity","data",identity);var saved=data(send(player,2,"CHARACTER_EDIT",edit));check(!saved.get("approved").getAsBoolean() && SheetSections.read(saved.getAsJsonObject("sheet"),"identity").get("biography").getAsString().equals("Dashboard edit"),"confirmed owner section edit follows approval invalidation policy");
            check(send(player,3,"CHARACTER_EDIT",edit).code().equals("CONFLICT"),"stale dashboard revision cannot overwrite confirmed edit");check(send(outsider,1,"CHARACTER_GET",args("character",id)).code().equals("FORBIDDEN"),"unrelated player cannot inspect dashboard sheet");
        } finally {cleanup(ctx,player,outsider);}ctx.complete();
    }
}
