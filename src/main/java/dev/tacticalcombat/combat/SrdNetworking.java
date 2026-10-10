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
    private static final Map<UUID,UUID> WATCHING=new HashMap<>();
    private static final Map<UUID,Long> SENT=new HashMap<>();
    private SrdNetworking() {}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(SrdRequestPayload.ID,SrdRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SrdStatePayload.ID,SrdStatePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SrdRequestPayload.ID,(request,ctx)->{
            if(ServerPlayNetworking.canSend(ctx.player(),SrdStatePayload.ID)) ServerPlayNetworking.send(ctx.player(),handle(ctx.player(),request));
        });
        ServerTickEvents.END_SERVER_TICK.register(server->{
            for(var player:server.getPlayerManager().getPlayerList()) {
                UUID watching=WATCHING.get(player.getUuid()); if(watching==null) continue;
                var encounter=find(watching);
                if(encounter==null) {
                    send(player,new SrdStatePayload(watching,Long.MAX_VALUE,"ENDED","Encounter ended; D&D resources retained","{}"));
                    WATCHING.remove(player.getUuid()); SENT.remove(player.getUuid()); continue;
                }
                if(SENT.getOrDefault(player.getUuid(),-1L)==encounter.revision()) continue;
                try { send(player,state(encounter,CharacterService.principal(player),"STATE",encounter.reason(),null)); SENT.put(player.getUuid(),encounter.revision()); }
                catch(IllegalArgumentException denied) { WATCHING.remove(player.getUuid()); SENT.remove(player.getUuid()); send(player,new SrdStatePayload(watching,Long.MAX_VALUE,"ENDED","Encounter permission no longer available","{}")); }
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->{ LIMIT.forget(handler.player.getUuid()); WATCHING.remove(handler.player.getUuid()); SENT.remove(handler.player.getUuid()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{LIMIT.clear();WATCHING.clear();SENT.clear();});
    }
    private static void send(ServerPlayerEntity p,SrdStatePayload payload) { if(ServerPlayNetworking.canSend(p,SrdStatePayload.ID)) ServerPlayNetworking.send(p,payload); }
    static SrdEncounter find(UUID id) { return CombatManager.encounters().stream().filter(e->e.id().equals(id) && e instanceof SrdEncounter).map(e->(SrdEncounter)e).findFirst().orElse(null); }
    static void watch(ServerPlayerEntity p,SrdEncounter encounter) {
        var payload=state(encounter,CharacterService.principal(p),"STATE",encounter.reason(),null);
        WATCHING.put(p.getUuid(),encounter.id()); SENT.remove(p.getUuid()); send(p,payload);
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
            var p=CharacterService.principal(player); encounter.snapshot(p); // Authorize before returning any data.
            revision=encounter.revision();
            if(request.operation().equals("state")) { WATCHING.put(player.getUuid(),encounter.id()); return state(encounter,p,"STATE",encounter.reason(),null); }
            if(!encounter.admit(p,request)) throw new IllegalArgumentException("Stale, duplicate, excessive or unauthorized request");
            var extra=new JsonObject(); String message="Request accepted",code="OK";
            switch(request.operation()) {
                case "tie" -> {
                    var choices=args.getAsJsonArray("order"); if(choices.size()>16) throw new IllegalArgumentException("Tie group too large");
                    var ids=new ArrayList<UUID>(); choices.forEach(e->ids.add(UUID.fromString(e.getAsString()))); encounter.tie(p,ids);
                }
                case "grid" -> {
                    var grid=encounter.grid(p); var cells=new JsonArray();
                    for(var n:grid.reachable().values()) {
                        var cell=new JsonArray(); cell.add(n.cell().x()); cell.add(n.cell().z()); cell.add(n.cell().y()); cell.add(n.costFeet());
                        if(n.parent()!=null) { cell.add(n.parent().x()); cell.add(n.parent().z()); cell.add(n.parent().y()); } cells.add(cell);
                    }
                    extra.add("grid",cells); extra.addProperty("truncated",grid.truncated()); code="GRID";
                }
                case "move" -> {
                    var result=encounter.move(p,request.request(),new SrdMovement.Cell(integer(args,"x"),integer(args,"z"),args.get("y").getAsDouble()));
                    message=result.message(); if(!result.accepted()) code="REJECTED";
                }
                case "cancel" -> encounter.cancelMove(p);
                case "preview" -> {
                    var intent=encounter.preview(p,UUID.fromString(args.get("target").getAsString()),SrdEquipment.Weapon.valueOf(args.get("weapon").getAsString()),
                            SrdCharacter.Ability.valueOf(args.get("ability").getAsString()),args.get("twoHands").getAsBoolean());
                    var plan=intent.plan(); var preview=new JsonObject(); preview.addProperty("id",intent.id().toString());
                    preview.addProperty("weapon",plan.weapon().name()); preview.addProperty("modifier",plan.attackModifier());
                    preview.addProperty("attack",(plan.disadvantage()?"2d20 keep lowest":"1d20")+" + "+plan.attackModifier());
                    preview.addProperty("damage",plan.dice()+"d"+plan.sides()+" + "+plan.flatDamage());
                    preview.addProperty("type",plan.weapon().damageType.name()); preview.addProperty("minimum",plan.minimumDamage()); preview.addProperty("maximum",plan.maximumDamage());
                    preview.addProperty("reach",plan.reachFeet()); preview.addProperty("cost","One action, spent even on a miss");
                    extra.add("preview",preview); code="PREVIEW";
                }
                case "confirm" -> {
                    var result=encounter.confirm(p,UUID.fromString(args.get("intent").getAsString()));
                    if(result.state==SrdEncounter.TransactionState.COMMITTED) {
                        var roll=new JsonObject(); roll.addProperty("action",result.intent.id().toString()); roll.addProperty("hit",result.roll.hit()); roll.addProperty("critical",result.roll.critical());
                        roll.addProperty("chosen",result.roll.roll().chosen()); roll.addProperty("total",result.roll.roll().total());
                        var faces=new JsonArray(); result.roll.roll().faces().forEach(faces::add); roll.add("faces",faces);
                        var damage=new JsonArray(); result.roll.damageFaces().forEach(damage::add); roll.add("damageFaces",damage);
                        extra.add("result",roll); code="RESULT";
                    } else code="SAVE_FAILED";
                    message=encounter.reason();
                }
                case "endturn" -> encounter.endTurn(p);
                case "retry" -> { encounter.retry(p); message=encounter.reason(); code=encounter.state()==SrdEncounter.State.PAUSED?"PAUSED":"OK"; }
                case "heal" -> encounter.heal(p,UUID.fromString(args.get("target").getAsString()),integer(args,"amount"));
                case "end" -> { encounter.stop(p); return new SrdStatePayload(encounter.id(),Long.MAX_VALUE,"ENDED","Encounter ended","{}"); }
                default -> throw new IllegalArgumentException("Unsupported SRD operation");
            }
            WATCHING.put(player.getUuid(),encounter.id()); return state(encounter,p,code,message,extra);
        } catch(java.io.IOException storage) { return new SrdStatePayload(request.encounter(),revision,"STORAGE_ERROR","Healing was not committed; refresh and retry","{}"); }
        catch(RuntimeException denied) {
            String text=denied.getMessage()==null?"Invalid SRD request":denied.getMessage();
            return new SrdStatePayload(request.encounter(),revision,"REJECTED",text.substring(0,Math.min(256,text.length())),"{}");
        }
    }
    private static int integer(JsonObject args,String key) { return args.get(key).getAsBigDecimal().intValueExact(); }
}
