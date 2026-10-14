package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Retained local editing buffers; only complete server acknowledgements publish saved state. */
public final class CharacterWizardScreen extends RpgScreen {
    private final String character;
    private final CreatorDraftState state;
    private JsonObject validation;
    private CreatorFlow.Step step=CreatorFlow.Step.IDENTITY,displayed=step;
    private CharacterShell.Layout layout;
    private CharacterCanvas pane;
    private SectionRail rail;
    private CreatorSheetView sheet;
    private final Map<CreatorFlow.Step,Integer> positions=new EnumMap<>(CreatorFlow.Step.class);
    private final Map<CreatorFlow.Step,String> focus=new EnumMap<>(CreatorFlow.Step.class);
    private final Map<CreatorFlow.Step,Map<String,Integer>> cursors=new EnumMap<>(CreatorFlow.Step.class);
    private RpgButton nextButton,saveButton;
    private boolean fresh;
    private Runnable resumed;
    public CharacterWizardScreen(Screen parent,JsonObject snapshot){super("Create Character",parent);character=text(snapshot,"id");state=new CreatorDraftState(DraftReplies.snapshot(snapshot,character));}
    @Override protected int maximumWidth(){return 980;}
    @Override protected void init(){remember();super.init();layout=CharacterShell.layout(frame);pane=null;sheet=null;busy=false;fresh=false;
        rail=addDrawableChild(new SectionRail(layout.rail(),Arrays.stream(CreatorFlow.Step.values()).map(s->new SectionRail.Item(s.name(),s.title)).toList(),s->go(CreatorFlow.Step.valueOf(s)),28));
        footer();
        saveButton=footerAction("Save",0,()->{if(step==CreatorFlow.Step.REVIEW&&validation!=null&&!array(validation,"errors").isEmpty())go(CreatorFlow.errorStep(text(array(validation,"errors").get(0).getAsJsonObject(),"section")));else saveCurrent(this::build);});
        nextButton=footerAction(step==CreatorFlow.Step.REVIEW?"Finish":"Continue",1,()->{if(step==CreatorFlow.Step.REVIEW)finish();else go(CreatorFlow.Step.values()[Math.min(step.ordinal()+1,CreatorFlow.Step.values().length-1)]);});
        footerAction("Refresh",2,this::reload);build();reload();
    }
    private void remember(){if(pane!=null){positions.put(displayed,pane.position());focus.put(displayed,pane.focusedKey());cursors.put(displayed,pane.cursors());}if(sheet!=null)positions.put(displayed,sheet.position());}
    private void build(){remember();if(pane!=null)remove(pane);if(sheet!=null)remove(sheet);sheet=null;displayed=step;rail.selected(step.name());if(saveButton!=null)saveButton.setMessage(net.minecraft.text.Text.literal(step==CreatorFlow.Step.REVIEW&&validation!=null&&!array(validation,"errors").isEmpty()?"Fix issue":"Save"));if(nextButton!=null)nextButton.setMessage(net.minecraft.text.Text.literal(step==CreatorFlow.Step.REVIEW?"Finish":"Continue"));pane=addDrawableChild(new CharacterCanvas(layout.content()));
        switch(step){
            case IDENTITY->CreatorWorkspace.identity(pane,textRenderer,state,this::inspect,this::build,()->custom("species","Race / subrace"));
            case CLASS->CreatorWorkspace.clazz(pane,state,this::inspect,this::build,()->custom("characterClass","Class"),()->go(CreatorFlow.Step.PROFICIENCIES));
            case ABILITIES->{if(text(state.snapshot(),"profile").equals("srd_5_1")){AbilityWorkspace.build(pane,textRenderer,state,this::build,busy);}else pane.label("Imported profile: preserve its existing ability representation.",6,6,pane.getWidth()-12,RpgTheme.MUTED);}
            case REVIEW->CharacterSheetUi.build(pane,state.snapshot(),true,null,validation,null);
            default->pane.label("Loading "+step.title+" workspace…",6,6,pane.getWidth()-12,RpgTheme.MUTED);
        }
        if(pane!=null){pane.cursors(cursors.getOrDefault(step,Map.of()));pane.position(positions.getOrDefault(step,0));String key=focus.getOrDefault(step,"");if(!key.isEmpty()){pane.focus(key);setInitialFocus(pane);}}
    }
    private void inspect(FeatureReference.Entry e){client.setScreen(new FeatureInspectScreen(this,e));}
    private void custom(String field,String title){saveCurrent(()->client.setScreen(WizardEditors.customIdentity(this,state.snapshot(),field,title)));}
    private void reload(){if(busy)return;request(LIBRARY_GET,args("character",character),r->{if(r.code().equals("OK")){var current=DraftReplies.snapshot(r.data(),character);state.accept(current,false);validation=DraftReplies.validationFromSnapshot(current);fresh=true;if(resumed==null&&step!=CreatorFlow.Step.IDENTITY&&step!=CreatorFlow.Step.CLASS&&step!=CreatorFlow.Step.ABILITIES&&step!=CreatorFlow.Step.REVIEW)resumed=()->go(step);message=state.dirty()?"Unsaved edits retained. Save to confirm.":"Saved character loaded.";build();}else{fresh=false;message=r.message()+" Edits retained; refresh before saving.";}});}
    public void navigateFromChild(CreatorFlow.Step next){step=next;resumed=()->go(next);client.setScreen(this);}
    private void go(CreatorFlow.Step next){saveCurrent(()->{remember();step=next;build();Screen editor=switch(next){case PROFICIENCIES->new ProficiencyPickerScreen(this,state.snapshot());case EQUIPMENT->new InventoryWorkspace(this,state.snapshot(),new DashboardSource(true,character));case FEATURES->new WizardRecordsScreen(this,state.snapshot(),"features");case SPELLS->new WizardRecordsScreen(this,state.snapshot(),"spellbook");case DETAILS->new CharacterDetailsScreen(this,state.snapshot());default->null;};if(editor!=null)client.setScreen(editor);else if(next==CreatorFlow.Step.REVIEW&&validation==null)reload();});}
    private void saveCurrent(Runnable after){
        if(busy||!fresh){message="Refresh the saved character before saving.";return;}
        if(state.reviewRequired()){long reviewed=revision(state.snapshot());client.setScreen(new RpgConfirmationScreen(this,"Review concurrent edits","The saved character changed while you were editing. Your local values are retained. Review the saved summary before replacing these fields.",()->{if(revision(state.snapshot())==reviewed){state.reviewed();resumed=()->saveCurrent(after);}else message="Character changed again; review before saving.";}));return;}
        var snapshot=state.snapshot();
        if(!state.fields.get("name").equals(CreatorDraftState.savedField(snapshot,"name"))){save("basics",args("name",state.fields.get("name")),()->saveCurrent(after));return;}
        if(List.of("species","age","height","alignment").stream().anyMatch(k->!state.fields.get(k).equals(CreatorDraftState.savedField(snapshot,k)))){save("identity",state.identityData(List.of("species","age","height","alignment")),()->saveCurrent(after));return;}
        if(state.classDirty()){save("identity",state.identityData(List.of("characterClass")),()->saveCurrent(after));return;}
        if(state.scoresDirty()){try{var scores=new JsonObject();state.scoreValues().forEach(scores::addProperty);save("abilityAllocation",args("abilities",scores,"method",state.method.name()),()->saveCurrent(after));}catch(RuntimeException invalid){message=invalid.getMessage();messageColor=RpgTheme.ERROR;}return;}
        if(text(snapshot,"profile").equals("srd_5_1")&&!dev.tacticalcombat.uiapi.CreationRules.enrolled(snapshot.getAsJsonObject("sheet"))){save("creation",dev.tacticalcombat.uiapi.SheetSections.raw(snapshot.getAsJsonObject("sheet"),"creation"),()->saveCurrent(after));return;}
        after.run();
    }
    private void save(String section,JsonObject data,Runnable after){request(LIBRARY_UPDATE,args("character",character,"revision",revision(state.snapshot()),"section",section,"data",data),r->{if(r.code().equals("OK")){state.accept(DraftReplies.snapshot(r.data(),character),true);validation=null;message="Saved.";build();after.run();}else{fresh=false;message=r.message()+" Edits retained. Refresh and review before retrying.";}});}
    private void finish(){saveCurrent(()->{long expected=revision(state.snapshot());request(LIBRARY_VALIDATE,args("character",character),r->{if(!r.code().equals("OK"))return;validation=DraftReplies.validation(r.data());if(!array(validation,"errors").isEmpty()){step=CreatorFlow.Step.REVIEW;message="Correct validation issues before finishing.";build();return;}request(LIBRARY_FINALIZE,args("character",character,"revision",expected),reply->{if(!reply.code().equals("OK")){fresh=false;return;}state.accept(DraftReplies.snapshot(reply.data(),character),true);client.setScreen(new ChoiceScreen(parent,"Character finished",List.of("Keep in My Library","Submit Independent Copy to Campaign"),s->s,s->{if(s.startsWith("Keep"))client.setScreen(CharacterDashboardScreen.library(parent,character));else client.setScreen(new LibraryCharacterScreen(parent,character).submitOnOpen());}));});});});}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE&&pane!=null&&pane.dismissPopup())return true;if(super.keyPressed(key,scan,mods))return true;if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER&&pane!=null&&(CreatorDraftState.FIELDS.contains(pane.focusedKey())||CreatorDraftState.ABILITIES.contains(pane.focusedKey()))){saveCurrent(this::build);return true;}return false;}
    @Override public void tick(){super.tick();if(resumed!=null&&fresh&&!busy){var next=resumed;resumed=null;next.run();}}
    @Override protected void content(DrawContext g,int mx,int my,float delta){var s=layout.summary();RpgTheme.panel(g,s.x(),s.y(),s.width(),s.height(),RpgTheme.PANEL,true);if(layout.compactSummary()){RpgTheme.text(g,textRenderer,"Saved: "+text(state.snapshot(),"name")+" · "+CreatorDraftState.savedField(state.snapshot(),"species")+" · "+CreatorDraftState.savedField(state.snapshot(),"characterClass"),s.x()+5,s.y()+(s.height()-9)/2,s.width()-10,RpgTheme.GOLD);}else{if(client.player!=null)PlayerSkinDrawer.draw(g,client.player.getSkinTextures(),s.x()+s.width()/2-12,s.y()+7,24);paragraphs(g,savedSummary()+(state.dirty()?"\nUnsaved preview edits":""),s.x()+6,s.y()+36,s.width()-12,s.height()-42);}}
    private String savedSummary(){var snap=state.snapshot();var out=new StringBuilder("Saved character\n"+text(snap,"name")+"\n"+CreatorDraftState.savedField(snap,"species")+"\n"+CreatorDraftState.savedField(snap,"characterClass")+"\nLevel "+DashboardModel.xp(snap.getAsJsonObject("sheet")).level()+"\n\n");for(String key:CreatorDraftState.ABILITIES)out.append(key.toUpperCase(Locale.ROOT)).append("  ").append(CreatorDraftState.savedScore(snap,key)).append('\n');long completed=CreatorFlow.statuses(snap).values().stream().filter(WizardModel.Status::complete).count();out.append("\nSections complete: ").append(completed).append(" / ").append(CreatorFlow.Step.values().length);return out.toString();}
    @Override public void close(){if(busy){message="Waiting for server acknowledgement.";return;}if(state.dirty())client.setScreen(new RpgConfirmationScreen(this,"Discard unsaved edits?","Previously acknowledged fields remain saved.",()->client.setScreen(parent)));else super.close();}
}
