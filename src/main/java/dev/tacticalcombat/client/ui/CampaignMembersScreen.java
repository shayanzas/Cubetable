package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class CampaignMembersScreen extends ManagementScreen {
    private final String id;
    private JsonObject campaign;
    public CampaignMembersScreen(Screen parent,String id) {super("Campaign members",parent);this.id=id;}
    @Override protected void refresh() {if(busy) return;fresh=false;clearRows();read(CAMPAIGN_GET,args("campaign",id),data->{campaign=data;display();});}
    private void display() {
        clearRows();if(!manager(campaign)) {info("Manager access required","Refresh your role; private membership controls are unavailable.");return;}
        row("Invite a player","Manager invitation; no public joining",true,"",()->invite(false));
        row("Invite / appoint co-DM","Owner or server administrator only",flag(campaign,"canManageRoles"),"Only campaign owner/admin changes co-DM authority",()->invite(true));
        var dms=new HashSet<String>();for(var e:array(campaign,"dms")) dms.add(e.getAsString());
        int unknown=0;for(var e:array(campaign,"members")) {String player=e.getAsString(),name=player(campaign,player);if(name.equals("Unknown offline player") || name.equals("Unknown/offline player")) name+=" #"+(++unknown);
            boolean owner=text(campaign,"owner").equals(player),dm=dms.contains(player);String label=name;
            boolean known=!label.startsWith("Unknown offline player") && !label.startsWith("Unknown/offline player");row(label,owner?"Campaign owner":dm?"Co-DM":"Member",known,"Account name unavailable; wait for this player to reconnect before changing authority",()->client.setScreen(new MemberActions(this,player,label,owner,dm)));
        }
    }
    private void invite(boolean dm) {client.setScreen(new PlayerPickerScreen(this,id,false,p->{String name=text(p,"name"),player=text(p,"id");long expected=revision(campaign);confirm(dm?"Grant co-DM authority?":"Invite player?",name+" will "+(dm?"gain campaign manager authority.":"become a campaign member; no character will be bound."),()->mutate(CAMPAIGN_INVITE,args("campaign",id,"revision",expected,"player",player,"dm",dm),d->refresh()));}));}
    private final class MemberActions extends ManagementScreen {
        private final String player,name;private final boolean owner,dm;
        MemberActions(Screen parent,String player,String name,boolean owner,boolean dm) {super("Member: "+name,parent);this.player=player;this.name=name;this.owner=owner;this.dm=dm;}
        @Override protected void refresh() {if(busy) return;fresh=false;clearRows();read(CAMPAIGN_GET,args("campaign",id),current->{
            campaign=current;boolean stillMember=false,isDm=false;for(var e:array(current,"members")) if(e.getAsString().equals(player)) stillMember=true;for(var e:array(current,"dms")) if(e.getAsString().equals(player)) isDm=true;
            clearRows();info(name,isDm?"Co-DM":"Member");boolean canRemove=stillMember && manager(current) && !text(current,"owner").equals(player) && (!isDm || flag(current,"canManageRoles"));
            row("Remove member","Affected bindings must be released first",canRemove,"Owner cannot be removed; co-DM removal requires owner/admin",()->operation(CAMPAIGN_REMOVE,"Remove "+name+" from this campaign? Existing affected bindings must be released first."));
            boolean currentDm=isDm;row(isDm?"Revoke co-DM":"Grant co-DM","Authority changes require confirmation",stillMember && roleChange(current,player),"Only campaign owner/admin can change this role",()->operation(currentDm?CAMPAIGN_REVOKE_DM:CAMPAIGN_INVITE,"Change "+name+"'s campaign manager authority?"));
        });}
        private void operation(dev.tacticalcombat.uiapi.UiApiService.Operation op,String question) {long expected=revision(campaign);confirm("Change membership?",question,()->{var a=args("campaign",id,"revision",expected,"player",player);if(op==CAMPAIGN_INVITE) a.addProperty("dm",true);mutate(op,a,d->client.setScreen(CampaignMembersScreen.this));});}
    }
}
