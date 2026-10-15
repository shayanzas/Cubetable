package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public class EncounterLauncherGameTests implements FabricGameTest {
    private static JsonObject args(String key,Object value){var a=new JsonObject();a.addProperty(key,value.toString());return a;}
    private static JsonObject call(ServerPlayerEntity p,UiApiService.Operation op){return EncounterLauncher.handle(p,op,new JsonObject());}
    private static ServerPlayerEntity manager(Fixture f)throws Exception{
        var p=f.ctx.createMockCreativeServerPlayerInWorld();SrdLiveGameTests.capability(p);f.repo.invite(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid(),true);CharacterService.select(p,f.campaign);p.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);return p;
    }
    private static UUID bindPlayer(Fixture f,ServerPlayerEntity p)throws Exception{var who=CharacterService.principal(p);UUID id=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,p.getUuid(),p.getUuid());return id;}
    private static JsonObject selection(ServerPlayerEntity p,UUID... actors){var discovered=call(p,ENCOUNTER_DISCOVER);var a=args("discovery",discovered.get("discovery").getAsString());var list=new JsonArray();for(UUID id:actors)list.add(id.toString());a.add("actors",list);return a;}
    private static void cleanup(ServerPlayerEntity p){EncounterLauncher.forget(p.getUuid());CharacterService.forget(p.getUuid());p.getServer().getPlayerManager().remove(p);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void graphicalPlayerAndZombieStartWatchAndEnd(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);UUID character=bindPlayer(f,p);var mob=f.actor(4,1,true);mob.setAiDisabled(false);var player=p;
            var discovered=call(p,ENCOUNTER_DISCOVER);check(discovered.get("radius").getAsInt()==32,"bounded discovery");check(discovered.getAsJsonArray("actors").asList().stream().anyMatch(v->v.getAsJsonObject().get("actor").getAsString().equals(mob.getUuidAsString())&&v.getAsJsonObject().get("eligible").getAsBoolean()),"bound zombie shown eligible");
            var selection=selection(p,p.getUuid(),mob.getUuid());var started=EncounterLauncher.handle(p,ENCOUNTER_START,selection);var e=SrdNetworking.find(UUID.fromString(started.get("encounter").getAsString()));f.encounters.add(e);
            check(e!=null&&e.actors().size()==2&&(e.state()==SrdEncounter.State.ACTIVE||e.state()==SrdEncounter.State.PENDING),"started existing engine, ties are successful pending starts");check(CombatManager.encounter(p)==e&&mob.isAiDisabled(),"actors claimed and AI paused");
            rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,selection),"double-click cannot create duplicate");check(call(p,ENCOUNTER_LIST).getAsJsonArray("encounters").size()==1,"one encounter");
            var watching=SrdNetworking.class.getDeclaredField("WATCHING");watching.setAccessible(true);check(((Map<?,?>)watching.get(null)).get(p.getUuid()).equals(e.id()),"participating DM subscribed");
            var before=f.repo.srdSnapshot(p.getUuid(),f.campaign);EncounterLauncher.handle(p,ENCOUNTER_END,args("encounter",e.id()));check(!CombatManager.isInCombat(p)&&!CombatManager.isInCombat(mob)&&!mob.isAiDisabled(),"ending releases claims and restores AI");check(f.repo.srdSnapshot(p.getUuid(),f.campaign).equals(before),"ending retains persistent resources");check(call(p,QUICK_SHEET).get("id").getAsString().equals(character.toString()),"quick sheet reads bound character");
        }finally{if(p!=null)cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void staleDuplicateInvalidAndCrossCampaignAdmissionIsAtomic(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);var player=p;var a=f.actor(3,1,true);var b=f.actor(5,1,true);var unbound=f.actor(7,1,false);
            rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,selection(player,a.getUuid(),a.getUuid())),"duplicate actor rejected");rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,selection(player,a.getUuid(),unbound.getUuid())),"unbound actor rejected");
            var stale=selection(p,a.getUuid(),b.getUuid());var old=f.repo.srdSnapshot(b.getUuid(),f.campaign);f.repo.commitSrdHealth(old,new SrdHealth(9,old.stats().maximumHp(),0));rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,stale),"changed character requires new selection");
            var moved=selection(p,a.getUuid(),b.getUuid());b.setPosition(b.getX()+.1,b.getY(),b.getZ());rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,moved),"unsupported footprint rejected without teleport");check(Math.abs(b.getX()-(f.origin.getX()+5.6))<.00001,"no silent repositioning");check(!CombatManager.isInCombat(a)&&!CombatManager.isInCombat(b),"failure claims nothing");
            UUID other=f.repo.createCampaign(f.dm,"Other");UUID foreign=f.repo.submit(f.dm,other,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,foreign,1);f.repo.bind(f.dm,foreign,2,unbound.getUuid(),null);rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,selection(player,a.getUuid(),unbound.getUuid())),"cross campaign rejected");
            var missing=selection(p,a.getUuid(),b.getUuid());b.discard();rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,missing),"unloaded stale selection rejected");
        }finally{if(p!=null)cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void quickSheetUsesBindingAndManagerInspectionRevokes(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);var player=p;UUID bound=bindPlayer(f,p);var npc=f.actor(4,1,true);UUID npcId=CharacterService.bound(npc).id();
            CharacterService.remember(p,f.campaign,npcId);check(call(p,QUICK_SHEET).get("id").getAsString().equals(bound.toString()),"local selected NPC does not replace actual bound sheet");
            check(EncounterLauncher.handle(p,QUICK_SHEET,args("character",npcId)).get("id").getAsString().equals(npcId.toString()),"manager inspects NPC");var page=new JsonObject();page.addProperty("page",0);check(EncounterLauncher.handle(p,DM_QUICK,page).getAsJsonArray("characters").size()==2,"DM summary roster includes NPC");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid());rejects(()->EncounterLauncher.handle(player,DM_QUICK,page),"revoked DM cannot read roster");rejects(()->EncounterLauncher.handle(player,QUICK_SHEET,args("character",npcId)),"revoked DM cannot inspect NPC");rejects(()->call(player,ENCOUNTER_DISCOVER),"ordinary player cannot discover private bindings");
            check(call(p,QUICK_SHEET).get("id").getAsString().equals(bound.toString()),"ordinary player retains own bound quick sheet");f.repo.unbind(f.dm,p.getUuid());check(call(p,QUICK_SHEET).has("empty"),"unbound quick sheet is empty");
        }finally{if(p!=null)cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void beginFailureRestoresAiAndManagerOnlyEnd(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);var player=p;bindPlayer(f,p);var mob=f.actor(4,1,true);mob.setAiDisabled(false);
            p.getAbilities().flying=true;var attempt=selection(p,p.getUuid(),mob.getUuid());rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,attempt),"begin failure propagates");check(!CombatManager.isInCombat(p)&&!CombatManager.isInCombat(mob)&&!mob.isAiDisabled(),"post-claim failure restores AI and releases every actor");p.getAbilities().flying=false;
            var response=EncounterLauncher.handle(p,ENCOUNTER_START,selection(p,p.getUuid(),mob.getUuid()));var e=SrdNetworking.find(UUID.fromString(response.get("encounter").getAsString()));f.encounters.add(e);
            rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,selection(player,player.getUuid(),mob.getUuid())),"already-in-combat actors cannot be claimed again");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid());rejects(()->EncounterLauncher.handle(player,ENCOUNTER_END,args("encounter",e.id())),"participant is not entitled to end encounter");check(CombatManager.encounter(p)==e,"unauthorized end leaves encounter intact");
        }finally{if(p!=null)cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void crossWorldAndUnapprovedParticipantsCannotEnter(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;net.minecraft.entity.LivingEntity foreign=null;try(var f=new Fixture(ctx)){p=manager(f);var player=p;var a=f.actor(3,1,true);var b=f.actor(5,1,false);
            UUID unapproved=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());rejects(()->{try{f.repo.bind(f.dm,unapproved,1,b.getUuid(),null);}catch(java.io.IOException e){throw new RuntimeException(e);}},"unapproved character cannot obtain a binding");
            rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,selection(player,a.getUuid(),b.getUuid())),"unapproved/unbound actor rejected");
            var world=ctx.getWorld().getServer().getWorld(net.minecraft.world.World.NETHER);foreign=net.minecraft.entity.EntityType.ZOMBIE.create(world);foreign.setPosition(p.getX(),p.getY(),p.getZ());world.spawnEntity(foreign);UUID foreignId=foreign.getUuid();
            UUID id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,foreignId,null);
            rejects(()->EncounterLauncher.handle(player,ENCOUNTER_START,selection(player,a.getUuid(),foreignId)),"same campaign different dimension rejected");check(!CombatManager.isInCombat(a)&&!CombatManager.isInCombat(foreign),"cross-world failure leaves both actors unclaimed");
        }finally{if(foreign!=null)foreign.discard();if(p!=null)cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void openPrivatePanelReceivesOpaqueRevocation(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=manager(f);
            var addon=net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(p.networkHandler);var field=net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels");field.setAccessible(true);@SuppressWarnings("unchecked") var channels=(Set<net.minecraft.util.Identifier>)field.get(addon);channels.add(dev.tacticalcombat.net.RulesChangedPayload.ID.id());
            var page=new JsonObject();page.addProperty("page",0);var data=EncounterLauncher.handle(p,DM_QUICK,page);UUID token=UUID.fromString(data.get("panelToken").getAsString());PacketProbe.clear();PacketProbe.enabled=true;
            EncounterLauncher.synchronize(p.getServer());check(PacketProbe.packets.isEmpty(),"idle panel does not repeatedly push private snapshots");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid());EncounterLauncher.synchronize(p.getServer());check(PacketProbe.packets.getLast() instanceof dev.tacticalcombat.net.RulesChangedPayload notice&&notice.token().equals(token)&&notice.revoked(),"revocation sends only matching opaque token");
            PacketProbe.clear();PacketProbe.enabled=true;EncounterLauncher.synchronize(p.getServer());check(PacketProbe.packets.isEmpty(),"revoked panel subscription removed");
        }finally{PacketProbe.clear();if(p!=null)cleanup(p);}ctx.complete();
    }
}
