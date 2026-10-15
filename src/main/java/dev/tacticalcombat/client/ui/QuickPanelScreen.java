package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.client.*;
import dev.tacticalcombat.uiapi.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Interactive world-visible panel; passive party/initiative/event HUD remains owned by CubetableHud. */
public final class QuickPanelScreen extends RpgScreen {
    private final boolean dm;
    private final String inspect;
    private JsonObject data;
    private CharacterCanvas canvas;
    private int page,y;
    private boolean refreshPending;
    public QuickPanelScreen(Screen parent,boolean dm){this(parent,dm,null);}
    public QuickPanelScreen(Screen parent,boolean dm,String inspect){super(dm?"DM Quick Tools":"Character Quick Sheet",parent);this.dm=dm;this.inspect=inspect;}
    public boolean dm(){return dm;}
    @Override protected void init(){super.init();frame=QuickPanelLayout.frame(width,height);clearChildren();busy=false;data=null;footer();button("Refresh","",frame.x()+frame.width()-84,frame.y()+8,72,18,this::refresh);build();refresh();}
    @Override protected void background(DrawContext g){RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),0xEF191D23,false);RpgTheme.text(g,textRenderer,getTitle().getString(),frame.x()+10,frame.y()+12,frame.width()-100,RpgTheme.GOLD);}
    @Override public void removed(){ClientRulesWatch.close(this);data=null;super.removed();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){var binding=dm?TacticalCombatClient.DM_QUICK_KEY:TacticalCombatClient.QUICK_SHEET_KEY;if(binding.matchesKey(key,scan)){close();return true;}var other=dm?TacticalCombatClient.QUICK_SHEET_KEY:TacticalCombatClient.DM_QUICK_KEY;if(other.matchesKey(key,scan)){client.setScreen(new QuickPanelScreen(parent,!dm));return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void tick(){super.tick();if(client.player==null||client.world==null){data=null;close();return;}if(refreshPending&&!busy){refreshPending=false;refresh();}}
    private void refresh(){if(busy)return;data=null;build();request(dm?DM_QUICK:QUICK_SHEET,dm?args("page",page):inspect==null?args():args("character",inspect),r->{if(r.code().equals("OK")){data=r.data();message="Server-authorized view";if(data.has("panelToken"))ClientRulesWatch.panel(this,UUID.fromString(text(data,"panelToken")),revoked->{if(client.currentScreen!=this)return;generation++;busy=false;data=null;build();refreshPending=!revoked;message=revoked?"Access or binding changed. Refresh to recheck.":"Character changed; refreshing…";});}else{ClientRulesWatch.close(this);data=null;}build();});}
    private int w(){return canvas.getWidth()-16;}
    private void line(String text,int color){canvas.wrapped(text,8,y,w(),color);y+=Math.max(1,textRenderer.wrapLines(net.minecraft.text.Text.literal(text),w()).size())*12+4;}
    private void action(String title,Runnable task){canvas.control("action:"+y,new RpgButton(8,y,w(),24,title,"",()->{if(!busy&&data!=null)task.run();}).wrappedTitle());y+=30;}
    private void build(){if(frame==null)return;int offset=canvas==null?0:canvas.position();if(canvas!=null)remove(canvas);canvas=addDrawableChild(new CharacterCanvas(new UiLayout.Rect(frame.x()+6,frame.y()+34,frame.width()-12,Math.max(30,frame.height()-82))));y=4;if(data==null){line("Loading or unavailable. Refresh to check current access.",RpgTheme.MUTED);return;}if(data.has("empty")){line(text(data,"empty"),RpgTheme.MUTED);action("Open Cubetable",()->client.setScreen(new HubScreen(this)));return;}
        if(dm)dmBody();else sheetBody();canvas.position(offset);
    }
    private void sheetBody(){line(text(data,"name"),RpgTheme.GOLD);var identity=SheetSections.read(data.getAsJsonObject("sheet"),"identity");line(PlayerUiModel.raceLabel(text(identity,"species"))+" · "+text(identity,"characterClass"),RpgTheme.TEXT);
        if(data.has("rules")&&flag(data.getAsJsonObject("rules"),"valid")){var r=data.getAsJsonObject("rules");line("Combat level "+text(r,"effectiveLevel")+" · recorded level "+text(r,"recordedLevel")+(flag(r,"recordedLevelExplicit")?"":" (default; not recorded)"),RpgTheme.MUTED);if(flag(r,"levelMismatch"))line(text(r,"levelNotice"),RpgTheme.GOLD);for(var stat:DashboardModel.combat(data,false))line(stat.label()+": "+stat.value(),RpgTheme.TEXT);for(String ability:List.of("str","dex","con","int","wis","cha"))line(ability.toUpperCase(Locale.ROOT)+" "+DashboardModel.score(data,ability)+" ("+String.format(java.util.Locale.ROOT,"%+d",DashboardModel.modifier(data,ability))+")",RpgTheme.TEXT);
            line("Supported weapon inputs",RpgTheme.GOLD);var inputs=r.getAsJsonObject("inputs");var weapons=new ArrayList<String>();for(var weapon:array(inputs,"weapons"))weapons.add(weapon.getAsString().toLowerCase(Locale.ROOT).replace('_',' '));line(String.join(", ",weapons),RpgTheme.TEXT);line("Virtual stock: "+text(r,"arrows")+" arrows · "+text(r,"bolts")+" bolts · "+text(r,"daggers")+" daggers. Availability and legal attacks are checked in combat.",RpgTheme.MUTED);
        }else line("No compatible rules projection. Open the full sheet to review saved inputs.",RpgTheme.MUTED);
        if(ClientSrdCombat.hudEncounter()!=null)action("Combat Controls",()->client.setScreen(new SrdCombatScreen(this)));
        String id=text(data,"id");action("Full Character Sheet",()->client.setScreen(PlayerNavigation.sheet(this,new DashboardSource(false,id))));action("Open Cubetable",()->client.setScreen(new HubScreen(this)));
    }
    private void dmBody(){String campaign=text(data,"campaign");line(text(data,"campaignName"),RpgTheme.GOLD);action("New Encounter",()->client.setScreen(new EncounterLauncherScreen(this,true)));action("View Active Encounters / Combat Controls",()->client.setScreen(new EncounterLauncherScreen(this,false)));
        UUID encounter=ClientSrdCombat.hudEncounter();if(encounter!=null){action("End Watched Encounter…",()->client.setScreen(new RpgConfirmationScreen(this,"End Encounter?","Persistent D&D HP and ammunition will be retained.",()->request(ENCOUNTER_END,args("encounter",encounter),r->{message=r.message();refreshPending=true;}))));}
        // Existing HUD presentation state supplies initiative; no separate initiative polling.
        if(CubetableHud.MODEL.campaign().equals(campaign)&&!CubetableHud.MODEL.actors().isEmpty()){line("Watched initiative",RpgTheme.GOLD);for(var actor:CubetableHud.MODEL.actors())line((actor.active()?"▶ ":"")+actor.name()+" · "+actor.initiative(),RpgTheme.MUTED);}
        line("Campaign characters · page "+(page+1),RpgTheme.GOLD);for(var value:array(data,"characters")){var row=value.getAsJsonObject();line(text(row,"name")+" · "+text(row,"kind")+" · "+(flag(row,"approved")?"approved":"needs approval")+" · "+(flag(row,"bound")?"bound":"unbound")+" · "+text(row,"health")+(flag(row,"inCombat")?" · in encounter":""),RpgTheme.TEXT);action("Inspect "+text(row,"name"),()->client.setScreen(new QuickPanelScreen(this,false,text(row,"character"))));}
        if(page>0)action("Previous",()->{page--;refresh();});if(flag(data,"hasMore"))action("Next",()->{page++;refresh();});action("Roster / Approvals / Bindings",()->client.setScreen(new CampaignRosterScreen(this,campaign)));action("Rest Requests",()->client.setScreen(new RestScreen(this,campaign)));action("Dice & Event Log",()->client.setScreen(new HudLogScreen(this)));action("Full Cubetable Menu",()->client.setScreen(new HubScreen(this)));
    }
}
