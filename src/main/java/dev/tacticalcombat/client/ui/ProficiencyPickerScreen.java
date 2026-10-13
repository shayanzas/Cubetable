package dev.tacticalcombat.client.ui;
import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
/** Staged choice lists; records never apply proficiency to combat formulas. */
public final class ProficiencyPickerScreen extends RpgScreen {
    private final JsonObject draft,section;
    private String category="skills";
    private RpgList list;
    private boolean dirty;
    public ProficiencyPickerScreen(Screen parent,JsonObject draft){super("Skills & Proficiencies · recorded",parent);this.draft=draft.deepCopy();section=SheetSections.read(draft.getAsJsonObject("sheet"),"proficiencies");}
    @Override protected void init(){super.init();clearChildren();busy=false;int x=frame.x()+12,w=frame.width()-24;
        String[] categories={"skills","saves","expertise"};for(int i=0;i<3;i++){String c=categories[i];var b=button(c.equals("saves")?"Saving throws":c.equals("skills")?"Skills":"Expertise","",x+i*(w+6)/3,frame.y()+36,(w-12)/3,20,()->{category=c;display();});b.hint("Recorded choices; class/background choice counts require manual review.");}
        list=addDrawableChild(new RpgList(x,frame.y()+62,w,Math.max(38,frame.height()-112),this::toggle));footer();button("Save choices","",frame.x()+frame.width()-116,frame.y()+frame.height()-30,104,20,this::save);button("Custom entries","",frame.x()+84,frame.y()+frame.height()-30,96,20,()->{if(dirty){message="Save checkbox choices before opening custom entries.";return;}client.setScreen(WizardEditors.proficiencies(parent,draft));});display();}
    private void display(){var rows=new ArrayList<HubState.Choice>();var names=category.equals("saves")?List.of("str","dex","con","int","wis","cha"):DashboardModel.SKILLS.keySet().stream().sorted().toList();var chosen=DashboardModel.strings(section,category);var identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");String clazz=text(identity,"characterClass");var allowed=Srd2014Catalog.classSkills(clazz);boolean acolyte=text(identity,"background").equals("Acolyte");String guidance=allowed.isEmpty()?"Manual/custom choices":clazz+": choose "+Srd2014Catalog.classSkillCount(clazz)+" class skills. "+(acolyte?"Acolyte: Insight and Religion. ":"")+"Duplicate and multiclass choices require DM review.";message=guidance;for(String name:names){String source=category.equals("skills")&&allowed.contains(name)?"Class choice":category.equals("skills")&&acolyte&&Set.of("Insight","Religion").contains(name)?"Background":"Manual/custom";rows.add(new HubState.Choice(name,(chosen.contains(name)?"☑ ":"☐ ")+name,source+" · recorded/manual · "+(category.equals("saves")?"saving throw":DashboardModel.SKILLS.get(name).toUpperCase(Locale.ROOT)),true,guidance+" No combat bonus is applied.",0));}list.rows(rows);}
    private void toggle(HubState.Choice row){if(busy)return;var values=new LinkedHashSet<>(DashboardModel.strings(section,category));if(!values.remove(row.id()))values.add(row.id());var a=new JsonArray();values.forEach(a::add);section.add(category,a);dirty=true;display();}
    private void save(){if(busy)return;request(LIBRARY_UPDATE,args("character",text(draft,"id"),"revision",revision(draft),"section","proficiencies","data",section),r->{if(r.code().equals("OK")){dirty=false;client.setScreen(parent);}else message=r.code()+": "+r.message()+". Choices retained; reopen after reviewing a concurrent change.";});}
    @Override public void close(){if(busy)return;if(dirty)client.setScreen(new RpgConfirmationScreen(this,"Discard proficiency choices?","Unsaved choices will be discarded.",()->client.setScreen(parent)));else super.close();}
}
