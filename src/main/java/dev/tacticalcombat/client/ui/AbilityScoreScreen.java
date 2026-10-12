package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class AbilityScoreScreen extends RpgScreen {
    private static final List<String> KEYS=List.of("str","dex","con","int","wis","cha");
    private final String character;
    private JsonObject snapshot,baseline;
    private final Map<String,String> values=new LinkedHashMap<>();
    private WizardModel.ScoreMethod method=WizardModel.ScoreMethod.STANDARD_ARRAY;
    private boolean dirty,fresh,saving,reviewRequired;
    private String confirmedSnapshot;
    public AbilityScoreScreen(Screen parent,JsonObject draft) {super("Ability scores · SRD 5.1 (2014)",parent);character=text(draft,"id");baseline=draft.deepCopy();var data=SrdAuthoring.data(draft.getAsJsonObject("sheet"));for(String k:KEYS) values.put(k,data.getAsJsonObject("abilities").get(k).getAsString());}
    @Override protected void init() {
        super.init();clearChildren();busy=false;fresh=false;int x=frame.x()+12,w=frame.width()-24;
        int methodW=(w-18)/4;WizardModel.ScoreMethod[] methods=WizardModel.ScoreMethod.values();for(int i=0;i<4;i++) {var m=methods[i];var b=button(switch(m){case STANDARD_ARRAY->"Standard";case POINT_BUY->"Point Buy";case MANUAL->"Manual";case EXTERNAL_ROLLS->"External rolls";},"",x+i*(methodW+6),frame.y()+36,methodW,20,()->{method=m;if(m==WizardModel.ScoreMethod.STANDARD_ARRAY) applyStandard();dirty=true;updateMessage();init();});b.selected(method==m);}
        int columns=frame.width()>=500?3:2,fieldW=(w-(columns-1)*6)/columns,top=frame.y()+76;
        for(int i=0;i<KEYS.size();i++) {String key=KEYS.get(i);int fx=x+(i%columns)*(fieldW+6),fy=top+(i/columns)*38;var input=addDrawableChild(new TextFieldWidget(textRenderer,fx,fy,fieldW,20,Text.literal(key)));input.setMaxLength(2);input.setText(values.get(key));input.setChangedListener(v->{values.put(key,v);dirty=true;updateMessage();});}
        footer();button("Reload","",frame.x()+frame.width()-202,frame.y()+frame.height()-30,76,20,this::reload);button("Save scores","",frame.x()+frame.width()-120,frame.y()+frame.height()-30,108,20,this::save);
        reload();updateMessage();
    }
    private void applyStandard() {for(int i=0;i<KEYS.size();i++) values.put(KEYS.get(i),Integer.toString(WizardModel.STANDARD_ARRAY.get(i)));}
    private Map<String,Integer> scores() {var scores=new LinkedHashMap<String,Integer>();for(String k:KEYS) try {scores.put(k,Integer.parseInt(values.get(k)));}catch(RuntimeException e) {throw new IllegalArgumentException("All six scores must be whole numbers");}return scores;}
    private void updateMessage() {try {var s=scores();WizardModel.validateScores(method,s);message=method==WizardModel.ScoreMethod.POINT_BUY?WizardModel.pointRemaining(s)+" of 27 points remain.":method==WizardModel.ScoreMethod.EXTERNAL_ROLLS?"Enter values rolled outside this wizard. No client roll is trusted.":"Valid "+method.name().toLowerCase(Locale.ROOT).replace('_',' ')+" values.";messageColor=RpgTheme.SUCCESS;}catch(RuntimeException e) {message=e.getMessage();messageColor=RpgTheme.ERROR;}}
    private void reload() {if(busy) return;request(LIBRARY_GET,args("character",character),r->{if(r.code().equals("OK")) {snapshot=r.data();fresh=true;reviewRequired=ManagementState.needsEditReview(baseline,snapshot);updateMessage();if(reviewRequired) message+=" Server revision changed; Save requires explicit comparison.";}else {fresh=false;message=r.code()+": "+r.message()+" Entered scores retained.";}});}
    private void save() {
        if(saving || busy || !fresh) {message="Reload authoritative state before saving; entered scores are retained.";return;}Map<String,Integer> scores;try {scores=scores();WizardModel.validateScores(method,scores);}catch(RuntimeException e){message=e.getMessage();messageColor=RpgTheme.ERROR;return;}
        if(reviewRequired) {String reviewed=snapshot.toString();client.setScreen(new RpgConfirmationScreen(this,"Reconcile score edit?","The library draft changed since this editor opened. Confirm applies these six effective scores to the newest server revision. Other combat fields remain as currently stored.\n\n"+scores,()->confirmedSnapshot=reviewed));return;}
        var next=snapshot.getAsJsonObject("sheet").deepCopy();var abilities=SrdAuthoring.data(next).getAsJsonObject("abilities");scores.forEach(abilities::addProperty);try {SrdCharacterCodec.parseSheet(next.toString());}catch(RuntimeException invalid){message=invalid.getMessage();return;}
        saving=true;request(LIBRARY_UPDATE,args("character",character,"revision",revision(snapshot),"section","combat","data",SrdAuthoring.data(next)),r->{saving=false;if(r.code().equals("OK")){dirty=false;client.setScreen(parent);}else {fresh=false;message=r.code()+": "+r.message()+" Scores retained; reload and reconcile.";}});
    }
    @Override public void tick(){super.tick();if(confirmedSnapshot!=null&&!busy){String reviewed=confirmedSnapshot;confirmedSnapshot=null;if(fresh&&snapshot.toString().equals(reviewed)){baseline=snapshot.deepCopy();reviewRequired=false;save();}else{message="Server changed again or refresh failed. Compare again before saving.";reviewRequired=true;}}}
    @Override protected void content(DrawContext g,int mx,int my,float delta) {
        int x=frame.x()+12,w=frame.width()-24,columns=frame.width()>=500?3:2,fieldW=(w-(columns-1)*6)/columns,top=frame.y()+64;
        for(int i=0;i<KEYS.size();i++) {String key=KEYS.get(i);int fx=x+(i%columns)*(fieldW+6),fy=top+(i/columns)*38;int score;try{score=Integer.parseInt(values.get(key));}catch(Exception e){score=10;}RpgTheme.text(g,textRenderer,key.toUpperCase(Locale.ROOT)+" · modifier "+String.format("%+d",WizardModel.modifier(score)),fx,fy,fieldW,RpgTheme.MUTED);}
        paragraphs(g,"Species bonuses are not auto-applied. These saved effective values feed the existing server SRD combat codec; inventory/features never rewrite them.",x,top+(KEYS.size()+columns-1)/columns*38+6,w,28);
    }
    @Override protected int statusWidth(){return frame.width()-300;}
    @Override public void close(){if(saving){message="Save pending; wait for acknowledgment.";return;}if(dirty) client.setScreen(new RpgConfirmationScreen(this,"Discard score edits?","Entered values have not been persisted.",()->client.setScreen(parent)));else super.close();}
}
