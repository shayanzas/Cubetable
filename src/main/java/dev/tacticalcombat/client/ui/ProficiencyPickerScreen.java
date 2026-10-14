package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Class choices have their own provenance; legacy/background/custom records are retained separately. */
public final class ProficiencyPickerScreen extends RpgScreen {
    private final DashboardSource source;
    private JsonObject snapshot,baseline,choices;
    private CharacterCanvas pane;
    private boolean dirty,fresh;
    private final DeferredCreatorAction resumed=new DeferredCreatorAction();
    public ProficiencyPickerScreen(Screen parent,JsonObject draft){this(parent,draft,new DashboardSource(true,text(draft,"id")));}
    public ProficiencyPickerScreen(Screen parent,JsonObject draft,DashboardSource source){super("Proficiencies",parent);this.source=source;snapshot=draft.deepCopy();baseline=snapshot.deepCopy();choices=SheetSections.raw(draft.getAsJsonObject("sheet"),"creation");}
    @Override protected boolean hasUnsavedChanges(){return dirty;}
    @Override protected int maximumWidth(){return 980;}
    @Override protected void init(){super.init();clearChildren();pane=null;busy=false;fresh=false;footer();footerAction(parent instanceof CharacterWizardScreen?"Save Draft":"Save Choices",0,this::save);footerAction("Refresh",2,this::reload);creatorContinue(CreatorFlow.Step.PROFICIENCIES,this::save);wizardSaveRail(CreatorFlow.Step.PROFICIENCIES,this::save);build();reload();}
    private void reload(){if(busy)return;fresh=false;request(source.read(),source.readArgs(),r->{if(r.code().equals("OK")){snapshot=SnapshotReplies.checked(source,r.data());if(!dirty||choices.equals(SheetSections.raw(snapshot.getAsJsonObject("sheet"),"creation"))){choices=SheetSections.raw(snapshot.getAsJsonObject("sheet"),"creation");dirty=false;baseline=snapshot.deepCopy();}fresh=true;build();message="Historical grants remain separate for DM review.";}else message=r.message()+" Choices retained.";});}
    private void toggle(String category,String key){if(busy||!fresh)return;try{choices=ProficiencyChoices.toggle(snapshot.getAsJsonObject("sheet"),choices,category,key);dirty=true;build();}catch(IllegalArgumentException invalid){message=invalid.getMessage();}}
    private void build(){
        int offset=pane==null?0:pane.position();String focus=pane==null?"":pane.focusedKey();if(pane!=null)remove(pane);
        pane=addDrawableChild(new CharacterCanvas(new UiLayout.Rect(frame.x()+12,frame.y()+36,frame.width()-24,Math.max(12,frame.height()-74-footerExtra()))));
        var view=snapshot.deepCopy();view.add("sheet",SheetSections.update(view.getAsJsonObject("sheet"),"creation",choices));var sheet=view.getAsJsonObject("sheet");String clazz=CreationRules.clazz(sheet);boolean standard=!CreationRules.skills(clazz).isEmpty();var legacy=SheetSections.raw(sheet,"proficiencies");var effective=SheetSections.read(sheet,"proficiencies");
        int x=6,w=pane.getWidth()-12,columns=w>=460?3:w>=300?2:1,cw=(w-(columns-1)*8)/columns,y=6,count=array(choices,"skills").size(),limit=CreationRules.skillCount(clazz),expertLimit=CreationRules.expertiseCount(clazz,CreationRules.level(sheet));
        pane.panel("Class Skill Choices",x,y,w,64);pane.label(standard?clazz+" — "+count+" of "+limit+" selected":"Custom class — record choices with your DM",x+7,y+24,w-14,RpgTheme.GOLD);pane.label(standard?(count<limit?(limit-count)+" more choice"+(limit-count==1?"":"s")+" required":"Class skill choices complete"):"Historical grants stay separate",x+7,y+38,w-14,RpgTheme.TEXT);pane.progress(x+7,y+53,w-14,limit==0?1:(double)count/limit);y+=72;
        pane.boldLabel("Skills",x,y,w,RpgTheme.GOLD);y+=17;pane.label("✓ Selected · ○ Available · dimmed: unavailable",x,y,w,RpgTheme.MUTED);y+=19;
        var skills=ProficiencyModel.checks(view,false);for(int i=0;i<skills.size();i++){var c=skills.get(i);String name=c.name();int sx=x+i%columns*(cw+8),sy=y+i/columns*25;boolean selected=ProficiencyModel.contains(choices,"skills",name),eligible=CreationRules.skills(clazz).contains(name),retained=ProficiencyModel.contains(legacy,"skills",name);
            var b=pane.control("skill:"+name,new RpgButton(sx,sy,cw,21,(selected||retained?"✓ ":"○ ")+name+" "+(ProficiencyModel.hasScores(view)?signed(c.total()):"—"),"",()->toggle("skills",name)).selected(selected).hint(c.breakdown()+"\n"+(eligible&&!selected&&count>=limit?"Maximum class selections reached; remove an existing choice first.":eligible?clazz+" eligible class choice":retained?"Retained historical grant; DM review":"Not in this class's starting skill list")));b.active=!busy&&fresh&&(selected||eligible&&count<limit);
        }y+=((skills.size()+columns-1)/columns)*25+9;
        if(expertLimit>0||!array(choices,"expertise").isEmpty()){
            pane.boldLabel("Expertise — "+array(choices,"expertise").size()+" of "+expertLimit+" selected",x,y,w,RpgTheme.GOLD);y+=18;
            String explanation="Expertise doubles your proficiency bonus for an eligible skill or tool.";pane.wrapped(explanation,x,y,w,RpgTheme.TEXT);y+=textRenderer.getTextHandler().wrapLines(explanation,w,net.minecraft.text.Style.EMPTY).size()*12+7;
            var eligible=ProficiencyChoices.expertiseOptions(sheet);var options=new ArrayList<>(eligible);for(String retained:DashboardModel.strings(choices,"expertise"))if(!options.contains(retained))options.add(retained);
            if(options.isEmpty()){pane.label("Select proficient skills to reveal eligible Expertise choices.",x,y,w,RpgTheme.MUTED);y+=18;}
            for(int i=0;i<options.size();i++){String name=options.get(i);boolean selected=ProficiencyModel.contains(choices,"expertise",name);var b=pane.control("expert:"+name,new RpgButton(x+i%columns*(cw+8),y+i/columns*25,cw,21,name,"",()->toggle("expertise",name)).selected(selected).accessible("Expertise in "+name).hint(eligible.contains(name)?"Eligible proficiency; counts toward your "+expertLimit+" Expertise choices.":"Retained former class choice. Remove it to reconcile this class; historical grants remain separate."));b.active=!busy&&fresh&&(selected||eligible.contains(name)&&array(choices,"expertise").size()<expertLimit);}
            y+=((options.size()+columns-1)/columns)*25+8;
        }
        pane.boldLabel("Tools & Languages",x,y,w,RpgTheme.GOLD);y+=18;
        if(clazz.equals("Rogue")){pane.wrapped("Rogue is your class. Thief is a subclass at level 3. Thieves' tools are a class tool proficiency and an eligible Expertise choice.",x,y,w,RpgTheme.MUTED);y+=textRenderer.getTextHandler().wrapLines("Rogue is your class. Thief is a subclass at level 3. Thieves' tools are a class tool proficiency and an eligible Expertise choice.",w,net.minecraft.text.Style.EMPTY).size()*12+6;}
        for(String category:List.of("tools","languages")){String recorded=(category.equals("tools")?"Tools: ":"Languages: ")+String.join(", ",DashboardModel.strings(effective,category));pane.wrapped(recorded,x,y,w,RpgTheme.TEXT);y+=textRenderer.getTextHandler().wrapLines(recorded,w,net.minecraft.text.Style.EMPTY).size()*12+5;}
        pane.wrapped("Recorded historical, species and custom grants are retained; additional automatic choices are deferred.",x,y,w,RpgTheme.MUTED);y+=textRenderer.getTextHandler().wrapLines("Recorded historical, species and custom grants are retained; additional automatic choices are deferred.",w,net.minecraft.text.Style.EMPTY).size()*12+8;
        if(!standard){var manual=pane.control("manual:grants",new RpgButton(x,y,w,19,"Record Custom Grants — DM Review","",()->save(()->client.setScreen(DashboardEditors.proficiencies(this,snapshot,source)))));manual.active=fresh&&!busy;y+=26;}
        for(String warning:CreationRules.warnings(sheet)){pane.wrapped(warning,x,y,w,RpgTheme.MUTED);y+=textRenderer.getTextHandler().wrapLines(warning,w,net.minecraft.text.Style.EMPTY).size()*12+4;}
        for(String error:CreationRules.choiceErrors(sheet,false)){pane.wrapped(error,x,y,w,RpgTheme.ERROR);y+=textRenderer.getTextHandler().wrapLines(error,w,net.minecraft.text.Style.EMPTY).size()*12+4;}
        y+=8;pane.boldLabel("Saving Throws",x,y,w,RpgTheme.GOLD);y+=20;var saves=ProficiencyModel.checks(view,true);for(int i=0;i<saves.size();i++){var c=saves.get(i);int sx=x+i%columns*(cw+8),sy=y+i/columns*24;String label=c.name()+" "+signed(c.total());if(c.proficient())pane.boldLabel(label,sx,sy,cw,RpgTheme.GOLD);else pane.label(label,sx,sy,cw,RpgTheme.TEXT);pane.tooltip(sx,sy,cw,20,c.breakdown()+"\n"+(CreationRules.saves(clazz).contains(c.ability())?"Granted by "+clazz:"Recorded grants retained"));}
        pane.position(offset);if(!focus.isEmpty()){pane.focus(focus);setInitialFocus(pane);}
    }
    private void save(){save(()->{});}
    private void save(Runnable after){if(!fresh||busy){message="Refresh before saving; choices retained.";return;}var preview=SheetSections.update(snapshot.getAsJsonObject("sheet"),"creation",choices);var errors=CreationRules.choiceErrors(preview,false);if(!errors.isEmpty()){message=String.join(" ",errors);messageColor=RpgTheme.ERROR;return;}if(!dirty){message="Draft saved.";after.run();return;}if(needsEditReview(baseline,snapshot)){long expected=revision(snapshot);client.setScreen(new RpgConfirmationScreen(this,"Review changed choices?","Saved choices: "+SheetSections.raw(snapshot.getAsJsonObject("sheet"),"creation")+"\nYour choices: "+choices+"\nConfirm applies these choices to the reviewed revision. Cancel keeps editing.",()->{if(revision(snapshot)==expected){resumed.queueReviewed(expected,()->revision(snapshot),()->{baseline=snapshot.deepCopy();save(after);},()->message="Character changed again; review before starting a new save.");}}).onCancel(()->{resumed.clear();message="Save cancelled. Choices retained.";}));return;}fresh=false;saveDraftRequest(source.edit(),source.sectionArgs(snapshot,"creation",choices),next->{snapshot=SnapshotReplies.checked(source,next);dirty=false;fresh=true;baseline=snapshot.deepCopy();message="Draft saved.";build();return true;},after);}

    @Override public void tick(){super.tick();if(resumed.pending()&&!busy&&fresh){resumed.run();}}
    @Override public void close(){if(busy)return;if(dirty)client.setScreen(new RpgConfirmationScreen(this,"Discard unsaved choices?","Acknowledged choices remain saved.",()->returnFromWizard(CreatorFlow.Step.PROFICIENCIES)));else if(parent instanceof CharacterWizardScreen)returnFromWizard(CreatorFlow.Step.PROFICIENCIES);else super.close();}
    private static String signed(int n){return (n>=0?"+":"")+n;}
}
