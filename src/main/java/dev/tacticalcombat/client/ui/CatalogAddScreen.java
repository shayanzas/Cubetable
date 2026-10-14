package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Catalog intent is distinct from editing an existing quantity. Ambiguous acknowledgements require inventory reconciliation. */
public final class CatalogAddScreen extends ManagementScreen {
    private JsonObject snapshot;
    private final DashboardSource source;
    private final JsonObject blueprint;
    private CharacterCanvas details;
    private boolean uncertain;
    public CatalogAddScreen(Screen parent,JsonObject snapshot,DashboardSource source,JsonObject item){super("Add from Catalog",parent);this.snapshot=snapshot.deepCopy();this.source=source;blueprint=item.deepCopy();}
    @Override protected void searchControls(){}
    @Override protected void controls(){list.visible=false;build();}
    @Override protected void refresh(){if(busy)return;fresh=false;read(source.read(),source.readArgs(),data->{snapshot=SnapshotReplies.checked(source,data);build();});}
    private boolean editable(){return fresh&&!busy&&!uncertain&&DashboardModel.editable(snapshot,source.library(),self(),false);}
    private void build(){if(details!=null)remove(details);details=addDrawableChild(new CharacterCanvas(new UiLayout.Rect(frame.x()+12,frame.y()+36,frame.width()-24,Math.max(24,frame.height()-74))));int w=details.getWidth()-12;details.label(text(blueprint,"name"),6,6,w,RpgTheme.GOLD);details.wrapped(text(blueprint,"notes"),6,24,w,RpgTheme.TEXT);int y=24+textRenderer.getTextHandler().wrapLines(text(blueprint,"notes"),w,net.minecraft.text.Style.EMPTY).size()*12+12;details.wrapped("Add to Inventory combines with a matching ordinary unequipped stack. Keep as Separate Item preserves a distinct item with its own identity or properties.",6,y,w,RpgTheme.MUTED);y+=textRenderer.getTextHandler().wrapLines("Add to Inventory combines with a matching ordinary unequipped stack. Keep as Separate Item preserves a distinct item with its own identity or properties.",w,net.minecraft.text.Style.EMPTY).size()*12+8;var add=details.control("add",new RpgButton(6,y,w,20,"Add to Inventory","",()->add(false)).primary(true));add.active=editable();var separate=details.control("separate",new RpgButton(6,y+25,w,20,"Keep as Separate Item","",()->add(true)));separate.active=editable();if(uncertain)details.wrapped("Acknowledgement was incomplete. Return to Inventory and inspect the item before starting another add intent.",6,y+53,w,RpgTheme.ERROR);}
    private void add(boolean separate){if(!editable())return;Runnable action=()->{if(!editable())return;try{var inventory=SheetSections.read(snapshot.getAsJsonObject("sheet"),"inventory");var data=InventoryModel.catalogAddition(inventory,blueprint,separate);uncertain=true;mutate(source.edit(),source.sectionArgs(snapshot,"inventory",data),saved->{snapshot=SnapshotReplies.checked(source,saved);uncertain=false;client.setScreen(parent);});}catch(RuntimeException invalid){message=invalid.getMessage();}};if(!source.library()&&flag(snapshot,"approved"))confirm("Add campaign equipment?","This manual sheet edit may require DM approval again.",action);else action.run();}
    @Override protected void request(UiApiService.Operation op,JsonObject args,java.util.function.Consumer<UiApiService.Reply> consumer){super.request(op,args,r->{if(op==source.edit()&&!r.code().equals("OK")){if(!Set.of("TIMEOUT","INVALID_REPLY").contains(r.code()))uncertain=false;fresh=false;}consumer.accept(r);if(op==source.edit()&&!r.code().equals("OK"))build();});}
    @Override protected void filter(){}
}
