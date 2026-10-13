package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** One entry path. An uncertain creation is never automatically repeated. */
public final class LibraryCreateScreen extends RpgScreen {
    private boolean started;
    public LibraryCreateScreen(Screen parent) { super("Create Character", parent); }
    @Override protected void init() {
        super.init(); clearChildren(); footer();
        if (started) { busy=false; message="Return to My Characters to find your draft before trying again."; return; }
        started=true;
        request(LIBRARY_CREATE,args("profile","srd_5_1","mode","GUIDED","sheet",null),r->{
            if(r.code().equals("OK")) client.setScreen(new CharacterWizardScreen(parent,DraftReplies.snapshot(r.data(),text(r.data(),"id"))));
            else message="Could not confirm creation. Return to My Characters and check for your draft before retrying.";
        });
    }
    @Override protected void content(DrawContext g,int mx,int my,float delta) {
        paragraphs(g,"Preparing your private character draft… Your library character stays independent of any campaign copy.",frame.x()+16,frame.y()+42,frame.width()-32,frame.height()-90);
    }
    @Override public void close() {if(!busy)super.close();}
}
