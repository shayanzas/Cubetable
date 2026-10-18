package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.checks.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.effect.*;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public class SrdDodgeGameTests implements FabricGameTest {
    static void dodge(SrdEncounter e,CharacterRepository.Principal p){e.dodge(p,e.campaign(),e.current().getUuid(),e.revision());}
    static JsonObject args(Object... pairs){var j=new JsonObject();for(int i=0;i<pairs.length;i+=2){var k=pairs[i].toString();var v=pairs[i+1];if(v==null)j.add(k,JsonNull.INSTANCE);else if(v instanceof JsonElement el)j.add(k,el);else if(v instanceof Number n)j.addProperty(k,n);else if(v instanceof Boolean b)j.addProperty(k,b);else j.addProperty(k,v.toString());}return j;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void independentDodgeLifetimeMovementAndActionExclusion(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(4,1,true);var c=f.actor(8,1,true);var e=begin(f,new Dice(20,10,1),a,b,c);
            var pos=a.getPos();dodge(e,f.dm);check(e.actionUsed()&&e.dodging(a)&&a.getPos().equals(pos)&&e.grid(f.dm).remainingFeet()==30,"Dodge spends one action without movement grant");
            rejects(()->dodge(e,f.dm),"Dodge twice rejected");rejects(()->SrdDashGameTests.dash(e,f.dm),"Dash after Dodge rejected");
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false),"attack after Dodge rejected");
            check(e.move(f.dm,UUID.randomUUID(),new SrdMovement.Cell(f.origin.getX()+2,f.origin.getZ()+1,f.origin.getY()+1)).accepted(),"ordinary movement remains");e.tick();check(e.grid(f.dm).remainingFeet()==25&&e.dodging(a),"movement costs retained while Dodging");
            e.endTurn(f.dm);check(e.dodging(a),"End Turn does not expire own Dodge");dodge(e,f.dm);e.endTurn(f.dm);
            check(e.dodging(a)&&e.dodging(b),"multiple creatures independently Dodging");e.endTurn(f.dm);
            check(!e.dodging(a)&&e.dodging(b)&&!e.actionUsed(),"only actor beginning its turn expires");e.endTurn(f.dm);check(!e.dodging(b),"second actor expires at own next turn");
            dodge(e,f.dm);e.end();check(!e.dodging(b),"end clears defensive state");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void incomingAttackRollUsesDisadvantageAndCommittedFaces(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,18,2);var e=begin(f,dice,a,b);dodge(e,f.dm);e.endTurn(f.dm);
            var intent=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            check(intent.geometry().disadvantage(),"Dodge contributes server preview disadvantage");var result=e.confirm(f.dm,intent.id());
            check(result.roll.roll().chosen()==2,"authoritative disadvantage keeps lower face");
            check(result.plan.attack().disadvantage()&&result.state==SrdEncounter.TransactionState.COMMITTED&&dice.rolls==4,"Dodge flags and exact outcome enter existing J2 transaction");
            rejects(()->dodge(e,f.dm),"Dodge after attack rejected");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void recoverableWeaponSaveFailureDoesNotExpireDefenderDodge(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,18,2);var e=begin(f,dice,a,b);dodge(e,f.dm);e.endTurn(f.dm);
            var armed=new java.util.concurrent.atomic.AtomicBoolean(true);f.field.set(null,SrdSaveFault.failFinalCommit(f.dir.resolve("state.json"),armed::get));
            var intent=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var tx=e.confirm(f.dm,intent.id());
            check(tx.state==SrdEncounter.TransactionState.SAVE_FAILED&&e.dodging(a),"temporary storage pause preserves defender Dodge");
            rejects(()->dodge(e,f.dm),"pending resource operation cannot take Dodge");armed.set(false);e.retry(f.dm);
            check(tx.state==SrdEncounter.TransactionState.COMMITTED&&e.dodging(a)&&dice.rolls==4,"retry retains exact disadvantaged dice and defender lifetime");
            e.endTurn(f.dm);check(!e.dodging(a),"effect expires only when defender next turn begins");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sightRevalidatedAtConfirmationWithoutSpendingOrRerolling(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1);var e=begin(f,dice,a,b);dodge(e,f.dm);e.endTurn(f.dm);
            b.setInvisible(true);var unseen=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            check(!unseen.geometry().disadvantage()&&unseen.geometry().advantage(),"unseen attacker receives no Dodge penalty");b.setInvisible(false);
            a.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS,200));var blind=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            check(!blind.geometry().disadvantage()&&e.dodging(a),"blind defender cannot see attacker but retains Dodge for saves");a.removeStatusEffect(StatusEffects.BLINDNESS);
            rejects(()->e.confirm(f.dm,blind.id()),"newly applicable Dodge invalidates old preview");check(dice.rolls==2&&!e.actionUsed(),"rejected stale preview never rolls or spends");
            var seen=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);check(seen.geometry().disadvantage(),"fresh preview reflects sight");
            ctx.getWorld().setBlockState(f.origin.add(2,1,1),net.minecraft.block.Blocks.STONE.getDefaultState());ctx.getWorld().setBlockState(f.origin.add(2,2,1),net.minecraft.block.Blocks.STONE.getDefaultState());
            check(!SrdTargeting.canSeeAttacker(a,CharacterService.srdStats(a,f.campaign).stats(),b,CharacterService.srdStats(b,f.campaign).stats()),"solid obstruction prevents sight");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dashRouteZeroSpeedAndUnavailableActorRejectDodge(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(8,6,false);var sheet=SrdAuthoring.template();SrdAuthoring.data(sheet).addProperty("speed_feet",0);UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,b.getUuid(),null);
            var e=begin(f,new Dice(20,1),a,b);e.move(f.dm,UUID.randomUUID(),new SrdMovement.Cell(f.origin.getX()+2,f.origin.getZ()+1,f.origin.getY()+1));
            rejects(()->dodge(e,f.dm),"in-flight route blocks Dodge");check(!e.actionUsed(),"no rejected action cost");e.tick();SrdDashGameTests.dash(e,f.dm);rejects(()->dodge(e,f.dm),"Dodge after Dash rejected");e.endTurn(f.dm);
            rejects(()->dodge(e,f.dm),"zero speed cannot Dodge");check(!e.actionUsed(),"zero speed rejection no cost");e.endTurn(f.dm);dodge(e,f.dm);
            a.discard();check(!e.dodging(a),"unavailable/incapable actor loses benefit permanently");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void recordedIncapacitationAndSleepFailClosed(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(4,1,false);var sheet=SrdAuthoring.template();
            var feature=new SheetSections.Feature("test:incapacitated",SheetSections.FeatureKind.CONDITION,"Incapacitated","DM recorded; not an executable condition system","DM",SheetSections.Automation.MANUAL,null,null,null,List.of());
            sheet=SheetSections.update(sheet,"features",SheetSections.JSON.toJsonTree(new SheetSections.Features(List.of(feature),List.of())).getAsJsonObject());
            UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,b.getUuid(),null);
            var e=begin(f,new Dice(20,1),a,b);dodge(e,f.dm);a.sleep(a.getBlockPos());check(!e.dodging(a),"sleep immediately ends benefit");a.wakeUp();check(!e.dodging(a),"waking does not restore spent defensive effect");e.tick();e.endTurn(f.dm);
            rejects(()->dodge(e,f.dm),"recorded condition requires adjudication, never silently ignored or executed");check(!e.actionUsed(),"uncertain eligibility spends nothing");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void defensiveIndicatorAndEligibilityStayPermissionFiltered(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);CharacterService.select(player,f.campaign);var who=CharacterService.principal(player);
            UUID id=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
            player.refreshPositionAndAngles(f.origin.getX()+4.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            var a=f.actor(1,1,true);var e=begin(f,new Dice(20,1),a,player);dodge(e,f.dm);
            var view=e.snapshot(who);check(!view.getAsJsonArray("actors").get(0).getAsJsonObject().has("dodging")&&!view.get("canDodge").getAsBoolean(),"uncontrolled NPC defensive state not disclosed");
            check(!view.get("reason").getAsString().contains("Dodge"),"shared status does not leak private defensive state");
            check(e.snapshot(f.dm).getAsJsonArray("actors").get(0).getAsJsonObject().get("dodging").getAsBoolean(),"DM sees authorized Dodge indicator");
            rejects(()->dodge(e,who),"ordinary participant cannot control NPC");e.endTurn(f.dm);dodge(e,who);
            check(e.snapshot(who).getAsJsonArray("actors").get(1).getAsJsonObject().get("dodging").getAsBoolean(),"ordinary player sees and controls own Dodge");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gatewayRejectsReplayStaleIdentityAndRevokedController(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),true);var who=CharacterService.principal(player);var a=f.actor(1,1,true);var b=f.actor(4,1,true);var e=begin(f,new Dice(20,1),a,b);
            var dash=SrdDashGameTests.request(e);var packet=new SrdRequestPayload(e.id(),e.turn(),dash.request(),"dodge",dash.json());
            check(SrdNetworking.handle(player,packet).code().equals("DODGE"),"gateway accepts Dodge");check(SrdNetworking.handle(player,packet).code().equals("REJECTED"),"same ID cannot execute twice");
            e.endTurn(who);check(SrdNetworking.handle(player,packet).code().equals("REJECTED")&&!e.actionUsed(),"old turn cannot affect next actor");
            var stale=SrdDashGameTests.request(e);e.cancelMove(who);check(SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),stale.request(),"dodge",stale.json())).code().equals("REJECTED"),"stale revision rejected");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),player.getUuid());rejects(()->dodge(e,who),"revocation blocks NPC control");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void boundNpcDexteritySaveUsesDodgeAndPreservesPrivateAcceptedResult(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();var outsider=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);f.repo.invite(f.dm,f.campaign,2,player.getUuid(),true);f.repo.invite(f.dm,f.campaign,3,outsider.getUuid(),false);CharacterService.select(player,f.campaign);CharacterService.select(outsider,f.campaign);
            var who=CharacterService.principal(player);var a=f.actor(1,1,false);var b=f.actor(2,1,true);UUID id=f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",a.getUuid());
            var e=begin(f,new Dice(20,1),a,b); // Zombie DEX -2 still wins this initiative.
            var spec=args("category","SAVE","key","dex","mode","NORMAL","purpose","Dodge save","visibility","DM_ONLY","recipients",new JsonArray(),"dc",12);
            var prepared=CheckGateway.handle(player,CHECK_PREPARE,args("character",id,"revision",f.repo.character(id).revision(),"spec",spec,"adjudicated",true));
            dodge(e,who);e.endTurn(who);var execute=args("operation",prepared.get("operation"));var accepted=CheckGateway.handle(player,CHECK_EXECUTE,execute);var roll=accepted.getAsJsonObject("result");
            check(roll.get("mode").getAsString().equals("ADVANTAGE")&&roll.getAsJsonArray("faces").size()==2&&roll.get("encounter").getAsString().equals(e.id().toString()),"NPC target binding supplies encounter and execution-time Dodge advantage");
            check(CheckGateway.handle(outsider,CHECK_INBOX,args("page",0)).getAsJsonArray("rolls").isEmpty(),"private NPC save not disclosed to other member");
            spec.addProperty("purpose","Save after expiry");var pending=CheckGateway.handle(player,CHECK_PREPARE,args("character",id,"revision",f.repo.character(id).revision(),"spec",spec,"adjudicated",true));e.endTurn(who);
            check(CheckGateway.handle(player,CHECK_EXECUTE,args("operation",pending.get("operation"))).getAsJsonObject("result").get("mode").getAsString().equals("NORMAL"),"execution rechecks expired Dodge");
            check(accepted.equals(CheckGateway.handle(player,CHECK_EXECUTE,execute)),"accepted result remains identical after expiry");
            dodge(e,who);spec.addProperty("purpose","Cancellation");spec.addProperty("mode","DISADVANTAGE");var cancel=CheckGateway.handle(player,CHECK_PREPARE,args("character",id,"revision",f.repo.character(id).revision(),"spec",spec,"adjudicated",true));
            check(CheckGateway.handle(player,CHECK_EXECUTE,args("operation",cancel.get("operation"))).getAsJsonObject("result").get("mode").getAsString().equals("NORMAL"),"server combines chosen disadvantage with Dodge advantage");
            spec.addProperty("purpose","Ability not save");spec.addProperty("category","ABILITY");spec.addProperty("mode","NORMAL");var ability=CheckGateway.handle(player,CHECK_PREPARE,args("character",id,"revision",f.repo.character(id).revision(),"spec",spec,"adjudicated",true));
            check(CheckGateway.handle(player,CHECK_EXECUTE,args("operation",ability.get("operation"))).getAsJsonObject("result").get("mode").getAsString().equals("NORMAL"),"ordinary Dexterity check unaffected");
            for(var key:List.of(new String[]{"SAVE","wis"},new String[]{"SKILL","Acrobatics"})){
                spec.addProperty("purpose","Unaffected "+key[0]);spec.addProperty("category",key[0]);spec.addProperty("key",key[1]);
                var unaffected=CheckGateway.handle(player,CHECK_PREPARE,args("character",id,"revision",f.repo.character(id).revision(),"spec",spec,"adjudicated",true));
                check(CheckGateway.handle(player,CHECK_EXECUTE,args("operation",unaffected.get("operation"))).getAsJsonObject("result").get("mode").getAsString().equals("NORMAL"),"Dodge affects only Dexterity saves");
            }
            spec.addProperty("purpose","Encounter closed");var closed=CheckGateway.handle(player,CHECK_PREPARE,args("character",id,"revision",f.repo.character(id).revision(),"spec",spec,"adjudicated",true));e.stop(who);
            rejects(()->CheckGateway.handle(player,CHECK_EXECUTE,args("operation",closed.get("operation"))),"pending check cannot silently change encounter association");
            check(accepted.equals(CheckGateway.handle(player,CHECK_EXECUTE,execute)),"accepted retrieval after closure never rerolls");
        }finally{CheckGateway.clear();for(var p:List.of(player,outsider)){CharacterService.forget(p.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(p);}}ctx.complete();
    }
}
