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
        SrdEncounter admitted=null;
        try {
            if(!player.getServer().isOnThread()) throw new IllegalArgumentException("Server thread required");
            if(!ServerPlayNetworking.canSend(player,SrdStatePayload.ID)) throw new IllegalArgumentException("SRD v1 client required");
            if(!LIMIT.allow(player.getUuid(),player.getServer().getOverworld().getTime())) throw new IllegalArgumentException("Rate limit: wait before retrying");
            if(request.json().length()>SrdRequestPayload.LIMIT) throw new IllegalArgumentException("Request too large");
            CharacterRepository.checkDepth(request.json(),8); JsonObject args=JsonParser.parseString(request.json()).getAsJsonObject();
            if(request.operation().equals("confirm")){
                if(!args.keySet().equals(Set.of("intent")))throw new IllegalArgumentException("Confirmation accepts only its server intent identity");
                var receipt=CharacterService.repository().gameplayReceipt(UUID.fromString(args.get("intent").getAsString()));
                if(receipt!=null)return replay(player,request,receipt);
            }
            SrdEncounter encounter=find(request.encounter()); if(encounter==null) throw new IllegalArgumentException("Encounter unavailable");
            var p=CharacterService.principal(player); encounter.authorizeViewer(p); // Authorize without constructing a discarded snapshot.
            revision=encounter.revision();
            if(request.operation().equals("state")) { selectWatch(player,encounter.id()); return state(encounter,p,"STATE",encounter.reason(),null); }
            if(!encounter.admit(p,request)) throw new IllegalArgumentException("Stale, duplicate, excessive or unauthorized request");
            admitted=encounter;
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
                    var result=encounter.move(p,request.request(),new SrdMovement.Cell(integer(args,"x"),integer(args,"z"),args.get("y").getAsDouble()),args.has("stepTicks")?integer(args,"stepTicks"):1);
                    message=result.message(); if(!result.accepted()) code="REJECTED";
                }
                case "drop_prone", "stand" -> {
                    if(!args.keySet().equals(Set.of("campaign","actor","revision")))throw new IllegalArgumentException("Posture accepts only expected campaign, actor and revision");
                    boolean dropping=request.operation().equals("drop_prone");
                    encounter.posture(p,UUID.fromString(args.get("campaign").getAsString()),UUID.fromString(args.get("actor").getAsString()),args.get("revision").getAsBigDecimal().longValueExact(),dropping);
                    SrdGridJobs.cancel(player.getUuid());message=dropping?"Dropped Prone; no action or movement spent; movement now crawls":"Stood up; half-speed movement cost spent; action unchanged";code="POSTURE";
                }
                case "dodge" -> {
                    if(!args.keySet().equals(Set.of("campaign","actor","revision")))throw new IllegalArgumentException("Dodge accepts only expected campaign, actor and revision");
                    encounter.dodge(p,UUID.fromString(args.get("campaign").getAsString()),UUID.fromString(args.get("actor").getAsString()),args.get("revision").getAsBigDecimal().longValueExact());
                    message="Dodge accepted; normal action spent; benefits until your next turn while eligible";code="DODGE";
                }
                case "dash" -> {
                    if(!args.keySet().equals(Set.of("campaign","actor","revision")))throw new IllegalArgumentException("Dash accepts only expected campaign, actor and revision");
                    encounter.dash(p,UUID.fromString(args.get("campaign").getAsString()),UUID.fromString(args.get("actor").getAsString()),args.get("revision").getAsBigDecimal().longValueExact());
                    SrdGridJobs.cancel(player.getUuid());message=encounter.reason();code="DASH";
                }
                case "cancel" -> {SrdGridJobs.cancel(player.getUuid());encounter.cancelMove(p);}
                case "preview" -> {
                    var intent=encounter.preview(p,UUID.fromString(args.get("target").getAsString()),SrdEquipment.Weapon.valueOf(args.get("weapon").getAsString()),
                            args.has("mode")?SrdRules.AttackMode.valueOf(args.get("mode").getAsString()):SrdRules.AttackMode.MELEE,
                            SrdCharacter.Ability.valueOf(args.get("ability").getAsString()),args.get("twoHands").getAsBoolean());
                    var plan=intent.plan(); var preview=new JsonObject(); preview.addProperty("id",intent.id().toString());
                    preview.addProperty("actor",intent.actor().toString());preview.addProperty("target",intent.target().toString());preview.addProperty("turn",intent.turn());preview.addProperty("revision",encounter.revision());
                    extra.addProperty("previewRequest",request.request().toString());
                    preview.addProperty("weapon",plan.weapon().name()); preview.addProperty("modifier",plan.attackModifier());
                    var mode=SrdRules.rollMode(intent.geometry().advantage(),intent.geometry().disadvantage()||plan.disadvantage());
                    preview.addProperty("rollMode",mode.name());
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
                        extra.add("result",result(result.plan,p)); code="RESULT";
                        // Dice are private to the acting player and authorized watching managers.
                        for(var viewer:player.getServer().getPlayerManager().getPlayerList()) if(viewer!=player
                                && encounter.id().equals(WATCHING.get(viewer.getUuid()))
                                && (viewer.getUuid().equals(result.intent.actor()) || CharacterService.repository().manages(CharacterService.principal(viewer),encounter.campaign()))) {
                            var privateExtra=new JsonObject();privateExtra.add("result",result(result.plan,CharacterService.principal(viewer)));
                            send(viewer,state(encounter,CharacterService.principal(viewer),"RESULT",encounter.reason(),privateExtra));
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
            if(code.equals("REJECTED")) rejection(encounter,player,request);
            extra.addProperty("replyRequest",request.request().toString());
            selectWatch(player,encounter.id()); return state(encounter,p,code,message,extra);
        } catch(java.io.IOException storage) { return new SrdStatePayload(request.encounter(),revision,"STORAGE_ERROR","Healing was not committed; refresh and retry",replyIdentity(request)); }
        catch(RuntimeException denied) {
            if(admitted!=null) rejection(admitted,player,request);
            String text=denied.getMessage()==null?"Invalid SRD request":denied.getMessage();
            return new SrdStatePayload(request.encounter(),revision,"REJECTED",text.substring(0,Math.min(256,text.length())),admitted==null?"{}":replyIdentity(request));
        }
    }
    private static String replyIdentity(SrdRequestPayload request){var data=new JsonObject();data.addProperty("replyRequest",request.request().toString());return data.toString();}
    private static SrdStatePayload replay(ServerPlayerEntity player,SrdRequestPayload request,GameplayReceipt raw){
        var repo=CharacterService.repository();var who=CharacterService.principal(player);var receipt=repo.visibleReceipt(who,raw.plan().operation());var plan=receipt.plan();
        if(!plan.encounter().equals(request.encounter())||plan.turn()!=request.turn())throw new IllegalArgumentException("Operation identity reused with altered encounter or turn");
        var extra=new JsonObject();extra.addProperty("replyRequest",request.request().toString());extra.addProperty("replayed",true);extra.addProperty("operation",plan.operation().toString());extra.addProperty("status",receipt.status().name());
        if(receipt.status()==GameplayReceipt.Status.COMMITTED)extra.add("result",result(plan,who));
        var encounter=find(request.encounter());String message="Stored gameplay operation: "+receipt.status()+"; no resources spent again";
        if(encounter!=null){encounter.authorizeViewer(who);selectWatch(player,encounter.id());return state(encounter,who,receipt.status()==GameplayReceipt.Status.COMMITTED?"RESULT":"SAVE_FAILED",message,extra);}
        return new SrdStatePayload(request.encounter(),0,"RECEIPT",message,extra.toString());
    }
    static JsonObject result(GameplayActionPlan plan,CharacterRepository.Principal viewer){
        var outcome=plan.outcome();var roll=new JsonObject();roll.addProperty("action",plan.operation().toString());roll.addProperty("turn",plan.turn());roll.addProperty("hit",outcome.hit());roll.addProperty("critical",outcome.critical());roll.addProperty("chosen",outcome.roll().chosen());roll.addProperty("total",outcome.roll().total());
        var faces=new JsonArray();outcome.roll().faces().forEach(faces::add);roll.add("faces",faces);var damage=new JsonArray();outcome.damageFaces().forEach(damage::add);roll.add("damageFaces",damage);
        var attack=plan.attack();var mode=SrdRules.rollMode(attack.advantage(),attack.disadvantage()||SrdRules.plan(plan.attacker().before(),attack.weapon(),attack.mode(),attack.ability(),attack.twoHands()).disadvantage());roll.addProperty("keep",mode==SrdRules.RollMode.NORMAL?0:mode==SrdRules.RollMode.ADVANTAGE?1:2);
        roll.addProperty("actor",plan.attacker().actor().toString());roll.addProperty("target",plan.target().actor().toString());
        roll.addProperty("modifier",outcome.roll().total()-outcome.roll().chosen());
        roll.addProperty("rolledDamage",outcome.damage().amount());roll.addProperty("damageType",outcome.damage().type().name());
        var repo=CharacterService.repository();
        if(repo.member(viewer,plan.campaign()) && (repo.manages(viewer,plan.campaign()) || plan.target().actor().equals(viewer.id()) && repo.controller(plan.target().character()).equals(viewer.id()))) {
            roll.addProperty("hpBefore",plan.target().before().hp());roll.addProperty("hpAfter",plan.target().after().hp());
            roll.addProperty("temporaryBefore",plan.target().before().temporaryHp());roll.addProperty("temporaryAfter",plan.target().after().temporaryHp());
            roll.addProperty("zeroHp",plan.target().after().hp()==0);
        }
        return roll;
    }
    private static void rejection(SrdEncounter encounter,ServerPlayerEntity player,SrdRequestPayload request) {
        dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(dev.tacticalcombat.uiapi.UiEvents.derived(request.request(),player.getUuid()+":rejection"),encounter.campaign(),encounter.id(),request.turn(),dev.tacticalcombat.uiapi.UiEvents.Kind.REJECTION,java.util.List.of(player.getUuid()),"Admitted SRD operation rejected; inspect the direct response for details",null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,java.util.Set.of(player.getUuid()));
    }
    private static int integer(JsonObject args,String key) { return args.get(key).getAsBigDecimal().intValueExact(); }
}
