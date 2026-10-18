package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

public class SrdProneGameTests implements FabricGameTest {
    static void posture(SrdEncounter e,CharacterRepository.Principal p,boolean prone){e.posture(p,e.campaign(),e.current().getUuid(),e.revision(),prone);}
    static SrdMovement.Cell cell(Fixture f,int x,int z){return new SrdMovement.Cell(f.origin.getX()+x,f.origin.getZ()+z,f.origin.getY()+1);}
    static void bind(Fixture f,net.minecraft.entity.LivingEntity actor,int speed)throws Exception{
        var sheet=SrdAuthoring.template();SrdAuthoring.data(sheet).addProperty("speed_feet",speed);
        UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,actor.getUuid(),null);
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void posturePersistsAcrossTurnsAndStandingPreservesActionAndDodge(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(4,1,true);var e=begin(f,new Dice(20,1),a,b);var pos=a.getPos();var stats=CharacterService.srdStats(a,f.campaign);
            posture(e,f.dm,true);check(e.prone(a)&&!e.actionUsed()&&e.grid(f.dm).remainingFeet()==30&&a.getPos().equals(pos),"Drop Prone costs no action, feet or displacement");
            rejects(()->posture(e,f.dm,true),"duplicate drop forbidden");SrdDodgeGameTests.dodge(e,f.dm);check(e.dodging(a),"executable Prone does not disable Dodge");
            check(e.move(f.dm,UUID.randomUUID(),cell(f,2,1)).accepted(),"crawl allowed after action spent");e.tick();check(e.grid(f.dm).remainingFeet()==20,"ordinary crawling costs ten feet");
            e.endTurn(f.dm);posture(e,f.dm,true);e.endTurn(f.dm);check(e.prone(a)&&e.prone(b)&&!e.dodging(a),"both postures persist, Dodge alone expires");
            pos=a.getPos();posture(e,f.dm,false);check(!e.prone(a)&&e.prone(b)&&e.grid(f.dm).remainingFeet()==15&&!e.actionUsed()&&a.getPos().equals(pos),"stand charges half speed only, independently");
            rejects(()->posture(e,f.dm,false),"duplicate stand forbidden");check(stats.equals(CharacterService.srdStats(a,f.campaign)),"posture has no persistent record mutation");e.end();check(!e.prone(b),"end clears encounter posture");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void staleStandingRouteDashCrawlAndInsufficientStand(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(9,6,true);var e=begin(f,new Dice(20,1),a,b);var destination=cell(f,6,1);
            check(e.grid(f.dm).reachable().get(destination).costFeet()==25,"old standing route costs 25");posture(e,f.dm,true);
            check(!e.move(f.dm,UUID.randomUUID(),destination).accepted()&&e.grid(f.dm).remainingFeet()==30,"old route cannot bypass fifty-foot crawling cost");SrdDashGameTests.dash(e,f.dm);
            check(e.grid(f.dm).reachable().get(destination).costFeet()==50&&e.move(f.dm,UUID.randomUUID(),destination).accepted(),"Dash gives sufficient crawl budget");
            rejects(()->posture(e,f.dm,false),"cannot stand during an executing route");for(int i=0;i<5;i++)e.tick();
            check(e.grid(f.dm).remainingFeet()==10,"five crawled squares cost fifty, with no refund");rejects(()->posture(e,f.dm,false),"ten feet insufficient for fifteen-foot stand");
            check(e.prone(a)&&e.grid(f.dm).remainingFeet()==10&&e.actionUsed(),"failed stand preserves posture, action and ledger");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void crawlingDifficultTerrainAndEachStepRevalidate(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(8,6,true);var e=begin(f,new Dice(20,1),a,b);posture(e,f.dm,true);
            ctx.getWorld().setBlockState(f.origin.add(2,0,1),Blocks.SOUL_SAND.getDefaultState());
            var terrain=new SrdTerrain(a,CharacterService.srdStats(a,f.campaign).stats(),true);var step=terrain.step(terrain.currentCell(),1,0);
            check(step.reason()==SrdMovement.Reason.OK&&step.feet()==15,"crawling and difficulty add to fifteen");
            check(e.grid(f.dm).reachable().get(step.to()).costFeet()==15,"server preview agrees with terrain charge");check(e.move(f.dm,UUID.randomUUID(),step.to()).accepted(),"difficult crawl accepted");e.tick();
            check(e.grid(f.dm).remainingFeet()==15,"executed difficult crawl charges fifteen");
            check(e.move(f.dm,UUID.randomUUID(),cell(f,3,1)).accepted(),"next normal step previewed at ten");
            ctx.getWorld().setBlockState(f.origin.add(3,0,1),Blocks.SOUL_SAND.getDefaultState());e.tick();
            check(e.grid(f.dm).remainingFeet()==15&&!e.snapshot(f.dm).get("moving").getAsBoolean(),"changed floor height invalidates accepted route without charging unexecuted step");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oddSpeedAndZeroSpeedStanding(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,false);var b=f.actor(8,6,false);bind(f,a,25);bind(f,b,0);var e=begin(f,new Dice(20,1),a,b);
            posture(e,f.dm,true);posture(e,f.dm,false);check(e.grid(f.dm).remainingFeet()==13,"half twenty-five rounds down to twelve feet, not grid multiples");
            check(e.move(f.dm,UUID.randomUUID(),cell(f,3,1)).accepted(),"ten feet can be spent from thirteen");e.tick();e.tick();check(e.grid(f.dm).remainingFeet()==3,"fractional-grid remainder retained");
            e.endTurn(f.dm);posture(e,f.dm,true);rejects(()->posture(e,f.dm,false),"zero speed cannot stand even though rounded cost is zero");check(e.prone(b)&&!e.actionUsed(),"zero speed may voluntarily drop without spending action");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void attackProneDistanceDodgeAndConfirmationRevalidation(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,15);var e=begin(f,dice,a,b);posture(e,f.dm,true);SrdDodgeGameTests.dodge(e,f.dm);e.endTurn(f.dm);
            var preview=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            check(preview.geometry().advantage()&&preview.geometry().disadvantage(),"near Prone advantage and visible Dodge disadvantage coexist");
            posture(e,f.dm,true);rejects(()->e.confirm(f.dm,preview.id()),"posture mutation invalidates old attack intent before RNG");check(dice.rolls==2&&!e.actionUsed(),"stale confirmation spends nothing");
            posture(e,f.dm,false);var freshPreview=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var result=e.confirm(f.dm,freshPreview.id());
            check(result.roll.roll().faces().size()==1&&dice.rolls==3&&result.state==SrdEncounter.TransactionState.COMMITTED,"cancellation rolls one die and uses J2 commit");
            posture(e,f.dm,true);check(e.prone(b)&&e.actionUsed(),"drop permitted after committed weapon action");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void proneAttackerAndFarTargetDisadvantage(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,false);var b=f.actor(4,1,true);var sheet=SrdAuthoring.template();SrdAuthoring.data(sheet).add("weapons",JsonParser.parseString("[\"shortbow\"]"));SrdAuthoring.data(sheet).addProperty("arrows",2);
            UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,a.getUuid(),null);
            var dice=new Dice(20,1,18,2);var e=begin(f,dice,a,b);posture(e,f.dm,true);
            var own=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true);check(own.geometry().disadvantage(),"Prone attacker disadvantage");e.endTurn(f.dm);posture(e,f.dm,true);e.endTurn(f.dm);
            var far=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true);check(!far.geometry().advantage()&&far.geometry().disadvantage()&&far.geometry().feet()==15,"far Prone defender adds disadvantage");
            var result=e.confirm(f.dm,far.id());check(result.roll.roll().faces().size()==2&&result.roll.roll().chosen()==2&&dice.rolls==4,"multiple disadvantages never stack more dice");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void incrementalGridDropsOldStandingCosts(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,false);var b=f.actor(8,6,true);bind(f,a,120);var e=begin(f,new Dice(20,1),a,b);
            var old=e.pendingGrid(f.dm);while(!old.advance(Long.MAX_VALUE)){}e.completedGrid(f.dm,old.result());old.close();posture(e,f.dm,true);
            check(!e.move(f.dm,UUID.randomUUID(),cell(f,2,1)).accepted(),"old cached standing search cannot authorize high-speed crawl");
            var next=e.pendingGrid(f.dm);while(!next.advance(Long.MAX_VALUE)){}var grid=next.result();next.close();e.completedGrid(f.dm,grid);
            check(grid.reachable().get(cell(f,2,1)).costFeet()==10,"incremental search uses crawl costs");check(e.move(f.dm,UUID.randomUUID(),cell(f,2,1)).accepted(),"new grid authorizes crawl");e.tick();check(e.snapshot(f.dm).get("remainingFeet").getAsInt()==110,"high-speed execution charges same crawl cost");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void participantPosturePrivacy(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);CharacterService.select(player,f.campaign);var who=CharacterService.principal(player);
            UUID id=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
            player.refreshPositionAndAngles(f.origin.getX()+4.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            var a=f.actor(1,1,true);var e=begin(f,new Dice(20,1),a,player);posture(e,f.dm,true);
            var view=e.snapshot(who);check(!view.has("prone")&&!view.has("standCost")&&!view.has("canPosture"),"noncontroller has no private posture controls");
            check(!view.getAsJsonArray("actors").get(0).getAsJsonObject().has("prone"),"uncontrolled NPC posture not disclosed");
            check(!view.get("reason").getAsString().contains("Prone"),"shared status does not disclose private posture");
            check(e.snapshot(f.dm).getAsJsonArray("actors").get(0).getAsJsonObject().get("prone").getAsBoolean(),"manager sees authorized NPC posture");
            rejects(()->posture(e,who,false),"ordinary participant cannot stand NPC");e.endTurn(f.dm);posture(e,who,true);
            check(e.snapshot(who).get("prone").getAsBoolean(),"ordinary participant controls and sees own posture");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void gatewayIdentityPreparedHuskAndRevocation(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);f.repo.invite(f.dm,f.campaign,2,player.getUuid(),true);var who=CharacterService.principal(player);
            var husk=EntityType.HUSK.create(ctx.getWorld());husk.setAiDisabled(true);husk.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);ctx.getWorld().spawnEntity(husk);f.entities.add(husk);f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",husk.getUuid());var b=f.actor(6,1,true);var e=begin(f,new Dice(20,1),husk,b);
            var envelope=SrdDashGameTests.request(e);var drop=new SrdRequestPayload(e.id(),e.turn(),envelope.request(),"drop_prone",envelope.json());check(SrdNetworking.handle(player,drop).code().equals("POSTURE"),"DM can drop prepared Husk");
            check(SrdNetworking.handle(player,drop).code().equals("REJECTED"),"duplicate identity cannot toggle posture");var stale=SrdDashGameTests.request(e);e.cancelMove(who);
            check(SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),stale.request(),"stand",stale.json())).code().equals("REJECTED"),"stale revision cannot stand");
            var forged=JsonParser.parseString(SrdDashGameTests.request(e).json()).getAsJsonObject();forged.addProperty("cost",0);check(SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"stand",forged.toString())).code().equals("REJECTED"),"client cannot supply standing costs");
            posture(e,who,false);check(e.grid(who).remainingFeet()==10&&!e.actionUsed(),"Husk half-speed charge is ten");posture(e,who,true);f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),player.getUuid());rejects(()->posture(e,who,false),"revoked DM cannot control NPC posture");
            rejects(()->e.snapshot(who),"revoked nonparticipant receives no encounter snapshot or private posture controls");
            check(e.snapshot(f.dm).get("prone").getAsBoolean(),"remaining manager retains authorized posture indicator");
            e.endTurn(f.dm);check(SrdNetworking.handle(player,drop).code().equals("REJECTED"),"old request/authority cannot change another turn");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
}
