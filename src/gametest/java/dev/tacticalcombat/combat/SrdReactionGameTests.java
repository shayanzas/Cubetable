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

public final class SrdReactionGameTests implements FabricGameTest {
    static JsonObject prompt(SrdEncounter e,CharacterRepository.Principal p){return e.snapshot(p).getAsJsonObject("opportunity");}
    static UUID id(JsonObject p,String key){return UUID.fromString(p.get(key).getAsString());}
    static void decline(SrdEncounter e,CharacterRepository.Principal p){var q=prompt(e,p);e.reactionDecline(p,id(q,"id"),id(q,"reactor"),id(q,"target"),e.campaign(),e.revision());}
    static SrdEncounter.Intent preview(SrdEncounter e,CharacterRepository.Principal p){var q=prompt(e,p);var c=q.getAsJsonArray("choices").get(0).getAsJsonObject();return e.reactionPreview(p,id(q,"id"),id(q,"reactor"),id(q,"target"),e.campaign(),e.revision(),new SrdEncounter.ReactionChoice(SrdEquipment.Weapon.valueOf(c.get("weapon").getAsString()),SrdCharacter.Ability.valueOf(c.get("ability").getAsString()),c.get("twoHands").getAsBoolean()));}
    static SrdEncounter.Transaction confirm(SrdEncounter e,CharacterRepository.Principal p,SrdEncounter.Intent i){var q=prompt(e,p);return e.reactionConfirm(p,id(q,"id"),id(q,"reactor"),id(q,"target"),e.campaign(),e.revision(),i.id());}
    static String budget(SrdEncounter e,CharacterRepository.Principal p,UUID actor){for(var a:e.snapshot(p).getAsJsonArray("actors"))if(a.getAsJsonObject().get("id").getAsString().equals(actor.toString()))return a.getAsJsonObject().get("reaction").getAsString();throw new AssertionError();}
    static void move(Fixture f,SrdEncounter e,int x,int z){check(e.move(f.dm,UUID.randomUUID(),new SrdMovement.Cell(f.origin.getX()+x,f.origin.getZ()+z,f.origin.getY()+1)).accepted(),"route accepted");}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void declinePausesBeforeDepartureAndRevalidatesTerrain(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var e=begin(f,new Dice(20,1),a,b);var pos=a.getPos();move(f,e,5,2);e.tick();
            check(a.getPos().equals(pos)&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==30&&prompt(e,f.dm)!=null,"departure waits, no distance charged");
            rejects(()->e.endTurn(f.dm),"cannot bypass offered reaction through end turn");rejects(()->e.cancelMove(f.dm),"cannot cancel pending offered reaction");
            decline(e,f.dm);check(budget(e,f.dm,b.getUuid()).equals("AVAILABLE"),"decline preserves reaction");
            ctx.getWorld().setBlockState(f.origin.add(4,1,2),net.minecraft.block.Blocks.STONE.getDefaultState());e.tick();
            check(a.getPos().equals(pos)&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==30&&!e.snapshot(f.dm).get("moving").getAsBoolean(),"changed terrain prevents resumed departure without cost");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void hitCommitsOnceResumesAndRefreshesOnlyReactorsTurn(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var c=f.actor(9,6,true);var dice=new Dice(20,10,1,20);var e=begin(f,dice,a,b,c);move(f,e,5,2);e.tick();var q=prompt(e,f.dm);var i=preview(e,f.dm);var tx=confirm(e,f.dm,i);
            check(tx.state==SrdEncounter.TransactionState.COMMITTED&&tx.plan.actionCost()==0&&tx.plan.reactionCost()==1,"reaction cost recorded separately");check(!e.actionUsed()&&budget(e,f.dm,b.getUuid()).equals("SPENT"),"only reactor reaction spent");check(CharacterService.srdStats(a,f.campaign).stats().hp()==9&&dice.rolls==4,"HP and one server outcome committed");
            rejects(()->e.reactionConfirm(f.dm,id(q,"id"),b.getUuid(),a.getUuid(),e.campaign(),e.revision(),i.id()),"old trigger cannot execute again");
            e.tick();e.tick();check(e.grid(f.dm).remainingFeet()==20&&prompt(e,f.dm)==null,"route resumes and charges exactly two steps");
            var reload=new CharacterRepository(f.dir.resolve("state.json"));check(reload.srdSnapshot(a.getUuid(),f.campaign).stats().hp()==9&&reload.gameplayReceipt(i.id()).plan().reactionCost()==1,"restart preserves version 2 receipt and HP");
            move(f,e,3,2);e.tick();e.tick();move(f,e,4,2);e.tick();check(prompt(e,f.dm)==null&&e.grid(f.dm).remainingFeet()==5&&dice.rolls==4,"same spent reaction cannot attack a second departure");
            e.endTurn(f.dm);check(e.current()==b&&budget(e,f.dm,b.getUuid()).equals("AVAILABLE"),"reactor own next turn restores reaction");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missAndTimeoutResumeWithoutNormalActionCost(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var dice=new Dice(20,1,1);var e=begin(f,dice,a,b);move(f,e,4,2);e.tick();var tx=confirm(e,f.dm,preview(e,f.dm));check(!tx.roll.hit()&&!e.actionUsed()&&CharacterService.srdStats(a,f.campaign).stats().hp()==10,"miss spends reaction only");e.tick();check(e.grid(f.dm).remainingFeet()==25,"miss resumes departure");}
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var e=begin(f,new Dice(20,1),a,b);move(f,e,4,2);e.tick();for(int i=0;i<400;i++)e.tick();check(prompt(e,f.dm)==null&&e.grid(f.dm).remainingFeet()==25&&budget(e,f.dm,b.getUuid()).equals("AVAILABLE"),"server timeout declines and releases route without spending reaction");}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void zeroHpPreventsDepartureAndRetainsCommittedHealth(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);f.repo.commitSrdHealth(CharacterService.srdStats(a,f.campaign),new SrdHealth(1,10,0));var e=begin(f,new Dice(20,1,20),a,b);var pos=a.getPos();move(f,e,4,2);e.tick();confirm(e,f.dm,preview(e,f.dm));e.tick();check(e.state()==SrdEncounter.State.PAUSED&&a.getPos().equals(pos)&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==30,"zero HP never executes or charges departure");check(new CharacterRepository(f.dir.resolve("state.json")).srdSnapshot(a.getUuid(),f.campaign).stats().hp()==0,"zero HP durable");}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void storageFailureReservesReactionAndRetriesSameDice(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var dice=new Dice(20,1,20);var e=begin(f,dice,a,b);var pos=a.getPos();move(f,e,4,2);e.tick();var i=preview(e,f.dm);
            var fault=new java.util.concurrent.atomic.AtomicBoolean(true);f.field.set(null,SrdSaveFault.repository(f.dir.resolve("state.json"),fault::get));var tx=confirm(e,f.dm,i);
            check(tx.state==SrdEncounter.TransactionState.SAVE_FAILED&&e.state()==SrdEncounter.State.PAUSED&&budget(e,f.dm,b.getUuid()).equals("RESERVED"),"failed save retains explicit reaction reservation");check(CharacterService.srdStats(a,f.campaign).stats().hp()==10&&a.getPos().equals(pos)&&dice.rolls==3,"no phantom HP, movement or reroll");
            e.retry(f.dm);check(dice.rolls==3&&a.getPos().equals(pos),"failed retry retains dice and departure");fault.set(false);e.retry(f.dm);
            check(tx.state==SrdEncounter.TransactionState.COMMITTED&&budget(e,f.dm,b.getUuid()).equals("SPENT")&&!e.actionUsed()&&dice.rolls==3,"retry commits exact saved outcome, reaction only");e.tick();check(e.grid(f.dm).remainingFeet()==25,"safe retry resumes uncharged departure once");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void multipleReactorsFollowInitiativeAndDeclineIndependently(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,3,true);var b=f.actor(2,2,true);var c=f.actor(2,4,true);var e=begin(f,new Dice(20,5,15),a,b,c);move(f,e,4,3);var pos=a.getPos();e.tick();check(id(prompt(e,f.dm),"reactor").equals(c.getUuid()),"highest initiative eligible reactor first");decline(e,f.dm);e.tick();check(id(prompt(e,f.dm),"reactor").equals(b.getUuid())&&a.getPos().equals(pos),"next reactor before departure");decline(e,f.dm);e.tick();check(e.grid(f.dm).remainingFeet()==25&&budget(e,f.dm,b.getUuid()).equals("AVAILABLE")&&budget(e,f.dm,c.getUuid()).equals("AVAILABLE"),"all declines preserve independent budgets");}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void disengageProtectsSplitMovementAndExpires(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var e=begin(f,new Dice(20,1),a,b);long revision=e.revision();rejects(()->e.disengage(f.dm,e.campaign(),a.getUuid(),revision-1),"stale disengage rejected");check(!e.actionUsed(),"invalid action costs nothing");e.disengage(f.dm,e.campaign(),a.getUuid(),e.revision());rejects(()->e.disengage(f.dm,e.campaign(),a.getUuid(),e.revision()),"duplicate disengage rejected");move(f,e,4,2);e.tick();move(f,e,5,2);e.tick();check(e.grid(f.dm).remainingFeet()==20&&prompt(e,f.dm)==null&&e.actionUsed(),"protected split movement has ordinary costs");rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false),"Disengage spends normal attack action");e.endTurn(f.dm);check(!e.snapshot(f.dm).get("disengaged").getAsBoolean()&&!e.actionUsed(),"protection expires at turn end");}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void preparedHuskAndPlayerUseAuthenticatedOutOfTurnGateway(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){capability(player);var who=CharacterService.principal(player);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);f.repo.invite(f.dm,f.campaign,2,player.getUuid(),true);CharacterService.select(player,f.campaign);
            UUID cid=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,cid,1);f.repo.bind(f.dm,cid,2,player.getUuid(),player.getUuid());player.refreshPositionAndAngles(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5,0,0);
            var husk=EntityType.HUSK.create(ctx.getWorld());husk.setAiDisabled(true);husk.refreshPositionAndAngles(f.origin.getX()+3.5,f.origin.getY()+1,f.origin.getZ()+2.5,0,0);ctx.getWorld().spawnEntity(husk);f.entities.add(husk);f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",husk.getUuid());var dice=new Dice(1,20,20);var e=begin(f,dice,player,husk);check(e.current()==husk,"prepared Husk owns active turn");move(f,e,4,2);e.tick();var q=prompt(e,who);check(id(q,"reactor").equals(player.getUuid()),"out-of-turn player may react");
            var args=new JsonObject();args.addProperty("campaign",e.campaign().toString());args.add("actor",q.get("reactor"));args.add("target",q.get("target"));args.add("trigger",q.get("id"));args.addProperty("revision",e.revision());var choice=q.getAsJsonArray("choices").get(0).getAsJsonObject();choice.entrySet().forEach(v->args.add(v.getKey(),v.getValue()));
            var response=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"reaction_preview",args.toString()));check(response.code().equals("REACTION_PREVIEW"),"out-of-turn preview gateway: "+response.message());var preview=JsonParser.parseString(response.json()).getAsJsonObject().getAsJsonObject("reactionPreview");args.remove("weapon");args.remove("ability");args.remove("twoHands");args.addProperty("revision",e.revision());args.add("intent",preview.get("id"));var packet=new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"reaction_confirm",args.toString());
            var accepted=SrdNetworking.handle(player,packet);check(accepted.code().equals("RESULT")&&!e.actionUsed(),"authorized manager confirms out of turn without mover action");check(SrdNetworking.handle(player,packet).code().equals("REJECTED")&&dice.rolls==3,"duplicate cannot reroll");e.tick();check(e.grid(who).remainingFeet()==15,"Husk speed20 ledger resumes correctly");
            var receipt=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"confirm","{\"intent\":\""+preview.get("id").getAsString()+"\"}"));check(receipt.code().equals("RESULT")&&dice.rolls==3,"accepted receipt read returns fixed reaction without reroll");
            e.endTurn(who);e.endTurn(who);e.disengage(who,e.campaign(),husk.getUuid(),e.revision());check(e.actionUsed()&&e.grid(who).remainingFeet()==20,"manager Disengage on prepared Husk spends no movement");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void extendedReachEnteringWithinDiagonalAndElevation(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(4,2,true);var b=f.actor(2,2,false);var sheet=SrdAuthoring.template();SrdAuthoring.data(sheet).addProperty("reach_feet",10);SrdTargetingGameTests.bind(f,b,sheet);var e=begin(f,new Dice(20,1),a,b);
            move(f,e,3,2);e.tick();check(prompt(e,f.dm)==null,"movement inward within extended reach does not provoke");move(f,e,4,2);e.tick();check(prompt(e,f.dm)==null,"movement to extended boundary does not provoke");move(f,e,5,3);e.tick();check(prompt(e,f.dm)!=null&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==20,"diagonal departure at ten foot boundary before charge");decline(e,f.dm);e.tick();check(e.grid(f.dm).remainingFeet()==15,"diagonal departure charges ordinary five feet");
        }
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);ctx.getWorld().setBlockState(f.origin.add(4,1,2),net.minecraft.block.Blocks.STONE.getDefaultState());var e=begin(f,new Dice(20,1),a,b);var from=a.getPos();check(e.move(f.dm,UUID.randomUUID(),new SrdMovement.Cell(f.origin.getX()+4,f.origin.getZ()+2,f.origin.getY()+2)).accepted(),"step-up route accepted");e.tick();check(prompt(e,f.dm)!=null&&a.getPos().equals(from),"one-block elevated departure pauses before climbing step");decline(e,f.dm);e.tick();check(a.getY()==from.y+1&&e.grid(f.dm).remainingFeet()==25,"authorized elevation resumes at ordinary grid cost");}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sightAndRecordedRestrictionsDoNotInventReactions(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var e=begin(f,new Dice(20,1),a,b);a.setInvisible(true);move(f,e,4,2);e.tick();check(prompt(e,f.dm)==null&&e.grid(f.dm).remainingFeet()==25,"reactor cannot see invisible mover; camera visibility irrelevant");}
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,false);var sheet=SrdAuthoring.template();
            var feature=new dev.tacticalcombat.uiapi.SheetSections.Feature("test:incapacitated",dev.tacticalcombat.uiapi.SheetSections.FeatureKind.CONDITION,"Incapacitated","Recorded, not executed","DM",dev.tacticalcombat.uiapi.SheetSections.Automation.MANUAL,null,null,null,List.of());
            sheet=dev.tacticalcombat.uiapi.SheetSections.update(sheet,"features",dev.tacticalcombat.uiapi.SheetSections.JSON.toJsonTree(new dev.tacticalcombat.uiapi.SheetSections.Features(List.of(feature),List.of())).getAsJsonObject());SrdTargetingGameTests.bind(f,b,sheet);var e=begin(f,new Dice(20,1),a,b);move(f,e,4,2);e.tick();check(prompt(e,f.dm)==null&&e.grid(f.dm).remainingFeet()==25,"unresolved recorded condition conservatively blocks reaction");}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dashCrawlingDodgeAndProneUseExistingCostsAndRollRules(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var dice=new Dice(20,1,20);var e=begin(f,dice,a,b);e.posture(f.dm,e.campaign(),a.getUuid(),e.revision(),true);e.dodge(f.dm,e.campaign(),a.getUuid(),e.revision());move(f,e,4,2);e.tick();var i=preview(e,f.dm);check(i.geometry().advantage()&&i.geometry().disadvantage(),"near Prone advantage cancels visible Dodge disadvantage");var tx=confirm(e,f.dm,i);check(tx.roll.roll().faces().size()==1&&e.actionUsed(),"reaction normal roll preserves mover spent Dodge action");e.tick();check(e.grid(f.dm).remainingFeet()==20,"crawling departure costs ten feet after reaction");}
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var e=begin(f,new Dice(20,1),a,b);e.dash(f.dm,e.campaign(),a.getUuid(),e.revision());move(f,e,4,2);e.tick();check(prompt(e,f.dm)!=null&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==60,"Dash movement still provokes without premature step cost");decline(e,f.dm);e.tick();check(e.grid(f.dm).remainingFeet()==55&&e.actionUsed(),"Dash grant and normal action retained");}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void permissionRevocationAndStalePreviewCannotSpendReaction(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),true);CharacterService.select(player,f.campaign);var who=CharacterService.principal(player);var a=f.actor(3,2,true);var b=f.actor(2,2,true);var dice=new Dice(20,1);var e=begin(f,dice,a,b);move(f,e,4,2);e.tick();var q=prompt(e,who);var old=preview(e,who);var latest=preview(e,f.dm);
            rejects(()->e.reactionConfirm(who,id(q,"id"),b.getUuid(),a.getUuid(),e.campaign(),e.revision(),old.id()),"replaced preview cannot use a new current revision");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),player.getUuid());
            var args=new JsonObject();args.addProperty("campaign",e.campaign().toString());args.add("actor",q.get("reactor"));args.add("target",q.get("target"));args.add("trigger",q.get("id"));args.addProperty("revision",e.revision());args.addProperty("intent",latest.id().toString());
            var rejected=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"reaction_confirm",args.toString()));check(rejected.code().equals("REJECTED")&&rejected.json().equals("{}"),"revoked manager cannot execute or inspect private reaction");
            check(dice.rolls==2&&budget(e,f.dm,b.getUuid()).equals("AVAILABLE")&&CharacterService.srdStats(a,f.campaign).stats().hp()==10,"rejections leave dice resources and reaction intact");decline(e,f.dm);e.tick();check(e.grid(f.dm).remainingFeet()==25,"current authorized DM can resolve pending decision");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void durablePreparedReactionSurvivesFinalCommitFaultAndRestart(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(3,2,true);var b=f.actor(2,2,true);var dice=new Dice(20,1,20);var e=begin(f,dice,a,b);var pos=a.getPos();move(f,e,4,2);e.tick();var i=preview(e,f.dm);
            var fault=new java.util.concurrent.atomic.AtomicBoolean(true);f.field.set(null,SrdSaveFault.failFinalCommit(f.dir.resolve("state.json"),fault::get));var tx=confirm(e,f.dm,i);
            var recovered=new CharacterRepository(f.dir.resolve("state.json"));
            check(recovered.gameplayReceipt(i.id()).status()==GameplayReceipt.Status.PREPARED&&recovered.gameplayReceipt(i.id()).plan().reactionCost()==1,"durable preparation survives restart before final commit");
            check(recovered.srdSnapshot(a.getUuid(),f.campaign).stats().hp()==10&&a.getPos().equals(pos)&&budget(e,f.dm,b.getUuid()).equals("RESERVED"),"no partial damage or phantom movement/spent reaction");
            rejects(()->recovered.requireUnreserved(CharacterService.srdStats(a,f.campaign).character()),"restart cannot bypass unresolved reservation");
            e.retry(f.dm);check(dice.rolls==3&&e.state()==SrdEncounter.State.PAUSED,"failed final commit retry has no extra dice");fault.set(false);e.retry(f.dm);e.tick();
            check(tx.state==SrdEncounter.TransactionState.COMMITTED&&dice.rolls==3&&e.grid(f.dm).remainingFeet()==25&&CharacterService.srdStats(a,f.campaign).stats().hp()==9,"same fixed prepared operation commits once and safely resumes");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void managerReactsWithPreparedHuskButOrdinaryParticipantCannot(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){capability(player);var who=CharacterService.principal(player);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);f.repo.invite(f.dm,f.campaign,2,player.getUuid(),true);CharacterService.select(player,f.campaign);
            UUID cid=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,cid,1);f.repo.bind(f.dm,cid,2,player.getUuid(),player.getUuid());player.refreshPositionAndAngles(f.origin.getX()+3.5,f.origin.getY()+1,f.origin.getZ()+2.5,0,0);
            var husk=EntityType.HUSK.create(ctx.getWorld());husk.setAiDisabled(true);husk.refreshPositionAndAngles(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5,0,0);ctx.getWorld().spawnEntity(husk);f.entities.add(husk);f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",husk.getUuid());var dice=new Dice(20,1,15,3);var e=begin(f,dice,player,husk);move(f,e,4,2);e.tick();var q=prompt(e,who);
            check(id(q,"reactor").equals(husk.getUuid()),"prepared NPC is reacting while player moves");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),player.getUuid());
            var ordinary=e.snapshot(who);check(!ordinary.has("opportunity")&&!ordinary.getAsJsonArray("actors").get(1).getAsJsonObject().has("reaction"),"ordinary bound player cannot see NPC reaction or private prompt");
            rejects(()->e.reactionDecline(who,id(q,"id"),husk.getUuid(),player.getUuid(),e.campaign(),e.revision()),"ordinary participant cannot decline another actor's reaction");
            f.repo.invite(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),player.getUuid(),true);
            var i=preview(e,who);check(i.weapon()==SrdEquipment.Weapon.SLAM,"only verified Zombie Slam is offered, no fake player-class attack");var tx=confirm(e,who,i);check(tx.state==SrdEncounter.TransactionState.COMMITTED&&CharacterService.srdStats(player,f.campaign).stats().hp()==6&&budget(e,who,husk.getUuid()).equals("SPENT"),"manager-controlled NPC Slam commits reviewed damage and reaction");
            e.tick();check(!e.actionUsed()&&e.grid(who).remainingFeet()==25,"NPC reaction leaves moving player's normal action intact");
            e.endTurn(who);check(e.current()==husk&&!e.actionUsed()&&budget(e,who,husk.getUuid()).equals("AVAILABLE"),"NPC retains normal action and refreshes reaction only on own turn");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
}
