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
}
