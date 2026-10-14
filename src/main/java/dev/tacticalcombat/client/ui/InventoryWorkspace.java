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
    private CharacterCanvas summary,picker;
    private final CurrencyDraft money;
    private int savedOffset,savedIndex;
    @Override protected boolean hasUnsavedChanges(){return money.dirty();}
    @Override protected void init(){if(list!=null){savedOffset=list.firstVisible();savedIndex=list.selectedIndex();}super.init();wizardSaveRail(CreatorFlow.Step.EQUIPMENT,this::saveCurrency);}
    private final List<RpgButton> categories=new ArrayList<>();
    public InventoryWorkspace(Screen parent,JsonObject snapshot,DashboardSource source){super("Inventory & Equipment",parent);this.snapshot=snapshot.deepCopy();this.source=source;money=new CurrencyDraft(SheetSections.read(snapshot.getAsJsonObject("sheet"),"inventory"));}
    @Override protected int maximumWidth(){return 980;}
    public static UiLayout.Rect[] geometry(UiLayout.Rect frame){int x=frame.x()+12,y=frame.y()+36,w=frame.width()-24,h=Math.max(2,frame.height()-74-CreatorFooter.extra(frame));boolean stacked=w<380;int left=stacked?w:(w-10)/2;return stacked?new UiLayout.Rect[]{new UiLayout.Rect(x,y,left,Math.max(1,(h-6)/2)),new UiLayout.Rect(x,y+Math.max(1,(h-6)/2)+6,w,Math.max(1,h-Math.max(1,(h-6)/2)-6))}:new UiLayout.Rect[]{new UiLayout.Rect(x,y,left,h),new UiLayout.Rect(x+left+10,y,w-left-10,h)};}
    @Override protected UiLayout.Rect listBounds(){return geometry(frame)[0];}
    @Override protected void searchControls(){footerAction("Refresh",2,this::refresh);}
    @Override protected void controls(){remove(list);picker=addDrawableChild(new CharacterCanvas(geometry(frame)[0]));int x=14,w=picker.getWidth()-28;picker.panel("Inventory",7,6,picker.getWidth()-14,Math.max(160,picker.getHeight()-12));var field=picker.control("inventory:search",new net.minecraft.client.gui.widget.TextFieldWidget(textRenderer,x,30,w,20,net.minecraft.text.Text.literal("Search inventory")));field.setText(query);field.setPlaceholder(net.minecraft.text.Text.literal("Search inventory…"));field.setMaxLength(80);field.setChangedListener(q->{query=q;display();});categories.clear();int filterW=(w-12)/4;for(var category:InventoryModel.Category.values())categories.add(picker.control("inventory:filter:"+category,new RpgButton(x+category.ordinal()*(filterW+4),55,filterW,19,category.name().charAt(0)+category.name().substring(1).toLowerCase(Locale.ROOT),"",()->{this.category=category;display();})));int toolbarWidth=(w-6)/2;picker.control("inventory:catalog",new RpgButton(x,79,toolbarWidth,32,"Add from Catalog","",this::catalog).primary(true).wrappedTitle().hint("Add from Catalog"));picker.control("inventory:custom",new RpgButton(x+toolbarWidth+6,79,toolbarWidth,32,"Add Custom Item","",()->{if(canEdit()&&!money.dirty())client.setScreen(InventoryForms.item(this,snapshot,source,null));else if(money.dirty())message="Save currency before opening an item editor.";}).wrappedTitle().hint("Add Custom Item"));list=picker.control("inventory:list",new RpgList(x,117,w,Math.max(32,picker.getHeight()-131),32,c->{if(!busy&&fresh){selected=c.id();details();}}));list.restorePosition(savedOffset,savedIndex);creatorContinue(CreatorFlow.Step.EQUIPMENT,this::saveCurrency);if(parent instanceof CharacterWizardScreen)footerAction("Save Draft",0,this::saveCurrency);}



    @Override protected void refresh(){if(busy)return;fresh=false;read(source.read(),source.readArgs(),data->{snapshot=SnapshotReplies.checked(source,data);money.accept(inventory(),null);display();});}
    private boolean canEdit(){return snapshot.has("sheet")&&fresh&&!busy&&DashboardModel.editable(snapshot,source.library(),self(),false);}
    private JsonObject inventory(){return SheetSections.read(snapshot.getAsJsonObject("sheet"),"inventory");}
    private void display(){if(list==null)return;if(!snapshot.has("sheet")){list.rows(List.of());return;}categories.forEach(b->b.selected(b==categories.get(category.ordinal())));var entries=InventoryModel.entries(inventory(),category,query);list.rows(entries.stream().map(e->new HubState.Choice(e.id(),e.label(),e.quantity()+" × · "+(e.equipped()?"Equipped":"Carried")+" · "+e.category().toLowerCase(Locale.ROOT),true,"Inspect item",0)).toList());if(InventoryModel.find(inventory(),selected)==null||entries.stream().noneMatch(e->e.id().equals(selected)))selected=entries.isEmpty()?"":entries.getFirst().id();int index=0;for(int i=0;i<entries.size();i++)if(entries.get(i).id().equals(selected))index=i;list.restorePosition(list.firstVisible(),index);details();}
    @Override protected void filter(){} // Display already filters the stable item projection.
    private void details(){int offset=summary==null?0:summary.position();if(summary!=null)remove(summary);summary=addDrawableChild(new CharacterCanvas(geometry(frame)[1]));String id=selected;money.controls(summary,textRenderer,6,canEdit(),this::saveCurrency);InventoryDetails.build(summary,InventoryModel.find(inventory(),id),new InventoryDetails.Actions(){public boolean editable(){return canEdit();}public void quantity(int delta){change(id,delta,false);}public void equip(){change(id,0,true);}public void edit(){if(canEdit())client.setScreen(InventoryForms.item(InventoryWorkspace.this,snapshot,source,InventoryModel.find(inventory(),id)));}public void remove(){if(canEdit())confirm("Remove item?","Remove this sheet entry only; physical inventory stays unchanged.",()->saveInventory(InventoryModel.remove(inventory(),id)));}},104);summary.position(offset);}
    private void change(String id,int delta,boolean equip){if(!canEdit())return;Runnable action=()->{if(!canEdit())return;saveInventory(equip?InventoryModel.equipped(inventory(),id):InventoryModel.quantity(inventory(),id,delta));};if(!source.library()&&flag(snapshot,"approved"))confirm("Change equipment?","This campaign edit may need DM approval again.",action);else action.run();}
    private void saveInventory(JsonObject data){if(!canEdit())return;mutate(source.edit(),source.sectionArgs(snapshot,"inventory",data),saved->{snapshot=SnapshotReplies.checked(source,saved);money.accept(inventory(),null);display();});}
    private void catalog(){if(money.dirty()){message="Save currency before opening the catalog.";return;}if(canEdit())client.setScreen(InventoryForms.catalog(this,snapshot,source));}
    private void saveCurrency(){saveCurrency(()->{});}
    private void saveCurrency(Runnable after){if(!canEdit()){message="Refresh before saving; currency retained.";return;}if(!money.dirty()){message="Draft saved.";after.run();return;}if(money.conflict()){long reviewed=revision(snapshot);confirm("Review changed currency?","Currency changed on the server. Replace those values with the retained entries?",()->{if(revision(snapshot)!=reviewed){message="Currency changed again; review the latest revision before saving.";return;}money.reviewed();saveCurrency(after);});return;}try{var sent=Map.copyOf(money.values);var data=money.data(inventory());fresh=false;saveDraftRequest(source.edit(),source.sectionArgs(snapshot,"inventory",data),saved->{snapshot=SnapshotReplies.checked(source,saved);fresh=true;money.accept(inventory(),sent);display();if(money.dirty()){saveCurrency(after);return false;}message="Draft saved.";return true;},after);}catch(RuntimeException invalid){message=invalid.getMessage();}}
    @Override public void close(){if(busy)return;if(money.dirty())confirm("Discard unsaved currency?","Saved money is unchanged.",()->returnFromWizard(CreatorFlow.Step.EQUIPMENT));else if(parent instanceof CharacterWizardScreen)returnFromWizard(CreatorFlow.Step.EQUIPMENT);else super.close();}
    @Override protected void request(dev.tacticalcombat.uiapi.UiApiService.Operation op,JsonObject args,java.util.function.Consumer<dev.tacticalcombat.uiapi.UiApiService.Reply> result){super.request(op,args,r->{if(!r.code().equals("OK")&&!r.code().equals("RATE_LIMIT")){fresh=false;if(r.code().equals("FORBIDDEN")){snapshot=new JsonObject();if(summary!=null){remove(summary);summary=null;}if(list!=null)list.rows(List.of());}}result.accept(r);});}
}
