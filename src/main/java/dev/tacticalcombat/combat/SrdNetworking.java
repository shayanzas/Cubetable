package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.core.RateLimit;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.*;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

/** Authenticated server-thread gateway. Discovery is explicit; snapshots are individually permission-filtered. */
public final class SrdNetworking {
    private static final RateLimit LIMIT=new RateLimit(12,20);
    // Reachable-grid searches are substantially more expensive than small state replies.
    private static final RateLimit GRID_LIMIT=new RateLimit(2,20);
    private static final Map<UUID,UUID> WATCHING=new HashMap<>();
    private record Delivery(UUID encounter,long revision,long campaignRevision,boolean admin) {}
    private static final Map<UUID,Delivery> SENT=new HashMap<>();
    private SrdNetworking() {}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(SrdRequestPayload.ID,SrdRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SrdStatePayload.ID,SrdStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SrdRequestPayload.ID,(request,ctx)->{
            if(ServerPlayNetworking.canSend(ctx.player(),SrdStatePayload.ID)) send(ctx.player(),handle(ctx.player(),request));
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            SrdGridJobs.tick(SrdNetworking::send);
            synchronize(server);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{ SrdGridJobs.cancel(handler.player.getUuid()); LIMIT.forget(handler.player.getUuid()); GRID_LIMIT.forget(handler.player.getUuid()); WATCHING.remove(handler.player.getUuid()); SENT.remove(handler.player.getUuid()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{SrdGridJobs.clear();LIMIT.clear();GRID_LIMIT.clear();WATCHING.clear();SENT.clear();});
    }
    static void synchronize(net.minecraft.server.MinecraftServer server) {
            for(var player:server.getPlayerManager().getPlayerList()) {
                UUID watching=WATCHING.get(player.getUuid()); if(watching==null) continue;
                var encounter=find(watching);
                if(encounter==null) {
                    send(player,new SrdStatePayload(watching,Long.MAX_VALUE,"ENDED","Encounter ended; D&D resources retained","{}"));
                    WATCHING.remove(player.getUuid()); SENT.remove(player.getUuid()); continue;
                }
                try {
                    var principal=CharacterService.principal(player);encounter.authorizeViewer(principal);
                    var previous=SENT.get(player.getUuid());
                    if(previous!=null && previous.encounter.equals(encounter.id()) && previous.revision==encounter.revision()
                            && previous.campaignRevision==CharacterService.repository().campaign(encounter.campaign()).revision()
                            && previous.admin==principal.admin()) continue;
                    send(player,state(encounter,principal,"STATE",encounter.reason(),null));
                }
                catch(IllegalArgumentException denied) { WATCHING.remove(player.getUuid()); SENT.remove(player.getUuid()); send(player,new SrdStatePayload(watching,Long.MAX_VALUE,"ENDED","Encounter permission no longer available","{}")); }
            }
    }
    static void send(ServerPlayerEntity p,SrdStatePayload payload) {
        if(ServerPlayNetworking.canSend(p,SrdStatePayload.ID)) {
            ServerPlayNetworking.send(p,payload);
            if(payload.code().equals("ENDED")) SENT.remove(p.getUuid());
            else if(!payload.json().equals("{}") && !Set.of("REJECTED","STORAGE_ERROR","GRID_CANCELLED").contains(payload.code())) {
                var encounter=find(payload.encounter());
                if(encounter!=null) SENT.put(p.getUuid(),new Delivery(payload.encounter(),payload.revision(),
                        CharacterService.repository().campaign(encounter.campaign()).revision(),CharacterService.principal(p).admin()));
            }
        }
    }
    static SrdEncounter find(UUID id) { return CombatManager.encounters().stream().filter(e->e.id().equals(id) && e instanceof SrdEncounter).map(e->(SrdEncounter)e).findFirst().orElse(null); }
    private static void selectWatch(ServerPlayerEntity p,UUID encounter) {
        UUID old=WATCHING.put(p.getUuid(),encounter);
        if(old!=null && !old.equals(encounter)) SrdGridJobs.cancel(p.getUuid());
    }
    static void watch(ServerPlayerEntity p,SrdEncounter encounter) {
        var payload=state(encounter,CharacterService.principal(p),"STATE",encounter.reason(),null);
        selectWatch(p,encounter.id()); send(p,payload);
    }
    static SrdStatePayload state(SrdEncounter encounter,CharacterRepository.Principal p,String code,String message,JsonObject extra) {
        JsonObject data=encounter.snapshot(p); if(extra!=null) extra.entrySet().forEach(e->data.add(e.getKey(),e.getValue()));
        String json=data.toString(); if(json.length()>SrdStatePayload.LIMIT) throw new IllegalArgumentException("Snapshot exceeds bounds");
        return new SrdStatePayload(encounter.id(),encounter.revision(),code,message.substring(0,Math.min(256,message.length())),json);
    }
    public static SrdStatePayload handle(ServerPlayerEntity player,SrdRequestPayload request) {
        long revision=0;
        try {
            if(!player.getServer().isOnThread()) throw new IllegalArgumentException("Server thread required");
            if(!ServerPlayNetworking.canSend(player,SrdStatePayload.ID)) throw new IllegalArgumentException("SRD v1 client required");
            if(!LIMIT.allow(player.getUuid(),player.getServer().getOverworld().getTime())) throw new IllegalArgumentException("Rate limit: wait before retrying");
            if(request.json().length()>SrdRequestPayload.LIMIT) throw new IllegalArgumentException("Request too large");
            CharacterRepository.checkDepth(request.json(),8); JsonObject args=JsonParser.parseString(request.json()).getAsJsonObject();
            SrdEncounter encounter=find(request.encounter()); if(encounter==null) throw new IllegalArgumentException("Encounter unavailable");
            var p=CharacterService.principal(player); encounter.authorizeViewer(p); // Authorize without constructing a discarded snapshot.
            revision=encounter.revision();
            if(request.operation().equals("state")) { selectWatch(player,encounter.id()); return state(encounter,p,"STATE",encounter.reason(),null); }
            if(!encounter.admit(p,request)) throw new IllegalArgumentException("Stale, duplicate, excessive or unauthorized request");
            var extra=new JsonObject(); String message="Request accepted",code="OK";
            switch(request.operation()) {
                case "tie" -> {
                    var choices=args.getAsJsonArray("order"); if(choices.size()>16) throw new IllegalArgumentException("Tie group too large");
                    var ids=new ArrayList<UUID>(); choices.forEach(e->ids.add(UUID.fromString(e.getAsString()))); encounter.tie(p,ids);
                }
                case "grid" -> {
                    if(!GRID_LIMIT.allow(player.getUuid(),player.getServer().getOverworld().getTime())) throw new IllegalArgumentException("Grid rate limit: wait a second before refreshing");
                    selectWatch(player,encounter.id());
                    if(encounter.budgetedGrid()) {
                        SrdGridJobs.submit(player,encounter,request.request());code="GRID_PENDING";message="Grid pending; calculating within server tick budget";
                        extra.addProperty("gridRequest",request.request().toString());
                    } else {
                        SrdGridJobs.cancel(player.getUuid());extra=SrdGridJobs.grid(encounter.grid(p));extra.addProperty("gridRequest",request.request().toString());code="GRID";
                    }
                }
                case "move" -> {
                    var result=encounter.move(p,request.request(),new SrdMovement.Cell(integer(args,"x"),integer(args,"z"),args.get("y").getAsDouble()));
                    message=result.message(); if(!result.accepted()) code="REJECTED";
                }
                case "cancel" -> {SrdGridJobs.cancel(player.getUuid());encounter.cancelMove(p);}
                case "preview" -> {
                    var intent=encounter.preview(p,UUID.fromString(args.get("target").getAsString()),SrdEquipment.Weapon.valueOf(args.get("weapon").getAsString()),
                            args.has("mode")?SrdRules.AttackMode.valueOf(args.get("mode").getAsString()):SrdRules.AttackMode.MELEE,
                            SrdCharacter.Ability.valueOf(args.get("ability").getAsString()),args.get("twoHands").getAsBoolean());
                    var plan=intent.plan(); var preview=new JsonObject(); preview.addProperty("id",intent.id().toString());
                    preview.addProperty("weapon",plan.weapon().name()); preview.addProperty("modifier",plan.attackModifier());
                    var mode=SrdRules.rollMode(intent.geometry().advantage(),intent.geometry().disadvantage()||plan.disadvantage());
                    preview.addProperty("attack",(mode==SrdRules.RollMode.NORMAL?"1d20":mode==SrdRules.RollMode.ADVANTAGE?"2d20 keep highest":"2d20 keep lowest")+" + "+plan.attackModifier());
                    preview.addProperty("damage",plan.dice()==0?Integer.toString(plan.flatDamage()):plan.dice()+"d"+plan.sides()+" + "+plan.flatDamage());
                    preview.addProperty("type",plan.weapon().damageType.name()); preview.addProperty("minimum",plan.minimumDamage()); preview.addProperty("maximum",plan.maximumDamage());
                    preview.addProperty("reach",plan.reachFeet()); preview.addProperty("cost","One action, spent even on a miss");
                    preview.addProperty("cover",intent.geometry().cover().name());
                    preview.addProperty("range",plan.mode()==SrdRules.AttackMode.MELEE?plan.reachFeet()+" ft reach":plan.normalRangeFeet()+"/"+plan.longRangeFeet()+" ft range");
                    preview.addProperty("stock",plan.mode()==SrdRules.AttackMode.MELEE?"No ammunition":plan.mode()==SrdRules.AttackMode.THROWN?"Spends one dagger":"Spends one arrow/bolt; loading limited to this one attack/action");
                    extra.add("preview",preview); code="PREVIEW";
                }
                case "confirm" -> {
                    var result=encounter.confirm(p,UUID.fromString(args.get("intent").getAsString()));
                    if(result.state==SrdEncounter.TransactionState.COMMITTED) {
                        var roll=new JsonObject(); roll.addProperty("action",result.intent.id().toString()); roll.addProperty("hit",result.roll.hit()); roll.addProperty("critical",result.roll.critical());
                        roll.addProperty("chosen",result.roll.roll().chosen()); roll.addProperty("total",result.roll.roll().total());
                        var faces=new JsonArray(); result.roll.roll().faces().forEach(faces::add); roll.add("faces",faces);
                        var damage=new JsonArray(); result.roll.damageFaces().forEach(damage::add); roll.add("damageFaces",damage);
                        var mode=SrdRules.rollMode(result.intent.geometry().advantage(),result.intent.geometry().disadvantage()||result.intent.plan().disadvantage());
                        roll.addProperty("keep",mode==SrdRules.RollMode.NORMAL?0:mode==SrdRules.RollMode.ADVANTAGE?1:2);
                        extra.add("result",roll); code="RESULT";
                        // Dice are private to the acting player and authorized watching managers.
                        for(var viewer:player.getServer().getPlayerManager().getPlayerList()) if(viewer!=player
                                && encounter.id().equals(WATCHING.get(viewer.getUuid()))
                                && (viewer.getUuid().equals(result.intent.actor()) || CharacterService.repository().manages(CharacterService.principal(viewer),encounter.campaign()))) {
                            send(viewer,state(encounter,CharacterService.principal(viewer),"RESULT",encounter.reason(),extra));
                        }
                    } else code="SAVE_FAILED";
                    message=encounter.reason();
                }
                case "endturn" -> encounter.endTurn(p);
                case "retry" -> { encounter.retry(p); message=encounter.reason(); code=encounter.state()==SrdEncounter.State.PAUSED?"PAUSED":"OK"; }
                case "heal" -> encounter.heal(p,UUID.fromString(args.get("target").getAsString()),integer(args,"amount"));
                case "end" -> { encounter.stop(p); return new SrdStatePayload(encounter.id(),Long.MAX_VALUE,"ENDED","Encounter ended","{}"); }
                default -> throw new IllegalArgumentException("Unsupported SRD operation");
            }
            selectWatch(player,encounter.id()); return state(encounter,p,code,message,extra);
        } catch(java.io.IOException storage) { return new SrdStatePayload(request.encounter(),revision,"STORAGE_ERROR","Healing was not committed; refresh and retry","{}"); }
        catch(RuntimeException denied) {
            String text=denied.getMessage()==null?"Invalid SRD request":denied.getMessage();
            return new SrdStatePayload(request.encounter(),revision,"REJECTED",text.substring(0,Math.min(256,text.length())),"{}");
        }
    }
    private static int integer(JsonObject args,String key) { return args.get(key).getAsBigDecimal().intValueExact(); }
}
