package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class LibraryScreen extends PagedManagementScreen {
    public LibraryScreen(Screen parent) {super("My Characters · private library",parent);}
    @Override protected void pageControls(int x,int y,int w) {button("Create Character","",x,y,w,20,()->{if(!busy && fresh) client.setScreen(new LibraryCreateScreen(this));});}
    @Override protected void refresh() {
        if(busy) return;loadingPage();read(LIBRARY_LIST,args("page",page),data->{clearRows();pagination(array(data,"characters").size()==8);
            for(var e:array(data,"characters")) {var c=e.getAsJsonObject();row(text(c,"name"),profile(text(c,"profile"))+" · "+(text(c,"stage").equals("COMPLETE")?"Finished":"In progress")+" · "+(flag(c,"supportedValid")?"Saved":"Needs attention"),true,"",()->{if(text(c,"stage").equals("COMPLETE"))client.setScreen(CharacterDashboardScreen.library(this,text(c,"id")));else read(LIBRARY_GET,args("character",text(c,"id")),draft->client.setScreen(new CharacterWizardScreen(this,draft)));});}
            if(rows.isEmpty()) message="No personal characters on this page. Create a persistent draft to begin.";
        });
    }
}
