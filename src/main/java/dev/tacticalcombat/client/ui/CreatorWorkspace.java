package dev.tacticalcombat.client.ui;

import java.util.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Shared shell's real Identity/Class controls, with individually inspectable traits. */
public final class CreatorWorkspace {
    private CreatorWorkspace() {}
    private static TextFieldWidget field(CharacterCanvas pane,TextRenderer font,CreatorDraftState state,String key,String label,int x,int y,int w,int limit){pane.label(label,x,y,w,RpgTheme.MUTED);var input=new TextFieldWidget(font,x,y+11,w,17,Text.literal(label));input.setMaxLength(limit);input.setText(state.fields.get(key));input.setChangedListener(v->state.fields.put(key,v));return pane.control(key,input);}
    public static void identity(CharacterCanvas pane,TextRenderer font,CreatorDraftState state,java.util.function.Consumer<FeatureReference.Entry> inspect,Runnable rebuild,Runnable custom){
        int w=pane.getWidth()-12,x=6;
        field(pane,font,state,"name","Character name",x,2,w,80);
        int fw=(w-8)/3;field(pane,font,state,"age","Age",x,34,fw,128);field(pane,font,state,"height","Height",x+fw+4,34,fw,128);field(pane,font,state,"alignment","Alignment",x+2*(fw+4),34,fw,128);
        String value=state.fields.get("species"),base=CreatorFlow.baseRace(value);
        var race=Srd2014Catalog.SPECIES.stream().filter(e->e.name().equals(base)).findFirst().orElse(Srd2014Catalog.SPECIES.getFirst());
        boolean stacked=w<440;int left=stacked?w:(w-6)/2,right=stacked?w:w-left-6;
        int gridTop=76,columns=Math.max(1,left/58),gridHeight=((9+columns-1)/columns)*44;
        var grid=pane.control("races",new CreatorChoiceGrid(new UiLayout.Rect(x,gridTop,left,gridHeight),e->{state.fields.put("species",e.name());rebuild.run();}));grid.entries(Srd2014Catalog.SPECIES.stream().map(e->new CreatorChoiceGrid.Entry(e.id(),e.name(),"")).toList(),Srd2014Catalog.SPECIES.stream().anyMatch(e->e.name().equals(base))?race.id():"");
        int dx=stacked?x:x+left+6,dy=stacked?gridTop+gridHeight+32:gridTop;
        pane.label("Preview: "+value,dx,dy,right,RpgTheme.GOLD);dy+=18;
        boolean known=Srd2014Catalog.SPECIES.stream().anyMatch(e->e.name().equals(base));
        var description=new FeatureReference.Entry("srd51:race:"+race.id(),(known?race.name():value)+" overview","D&D 5e SRD 5.1 (2014)",0,"","Reference","","","","","INFORMATIONAL",known?CreatorLore.details(CreatorFlow.Step.IDENTITY,race):"Custom race: recorded manually. Select a standard race or use Custom Race to edit this record.");feature(pane,description,dx,dy,right,inspect);dy+=21;
        for(String sub:CreatorFlow.subraces(base)){String selected=CreatorFlow.recordedRace(race.name(),sub);pane.control("subrace:"+sub,new RpgButton(dx,dy,right,18,sub,"",()->{state.fields.put("species",selected);rebuild.run();}).selected(value.equals(selected)).hint("Inspect/save this subrace; traits remain reference only."));dy+=21;}
        String sub=value.contains(" — ")?value.substring(value.indexOf(" — ")+3):CreatorFlow.subraces(base).contains(value)?value:"";
        pane.label("Race traits · reference",dx,dy,right,RpgTheme.GOLD);dy+=17;
        for(var entry:FeatureReference.raceTraits(base,sub)){feature(pane,entry,dx,dy,right,inspect);dy+=21;}
        pane.control("customRace",new RpgButton(x,gridTop+gridHeight+5,left,18,"Custom Race","",custom));
    }
    public static void clazz(CharacterCanvas pane,CreatorDraftState state,java.util.function.Consumer<FeatureReference.Entry> inspect,Runnable rebuild,Runnable custom){
        int x=6,w=pane.getWidth()-12;boolean stacked=w<440;int left=stacked?w:(w-6)/2,right=stacked?w:w-left-6;
        int columns=Math.max(1,left/58),gridHeight=((12+columns-1)/columns)*44;
        var selected=Srd2014Catalog.CLASSES.stream().filter(c->c.name().equals(state.fields.get("characterClass"))).findFirst().orElse(Srd2014Catalog.CLASSES.getFirst());
        var grid=pane.control("classes",new CreatorChoiceGrid(new UiLayout.Rect(x,2,left,gridHeight),e->{state.fields.put("characterClass",e.name());rebuild.run();}));grid.entries(Srd2014Catalog.CLASSES.stream().map(e->new CreatorChoiceGrid.Entry(e.id(),e.name(),e.id())).toList(),Srd2014Catalog.CLASSES.stream().anyMatch(c->c.name().equals(state.fields.get("characterClass")))?selected.id():"");
        pane.control("customClass",new RpgButton(x,gridHeight+8,left,18,"Custom Class","",custom));
        int dx=stacked?x:x+left+6,y=stacked?gridHeight+36:2;
        String current=state.fields.get("characterClass");if(!current.isBlank()&&!current.equals(selected.name())){pane.label("Custom Class: "+current,dx,y,right,RpgTheme.GOLD);pane.label("Recorded manually; select a standard class to inspect its features.",dx,y+18,right,RpgTheme.MUTED);return;}
        pane.label("Preview: "+selected.name(),dx,y,right,RpgTheme.GOLD);y+=18;
        feature(pane,new FeatureReference.Entry("srd51:class:"+selected.id()+":overview",selected.name()+" overview","D&D 5e SRD 5.1 (2014)",1,"","Reference","","","","","INFORMATIONAL",selected.summary()+". "+selected.guidance()),dx,y,right,inspect);y+=21;
        String[] summary=selected.summary().split(" · ");pane.label("Hit Die: "+summary[0],dx,y,right,RpgTheme.TEXT);y+=14;pane.label("Primary: "+(summary.length>1?summary[1]:"See SRD"),dx,y,right,RpgTheme.TEXT);y+=17;
        pane.label("Proficiencies",dx,y,right,RpgTheme.GOLD);y+=15;
        String prof=FeatureReference.classProficiencies(selected.id())+"\nSaves: "+selected.guidance().split(" · ")[0];
        for(String line:prof.split("\n")){pane.control("prof:"+line,new RpgButton(dx,y,right,18,line,"",()->inspect.accept(new FeatureReference.Entry("srd51:"+selected.id()+":proficiencies","Proficiencies", "D&D 5e SRD 5.1 (2014)",1,"","Recorded/manual","","","",prof,"INFORMATIONAL",prof))).hint(line));y+=21;}
        String skills="Choose "+Srd2014Catalog.classSkillCount(selected.name())+" skills";pane.control("skills",new RpgButton(dx,y,right,18,skills,"",()->inspect.accept(new FeatureReference.Entry("srd51:"+selected.id()+":skills",skills,"D&D 5e SRD 5.1 (2014)",1,"","Recorded/manual","","","","","INFORMATIONAL",String.join(", ",new TreeSet<>(Srd2014Catalog.classSkills(selected.name())))+". Choices are recorded in Proficiencies; no bonus is granted automatically."))));y+=25;
        int level=-1;for(var entry:FeatureReference.classFeatures(selected.id())){if(entry.level()!=level){level=entry.level();pane.label("Level "+level,dx,y,right,RpgTheme.GOLD);y+=16;}feature(pane,entry,dx,y,right,inspect);y+=21;}
    }
    public static void feature(CharacterCanvas pane,FeatureReference.Entry entry,int x,int y,int width,java.util.function.Consumer<FeatureReference.Entry> inspect){pane.control(entry.id(),new RpgButton(x,y,width,18,entry.name(),"",()->inspect.accept(entry)).icon("features").hint(entry.tooltip()));}
}
