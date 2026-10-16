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
    @Override protected void init(){super.init();if(dm){client.setScreen(new DmWorkspaceScreen(parent));return;}frame=QuickPanelLayout.frame(width,height);clearChildren();busy=false;data=null;footer();button("Refresh","",frame.x()+frame.width()-84,frame.y()+8,72,18,this::refresh);build();refresh();}
    @Override protected void background(DrawContext g){RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),0xEF191D23,false);RpgTheme.text(g,textRenderer,getTitle().getString(),frame.x()+10,frame.y()+12,frame.width()-100,RpgTheme.GOLD);}
    @Override public void removed(){ClientRulesWatch.close(this);data=null;super.removed();}
    @Override public boolean keyPressed(int key,int scan,int modifiers){var binding=dm?TacticalCombatClient.DM_QUICK_KEY:TacticalCombatClient.QUICK_SHEET_KEY;if(binding.matchesKey(key,scan)){close();return true;}var other=dm?TacticalCombatClient.QUICK_SHEET_KEY:TacticalCombatClient.DM_QUICK_KEY;if(other.matchesKey(key,scan)){client.setScreen(new QuickPanelScreen(parent,!dm));return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void tick(){super.tick();if(client.player==null||client.world==null){data=null;close();return;}if(refreshPending&&!busy){refreshPending=false;refresh();}}
    private void refresh(){if(busy)return;data=null;build();request(dm?DM_QUICK:QUICK_SHEET,dm?args("page",page):inspect==null?args():args("character",inspect),r->{if(r.code().equals("OK")){data=r.data();message="Server-authorized view";if(data.has("panelToken"))ClientRulesWatch.panel(this,UUID.fromString(text(data,"panelToken")),revoked->{if(client.currentScreen!=this)return;generation++;busy=false;data=null;build();refreshPending=!revoked;message=revoked?"Access or binding changed. Refresh to recheck.":"Character changed; refreshing…";});}else{ClientRulesWatch.close(this);data=null;}build();});}
    private int w(){return canvas.getWidth()-16;}
    private void line(String text,int color){canvas.wrapped(text,8,y,w(),color);y+=Math.max(1,textRenderer.wrapLines(net.minecraft.text.Text.literal(text),w()).size())*12+4;}
    private void action(String title,Runnable task){canvas.control("action:"+y,new RpgButton(8,y,w(),24,title,"",()->{if(!busy&&data!=null)task.run();}).wrappedTitle());y+=30;}
    private void build(){if(frame==null)return;int offset=canvas==null?0:canvas.position();if(canvas!=null)remove(canvas);canvas=addDrawableChild(new CharacterCanvas(new UiLayout.Rect(frame.x()+6,frame.y()+34,frame.width()-12,Math.max(30,frame.height()-82))));y=4;if(data==null){line("Loading or unavailable. Refresh to check current access.",RpgTheme.MUTED);return;}if(data.has("empty")){line(text(data,"empty"),RpgTheme.MUTED);action("Open Cubetable",()->client.setScreen(new HubScreen(this)));return;}
        sheetBody();canvas.position(offset);
    }
    private void sheetBody(){y=CompactSheet.draw(canvas,data,y);
        action("Abilities, Skills & Saves",()->client.setScreen(new CharacterRollMenuScreen(this,data)));
        action("Manual Dice",()->client.setScreen(new DiceToolbarScreen(this,true)));
        if(ClientSrdCombat.hudEncounter()!=null)action("Combat Controls",()->client.setScreen(new SrdCombatScreen(this)));
        action("Full Character Sheet",()->client.setScreen(PlayerNavigation.sheet(this,new DashboardSource(false,text(data,"id")))));
        action("SRD Reference",()->client.setScreen(new ReferenceCatalogScreen(this)));
    }
}
