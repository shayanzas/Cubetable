package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import java.util.*;

/** Dedicated event history, expandable calculation and deliberate navigation. */
public final class HudLogScreen extends ManagementScreen {
    private HudSession.Filter filter=HudSession.Filter.ALL;
    private boolean follow=true;
    private int count;
    private long view;
    public HudLogScreen(Screen parent){super("Cubetable event log · current session",parent);}
    @Override protected void controls(){button("Filter","",frame.x()+84,frame.y()+frame.height()-30,70,20,()->client.setScreen(new ChoiceScreen(this,"Event filters",Arrays.stream(HudSession.Filter.values()).map(Enum::name).toList(),s->s,s->{filter=HudSession.Filter.valueOf(s);refresh();})));button("Follow / Read","",frame.x()+160,frame.y()+frame.height()-30,100,20,()->{follow=!follow;refresh();});button("Skip dice","",frame.x()+frame.width()-96,frame.y()+frame.height()-30,84,20,CubetableHud::skip);}
    @Override protected void refresh(){clearRows();fresh=true;var events=new ArrayList<>(CubetableHud.MODEL.history(filter));if(!CubetableHud.MODEL.members().isEmpty())row("Inspect party character","Private sheets are checked by the server before opening",true,"",()->partyCharacter());if(!CubetableHud.MODEL.campaign().isBlank())row("Campaign requests / assignments","Open authorized rest requests or control offers",true,"",()->client.setScreen(new ChoiceScreen(this,"Campaign activity",List.of("rests","assignments"),s->s,s->{if(s.equals("rests"))client.setScreen(new RestScreen(this,CubetableHud.MODEL.campaign()));else client.setScreen(new AssignmentScreen(this,CubetableHud.MODEL.campaign()));})));count=CubetableHud.MODEL.historySize();view=CubetableHud.MODEL.viewVersion();if(follow)Collections.reverse(events);for(var event:events)row(event.sequence()+" · "+event.kind(),event.roll()==null?event.summary():event.calculation(),true,"",()->client.setScreen(new HudEventScreen(this,event.id())));if(events.isEmpty())info("No visible events","Select a campaign and allow the bounded event poll to complete.");message="Current session only · "+filter+" · "+(follow?"newest first":"reading older events");}
    private void partyCharacter(){var members=CubetableHud.MODEL.members();client.setScreen(new ChoiceScreen(this,"Party character",members.stream().map(HudSession.Member::character).toList(),id->members.stream().filter(m->m.character().equals(id)).findFirst().map(HudSession.Member::name).orElse("Unavailable"),id->read(dev.tacticalcombat.uiapi.UiApiService.Operation.CHARACTER_GET,ManagementState.args("character",id),data->client.setScreen(CharacterDashboardScreen.campaign(this,id)))));}
    @Override public void tick(){super.tick();int next=CubetableHud.MODEL.historySize();if(next<count){refresh();}else if(view!=CubetableHud.MODEL.viewVersion()){if(follow)refresh();else message="New events arrived. Refresh or enable Follow to view them.";}}
}
