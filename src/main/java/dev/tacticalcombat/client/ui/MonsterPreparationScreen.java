package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import java.util.UUID;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Source identity is explicit; a confirmation authorizes creation, approval and binding together. */
public final class MonsterPreparationScreen extends ManagementScreen {
    private final String actor;
    private final UUID operation=UUID.randomUUID();
    private String chosen;
    public MonsterPreparationScreen(Screen parent,String actor){super("Prepare SRD monster",parent);this.actor=actor;}
    @Override protected void refresh(){if(busy)return;clearRows();read(MONSTER_OPTIONS,args("actor",actor),data->{
        info(text(data,"actorName"),"Minecraft: "+text(data,"entityType")+" · choose its separate D&D identity");
        for(var value:array(data,"monsters")){var m=value.getAsJsonObject();String source=text(m,"id");
            info(text(m,"name")+" statistics","HP "+text(m,"hp")+" · AC "+text(m,"ac")+" · speed "+text(m,"speed")+" ft · "+text(m,"size")+" · STR/DEX/CON/INT/WIS/CHA "+m.get("abilities"));
            row("Prepare & bind "+text(m,"name"),text(m,"limitations"),chosen==null||chosen.equals(source),text(m,"limitations"),()->confirm("Prepare, approve and bind NPC?","Minecraft "+text(data,"entityType")+" will use D&D "+text(m,"name")+". "+text(m,"limitations")+" Creates an independent campaign NPC under your control. No entity is spawned or moved.",()->{chosen=source;mutate(MONSTER_PREPARE,args("actor",actor,"source",source,"operation",operation.toString()),reply->client.setScreen(parent));}));
        }filter();
    });}
}
