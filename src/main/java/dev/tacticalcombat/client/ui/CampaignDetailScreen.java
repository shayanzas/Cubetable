package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class CampaignDetailScreen extends ManagementScreen {
    private final String id;
    private JsonObject campaign=new JsonObject();
    private boolean inCombat;
    public CampaignDetailScreen(Screen parent,String id) {super("Campaign details",parent);this.id=id;}
    @Override protected void refresh() {
        if(busy) return;fresh=false;clearRows();
        read(ACTIVE,args(),state->{inCombat=flag(state,"inCombat");read(CAMPAIGN_GET,args("campaign",id),data->{campaign=data;display(text(state,"campaign"));});});
    }
    private void display(String active) {
        clearRows();info(text(campaign,"name"),profile(text(campaign,"profile"))+" · "+(manager(campaign)?"DM/manager":"Member"));
        row(id.equals(active)?"Selected campaign":"Select campaign","Selection does not change existing bindings",!inCombat && !id.equals(active),inCombat?"Campaign switching is locked during an encounter":"Already selected",()->mutate(CAMPAIGN_SELECT,args("campaign",id),d->refresh()));
        row("Character roster","Approval, controllers, binding and readiness",true,"",()->client.setScreen(new CampaignRosterScreen(this,id)));
        row("Assignments","Pending offers and current control",true,"",()->client.setScreen(new AssignmentScreen(this,id)));
        row("Approval policy","Server-controlled; existing approvals are unchanged",true,"",()->client.setScreen(new CampaignPolicyScreen(this,id)));
        row("Presentation / rest request policy","Party HP sharing and existing nearby-party radius",true,"",()->client.setScreen(new CampaignPresentationScreen(this,id)));
        if(manager(campaign)) {
            row("Members and roles","Invite, remove and manage authorized co-DMs",true,"",()->client.setScreen(new CampaignMembersScreen(this,id)));
            row("Change default profile","Existing characters and encounters are not converted",true,"",()->client.setScreen(new ChoiceScreen(this,"Campaign profile",List.of("minecraft_tactical","manual_tabletop","srd_5_1"),ManagementState::profile,value->confirm("Change default profile?","New default: "+profile(value)+". Existing character sheets, approvals and active encounters remain unchanged.",()->mutate(CAMPAIGN_PROFILE,args("campaign",id,"revision",revision(campaign),"profile",value),d->refresh())))));
        } else info("Read-only campaign view","Managers review approvals, invitations, policies and bindings.");
        info("Control limitations","Live takeover, remote NPC delegation and automatic reconnect restoration are unavailable.");
    }
}
