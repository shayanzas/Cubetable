package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Secondary resource detail; primary sheet retains one compact resources summary. */
public final class SheetTrackersScreen extends RpgScreen {
    private JsonObject snapshot;
    private final DashboardSource source;
    private CharacterCanvas pane;
    private boolean fresh;
    public SheetTrackersScreen(Screen parent,JsonObject snapshot,DashboardSource source){super("Conditions, Hit Dice & Resources",parent);this.snapshot=snapshot.deepCopy();this.source=source;}
    @Override protected void init(){super.init();clearChildren();busy=false;fresh=false;footer();button("Refresh","",frame.x()+frame.width()-86,frame.y()+8,74,20,this::reload);build();reload();}
    private void reload(){if(busy)return;fresh=false;request(source.read(),source.readArgs(),r->{if(r.code().equals("OK")){snapshot=SnapshotReplies.checked(source,r.data());fresh=true;build();}else message=r.message();});}
    private boolean editable(String section){return fresh&&!busy&&(source.library()||flag(snapshot,"canTrack")&&array(snapshot,"manualSections").asList().stream().anyMatch(v->v.getAsString().equals(section)));}
    private void change(RpgRecords.Change change){if(!editable(change.section()))return;fresh=false;request(source.manualOperation(),source.manualArgs(snapshot,change),r->{if(r.code().equals("OK")){snapshot=SnapshotReplies.checked(source,r.data());fresh=true;message="Manual value acknowledged; no combat effect applied.";build();}else message=r.message()+" Refresh before retrying.";});}
    private void build(){int offset=pane==null?0:pane.position();if(pane!=null)remove(pane);pane=addDrawableChild(new CharacterCanvas(new UiLayout.Rect(frame.x()+12,frame.y()+36,frame.width()-24,Math.max(24,frame.height()-74))));int y=6,w=pane.getWidth()-12;var t=SheetSections.read(snapshot.getAsJsonObject("sheet"),"trackers");counter("Exhaustion",RpgRecords.Action.EXHAUSTION,"",0,t.get("exhaustion").getAsInt(),6,y);y+=28;int i=0;for(var raw:array(t,"hitDice")){var die=raw.getAsJsonObject();counter("Hit Dice d"+text(die,"sides"),RpgRecords.Action.HIT_DICE,"",i++,die.get("current").getAsInt(),die.get("maximum").getAsInt(),y);y+=28;}for(var raw:array(t,"resources")){var p=raw.getAsJsonObject();if(text(p,"status").equals("MANUAL"))counter(text(p,"name"),RpgRecords.Action.RESOURCE,text(p,"id"),0,p.get("current").getAsInt(),p.get("maximum").getAsInt(),y);else pane.label(text(p,"name")+": "+text(p,"current")+" / "+text(p,"maximum"),6,y,w,RpgTheme.MUTED);y+=28;}for(var raw:array(SheetSections.read(snapshot.getAsJsonObject("sheet"),"features"),"entries")){var f=raw.getAsJsonObject();if(Set.of("CONDITION","TEMPORARY").contains(text(f,"kind"))){pane.control("condition:"+text(f,"id"),new RpgButton(6,y,w,19,text(f,"name"),"",()->client.setScreen(new FeatureInspectScreen(this,FeatureReference.recorded(f)))));y+=23;}}pane.wrapped("Manual counters only: these controls do not heal, recover resources, apply exhaustion penalties or run feature effects.",6,y,w,RpgTheme.MUTED);pane.position(offset);}
    private void counter(String name,RpgRecords.Action action,String key,int index,int current,int maximum,int y){int w=pane.getWidth()-12;pane.label(name+" · "+current+" / "+maximum,6,y+5,w-50,RpgTheme.GOLD);for(int delta:List.of(-1,1)){var b=pane.control(name+delta,new RpgButton(w-38+(delta>0?21:0),y,18,19,delta<0?"−":"+","",()->change(new RpgRecords.Change(action,"trackers",key,index,current+delta))));b.active=editable("trackers")&&(delta<0?current>0:current<maximum);}}
}
