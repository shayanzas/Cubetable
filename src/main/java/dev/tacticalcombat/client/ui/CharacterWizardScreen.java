package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
import java.util.*;

/** Eleven-stage creation workspace. Mutations remain section-scoped server CAS operations. */
public final class CharacterWizardScreen extends RpgScreen {
    private final String character;
    private JsonObject draft;
    private WizardModel.Stage stage=WizardModel.Stage.METHOD;
    private RpgList stages;
    private UiLayout.Rect[] areas;
    private boolean fresh;
    public CharacterWizardScreen(Screen parent,JsonObject draft){super("Character creation",parent);this.draft=draft.deepCopy();character=text(draft,"id");}
    @Override protected void init(){
        super.init();clearChildren();busy=false;fresh=false;areas=WizardModel.layout(frame);
        stages=addDrawableChild(new RpgList(areas[0].x(),areas[0].y(),areas[0].width(),areas[0].height(),choice->{stage=WizardModel.Stage.values()[Integer.parseInt(choice.id())];buildActions();}));
        footer();button("‹ Previous","",frame.x()+84,frame.y()+frame.height()-30,82,20,()->move(-1));button("Next ›","",frame.x()+172,frame.y()+frame.height()-30,72,20,()->move(1));
        button("Save draft","",frame.x()+frame.width()-100,frame.y()+frame.height()-30,88,20,this::reload).hint("Confirms all server-acknowledged section saves; editors never save on every keystroke.");
        stageRows();buildActions();reload();
    }
    private void stageRows(){var states=WizardModel.statuses(draft);var rows=new ArrayList<HubState.Choice>();for(var s:WizardModel.Stage.values()){var status=states.get(s);rows.add(new HubState.Choice(Integer.toString(s.ordinal()),WizardModel.title(s),(status.complete()?"✓ ":"• ")+status.detail(),true,"Open stage",0));}stages.rows(rows);}
    private void reload(){if(busy)return;request(LIBRARY_GET,args("character",character),r->{if(r.code().equals("OK")){draft=r.data();fresh=true;message="Draft refreshed. All acknowledged section saves are persisted at revision "+revision(draft)+".";messageColor=RpgTheme.SUCCESS;stageRows();}else{fresh=false;message=r.code()+": "+r.message()+". Existing screen state retained.";}});}
    private void move(int d){int next=Math.clamp(stage.ordinal()+d,0,WizardModel.Stage.values().length-1);stage=WizardModel.Stage.values()[next];buildActions();}
    private void buildActions(){
        // Keep navigation widgets, recreate only stage action buttons by reinitializing safely without changing data.
        initStageButtons();
    }
    private final List<RpgButton> actionButtons=new ArrayList<>();
    private void initStageButtons(){for(var b:actionButtons)remove(b);actionButtons.clear();int x=areas[1].x()+8,y=areas[1].y()+42,w=areas[1].width()-16;
        switch(stage){
            case METHOD->{action(x,y,w,"Creation method",text(draft,"mode")+" · "+profile(text(draft,"profile")),()->{});action(x,y+28,w,"Guided support status",text(draft,"mode").equals("GUIDED")?"Partial: catalogs/calculators guide; server does not verify full 2014 build legality":"Manual: custom records are permitted but never bypass combat authority",()->{});}
            case BASICS->{action(x,y,w,"Edit canonical name","Required library name; one canonical field",()->client.setScreen(WizardEditors.canonicalName(this,draft)));action(x,y+28,w,"Identity and narrative","Age, height, appearance, biography, personality, ideals, bonds and flaws",()->client.setScreen(WizardEditors.identity(this,draft)));}
            case SPECIES,CLASS,BACKGROUND->{boolean guided=text(draft,"mode").equals("GUIDED")&&text(draft,"profile").equals("srd_5_1");action(x,y,w,guided?"Browse verified choices":"Edit custom identity record",guided?"Searchable SRD 5.1 guidance; effects remain informational":"Direct manual/custom record",()->client.setScreen(guided?new WizardChoiceScreen(this,draft,stage):WizardEditors.identity(this,draft)));if(stage==WizardModel.Stage.CLASS)action(x,y+28,w,"Progression and class records","Level, XP, class, feat, ASI and outstanding choice summaries",()->client.setScreen(WizardEditors.progression(this,draft)));}
            case ABILITIES->{boolean srd=text(draft,"profile").equals("srd_5_1");action(x,y,w,srd?"Edit all six ability scores":"Use existing profile editor",srd?"Standard array, 27-point buy, manual, or externally rolled values":"Generic and manual packs retain their established sheet representation",()->{if(srd)client.setScreen(new AbilityScoreScreen(this,draft));else client.setScreen(new InfoScreen(this,"Manual profile abilities","Existing profile representation","Open this source through the established Tabletop Tools editor; this wizard does not force non-SRD packs through SRD fields."));});}
            case PROFICIENCIES->action(x,y,w,"Edit proficiency records","Skills, saves, languages, tools and expertise; informational unless existing combat schema says otherwise",()->client.setScreen(WizardEditors.proficiencies(this,draft)));
            case EQUIPMENT->action(x,y,w,"Equipment and inventory","Structured manual records; never grants Minecraft items or recalculates combat",()->client.setScreen(new WizardRecordsScreen(this,draft,"inventory")));
            case FEATURES->action(x,y,w,"Features, feats and progression","Structured manual/informational records; no automatic feat or class feature",()->client.setScreen(new WizardRecordsScreen(this,draft,"features")));
            case SPELLS->action(x,y,w,"Custom spellbook","Search/filter-ready record list; verified standard catalog remains empty",()->client.setScreen(new WizardRecordsScreen(this,draft,"spellbook")));
            case REVIEW->{action(x,y,w,"Validate supported fields","Structural validation only; does not prove complete D&D legality",this::validate);action(x,y+28,w,"Finalize / submit to campaign","Finalization, campaign-copy confirmation and submission remain distinct server operations",()->client.setScreen(new LibraryCharacterScreen(this,character)));action(x,y+56,w,"Inspect saved source","Review exact persisted sheet and status",()->client.setScreen(new InfoScreen(this,text(draft,"name"),"Library source · "+text(draft,"stage"),draft.getAsJsonObject("sheet").toString())));}
        }
    }
    private void action(int x,int y,int w,String name,String detail,Runnable task){var b=button(name,detail,x,y,w,24,task);b.hint(detail);if(task.toString().isEmpty())b.active=false;actionButtons.add(b);}
    private void validate(){if(!fresh){message="Refresh the authoritative draft before validation.";return;}request(LIBRARY_VALIDATE,args("character",character),r->{if(r.code().equals("OK")){draft=r.data();message=ManagementState.validation(draft);messageColor=array(draft,"errors").isEmpty()?RpgTheme.SUCCESS:RpgTheme.ERROR;stageRows();}});}
    @Override protected void content(DrawContext g,int mx,int my,float delta){
        RpgTheme.panel(g,areas[1].x(),areas[1].y(),areas[1].width(),areas[1].height(),RpgTheme.PANEL,false);RpgTheme.text(g,textRenderer,WizardModel.title(stage),areas[1].x()+8,areas[1].y()+9,areas[1].width()-16,RpgTheme.GOLD);
        String guidance=WizardModel.statuses(draft).get(stage).detail();paragraphs(g,guidance,areas[1].x()+8,areas[1].y()+22,areas[1].width()-16,20);
        if(areas[2].width()>0){RpgTheme.panel(g,areas[2].x(),areas[2].y(),areas[2].width(),areas[2].height(),RpgTheme.PANEL,false);if(client.player!=null)PlayerSkinDrawer.draw(g,client.player.getSkinTextures(),areas[2].x()+areas[2].width()/2-16,areas[2].y()+12,32);JsonObject identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");int y=areas[2].y()+51;for(String line:List.of(text(draft,"name"),text(identity,"species"),text(identity,"characterClass"),"Level "+SheetSections.read(draft.getAsJsonObject("sheet"),"progression").get("recordedLevel").getAsInt(),text(draft,"stage"))){RpgTheme.text(g,textRenderer,line.isBlank()?"—":line,areas[2].x()+8,y,areas[2].width()-16,RpgTheme.TEXT);y+=13;}}
    }
    @Override protected int statusWidth(){return frame.width()-360;}
}
