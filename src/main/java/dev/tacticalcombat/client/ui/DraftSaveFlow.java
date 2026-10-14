package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.uiapi.UiApiService;
import java.util.function.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** One completion gate shared by creator editors: only a checked ACK can advance. */
public final class DraftSaveFlow {
    @FunctionalInterface public interface Sender {void send(UiApiService.Operation op,JsonObject args,Consumer<UiApiService.Reply> callback);}
    private long generation;
    private boolean pending;
    public boolean pending(){return pending;}
    public void invalidate(){generation++;pending=false;}
    public void submit(Sender sender,UiApiService.Operation op,JsonObject args,Function<JsonObject,Boolean> accept,Runnable after,Consumer<String> failure){if(pending)return;pending=true;long token=++generation;sender.send(op,args.deepCopy(),reply->{if(token!=generation||!pending)return;pending=false;if(!reply.code().equals("OK")){failure.accept(reply.code()+": "+reply.message()+" Edits retained; refresh and review before retrying.");return;}try{var snapshot=reply.data();if(op==UiApiService.Operation.LIBRARY_UPDATE){snapshot=DraftReplies.snapshot(snapshot,text(args,"character"));if(revision(snapshot)!=revision(args)+1)throw new IllegalArgumentException("Unexpected save acknowledgement revision");}if(accept.apply(snapshot))after.run();}catch(RuntimeException invalid){failure.accept("Invalid save acknowledgement. Edits retained; refresh before retrying.");}});}
}
