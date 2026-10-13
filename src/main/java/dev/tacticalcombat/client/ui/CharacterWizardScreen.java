package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.gui.Element;
import net.minecraft.text.Text;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Unified creator: presentation is local, all saved character mutations are server CAS. */
public final class CharacterWizardScreen extends RpgScreen {
    private final String character;
    private JsonObject draft,validation;
    private CreatorFlow.Step step=CreatorFlow.Step.IDENTITY;
    private CreatorFlow.Step displayedStep=step;
    private CreatorFlow.Layout layout;
    private final List<Element> contentWidgets=new ArrayList<>();
    private final Map<CreatorFlow.Step,String> queries=new EnumMap<>(CreatorFlow.Step.class);
    private final Map<CreatorFlow.Step,Integer> gridPositions=new EnumMap<>(CreatorFlow.Step.class),detailPositions=new EnumMap<>(CreatorFlow.Step.class);
    private final Map<CreatorFlow.Step,String> inspected=new EnumMap<>(CreatorFlow.Step.class);
    private final List<RpgButton> navigation=new ArrayList<>();
    private CreatorChoiceGrid grid;
    private RpgTextBox detail;
    private CreatorSheetView sheetView;
    private TextFieldWidget nameInput,searchInput;
    private final Map<CreatorFlow.Step,Integer> searchCursors=new EnumMap<>(CreatorFlow.Step.class),gridFocus=new EnumMap<>(CreatorFlow.Step.class);
    private final Set<CreatorFlow.Step> searchFocused=EnumSet.noneOf(CreatorFlow.Step.class),gridFocused=EnumSet.noneOf(CreatorFlow.Step.class);
    private String stagedName;
    private boolean dirtyName,fresh,nameFocused;
    private int nameCursor;
    private long nameRevision;
    private Runnable afterName,pendingChoice;
    public CharacterWizardScreen(Screen parent,JsonObject snapshot){super("Create Character",parent);character=text(snapshot,"id");draft=DraftReplies.snapshot(snapshot,character);stagedName=text(draft,"name");nameRevision=revision(draft);}
    @Override protected void init(){
        rememberView();super.init();clearChildren();contentWidgets.clear();navigation.clear();grid=null;detail=null;sheetView=null;nameInput=null;searchInput=null;busy=false;fresh=false;
        layout=CreatorFlow.layout(frame);var n=layout.navigation();
        if(frame.height()<200){navigation.add(button(step.title,"",n.x(),n.y(),n.width(),15,this::sectionMenu));}
        else {int columns=layout.columns(),w=(n.width()-(columns-1)*3)/columns;for(var s:CreatorFlow.Step.values()){int i=s.ordinal();var b=button(s.title,"",n.x()+i%columns*(w+3),n.y()+i/columns*16,w,15,()->go(s));b.hint(s.title);navigation.add(b);}}
        footer();int w=(frame.width()-108)/3,y=frame.y()+frame.height()-30;
        button("Previous","",frame.x()+84,y,w,20,()->move(-1));
        button("Continue","",frame.x()+90+w,y,w,20,()->move(1));
        button("Refresh","",frame.x()+96+w*2,y,w,20,this::reload).hint("Check saved character; keeps unsaved name and current section.");
        build();reload();
    }
    private void sectionMenu(){client.setScreen(new ChoiceScreen(this,"Character sections",Arrays.stream(CreatorFlow.Step.values()).map(Enum::name).toList(),s->CreatorFlow.Step.valueOf(s).title,s->pendingChoice=()->go(CreatorFlow.Step.valueOf(s))));}
    private void rememberView(){if(nameInput!=null){nameFocused=nameInput.isFocused();nameCursor=nameInput.getCursor();}if(searchInput!=null){searchCursors.put(displayedStep,searchInput.getCursor());if(searchInput.isFocused())searchFocused.add(displayedStep);else searchFocused.remove(displayedStep);}
        if(grid!=null){gridPositions.put(displayedStep,grid.position());gridFocus.put(displayedStep,grid.focusedIndex());if(grid.isFocused())gridFocused.add(displayedStep);else gridFocused.remove(displayedStep);}if(detail!=null)detailPositions.put(displayedStep,detail.position());if(sheetView!=null)detailPositions.put(displayedStep,sheetView.position());}
    private <T extends net.minecraft.client.gui.widget.ClickableWidget>T widget(T w){contentWidgets.add(w);return addDrawableChild(w);}
    private RpgButton action(String title,int x,int y,int w,int h,Runnable r){var b=new RpgButton(x,y,w,h,title,"",r);return widget(b);}
    private void build(){
        rememberView();displayedStep=step;for(var child:contentWidgets)remove(child);contentWidgets.clear();grid=null;detail=null;sheetView=null;nameInput=null;searchInput=null;
        for(int i=0;i<navigation.size();i++){navigation.get(i).selected(frame.height()<200||i==step.ordinal());if(frame.height()<200)navigation.get(i).setMessage(Text.literal(step.title));}
        var c=layout.content();int x=c.x()+6,y=c.y()+5,w=c.width()-12,h=c.height()-10;
        switch(step){
            case IDENTITY,CLASS,BACKGROUND->catalog(x,y,w,h);
            case ABILITIES->section(x,y,w,h,"Shape your six ability scores","Use the standard array, 27-point buy, or enter manual/external rolls. Species bonuses are not added automatically.","Choose ability scores",()->{if(text(draft,"profile").equals("srd_5_1"))open(new AbilityScoreScreen(this,draft));else open(new InfoScreen(this,"Manual character","Existing profile","Your imported tabletop profile keeps its existing ability representation."));});
            case PROFICIENCIES->section(x,y,w,h,"Skills & Proficiencies","Choose skills, saving throws and expertise in one dedicated menu. Tools and languages remain custom records. Class and background limits are guidance, not automatic grants.","Choose proficiencies",()->open(new ProficiencyPickerScreen(this,draft)));
            case EQUIPMENT->section(x,y,w,h,"Pack your equipment","Search the supported weapons and armor, inspect their details, and record quantity and equipped state. Sheet equipment does not grant physical Minecraft items.","Open equipment picker",()->open(new WizardRecordsScreen(this,draft,"inventory")));
            case FEATURES->section(x,y,w,h,"Features & Feats · manual","Record features and feats with your DM. Rules automation is not implemented; entries do not apply combat effects.","Edit manual features",()->open(new WizardRecordsScreen(this,draft,"features")));
            case SPELLS->section(x,y,w,h,"Spellbook · manual","Record and search custom spells. Standard spell catalog and casting automation are not implemented yet.","Edit manual spellbook",()->open(new WizardRecordsScreen(this,draft,"spellbook")));
            case DETAILS->section(x,y,w,h,"Character Details · optional","Your appearance and story are yours to tell. Add biography, personality, ideals, bonds and flaws without repeating race/class selections.","Write character details",()->open(new CharacterDetailsScreen(this,draft)));
            case OVERVIEW,REVIEW->review(x,y,w,h);
        }
    }
    private void section(int x,int y,int w,int h,String title,String text,String label,Runnable r){
        widget(new RpgTextBox(x,y,w,Math.max(16,h-25),title+"\n\n"+text));action(label,x,y+h-21,w,20,r).icon(switch(step){case ABILITIES->"str";case PROFICIENCIES->"skills";case EQUIPMENT->"equipment";case FEATURES->"feats";case SPELLS->"spells";case DETAILS->"inspect";default->"";});
    }
    private List<Srd2014Catalog.Choice> entries(){return switch(step){case IDENTITY->Srd2014Catalog.SPECIES;case CLASS->Srd2014Catalog.CLASSES;case BACKGROUND->List.of(Srd2014Catalog.BACKGROUNDS.getFirst(),new Srd2014Catalog.Choice("custom","Custom background","Create your own background with your DM.","No predefined benefits are granted. Use Custom details to record your choices."));default->List.of();};}
    private String identityKey(){return switch(step){case IDENTITY->"species";case CLASS->"characterClass";case BACKGROUND->"background";default->throw new IllegalStateException();};}
    private JsonObject identity(){return SheetSections.read(draft.getAsJsonObject("sheet"),"identity");}
    private void catalog(int x,int y,int w,int h){
        boolean tiny=h<85;int top=y;
        String recorded=text(identity(),identityKey());String baseRecorded=step==CreatorFlow.Step.IDENTITY?CreatorFlow.baseRace(recorded):recorded;
        var initial=entries().stream().filter(e->e.id().equals(inspected.get(step))).findFirst().orElseGet(()->entries().stream().filter(e->e.name().equals(baseRecorded)).findFirst().orElse(entries().getFirst()));
        var geometry=CreatorFlow.catalogLayout(layout.content(),step,!CreatorFlow.subraces(initial.name()).isEmpty());
        if(step==CreatorFlow.Step.IDENTITY){
            nameInput=widget(new TextFieldWidget(textRenderer,geometry.name().x(),geometry.name().y(),geometry.name().width(),geometry.name().height(),Text.literal("Character name")));nameInput.setMaxLength(80);nameInput.setText(stagedName);nameInput.setPlaceholder(Text.literal("Character name"));nameInput.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Enter or Continue saves your name. Other choices save only after the server confirms them.")));nameInput.setChangedListener(v->{stagedName=v;dirtyName=!v.equals(text(draft,"name"));});
            if(nameFocused){setInitialFocus(nameInput);nameInput.setCursor(Math.min(nameCursor,stagedName.length()),false);}top+=tiny?16:22;
        }
        String saved=text(identity(),identityKey()),base=step==CreatorFlow.Step.IDENTITY?CreatorFlow.baseRace(saved):saved;
        var list=entries();String inspect=inspected.get(step);var choice=list.stream().filter(e->e.id().equals(inspect)).findFirst().orElseGet(()->list.stream().filter(e->e.name().equals(base)).findFirst().orElse(list.getFirst()));
        inspected.put(step,choice.id());
        if(step==CreatorFlow.Step.IDENTITY&&!CreatorFlow.subraces(choice.name()).isEmpty()&&!tiny){
            var subs=CreatorFlow.subraces(choice.name());int bw=(w-4)/(subs.size()+1);action("Base "+choice.name(),x,top,bw,15,()->saveIdentity("species",choice.name()));
            for(int i=0;i<subs.size();i++){String sub=subs.get(i);action(sub,x+(i+1)*(bw+3),top,bw,15,()->saveIdentity("species",CreatorFlow.recordedRace(choice.name(),sub)));}top+=18;
        }
        int remaining=y+h-top;
        if(tiny){
            var bounds=geometry.grid();grid=widget(new CreatorChoiceGrid(bounds,e->{inspected.put(step,e.id());if(step==CreatorFlow.Step.IDENTITY&&!CreatorFlow.subraces(e.name()).isEmpty()){var race=entries().stream().filter(a->a.id().equals(e.id())).findFirst().orElseThrow();String sub=CreatorFlow.subraces(race.name()).getFirst();client.setScreen(new ChoiceScreen(this,race.name()+" · choose subrace",List.of(race.name(),sub),s->s,s->pendingChoice=()->saveIdentity("species",s.equals(race.name())?s:CreatorFlow.recordedRace(race.name(),s))));}else{var selected=entries().stream().filter(a->a.id().equals(e.id())).findFirst().orElseThrow();client.setScreen(new RpgConfirmationScreen(this,"Choose "+selected.name()+"?",CreatorLore.details(step,selected),()->pendingChoice=()->saveIdentity(identityKey(),selected.name())));}}));
            grid.entries(list.stream().map(e->new CreatorChoiceGrid.Entry(e.id(),e.name(),step==CreatorFlow.Step.IDENTITY?"":e.id())).toList(),choice.id());grid.position(gridPositions.getOrDefault(step,0));grid.focusedIndex(gridFocus.getOrDefault(step,0));if(gridFocused.contains(step))setInitialFocus(grid);
            action("Custom details",x,y+h-16,w,16,()->open(WizardEditors.customIdentity(this,draft,identityKey(),step==CreatorFlow.Step.IDENTITY?"Race / subrace":step==CreatorFlow.Step.CLASS?"Class":"Background")));return;
        }
        int left=Math.max(100,w*48/100),right=w-left-6;
        if(step!=CreatorFlow.Step.IDENTITY){TextFieldWidget search=widget(new TextFieldWidget(textRenderer,geometry.search().x(),geometry.search().y(),geometry.search().width(),geometry.search().height(),Text.literal("Search choices")));search.setText(queries.getOrDefault(step,""));search.setPlaceholder(Text.literal("Search…"));search.setChangedListener(q->{queries.put(step,q);filterGrid();});searchInput=search;if(searchFocused.contains(step)){setInitialFocus(search);search.setCursor(searchCursors.getOrDefault(step,search.getText().length()),false);}}
        int searchHeight=step==CreatorFlow.Step.IDENTITY?0:21;
        grid=widget(new CreatorChoiceGrid(geometry.grid(),e->{inspected.put(step,e.id());detailPositions.put(step,0);build();}));
        filterGrid();grid.position(gridPositions.getOrDefault(step,0));grid.focusedIndex(gridFocus.getOrDefault(step,0));if(gridFocused.contains(step))setInitialFocus(grid);
        detail=widget(new RpgTextBox(geometry.details().x(),geometry.details().y(),geometry.details().width(),geometry.details().height(),"Saved: "+saved+"\n\n"+CreatorLore.details(step,choice)));detail.position(detailPositions.getOrDefault(step,0));
        action("Choose "+choice.name(),x+left+6,y+h-20,right,20,()->{if(choice.id().equals("custom"))open(WizardEditors.customIdentity(this,draft,identityKey(),step==CreatorFlow.Step.IDENTITY?"Race / subrace":step==CreatorFlow.Step.CLASS?"Class":"Background"));else saveIdentity(identityKey(),choice.name());}).hint("Save this choice. Benefits remain manually recorded.");
        action("Custom details",x,y+h-20,left,20,()->open(WizardEditors.customIdentity(this,draft,identityKey(),step==CreatorFlow.Step.IDENTITY?"Race / subrace":step==CreatorFlow.Step.CLASS?"Class":"Background")));
    }
    private void filterGrid(){if(grid==null)return;String q=queries.getOrDefault(step,"").toLowerCase(Locale.ROOT);grid.entries(entries().stream().filter(e->(e.name()+" "+e.summary()).toLowerCase(Locale.ROOT).contains(q)).map(e->new CreatorChoiceGrid.Entry(e.id(),e.name(),step==CreatorFlow.Step.IDENTITY?"":e.id())).toList(),inspected.getOrDefault(step,""));}
    private void saveIdentity(String field,String value){
        if(busy||!fresh){message="Refresh your saved character before choosing.";return;}
        var section=identity();section.addProperty(field,value);
        request(LIBRARY_UPDATE,args("character",character,"revision",revision(draft),"section","identity","data",section),r->{
            if(r.code().equals("OK")){draft=DraftReplies.snapshot(r.data(),character);nameRevision=revision(draft);validation=null;message=value+" saved. Benefits remain manual.";build();}
            else{fresh=false;message=r.message()+" Your draft is retained; refresh to review changes.";}
        });
    }
    private void reload(){if(busy)return;request(LIBRARY_GET,args("character",character),r->{if(r.code().equals("OK")){var next=DraftReplies.snapshot(r.data(),character);if(!dirtyName){stagedName=text(next,"name");nameRevision=revision(next);}draft=next;fresh=true;message=dirtyName?"Your unsaved name is retained.":"Your character is saved in My Characters.";build();}else{fresh=false;message=r.message()+" Current draft retained.";}});}
    private void go(CreatorFlow.Step next){if(busy)return;if(dirtyName){saveName(()->{step=next;build();});return;}rememberView();step=next;build();}
    private void move(int d){go(CreatorFlow.Step.values()[Math.clamp(step.ordinal()+d,0,CreatorFlow.Step.values().length-1)]);}
    private void open(Screen screen){if(busy||!fresh)return;if(dirtyName){saveName(()->client.setScreen(screen));return;}client.setScreen(screen);}
    private void saveName(Runnable after){
        if(!fresh||busy)return;
        if(nameRevision!=revision(draft)){long reviewed=revision(draft);client.setScreen(new RpgConfirmationScreen(this,"Review name change?","Saved name: "+text(draft,"name")+"\nYour name: "+stagedName+"\nSave your name after reviewing this change?",()->{if(revision(draft)==reviewed){nameRevision=reviewed;afterName=after;}else message="Character changed again; review before saving.";}));return;}
        request(LIBRARY_UPDATE,args("character",character,"revision",revision(draft),"section","basics","data",args("name",stagedName)),r->{if(r.code().equals("OK")){draft=DraftReplies.snapshot(r.data(),character);dirtyName=false;nameRevision=revision(draft);validation=null;message="Name saved.";after.run();}else{fresh=false;message=r.message()+" Your name is retained; refresh before saving.";}});
    }
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER&&nameInput!=null&&nameInput.isFocused()){saveName(this::build);return true;}return super.keyPressed(key,scan,mods);}
    @Override public void tick(){super.tick();if(pendingChoice!=null&&!busy&&fresh){var action=pendingChoice;pendingChoice=null;action.run();}if(afterName!=null&&!busy&&fresh){var next=afterName;afterName=null;saveName(next);}}
    private void review(int x,int y,int w,int h){
        int footer=step==CreatorFlow.Step.REVIEW?24:0;
        var sections=CreatorSheet.sections(draft,validation);if(step==CreatorFlow.Step.OVERVIEW)sections=sections.subList(0,4);
        sheetView=widget(new CreatorSheetView(new UiLayout.Rect(x,y,w,Math.max(18,h-footer)),sections));sheetView.position(detailPositions.getOrDefault(step,0));
        if(step==CreatorFlow.Step.REVIEW){int bw=validation==null||array(validation,"errors").isEmpty()?w:(w-5)/2;
            action("Finish Character",x,y+h-20,bw,20,this::finish);
            if(bw<w)action("Fix first issue",x+bw+5,y+h-20,bw,20,()->go(CreatorFlow.errorStep(text(array(validation,"errors").get(0).getAsJsonObject(),"section"))));}
    }
    private void finish(){
        if(!fresh||busy)return;if(dirtyName){saveName(this::finish);return;}long expected=revision(draft);
        request(LIBRARY_VALIDATE,args("character",character),r->{if(!r.code().equals("OK"))return;validation=DraftReplies.validation(r.data());if(!array(validation,"errors").isEmpty()){step=CreatorFlow.Step.REVIEW;message="Please correct the highlighted issues before finishing.";build();return;}
            request(LIBRARY_FINALIZE,args("character",character,"revision",expected),reply->{if(!reply.code().equals("OK")){fresh=false;message=reply.message()+" Your draft is retained; refresh before retrying.";return;}draft=DraftReplies.snapshot(reply.data(),character);client.setScreen(new ChoiceScreen(parent,"Character finished",List.of("Keep in My Library","Submit Independent Copy to Campaign"),s->s,s->{if(s.startsWith("Keep"))client.setScreen(CharacterDashboardScreen.library(parent,character));else client.setScreen(new LibraryCharacterScreen(parent,character).submitOnOpen());}));});
        });
    }
    @Override protected void content(DrawContext g,int mx,int my,float delta){
        var c=layout.content();if(layout.summary().width()==0){var i=identity();RpgTheme.text(g,textRenderer,stagedName+(dirtyName?" *":"")+" · "+text(i,"species")+" · "+text(i,"characterClass"),c.x()+3,c.y()-12,c.width()-6,RpgTheme.GOLD);}
        RpgTheme.panel(g,c.x(),c.y(),c.width(),c.height(),0xFF171A20,true);
        var s=layout.summary();if(s.width()>0){RpgTheme.panel(g,s.x(),s.y(),s.width(),s.height(),RpgTheme.PANEL,true);if(client.player!=null)PlayerSkinDrawer.draw(g,client.player.getSkinTextures(),s.x()+s.width()/2-12,s.y()+8,24);paragraphs(g,CreatorSheet.summary(draft,false,null),s.x()+7,s.y()+37,s.width()-14,s.height()-42);}
    }
    @Override public void close(){if(busy){message="Waiting for the server…";return;}if(dirtyName)client.setScreen(new RpgConfirmationScreen(this,"Leave without saving the name?","Your other acknowledged choices stay saved.",()->client.setScreen(parent)));else super.close();}
}
