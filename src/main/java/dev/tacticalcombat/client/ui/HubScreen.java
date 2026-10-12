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
    private RpgButton campaignButton,characterButton,dmButton,sheetButton;
    private RpgList destinationList;
    private boolean lastDm;
    private boolean availableDmCampaign;
    public HubScreen(Screen parent) {super("CUBETABLE",parent);}
    @Override protected void init() {
        super.init();clearChildren();dmButton=null;sheetButton=null;destinationList=null;busy=false;state.reset(ClientUiApi.supported());availableDmCampaign=false;message=state.message;
        int x=frame.x()+12,y=frame.y()+38,w=(frame.width()-30)/2;
        campaignButton=button("Campaign ▾","Loading…",x,y,w,38,this::campaigns).hint("DM quick-switch. Ordinary campaign selection is available under Campaigns.");
        characterButton=button("Character ▾","Loading…",x+w+6,y,w,38,this::characters).hint("Selection does not change entity binding or combat resources.");
        var tiles=UiLayout.hubTiles(frame);String[] names={"Campaigns","My Characters","Encounters","DM Tools","Tabletop Tools","Settings"};
        String[] detail={"Browse your campaigns","Library & existing sheets","Tactical & SRD controls","Campaign manager access","Manual packs, dice & cards","Minecraft & future HUD"};
        Runnable[] actions={()->client.setScreen(new CampaignBrowserScreen(this)),()->client.setScreen(new LibraryScreen(this)),()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.ENCOUNTERS)),this::dmTools,()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.TABLETOP)),()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.SETTINGS))};
        if(!tiles.isEmpty()) {sheetButton=button("Open Sheet","Active character dashboard",frame.x()+frame.width()-184,frame.y()+8,92,20,this::dashboard);for(int i=0;i<names.length;i++) {var r=tiles.get(i);var b=button(names[i],detail[i],r.x(),r.y(),r.width(),r.height(),actions[i]);if(i==3) dmButton=b;}}
        else {
            var list=addDrawableChild(new RpgList(x,frame.y()+90,frame.width()-24,Math.max(38,frame.height()-120),row->{int i=Integer.parseInt(row.id());if(i==3 && !state.dmTools()) {message=state.dmReason();return;}actions[i].run();}));
            var rows=new ArrayList<HubState.Choice>();for(int i=0;i<names.length;i++) rows.add(new HubState.Choice(Integer.toString(i),names[i],detail[i],true,"",0));destinationList=list;list.rows(rows);lastDm=true;
        }
        footer();button("Help","",frame.x()+frame.width()-78,frame.y()+frame.height()-30,66,20,()->client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.HELP)));
        button("Refresh","",frame.x()+frame.width()-86,frame.y()+8,74,20,this::refresh);
        updateControls();
        if(ClientUiApi.supported()) loadCapabilities();else {busy=false;state.status=HubState.Status.UNSUPPORTED;updateControls();}
    }
    private void refresh() {init();}
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
        campaignButton.active=state.canSwitch() && state.quickSupported && (state.manager || availableDmCampaign);
        campaignButton.hint(state.inCombat?"Finish the encounter before switching":!state.quickSupported?"Server lacks DM quick-switch support. Use Campaigns for permitted selection.":!state.manager && !availableDmCampaign?"Hub quick-switch is restricted to campaign DMs. Use Campaigns to navigate.":state.campaignName+" · "+state.profile);
        characterButton.active=state.canSwitch() && state.campaign!=null;
        characterButton.hint(state.inCombat?"Selection is locked during an encounter":state.campaign==null?"Choose a campaign first":state.characterName+"\nSelection does not change binding.");
        if(destinationList!=null && lastDm!=state.dmTools()) {
            lastDm=state.dmTools();String[] names={"Campaigns","My Characters","Encounters","DM Tools","Tabletop Tools","Settings"};
            var rows=new ArrayList<HubState.Choice>();for(int i=0;i<names.length;i++) rows.add(new HubState.Choice(Integer.toString(i),names[i],i==3?state.dmReason():"Open destination",i!=3 || lastDm,i==3?state.dmReason():"",0));destinationList.rows(rows);
        }
        if(dmButton!=null) {dmButton.active=state.dmTools();dmButton.hint(state.dmReason());}
        if(sheetButton!=null) {sheetButton.active=state.status==HubState.Status.READY && (state.character!=null || state.bound!=null);sheetButton.hint(sheetButton.active?"Open the selected or bound campaign character without changing selection":"Select or bind an authorized campaign character first");}
    }
    private void campaigns() {if(campaignButton.active) client.setScreen(new BrowseScreen(this,BrowseScreen.Mode.CAMPAIGNS,true,state));}
    private void characters() {if(characterButton.active) client.setScreen(new BrowseScreen(this,BrowseScreen.Mode.CHARACTERS,true,state));}
    private void dashboard() {String id=state.character==null?state.bound:state.character;if(id!=null) client.setScreen(CharacterDashboardScreen.campaign(this,id));}
    private void dmTools() {
        if(!state.dmTools()) {message=state.dmReason();return;}
        var args=new JsonObject();args.addProperty("campaign",state.campaign);request(CAMPAIGN_GET,args,r->{if(!ok(r)) return;state.campaign(r.data());updateControls();
            if(state.manager) client.setScreen(new DestinationScreen(this,DestinationScreen.Destination.DM));else {message=state.dmReason();messageColor=RpgTheme.ERROR;}
        });
    }
    @Override protected void content(DrawContext g,int mx,int my,float delta) {
        RpgTheme.text(g,textRenderer,state.status==HubState.Status.UNSUPPORTED?"LOCAL TABLETOP · SERVER CONTROLS UNAVAILABLE":"RPG TOOLS · "+(state.inCombat?"ENCOUNTER ACTIVE":"CAMPAIGN & CHARACTER SELECTION"),frame.x()+12,frame.y()+78,frame.width()-24,RpgTheme.MUTED);
    }
    @Override protected int statusWidth() {return frame.width()-180;}
    @Override public void tick() {updateControls();}
}
