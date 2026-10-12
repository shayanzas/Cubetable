package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Permission-filtered bounded session history; not a durable character audit log. */
public final class RecentActivityScreen extends ManagementScreen {
    private final String campaign;
    public RecentActivityScreen(Screen parent,String campaign){super("Recent campaign activity",parent);this.campaign=campaign;}
    @Override protected void refresh(){if(busy)return;clearRows();read(EVENTS,args("campaign",campaign,"cursor",0,"kind",null),data->{info("Session history","Bounded, privacy-filtered and reset when the server session changes. Not a lifetime audit log.");for(var e:array(data,"events")){var event=e.getAsJsonObject();row(text(event,"kind"),text(event,"summary")+" · sequence "+text(event,"sequence"),true,"",()->client.setScreen(new InfoScreen(this,text(event,"kind"),"Read-only server event · current session",event.toString())));}if(array(data,"events").isEmpty())info("No visible recent events","The server may not produce every reserved event type.");});}
}
