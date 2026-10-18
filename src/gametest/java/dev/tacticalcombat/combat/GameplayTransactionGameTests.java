package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.UiApiService;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

public class GameplayTransactionGameTests implements FabricGameTest {
    private static JsonObject args(String key,Object value){var out=new JsonObject();if(value instanceof Number n)out.addProperty(key,n);else out.addProperty(key,value.toString());return out;}
    private static ServerPlayerEntity manager(Fixture f)throws Exception{var p=f.ctx.createMockCreativeServerPlayerInWorld();capability(p);f.repo.invite(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid(),true);CharacterService.select(p,f.campaign);return p;}
    private static void cleanup(ServerPlayerEntity p){CharacterService.forget(p.getUuid());p.getServer().getPlayerManager().remove(p);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void committedLostReplyAndRestartReturnSamePrivateResult(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,15);var e=begin(f,dice,a,b);var who=CharacterService.principal(p);
            var intent=e.preview(who,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var packet=new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"confirm",args("intent",intent.id()).toString());var result=SrdNetworking.handle(p,packet);check(result.code().equals("RESULT"),"first confirmation committed");String fixed=data(result).getAsJsonObject("result").toString();var resource=CharacterService.srdStats(b,f.campaign);
            check(data(SrdNetworking.handle(p,packet)).getAsJsonObject("result").toString().equals(fixed)&&dice.rolls==3&&CharacterService.srdStats(b,f.campaign).equals(resource),"lost reply duplicate returns exact result without write/RNG");
            var forged=args("intent",intent.id());forged.addProperty("damage",999);check(SrdNetworking.handle(p,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"confirm",forged.toString())).code().equals("REJECTED"),"same operation with added authoritative fields rejected");
            e.stop(f.dm);var loaded=new CharacterRepository(f.dir.resolve("state.json"));f.field.set(null,loaded);var replay=SrdNetworking.handle(p,packet);check(replay.code().equals("RECEIPT")&&data(replay).getAsJsonObject("result").toString().equals(fixed),"closed/reloaded encounter returns receipt without resuming");check(loaded.srdSnapshot(b.getUuid(),f.campaign).equals(resource)&&!CombatManager.isInCombat(a),"restart/replay never restores world claims or resets HP");
            loaded.revokeDm(f.dm,f.campaign,loaded.campaign(f.campaign).revision(),p.getUuid());check(SrdNetworking.handle(p,packet).code().equals("REJECTED"),"revoked manager cannot obtain NPC receipt");
        }finally{if(p!=null)cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void durablePreparationSurvivesClosureAndOnlyDmCanCancel(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);var player=p;var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,15);var e=begin(f,dice,a,b);var who=CharacterService.principal(p);var armed=new java.util.concurrent.atomic.AtomicBoolean(true);
            f.field.set(null,SrdSaveFault.failFinalCommit(f.dir.resolve("state.json"),armed::get));var intent=e.preview(who,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var result=e.confirm(who,intent.id());var repo=CharacterService.repository();
            check(result.state==SrdEncounter.TransactionState.SAVE_FAILED&&repo.gameplayReceipt(intent.id()).status()==GameplayReceipt.Status.PREPARED&&repo.srdSnapshot(b.getUuid(),f.campaign).stats().hp()==10,"durable prep precedes failed final write with unchanged HP");
            rejects(()->e.dodge(who,e.campaign(),e.current().getUuid(),e.revision()),"Dodge cannot bypass failed prepared weapon reservation");
            rejects(()->e.dash(who,e.campaign(),e.current().getUuid(),e.revision()),"Dash cannot bypass failed prepared weapon reservation");
            var pendingPacket=new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"confirm",args("intent",intent.id()).toString());
            net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.invoker().onPlayDisconnect(p.networkHandler,p.getServer());CharacterService.select(p,f.campaign);
            var pendingReply=SrdNetworking.handle(p,pendingPacket);check(pendingReply.code().equals("SAVE_FAILED")&&!data(pendingReply).has("result")&&repo.reserved(result.plan.attacker().character())&&dice.rolls==3,"disconnect cleanup and replay preserve reservation without publishing a committed result");
            armed.set(false);repo.revokeDm(f.dm,f.campaign,repo.campaign(f.campaign).revision(),p.getUuid());rejects(()->e.retry(who),"revoked manager cannot recover live pending transaction");repo.invite(f.dm,f.campaign,repo.campaign(f.campaign).revision(),p.getUuid(),true);
            armed.set(false);rejects(()->{try{GameplayRecovery.handle(player,UiApiService.Operation.GAMEPLAY_CANCEL,argsCancel(intent.id()));}catch(java.io.IOException io){throw new RuntimeException(io);}},"cannot cancel while old encounter owns actors");
            e.stop(f.dm);var restart=new CharacterRepository(f.dir.resolve("state.json"));f.field.set(null,restart);rejects(()->f.start(a,b),"new encounter cannot bypass durable reservation after restart");check(restart.gameplayReceipt(intent.id()).plan().outcome().equals(result.roll)&&dice.rolls==3,"prepared fixed outcome preserved");
            var review=GameplayRecovery.handle(p,UiApiService.Operation.GAMEPLAY_RECOVERY,args("page",0));check(review.has("panelToken")&&review.getAsJsonArray("operations").size()==1,"DM review carries permission invalidation watch and fixed receipt");
            restart.revokeDm(f.dm,f.campaign,restart.campaign(f.campaign).revision(),p.getUuid());rejects(()->{try{GameplayRecovery.handle(player,UiApiService.Operation.GAMEPLAY_CANCEL,argsCancel(intent.id()));}catch(java.io.IOException io){throw new RuntimeException(io);}},"revoked DM cannot cancel");
            restart.invite(f.dm,f.campaign,restart.campaign(f.campaign).revision(),p.getUuid(),true);var cancelled=GameplayRecovery.handle(p,UiApiService.Operation.GAMEPLAY_CANCEL,argsCancel(intent.id()));check(cancelled.get("status").getAsString().equals("CANCELLED")&&restart.srdSnapshot(b.getUuid(),f.campaign).stats().hp()==10,"explicit DM cancellation no damage");check(restart.gameplayReceipt(intent.id()).resolvedBy().equals(p.getUuid()),"cancellation actor retained in durable audit");
            f.start(a,b);check(CombatManager.isInCombat(a),"only audited resolution releases admission reservation");
        }finally{if(p!=null)cleanup(p);}ctx.complete();
    }
    private static JsonObject argsCancel(UUID operation){var out=args("operation",operation);out.addProperty("reason","Reviewed after restart; cancelled for manual adjudication");return out;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void finalCommitRetryPreservesRangedRollAndSpentMovement(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(1,1,false);var sheet=SrdAuthoring.template();var d=SrdAuthoring.data(sheet);d.addProperty("arrows",1);d.add("weapons",JsonParser.parseString("[\"shortbow\"]"));UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,a.getUuid(),null);var b=f.actor(8,1,true);var dice=new Dice(20,1,20,1,1);var e=begin(f,dice,a,b);
            e.move(f.dm,UUID.randomUUID(),new SrdMovement.Cell(f.origin.getX()+2,f.origin.getZ()+1,f.origin.getY()+1));e.tick();var armed=new java.util.concurrent.atomic.AtomicBoolean(true);f.field.set(null,SrdSaveFault.failFinalCommit(f.dir.resolve("state.json"),armed::get));
            var intent=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true);var result=e.confirm(f.dm,intent.id());check(result.state==SrdEncounter.TransactionState.SAVE_FAILED&&CharacterService.srdStats(a,f.campaign).stats().arrows()==1&&CharacterService.srdStats(b,f.campaign).stats().hp()==10,"failed final commit leaves both quantities unchanged");
            e.retry(f.dm);check(dice.rolls==5,"failed repeat uses stored dice");armed.set(false);e.retry(f.dm);check(result.state==SrdEncounter.TransactionState.COMMITTED&&CharacterService.srdStats(a,f.campaign).stats().arrows()==0&&CharacterService.srdStats(b,f.campaign).stats().hp()==8,"fixed critical spends ammunition and HP atomically");check(e.grid(f.dm).remainingFeet()==25&&dice.rolls==5,"committed retry cannot reset spent movement or reroll");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void presentationFailureAfterCommitCannotRetrySpending(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,15);var e=begin(f,dice,a,b);e.afterDurableCommit=()->{throw new IllegalStateException("Injected after durable commit before encounter readback");};var intent=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var result=e.confirm(f.dm,intent.id());
            check(result.state==SrdEncounter.TransactionState.COMMITTED&&e.state()==SrdEncounter.State.PAUSED,"presentation failure does not mark durable action failed");var repo=CharacterService.repository();var before=repo.srdSnapshot(b.getUuid(),f.campaign);check(before.stats().hp()==9&&repo.gameplayReceipt(intent.id()).status()==GameplayReceipt.Status.COMMITTED,"resources and receipt committed before presentation fault");rejects(()->e.retry(f.dm),"cannot re-spend committed action after presentation failure");check(dice.rolls==3&&repo.srdSnapshot(b.getUuid(),f.campaign).equals(before),"presentation retry rejection preserves fixed result");
        }ctx.complete();
    }
}
