package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import java.util.stream.IntStream;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Exposes existing visibility/adjudication settings, without implementing a HUD or rest recovery. */
public final class CampaignPresentationScreen extends ManagementScreen {
    private final String campaign;
    private JsonObject policy;
    private boolean manager;
    public CampaignPresentationScreen(Screen parent,String campaign) {super("Campaign presentation policy",parent);this.campaign=campaign;}
    @Override protected void refresh() {
        if(busy) return;fresh=false;clearRows();read(CAMPAIGN_GET,args("campaign",campaign),data->{manager=manager(data);read(PARTY,args("campaign",campaign,"page",0),party->{policy=party.getAsJsonObject("policy");display();});});
    }
    private void display() {
        clearRows();info("HP visibility: "+text(policy,"hp"),"PRIVATE: authorized viewers only; BARS: party fraction; EXACT: party numbers. NPC enemies excluded.");
        row("Change party HP visibility","Changes sharing policy; does not alter HP or combat",manager,"Only campaign managers change sharing policy",()->client.setScreen(new ChoiceScreen(this,"Party HP visibility",List.of("PRIVATE","BARS","EXACT"),v->switch(v) {case "PRIVATE"->"Private";case "BARS"->"Bars / fraction";default->"Exact numbers";},hp->change(hp,policy.get("radius").getAsInt()))));
        info("Rest request radius: "+policy.get("radius").getAsInt()+" blocks","Existing nearby-party eligibility only. Approval is manual adjudication; no automatic healing, time or recovery.");
        row("Change rest request radius","Supported range: 1–64 Minecraft blocks",manager,"Only campaign managers change this policy",()->client.setScreen(new ChoiceScreen(this,"Rest request radius",IntStream.rangeClosed(1,64).mapToObj(Integer::toString).toList(),v->v+" blocks",radius->change(text(policy,"hp"),Integer.parseInt(radius)))));
    }
    private void change(String hp,int radius) {long expected=revision(policy);confirm("Change presentation policy?","Party HP visibility: "+hp+". Rest request radius: "+radius+" blocks. This changes sharing/eligibility settings only. Existing character and encounter statistics are unchanged.",()->mutate(PRESENTATION_POLICY,args("campaign",campaign,"revision",expected,"hp",hp,"radius",radius),data->refresh()));}
}
