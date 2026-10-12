package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Searchable, informational SRD 5.1 choices. Selection only writes the inert identity record. */
public final class WizardChoiceScreen extends ManagementScreen {
    private final String character;
    private final WizardModel.Stage stage;
    private JsonObject draft;
    public WizardChoiceScreen(Screen parent,JsonObject draft,WizardModel.Stage stage) {
        super(WizardModel.title(stage)+" · verified SRD choices",parent);this.character=text(draft,"id");this.draft=draft.deepCopy();this.stage=stage;
    }
    @Override protected void refresh() {clearRows();read(LIBRARY_GET,args("character",character),data->{draft=data;display();});}
    private void display() {
        clearRows();
        for(var choice:Srd2014Catalog.forStage(stage)) row(choice.name(),choice.summary(),true,"",()->select(choice));
        info("Informational guidance",stage==WizardModel.Stage.BACKGROUND?"The verified standard list currently contains the SRD Acolyte example only.":"Traits and bonuses are not applied to authoritative combat values.");
        row("Custom / detailed record","Open the shared identity editor for subrace, subclass, custom text and narrative fields",true,"",()->client.setScreen(WizardEditors.identity(this,draft)));
    }
    private void select(Srd2014Catalog.Choice choice) {
        String field=switch(stage){case SPECIES->"species";case CLASS->"characterClass";case BACKGROUND->"background";default->throw new IllegalStateException("Not a catalog stage");};
        confirm("Record "+choice.name()+"?",choice.summary()+"\n\n"+choice.guidance()+" This does not grant automated traits, bonuses, proficiencies, equipment, features or spells.",()->{
            JsonObject identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");identity.addProperty(field,choice.name());
            mutate(LIBRARY_UPDATE,args("character",character,"revision",revision(draft),"section","identity","data",identity),data->{draft=data;client.setScreen(parent);});
        });
    }
}
