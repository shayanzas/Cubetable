package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;
import static dev.tacticalcombat.client.ui.ManagementState.args;

public final class SrdRecoveryGameTests implements FabricGameTest {
    private static ServerPlayerEntity viewer(Fixture f,boolean manager)throws Exception{var p=f.ctx.createMockCreativeServerPlayerInWorld();capability(p);f.repo.invite(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid(),manager);CharacterService.select(p,f.campaign);return p;}
    private static SrdStatePayload call(ServerPlayerEntity p,SrdEncounter e,UUID request,String op,JsonObject args){var w=p.getServer().getOverworld();((net.minecraft.world.level.ServerWorldProperties)w.getLevelProperties()).setTime(w.getTime()+21);return SrdNetworking.handle(p,new SrdRequestPayload(e.id(),e.turn(),request,op,args.toString()));}
    private static void cleanup(ServerPlayerEntity... players){for(var p:players)if(p!=null){CharacterService.forget(p.getUuid());p.getServer().getPlayerManager().remove(p);}}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pausedHealPacketPersistsOnceAndNeverResumes(TestContext ctx)throws Exception{
        ServerPlayerEntity dm=null,ordinary=null;
        try(var f=new Fixture(ctx)){
            dm=viewer(f,true);ordinary=viewer(f,false);var a=f.actor(1,1,true);var b=f.actor(2,1,true);
            f.repo.commitSrdHealth(CharacterService.srdStats(b,f.campaign),new SrdHealth(1,10,0));var dice=new Dice(20,1,20);var e=begin(f,dice,a,b);
            check(!e.snapshot(CharacterService.principal(dm)).getAsJsonObject("recovery").get("canHeal").getAsBoolean(),"active encounter offers no healing");
            check(call(dm,e,UUID.randomUUID(),"heal",args("target",b.getUuid(),"amount",4,"revision",e.revision())).code().equals("REJECTED"),"active heal rejected");
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var committed=e.confirm(f.dm,preview.id());var receipt=f.repo.gameplayReceipt(committed.plan.operation());
            var recovery=e.snapshot(CharacterService.principal(dm)).getAsJsonObject("recovery");check(recovery.get("canHeal").getAsBoolean()&&!recovery.get("canRetry").getAsBoolean(),"zero HP committed pause offers heal not retry");
            long revision=e.revision();var heal=args("target",b.getUuid(),"amount",4,"revision",revision);
            var denied=call(ordinary,e,UUID.randomUUID(),"heal",heal);check(denied.code().equals("REJECTED")&&denied.json().equals("{}"),"ordinary member forged healing has no encounter/private data");
            for(int invalid:new int[]{-1,0,1025})check(call(dm,e,UUID.randomUUID(),"heal",args("target",b.getUuid(),"amount",invalid,"revision",revision)).code().equals("REJECTED"),"amount outside positive supported bounds rejected");
            check(call(dm,e,UUID.randomUUID(),"heal",args("target",UUID.randomUUID(),"amount",4,"revision",revision)).code().equals("REJECTED"),"nonparticipant rejected");
            check(call(dm,e,UUID.randomUUID(),"heal",args("target",b.getUuid(),"amount",4,"revision",revision-1)).code().equals("REJECTED"),"stale reviewed revision rejected");
            UUID request=UUID.randomUUID();var accepted=call(dm,e,request,"heal",heal);check(accepted.code().equals("OK")&&accepted.message().contains("end this controlled encounter"),"healing response explains no auto resume");
            check(CharacterService.srdStats(b,f.campaign).stats().hp()==4&&e.state()==SrdEncounter.State.PAUSED,"only server applies HP and remains paused");
            check(call(dm,e,request,"heal",heal).code().equals("REJECTED"),"same mutation identity cannot heal again");check(call(dm,e,UUID.randomUUID(),"heal",heal).code().equals("REJECTED"),"new identity cannot apply stale reviewed amount");
            check(f.repo.gameplayReceipt(committed.plan.operation()).equals(receipt)&&dice.rolls==3,"healing/retry cannot alter receipt or fixed attack RNG");
            check(new CharacterRepository(f.dir.resolve("state.json")).srdSnapshot(b.getUuid(),f.campaign).stats().hp()==4,"real campaign reload retains healed health");
            check(call(dm,e,UUID.randomUUID(),"end",args()).code().equals("ENDED")&&!CombatManager.isInCombat(b),"explicit end releases world claims");
            check(CharacterService.srdStats(b,f.campaign).stats().hp()==4&&call(dm,e,UUID.randomUUID(),"heal",args("target",b.getUuid(),"amount",4,"revision",e.revision())).code().equals("REJECTED"),"end retains HP and rejects healing after closure");
        }finally{cleanup(dm,ordinary);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void failedSaveRecoveryRejectsHealingAndRetriesFixedDice(TestContext ctx)throws Exception{
        ServerPlayerEntity dm=null;
        try(var f=new Fixture(ctx)){
            dm=viewer(f,true);var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,20);var e=begin(f,dice,a,b);
            var fault=new java.util.concurrent.atomic.AtomicBoolean(true);f.field.set(null,SrdSaveFault.repository(f.dir.resolve("state.json"),fault::get));
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var tx=e.confirm(f.dm,preview.id());
            var recovery=e.snapshot(CharacterService.principal(dm)).getAsJsonObject("recovery");check(recovery.get("kind").getAsString().equals("SAVE_FAILED")&&!recovery.get("canHeal").getAsBoolean()&&recovery.get("canRetry").getAsBoolean(),"save failure offers saved-roll review not healing");
            check(call(dm,e,UUID.randomUUID(),"heal",args("target",b.getUuid(),"amount",4,"revision",e.revision())).code().equals("REJECTED"),"heal cannot bypass failed transaction");
            call(dm,e,UUID.randomUUID(),"retry",args());check(e.state()==SrdEncounter.State.PAUSED&&CharacterService.srdStats(b,f.campaign).stats().hp()==10&&dice.rolls==3,"real failed persistence retry keeps resources and dice");
            fault.set(false);call(dm,e,UUID.randomUUID(),"retry",args());check(tx.state==SrdEncounter.TransactionState.COMMITTED&&CharacterService.srdStats(b,f.campaign).stats().hp()==9&&dice.rolls==3,"recovered persistence commits fixed outcome once");
        }finally{cleanup(dm);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void healingRechecksCampaignPermissionAndStorage(TestContext ctx)throws Exception{
        ServerPlayerEntity dm=null;
        try(var f=new Fixture(ctx)){
            dm=viewer(f,true);var a=f.actor(1,1,true);var b=f.actor(2,1,true);f.repo.commitSrdHealth(CharacterService.srdStats(b,f.campaign),new SrdHealth(1,10,0));var e=begin(f,new Dice(20,1,20),a,b);var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);e.confirm(f.dm,preview.id());
            var fault=new java.util.concurrent.atomic.AtomicBoolean(true);var broken=SrdSaveFault.repository(f.dir.resolve("state.json"),fault::get);f.field.set(null,broken);long revision=e.revision();
            var healing=args("target",b.getUuid(),"amount",1024,"revision",revision);check(call(dm,e,UUID.randomUUID(),"heal",healing).code().equals("STORAGE_ERROR"),"real health atomic replacement failure reported");check(CharacterService.srdStats(b,f.campaign).stats().hp()==0&&e.revision()==revision,"failed healing leaves HP and encounter revision unchanged");
            fault.set(false);var other=broken.createCampaign(f.dm,"Other recovery scope");broken.invite(f.dm,other,1,dm.getUuid(),true);CharacterService.select(dm,other);
            check(call(dm,e,UUID.randomUUID(),"heal",healing).code().equals("REJECTED"),"selected campaign change prevents stale healing");CharacterService.select(dm,f.campaign);
            broken.revokeDm(f.dm,f.campaign,broken.campaign(f.campaign).revision(),dm.getUuid());check(call(dm,e,UUID.randomUUID(),"heal",healing).code().equals("REJECTED"),"revoked manager cannot confirm prior healing review");
            broken.invite(f.dm,f.campaign,broken.campaign(f.campaign).revision(),dm.getUuid(),true);check(call(dm,e,UUID.randomUUID(),"heal",healing).code().equals("OK"),"new explicit authorized heal succeeds after resolved storage fault");check(CharacterService.srdStats(b,f.campaign).stats().hp()==10,"server caps healing at maximum HP");
        }finally{cleanup(dm);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void healingRejectsBindingAndEntityChanges(TestContext ctx)throws Exception {
        ServerPlayerEntity dm=null;
        try(var f=new Fixture(ctx)){
            dm=viewer(f,true);var a=f.actor(1,1,true);var b=f.actor(2,1,true);f.repo.commitSrdHealth(CharacterService.srdStats(b,f.campaign),new SrdHealth(1,10,0));var e=begin(f,new Dice(20,1,20),a,b);var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);e.confirm(f.dm,preview.id());
            var saved=CharacterService.srdStats(b,f.campaign);var heal=args("target",b.getUuid(),"amount",4,"revision",e.revision());
            var unknown=SrdNetworking.handle(dm,new SrdRequestPayload(UUID.randomUUID(),e.turn(),UUID.randomUUID(),"heal",heal.toString()));check(unknown.code().equals("REJECTED")&&unknown.json().equals("{}"),"unknown encounter cannot heal or disclose state");
            f.repo.unbind(f.dm,b.getUuid());var unbound=f.repo.state();check(call(dm,e,UUID.randomUUID(),"heal",heal).code().equals("REJECTED")&&f.repo.state()==unbound,"changed binding rejected without persistence");
            f.repo.bind(f.dm,saved.character(),f.repo.character(saved.character()).revision(),b.getUuid(),null);
            b.discard();var missing=f.repo.state();check(call(dm,e,UUID.randomUUID(),"heal",heal).code().equals("REJECTED")&&f.repo.state()==missing,"unloaded/dead actor cannot be healed via stale roster");
        }finally{cleanup(dm);}ctx.complete();
    }

}
