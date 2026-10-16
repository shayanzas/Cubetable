package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import dev.tacticalcombat.client.ClientRulesWatch;
import java.util.UUID;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Review only: persisted outcomes cannot be rerolled or automatically applied after restart. */
public final class GameplayRecoveryScreen extends ManagementScreen {
    private int page;
    private boolean refreshPending;
    @Override public void removed(){ClientRulesWatch.close(this);super.removed();}
    @Override public void tick(){super.tick();if(refreshPending&&!busy){refreshPending=false;refresh();}}
    public GameplayRecoveryScreen(Screen parent){super("Gameplay Transaction Review",parent);}
    @Override protected void refresh(){if(busy)return;fresh=false;read(GAMEPLAY_RECOVERY,args("page",page),data->{ClientRulesWatch.panel(this,UUID.fromString(text(data,"panelToken")),revoked->{if(client.currentScreen!=this)return;generation++;busy=false;fresh=false;clearRows();filter();refreshPending=!revoked;message=revoked?"Campaign access changed. Refresh to recheck.":"Transactions changed; refreshing…";});clearRows();info("Prepared actions reserve both characters","After ending the old encounter, a DM may cancel with an audit record. This applies no damage or refunds. Use live Retry before ending when recovery is appropriate.");info("Receipt history: "+text(data,"stored")+" / "+text(data,"limit"),"Receipts are never evicted. At capacity, new preparations stop safely.");for(var value:array(data,"operations")){var r=value.getAsJsonObject();row(text(r,"status")+" · "+text(r,"attacker")+" → "+text(r,"target"),text(r,"weapon")+" · "+text(r,"outcome")+" · "+text(r,"resolution"),flag(r,"canCancel"),flag(r,"canCancel")?"Review audited cancellation":"Only unresolved preparations from closed encounters can be cancelled",()->confirm("Cancel this prepared operation?",text(r,"outcome")+". Gameplay resources remain unchanged. The receipt and fixed rolls are retained; cancellation is audited as your DM decision.",()->request(GAMEPLAY_CANCEL,args("operation",text(r,"operation"),"reason","DM cancelled after encounter closure for manual adjudication"),reply->refresh())));}if(page>0)row("Previous","",true,"",()->{page--;refresh();});if(flag(data,"hasMore"))row("Next","",true,"",()->{page++;refresh();});filter();});}
}
