package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.client.ClientUiApi;
import dev.tacticalcombat.uiapi.UiApiService;
import java.util.function.*;

/** One shared client-thread pump prevents opening multiple screens from bypassing UI pacing. */
public final class UiRequestQueue {
    private static final UiRequestPump PUMP=new UiRequestPump(new UiRequestPump.Transport() {
        public boolean supported() {return ClientUiApi.supported();}
        public void send(UiApiService.Operation op,JsonObject args,Consumer<UiApiService.Reply> callback) {ClientUiApi.request(op,args,callback);}
    });
    private UiRequestQueue() {}
    public static void enqueue(UiApiService.Operation op,JsonObject args,BooleanSupplier valid,Consumer<UiApiService.Reply> callback) {PUMP.enqueue(op,args,valid,callback);}
    public static void tick() {PUMP.tick();}
    public static void clear() {PUMP.clear();}
}
