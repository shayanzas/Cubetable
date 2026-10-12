package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.function.Consumer;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class CampaignBrowserScreen extends PagedManagementScreen {
    private final Consumer<JsonObject> selected;
    private String active="";
    private RpgButton create;
    public CampaignBrowserScreen(Screen parent) {this(parent,null);}
    public CampaignBrowserScreen(Screen parent,Consumer<JsonObject> selected) {super(selected==null?"Campaigns":"Choose submission campaign",parent);this.selected=selected;}
    @Override protected void pageControls(int x,int y,int w) {create=button("Create","",x,y,w,20,this::create);create.active=false;create.hint("Only server administrators create campaigns. Invitations come from campaign managers.");}
    @Override protected void refresh() {
        if(busy) return;loadingPage();create.active=false;
        read(CAPABILITIES,args(),cap->{create.active=selected==null && flag(cap,"canCreateCampaign");
            read(ACTIVE,args(),state->{active=text(state,"campaign");
                read(CAMPAIGN_LIST,args("page",page),data->{clearRows();pagination((page+1)*8<data.get("total").getAsInt());
                    for(var c:array(data,"campaigns")) {var campaign=c.getAsJsonObject();String id=text(campaign,"id");
                        row(text(campaign,"name"),profile(text(campaign,"profile"))+" · "+(manager(campaign)?"DM/manager":"Member")+(id.equals(active)?" · selected":""),true,"Authorized campaign",()->{
                            if(selected!=null) returnChoice(parent,()->selected.accept(campaign.deepCopy()));else client.setScreen(new CampaignDetailScreen(this,id));
                        });
                    }if(rows.isEmpty()) message="No authorized campaigns on this page. Ask a manager for an invitation; public joining is unavailable.";
                });
            });
        });
    }
    private void create() {
        if(busy || !fresh || !create.active) return;
        client.setScreen(new RecordFormScreen(this,"Create campaign",CAPABILITIES,args(),java.util.List.of(new RecordFormScreen.Field("name","Campaign name","",64)),
            (state,values)->args("name",values.get("name")),CAMPAIGN_CREATE,data->client.setScreen(new CampaignDetailScreen(this,text(data,"id"))),"Server administrator required. Default profile is Minecraft Tactical; configure it after creation."));
    }
}
