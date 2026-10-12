package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class CampaignRosterScreen extends PagedManagementScreen {
    private final String campaign;
    private boolean pendingOnly;
    public CampaignRosterScreen(Screen parent,String campaign) {super("Campaign character roster",parent);this.campaign=campaign;}
    @Override protected void pageControls(int x,int y,int w) {button("Pending / All","",x,y,w,20,()->{if(!busy) {pendingOnly=!pendingOnly;refresh();}}).hint("Filter unapproved characters on this server page");}
    @Override protected void refresh() {
        if(busy) return;loadingPage();read(ROSTER,args("campaign",campaign,"page",page),data->{clearRows();pagination(array(data,"characters").size()==8);
            for(var e:array(data,"characters")) {var c=e.getAsJsonObject();if(pendingOnly && flag(c,"approved")) continue;
                String detail=profile(text(c,"profile"))+" · "+(flag(c,"approved")?"Approved":"Awaiting approval")+" · "+(text(c,"bound").isEmpty()?"Unbound":"Bound")+" · owner "+player(data,text(c,"owner"))+" · controller "+player(data,text(c,"controller"))+" · "+text(c,"readiness");
                row(text(c,"name"),detail,flag(c,"privateView"),"Private inspection requires owner, accepted controller or DM permission",()->client.setScreen(CharacterDashboardScreen.campaign(this,text(c,"id"))));
            }if(rows.isEmpty()) message=pendingOnly?"No pending approvals on this page.":"No campaign characters on this page.";
        });
    }
}
