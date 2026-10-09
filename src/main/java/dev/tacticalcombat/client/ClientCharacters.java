package dev.tacticalcombat.client;

import com.google.gson.JsonObject;
import dev.tacticalcombat.net.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.function.Consumer;

/** One in-flight request per connection; retries reuse sequence identity, never replay mutations as new requests. */
public final class ClientCharacters {
    private static long sequence;
    private static CharacterRequestPayload pending;
    private static Consumer<CharacterReplyPayload> callback;
    private static int ticks, retries;
    private ClientCharacters() {}
    public static boolean available() { return ClientPlayNetworking.canSend(CharacterRequestPayload.ID); }
    public static boolean busy() { return pending!=null; }
    public static boolean send(String operation,JsonObject args,Consumer<CharacterReplyPayload> result) {
        if(!available() || busy()) return false;
        String json=args.toString();
        if(json.length()>CharacterRequestPayload.LIMIT) { result.accept(new CharacterReplyPayload(0,"REJECTED","Request too large","{}")); return true; }
        pending=new CharacterRequestPayload(++sequence,operation,json); callback=result; ticks=0; retries=0;
        ClientPlayNetworking.send(pending); return true;
    }
    public static void receive(CharacterReplyPayload reply) {
        if(pending==null || reply.sequence()!=pending.sequence()) return;
        var consumer=callback; pending=null; callback=null; consumer.accept(reply);
    }
    public static void tick() {
        if(pending==null || ++ticks<100) return;
        ticks=0;
        if(++retries<=2 && available()) ClientPlayNetworking.send(pending);
        else receive(new CharacterReplyPayload(pending.sequence(),"TIMEOUT","Result unknown; refresh the list before attempting another change.","{}"));
    }
    public static void clear() { pending=null; callback=null; sequence=0; ticks=0; retries=0; }
}
