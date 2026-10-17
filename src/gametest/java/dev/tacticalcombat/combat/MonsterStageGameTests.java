package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.uiapi.UiApiService.Operation;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public class MonsterStageGameTests implements FabricGameTest {
    private static JsonObject args(Object... pairs){var a=new JsonObject();for(int i=0;i<pairs.length;i+=2){if(pairs[i+1] instanceof Number n)a.addProperty(pairs[i].toString(),n);else a.addProperty(pairs[i].toString(),pairs[i+1].toString());}return a;}
    private static ServerPlayerEntity manager(Fixture f)throws Exception{f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);var p=f.ctx.createMockCreativeServerPlayerInWorld();SrdLiveGameTests.capability(p);f.repo.invite(f.dm,f.campaign,2,p.getUuid(),true);CharacterService.select(p,f.campaign);p.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);return p;}
    private static JsonObject choose(UUID stage,long revision,UUID... actors){var a=args("stage",stage,"revision",revision);var array=new JsonArray();for(var actor:actors)array.add(actor.toString());a.add("actors",array);return a;}
    private static void cleanup(ServerPlayerEntity p){EncounterStages.clear();EncounterStages.activationFault=index->{};if(p!=null){CharacterService.forget(p.getUuid());p.getServer().getPlayerManager().remove(p);}}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void preparationIsAtomicReplaySafeAndPermissionChecked(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);var mob=f.actor(4,1,false);var op=UUID.randomUUID();var a=args("actor",mob.getUuid(),"operation",op,"source","srd51:monsters:zombie");var prepared=MonsterPreparation.handle(p,MONSTER_PREPARE,a);check(prepared.get("bound").getAsBoolean(),"prepared/bound together");check(f.repo.srdSnapshot(mob.getUuid(),f.campaign).stats().hp()==22,"zombie source HP");MonsterPreparation.handle(p,MONSTER_PREPARE,a);check(f.repo.state().characters().size()==1,"retry cannot duplicate NPC");var peer=f.actor(7,1,true);var encounter=f.start(mob,peer);check(encounter!=null,"source NPC admitted without player class");encounter.stop(f.dm);f.repo.revokeDm(f.dm,f.campaign,3,p.getUuid());boolean denied=false;try{MonsterPreparation.handle(p,MONSTER_PREPARE,a);}catch(IllegalArgumentException e){denied=true;}check(denied,"revocation blocks preparation retry");}finally{cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void stageValidationActivationReplayAndRollback(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);EncounterStages.initialize(f.dir.resolve("stages.json"));var a=f.actor(4,1,true);var b=f.actor(7,1,true);var id=UUID.randomUUID();EncounterStages.handle(p,STAGE_CREATE,args("stage",id,"name","Crypt","description","Saved"));var originalA=a.getPos();var originalB=b.getPos();EncounterStages.handle(p,STAGE_CAPTURE,choose(id,1,a.getUuid(),b.getUuid()));check(a.getPos().equals(originalA)&&b.getPos().equals(originalB),"capture does not move actors");a.setPosition(originalA.add(0,0,3));b.setPosition(originalB.add(0,0,3));var fromA=a.getPos();var fromB=b.getPos();var preview=EncounterStages.handle(p,STAGE_VALIDATE,choose(id,2,a.getUuid(),b.getUuid()));EncounterStages.activationFault=index->{if(index==1)throw new IllegalStateException("Injected placement failure");};boolean denied=false;try{EncounterStages.handle(p,STAGE_ACTIVATE,args("token",preview.get("token").getAsString()));}catch(IllegalArgumentException e){denied=true;}check(denied&&a.getPos().equals(fromA)&&b.getPos().equals(fromB),"failure after first move restores positions");EncounterStages.activationFault=index->{};
            preview=EncounterStages.handle(p,STAGE_VALIDATE,choose(id,2,a.getUuid(),b.getUuid()));var token=args("token",preview.get("token").getAsString());EncounterStages.handle(p,STAGE_ACTIVATE,token);check(a.getPos().equals(originalA)&&b.getPos().equals(originalB),"complete validated placement");denied=false;try{EncounterStages.handle(p,STAGE_ACTIVATE,token);}catch(IllegalArgumentException e){denied=true;}check(denied&&!CombatManager.isInCombat(a),"activation token cannot replay or start combat");EncounterStages.initialize(f.dir.resolve("stages.json"));check(EncounterStages.handle(p,STAGE_LIST,new JsonObject()).getAsJsonArray("stages").size()==1,"stage reload retained");
        }finally{cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void stageRejectsForgedStaleReservedAndRevokedPlacement(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){
            p=manager(f);EncounterStages.initialize(f.dir.resolve("stages.json"));var a=f.actor(4,1,true);var b=f.actor(7,1,true);var id=UUID.randomUUID();
            var forged=args("stage",id,"name","Crypt","description","Test","x",999);boolean denied=false;try{EncounterStages.handle(p,STAGE_CREATE,forged);}catch(IllegalArgumentException expected){denied=true;}check(denied,"client coordinates rejected");
            EncounterStages.handle(p,STAGE_CREATE,args("stage",id,"name","Crypt","description","Test"));EncounterStages.handle(p,STAGE_CAPTURE,choose(id,1,a.getUuid(),b.getUuid()));
            denied=false;try{EncounterStages.handle(p,STAGE_CAPTURE,choose(id,1,a.getUuid(),b.getUuid()));}catch(IllegalArgumentException expected){denied=true;}check(denied,"stale template revision rejected");
            var sa=f.repo.srdSnapshot(a.getUuid(),f.campaign);var sb=f.repo.srdSnapshot(b.getUuid(),f.campaign);
            var attack=new dev.tacticalcombat.character.GameplayActionPlan.Attack(dev.tacticalcombat.rules.srd.SrdEquipment.Weapon.UNARMED,dev.tacticalcombat.rules.srd.SrdRules.AttackMode.MELEE,dev.tacticalcombat.rules.srd.SrdCharacter.Ability.STR,false,0,false,false);
            var result=dev.tacticalcombat.rules.srd.SrdRules.attack(sa.stats(),attack.weapon(),attack.mode(),attack.ability(),false,sb.stats().armorClass(),false,false,n->10);
            var plan=dev.tacticalcombat.character.GameplayActionPlan.weapon(UUID.randomUUID(),f.dm.id(),f.campaign,UUID.randomUUID(),1,sa,f.dm.id(),sb,f.dm.id(),attack,result);
            f.repo.prepareGameplay(f.dm,plan);denied=false;try{EncounterStages.handle(p,STAGE_VALIDATE,choose(id,2,a.getUuid(),b.getUuid()));}catch(IllegalArgumentException expected){denied=true;}check(denied,"stage cannot bypass prepared gameplay reservation");f.repo.cancelGameplay(f.dm,plan.operation(),"Automated test audited cancellation");
            var preview=EncounterStages.handle(p,STAGE_VALIDATE,choose(id,2,a.getUuid(),b.getUuid()));var original=a.getPos();f.repo.revokeDm(f.dm,f.campaign,3,p.getUuid());denied=false;try{EncounterStages.handle(p,STAGE_ACTIVATE,args("token",preview.get("token").getAsString()));}catch(IllegalArgumentException expected){denied=true;}check(denied&&a.getPos().equals(original),"revocation prevents authorized preview from moving actors");
        }finally{cleanup(p);}ctx.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void savedMonsterSlotReusesNewEntityAndRejectsUnsafeTerrain(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){
            p=manager(f);EncounterStages.initialize(f.dir.resolve("stages.json"));var original=f.actor(4,1,false);var source="srd51:monsters:zombie";
            MonsterPreparation.handle(p,MONSTER_PREPARE,args("actor",original.getUuid(),"operation",UUID.randomUUID(),"source",source));var id=UUID.randomUUID();EncounterStages.handle(p,STAGE_CREATE,args("stage",id,"name","Replacement","description",""));EncounterStages.handle(p,STAGE_CAPTURE,choose(id,1,original.getUuid()));var destination=original.getPos();original.discard();
            boolean denied=false;try{EncounterStages.handle(p,STAGE_VALIDATE,choose(id,2,original.getUuid()));}catch(IllegalArgumentException expected){denied=true;}check(denied,"despawned actor unavailable");
            var replacement=f.actor(7,4,false);MonsterPreparation.handle(p,MONSTER_PREPARE,args("actor",replacement.getUuid(),"operation",UUID.randomUUID(),"source",source));var before=replacement.getPos();var block=net.minecraft.util.math.BlockPos.ofFloored(destination);ctx.getWorld().setBlockState(block,net.minecraft.block.Blocks.STONE.getDefaultState());denied=false;try{EncounterStages.handle(p,STAGE_VALIDATE,choose(id,2,replacement.getUuid()));}catch(IllegalArgumentException expected){denied=true;}check(denied&&replacement.getPos().equals(before),"unsafe destination rejected without movement");ctx.getWorld().setBlockState(block,net.minecraft.block.Blocks.AIR.getDefaultState());
            var preview=EncounterStages.handle(p,STAGE_VALIDATE,choose(id,2,replacement.getUuid()));EncounterStages.handle(p,STAGE_ACTIVATE,args("token",preview.get("token").getAsString()));check(replacement.getPos().equals(destination),"source-compatible replacement uses saved slot without old entity UUID");
        }finally{cleanup(p);}ctx.complete();
    }

}
