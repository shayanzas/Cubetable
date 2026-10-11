package dev.tacticalcombat.client;
import com.google.gson.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.uiapi.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.*;
import java.util.function.Consumer;
/** GUI-independent transport. Does not overwrite screens or unsaved local edits. Call on client thread. */
public final class ClientUiApi {
    private record Pending(long expires,Consumer<UiApiService.Reply> callback) {}
    private static final Map<Long,Pending> PENDING=new LinkedHashMap<>();
    private static long sequence;
    private ClientUiApi() {}
    public static boolean supported() {return ClientPlayNetworking.canSend(UiRequestPayload.ID);}
    public static long request(UiApiService.Operation operation,JsonObject args,Consumer<UiApiService.Reply> callback) {
        if(!supported()) throw new IllegalStateException("Updated Cubetable UI server required");
        if(PENDING.size()>=8) throw new IllegalStateException("Wait for pending UI requests");
        String json=args.toString();if(json.length()>UiRequestPayload.LIMIT) throw new IllegalArgumentException("Request too large");
        if(sequence==Long.MAX_VALUE) throw new IllegalStateException("Reconnect to renew request session");
        long id=++sequence;PENDING.put(id,new Pending(System.nanoTime()+10_000_000_000L,Objects.requireNonNull(callback)));
        try {ClientPlayNetworking.send(new UiRequestPayload(id,operation.name(),json));} catch(RuntimeException failure) {PENDING.remove(id);throw failure;}return id;
    }
    public static void receive(UiReplyPayload reply) {
        var pending=PENDING.remove(reply.sequence());if(pending==null) return;
        UiApiService.Reply result;
        try {dev.tacticalcombat.character.CharacterRepository.checkDepth(reply.json(),24);result=new UiApiService.Reply(reply.code(),reply.message(),JsonParser.parseString(reply.json()).getAsJsonObject());}
        catch(RuntimeException invalid) {result=new UiApiService.Reply("INVALID_REPLY","Server reply could not be read",new JsonObject());}
        pending.callback.accept(result);
    }
    public static void tick() {
        var expired=new ArrayList<Pending>();long now=System.nanoTime();PENDING.values().removeIf(p->{if(now>=p.expires) {expired.add(p);return true;}return false;});
        for(var p:expired) p.callback.accept(new UiApiService.Reply("TIMEOUT","No acknowledgment; refresh before retrying a mutation",new JsonObject()));
    }
    public static void clear() {PENDING.clear();sequence=0;}
}
