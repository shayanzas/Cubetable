package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

public class SrdDashGameTests implements FabricGameTest {
    static void dash(SrdEncounter e,CharacterRepository.Principal p){e.dash(p,e.campaign(),e.current().getUuid(),e.revision());}
    static SrdRequestPayload request(SrdEncounter e){
        var args=new JsonObject();args.addProperty("campaign",e.campaign().toString());args.addProperty("actor",e.current().getUuidAsString());args.addProperty("revision",e.revision());
        return new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"dash",args.toString());
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void maximumSupportedSpeedDashUsesBudgetedGrid(TestContext ctx)throws Exception {
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,false);var b=f.actor(8,6,true);var sheet=SrdAuthoring.template();SrdAuthoring.data(sheet).addProperty("speed_feet",300);
            UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,a.getUuid(),null);
            var e=begin(f,new Dice(20,1),a,b);dash(e,f.dm);
            check(e.budgetedGrid()&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==600,"maximum supported speed receives full Dash without reducing grant");
            var search=e.pendingGrid(f.dm);while(!search.advance(Long.MAX_VALUE)){}var grid=search.result();search.close();e.completedGrid(f.dm,grid);
            check(grid.remainingFeet()==600&&grid.reachable().size()<=SrdMovement.MAX_NODES,"incremental real terrain preview accepts expanded bounded allowance");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dashBeforeMovementExpiresAndDoesNotTouchPersistentResources(TestContext ctx)throws Exception {
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(8,6,true);var dice=new Dice(20,1);var e=begin(f,dice,a,b);
            var before=CharacterService.srdStats(a,f.campaign);var position=a.getPos();long rev=e.revision();
            dash(e,f.dm);check(e.actionUsed()&&e.grid(f.dm).remainingFeet()==60&&a.getPos().equals(position),"Dash spends action, grants speed, never moves");
            check(e.revision()==rev+1&&dice.rolls==2&&before.equals(CharacterService.srdStats(a,f.campaign)),"no RNG, persistent mutation, or character revision");
            rejects(()->dash(e,f.dm),"new request cannot double grant");
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false),"Dash prevents normal weapon action");
            var cell=new SrdMovement.Cell(f.origin.getX()+8,f.origin.getZ()+1,f.origin.getY()+1);
            check(e.move(f.dm,UUID.randomUUID(),cell).accepted(),"Dash permits a 35-foot route");for(int i=0;i<7;i++)e.tick();
            check(e.grid(f.dm).remainingFeet()==25,"movement beyond original speed charged normally");
            e.endTurn(f.dm);check(!e.actionUsed()&&e.grid(f.dm).remainingFeet()==30,"next actor independent");e.endTurn(f.dm);
            check(!e.actionUsed()&&e.grid(f.dm).remainingFeet()==30,"unused Dash expires on new turn");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dashRejectsExecutingRouteAndAttackSpentAction(TestContext ctx)throws Exception {
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(3,1,true);var e=begin(f,new Dice(20,1,15),a,b);
            var destination=new SrdMovement.Cell(f.origin.getX()+2,f.origin.getZ()+1,f.origin.getY()+1);
            check(e.move(f.dm,UUID.randomUUID(),destination).accepted(),"route accepted");rejects(()->dash(e,f.dm),"cannot Dash during route");
            check(!e.actionUsed(),"rejected Dash costs nothing");e.tick();
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);e.confirm(f.dm,preview.id());
            rejects(()->dash(e,f.dm),"weapon attack spends same action");check(e.grid(f.dm).remainingFeet()==25,"failed Dash after attack preserves ledger");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dashGatewayReplayRevisionAndForgedGrantRejected(TestContext ctx)throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),true);var who=CharacterService.principal(player);
            var a=f.actor(1,1,true);var b=f.actor(8,6,true);var e=begin(f,new Dice(20,1),a,b);
            var stale=request(e);var destination=new SrdMovement.Cell(f.origin.getX()+2,f.origin.getZ()+1,f.origin.getY()+1);
            e.move(who,UUID.randomUUID(),destination);e.tick();
            check(SrdNetworking.handle(player,stale).code().equals("REJECTED")&&!e.actionUsed(),"stale revision rejected without cost");
            var valid=request(e);var forged=JsonParser.parseString(valid.json()).getAsJsonObject();forged.addProperty("grant",999);
            check(SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"dash",forged.toString())).code().equals("REJECTED"),"client cannot supply speed/grant");
            var accepted=SrdNetworking.handle(player,valid);check(accepted.code().equals("DASH"),"authenticated gateway accepts Dash: "+accepted.message());
            check(JsonParser.parseString(accepted.json()).getAsJsonObject().get("remainingFeet").getAsInt()==55&&e.actionUsed(),"five spent then Dash yields 55");
            check(SrdNetworking.handle(player,valid).code().equals("REJECTED")&&e.grid(who).remainingFeet()==55,"lost ack replay rejected without second grant");
            check(SrdNetworking.handle(player,request(e)).code().equals("REJECTED")&&e.grid(who).remainingFeet()==55,"new ID cannot bypass spent action");
            var grid=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"grid","{}"));
            check(grid.code().equals("GRID")&&JsonParser.parseString(grid.json()).getAsJsonObject().get("remainingFeet").getAsInt()==55,"fresh server grid uses expanded allowance");
            e.endTurn(who);check(SrdNetworking.handle(player,valid).code().equals("REJECTED")&&!e.actionUsed(),"old turn packet cannot spend next actor action");
            var badScope=request(e);var args=JsonParser.parseString(badScope.json()).getAsJsonObject();args.addProperty("actor",a.getUuidAsString());
            check(SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"dash",args.toString())).code().equals("REJECTED")&&!e.actionUsed(),"wrong actor scope rejected");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void playerAndPreparedHuskIndependentDashRevocationAndDisconnect(TestContext ctx)throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);var who=CharacterService.principal(player);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);
            f.repo.invite(f.dm,f.campaign,2,player.getUuid(),true);CharacterService.select(player,f.campaign);
            UUID id=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
            player.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            var husk=EntityType.HUSK.create(ctx.getWorld());husk.setAiDisabled(true);husk.refreshPositionAndAngles(f.origin.getX()+4.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);ctx.getWorld().spawnEntity(husk);f.entities.add(husk);
            f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",husk.getUuid());var e=begin(f,new Dice(20,1),player,husk);
            dash(e,who);check(e.grid(who).remainingFeet()==60,"player speed 30");e.endTurn(who);dash(e,who);
            check(e.grid(who).remainingFeet()==40&&e.snapshot(who).get("dashFeet").getAsInt()==20,"same DM controls Husk with reviewed speed 20");
            e.endTurn(who);e.endTurn(who);f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),player.getUuid());
            rejects(()->dash(e,who),"ordinary former DM cannot Dash unauthorized NPC");check(!e.actionUsed()&&e.grid(f.dm).remainingFeet()==20,"revocation rejection preserves NPC budget");
            e.endTurn(f.dm);dash(e,who);check(e.grid(who).remainingFeet()==60,"ordinary owner may Dash own PC");
            e.endTurn(f.dm);e.endTurn(f.dm);ctx.getWorld().getServer().getPlayerManager().remove(player);
            rejects(()->dash(e,who),"disconnected active actor cannot Dash");check(!e.actionUsed(),"disconnection cannot spend action");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
}
