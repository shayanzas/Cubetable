package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.DrawContext;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Search/category picker on the left; selected equipment and acknowledged controls on the right. */
public final class InventoryWorkspace extends ManagementScreen {
    private final DashboardSource source;
    private JsonObject snapshot;
    private InventoryModel.Category category=InventoryModel.Category.ALL;
    private String selected="";
    private CharacterCanvas summary;
    private int savedOffset,savedIndex;
    @Override protected void init(){if(list!=null){savedOffset=list.firstVisible();savedIndex=list.selectedIndex();}super.init();}
    private final List<RpgButton> categories=new ArrayList<>();
    public InventoryWorkspace(Screen parent,JsonObject snapshot,DashboardSource source){super("Inventory & Equipment",parent);this.snapshot=snapshot.deepCopy();this.source=source;}
    @Override protected int maximumWidth(){return 980;}
    public static UiLayout.Rect[] geometry(UiLayout.Rect frame){int x=frame.x()+12,y=frame.y()+80,w=frame.width()-24,h=Math.max(2,frame.height()-118);boolean stacked=w<380;int left=stacked?w:(w-10)/2;return stacked?new UiLayout.Rect[]{new UiLayout.Rect(x,y,left,Math.max(1,(h-6)/2)),new UiLayout.Rect(x,y+Math.max(1,(h-6)/2)+6,w,Math.max(1,h-Math.max(1,(h-6)/2)-6))}:new UiLayout.Rect[]{new UiLayout.Rect(x,y,left,h),new UiLayout.Rect(x+left+10,y,w-left-10,h)};}
    @Override protected UiLayout.Rect listBounds(){return geometry(frame)[0];}
    @Override protected void searchControls(){search(frame.x()+12,frame.y()+36,frame.width()-110,query,s->{query=s;display();});button("Refresh","",frame.x()+frame.width()-86,frame.y()+36,74,20,this::refresh);}
    @Override protected void controls(){remove(list);var bounds=listBounds();list=addDrawableChild(new RpgList(bounds.x(),bounds.y(),bounds.width(),bounds.height(),32,c->{if(!busy&&fresh){selected=c.id();details();}}));list.restorePosition(savedOffset,savedIndex);categories.clear();int x=frame.x()+12,w=(frame.width()-42)/4;for(var c:InventoryModel.Category.values())categories.add(button(c.name().charAt(0)+c.name().substring(1).toLowerCase(Locale.ROOT),"",x+c.ordinal()*(w+6),frame.y()+61,w,19,()->{category=c;display();}));int slot=Math.max(24,(frame.width()-108)/3);int fy=frame.y()+frame.height()-30;button("Browse","",frame.x()+84,fy,slot,20,this::catalog).hint("Browse supported equipment");button("Add item","",frame.x()+90+slot,fy,slot,20,()->{if(canEdit())client.setScreen(InventoryForms.item(this,snapshot,source,null));});button("Currency","",frame.x()+96+slot*2,fy,slot,20,()->{if(canEdit())client.setScreen(InventoryForms.currency(this,snapshot,source));});}

    @Override protected void refresh(){if(busy)return;fresh=false;read(source.read(),source.readArgs(),data->{snapshot=SnapshotReplies.checked(source,data);display();});}
    private boolean canEdit(){return snapshot.has("sheet")&&fresh&&!busy&&DashboardModel.editable(snapshot,source.library(),self(),false);}
    private JsonObject inventory(){return SheetSections.read(snapshot.getAsJsonObject("sheet"),"inventory");}
    private void display(){if(list==null)return;if(!snapshot.has("sheet")){list.rows(List.of());return;}categories.forEach(b->b.selected(b==categories.get(category.ordinal())));var entries=InventoryModel.entries(inventory(),category,query);list.rows(entries.stream().map(e->new HubState.Choice(e.id(),e.label(),e.quantity()+" × · "+(e.equipped()?"Equipped":"Carried")+" · "+e.category().toLowerCase(Locale.ROOT),true,"Inspect item",0)).toList());if(InventoryModel.find(inventory(),selected)==null||entries.stream().noneMatch(e->e.id().equals(selected)))selected=entries.isEmpty()?"":entries.getFirst().id();int index=0;for(int i=0;i<entries.size();i++)if(entries.get(i).id().equals(selected))index=i;list.restorePosition(list.firstVisible(),index);details();}
    @Override protected void filter(){} // Display already filters the stable item projection.
    private void details(){int offset=summary==null?0:summary.position();if(summary!=null)remove(summary);summary=addDrawableChild(new CharacterCanvas(geometry(frame)[1]));String id=selected;InventoryDetails.build(summary,InventoryModel.find(inventory(),id),new InventoryDetails.Actions(){public boolean editable(){return canEdit();}public void quantity(int delta){change(id,delta,false);}public void equip(){change(id,0,true);}public void edit(){if(canEdit())client.setScreen(InventoryForms.item(InventoryWorkspace.this,snapshot,source,InventoryModel.find(inventory(),id)));}public void remove(){if(canEdit())confirm("Remove item?","Remove this sheet entry only; physical inventory stays unchanged.",()->saveInventory(InventoryModel.remove(inventory(),id)));}});summary.position(offset);}
    private void change(String id,int delta,boolean equip){if(!canEdit())return;Runnable action=()->{if(!canEdit())return;saveInventory(equip?InventoryModel.equipped(inventory(),id):InventoryModel.quantity(inventory(),id,delta));};if(!source.library()&&flag(snapshot,"approved"))confirm("Change equipment?","This campaign edit may need DM approval again.",action);else action.run();}
    private void saveInventory(JsonObject data){if(!canEdit())return;mutate(source.edit(),source.sectionArgs(snapshot,"inventory",data),saved->{snapshot=SnapshotReplies.checked(source,saved);display();});}
    private void catalog(){if(canEdit())client.setScreen(InventoryForms.catalog(this,snapshot,source));}
    @Override protected void request(dev.tacticalcombat.uiapi.UiApiService.Operation op,JsonObject args,java.util.function.Consumer<dev.tacticalcombat.uiapi.UiApiService.Reply> result){super.request(op,args,r->{if(!r.code().equals("OK")&&!r.code().equals("RATE_LIMIT")){fresh=false;if(r.code().equals("FORBIDDEN")){snapshot=new JsonObject();if(summary!=null){remove(summary);summary=null;}if(list!=null)list.rows(List.of());}}result.accept(r);});}
}
