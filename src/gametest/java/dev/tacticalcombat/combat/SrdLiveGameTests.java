package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;

public class SrdLiveGameTests implements FabricGameTest {
    static final class Dice implements SrdRules.Dice {
        final ArrayDeque<Integer> faces=new ArrayDeque<>(); int rolls;
        Dice(Integer... values) { faces.addAll(List.of(values)); }
        public int roll(int sides) { rolls++; if(faces.isEmpty()) throw new IllegalStateException("Unexpected extra RNG"); return faces.remove(); }
    }
    static void capability(ServerPlayerEntity player) throws Exception {
        var addon=net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(player.networkHandler);
        var field=net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var channels=(Set<net.minecraft.util.Identifier>)field.get(addon); channels.add(SrdStatePayload.ID.id());
    }
    static SrdEncounter begin(Fixture f,Dice dice,net.minecraft.entity.LivingEntity... actors) {
        var e=SrdEncounter.start(f.ctx.getWorld(),f.dm,f.campaign,List.of(actors),dice); f.encounters.add(e); e.begin(); return e;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unsupportedClientAdmissionAndPcTieAgreement(TestContext ctx) throws Exception {
        var a=ctx.createMockCreativeServerPlayerInWorld(); var b=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            int i=0;
            for(var p:List.of(a,b)) {
                f.repo.invite(f.dm,f.campaign,++i,p.getUuid(),false);
                UUID id=f.repo.submit(CharacterService.principal(p),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,p.getUuid(),p.getUuid());
                CharacterService.select(p,f.campaign);p.refreshPositionAndAngles(f.origin.getX()+i+.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            }
            rejects(()->f.start(a,b),"unsupported client cannot be admitted"); check(!CombatManager.isInCombat(a),"failed capability check does not claim actors");
            capability(a);capability(b);var e=begin(f,new Dice(10,10),a,b);
            rejects(()->e.tie(f.dm,List.of(a.getUuid(),b.getUuid())),"DM cannot silently choose tied PC order");
            e.tie(CharacterService.principal(a),List.of(b.getUuid(),a.getUuid()));check(e.state()==SrdEncounter.State.PENDING,"one tied player cannot speak for another");
            e.tie(CharacterService.principal(b),List.of(a.getUuid(),b.getUuid()));check(e.state()==SrdEncounter.State.PENDING,"disagreement stays pending");
            e.tie(CharacterService.principal(b),List.of(b.getUuid(),a.getUuid()));check(e.current()==b && e.state()==SrdEncounter.State.ACTIVE,"matching PC choices establish order");
            var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
            check(!e.admit(outsider,request(e,"endturn",new JsonObject())),"outsider cannot consume active turn request budget");
        } finally {
            for(var p:List.of(a,b)) {CharacterService.forget(p.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(p);}
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void boundedSrdProtocolRoundTripsAndRejectsMalformedLengths(TestContext ctx) {
        var buffer=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),ctx.getWorld().getRegistryManager());
        try {
            var request=new SrdRequestPayload(UUID.randomUUID(),1,UUID.randomUUID(),"confirm","{\"intent\":\"test\"}"); SrdRequestPayload.CODEC.encode(buffer,request);
            check(request.equals(SrdRequestPayload.CODEC.decode(buffer)) && !buffer.isReadable(),"SRD request round trip");
            var state=new SrdStatePayload(request.encounter(),1,"RESULT","Committed","{}"); SrdStatePayload.CODEC.encode(buffer,state);
            check(state.equals(SrdStatePayload.CODEC.decode(buffer)) && !buffer.isReadable(),"SRD reply round trip");
            buffer.clear(); buffer.writeUuid(request.encounter());buffer.writeLong(1);buffer.writeUuid(request.request());buffer.writeString("preview");buffer.writeVarInt(SrdRequestPayload.LIMIT*3+1);
            boolean denied=false; try { SrdRequestPayload.CODEC.decode(buffer); } catch(RuntimeException expected) {denied=true;} check(denied,"oversized declared request rejected before allocation");
            buffer.clear();buffer.writeUuid(request.encounter());denied=false;
            try {SrdStatePayload.CODEC.decode(buffer);}catch(RuntimeException expected){denied=true;}check(denied,"truncated reply rejected");
            buffer.clear();denied=false;try {SrdRequestPayload.CODEC.encode(buffer,new SrdRequestPayload(request.encounter(),1,request.request(),"preview","x".repeat(SrdRequestPayload.LIMIT+1)));}catch(RuntimeException expected){denied=true;}check(denied,"oversized outbound request rejected");
        } finally {buffer.release();}
        ctx.complete();
    }
    static JsonObject data(SrdStatePayload payload) { return JsonParser.parseString(payload.json()).getAsJsonObject(); }
    static SrdRequestPayload request(SrdEncounter e,String op,JsonObject args) { return new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),op,args.toString()); }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void mockPlayerUsesProductionProtocolForMovePreviewConfirmAndEndTurn(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        var outsider=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            capability(player); capability(outsider);
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);
            var owner=CharacterService.principal(player);
            UUID id=f.repo.submit(owner,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString()); f.repo.approve(f.dm,id,1);
            f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid()); CharacterService.select(player,f.campaign);
            player.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            outsider.refreshPositionAndAngles(f.origin.getX()+10.5,f.origin.getY()+1,f.origin.getZ()+7.5,0,0);
            var mob=f.actor(4,1,true); var dice=new Dice(20,1,20); var e=begin(f,dice,player,mob);
            var state=SrdNetworking.handle(player,request(e,"state",new JsonObject()));
            check(state.code().equals("STATE") && data(state).getAsJsonArray("actors").size()==2,"authenticated production handler returns encounter snapshot");
            var entries=data(state).getAsJsonArray("actors"); check(entries.get(0).getAsJsonObject().has("hp") && !entries.get(1).getAsJsonObject().has("hp"),"enemy HP/AC/defenses stay private");
            var denied=SrdNetworking.handle(outsider,request(e,"state",new JsonObject())); check(denied.code().equals("REJECTED") && denied.json().equals("{}"),"unrelated campaign viewer receives no encounter data");
            var grid=SrdNetworking.handle(player,request(e,"grid",new JsonObject())); check(grid.code().equals("GRID") && !data(grid).getAsJsonArray("grid").isEmpty(),"protocol carries Stage B reachable squares");
            var move=new JsonObject(); move.addProperty("x",f.origin.getX()+3); move.addProperty("z",f.origin.getZ()+1); move.addProperty("y",f.origin.getY()+1);
            var moveRequest=request(e,"move",move); check(SrdNetworking.handle(player,moveRequest).code().equals("OK"),"player movement accepted through real handler");
            check(SrdNetworking.handle(player,moveRequest).code().equals("REJECTED"),"replayed movement packet rejected"); e.tick(); e.tick();
            check(e.grid(owner).remainingFeet()==20,"mock player server position and movement ledger synchronized");
            var choice=new JsonObject(); choice.addProperty("target",mob.getUuid().toString()); choice.addProperty("weapon","UNARMED"); choice.addProperty("ability","STR"); choice.addProperty("twoHands",false);
            choice.addProperty("damage",999); choice.addProperty("roll",20);
            var preview=SrdNetworking.handle(player,request(e,"preview",choice)); check(preview.code().equals("PREVIEW") && dice.rolls==2 && !e.actionUsed(),"selection previews without accepting client dice/damage or spending action");
            var confirm=new JsonObject(); confirm.addProperty("intent",data(preview).getAsJsonObject("preview").get("id").getAsString()); var packet=request(e,"confirm",confirm);
            var result=SrdNetworking.handle(player,packet); check(result.code().equals("RESULT") && e.actionUsed(),"confirmation commits before returning success");
            var detail=data(result).getAsJsonObject("result");
            check(detail.get("rolledDamage").getAsInt()==1 && !detail.has("hpBefore") && !detail.has("hpAfter") && !detail.has("zeroHp"),"ordinary attacker gets rolled damage but no private target HP/defeat fields");
            check(CharacterService.srdStats(mob,f.campaign).stats().hp()==9 && player.getHealth()==20,"forged fields cannot change authoritative damage");
            check(SrdNetworking.handle(player,packet).code().equals("RESULT") && dice.rolls==3 && CharacterService.srdStats(mob,f.campaign).stats().hp()==9,"duplicate result request returns durable result without reroll or damage");
            long old=e.turn(); check(SrdNetworking.handle(player,request(e,"endturn",new JsonObject())).code().equals("OK") && e.current()==mob,"player End Turn advances live encounter");
            check(SrdNetworking.handle(player,new SrdRequestPayload(e.id(),old+99,UUID.randomUUID(),"confirm",confirm.toString())).code().equals("REJECTED"),"altered stale turn identity denied even for a retained receipt");
            e.stop(f.dm); check(!CombatManager.isInCombat(player),"termination releases player ownership");
        } finally {
            CharacterService.forget(player.getUuid()); ctx.getWorld().getServer().getPlayerManager().remove(player); ctx.getWorld().getServer().getPlayerManager().remove(outsider);
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void zeroHpPausesWithoutVanillaDeathAndDmCanHealOrEnd(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true); var b=f.actor(2,1,true);
            f.repo.commitSrdHealth(CharacterService.srdStats(b,f.campaign),new SrdHealth(1,10,0));
            var e=begin(f,new Dice(20,1,20),a,b);
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false); var committed=e.confirm(f.dm,preview.id());
            var feedback=SrdNetworking.result(committed.plan,f.dm);
            check(feedback.get("zeroHp").getAsBoolean()&&feedback.get("hpAfter").getAsInt()==0&&feedback.get("target").getAsString().equals(b.getUuidAsString()),"authorized outcome identifies exactly which target reached zero HP");
            check(e.state()==SrdEncounter.State.PAUSED && b.isAlive() && b.getHealth()==20 && CharacterService.srdStats(b,f.campaign).stats().hp()==0,"zero D&D HP pauses instead of vanilla death");
            check(dev.tacticalcombat.uiapi.UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,dev.tacticalcombat.uiapi.UiEvents.Kind.ZERO_HP).events().size()==1,"committed zero HP emits one event");
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false),"ordinary actions prohibited at adjudication pause");
            rejects(()->e.endTurn(f.dm),"zero HP cannot be skipped as ordinary live turn");
            e.heal(f.dm,b.getUuid(),4); check(CharacterService.srdStats(b,f.campaign).stats().hp()==4 && e.state()==SrdEncounter.State.PAUSED,"scoped DM healing stays paused for explicit termination");
            check(dev.tacticalcombat.uiapi.UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,dev.tacticalcombat.uiapi.UiEvents.Kind.HEALING).events().getFirst().summary().endsWith("4"),"healing event describes applied gain");
            e.stop(f.dm); check(CharacterService.srdStats(b,f.campaign).stats().hp()==4 && !CombatManager.isInCombat(b),"end retains healed D&D resources");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void initiativeMovementConfirmedAttackPersistentHpAndTurnAdvance(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true); var b=f.actor(4,1,true); var dice=new Dice(20,1,20);
            var e=begin(f,dice,a,b); check(e.current()==a && e.round()==1 && e.state()==SrdEncounter.State.ACTIVE,"server initiative enters active turn; natural 1/20 have no attack semantics");
            var cell=new SrdMovement.Cell(f.origin.getX()+3,f.origin.getZ()+1,f.origin.getY()+1);
            check(e.grid(f.dm).reachable().get(cell).costFeet()==10,"encounter invokes Stage B weighted grid");
            check(e.move(f.dm,UUID.randomUUID(),cell).accepted(),"two-step live route accepted"); e.tick(); e.tick();
            check(e.grid(f.dm).remainingFeet()==20,"live scheduler charges completed squares exactly once");
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            check(dice.rolls==2 && !e.actionUsed() && CharacterService.srdStats(b,f.campaign).stats().hp()==10,"preview rolls nothing and spends nothing");
            var action=e.confirm(f.dm,preview.id());
            check(action.state==SrdEncounter.TransactionState.COMMITTED && action.roll.critical() && e.actionUsed(),"confirmed authoritative attack resolves and spends one action");
            check(CharacterService.srdStats(b,f.campaign).stats().hp()==9 && b.getHealth()==20,"D&D damage never changes vanilla HP");
            check(new CharacterRepository(f.dir.resolve("state.json")).srdSnapshot(b.getUuid(),f.campaign).stats().hp()==9,"HP durable before success");
            rejects(()->e.confirm(f.dm,preview.id()),"confirmation replay rejected"); check(dice.rolls==3,"replay never rerolls");
            long turn=e.turn(); e.endTurn(f.dm); check(e.current()==b && e.turn()>turn,"turn advances to next actor with latest persistent resources");
            rejects(()->e.confirm(f.dm,preview.id()),"old-turn intent rejected"); e.endTurn(f.dm); check(e.round()==2,"full order increments round");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void missStalePreviewAndTieDecisionPermissions(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true); var b=f.actor(2,1,true); var dice=new Dice(10,10,1);
            var e=begin(f,dice,a,b); check(e.state()==SrdEncounter.State.PENDING && !e.actionUsed(),"tied NPC initiative pauses for explicit DM order");
            var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
            rejects(()->e.tie(outsider,List.of(a.getUuid(),b.getUuid())),"outsider cannot order NPC turns");
            e.tie(f.dm,List.of(b.getUuid(),a.getUuid())); check(e.current()==b,"DM tie decision establishes chosen order");
            rejects(()->e.preview(outsider,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false),"outsider cannot control actor");
            var preview=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            UUID staleIntent=preview.id();
            a.setPosition(a.getPos().add(0,.1,0)); rejects(()->e.confirm(f.dm,staleIntent),"moved target invalidates intent without spending action");
            check(!e.actionUsed() && dice.rolls==2,"stale confirmation no RNG/cost"); e.tick();
            preview=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            var result=e.confirm(f.dm,preview.id());
            check(!result.roll.hit() && e.actionUsed() && CharacterService.srdStats(a,f.campaign).stats().hp()==10,"accepted miss spends action but no HP");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void failedSaveRetainsDiceAndExpectedRevisionWithoutDoubleDamage(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true); var b=f.actor(2,1,true); var dice=new Dice(20,1,20);
            var e=begin(f,dice,a,b);
            var failure=new java.util.concurrent.atomic.AtomicBoolean(true);
            f.field.set(null,SrdSaveFault.repository(f.dir.resolve("state.json"),failure::get));
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            var result=e.confirm(f.dm,preview.id());
            check(e.state()==SrdEncounter.State.PAUSED && e.actionUsed() && result.state==SrdEncounter.TransactionState.SAVE_FAILED,"save failure holds reserved action in explicit paused state");
            check(CharacterService.srdStats(b,f.campaign).stats().hp()==10 && dice.rolls==3,"failed write publishes no HP and retains exact rolls");
            rejects(()->e.endTurn(f.dm),"failed transaction cannot be skipped by end-turn");
            e.retry(f.dm); check(dice.rolls==3 && CharacterService.srdStats(b,f.campaign).stats().hp()==10,"failed retry does not reroll or rebase");
            check(dev.tacticalcombat.uiapi.UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,dev.tacticalcombat.uiapi.UiEvents.Kind.ATTACK).events().isEmpty() && dev.tacticalcombat.uiapi.UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,dev.tacticalcombat.uiapi.UiEvents.Kind.DAMAGE).events().isEmpty(),"failed transaction emits no success events");
            failure.set(false); e.retry(f.dm);
            check(result.state==SrdEncounter.TransactionState.COMMITTED && CharacterService.srdStats(b,f.campaign).stats().hp()==9 && dice.rolls==3,"same fixed transaction commits once after storage recovery");
            rejects(()->e.retry(f.dm),"committed transaction cannot apply twice");
            check(dev.tacticalcombat.uiapi.UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,dev.tacticalcombat.uiapi.UiEvents.Kind.DAMAGE).events().size()==1,"successful storage retry emits damage once");
        }
        ctx.complete();
    }
}
