package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import dev.tacticalcombat.checks.CharacterChecks;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Bounded target picker and explicit adjudication settings, inside the existing Rolls workspace. */
public final class DmCheckScreen extends CheckScreen {
    private final Set<String> targets=new LinkedHashSet<>(),recipients=new LinkedHashSet<>();
    private boolean adjudicated;private int stage;private String mode="NORMAL",visibility="DM_ONLY";
    private CharacterChecks.Key check=new CharacterChecks.Key(CharacterChecks.Category.ABILITY,"str");
    public DmCheckScreen(Screen parent){super("DM Character Checks",parent);}
    @Override protected void refresh(){if(busy)return;clearRows();read(CHECK_TARGETS,args(),data->{
        row(adjudicated?"DM-adjudicated roll":"Request player checks","Switch mode; no combat actions or resources are affected",true,"",()->{adjudicated=!adjudicated;targets.clear();refresh();});
        if(stage==0){
            if(!adjudicated)row("Select all eligible online player characters","Maximum 16; larger campaigns require explicit batches",true,"",()->{var eligible=new ArrayList<String>();for(var v:array(data,"characters"))if(flag(v.getAsJsonObject(),"requestEligible"))eligible.add(text(v.getAsJsonObject(),"id"));if(eligible.size()>16){message="More than 16 targets: select a bounded group.";return;}targets.clear();targets.addAll(eligible);refresh();});
            for(var v:array(data,"characters")){var c=v.getAsJsonObject();String id=text(c,"id");boolean eligible=adjudicated||flag(c,"requestEligible");row((targets.contains(id)?"☑ ":"☐ ")+text(c,"name"),eligible?"Approved SRD character":"Offline or not bound to current controller",eligible,"Online bound controller required for player requests",()->{if(targets.contains(id))targets.remove(id);else if(adjudicated){targets.clear();targets.add(id);}else if(targets.size()<16)targets.add(id);refresh();});}
            row("Choose check →",targets.size()+" targets selected",!targets.isEmpty(),"Select at least one target",()->{stage=1;refresh();});
        }else if(stage==1){for(var k:CharacterChecks.keys())row(k.category()+" · "+k.id(),"Eligibility and bonus validated independently for each target",true,"",()->{check=k;stage=2;refresh();});}
        else{
            info("Request identity","Identical targets/check/mode/purpose/visibility/DC reuse the same group for 10 minutes, even after completion. Use a distinct purpose for a deliberate new situation.");
            info("Selected check",check.category()+" · "+check.id()+" · "+targets.size()+" target(s)");
            row("Mode: "+mode,"Explicit DM adjudication; no automated condition effects",true,"",()->{mode=switch(mode){case "NORMAL"->"ADVANTAGE";case "ADVANTAGE"->"DISADVANTAGE";default->"NORMAL";};refresh();});
            row("Visibility: "+visibility,"DC and success/failure are always DM-only",true,"",()->{visibility=switch(visibility){case "DM_ONLY"->"CAMPAIGN";case "CAMPAIGN"->"RECIPIENTS";default->"DM_ONLY";};refresh();});
            if(visibility.equals("RECIPIENTS"))for(var v:array(data,"members")){var m=v.getAsJsonObject();String id=text(m,"id");row((recipients.contains(id)?"☑ ":"☐ ")+text(m,"name"),"Specific result recipient; current DMs also receive results",true,"",()->{if(!recipients.remove(id)&&recipients.size()<16)recipients.add(id);refresh();});}
            row("Purpose / hidden DC →","Review and send the request or prepare the adjudicated roll",!targets.isEmpty(),"Select targets",()->form(data));
        }
        if(stage>0)row("← Back","",true,"",()->{stage--;refresh();});row("Requests & Results","Inspect pending and completed group members",true,"",()->client.setScreen(new CheckInboxScreen(this)));filter();
    });}
    private void form(JsonObject snapshot){
        client.setScreen(new RecordFormScreen(this,adjudicated?"Prepare DM roll":"Send roll requests",CHECK_TARGETS,args(),List.of(new RecordFormScreen.Field("purpose","Purpose (optional)","",80),new RecordFormScreen.Field("dc","Hidden DC (blank for none)","",3)),(current,values)->{
            var recipientsJson=new JsonArray();if(visibility.equals("RECIPIENTS"))recipients.forEach(recipientsJson::add);Integer dc=values.get("dc").isBlank()?null:Integer.valueOf(values.get("dc"));var spec=args("category",check.category(),"key",check.id(),"mode",mode,"purpose",values.get("purpose"),"visibility",visibility,"recipients",recipientsJson,"dc",dc);
            if(adjudicated){String id=targets.iterator().next();JsonObject chosen=null;for(var v:array(current,"characters"))if(text(v.getAsJsonObject(),"id").equals(id))chosen=v.getAsJsonObject();if(chosen==null)throw new IllegalArgumentException("Target unavailable; return and refresh");return args("character",id,"revision",revision(chosen),"spec",spec,"adjudicated",true);}
            var ids=new JsonArray();targets.forEach(ids::add);return args("characters",ids,"spec",spec);
        },adjudicated?CHECK_PREPARE:CHECK_REQUEST,r->client.setScreen(new CheckInboxScreen(this)),"No automatic effects. DC/outcome private to current DMs. Review each participant result in the inbox.").saveLabel(adjudicated?"Prepare roll":"Send requests"));
    }
}
