package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.checks.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.net.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;

import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class CharacterCheckGameTests implements FabricGameTest {
    private static JsonObject args(Object... pairs){var out=new JsonObject();for(int i=0;i<pairs.length;i+=2){String key=pairs[i].toString();Object v=pairs[i+1];if(v==null)out.add(key,JsonNull.INSTANCE);else if(v instanceof JsonElement j)out.add(key,j.deepCopy());else if(v instanceof Number n)out.addProperty(key,n);else if(v instanceof Boolean b)out.addProperty(key,b);else out.addProperty(key,v.toString());}return out;}
    private static ServerPlayerEntity player(Fixture f,boolean dm)throws Exception{var p=f.ctx.createMockCreativeServerPlayerInWorld();SrdLiveGameTests.capability(p);f.repo.invite(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid(),dm);CharacterService.select(p,f.campaign);return p;}
    private static UUID bind(Fixture f,ServerPlayerEntity p)throws Exception{UUID id=f.repo.submit(CharacterService.principal(p),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,p.getUuid(),p.getUuid());return id;}
    private static void cleanup(ServerPlayerEntity... players){CheckGateway.clear();for(var p:players)if(p!=null){CharacterService.forget(p.getUuid());UiNetworking.forget(p.getUuid());p.getServer().getPlayerManager().remove(p);}PacketProbe.clear();}
    private static JsonObject spec(String visibility,UUID recipient,Integer dc){var ids=new JsonArray();if(recipient!=null)ids.add(recipient.toString());return args("category","ABILITY","key","str","mode","ADVANTAGE","purpose","Door check","visibility",visibility,"recipients",ids,"dc",dc);}
    private static JsonObject call(ServerPlayerEntity p,UiApiService.Operation op,JsonObject a){return CheckGateway.handle(p,op,a);}
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void authenticatedChecksRetryAndRejectForgedFields(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null,other=null;try(var f=new Fixture(ctx)){p=player(f,false);other=player(f,false);UUID id=bind(f,p);var player=p;var outsider=other;
            var wire=UiNetworking.handle(p,new UiRequestPayload(1,"CHECK_OPTIONS","{}"));check(wire.code().equals("OK"),"authenticated UI transport dispatches character checks");var options=JsonParser.parseString(wire.json()).getAsJsonObject();check(options.get("character").getAsString().equals(id.toString())&&options.getAsJsonArray("checks").size()==30,"own bound checks and explicit unsupported historical skills");
            var request=args("character",id,"revision",2,"spec",spec("RECIPIENTS",p.getUuid(),null),"adjudicated",false);var before=f.repo.state();
            var forged=request.deepCopy();forged.getAsJsonObject("spec").addProperty("modifier",99);rejects(()->call(player,CHECK_PREPARE,forged),"client modifier forbidden by strict typed envelope");rejects(()->call(outsider,CHECK_PREPARE,request),"unrelated player denied");
            var prepared=call(p,CHECK_PREPARE,request);var execute=args("operation",prepared.get("operation").getAsString());PacketProbe.enabled=true;var result=call(p,CHECK_EXECUTE,execute);check(result.equals(call(p,CHECK_EXECUTE,execute)),"network retry fixed result");var roll=result.getAsJsonObject("result");check(roll.getAsJsonArray("faces").size()==2&&roll.get("total").getAsInt()>=1&&roll.get("total").getAsInt()<=20,"server advantage bounded");check(f.repo.state()==before&&f.repo.state().receipts().isEmpty(),"no campaign resource or receipt writes");
            var altered=execute.deepCopy();altered.addProperty("total",100);rejects(()->call(player,CHECK_EXECUTE,altered),"same ID with altered parameters forbidden");check(PacketProbe.packets.stream().noneMatch(x->x instanceof DiceRollPayload),"authoritative check never leaks through manual broadcast payload");
            CharacterService.remember(p,f.campaign,UUID.randomUUID());check(call(p,CHECK_OPTIONS,args()).get("character").getAsString().equals(id.toString()),"local selection never substitutes bound authority");
        }finally{cleanup(p,other);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dmHiddenRequestsPrivacyAndRevocation(TestContext ctx)throws Exception{
        ServerPlayerEntity dm=null,p=null,other=null;try(var f=new Fixture(ctx)){dm=player(f,true);p=player(f,false);other=player(f,false);UUID id=bind(f,p);bind(f,other);var player=p;
            var ids=new JsonArray();ids.add(id.toString());var request=args("characters",ids,"spec",spec("DM_ONLY",null,13));var created=call(dm,CHECK_REQUEST,request);check(created.equals(call(dm,CHECK_REQUEST,request)),"request retry stable IDs");String operation=created.getAsJsonArray("operations").get(0).getAsString();var inbox=call(p,CHECK_INBOX,args("page",0));check(!inbox.toString().contains("\"dc\"")&&!inbox.toString().contains("\"success\""),"pending request packet contains no hidden DC");
            PacketProbe.enabled=true;var ack=call(p,CHECK_EXECUTE,args("operation",operation));check(!ack.has("result")||ack.get("result").isJsonNull(),"executor does not receive hidden result");check(!call(other,CHECK_INBOX,args("page",0)).toString().contains(operation),"unrelated player gets no hidden request");check(UiEvents.SESSION.query(f.repo,CharacterService.principal(other),f.campaign,0,UiEvents.Kind.CHARACTER_CHECK).events().isEmpty(),"no hidden event disclosure");check(call(dm,CHECK_INBOX,args("page",0)).toString().contains("\"dc\":13"),"authorized DM sees full result");check(PacketProbe.packets.stream().noneMatch(x->x instanceof DiceRollPayload),"private result not manual/HUD broadcast");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),dm.getUuid());rejects(()->call(player,CHECK_EXECUTE,args("operation",operation)),"revoked requesting DM blocks retry");check(call(p,CHECK_INBOX,args("page",0)).getAsJsonArray("rolls").isEmpty(),"revoked request removed from current inbox");
        }finally{cleanup(dm,p,other);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void checkPanelRevokesOnCampaignSwitch(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=player(f,false);bind(f,p);
            var addon=net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(p.networkHandler);var field=net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels");field.setAccessible(true);@SuppressWarnings("unchecked") var channels=(Set<net.minecraft.util.Identifier>)field.get(addon);channels.add(RulesChangedPayload.ID.id());
            UUID token=UUID.fromString(call(p,CHECK_OPTIONS,args()).get("panelToken").getAsString());UUID next=f.repo.createCampaign(f.dm,"Other check scope");f.repo.invite(f.dm,next,1,p.getUuid(),false);CharacterService.select(p,next);PacketProbe.enabled=true;EncounterLauncher.synchronize(p.getServer());check(PacketProbe.packets.stream().anyMatch(x->x instanceof RulesChangedPayload notice&&notice.token().equals(token)&&notice.revoked()),"campaign switch invalidates private check panel with opaque notice");
        }finally{cleanup(p);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void staleRuntimeAndRestartDoNotReroll(TestContext ctx)throws Exception{
        ServerPlayerEntity p=null;try(var f=new Fixture(ctx)){p=player(f,false);UUID id=bind(f,p);var player=p;
            var prepared=call(p,CHECK_PREPARE,args("character",id,"revision",2,"spec",spec("RECIPIENTS",p.getUuid(),null),"adjudicated",false));var execute=args("operation",prepared.get("operation").getAsString());var snapshot=f.repo.srdSnapshot(p.getUuid(),f.campaign);f.repo.commitSrdHealth(snapshot,new dev.tacticalcombat.rules.srd.SrdHealth(9,10,0));rejects(()->call(player,CHECK_EXECUTE,execute),"stale runtime revision rejected before RNG");
            CheckGateway.clear();rejects(()->call(player,CHECK_EXECUTE,execute),"process session reset rejects old ID");check(f.repo.srdSnapshot(p.getUuid(),f.campaign).stats().hp()==9,"J2 persistent health untouched by failed rolls");
        }finally{cleanup(p);}ctx.complete();
    }
}
