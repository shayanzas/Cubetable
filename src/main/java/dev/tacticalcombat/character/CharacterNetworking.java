package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.net.*;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.UUID;

/** Fabric calls the receiver on the server thread. All private replies go only to the requesting connection. */
public final class CharacterNetworking {
    private static final CharacterSessions SESSIONS=new CharacterSessions();
    private CharacterNetworking() {}
    public static void register() {
        PayloadTypeRegistry.playC2S().register(CharacterRequestPayload.ID,CharacterRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(CharacterReplyPayload.ID,CharacterReplyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(CharacterRequestPayload.ID,(request,context)->{
            if(ServerPlayNetworking.canSend(context.player(),CharacterReplyPayload.ID))
                ServerPlayNetworking.send(context.player(),handle(context.player(),request,context.server().getOverworld().getTime()));
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SESSIONS.forget(handler.player.getUuid()));
        ServerLifecycleEvents.SERVER_STOPPING.register(server->SESSIONS.clear());
    }
    public static CharacterReplyPayload handle(ServerPlayerEntity player,CharacterRequestPayload request,long tick) {
        String admitted=SESSIONS.admit(player.getUuid(),request.sequence(),tick);
        if(!admitted.equals("OK")) return new CharacterReplyPayload(request.sequence(),admitted,
            admitted.equals("RATE_LIMIT")?"Please wait a second, then retry.":"Request already processed or invalid. Refresh to confirm server state.","{}");
        try {
            if(request.json().length()>CharacterRequestPayload.LIMIT) throw new IllegalArgumentException("Request too large");
            CharacterRepository.checkDepth(request.json(),24);
            JsonObject args=JsonParser.parseString(request.json()).getAsJsonObject();
            if(request.operation().equals("get")) CharacterService.captureForViewer(player,UUID.fromString(args.get("character").getAsString()));
            CharacterSyncService.Reply reply;
            if(request.operation().equals("select")) {
                UUID id=UUID.fromString(args.get("campaign").getAsString());
                CharacterService.select(player,id); var data=new JsonObject(); data.addProperty("selected",id.toString());
                reply=new CharacterSyncService.Reply("OK","Campaign selected",data);
            } else {
                UUID selected=null;
                try { selected=CharacterService.selected(player); } catch(IllegalArgumentException ignored) { }
                reply=new CharacterSyncService(CharacterService.repository()).handle(CharacterService.principal(player),selected,request.operation(),args);
            }
            String json=reply.data().toString();
            if(json.length()>CharacterReplyPayload.LIMIT) throw new IllegalArgumentException("Result too large; use a smaller page");
            if(reply.code().equals("OK") && java.util.Set.of("create","srd_create","edit","approve","policy").contains(request.operation()))
                dev.tacticalcombat.TacticalCombatMod.LOGGER.info("Character {} by {}",request.operation(),player.getUuid());
            return new CharacterReplyPayload(request.sequence(),reply.code(),reply.message(),json);
        } catch(java.io.IOException e) {
            return new CharacterReplyPayload(request.sequence(),"STORAGE_ERROR","Could not capture current health; server state was not published.","{}");
        } catch(RuntimeException e) {
            String message=e.getMessage()==null?"Invalid character request":e.getMessage();
            return new CharacterReplyPayload(request.sequence(),message.toLowerCase(java.util.Locale.ROOT).contains("permission") || message.toLowerCase(java.util.Locale.ROOT).contains("membership")?"FORBIDDEN":"REJECTED",message.substring(0,Math.min(240,message.length())),"{}");
        }
    }
}
