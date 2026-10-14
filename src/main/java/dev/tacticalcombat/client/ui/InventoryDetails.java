package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Shared item summary used by the creator picker and dashboard inventory tab. */
public final class InventoryDetails {
    public interface Actions {boolean editable();void quantity(int delta);void equip();void edit();void remove();}
    private InventoryDetails() {}
    public static void build(CharacterCanvas pane,JsonObject item,Actions actions){build(pane,item,actions,7);}
    public static void build(CharacterCanvas pane,JsonObject item,Actions actions,int top){int x=7,w=pane.getWidth()-14,y=top;if(item==null){pane.label("Select an equipment entry",x,y,w,RpgTheme.GOLD);return;}pane.panel("Equipment",x-2,y-2,w+4,76);pane.label(text(item,"name"),x+5,y+22,w-10,RpgTheme.GOLD);pane.label("Quantity: "+text(item,"quantity")+" · "+(flag(item,"equipped")?"Equipped":"Carried"),x+5,y+40,w-10,RpgTheme.TEXT);pane.label("Slot: "+(text(item,"slot").isBlank()?"Not set":text(item,"slot")),x+5,y+56,w-10,RpgTheme.MUTED);y+=85;String notes=text(item,"notes").isBlank()?"No description added.":text(item,"notes");pane.wrapped(notes,x,y,w,RpgTheme.TEXT);y+=net.minecraft.client.MinecraftClient.getInstance().textRenderer.getTextHandler().wrapLines(notes,w,net.minecraft.text.Style.EMPTY).size()*12+10;var minus=pane.control("quantity:-",new RpgButton(x,y,26,19,"−","",()->actions.quantity(-1)).accessible("Decrease quantity"));var plus=pane.control("quantity:+",new RpgButton(x+30,y,26,19,"+","",()->actions.quantity(1)).accessible("Increase quantity"));minus.active=plus.active=actions.editable();y+=25;var equip=pane.control("equip",new RpgButton(x,y,w,19,flag(item,"equipped")?"Unequip":"Equip","",actions::equip));equip.active=actions.editable();y+=23;var edit=pane.control("edit",new RpgButton(x,y,w,19,"Edit details","",actions::edit));edit.active=actions.editable();y+=23;var remove=pane.control("remove",new RpgButton(x,y,w,19,"Remove item","",actions::remove));remove.active=actions.editable();y+=26;pane.wrapped("Sheet equipment does not create physical items or change combat statistics.",x,y,w,RpgTheme.MUTED);}
}
