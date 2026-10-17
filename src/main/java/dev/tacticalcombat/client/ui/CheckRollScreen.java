package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Retains operation identity across refresh/resize. Never auto-rerolls an ambiguous reply. */
public final class CheckRollScreen extends CheckScreen {
    private final String character;private final JsonObject key;private final boolean adjudicated;private String operation="";private JsonObject outcome;
    public CheckRollScreen(Screen parent,String character,JsonObject key,boolean adjudicated){super("Character Check",parent);this.character=character;this.key=key.deepCopy();this.adjudicated=adjudicated;}
    public static String breakdown(JsonObject m){return text(m,"ability").toUpperCase()+" "+text(m,"abilityModifier")+" + proficiency "+text(m,"proficiencyBonus")+" × "+text(m,"multiplier")+" = "+text(m,"total")+" · "+text(m,"source");}
    public static String result(JsonObject r){return text(r,"source")+" · "+text(r,"mode")+" · d20 "+r.get("faces")+" · kept indices "+r.get("kept")+" · "+breakdown(r.getAsJsonObject("modifier"))+" → total "+text(r,"total")+(r.has("dc")&&!r.get("dc").isJsonNull()?" · DC "+text(r,"dc")+" · success "+text(r,"success"):"")+" · operation "+text(r,"operation");}
    @Override protected void refresh(){if(busy)return;clearRows();outcome=null;read(CHECK_OPTIONS,args("character",character),data->{
        info(text(key,"category")+" · "+text(key,"id"),adjudicated?"DM-adjudicated check; private to current DMs":"Player character check; private to self and current DMs");
        JsonObject current=null;for(var v:array(data,"checks")){var c=v.getAsJsonObject();if(text(c,"category").equals(text(key,"category"))&&text(c,"id").equals(text(key,"id")))current=c;}if(current==null||!flag(current,"available")){info("Unavailable",current==null?"Check unavailable":text(current,"reason"));filter();return;}info("Validated modifier",breakdown(current.getAsJsonObject("modifier")));
        if(!operation.isEmpty())row("Retrieve / retry the same operation","The same accepted ID cannot reroll",true,"",this::execute);
        else for(String mode:java.util.List.of("NORMAL","ADVANTAGE","DISADVANTAGE"))row(mode,"Server-owned D20; no resource or combat effect",true,"",()->{
            var recipients=new JsonArray();if(!adjudicated)recipients.add(self());var spec=args("category",text(key,"category"),"key",text(key,"id"),"mode",mode,"purpose","","visibility",adjudicated?"DM_ONLY":"RECIPIENTS","recipients",recipients,"dc",null);
            mutate(CHECK_PREPARE,args("character",character,"revision",revision(data),"spec",spec,"adjudicated",adjudicated),reply->{operation=text(reply,"operation");execute();});});
        row("Requests & Result History","Reconcile accepted outcomes before starting another roll",true,"",()->client.setScreen(new CheckInboxScreen(this)));filter();
    });}
    private void execute(){request(CHECK_EXECUTE,args("operation",operation),reply->{clearRows();fresh=reply.code().equals("OK");if(fresh){if(reply.data().has("result")&&!reply.data().get("result").isJsonNull()){outcome=reply.data().getAsJsonObject("result");info("Accepted server result",result(outcome));}else info("Accepted","Result restricted to authorized recipients.");row("Start a new deliberate check","Creates a separate operation",true,"",()->{operation="";refresh();});}else{message=reply.message()+" Refresh to retry this same operation.";}filter();});}
}
