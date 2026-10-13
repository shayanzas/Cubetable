package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
import java.util.*;

/** Eleven-stage creation workspace. Mutations remain section-scoped server CAS operations. */
public final class CharacterWizardScreen extends RpgScreen {
    private final String character;
    private JsonObject draft;
    private JsonObject validation;
    private WizardModel.Stage stage=WizardModel.Stage.BASICS;
    private RpgList stages,actions;
    private final List<RpgButton> stageTabs=new ArrayList<>();
    private final Map<String,Runnable> stageActions=new HashMap<>();
    private final List<HubState.Choice> choices=new ArrayList<>();
    private UiLayout.Rect[] areas;
    private boolean fresh;
    private String stagedName;private boolean nameDirty;private long nameRevision=-1,confirmedNameRevision=-1;
    private boolean nameFocused;private int nameCursor;
    private TextFieldWidget catalogSearch;
    private final Map<WizardModel.Stage,String> stageQueries=new EnumMap<>(WizardModel.Stage.class);
    private TextFieldWidget nameInput;private RpgButton nameSave;
    public CharacterWizardScreen(Screen parent,JsonObject draft){super("Character creation",parent);this.draft=draft.deepCopy();character=text(draft,"id");}
    @Override protected void init(){
        super.init();clearChildren();busy=false;fresh=false;areas=WizardModel.layout(frame);
        stageTabs.clear();int columns=frame.width()<560?4:1;int tw=(areas[0].width()-(columns-1)*3)/columns;
        for(var step:WizardModel.Stage.values()){if(step==WizardModel.Stage.METHOD){stageTabs.add(null);continue;}int i=step.ordinal()-1;var b=button(WizardModel.title(step),"",areas[0].x()+i%columns*(tw+3),areas[0].y()+i/columns*18,tw,17,()->{stage=step;buildActions();});b.hint(WizardModel.title(step));stageTabs.add(b);}
        actions=addDrawableChild(new RpgList(areas[1].x()+6,areas[1].y()+(areas[1].height()<80?18:36),areas[1].width()-12,Math.max(12,areas[1].height()-(areas[1].height()<80?22:42)),30,c->{if(!busy&&fresh)stageActions.getOrDefault(c.id(),()->{}).run();}));
        footer();int navWidth=(frame.width()-108)/3;button("‹ Previous","",frame.x()+84,frame.y()+frame.height()-30,navWidth,20,()->move(-1));button("Next ›","",frame.x()+90+navWidth,frame.y()+frame.height()-30,navWidth,20,()->move(1));
        button("Save draft","",frame.x()+96+navWidth*2,frame.y()+frame.height()-30,navWidth,20,()->{if(nameDirty)saveName();else reload();}).hint("Confirms all server-acknowledged section saves; editors never save on every keystroke.");
        stageRows();buildActions();reload();
    }
    private void stageRows(){var states=WizardModel.statuses(draft);for(var s:WizardModel.Stage.values()){var b=stageTabs.get(s.ordinal());if(b==null)continue;b.selected(s==stage);b.hint(WizardModel.title(s)+" · "+states.get(s).detail());}}
    private void reload(){if(busy)return;request(LIBRARY_GET,args("character",character),r->{if(r.code().equals("OK")){draft=DraftReplies.snapshot(r.data(),character);fresh=true;message="Draft refreshed. All acknowledged section saves are persisted at revision "+revision(draft)+".";messageColor=RpgTheme.SUCCESS;stageRows();buildActions();}else{fresh=false;message=r.code()+": "+r.message()+". Existing screen state retained.";}});}
    private void move(int d){int next=Math.clamp(stage.ordinal()+d,1,WizardModel.Stage.values().length-1);stage=WizardModel.Stage.values()[next];buildActions();}
    private void buildActions(){
        // Keep navigation widgets, recreate only stage action buttons by reinitializing safely without changing data.
        initStageButtons();stageRows();
    }
    private final List<RpgButton> actionButtons=new ArrayList<>();
    private void initStageButtons(){if(catalogSearch!=null){remove(catalogSearch);catalogSearch=null;}if(nameInput!=null){nameFocused=nameInput.isFocused();nameCursor=nameInput.getCursor();remove(nameInput);nameInput=null;}if(nameSave!=null){remove(nameSave);nameSave=null;}choices.clear();stageActions.clear();actions.visible=true;actions.setY(areas[1].y()+(areas[1].height()<80?18:36));actions.setHeight(Math.max(12,areas[1].height()-(areas[1].height()<80?22:42)));int x=areas[1].x()+8,y=areas[1].y()+42,w=areas[1].width()-16;
        switch(stage){
            case METHOD->{action(x,y,w,"Creation method",text(draft,"mode")+" · "+profile(text(draft,"profile")),()->{});action(x,y+28,w,"Guided support status",text(draft,"mode").equals("GUIDED")?"Partial: catalogs/calculators guide; server does not verify full 2014 build legality":"Manual: custom records are permitted but never bypass combat authority",()->{});}
            case BASICS->{if(stagedName==null){stagedName=text(draft,"name");nameRevision=revision(draft);}if(areas[1].height()>=90){nameInput=addDrawableChild(new TextFieldWidget(textRenderer,x,areas[1].y()+36,w-82,20,Text.literal("Character name")));nameInput.setMaxLength(80);nameInput.setText(stagedName);nameInput.setChangedListener(v->{stagedName=v;nameDirty=true;});if(nameFocused){setInitialFocus(nameInput);nameInput.setCursor(nameCursor,false);}nameSave=button("Save name","",x+w-78,areas[1].y()+36,78,20,this::saveName);actions.setY(areas[1].y()+60);actions.setHeight(Math.max(12,areas[1].height()-64));}else action(x,y,w,"Edit canonical name","Required library name",()->client.setScreen(WizardEditors.canonicalName(this,draft)));action(x,y+28,w,"Appearance & background story","Age, height, biography, personality, ideals, bonds and flaws",()->client.setScreen(WizardEditors.identity(this,draft)));}
            case SPECIES,CLASS,BACKGROUND->{boolean guided=text(draft,"mode").equals("GUIDED")&&text(draft,"profile").equals("srd_5_1");if(guided&&areas[1].height()>=90){catalogSearch=search(x,areas[1].y()+36,w,stageQueries.getOrDefault(stage,""),q->{stageQueries.put(stage,q);actions.rows(HubState.filter(choices,q));});actions.setY(areas[1].y()+60);actions.setHeight(Math.max(12,areas[1].height()-64));}if(guided)for(var entry:Srd2014Catalog.forStage(stage))action(x,y,w,entry.name(),entry.summary()+" "+entry.guidance()+" · informational/manual",()->recordChoice(entry));action(x,y,w,"Manual / custom details","Record a custom species, class, subclass or background",()->client.setScreen(WizardEditors.identity(this,draft)));if(stage==WizardModel.Stage.CLASS)action(x,y,w,"Progression and class records","Recorded level/XP/ASI choices; manual",()->client.setScreen(WizardEditors.progression(this,draft)));}
            case ABILITIES->{boolean srd=text(draft,"profile").equals("srd_5_1");action(x,y,w,srd?"Edit all six ability scores":"Use existing profile editor",srd?"Standard array, 27-point buy, manual, or externally rolled values":"Generic and manual packs retain their established sheet representation",()->{if(srd)client.setScreen(new AbilityScoreScreen(this,draft));else client.setScreen(new InfoScreen(this,"Manual profile abilities","Existing profile representation","Open this source through the established Tabletop Tools editor; this wizard does not force non-SRD packs through SRD fields."));});}
            case PROFICIENCIES->action(x,y,w,"Choose skills & saving throws","Checkbox choices; recorded/manual. Tools and languages stay available as custom entries.",()->client.setScreen(new ProficiencyPickerScreen(this,draft)));
            case EQUIPMENT->action(x,y,w,"Equipment and inventory","Structured manual records; never grants Minecraft items or recalculates combat",()->client.setScreen(new WizardRecordsScreen(this,draft,"inventory")));
            case FEATURES->action(x,y,w,"Features, feats and progression","Structured manual/informational records; no automatic feat or class feature",()->client.setScreen(new WizardRecordsScreen(this,draft,"features")));
            case SPELLS->action(x,y,w,"Custom spellbook","Search/filter-ready record list; verified standard catalog remains empty",()->client.setScreen(new WizardRecordsScreen(this,draft,"spellbook")));
            case REVIEW->{if(validation!=null)for(var error:array(validation,"errors")){var e=error.getAsJsonObject();action(x,y,w,"Fix: "+text(e,"section"),text(e,"message"),()->{stage=WizardModel.validationStage(text(e,"section"));buildActions();});}action(x,y+28,w,"Finish Character","Server validation, finalization, then optional independent campaign submission",this::finish);action(x,y+56,w,"Inspect saved source","Review exact persisted sheet and status",()->client.setScreen(new InfoScreen(this,text(draft,"name"),"Library source · "+text(draft,"stage"),draft.getAsJsonObject("sheet").toString())));}
        }
        actions.rows(HubState.filter(choices,catalogSearch==null?"":stageQueries.getOrDefault(stage,"")));
    }
    private void action(int x,int y,int w,String name,String detail,Runnable task){String id=Integer.toString(choices.size());choices.add(new HubState.Choice(id,name,detail,true,detail,0));stageActions.put(id,task);}
    private void saveName(){if(busy||!fresh)return;if(nameDirty&&nameRevision!=revision(draft)){long reviewed=revision(draft);client.setScreen(new RpgConfirmationScreen(this,"Review concurrent name change?","Saved name: "+text(draft,"name")+"\nYour staged name: "+stagedName+"\nConfirm applies the staged name to this reviewed server revision.",()->confirmedNameRevision=reviewed));return;}request(LIBRARY_UPDATE,args("character",character,"revision",revision(draft),"section","basics","data",args("name",stagedName)),r->{if(r.code().equals("OK")){draft=DraftReplies.snapshot(r.data(),character);nameDirty=false;nameRevision=revision(draft);validation=null;message="Name saved.";buildActions();}});}
    private void recordChoice(Srd2014Catalog.Choice entry){if(busy||!fresh)return;String field=switch(stage){case SPECIES->"species";case CLASS->"characterClass";case BACKGROUND->"background";default->throw new IllegalStateException();};var identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");identity.addProperty(field,entry.name());request(LIBRARY_UPDATE,args("character",character,"revision",revision(draft),"section","identity","data",identity),r->{if(r.code().equals("OK")){draft=DraftReplies.snapshot(r.data(),character);validation=null;message=entry.name()+" recorded. Benefits remain manual/informational.";buildActions();}});}
    @Override public void tick(){super.tick();if(confirmedNameRevision>=0&&!busy){long reviewed=confirmedNameRevision;confirmedNameRevision=-1;if(fresh&&revision(draft)==reviewed){nameRevision=reviewed;saveName();}else message="Server changed again; review the name before saving.";}}
    @Override public void close(){if(busy){message="Wait for the server reply before leaving.";return;}if(nameDirty)client.setScreen(new RpgConfirmationScreen(this,"Discard unsaved name?","The staged name is not saved yet.",()->client.setScreen(parent)));else super.close();}
    private void finish(){if(busy||!fresh)return;if(nameDirty){stage=WizardModel.Stage.BASICS;buildActions();message="Save the staged name before finishing.";return;}long expected=revision(draft);request(LIBRARY_VALIDATE,args("character",character),r->{if(!r.code().equals("OK"))return;validation=DraftReplies.validation(r.data());if(!array(validation,"errors").isEmpty()){message=ManagementState.validation(validation);messageColor=RpgTheme.ERROR;stage=WizardModel.Stage.REVIEW;buildActions();return;}request(LIBRARY_FINALIZE,args("character",character,"revision",expected),reply->{if(!reply.code().equals("OK")){message=reply.code()+": "+reply.message()+". Draft retained; refresh before retrying.";return;}draft=DraftReplies.snapshot(reply.data(),character);client.setScreen(new ChoiceScreen(parent,"Character finished",List.of("Keep in My Library","Submit Independent Copy to Campaign"),s->s,s->{if(s.startsWith("Keep"))client.setScreen(CharacterDashboardScreen.library(parent,character));else client.setScreen(new LibraryCharacterScreen(parent,character).submitOnOpen());}));});});}
    private void validate(){if(!fresh){message="Refresh the authoritative draft before validation.";return;}request(LIBRARY_VALIDATE,args("character",character),r->{if(r.code().equals("OK")){validation=DraftReplies.validation(r.data());message=ManagementState.validation(validation);messageColor=array(validation,"errors").isEmpty()?RpgTheme.SUCCESS:RpgTheme.ERROR;stageRows();}});}
    @Override protected void content(DrawContext g,int mx,int my,float delta){
        RpgTheme.panel(g,areas[1].x(),areas[1].y(),areas[1].width(),areas[1].height(),RpgTheme.PANEL,false);RpgTheme.text(g,textRenderer,WizardModel.title(stage),areas[1].x()+8,areas[1].y()+9,areas[1].width()-16,RpgTheme.GOLD);
        String guidance=WizardModel.statuses(draft).get(stage).detail();if(areas[1].height()>=80)paragraphs(g,guidance,areas[1].x()+8,areas[1].y()+22,areas[1].width()-16,20);
        if(areas[2].width()>0){RpgTheme.panel(g,areas[2].x(),areas[2].y(),areas[2].width(),areas[2].height(),RpgTheme.PANEL,false);if(client.player!=null)PlayerSkinDrawer.draw(g,client.player.getSkinTextures(),areas[2].x()+areas[2].width()/2-16,areas[2].y()+12,32);JsonObject identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");int y=areas[2].y()+51;for(String line:List.of(text(draft,"name"),text(identity,"species"),text(identity,"characterClass"),"Level "+SheetSections.read(draft.getAsJsonObject("sheet"),"progression").get("recordedLevel").getAsInt(),text(draft,"stage"))){RpgTheme.text(g,textRenderer,line.isBlank()?"—":line,areas[2].x()+8,y,areas[2].width()-16,RpgTheme.TEXT);y+=13;}}
    }
    @Override protected int statusWidth(){return frame.width()-360;}
}
