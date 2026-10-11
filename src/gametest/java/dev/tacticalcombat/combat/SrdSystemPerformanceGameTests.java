package dev.tacticalcombat.combat;

import com.google.gson.JsonObject;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdGridPerformanceGameTests.*;

/** Warm repeated measurements; timers exclude fixture setup and assertions. No simulated network latency claims. */
public class SrdSystemPerformanceGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void tacticalSnapshotTraffic(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();Combat fight=null;
        try(var f=new Fixture(ctx)) {
            player.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var mob=f.actor(4,2,false);PacketProbe.clear();PacketProbe.enabled=true;
            fight=Combat.start(ctx.getWorld(),List.of(player),List.of(mob));
            check(fight!=null,"legacy Tactical admission retained");
            var tick=Combat.class.getDeclaredField("tickCounter");tick.setAccessible(true);
            for(int i=1;i<=10;i++) {tick.setInt(fight,i*2);fight.sync();}
            long packets=PacketProbe.packets.stream().filter(p->p instanceof CombatStatePayload s && s.active()).count();
            int bytes=0;var buf=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),ctx.getWorld().getRegistryManager());
            try {for(var packet:PacketProbe.packets) if(packet instanceof CombatStatePayload s) {buf.clear();CombatStatePayload.CODEC.encode(buf,s);bytes+=buf.readableBytes();}}
            finally {buf.release();}
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_NETWORK mode=tactical periodic_sync_calls=10 simulated_ticks=20 actual_payloads={} encoded_bytes={}",packets,bytes);
            check(packets==2,"initial state and periodic full recovery retained; unchanged intermediate snapshots suppressed");
            PacketProbe.packets.clear();player.setHealth(player.getHealth()-1);fight.sync();fight.sync();
            check(PacketProbe.packets.stream().filter(p->p instanceof CombatStatePayload s && s.active()).count()==1,"HP change sends immediately exactly once");
            PacketProbe.packets.clear();fight.endTurn();
            check(PacketProbe.packets.stream().anyMatch(p->p instanceof CombatStatePayload s && s.active()),"turn identity change sends full state");
        } finally {PacketProbe.clear();if(fight!=null) fight.end();CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void srdDeliveryCoalescingStillChecksPermissions(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            SrdLiveGameTests.capability(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),true);
            var a=f.actor(2,2,true);var b=f.actor(3,2,true);var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),a,b);
            PacketProbe.clear();PacketProbe.enabled=true;
            SrdNetworking.watch(player,e);SrdNetworking.synchronize(player.getServer());SrdNetworking.synchronize(player.getServer());
            check(PacketProbe.packets.stream().filter(p->p instanceof SrdStatePayload).count()==1,"watch snapshot is not sent again on idle tick");
            e.endTurn(f.dm);SrdNetworking.synchronize(player.getServer());
            check(PacketProbe.packets.stream().filter(p->p instanceof SrdStatePayload).count()==2,"changed revision sends full state once");
            e.endTurn(f.dm);
            SrdNetworking.send(player,SrdNetworking.state(e,CharacterService.principal(player),"REJECTED","Rejected action",null));
            SrdNetworking.synchronize(player.getServer());
            check(PacketProbe.packets.getLast() instanceof SrdStatePayload recovered && recovered.code().equals("STATE"),
                    "message-only rejection cannot suppress the state update that the client still needs");
            f.repo.revokeDm(f.dm,f.campaign,2,player.getUuid());SrdNetworking.synchronize(player.getServer());
            check(PacketProbe.packets.getLast() instanceof SrdStatePayload last && last.code().equals("ENDED") && last.json().equals("{}"),
                    "revoked observer permission clears client even when encounter revision unchanged");
        } finally {PacketProbe.clear();CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @FunctionalInterface interface Operation {long run() throws Exception;}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void coDmPlayerLosesPrivateFieldsWithoutEncounterRevisionChange(TestContext ctx) throws Exception {
        var p=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            SrdLiveGameTests.capability(p);f.repo.invite(f.dm,f.campaign,1,p.getUuid(),true);
            UUID character=f.repo.submit(CharacterService.principal(p),f.campaign,dev.tacticalcombat.rules.RulesProfiles.SRD,SrdAuthoring.template().toString());
            f.repo.approve(f.dm,character,1);f.repo.bind(f.dm,character,2,p.getUuid(),p.getUuid());CharacterService.select(p,f.campaign);
            p.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var npc=f.actor(3,2,true);var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),p,npc);
            PacketProbe.clear();PacketProbe.enabled=true;SrdNetworking.watch(p,e);long revision=e.revision();
            f.repo.revokeDm(f.dm,f.campaign,2,p.getUuid());SrdNetworking.synchronize(p.getServer());
            check(e.revision()==revision && PacketProbe.packets.size()==2,"campaign role change sends new projection without encounter mutation");
            var last=(SrdStatePayload)PacketProbe.packets.getLast();var state=com.google.gson.JsonParser.parseString(last.json()).getAsJsonObject();
            for(var row:state.getAsJsonArray("actors")) {
                var actor=row.getAsJsonObject();check(actor.has("hp")==actor.get("id").getAsString().equals(p.getUuid().toString()),"only own HP remains after DM revocation");
            }
        } finally {PacketProbe.clear();CharacterService.forget(p.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(p);}
        ctx.complete();
    }
    static void measure(String name,int actors,Operation setup,Operation operation) throws Exception {
        int samples=Boolean.getBoolean("cubetable.srd.benchmark")?20:1;
        long[] wall=new long[samples],thread=new long[samples],allocated=new long[samples];long bytes=0;
        for(int i=-3;i<samples;i++) {
            setup.run();long a=allocation(),c=cpu(),at=System.nanoTime();long output=operation.run();
            long elapsed=System.nanoTime()-at,usedCpu=cpu()-c,usedBytes=allocation()-a;
            if(i>=0) {wall[i]=elapsed;thread[i]=usedCpu;allocated[i]=usedBytes;bytes=output;}
        }
        long mean=Arrays.stream(wall).sum()/samples;
        Arrays.sort(wall);Arrays.sort(thread);Arrays.sort(allocated);
        dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD_SYSTEM operation={} actors={} n={} mean_us={} median_us={} p95_us={} max_us={} cpu_median_us={} allocated_median={} payload_bytes={}",
                name,actors,samples,mean/1000,wall[samples/2]/1000,wall[(int)Math.ceil(samples*.95)-1]/1000,wall[samples-1]/1000,
                thread[samples/2]/1000,allocated[samples/2],bytes);
    }
    @GameTest(templateName=EMPTY_STRUCTURE,tickLimit=1200)
    public void combatStorageAndSerializationScaling(TestContext ctx) throws Exception {
        for(int count:new int[]{2,4,8,16}) try(var f=new Fixture(ctx)) {
            for(int i=0;i<count;i++) f.actor(2+i%8,2+i/8,true);
            Integer[] faces=new Integer[count+64];Arrays.fill(faces,1);for(int i=0;i<count;i++) faces[i]=20-i;
            var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(faces),f.entities.toArray(net.minecraft.entity.LivingEntity[]::new));
            measure("idle_tick",count,()->0,()->{check(!e.tick(),"active idle encounter survives");return 0;});
            measure("snapshot_json",count,()->0,()->SrdNetworking.state(e,f.dm,"STATE",e.reason(),null).json().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            measure("attack_preview",count,()->0,()->{e.preview(f.dm,f.entities.get(f.entities.indexOf(e.current())^1).getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);return 0;});
            measure("turn_transition",count,()->0,()->{e.endTurn(f.dm);return 0;});
            final UUID[] intent={null};
            measure("confirmed_miss_durable",count,()->{
                e.endTurn(f.dm);intent[0]=e.preview(f.dm,f.entities.get(f.entities.indexOf(e.current())^1).getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false).id();return 0;
            },()->{check(e.confirm(f.dm,intent[0]).state==SrdEncounter.TransactionState.COMMITTED,"miss durably commits exactly once");return 0;});
            var actor=f.entities.getFirst();UUID character=CharacterService.srdStats(actor,f.campaign).character();
            measure("character_refresh",count,()->0,()->new CharacterSyncService(f.repo).snapshot(f.dm,character).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            measure("authorized_srd_read",count,()->0,()->{f.repo.srdSnapshot(actor.getUuid(),f.campaign);return 0;});
            e.end();
            var encounters=new ArrayList<SrdEncounter>();
            for(int i=0;i<count;i+=2) encounters.add(SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),f.entities.get(i),f.entities.get(i+1)));
            measure("multiple_encounter_ticks",count,()->0,()->{for(var fight:encounters) check(!fight.tick(),"independent encounter survives");return 0;});
            for(var fight:encounters) fight.end();
            measure("hp_atomic_write",count,()->0,()->{
                var before=f.repo.srdSnapshot(actor.getUuid(),f.campaign);
                var saved=f.repo.commitSrdHealth(before,new SrdHealth(before.stats().hp()==10?9:10,10,0));
                check(saved.runtimeRevision()==before.runtimeRevision()+1,"durable runtime revision advances once");return 0;
            });
            measure("store_reload",count,()->0,()->{var reloaded=new CharacterRepository(f.dir.resolve("state.json"));check(reloaded.state().equals(f.repo.state()),"normal restart preserves all identities/resources");return java.nio.file.Files.size(f.dir.resolve("state.json"));});
        }
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try {
            SrdLiveGameTests.capability(player);
            measure("invalid_request_flood",0,()->0,()->{
                var reply=SrdNetworking.handle(player,new SrdRequestPayload(UUID.randomUUID(),1,UUID.randomUUID(),"grid","{}"));
                check(reply.code().equals("REJECTED"),"invalid flood never creates jobs or discloses state");return reply.json().length();
            });
        } finally {CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
}
