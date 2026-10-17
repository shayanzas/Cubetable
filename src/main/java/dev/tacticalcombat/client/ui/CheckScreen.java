package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.client.ClientRulesWatch;
import dev.tacticalcombat.uiapi.UiApiService;
import net.minecraft.client.gui.screen.Screen;
import java.util.UUID;
import java.util.function.Consumer;

/** Shares existing opaque permission invalidation; no cached private check panel survives revocation. */
abstract class CheckScreen extends ManagementScreen {
    protected CheckScreen(String title,Screen parent){super(title,parent);}
    @Override protected void read(UiApiService.Operation op,JsonObject args,Consumer<JsonObject> result){super.read(op,args,data->{if(data.has("panelToken"))ClientRulesWatch.panel(this,UUID.fromString(data.get("panelToken").getAsString()),revoked->{generation++;busy=false;fresh=false;clearRows();filter();message="Character or campaign access changed. Refresh to revalidate.";});result.accept(data);});}
    @Override public void removed(){ClientRulesWatch.close(this);clearRows();fresh=false;super.removed();}
}
