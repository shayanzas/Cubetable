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
        int x=6,w=pane.getWidth()-12;
        field(pane,font,state,"name","Character Name",x,0,w,80);
        int fw=(w-8)/3;field(pane,font,state,"age","Age",x,30,fw,128);field(pane,font,state,"height","Height",x+fw+4,30,fw,128);
        int alignmentX=x+2*(fw+4);pane.label("Alignment",alignmentX,30,fw,RpgTheme.MUTED);
        var geometry=IdentityLayout.layout(pane.getWidth(),pane.getHeight(),font::getWidth);
        String recorded=state.fields.get("species"),base=CreatorFlow.baseRace(recorded),sub=IdentityModel.subrace(recorded);
        pane.control("races",new IdentityRaceGrid(geometry,base,race->{IdentityModel.race(state,race);rebuild.run();}));
        pane.control("customRace",new RpgButton(geometry.grid().x(),geometry.grid().y()+geometry.grid().height()+6,geometry.grid().width(),18,"Custom Race","",custom));
        int dx=geometry.information().x(),dy=geometry.information().y(),dw=geometry.information().width();
        pane.label("Preview",dx,dy,dw,RpgTheme.GOLD);dy+=16;
        if(recorded.isBlank()){pane.wrapped("Select a race to view its description, subrace options, and racial traits.",dx,dy,dw,RpgTheme.TEXT);}else{
        pane.label(recorded,dx,dy,dw,RpgTheme.TEXT);dy+=14;
        String intro=recorded.isBlank()?"Choose a race to explore its heritage and traits.":IdentityModel.intro(base);
        for(var line:font.getTextHandler().wrapLines(intro,dw,net.minecraft.text.Style.EMPTY)){pane.label(line.getString(),dx,dy,dw,RpgTheme.TEXT);dy+=12;}
        dy+=5;
        var variants=CreatorFlow.subraces(base);
        if(!variants.isEmpty()){
            dy+=8;pane.label("Subrace",dx,dy,dw,RpgTheme.GOLD);dy+=17;

            pane.control("subrace:base",new RpgButton(dx,dy,dw,19,base+" — Base Traits","",()->{IdentityModel.subrace(state,"Base Traits");rebuild.run();}).selected(sub.equals("Base Traits")).hint("Intentional base-only selection. Saves as a draft with parent traits only; standard guided Finish requires a supported subrace. No subrace traits are substituted."));dy+=23;
            for(String variant:variants){pane.control("subrace:"+variant,new RpgButton(dx,dy,dw,19,variant,"",()->{IdentityModel.subrace(state,variant);rebuild.run();}).selected(sub.equals(variant)).hint(variant+" · 2014 SRD choice. Its extra traits appear below; no bonus is granted automatically."));dy+=23;}
        }
        if(!IdentityModel.subraceIntro(sub).isBlank()){String description=IdentityModel.subraceIntro(sub);pane.wrapped(description,dx,dy,dw,RpgTheme.TEXT);dy+=net.minecraft.client.MinecraftClient.getInstance().textRenderer.getTextHandler().wrapLines(description,dw,net.minecraft.text.Style.EMPTY).size()*12+5;}
        dy+=8;pane.label("Racial Traits",dx,dy,dw,RpgTheme.GOLD);dy+=17;
        for(var trait:FeatureReference.raceTraits(base,sub)){pane.control(trait.id(),new RpgButton(dx,dy,dw,19,trait.name(),"",()->inspect.accept(trait)).hint(trait.tooltip()));dy+=23;}
        if(!recorded.isBlank())for(var line:font.getTextHandler().wrapLines(IdentityModel.completeness(recorded),dw,net.minecraft.text.Style.EMPTY)){pane.label(line.getString(),dx,dy,dw,RpgTheme.MUTED);dy+=12;}
        }
        // Popup renders last and receives input first, so its nine choices overlay other controls safely.
        pane.control("alignment",new AlignmentDropdown(alignmentX,41,fw,()->state.fields.get("alignment"),v->IdentityModel.alignment(state,v),()->new UiLayout.Rect(pane.getX(),pane.getY(),pane.getWidth(),pane.getHeight())));
    }
    public static void clazz(CharacterCanvas pane,CreatorDraftState state,java.util.function.Consumer<FeatureReference.Entry> inspect,Runnable rebuild,Runnable custom,Runnable proficiencies,java.util.function.Consumer<String> chooseClass){
        int x=6,w=pane.getWidth()-12;boolean stacked=w<600;int left=stacked?w:Math.max(320,w*55/100),right=stacked?w:w-left-6;
        int columns=4,cellWidth=Math.max(36,left/4),gridHeight=3*62;
        var selected=Srd2014Catalog.CLASSES.stream().filter(c->c.name().equals(state.fields.get("characterClass"))).findFirst().orElse(Srd2014Catalog.CLASSES.getFirst());
        var grid=pane.control("classes",new CreatorChoiceGrid(new UiLayout.Rect(x,2,left,gridHeight),e->chooseClass.accept(e.name()),cellWidth,62,40));grid.entries(Srd2014Catalog.CLASSES.stream().map(e->new CreatorChoiceGrid.Entry(e.id(),e.name(),e.id())).toList(),Srd2014Catalog.CLASSES.stream().anyMatch(c->c.name().equals(state.fields.get("characterClass")))?selected.id():"");
        pane.control("customClass",new RpgButton(x,gridHeight+8,left,18,"Custom Class","",custom));
        int dx=stacked?x:x+left+6,y=stacked?gridHeight+36:2;
        String current=state.fields.get("characterClass");if(!current.isBlank()&&!current.equals(selected.name())){pane.label("Custom Class: "+current,dx,y,right,RpgTheme.GOLD);pane.label("Recorded manually; select a standard class to inspect its features.",dx,y+18,right,RpgTheme.MUTED);return;}
        pane.label("Preview: "+selected.name(),dx,y,right,RpgTheme.GOLD);y+=16;String savedClass=ManagementState.text(dev.tacticalcombat.uiapi.SheetSections.read(state.snapshot().getAsJsonObject("sheet"),"identity"),"characterClass");pane.label(current.equals(savedClass)?"Saved selection":"Unsaved selection · Save to confirm",dx,y,right,RpgTheme.MUTED);y+=18;
        String overview=FeatureReference.classIntro(selected.id());pane.wrapped(overview,dx,y,right,RpgTheme.TEXT);y+=net.minecraft.client.MinecraftClient.getInstance().textRenderer.getTextHandler().wrapLines(overview,right,net.minecraft.text.Style.EMPTY).size()*12+8;
        String[] summary=selected.summary().split(" · ");pane.label("Hit Die: "+summary[0],dx,y,right,RpgTheme.TEXT);y+=14;pane.label("Primary: "+(summary.length>1?summary[1]:"See SRD"),dx,y,right,RpgTheme.TEXT);y+=17;
        pane.label("Proficiencies",dx,y,right,RpgTheme.GOLD);y+=15;
        String prof=FeatureReference.classProficiencies(selected.id())+"\nSaves: "+selected.guidance().split(" · ")[0].replace(" saves","");
        for(String line:prof.split("\n")){int lines=net.minecraft.client.MinecraftClient.getInstance().textRenderer.getTextHandler().wrapLines(line,right-14,net.minecraft.text.Style.EMPTY).size();int height=lines*12+10;pane.panel("",dx,y,right,height);pane.wrapped(line,dx+7,y+5,right-14,RpgTheme.TEXT);y+=height+4;}
        String skills="Choose "+Srd2014Catalog.classSkillCount(selected.name())+" skills";pane.control("skills",new RpgButton(dx,y,right,18,skills,"",proficiencies).hint("Open the interactive class-specific Proficiencies page."));y+=25;
        ClassFeatureRegion.build(pane,selected.id(),new UiLayout.Rect(dx,y,right,Math.max(1,pane.getHeight()-y)),inspect);
    }
    public static void feature(CharacterCanvas pane,FeatureReference.Entry entry,int x,int y,int width,java.util.function.Consumer<FeatureReference.Entry> inspect){pane.control(entry.id(),new RpgButton(x,y,width,18,entry.name(),"",()->inspect.accept(entry)).icon("features").hint(entry.tooltip()));}
}
