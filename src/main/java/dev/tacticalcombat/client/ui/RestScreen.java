package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Actual rest workflow: request and DM adjudication status, never automatic recovery. */
public final class RestScreen extends ManagementScreen {
    private final String campaign;
    private UUID pendingIntent;
    private JsonObject campaignState;
    public RestScreen(Screen parent,String campaign){super("Rest requests · manual adjudication",parent);this.campaign=campaign;}
    @Override protected void refresh(){if(busy)return;clearRows();read(CAMPAIGN_GET,args("campaign",campaign),c->{campaignState=c;read(REST_LIST,args("campaign",campaign,"page",0),this::display);});}
    private void display(JsonObject data){clearRows();info("Recovery is not automated","Approval means approved for manual adjudication. HP, slots, Hit Dice, resources and time do not change automatically.");row("Request Short Rest","Server derives and validates the current online party",true,"",()->requestRest("SHORT"));row("Request Long Rest","Server derives and validates the current online party",true,"",()->requestRest("LONG"));for(var e:array(data,"requests")){var r=e.getAsJsonObject();String state=text(r,"state");row(text(r,"kind")+" Rest · "+state,"Participants "+array(r,"participants").size()+" · expires "+text(r,"expires")+" · no automatic recovery",manager(campaignState)&&state.equals("PENDING"),"Only campaign managers review pending requests",()->review(r));}if(array(data,"requests").isEmpty())info("No visible requests","Only authorized participant/DM records are returned.");}
    private void requestRest(String kind){if(pendingIntent==null)pendingIntent=UUID.randomUUID();UUID token=pendingIntent;confirm("Request "+kind.toLowerCase(Locale.ROOT).replace('_',' ')+" rest?","This only creates a party request for DM/manual adjudication. It does not recover anything.",()->{request(REST_REQUEST,args("campaign",campaign,"request",token,"kind",kind),r->{if(r.code().equals("OK")){pendingIntent=null;message="Rest requested; no recovery applied.";refresh();}else message=r.code()+": "+r.message()+". Retry retains the same request identity while this screen remains open.";});});}
    private void review(JsonObject rest){client.setScreen(new ChoiceScreen(this,"Review rest request",List.of("approve","reject"),v->v.equals("approve")?"Approve for manual adjudication":"Reject request",v->confirm("Confirm rest review?",v.equals("approve")?"Approval permits manual adjudication only; no resources recover.":"Reject this request without changing character resources.",()->request(REST_REVIEW,args("request",text(rest,"id"),"revision",revision(rest),"approve",v.equals("approve")),r->{message=r.code()+": "+r.message();refresh();}))));}
}
