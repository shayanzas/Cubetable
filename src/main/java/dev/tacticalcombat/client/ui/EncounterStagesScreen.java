package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Explicit template editing and named actor selection; never accepts teleport coordinates. */
public final class EncounterStagesScreen extends ManagementScreen {
    private final String stage;
    public EncounterStagesScreen(Screen parent){this(parent,null);}
    private EncounterStagesScreen(Screen parent,String stage){super(stage==null?"Saved encounter stages":"Encounter stage",parent);this.stage=stage;}
    @Override protected void refresh(){if(busy)return;clearRows();read(stage==null?STAGE_LIST:STAGE_GET,stage==null?args():args("stage",stage),data->{
        if(stage==null){
            row("Save new stage here","Uses your current dimension and location; does not move actors",true,"",()->edit(null));
            for(var value:array(data,"stages")){var s=value.getAsJsonObject();row(text(s,"name"),text(s,"dimension")+" · "+array(s,"slots").size()+" slots",true,"",()->client.setScreen(new EncounterStagesScreen(this,text(s,"id"))));}
        }else{
            var s=data.getAsJsonObject("stage");info(text(s,"name"),text(s,"description"));info("Location",text(s,"dimension")+" · "+text(s,"x")+", "+text(s,"y")+", "+text(s,"z"));
            int n=0;for(var value:array(s,"slots")){var slot=value.getAsJsonObject();row("Slot "+(++n)+" · "+text(slot,"role"),text(slot,"x")+", "+text(slot,"y")+", "+text(slot,"z")+" · "+text(slot,"monster"),true,"Edit role label",()->client.setScreen(new RecordFormScreen(this,"Edit actor role",STAGE_GET,args("stage",stage),List.of(new RecordFormScreen.Field("role","Role label",text(slot,"role"),64)),(snapshot,values)->args("stage",stage,"revision",snapshot.getAsJsonObject("stage").get("revision").getAsLong(),"slot",text(slot,"id"),"role",values.get("role")),STAGE_ROLE,reply->client.setScreen(this),"Rename this placement slot; no actor movement.")));}
            row("Edit name and description","Revision-safe template edit",true,"",()->edit(s));
            row("Capture actor placements","Replace slots with selected actors' current safe grid positions",true,"Saving does not move actors",()->client.setScreen(new ActorSelection(this,s,false)));
            row("Choose actors & validate","Select one compatible actor per slot, in displayed slot order",array(s,"slots").size()>0,"Capture slots first",()->client.setScreen(new ActorSelection(this,s,true)));
            row("Open encounter launcher","Stage activation does not start combat",true,"",()->client.setScreen(new EncounterLauncherScreen(this,true)));
            row("Delete stage","Does not delete actors or characters",true,"",()->confirm("Delete saved stage?",text(s,"name"),()->mutate(STAGE_DELETE,args("stage",stage,"revision",s.get("revision").getAsLong()),reply->client.setScreen(parent))));
        }filter();
    });}
    private void edit(JsonObject s){String id=s==null?UUID.randomUUID().toString():text(s,"id");client.setScreen(new RecordFormScreen(this,s==null?"Save stage here":"Edit stage",s==null?STAGE_LIST:STAGE_GET,s==null?args():args("stage",id),List.of(new RecordFormScreen.Field("name","Stage name",s==null?"Encounter stage":text(s,"name"),64),new RecordFormScreen.Field("description","Description",s==null?"":text(s,"description"),512)),(snapshot,values)->{var a=args("stage",id,"name",values.get("name"),"description",values.get("description"));if(s!=null)a.addProperty("revision",snapshot.getAsJsonObject("stage").get("revision").getAsLong());return a;},s==null?STAGE_CREATE:STAGE_EDIT,reply->client.setScreen(this),"Stage templates do not start combat or move actors."));}
    private static final class ActorSelection extends ManagementScreen {
        private final JsonObject stage;private final boolean activate;private final LinkedHashSet<String> selected=new LinkedHashSet<>();
        ActorSelection(Screen parent,JsonObject stage,boolean activate){super(activate?"Assign actors in slot order":"Capture current placements",parent);this.stage=stage.deepCopy();this.activate=activate;}
        @Override protected void refresh(){if(busy)return;clearRows();read(ENCOUNTER_DISCOVER,args("binding","ALL","kind","ALL"),data->{
            if(activate){int n=0;for(var value:array(stage,"slots")){var slot=value.getAsJsonObject();info("Slot "+(++n)+" · "+text(slot,"role"),text(slot,"monster")+" @ "+text(slot,"x")+", "+text(slot,"y")+", "+text(slot,"z"));}}
            info("Selection order",selected.isEmpty()?"Choose actors below":"Selected "+selected.size()+" actors; numbered rows show slot assignments");
            for(var value:array(data,"actors")){var actor=value.getAsJsonObject();String id=text(actor,"actor");int index=new ArrayList<>(selected).indexOf(id);row((index<0?"□ ":(index+1)+" · ")+text(actor,"name"),text(actor,"characterName")+" · "+text(actor,"reason"),flag(actor,"eligible"),text(actor,"reason"),()->{if(!selected.remove(id)&&selected.size()<16)selected.add(id);refresh();});}
            row(activate?"Validate placement":"Save placement slots",activate?"Preview only; confirmation required before movement":"Replaces saved slots without moving anyone",!selected.isEmpty(),"Select actors first",()->{
                var a=args("stage",text(stage,"id"),"revision",stage.get("revision").getAsLong());var ids=new JsonArray();selected.forEach(ids::add);a.add("actors",ids);
                if(activate)mutate(STAGE_VALIDATE,a,reply->confirm("Activate stage?","Move "+text(reply,"count")+" actors to the displayed saved positions (maximum 16 blocks each). No combat starts. The server revalidates all actors and destinations.",()->mutate(STAGE_ACTIVATE,args("token",text(reply,"token")),done->client.setScreen(parent))));
                else confirm("Save selected placements?","Replace this stage's slots with selected actors' current positions. No movement or combat occurs.",()->mutate(STAGE_CAPTURE,a,done->client.setScreen(parent)));
            });filter();
        });}
    }
}
