package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import dev.tacticalcombat.sheet.SheetLibrary;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class LibraryCreateScreen extends ManagementScreen {
    private String mode="";
    public LibraryCreateScreen(Screen parent) {super("Create a personal draft",parent);}
    @Override protected void refresh() {
        if(busy) return;fresh=false;clearRows();read(CAPABILITIES,args(),data->display());
    }
    private void display() {
        clearRows();if(mode.isEmpty()) {
            row("Guided","Persist a scaffold now; full creation wizard is a later checkpoint",true,"",()->{mode="GUIDED";display();});
            row("Manual / Custom","Supported SRD fields or an independent copy of a local pack sheet",true,"",()->{mode="MANUAL";display();});
            info("Library ownership","Only you can inspect your library. Campaign submissions create separate copies.");return;
        }
        info("Creation mode: "+mode,"Changing mode does not grant approval, automation or full character legality.");
        row("New SRD 5.1 draft","Existing canonical SRD scaffold; grouped basic editing available",!ambiguousCreation,"Return to My Characters and inspect a possibly committed creation",()->create("srd_5_1",null));
        if(mode.equals("MANUAL")) {
            if(SheetLibrary.FORMATS.isEmpty()) SheetLibrary.reload();
            for(var local:SheetLibrary.CHARACTERS) {
                JsonObject sheet=local.toJson();String name=local.texts.getOrDefault("name","Local sheet");
                row("Copy local sheet: "+name,local.format+" · choose Manual Tabletop or Minecraft Tactical; original stays local",!ambiguousCreation,"Reconcile possibly committed creation in My Characters first",()->client.setScreen(new ChoiceScreen(this,"Local copy profile",java.util.List.of("manual_tabletop","minecraft_tactical"),ManagementState::profile,p->create(p,sheet.toString()))));
            }
            if(SheetLibrary.CHARACTERS.isEmpty()) info("No local pack characters loaded","Create a local sheet through Tabletop Tools, then return. Generic D20/Percentile data remains unchanged.");
        }
        row("Return to My Characters","Inspect all drafts before retrying an uncertain creation",true,"",()->client.setScreen(parent));
        row("Change creation method","Return to Guided / Manual choice",true,"",()->{mode="";display();});
    }
    private void create(String profile,String sheet) {
        confirm("Create persistent draft?","Create a "+mode.toLowerCase()+" "+ManagementState.profile(profile)+" library draft. The full guided wizard and detailed dashboard are not included. If the response times out, refresh My Characters before creating another draft.",()->mutate(LIBRARY_CREATE,args("profile",profile,"mode",mode,"sheet",sheet),data->client.setScreen(new LibraryCharacterScreen(parent,text(data,"id")))));
    }
}
