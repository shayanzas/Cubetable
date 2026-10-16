package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import java.util.UUID;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Uses the existing authenticated BIND API with the current character revision. */
public final class ActorBindingScreen extends ManagementScreen {
    private final String actor;private int page;
    public ActorBindingScreen(Screen parent,String actor){super("Bind approved character",parent);this.actor=actor;}
    @Override protected void refresh(){if(busy)return;fresh=false;clearRows();read(ACTOR_CHARACTERS,args("actor",actor,"page",page),data->{
        dev.tacticalcombat.client.ClientRulesWatch.panel(this,UUID.fromString(text(data,"panelToken")),revoked->{generation++;busy=false;fresh=false;clearRows();filter();message="Campaign or characters changed. Refresh before binding.";});
        for(var value:array(data,"characters")){var c=value.getAsJsonObject();row(text(c,"name"),"Approved · available campaign character",true,"Confirm binding; no monster mechanics are generated",()->confirm("Bind character?",text(c,"name")+" will be bound to this NPC. The server rechecks approval, revision, controller and encounter locks.",()->mutate(BIND,args("character",text(c,"character"),"revision",c.get("revision").getAsLong(),"entity",actor),reply->client.setScreen(parent))));}
        if(rows.isEmpty())info("No available approved SRD characters","Prepare and approve a campaign character first. SRD monster automation is a later checkpoint.");
        if(page>0)row("Previous","",true,"",()->{page--;refresh();});if(flag(data,"hasMore"))row("Next","",true,"",()->{page++;refresh();});filter();
    });}
    @Override public void removed(){dev.tacticalcombat.client.ClientRulesWatch.close(this);super.removed();}
}
