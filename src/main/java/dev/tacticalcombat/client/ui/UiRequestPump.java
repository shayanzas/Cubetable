package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.uiapi.UiApiService;
import java.util.*;
import java.util.function.*;

/** Testable bounded pacing/lifecycle logic. It never retries an uncertain mutation. */
public final class UiRequestPump {
    public interface Transport {
        boolean supported();
        void send(UiApiService.Operation op,JsonObject args,Consumer<UiApiService.Reply> callback);
    }
    private record Work(UiApiService.Operation operation,JsonObject args,BooleanSupplier valid,Consumer<UiApiService.Reply> reply) {}
    private final Deque<Work> queue=new ArrayDeque<>();
    private final Transport transport;
    private boolean waiting;
    private int delay;
    private long session;
    public UiRequestPump(Transport transport) {this.transport=transport;}
    public void enqueue(UiApiService.Operation op,JsonObject args,BooleanSupplier valid,Consumer<UiApiService.Reply> callback) {
        queue.removeIf(w->!w.valid.getAsBoolean());
        if(queue.size()>=16) {if(valid.getAsBoolean()) callback.accept(new UiApiService.Reply("BUSY","Wait for queued UI requests",new JsonObject()));return;}
        queue.addLast(new Work(op,args.deepCopy(),valid,callback));
    }
    public void tick() {
        if(delay>0) delay--;if(waiting || delay>0) return;
        while(!queue.isEmpty()) {
            var w=queue.removeFirst();if(!w.valid.getAsBoolean()) continue;
            if(!transport.supported()) {w.reply.accept(new UiApiService.Reply("UNAVAILABLE","Updated Cubetable UI server required",new JsonObject()));return;}
            waiting=true;delay=8;long current=session;
            try {transport.send(w.operation,w.args,reply->{
                if(current!=session) return;waiting=false;if(reply.code().equals("RATE_LIMIT")) delay=25;
                if(w.valid.getAsBoolean()) w.reply.accept(reply);
            });} catch(RuntimeException failure) {waiting=false;if(w.valid.getAsBoolean()) w.reply.accept(new UiApiService.Reply("REJECTED",failure.getMessage(),new JsonObject()));}
            return;
        }
    }
    public void clear() {session++;queue.clear();waiting=false;delay=0;}
    public int queued() {return queue.size();}
    public boolean waiting() {return waiting;}
}
