package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.client.*;
import dev.tacticalcombat.uiapi.UiApiService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Six-destination entry point. Selection changes are acknowledged by the server before readback. */
public final class HubScreen extends RpgScreen {
    final HubState state=new HubState();
    private RpgButton campaignButton,characterButton,sheetButton,refreshButton;
    private CharacterCanvas surface;
    private int scroll;
    private String focused="";
    private final java.util.List<RpgNavigationTile> cards=new java.util.ArrayList<>();
    private boolean availableDmCampaign;
    public HubScreen(Screen parent) {super("CUBETABLE",parent);}
    @Override protected int maximumWidth(){return 900;}
    @Override protected void init() {
        if(surface!=null){scroll=surface.position();focused=surface.focusedKey();}super.init();clearChildren();cards.clear();busy=false;state.reset(ClientUiApi.supported());availableDmCampaign=false;message=state.message;
        surface=addDrawableChild(new CharacterCanvas(PlayerUiLayout.viewport(frame)));int w=surface.getWidth()-12,x=6,overview=PlayerUiLayout.overviewHeight(surface.getWidth());surface.panel("Your Table",x,6,w,overview-6);
        boolean narrow=surface.getWidth()<400;int bw=narrow?w-16:(w-22)/2,y=31;
        campaignButton=surface.control("campaign",new RpgButton(x+8,y,bw,38,"Active Campaign ▾","Loading…",this::campaigns));
        characterButton=surface.control("character",new RpgButton(narrow?x+8:x+bw+14,narrow?y+44:y,bw,38,"Active Character ▾","Loading…",this::characters));
        int statusY=narrow?118:76;surface.liveLabel(()->HubOverview.approval(state),x+8,statusY,w-16,RpgTheme.GOLD);surface.liveLabel(()->HubOverview.binding(state),x+8,statusY+15,w-16,RpgTheme.TEXT);surface.liveLabel(()->HubOverview.encounter(state),x+8,statusY+30,w-16,RpgTheme.MUTED);
        sheetButton=surface.control("sheet",new RpgButton(x+8,overview-38,w-16,24,"Open Character Sheet","",this::dashboard).primary(true));
        String[] names={"Campaigns","My Characters","Encounters","DM Tools","Tabletop Tools","Settings"};String[] detail={"Choose your table and fellow adventurers","Create, continue and manage your heroes","Return to the battlefield","Review your campaign's characters","Manual sheets, dice and shared cards","Minecraft options and Cubetable HUD"};
        String[] icons={"campaigns","characters","encounters","dm","tabletop","settings"};Runnable[] actions={()->client.setScreen(new CampaignBrowserScreen(this)),()->client.setScreen(new LibraryScreen(this)),()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.ENCOUNTERS)),this::dmTools,()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.TABLETOP)),()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.SETTINGS))};
        var tiles=PlayerUiLayout.hubTiles(surface.getWidth());for(int i=0;i<6;i++){var r=tiles.get(i);cards.add(surface.control("tile:"+i,new RpgNavigationTile(r.x(),r.y(),r.width(),r.height(),names[i],detail[i],icons[i],actions[i])));}
        surface.position(scroll);if(!focused.isEmpty()){surface.focus(focused);setInitialFocus(surface);}footer();button("Help","",frame.x()+frame.width()-78,frame.y()+frame.height()-30,66,20,()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.HELP)));refreshButton=button("Refresh","",frame.x()+frame.width()-86,frame.y()+8,74,20,this::refresh).hint("Reload server campaign and character information; connect to an updated server if unavailable");
        updateControls();if(ClientUiApi.supported())loadCapabilities();else {state.status=HubState.Status.UNSUPPORTED;message=state.message;updateControls();}
    }
    private void refresh(){if(busy)return;if(!ClientUiApi.supported()){message="Server UI unavailable. Tabletop Tools and Settings remain available.";return;}state.status=HubState.Status.LOADING;updateControls();loadCapabilities();}
    private boolean ok(UiApiService.Reply reply) {if(reply.code().equals("OK")) return true;state.failed(reply.code(),reply.message());updateControls();return false;}
    private void loadCapabilities() {
        request(CAPABILITIES,new JsonObject(),r->{if(!ok(r)) return;state.quickSupported=r.data().has("hubQuickSwitch") && r.data().get("hubQuickSwitch").getAsBoolean();
            request(ACTIVE,new JsonObject(),a->{if(!ok(a)) return;state.active(a.data());
                if(state.campaign==null) discoverDm(0);else {var args=new JsonObject();args.addProperty("campaign",state.campaign);request(CAMPAIGN_GET,args,c->{if(!ok(c)) return;state.campaign(c.data());loadCharacter();});}
            });
        });
    }
    private void discoverDm(int page) {
        var args=new JsonObject();args.addProperty("page",page);request(CAMPAIGN_LIST,args,r->{if(!ok(r)) return;
            for(var row:r.data().getAsJsonArray("campaigns")) if(row.getAsJsonObject().get("manager").getAsBoolean()) availableDmCampaign=true;
            if(!availableDmCampaign && page<7 && (page+1)*8<r.data().get("total").getAsInt()) discoverDm(page+1);else loadCharacter();
        });
    }
    private void loadCharacter() {
        String id=state.character==null?state.bound:state.character;
        if(id==null) {ready();return;}
        var args=new JsonObject();args.addProperty("character",id);request(CHARACTER_GET,args,r->{if(!ok(r)) return;state.character(r.data());ready();});
    }
    private void ready() {state.status=HubState.Status.READY;message=state.inCombat?"Encounter active — campaign and character selection locked.":"Ready. Selection and entity binding are separate.";messageColor=RpgTheme.MUTED;updateControls();}
    private void updateControls() {
        campaignButton.detail(state.campaign==null?"None selected":state.campaignName+" · "+state.profile);characterButton.detail(state.characterName);
        campaignButton.active=!busy && state.canSwitch() && state.quickSupported && (state.manager || availableDmCampaign);
        campaignButton.hint(state.inCombat?"Finish the encounter before switching":!state.quickSupported?"Server lacks DM quick-switch support. Use Campaigns for permitted selection.":!state.manager && !availableDmCampaign?"Hub quick-switch is restricted to campaign DMs. Use Campaigns to navigate.":state.campaignName+" · "+state.profile);
        characterButton.active=!busy && state.canSwitch() && state.campaign!=null;
        characterButton.hint(state.inCombat?"Selection is locked during an encounter":state.campaign==null?"Choose a campaign first":state.characterName+"\nSelection does not change binding.");
        for(int i=0;i<cards.size();i++){boolean server=i==0||i==1;boolean enabled=i==3?state.dmTools()&&!busy:!server||state.status==HubState.Status.READY;cards.get(i).availability(enabled,i==3?(busy?"Wait for the server to finish checking permissions.":state.dmReason()):"Connect to an updated server and Refresh to load personal and campaign records.");}
        if(refreshButton!=null)refreshButton.active=!busy&&ClientUiApi.supported();
        if(sheetButton!=null) {sheetButton.active=state.opensDashboard();sheetButton.hint(sheetButton.active?"Open the selected or bound campaign character without changing selection":"Select an eligible SRD campaign character first. Manual sheets are under Tabletop Tools.");}
    }
    private void campaigns() {if(campaignButton.active) client.setScreen(new BrowseScreen(this,BrowseScreen.Mode.CAMPAIGNS,true,state));}
    private void characters() {if(characterButton.active) client.setScreen(new BrowseScreen(this,BrowseScreen.Mode.CHARACTERS,true,state));}
    private void dashboard() {String id=state.character==null?state.bound:state.character;if(id!=null) client.setScreen(CharacterDashboardScreen.campaign(this,id));}
    private void dmTools() {
        if(busy||!state.dmTools()) {message=state.dmReason();return;}
        var args=new JsonObject();args.addProperty("campaign",state.campaign);request(CAMPAIGN_GET,args,r->{if(!ok(r)) return;state.campaign(r.data());updateControls();
            if(state.manager) client.setScreen(new CampaignRosterScreen(this,state.campaign));else {message=state.dmReason();messageColor=RpgTheme.ERROR;}
        });
    }
    @Override protected int statusWidth() {return frame.width()-180;}
    @Override public void tick() {updateControls();}
}
