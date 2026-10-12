package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.client.ClientUiApi;
import dev.tacticalcombat.uiapi.UiApiService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Searchable dropdown/popover and reusable paged browser. It never binds, approves or edits a sheet. */
public final class BrowseScreen extends RpgScreen {
    public enum Mode { CAMPAIGNS, CHARACTERS, LIBRARY }
    private Mode mode;
    private final boolean quick;
    private final HubState hub;
    private List<HubState.Choice> rows=List.of();
    private RpgList list;
    private TextFieldWidget search;
    private RpgButton previous,next;
    private String query="";
    private int page;
    private boolean hasNext;
    public BrowseScreen(Screen parent,Mode mode,boolean quick,HubState hub) {
        super(quick?(mode==Mode.CAMPAIGNS?"Active campaign ▾":"Active character ▾"):mode==Mode.LIBRARY?"My Characters":"Campaigns",parent);this.mode=mode;this.quick=quick;this.hub=hub;
    }
    @Override protected void init() {
        super.init();clearChildren();busy=false;if(quick) frame=UiLayout.frame(width,height,480);
        int x=frame.x()+12,w=frame.width()-24;
        if(!quick && mode!=Mode.CAMPAIGNS) {
            tab("Library",x,frame.y()+36,w/2-3,mode==Mode.LIBRARY,()->change(Mode.LIBRARY));
            tab("Campaign characters",x+w/2+3,frame.y()+36,w/2-3,mode==Mode.CHARACTERS,()->change(Mode.CHARACTERS));
        }
        int top=frame.y()+(!quick && mode!=Mode.CAMPAIGNS?62:36);
        search=search(x,top,w,query,text->{query=text;filter();});
        list=addDrawableChild(new RpgList(x,top+26,w,Math.max(38,frame.y()+frame.height()-62-(top+26)),this::choose));
        previous=button("‹ Previous","",x,frame.y()+frame.height()-56,92,20,()->load(page-1));
        next=button("Next ›","",x+w-92,frame.y()+frame.height()-56,92,20,()->load(page+1));
        button("Refresh","",x+(w-74)/2,frame.y()+frame.height()-56,74,20,()->load(page));footer();
        load(page);
    }
    private void tab(String text,int x,int y,int w,boolean selected,Runnable action) {var b=button(text,"",x,y,w,20,action);b.selected(selected).hint(selected?"Current tab":"Switch tab");}
    private void change(Mode mode) {this.mode=mode;page=0;rows=List.of();query="";init();}
    private void load(int target) {
        if(busy || target<0) return;page=target;rows=List.of();filter();previous.active=false;next.active=false;
        if(!ClientUiApi.supported()) {message="Updated UI server required. Use Tabletop Tools for local sheets.";messageColor=RpgTheme.MUTED;return;}
        if(mode==Mode.LIBRARY) fetchPage();
        else request(ACTIVE,new JsonObject(),active->{
            if(!active.code().equals("OK")) return;hub.active(active.data());
            if(mode==Mode.CAMPAIGNS) fetchPage();
            else if(hub.campaign==null) {message="Select a campaign before choosing a campaign character.";}
            else {
                var args=new JsonObject();args.addProperty("campaign",hub.campaign);
                request(CAMPAIGN_GET,args,campaign->{if(!campaign.code().equals("OK")) return;hub.campaign(campaign.data());fetchPage();});
            }
        });
    }
    private void fetchPage() {
        var args=new JsonObject();args.addProperty("page",page);
        UiApiService.Operation op=mode==Mode.CAMPAIGNS?CAMPAIGN_LIST:mode==Mode.LIBRARY?LIBRARY_LIST:ROSTER;
        if(mode==Mode.CHARACTERS) args.addProperty("campaign",hub.campaign);
        request(op,args,r->{
            if(!r.code().equals("OK")) {rows=List.of();filter();return;}
            var data=r.data();var array=data.getAsJsonArray(mode==Mode.CAMPAIGNS?"campaigns":"characters");
            rows=switch(mode) {
                case CAMPAIGNS->HubState.campaigns(array,quick,hub.inCombat);
                case LIBRARY->HubState.library(array);
                case CHARACTERS->HubState.allowManager(HubState.characters(array,client.player.getUuidAsString(),hub.inCombat),hub.manager,hub.inCombat);
            };
            hasNext=mode==Mode.CAMPAIGNS?(page+1)*8<data.get("total").getAsInt():array.size()==8;
            previous.active=page>0;next.active=hasNext;filter();message=rows.isEmpty()?(quick && mode==Mode.CAMPAIGNS?"No DM-authorized campaigns on this page.":mode==Mode.CAMPAIGNS?"No campaigns on this page.":"No authorized characters on this page."):"Page "+(page+1)+" · "+(mode==Mode.CHARACTERS?hub.campaignName+" · selection does not bind.":mode==Mode.LIBRARY?"Library sources are independent from campaign copies.":"Choose a campaign by name.");
            messageColor=RpgTheme.MUTED;
        });
    }
    private void filter() {if(list!=null) list.rows(HubState.filter(rows,query));}
    private void choose(HubState.Choice choice) {
        if(busy) return;
        if(!choice.enabled()) {message=choice.reason();messageColor=RpgTheme.MUTED;return;}
        if(mode==Mode.LIBRARY) {client.setScreen(choice.detail().startsWith("COMPLETE")?CharacterDashboardScreen.library(this,choice.id()):new LibraryCharacterScreen(this,choice.id()));return;}
        var args=new JsonObject();UiApiService.Operation op;
        if(mode==Mode.CAMPAIGNS) {args.addProperty("campaign",choice.id());if(quick) args.addProperty("quick",true);op=CAMPAIGN_SELECT;}
        else {args.addProperty("character",choice.id());args.addProperty("revision",choice.revision());op=CHARACTER_SELECT;}
        request(op,args,r->{
            if(r.code().equals("OK")) {message="Selection acknowledged by server; refreshing…";messageColor=RpgTheme.SUCCESS;client.setScreen(parent);}
            else {
                message=r.code()+": "+r.message()+" Refresh before selecting again.";
                rows=rows.stream().map(c->new HubState.Choice(c.id(),c.name(),c.detail(),false,"Refresh server state before selecting again",c.revision())).toList();filter();
            }
        });
    }
    @Override protected void content(DrawContext g,int mx,int my,float delta) {
        if(!busy && HubState.filter(rows,query).isEmpty()) paragraphs(g,rows.isEmpty()?"No choices to display. Check another page or refresh.":"No matches on this page. Clear the search or check another page.",frame.x()+24,frame.y()+(!quick && mode!=Mode.CAMPAIGNS?98:72),frame.width()-48,30);
    }
}
