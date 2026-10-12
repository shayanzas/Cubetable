package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class AssignmentScreen extends PagedManagementScreen {
    private final String campaign;
    public AssignmentScreen(Screen parent,String campaign) {super("Character assignments",parent);this.campaign=campaign;}
    @Override protected void refresh() {
        if(busy) return;loadingPage();read(ASSIGNMENT_LIST,args("campaign",campaign,"page",page),data->{clearRows();pagination(array(data,"assignments").size()==8);
            for(var e:array(data,"assignments")) {var a=e.getAsJsonObject();String name=text(a,"name");
                row(name,text(a,"decision")+" · controller "+player(data,text(a,"controller"))+(text(a,"offered").isEmpty()?"":" · offered to "+player(data,text(a,"offered"))),true,"",()->client.setScreen(new AssignmentDetail(this,a.deepCopy(),data.deepCopy())));
            }if(rows.isEmpty()) message="No authorized assignments on this page. Offers do not bind characters.";
        });
    }
    private final class AssignmentDetail extends ManagementScreen {
        private final String character;
        private JsonObject assignment,names;
        private boolean found;
        AssignmentDetail(Screen parent,JsonObject assignment,JsonObject names) {super("Assignment: "+text(assignment,"name"),parent);this.assignment=assignment;this.names=names;this.character=text(assignment,"character");}
        @Override protected void refresh() {if(busy) return;fresh=false;found=false;clearRows();find(0);}
        private void find(int page) {read(ASSIGNMENT_LIST,args("campaign",campaign,"page",page),data->{
            for(var e:array(data,"assignments")) if(text(e.getAsJsonObject(),"character").equals(character)) {assignment=e.getAsJsonObject();names=data;found=true;break;}
            if(found) display();else if(array(data,"assignments").size()==8 && page<128) find(page+1);else {fresh=false;info("Assignment unavailable","Permission or assignment changed. Return to the list and refresh.");}
        });}
        private void display() {
            clearRows();info(text(assignment,"name"),text(assignment,"decision")+" · controller "+player(names,text(assignment,"controller")));
            info("Offer recipient",text(assignment,"offered").isEmpty()?"No pending recipient":player(names,text(assignment,"offered")));
            boolean answer=canAnswer(assignment,self());
            row("Accept offer","Control changes; original ownership and binding remain unchanged",answer,"Only the pending recipient can answer an unbound offer",()->answer(true));
            row("Decline offer","Previous controller stays unchanged",answer,"Only the pending recipient can answer an unbound offer",()->answer(false));
            row("Open character","Private inspection becomes available after accepted control",!text(assignment,"decision").equals("PENDING") || !text(assignment,"offered").equals(self()),"Accept first; pending offers do not disclose the private sheet",()->client.setScreen(new CampaignCharacterScreen(this,character)));
        }
        private void answer(boolean accept) {long expected=revision(assignment);confirm(accept?"Accept control?":"Decline control?","This does not transfer ownership or bind an entity. Mid-encounter takeover and automatic reconnect restoration are unavailable.",()->mutate(ASSIGNMENT_ANSWER,args("character",character,"revision",expected,"accept",accept),d->refresh()));}
    }
}
