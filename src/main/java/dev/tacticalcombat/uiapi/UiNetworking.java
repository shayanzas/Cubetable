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
    private static CharacterWatchService watches;
    private static final dev.tacticalcombat.core.RateLimit WATCH_LIMIT=new dev.tacticalcombat.core.RateLimit(8,20);
    private static CharacterWatchService watches(){var repo=CharacterService.repository();require(library!=null,"Library service unavailable");if(watches==null||!watches.matches(repo,library))watches=new CharacterWatchService(repo,library);return watches;}

    private UiNetworking() {}
    public static void forget(UUID player){SESSIONS.forget(player);EncounterLauncher.forget(player);SELECTED.remove(player);WATCH_LIMIT.forget(player);if(watches!=null)watches.forget(player);}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(RulesWatchPayload.ID,RulesWatchPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RulesChangedPayload.ID,RulesChangedPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RulesWatchPayload.ID,(request,context)->{
            var player=context.player();if(!player.getServer().isOnThread()||!ServerPlayNetworking.canSend(player,RulesChangedPayload.ID))return;
            if(request.close()){EncounterLauncher.close(player.getUuid(),request.token());if(watches!=null)watches.close(player.getUuid(),request.token());return;}
            if(!WATCH_LIMIT.allow(player.getUuid(),player.getServer().getOverworld().getTime()))return;
            try{watches().watch(CharacterService.principal(player),request.token(),request.library(),request.character(),request.observed());}
            catch(RuntimeException unavailable){ServerPlayNetworking.send(player,new RulesChangedPayload(request.token(),true));}
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server->{
            if(library!=null)EncounterLauncher.synchronize(server);
            if(watches==null)return;
            for(var notice:watches.drain(id->{var p=server.getPlayerManager().getPlayer(id);return p==null?null:CharacterService.principal(p);})){var p=server.getPlayerManager().getPlayer(notice.viewer());if(p!=null&&ServerPlayNetworking.canSend(p,RulesChangedPayload.ID))ServerPlayNetworking.send(p,new RulesChangedPayload(notice.token(),notice.revoked()));}
        });
        PayloadTypeRegistry.playC2S().register(UiRequestPayload.ID,UiRequestPayload.CODEC);PayloadTypeRegistry.playS2C().register(UiReplyPayload.ID,UiReplyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UiRequestPayload.ID,(request,context)->{
            if(ServerPlayNetworking.canSend(context.player(),UiReplyPayload.ID)) ServerPlayNetworking.send(context.player(),handle(context.player(),request));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try {library=new LibraryRepository(server.getSavePath(WorldSavePath.ROOT).resolve("tacticalcombat/library.json"));if(library.recoveredFromBackup()) dev.tacticalcombat.TacticalCombatMod.LOGGER.warn("UI library recovered from validated backup");}
            catch(Exception e) {library=null;dev.tacticalcombat.TacticalCombatMod.LOGGER.error("UI library unavailable; existing combat storage preserved",e);}});
        ServerLifecycleEvents.SERVER_STARTED.register(server->{try {workflows=new UiWorkflowStore(server.getSavePath(WorldSavePath.ROOT).resolve("tacticalcombat/ui-workflows.json"));if(workflows.recoveredFromBackup()) dev.tacticalcombat.TacticalCombatMod.LOGGER.warn("UI workflows recovered from validated backup");}
            catch(Exception e) {workflows=null;dev.tacticalcombat.TacticalCombatMod.LOGGER.error("UI workflows unavailable",e);}});
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{forget(handler.player.getUuid());});
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{EncounterLauncher.clear();dev.tacticalcombat.checks.CheckGateway.clear();watches=null;WATCH_LIMIT.clear();library=null;workflows=null;UiEvents.SESSION.clear();SESSIONS.clear();SELECTED.clear();});
    }
    public static UiReplyPayload handle(ServerPlayerEntity player,UiRequestPayload request) {
        require(player.getServer().isOnThread(),"UI operations require server thread");
        String admission=SESSIONS.admit(player.getUuid(),request.sequence(),player.getServer().getOverworld().getTime());
        if(!admission.equals("OK")) return new UiReplyPayload(request.sequence(),admission,"Request repeated or too frequent; refresh before retrying","{}");
        try {
            require(library!=null,"Library service unavailable");require(request.json().length()<=UiRequestPayload.LIMIT,"Request too large");CharacterRepository.checkDepth(request.json(),24);
            var op=Operation.valueOf(request.operation());var args=JsonParser.parseString(request.json()).getAsJsonObject();
            if(op==Operation.CHARACTER_GET) {
                UUID character=id(args,"character");
                try {CharacterService.repository().visible(CharacterService.principal(player),character);}
                catch(IllegalArgumentException denied) {return new UiReplyPayload(request.sequence(),"FORBIDDEN","Character viewing permission denied","{}");}
                CharacterService.captureForViewer(player,character);
            }
            var response=new UiApiService(CharacterService.repository(),library,(operation,a)->world(player,operation,a)).handle(CharacterService.principal(player),op,args);
            if(response.code().equals("OK")) {
                if(op==Operation.CAPABILITIES) response.data().addProperty("canCreateCampaign",CharacterService.principal(player).admin());
                // Only resolve identities already present in this authorized read model. Never enumerate accounts.
                var names=new JsonObject();collectNames(player,response.data(),names,0);if(!names.isEmpty()) response.data().add("playerNames",names);
            }
            require(response.data().toString().length()<=UiReplyPayload.LIMIT,"Result too large");
            return new UiReplyPayload(request.sequence(),response.code(),response.message(),response.data().toString());
        } catch(java.io.IOException e) {return new UiReplyPayload(request.sequence(),"STORAGE_ERROR","Health capture failed; refresh before retrying","{}");}
        catch(RuntimeException e) {return new UiReplyPayload(request.sequence(),"REJECTED","Invalid UI request or unavailable service","{}");}
    }
    private static void collectNames(ServerPlayerEntity p,JsonElement value,JsonObject names,int depth) {
        if(depth>4 || names.size()>=256 || value==null || value.isJsonNull()) return;
        if(value.isJsonArray()) {for(var e:value.getAsJsonArray()) collectNames(p,e,names,depth+1);return;}
        if(!value.isJsonObject()) return;
        for(var entry:value.getAsJsonObject().entrySet()) {
            if(Set.of("sheet","history","origin","policy").contains(entry.getKey())) continue;
            if(Set.of("owner","controller","offered","members","dms").contains(entry.getKey())) {
                var ids=entry.getValue().isJsonArray()?entry.getValue().getAsJsonArray():new JsonArray();
                if(!entry.getValue().isJsonArray()) ids.add(entry.getValue());
                for(var e:ids) if(!e.isJsonNull() && names.size()<256) {
                    UUID id=UUID.fromString(e.getAsString());var online=p.getServer().getPlayerManager().getPlayer(id);
                    String name=online!=null?online.getName().getString():p.getServer().getUserCache()==null?"Unknown offline player":p.getServer().getUserCache().getByUuid(id).map(profile->profile.getName()).orElse("Unknown offline player");
                    names.addProperty(id.toString(),name.substring(0,Math.min(80,name.length())));
                }
            } else collectNames(p,entry.getValue(),names,depth+1);
        }
    }
    private static JsonObject world(ServerPlayerEntity p,Operation op,JsonObject a) throws java.io.IOException {
        var repo=CharacterService.repository();var who=CharacterService.principal(p);var out=new JsonObject();
        switch(op) {
            case CHECK_TARGETS, CHECK_OPTIONS, CHECK_PREPARE, CHECK_EXECUTE, CHECK_INBOX, CHECK_REQUEST, CHECK_DISMISS -> out=dev.tacticalcombat.checks.CheckGateway.handle(p,op,a);
            case GAMEPLAY_RECOVERY, GAMEPLAY_CANCEL -> out=GameplayRecovery.handle(p,op,a);
            case ACTOR_CHARACTERS, ENCOUNTER_DISCOVER, ENCOUNTER_START, ENCOUNTER_LIST, ENCOUNTER_WATCH, ENCOUNTER_END, QUICK_SHEET, DM_QUICK -> out=EncounterLauncher.handle(p,op,a);
            case ACTIVE -> {
                fields(a,"");var restored=CharacterService.restoreSelection(p);if(restored!=null&&restored.character()!=null)SELECTED.put(p.getUuid(),restored.character());try {UUID campaign=CharacterService.selected(p);if(repo.member(who,campaign)) out.addProperty("campaign",campaign.toString());} catch(IllegalArgumentException none) { }
                UUID selected=SELECTED.get(p.getUuid());if(selected!=null) try {var c=repo.visible(who,selected);require(c.campaign().equals(CharacterService.selected(p)),"Selection campaign changed");require(repo.controller(selected).equals(p.getUuid())||repo.manages(who,c.campaign()),"Character control revoked");out.addProperty("character",selected.toString());} catch(IllegalArgumentException revoked) {SELECTED.remove(p.getUuid());}
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
                UiEvents.SESSION.safePublish(rest.id(),campaign,null,0,UiEvents.Kind.REST,List.of(p.getUuid()),"Rest requested; no recovery has been applied",null,UiEvents.Visibility.RECIPIENTS_AND_DM,Set.copyOf(party));
            }
            case REST_REVIEW -> {
                fields(a,"request revision approve");require(workflows!=null,"Workflow service unavailable");var rest=workflows.get(repo,who,id(a,"request"));require(repo.manages(who,rest.campaign()),"Only campaign DMs review rest requests");
                if(bool(a,"approve") && System.currentTimeMillis()<rest.expires()) UiPresentation.eligible(p,rest.campaign(),rest.participants(),rest.dimension(),rest.x(),rest.y(),rest.z(),rest.radius());
                var saved=workflows.review(repo,who,rest.id(),revision(a),bool(a,"approve"),System.currentTimeMillis());out=SheetSections.JSON.toJsonTree(saved).getAsJsonObject();
                UiEvents.SESSION.safePublish(UUID.randomUUID(),rest.campaign(),null,0,UiEvents.Kind.REST,List.of(p.getUuid()),"Rest review: "+saved.state()+"; recovery remains manual",null,UiEvents.Visibility.RECIPIENTS_AND_DM,Set.copyOf(rest.participants()));
            }
            case REST_LIST -> {fields(a,"campaign page");require(workflows!=null,"Workflow service unavailable");out.add("requests",SheetSections.JSON.toJsonTree(workflows.list(repo,who,id(a,"campaign"),integer(a,"page"))));out.addProperty("serverTime",System.currentTimeMillis());out.addProperty("recoveryAutomated",false);}
            case CAMPAIGN_SELECT -> {
                if(a.has("quick")) {fields(a,"campaign quick");require(bool(a,"quick") && repo.manages(who,id(a,"campaign")),"Only campaign DMs may use hub quick-switch");}
                else fields(a,"campaign");
                CharacterService.select(p,id(a,"campaign"));SELECTED.remove(p.getUuid());out.addProperty("campaign",id(a,"campaign").toString());
            }
            case CHARACTER_SELECT -> {
                fields(a,"character revision");var c=repo.visible(who,id(a,"character"));require(c.revision()==revision(a),"Stale character revision");require(!CombatManager.isInCombat(p),"Character selection locked during combat");
                require(Objects.equals(CharacterService.selected(p),c.campaign()),"Select the character's campaign first");require(repo.controller(c.id()).equals(p.getUuid()) || repo.manages(who,c.campaign()),"Character control permission denied");
                CharacterService.remember(p,c.campaign(),c.id());SELECTED.put(p.getUuid(),c.id());out.addProperty("character",c.id().toString());out.addProperty("bindingChanged",false);
            }
            case BIND -> {
                fields(a,"character revision entity");UUID entity=id(a,"entity");LivingEntity target=null;
                for(var world:p.getServer().getWorlds()) if(world.getEntity(entity) instanceof LivingEntity living) {target=living;break;}
                require(target!=null && target.isAlive() && !target.isRemoved(),"Eligible loaded living entity required");
                var record=repo.visible(who,id(a,"character"));require(!record.profile().equals(dev.tacticalcombat.rules.RulesProfiles.SRD) || target instanceof ServerPlayerEntity || target instanceof net.minecraft.entity.mob.MobEntity,"Unsupported SRD binding entity");
                CharacterService.bind(p,id(a,"character"),revision(a),target);out.addProperty("bound",entity.toString());
            }
            case UNBIND -> {fields(a,"character revision");CharacterService.release(p,id(a,"character"),revision(a));out.addProperty("unbound",true);}
            case ELIGIBLE_ENTITIES -> {
                fields(a,a.has("binding")?"character binding kind":"character");var c=repo.visible(who,id(a,"character"));require(repo.manages(who,c.campaign()),"Only campaign DMs inspect binding candidates");
                var binding=a.has("binding")?dev.tacticalcombat.combat.NearbyActors.Binding.valueOf(string(a,"binding")):dev.tacticalcombat.combat.NearbyActors.Binding.ALL;var kind=a.has("kind")?dev.tacticalcombat.combat.NearbyActors.Kind.valueOf(string(a,"kind")):dev.tacticalcombat.combat.NearbyActors.Kind.ALL;
                var nearby=dev.tacticalcombat.combat.NearbyActors.sorted(p,32);
                var rows=new JsonArray();boolean truncated=false;for(var entity:nearby) {
                    var row=new JsonObject();row.addProperty("id",entity.getUuidAsString());row.addProperty("name",entity.getName().getString().substring(0,Math.min(80,entity.getName().getString().length())));
                    row.addProperty("distance",Math.round(Math.sqrt(entity.squaredDistanceTo(p))*10)/10d);row.addProperty("position",entity.getBlockX()+", "+entity.getBlockY()+", "+entity.getBlockZ());
                    boolean occupied=CharacterService.bound(entity)!=null,combat=CombatManager.isInCombat(entity),controller=!(entity instanceof ServerPlayerEntity) || repo.controller(c.id()).equals(entity.getUuid());
                    boolean type=!c.profile().equals(dev.tacticalcombat.rules.RulesProfiles.SRD) || entity instanceof ServerPlayerEntity || entity instanceof net.minecraft.entity.mob.MobEntity;
                    boolean eligible=c.approved() && !repo.reserved(c.id()) && repo.binding(c.id())==null && !occupied && !combat && controller && type;
                    if(!dev.tacticalcombat.combat.NearbyActors.matches(binding,kind,occupied,eligible,entity instanceof ServerPlayerEntity))continue;if(rows.size()==64){truncated=true;break;}row.addProperty("eligible",eligible);
                    row.addProperty("reason",!c.approved()?"Character needs approval":repo.reserved(c.id())?"Prepared gameplay reservation":repo.binding(c.id())!=null?"Character already bound":occupied?"Entity already bound":combat?"Entity in combat":!controller?"Player must accept control first":!type?"Unsupported SRD entity":"Binding candidate; server will revalidate");rows.add(row);
                }out.add("entities",rows);out.addProperty("truncated",truncated);out.addProperty("radius",32);
            }
            case ELIGIBLE_PLAYERS -> {
                fields(a,"campaign");UUID campaign=id(a,"campaign");require(repo.manages(who,campaign),"Only campaign DMs inspect eligible players");var rows=new JsonArray();
                for(var player:p.getServer().getPlayerManager().getPlayerList()) if(rows.size()<256) {var row=new JsonObject();row.addProperty("id",player.getUuidAsString());row.addProperty("name",player.getName().getString());row.addProperty("member",repo.member(new CharacterRepository.Principal(player.getUuid(),false),campaign));row.addProperty("inCombat",CombatManager.isInCombat(player));rows.add(row);}out.add("players",rows);
            }
            default -> throw new IllegalArgumentException("Operation unavailable in this checkpoint");
        }
        return out;
    }
}
