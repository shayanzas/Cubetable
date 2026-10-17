package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Permission-filtered session inbox; no global/private client result cache. */
public final class CheckInboxScreen extends CheckScreen {
    private int page,ticks;private boolean baselineNext=true;private final java.util.Set<String> observedResults=new java.util.HashSet<>();
    public CheckInboxScreen(Screen parent){super("Character Checks · Requests & Results",parent);}
    @Override protected void refresh(){if(busy)return;clearRows();read(CHECK_INBOX,args("page",page),data->{
        info("Session history","Requests and outcomes expire after 10 minutes. Restarted or expired IDs cannot be rerolled. Refresh rechecks access.");
        for(var v:array(data,"rolls")){var view=v.getAsJsonObject();var t=view.getAsJsonObject("ticket");var spec=t.getAsJsonObject("spec");String id=text(t,"id"),title=text(spec.getAsJsonObject("check"),"id")+" · "+text(view,"status");
            if(view.has("result")&&!view.get("result").isJsonNull()){var accepted=view.getAsJsonObject("result");if(!baselineNext&&observedResults.add(id))dev.tacticalcombat.client.DiceAnimation.enqueueCheck(accepted);else observedResults.add(id);info(title,CheckRollScreen.result(accepted));}
            else if(text(view,"status").equals("PENDING")){info(title,text(spec,"purpose")+" · "+text(spec,"mode")+" · "+text(spec,"visibility")+" · character "+text(t,"character"));if(text(t,"executor").equals(self()))row("Roll requested "+text(spec.getAsJsonObject("check"),"id"),"Server will recheck current character and requester authority",true,"",()->mutate(CHECK_EXECUTE,args("operation",id),r->{if(r.has("result")&&!r.get("result").isJsonNull())dev.tacticalcombat.client.DiceAnimation.enqueueCheck(r.getAsJsonObject("result"));refresh();}));if(text(t,"executor").equals(self())||flag(data,"manager"))row("Dismiss request","No roll or gameplay cost; cannot reopen this ID",true,"",()->mutate(CHECK_DISMISS,args("operation",id),r->refresh()));}
            else info(title,"Result is restricted or request was dismissed.");
        }
        baselineNext=false;if(page>0)row("Previous","",true,"",()->{page--;baselineNext=true;refresh();});if(flag(data,"hasMore"))row("Next","",true,"",()->{page++;baselineNext=true;refresh();});filter();
    });}
    @Override public void tick(){super.tick();if(++ticks>=100&&!busy){ticks=0;fresh=false;clearRows();filter();refresh();}}
}
