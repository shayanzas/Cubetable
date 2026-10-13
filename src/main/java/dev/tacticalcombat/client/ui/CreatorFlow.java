package dev.tacticalcombat.client.ui;

import java.util.*;

/** Presentation stages only: persisted draft modes and section schemas remain compatible. */
public final class CreatorFlow {
    public enum Step {
        IDENTITY("Identity"), CLASS("Class"), ABILITIES("Ability Points"),
        PROFICIENCIES("Proficiencies"), EQUIPMENT("Equipment"), FEATURES("Features"),
        SPELLS("Spells"), DETAILS("Character Details"), REVIEW("Review & Finish");
        public final String title;
        Step(String title){this.title=title;}
    }
    public record Layout(UiLayout.Rect navigation,UiLayout.Rect content,UiLayout.Rect summary,int columns) {}
    public record CatalogLayout(UiLayout.Rect name,UiLayout.Rect subraces,UiLayout.Rect search,UiLayout.Rect grid,UiLayout.Rect details,UiLayout.Rect choose,UiLayout.Rect custom,boolean tiny) {}
    private CreatorFlow() {}
    public static Layout layout(UiLayout.Rect frame) {
        var shell=CharacterShell.layout(frame);
        return new Layout(shell.rail(),shell.content(),shell.summary(),1);
    }
    public static Map<Step,WizardModel.Status> statuses(com.google.gson.JsonObject draft){var old=WizardModel.statuses(draft);var out=new EnumMap<Step,WizardModel.Status>(Step.class);for(var step:Step.values()){var stage=switch(step){case IDENTITY->WizardModel.Stage.SPECIES;case CLASS->WizardModel.Stage.CLASS;case ABILITIES->WizardModel.Stage.ABILITIES;case PROFICIENCIES->WizardModel.Stage.PROFICIENCIES;case EQUIPMENT->WizardModel.Stage.EQUIPMENT;case FEATURES->WizardModel.Stage.FEATURES;case SPELLS->WizardModel.Stage.SPELLS;case DETAILS,REVIEW->WizardModel.Stage.REVIEW;};var status=old.get(stage);if(step==Step.IDENTITY)status=new WizardModel.Status(old.get(WizardModel.Stage.BASICS).complete()&&status.complete(),status.detail());if(step==Step.DETAILS)status=new WizardModel.Status(true,"Optional story");out.put(step,status);}return out;}
    public static Step errorStep(String section) {
        return switch(section){case "basics","identity"->Step.IDENTITY;case "combat","abilities"->Step.ABILITIES;case "proficiencies"->Step.PROFICIENCIES;case "inventory"->Step.EQUIPMENT;case "features"->Step.FEATURES;case "spellbook"->Step.SPELLS;case "progression"->Step.DETAILS;default->Step.REVIEW;};
    }
    public static CatalogLayout catalogLayout(UiLayout.Rect content,Step step,boolean subraces) {
        int x=content.x()+6,y=content.y()+5,w=content.width()-12,h=content.height()-10,top=y;
        var absent=new UiLayout.Rect(0,0,0,0);boolean tiny=h<85;
        var name=step==Step.IDENTITY?new UiLayout.Rect(x,top,w,tiny?14:18):absent;if(name.width()>0)top+=name.height()+4-(tiny?2:0);
        var subs=step==Step.IDENTITY&&subraces&&!tiny?new UiLayout.Rect(x,top,w,15):absent;if(subs.width()>0)top+=18;
        int remaining=y+h-top;
        if(tiny)return new CatalogLayout(name,subs,absent,new UiLayout.Rect(x,top,w,Math.max(9,remaining-19)),absent,absent,new UiLayout.Rect(x,y+h-16,w,16),true);
        int left=Math.max(100,w*48/100),right=w-left-6;
        var search=step==Step.IDENTITY?absent:new UiLayout.Rect(x,top,left,17);int searchHeight=search.width()>0?21:0;
        return new CatalogLayout(name,subs,search,new UiLayout.Rect(x,top+searchHeight,left,Math.max(12,remaining-searchHeight-25)),new UiLayout.Rect(x+left+6,top,right,Math.max(20,remaining-25)),new UiLayout.Rect(x+left+6,y+h-20,right,20),new UiLayout.Rect(x,y+h-20,left,20),false);
    }
    public static String baseRace(String recorded) {
        for(var c:Srd2014Catalog.SPECIES) {
            if(c.name().equals(recorded)||recorded.startsWith(c.name()+" — "))return c.name();
            for(String sub:subraces(c.name()))if(sub.equals(recorded))return c.name();
        }
        return recorded;
    }
    public static List<String> subraces(String race) {
        return switch(race){case "Elf"->List.of("High Elf");case "Dwarf"->List.of("Hill Dwarf");case "Halfling"->List.of("Lightfoot Halfling");case "Gnome"->List.of("Rock Gnome");default->List.of();};
    }
    public static String recordedRace(String race,String subrace) {
        if(subrace.isEmpty())return race;
        if(!subraces(race).contains(subrace))throw new IllegalArgumentException("Choose a supported subrace or use Custom details");
        return race+" — "+subrace;
    }
}
