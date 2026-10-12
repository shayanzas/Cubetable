package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.function.Consumer;
import java.util.HashSet;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class PlayerPickerScreen extends ManagementScreen {
    private final String campaign;
    private final boolean assignment;
    private final Consumer<JsonObject> picked;
    public PlayerPickerScreen(Screen parent,String campaign,boolean assignment,Consumer<JsonObject> picked) {super(assignment?"Offer control to player":"Invite online player",parent);this.campaign=campaign;this.assignment=assignment;this.picked=picked;}
    @Override protected void refresh() {
        if(busy) return;fresh=false;clearRows();read(CAMPAIGN_GET,args("campaign",campaign),membership->read(ELIGIBLE_PLAYERS,args("campaign",campaign),data->{clearRows();var online=new HashSet<String>();
            for(var e:array(data,"players")) {var p=e.getAsJsonObject();boolean enabled=selectablePlayer(p,assignment);
                online.add(text(p,"id"));
                row(text(p,"name"),(flag(p,"member")?"Campaign member":"Not a member")+(flag(p,"inCombat")?" · in combat":""),enabled,"Control offers require an online member outside combat",()->returnChoice(parent,()->picked.accept(p.deepCopy())));
            }
            if(assignment) for(var e:array(membership,"members")) {String id=e.getAsString();if(online.contains(id)) continue;String name=player(membership,id);boolean known=!name.startsWith("Unknown");
                var p=args("id",id,"name",name,"member",true,"inCombat",false);row(name,"Offline member · may manually accept an offer after reconnecting",known,"Account name unavailable; wait for reconnect. Binding requires a loaded entity.",()->returnChoice(parent,()->picked.accept(p)));
            }
            message=assignment?"Online or known offline members. Offers do not bind; offline recipients must reconnect and accept manually.":"New invitations use online candidates. Existing offline members can be managed under Members.";
        }));
    }
}
