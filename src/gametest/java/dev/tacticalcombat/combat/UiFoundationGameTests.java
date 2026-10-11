package dev.tacticalcombat.combat;
import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
public class UiFoundationGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void uiGatewayChecksAuthenticatedPermissionReplayAndBounds(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            var args=new JsonObject();args.addProperty("campaign",f.campaign.toString());
            var request=new UiRequestPayload(1,"CAMPAIGN_GET",args.toString());
            check(UiNetworking.handle(player,request).code().equals("FORBIDDEN"),"authenticated outsider cannot inspect campaign");
            check(UiNetworking.handle(player,request).code().equals("ALREADY_PROCESSED"),"same request cannot replay");
            check(!UiNetworking.handle(player,new UiRequestPayload(2,"CAPABILITIES","x".repeat(UiRequestPayload.LIMIT+1))).code().equals("OK"),"oversized direct request denied");
            args.addProperty("admin",true);check(!UiNetworking.handle(player,new UiRequestPayload(3,"CAMPAIGN_GET",args.toString())).code().equals("OK"),"forged administrator field rejected");
        } finally {ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void assignedPlayerControlsBoundActorWithoutOwningOrEditingIt(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            SrdLiveGameTests.capability(player);var principal=CharacterService.principal(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);CharacterService.select(player,f.campaign);
            player.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var character=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,character,1);
            f.repo.offerControl(f.dm,character,2,0,player.getUuid());f.repo.answerControl(principal,character,1,true);f.repo.bind(f.dm,character,2,player.getUuid(),player.getUuid());
            var npc=f.actor(5,2,true);var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),player,npc);
            check(e.snapshot(principal).getAsJsonArray("actors").get(0).getAsJsonObject().get("controlled").getAsBoolean(),"substitute has authoritative actor control");
            e.grid(principal);check(e.current()==player,"assigned player can request grid for active actor");
            rejects(()->{try {f.repo.clearControl(f.dm,character,2);} catch(java.io.IOException storage) {throw new AssertionError(storage);}},"active binding blocks control replacement");
            rejects(()->CharacterService.select(player,f.campaign),"ordinary campaign switch remains combat locked");
            check(f.repo.character(character).owner().equals(f.dm.id()),"ownership remains original");
            e.end();
        } finally {CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void partyPrivacyAndRestEligibilityUseLiveWorldState(TestContext ctx) throws Exception {
        var a=ctx.createMockCreativeServerPlayerInWorld();var b=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,a.getUuid(),false);f.repo.invite(f.dm,f.campaign,2,b.getUuid(),false);
            var id=f.repo.submit(CharacterService.principal(a),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,a.getUuid(),a.getUuid());
            var npc=f.actor(4,2,true);var second=f.actor(6,2,true);
            var privateView=UiPresentation.party(b,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,0,UiWorkflowStore.HpVisibility.PRIVATE,16)).getAsJsonArray("members");
            check(privateView.size()==1 && !privateView.get(0).getAsJsonObject().has("hp"),"party excludes NPC enemies and private peer HP");
            var bars=UiPresentation.party(b,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,1,UiWorkflowStore.HpVisibility.BARS,16)).getAsJsonArray("members").get(0).getAsJsonObject();
            check(bars.has("healthFraction") && !bars.has("maximumHp"),"bar policy does not expose exact HP fields");
            check(UiPresentation.party(a,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,0,UiWorkflowStore.HpVisibility.PRIVATE,16)).getAsJsonArray("members").get(0).getAsJsonObject().has("hp"),"owner retains exact private HP");
            String dimension=ctx.getWorld().getRegistryKey().getValue().toString();a.setPosition(0.5,64,0.5);b.setPosition(1.5,64,0.5);
            UiPresentation.eligible(a,f.campaign,List.of(a.getUuid(),b.getUuid()),dimension,0,64,0,16);
            b.setPosition(100.5,64,0.5);rejects(()->UiPresentation.eligible(a,f.campaign,List.of(a.getUuid(),b.getUuid()),dimension,0,64,0,16),"distant party cannot approve rest");
            var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),npc,second);
            rejects(()->UiPresentation.initiative(b,e.id()),"campaign membership alone does not expose a separate encounter");
        } finally {CharacterService.forget(a.getUuid());CharacterService.forget(b.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(a);ctx.getWorld().getServer().getPlayerManager().remove(b);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void committedAttackFeedsOnePrivateStructuredRoll(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(2,2,true);var target=f.actor(3,2,true);var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1,18),actor,target);
            var intent=e.preview(f.dm,target.getUuid(),SrdEquipment.Weapon.UNARMED,SrdRules.AttackMode.MELEE,SrdCharacter.Ability.STR,false);var result=e.confirm(f.dm,intent.id());
            var events=UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.ATTACK).events();
            check(events.size()==1 && events.getFirst().id().equals(intent.id()),"committed action emits one stable event");
            check(events.getFirst().roll().total()==result.roll.roll().total() && events.getFirst().roll().faces().equals(result.roll.roll().faces()),"presentation matches authoritative RNG");
            rejects(()->e.confirm(f.dm,intent.id()),"duplicate accepted intent cannot reroll");
            check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.ATTACK).events().size()==1,"retry cannot duplicate event");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void restGatewayApprovesAdjudicationWithoutRecoveringResources(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();var dm=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);f.repo.invite(f.dm,f.campaign,2,dm.getUuid(),true);
            var args=new JsonObject();args.addProperty("campaign",f.campaign.toString());args.addProperty("request",UUID.randomUUID().toString());args.addProperty("kind","SHORT");
            var request=UiNetworking.handle(player,new UiRequestPayload(1,"REST_REQUEST",args.toString()));check(request.code().equals("OK"),"rest request committed: "+request.message());
            var review=new JsonObject();review.add("request",args.get("request"));review.addProperty("revision",1);review.addProperty("approve",true);
            check(!UiNetworking.handle(player,new UiRequestPayload(2,"REST_REVIEW",review.toString())).code().equals("OK"),"requester cannot approve rest");
            var before=f.repo.state();var response=UiNetworking.handle(dm,new UiRequestPayload(1,"REST_REVIEW",review.toString()));
            check(response.code().equals("OK") && response.json().contains("APPROVED_FOR_ADJUDICATION"),"DM approval is explicit manual adjudication");
            check(f.repo.state().equals(before),"rest review cannot heal, recover slots or mutate characters");
            var buf=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),ctx.getWorld().getRegistryManager());
            try {rejects(()->{try {UiRequestPayload.CODEC.encode(buf,new UiRequestPayload(3,"CAPABILITIES","x".repeat(UiRequestPayload.LIMIT+1)));} catch(io.netty.handler.codec.EncoderException expected) {throw new IllegalArgumentException(expected);}},"actual packet codec bounds");} finally {buf.release();}
        } finally {ctx.getWorld().getServer().getPlayerManager().remove(player);ctx.getWorld().getServer().getPlayerManager().remove(dm);}
        ctx.complete();
    }
}
