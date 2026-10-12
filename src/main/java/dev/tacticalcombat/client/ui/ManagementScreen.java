package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.client.ClientUiApi;
import dev.tacticalcombat.uiapi.UiApiService;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import java.util.function.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Shared list/action workspace built from Checkpoint 1 components, with fail-closed mutation state. */
public abstract class ManagementScreen extends RpgScreen {
    protected RpgList list;
    protected final List<HubState.Choice> rows=new ArrayList<>();
    private final Map<String,Runnable> actions=new HashMap<>();
    protected String query="";
    protected boolean fresh;
    protected boolean ambiguousCreation;
    protected ManagementScreen(String title,Screen parent) {super(title,parent);}
    @Override protected void init() {
        super.init();clearChildren();busy=false;fresh=false;rows.clear();actions.clear();
        search(frame.x()+12,frame.y()+36,frame.width()-110,query,s->{query=s;filter();});
        button("Refresh","",frame.x()+frame.width()-86,frame.y()+36,74,20,()->{if(!busy) refresh();});
        var area=listBounds();list=addDrawableChild(new RpgList(area.x(),area.y(),area.width(),area.height(),this::choose));footer();
        controls();if(ClientUiApi.supported()) refresh();else {message="Server UI unavailable. Local sheets remain under Tabletop Tools.";}
    }
    protected boolean paged() {return false;}
    protected UiLayout.Rect listBounds() {return listArea(frame,paged());}
    protected void controls() {}
    protected abstract void refresh();
    protected void clearRows() {rows.clear();actions.clear();filter();}
    protected void info(String name,String detail) {row(name,detail,false,detail,()->{});}
    protected void row(String name,String detail,boolean enabled,String reason,Runnable action) {
        String id=Integer.toString(rows.size());rows.add(new HubState.Choice(id,name,detail,enabled,reason,0));actions.put(id,action);filter();
    }
    protected void filter() {if(list!=null) list.rows(HubState.filter(rows,query));}
    private void choose(HubState.Choice choice) {
        if(busy) return;if(!choice.enabled()) {message=choice.reason();return;}
        actions.getOrDefault(choice.id(),()->{}).run();
    }
    protected void read(UiApiService.Operation op,JsonObject args,Consumer<JsonObject> result) {
        request(op,args,r->{if(r.code().equals("OK")) {fresh=true;message="Server state refreshed.";messageColor=RpgTheme.MUTED;result.accept(r.data());}else {fresh=false;clearRows();message=r.code()+": "+r.message()+" Refresh before retrying.";}});
    }
    protected void mutate(UiApiService.Operation op,JsonObject args,Consumer<JsonObject> result) {
        if(busy || !fresh) {message="Refresh authoritative state before changing it.";return;}
        if(ambiguousCreation && (op==UiApiService.Operation.LIBRARY_CREATE || op==UiApiService.Operation.LIBRARY_DUPLICATE)) {message="An earlier creation may have committed. Return to My Characters and reconcile the list before another creation.";return;}
        boolean creates=op==UiApiService.Operation.LIBRARY_CREATE || op==UiApiService.Operation.LIBRARY_DUPLICATE;
        if(creates) ambiguousCreation=true; // Retained even when resize/close invalidates the acknowledgment callback.
        fresh=false;clearRows();request(op,args,r->{
            if(r.code().equals("OK")) {if(creates) ambiguousCreation=false;fresh=true;message="Server confirmed the change.";messageColor=RpgTheme.SUCCESS;result.accept(r.data());}
            else {if(creates && !r.code().equals("TIMEOUT") && !r.code().equals("INVALID_REPLY")) ambiguousCreation=false;message=r.code()+": "+r.message()+" Refresh/reconcile before retrying; a timeout may have committed.";messageColor=RpgTheme.ERROR;}
        });
    }
    protected void confirm(String title,String explanation,Runnable action) {
        if(busy || !fresh) return;
        // Returning from the dialog reinitializes this screen. Wait for its new authoritative read before dispatch.
        client.setScreen(new RpgConfirmationScreen(this,title,explanation,()->afterRefresh=action));
    }
    private Runnable afterRefresh;
    protected void returnChoice(Screen destination,Runnable action) {client.setScreen(destination);if(destination instanceof ManagementScreen m) m.afterRefresh=action;else action.run();}
    @Override public void tick() {super.tick();if(afterRefresh!=null && !busy) {var action=afterRefresh;afterRefresh=null;if(fresh) {try {action.run();}catch(RuntimeException failure) {message=failure.getMessage()==null?"Action unavailable; refresh and review again.":failure.getMessage();messageColor=RpgTheme.ERROR;}}else message="Confirmation cancelled because the latest server read failed. Refresh and review again.";}}
    protected String self() {return client.player==null?"":client.player.getUuidAsString();}
}
