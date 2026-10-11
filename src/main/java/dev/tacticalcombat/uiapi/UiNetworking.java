package dev.tacticalcombat.uiapi;
import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.combat.*;
import dev.tacticalcombat.net.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.WorldSavePath;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

public final class UiNetworking {
    private static final CharacterSessions SESSIONS=new CharacterSessions();
    private static final Map<UUID,UUID> SELECTED=new HashMap<>();
    private static LibraryRepository library;
    private static UiWorkflowStore workflows;
    private UiNetworking() {}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(UiRequestPayload.ID,UiRequestPayload.CODEC);PayloadTypeRegistry.playS2C().register(UiReplyPayload.ID,UiReplyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UiRequestPayload.ID,(request,context)->{
            if(ServerPlayNetworking.canSend(context.player(),UiReplyPayload.ID)) ServerPlayNetworking.send(context.player(),handle(context.player(),request));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try {library=new LibraryRepository(server.getSavePath(WorldSavePath.ROOT).resolve("tacticalcombat/library.json"));}
            catch(Exception e) {library=null;dev.tacticalcombat.TacticalCombatMod.LOGGER.error("UI library unavailable; existing combat storage preserved",e);}});
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try {workflows=new UiWorkflowStore(server.getSavePath(WorldSavePath.ROOT).resolve("tacticalcombat/ui-workflows.json"));}
            catch(Exception e) {workflows=null;dev.tacticalcombat.TacticalCombatMod.LOGGER.error("UI workflows unavailable",e);}});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{SESSIONS.forget(handler.player.getUuid());SELECTED.remove(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{library=null;workflows=null;UiEvents.SESSION.clear();SESSIONS.clear();SELECTED.clear();});
    }
    public static UiReplyPayload handle(ServerPlayerEntity player,UiRequestPayload request) {
        require(player.getServer().isOnThread(),"UI operations require server thread");
        String admission=SESSIONS.admit(player.getUuid(),request.sequence(),player.getServer().getOverworld().getTime());
        if(!admission.equals("OK")) return new UiReplyPayload(request.sequence(),admission,"Request repeated or too frequent; refresh before retrying","{}");
        try {
            require(library!=null,"Library service unavailable");require(request.json().length()<=UiRequestPayload.LIMIT,"Request too large");CharacterRepository.checkDepth(request.json(),24);
            var op=Operation.valueOf(request.operation());var args=JsonParser.parseString(request.json()).getAsJsonObject();
            if(op==Operation.CHARACTER_GET) CharacterService.captureForViewer(player,id(args,"character"));
            var response=new UiApiService(CharacterService.repository(),library,(operation,a)->world(player,operation,a)).handle(CharacterService.principal(player),op,args);
            return new UiReplyPayload(request.sequence(),response.code(),response.message(),response.data().toString());
        } catch(java.io.IOException e) {return new UiReplyPayload(request.sequence(),"STORAGE_ERROR","Health capture failed; refresh before retrying","{}");}
        catch(RuntimeException e) {return new UiReplyPayload(request.sequence(),"REJECTED","Invalid UI request or unavailable service","{}");}
    }
    private static JsonObject world(ServerPlayerEntity p,Operation op,JsonObject a) throws java.io.IOException {
        var repo=CharacterService.repository();var who=CharacterService.principal(p);var out=new JsonObject();
        switch(op) {
            case ACTIVE -> {
                fields(a,"");try {UUID campaign=CharacterService.selected(p);if(repo.member(who,campaign)) out.addProperty("campaign",campaign.toString());} catch(IllegalArgumentException none) { }
                UUID selected=SELECTED.get(p.getUuid());if(selected!=null) try {repo.visible(who,selected);out.addProperty("character",selected.toString());} catch(IllegalArgumentException revoked) {SELECTED.remove(p.getUuid());}
                var bound=CharacterService.bound(p);if(bound!=null) out.addProperty("boundCharacter",bound.id().toString());out.addProperty("inCombat",CombatManager.isInCombat(p));
            }
            case EVENTS -> {fields(a,"campaign cursor kind");UUID campaign=id(a,"campaign");var filter=a.get("kind").isJsonNull()?null:UiEvents.Kind.valueOf(string(a,"kind"));out=SheetSections.JSON.toJsonTree(UiEvents.SESSION.query(repo,who,campaign,number(a,"cursor"),filter)).getAsJsonObject();}
            case PARTY -> {fields(a,"campaign page");require(workflows!=null,"Workflow service unavailable");UUID campaign=id(a,"campaign");out=UiPresentation.party(p,campaign,integer(a,"page"),workflows.policy(campaign));}
            case INITIATIVE -> {fields(a,"encounter");out=UiPresentation.initiative(p,id(a,"encounter"));}
            case PRESENTATION_POLICY -> {fields(a,"campaign revision hp radius");require(workflows!=null,"Workflow service unavailable");UUID campaign=id(a,"campaign");workflows.policy(repo,who,new UiWorkflowStore.Policy(campaign,revision(a)+1,UiWorkflowStore.HpVisibility.valueOf(string(a,"hp")),integer(a,"radius")));out=SheetSections.JSON.toJsonTree(workflows.policy(campaign)).getAsJsonObject();}
            case REST_REQUEST -> {
                fields(a,"campaign request kind");require(workflows!=null,"Workflow service unavailable");UUID campaign=id(a,"campaign");require(repo.member(who,campaign),"Campaign membership permission required");
                var party=UiPresentation.restParty(p,campaign);String dimension=p.getServerWorld().getRegistryKey().getValue().toString();int radius=workflows.policy(campaign).radius();
                UiPresentation.eligible(p,campaign,party,dimension,p.getBlockX(),p.getBlockY(),p.getBlockZ(),radius);
                var rest=workflows.request(repo,who,id(a,"request"),campaign,UiWorkflowStore.RestKind.valueOf(string(a,"kind")),party,dimension,p.getBlockX(),p.getBlockY(),p.getBlockZ(),System.currentTimeMillis());
                out=SheetSections.JSON.toJsonTree(rest).getAsJsonObject();
                UiEvents.SESSION.publish(rest.id(),campaign,null,0,UiEvents.Kind.REST,List.of(p.getUuid()),"Rest requested; no recovery has been applied",null,UiEvents.Visibility.RECIPIENTS_AND_DM,Set.copyOf(party));
            }
            case REST_REVIEW -> {
                fields(a,"request revision approve");require(workflows!=null,"Workflow service unavailable");var rest=workflows.get(repo,who,id(a,"request"));require(repo.manages(who,rest.campaign()),"Only campaign DMs review rest requests");
                if(bool(a,"approve") && System.currentTimeMillis()<rest.expires()) UiPresentation.eligible(p,rest.campaign(),rest.participants(),rest.dimension(),rest.x(),rest.y(),rest.z(),rest.radius());
                var saved=workflows.review(repo,who,rest.id(),revision(a),bool(a,"approve"),System.currentTimeMillis());out=SheetSections.JSON.toJsonTree(saved).getAsJsonObject();
                UiEvents.SESSION.publish(UUID.randomUUID(),rest.campaign(),null,0,UiEvents.Kind.REST,List.of(p.getUuid()),"Rest review: "+saved.state()+"; recovery remains manual",null,UiEvents.Visibility.RECIPIENTS_AND_DM,Set.copyOf(rest.participants()));
            }
            case REST_LIST -> {fields(a,"campaign page");require(workflows!=null,"Workflow service unavailable");out.add("requests",SheetSections.JSON.toJsonTree(workflows.list(repo,who,id(a,"campaign"),integer(a,"page"))));out.addProperty("serverTime",System.currentTimeMillis());out.addProperty("recoveryAutomated",false);}
            case CAMPAIGN_SELECT -> {fields(a,"campaign");CharacterService.select(p,id(a,"campaign"));SELECTED.remove(p.getUuid());out.addProperty("campaign",id(a,"campaign").toString());}
            case CHARACTER_SELECT -> {
                fields(a,"character revision");var c=repo.visible(who,id(a,"character"));require(c.revision()==revision(a),"Stale character revision");require(!CombatManager.isInCombat(p),"Character selection locked during combat");
                require(Objects.equals(CharacterService.selected(p),c.campaign()),"Select the character's campaign first");require(repo.controller(c.id()).equals(p.getUuid()) || repo.manages(who,c.campaign()),"Character control permission denied");
                SELECTED.put(p.getUuid(),c.id());out.addProperty("character",c.id().toString());out.addProperty("bindingChanged",false);
            }
            case BIND -> {
                fields(a,"character revision entity");UUID entity=id(a,"entity");LivingEntity target=null;
                for(var world:p.getServer().getWorlds()) if(world.getEntity(entity) instanceof LivingEntity living) {target=living;break;}
                require(target!=null && target.isAlive() && !target.isRemoved(),"Eligible loaded living entity required");
                CharacterService.bind(p,id(a,"character"),revision(a),target);out.addProperty("bound",entity.toString());
            }
            case UNBIND -> {fields(a,"character revision");CharacterService.release(p,id(a,"character"),revision(a));out.addProperty("unbound",true);}
            case ELIGIBLE_PLAYERS -> {
                fields(a,"campaign");UUID campaign=id(a,"campaign");require(repo.manages(who,campaign),"Only campaign DMs inspect eligible players");var rows=new JsonArray();
                for(var player:p.getServer().getPlayerManager().getPlayerList()) if(rows.size()<256) {var row=new JsonObject();row.addProperty("id",player.getUuidAsString());row.addProperty("name",player.getName().getString());row.addProperty("member",repo.member(new CharacterRepository.Principal(player.getUuid(),false),campaign));row.addProperty("inCombat",CombatManager.isInCombat(player));rows.add(row);}out.add("players",rows);
            }
            default -> throw new IllegalArgumentException("Operation unavailable in this checkpoint");
        }
        return out;
    }
}
