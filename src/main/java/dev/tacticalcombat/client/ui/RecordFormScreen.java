package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.UiApiService;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.*;

/** Grouped staged fields; explicit reload updates CAS only, retaining unsaved text even after rejected saves. */
public final class RecordFormScreen extends RpgScreen {
    public record Field(String key,String label,String initial,int limit) {}
    private final List<Field> fields;
    private final Map<String,String> values=new LinkedHashMap<>();
    private final UiApiService.Operation readOp,saveOp;
    private final JsonObject readArgs;
    private final BiFunction<JsonObject,Map<String,String>,JsonObject> build;
    private final Consumer<JsonObject> saved;
    private JsonObject snapshot;
    private final String explanation;
    private int page;
    private boolean fresh,dirty;
    private JsonObject baseline;
    private boolean reviewRequired;
    private String confirmedSnapshot;
    private boolean ambiguousCreation;
    private boolean saving;
    public RecordFormScreen(Screen parent,String title,UiApiService.Operation readOp,JsonObject readArgs,List<Field> fields,
                            BiFunction<JsonObject,Map<String,String>,JsonObject> build,UiApiService.Operation saveOp,Consumer<JsonObject> saved,String explanation) {
        super(title,parent);this.readOp=readOp;this.readArgs=readArgs.deepCopy();this.fields=List.copyOf(fields);this.build=build;this.saveOp=saveOp;this.saved=saved;this.explanation=explanation;
        for(var field:fields) values.put(field.key(),field.initial());
    }
    public static int capacity(int frameHeight) {return ManagementState.formCapacity(frameHeight);}
    public RecordFormScreen expected(JsonObject initial) {baseline=initial.deepCopy();return this;}
    @Override protected void init() {
        super.init();clearChildren();busy=false;fresh=false;saving=false;
        int x=frame.x()+12,w=frame.width()-24,n=capacity(frame.height());page=Math.min(page,(fields.size()-1)/n);
        for(int i=page*n;i<Math.min(fields.size(),(page+1)*n);i++) {
            var f=fields.get(i);int y=frame.y()+54+(i-page*n)*36;
            var input=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal(f.label())));input.setMaxLength(f.limit());input.setText(values.get(f.key()));
            input.setChangedListener(v->{values.put(f.key(),v);dirty=true;});
        }
        int bottom=frame.y()+frame.height()-60;
        button("‹ Fields","",x,bottom,(w-12)/3,20,()->{if(!busy && page>0) {page--;init();}}).active=page>0;
        button("Reload","",x+(w-12)/3+6,bottom,(w-12)/3,20,this::reload).hint("Fetch the latest revision without discarding staged text. Review concurrent changes before saving.");
        button("Fields ›","",x+2*((w-12)/3+6),bottom,(w-12)/3,20,()->{if(!busy && (page+1)*n<fields.size()) {page++;init();}}).active=(page+1)*n<fields.size();
        footer();button("Save section","",frame.x()+frame.width()-120,frame.y()+frame.height()-30,108,20,this::save);
        reload();
    }
    private void reload() {
        if(busy) return;fresh=false;request(readOp,readArgs,r->{if(r.code().equals("OK")) {
            snapshot=r.data().deepCopy();if(baseline==null) baseline=snapshot.deepCopy();
            reviewRequired=ManagementState.needsEditReview(baseline,snapshot);
            fresh=true;message=explanation+(reviewRequired?" Server changed: Save opens a comparison; staged text retained.":dirty?" Staged text retained.":"");
        }else message=r.code()+": "+r.message()+" Unsaved text retained.";});
    }
    private void save() {
        if(ambiguousCreation) {message="Campaign creation may have committed. Return to Campaigns and reconcile its list before starting another creation.";return;}
        if(busy || !fresh) {message="Reload and reconcile before saving. Unsaved text is retained.";return;}
        if(reviewRequired) {
            String reviewed=snapshot.toString();client.setScreen(new RpgConfirmationScreen(this,"Reconcile concurrent edits?","The server revision changed. Compare the latest server sheet with your staged fields below. Confirm explicitly applies the staged section to this reviewed server version; Cancel preserves your text.\n\nStaged fields:\n"+values+"\n\nLatest server sheet:\n"+(snapshot.has("sheet")?snapshot.get("sheet").toString():snapshot.toString()),()->confirmedSnapshot=reviewed));return;
        }
        performSave();
    }
    @Override public void tick() {
        super.tick();if(confirmedSnapshot!=null && !busy) {
            String reviewed=confirmedSnapshot;confirmedSnapshot=null;
            if(fresh && snapshot.toString().equals(reviewed)) performSave();else {message="Server changed again or refresh failed. Compare again before saving.";reviewRequired=true;}
        }
    }
    private void performSave() {
        JsonObject a;try {a=build.apply(snapshot,Map.copyOf(values));}catch(RuntimeException invalid) {message=invalid.getMessage();messageColor=RpgTheme.ERROR;return;}
        fresh=false;saving=true;if(saveOp==UiApiService.Operation.CAMPAIGN_CREATE) ambiguousCreation=true;
        request(saveOp,a,r->{saving=false;if(r.code().equals("OK")) {ambiguousCreation=false;dirty=false;saved.accept(r.data());}else {if(!r.code().equals("TIMEOUT") && !r.code().equals("INVALID_REPLY")) ambiguousCreation=false;message=r.code()+": "+r.message()+" Unsaved text retained. Reload and review server state before retrying.";}});
    }
    @Override protected void content(DrawContext g,int mx,int my,float delta) {
        int n=capacity(frame.height());for(int i=page*n;i<Math.min(fields.size(),(page+1)*n);i++) RpgTheme.text(g,textRenderer,fields.get(i).label(),frame.x()+12,frame.y()+41+(i-page*n)*36,frame.width()-24,RpgTheme.MUTED);
    }
    @Override protected int statusWidth() {return frame.width()-226;}
    @Override public void close() {
        if(saving) {message="Save is pending; wait for acknowledgment before leaving.";return;}
        if(dirty) client.setScreen(new RpgConfirmationScreen(this,"Discard staged edits?","This discards only your unsaved form text. Server records are unchanged.",()->client.setScreen(parent)));else super.close();
    }
}
