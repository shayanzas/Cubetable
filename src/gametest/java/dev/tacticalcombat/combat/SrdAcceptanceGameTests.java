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
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

/** Production entrypoints with embedded players; not graphical or remote-client acceptance. */
public class SrdAcceptanceGameTests implements FabricGameTest {
    private static void remove(TestContext ctx,ServerPlayerEntity... players) {
        for(var p:players) { CharacterService.forget(p.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(p); }
    }
    private static void seat(Fixture f,ServerPlayerEntity p,int x) {
        p.refreshPositionAndAngles(f.origin.getX()+x+.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void campaignCharacterCommandAndProtocolCompleteCombatLoop(TestContext ctx) throws Exception {
        var dm=ctx.createMockCreativeServerPlayerInWorld();var pc=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            capability(dm);capability(pc);
            f.repo.invite(f.dm,f.campaign,1,dm.getUuid(),true);f.repo.invite(f.dm,f.campaign,2,pc.getUuid(),false);
            f.repo.profile(f.dm,f.campaign,3,RulesProfiles.SRD);CharacterService.select(dm,f.campaign);
            var selection=new JsonObject();selection.addProperty("campaign",f.campaign.toString());
            check(CharacterNetworking.handle(pc,new CharacterRequestPayload(1,"select",selection.toString()),100).code().equals("OK"),"campaign selected through real handler");
            var created=CharacterNetworking.handle(pc,new CharacterRequestPayload(2,"srd_create","{}"),120);
            check(created.code().equals("OK"),"server character created");
            UUID id=UUID.fromString(JsonParser.parseString(created.json()).getAsJsonObject().get("id").getAsString());
            var approval=new JsonObject();approval.addProperty("character",id.toString());approval.addProperty("revision",1);
            check(CharacterNetworking.handle(dm,new CharacterRequestPayload(1,"approve",approval.toString()),140).code().equals("OK"),"ordinary campaign co-DM approves");
            CharacterService.bind(dm,id,2,pc);seat(f,pc,1);seat(f,dm,10);var npc=f.actor(4,1,true);
            var dispatcher=ctx.getWorld().getServer().getCommandManager().getDispatcher();
            check(dispatcher.execute("tbc srd test-public-ffa "+pc.getUuid()+","+npc.getUuid(),dm.getCommandSource())==1,"production command starts controlled encounter");
            var e=(SrdEncounter)CombatManager.encounter(pc);f.encounters.add(e);
            if(e.state()==SrdEncounter.State.PENDING) e.tie(CharacterService.principal(dm),List.of(pc.getUuid(),npc.getUuid()));
            if(e.current()!=pc) e.endTurn(CharacterService.principal(dm));
            check(e.current()==pc,"initiative establishes player turn");
            var move=new JsonObject();move.addProperty("x",f.origin.getX()+3);move.addProperty("z",f.origin.getZ()+1);move.addProperty("y",f.origin.getY()+1);
            check(SrdNetworking.handle(pc,request(e,"grid",new JsonObject())).code().equals("GRID"),"live reachable grid");
            check(SrdNetworking.handle(pc,request(e,"move",move)).code().equals("OK"),"live route accepted");e.tick();e.tick();
            var choice=new JsonObject();choice.addProperty("target",npc.getUuid().toString());choice.addProperty("weapon","UNARMED");choice.addProperty("ability","STR");choice.addProperty("twoHands",false);
            var preview=SrdNetworking.handle(pc,request(e,"preview",choice));check(preview.code().equals("PREVIEW"),"live attack preview");
            var confirm=new JsonObject();confirm.addProperty("intent",data(preview).getAsJsonObject("preview").get("id").getAsString());
            // Seed the SERVER RNG in this development test; no roll input exists in the confirmation.
            long seed=0;while(net.minecraft.util.math.random.Random.create(seed).nextInt(20)!=19) seed++;
            ctx.getWorld().getRandom().setSeed(seed);
            var result=SrdNetworking.handle(pc,request(e,"confirm",confirm));
            check(result.code().equals("RESULT") && data(result).getAsJsonObject("result").get("critical").getAsBoolean(),"server-generated natural 20 resolved through real handler");
            check(CharacterService.srdStats(npc,f.campaign).stats().hp()==9 && npc.getHealth()==20,"D&D damage only");
            check(new CharacterRepository(f.dir.resolve("state.json")).srdSnapshot(npc.getUuid(),f.campaign).stats().hp()==9,"success follows durable HP write");
            var dmState=data(SrdNetworking.handle(dm,request(e,"state",new JsonObject())));
            var npcState=dmState.getAsJsonArray("actors").asList().stream().map(JsonElement::getAsJsonObject)
                    .filter(actor->actor.get("id").getAsString().equals(npc.getUuid().toString())).findFirst().orElseThrow();
            check(npcState.get("hp").getAsInt()==9,"authorized DM protocol sees updated HP independent of initiative order");
            check(SrdNetworking.handle(pc,request(e,"endturn",new JsonObject())).code().equals("OK") && e.current()==npc,"next NPC turn");
            check(dispatcher.execute("tbc srd end "+e.id(),dm.getCommandSource())==1 && !CombatManager.isInCombat(pc) && !CombatManager.isInCombat(npc),"command cleanup releases all actors");
        } finally {remove(ctx,dm,pc);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void campaignDefaultChangeAndDisconnectCannotConvertOrStrandActors(TestContext ctx) throws Exception {
        var pc=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            capability(pc);f.repo.invite(f.dm,f.campaign,1,pc.getUuid(),false);
            UUID id=f.repo.submit(CharacterService.principal(pc),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());
            f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,pc.getUuid(),pc.getUuid());CharacterService.select(pc,f.campaign);seat(f,pc,1);
            var npc=f.actor(4,1,true);var e=begin(f,new Dice(20,1),pc,npc);var before=CharacterService.srdStats(pc,f.campaign);
            f.repo.profile(f.dm,f.campaign,2,RulesProfiles.MANUAL);
            check(e.rules().id().equals(RulesProfiles.SRD) && CharacterService.srdStats(pc,f.campaign).equals(before),"campaign default does not convert active rules or characters");
            rejects(()->CharacterService.select(pc,f.campaign),"active actor cannot change campaign selection");
            check(e.move(CharacterService.principal(pc),UUID.randomUUID(),new SrdMovement.Cell(f.origin.getX()+3,f.origin.getZ()+1,f.origin.getY()+1)).accepted(),"queued movement before disconnect");
            ctx.getWorld().getServer().getPlayerManager().remove(pc);
            check(e.tick(),"disconnected player ends controlled encounter before executing route");CombatManager.endCombat(e);
            check(!CombatManager.isInCombat(pc) && !CombatManager.isInCombat(npc) && CharacterService.srdStats(npc,f.campaign).stats().hp()==10,"disconnect cleanup preserves resources and releases NPCs");
        } finally {CharacterService.forget(pc.getUuid());}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void repeatedPreviewsAndBoundedGridMeasuredWithoutSpendingOrRng(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1);var e=begin(f,dice,a,b);
            long[] previews=new long[100],grids=new long[10],ticks=new long[100];int nodes=0;
            for(int i=0;i<previews.length;i++) {long at=System.nanoTime();e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);previews[i]=System.nanoTime()-at;}
            for(int i=0;i<grids.length;i++) {long at=System.nanoTime();var grid=e.grid(f.dm);grids[i]=System.nanoTime()-at;nodes=grid.reachable().size();check(nodes<=4096,"grid remains bounded");}
            for(int i=0;i<ticks.length;i++) {long at=System.nanoTime();check(!e.tick(),"stable encounter stays active");ticks[i]=System.nanoTime()-at;}
            check(dice.rolls==2 && !e.actionUsed() && CharacterService.srdStats(b,f.campaign).stats().hp()==10,"repeated previews never roll or spend");
            measure("preview",previews);measure("grid",grids);measure("idle tick",ticks);
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD bounded exercise: actors=2, grid nodes={}, flat 12x9 fixture; not production throughput",nodes);
        }
        ctx.complete();
    }
    private static void measure(String name,long[] values) {
        Arrays.sort(values);
        dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD measured {} n={} p50={}us p95={}us max={}us",name,values.length,values[values.length/2]/1000,values[(int)Math.ceil(values.length*.95)-1]/1000,values[values.length-1]/1000);
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void packetFloodAndCapabilityLossFailClosed(TestContext ctx) throws Exception {
        var pc=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            capability(pc);f.repo.invite(f.dm,f.campaign,1,pc.getUuid(),false);
            UUID id=f.repo.submit(CharacterService.principal(pc),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());
            f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,pc.getUuid(),pc.getUuid());CharacterService.select(pc,f.campaign);seat(f,pc,1);
            var npc=f.actor(3,1,true);var dice=new Dice(20,1);var e=begin(f,dice,pc,npc);
            for(int i=0;i<2;i++) check(SrdNetworking.handle(pc,request(e,"grid",new JsonObject())).code().equals("GRID"),"bounded grid admitted");
            var gridFlood=SrdNetworking.handle(pc,request(e,"grid",new JsonObject()));
            check(gridFlood.code().equals("REJECTED") && gridFlood.message().contains("Grid rate limit"),"expensive grid has a tighter independent limit");
            for(int i=0;i<9;i++) check(SrdNetworking.handle(pc,request(e,"state",new JsonObject())).code().equals("STATE"),"bounded refresh admitted");
            for(int i=0;i<100;i++) {
                var response=SrdNetworking.handle(pc,request(e,"state",new JsonObject()));
                check(response.code().equals("REJECTED") && response.json().equals("{}"),"flood rejected without snapshot");
            }
            check(dice.rolls==2 && !e.actionUsed(),"packet flood does not mutate action state");
            var addon=net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(pc.networkHandler);
            var field=net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels");field.setAccessible(true);
            ((Set<?>)field.get(addon)).remove(SrdStatePayload.ID.id());
            check(e.tick(),"lost protocol capability requests cleanup");CombatManager.endCombat(e);
            check(!CombatManager.isInCombat(npc),"capability loss cannot strand NPC");
        } finally {remove(ctx,pc);}
        ctx.complete();
    }
}
