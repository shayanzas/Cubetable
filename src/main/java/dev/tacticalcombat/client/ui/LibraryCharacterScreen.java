package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import dev.tacticalcombat.rules.srd.SrdCharacterCodec;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class LibraryCharacterScreen extends ManagementScreen {
    private final String id;
    private JsonObject draft;
    private RpgButton refreshButton;private CharacterCanvas pane;
    private final List<RpgNavigationTile> tiles=new ArrayList<>();
    private final List<RpgButton> buttons=new ArrayList<>();
    private boolean submitOnOpen;
    public LibraryCharacterScreen submitOnOpen(){submitOnOpen=true;return this;}
    private String validation="Finish Character checks your supported fields.";
    public LibraryCharacterScreen(Screen parent,String id) {super("Manage Character",parent);this.id=id;}
    String recordId() {return id;}
    @Override protected int maximumWidth(){return 900;}
    @Override protected void searchControls(){refreshButton=button("Refresh","",frame.x()+frame.width()-86,frame.y()+8,74,20,this::refresh).hint("Reload your personal characters; an updated server connection is required");}
    @Override protected void controls(){remove(list);if(draft!=null)display();}
    @Override protected void filter(){}
    @Override public void tick(){super.tick();if(refreshButton!=null)refreshButton.active=!busy&&dev.tacticalcombat.client.ClientUiApi.supported();if(busy||!fresh){for(var tile:tiles)tile.availability(false,"Refresh this personal character before acting.");for(var button:buttons)button.active=false;}}
    @Override protected void refresh() {if(busy) return;fresh=false;clearRows();read(LIBRARY_GET,args("character",id),data->{draft=DraftReplies.snapshot(data,id);validation=data.has("errors")?ManagementState.validation(data):"Finish Character checks your supported fields.";display();if(submitOnOpen){submitOnOpen=false;if(PlayerUiModel.complete(draft))client.setScreen(new CampaignBrowserScreen(this,this::submission));else message="Finish Character before submitting to a campaign.";}});}
    private void display(){
        int offset=pane==null?0:pane.position();String focus=pane==null?"":pane.focusedKey();if(pane!=null)remove(pane);pane=addDrawableChild(new CharacterCanvas(PlayerUiLayout.viewport(frame)));tiles.clear();buttons.clear();int x=6,w=pane.getWidth()-12,y=6;String name=text(draft,"name"),identity=PlayerUiModel.race(draft)+" · "+PlayerUiModel.clazz(draft);int nameLines=textRenderer.wrapLines(net.minecraft.text.Text.literal(name).formatted(net.minecraft.util.Formatting.BOLD),Math.max(1,w-66)).size(),metaLines=textRenderer.wrapLines(net.minecraft.text.Text.literal(identity),Math.max(1,w-20)).size(),header=72+nameLines*11+metaLines*12;
        pane.panel("Personal Character",x,y,w,header);pane.icon(PlayerUiModel.icon(draft),x+10,y+27,32);int ny=y+26;for(var line:textRenderer.getTextHandler().wrapLines(name,Math.max(1,w-66),net.minecraft.text.Style.EMPTY.withBold(true))){pane.boldLabel(line.getString(),x+54,ny,w-64,RpgTheme.GOLD);ny+=11;}pane.label(PlayerUiModel.statusLine(draft),x+54,ny+3,w-64,RpgTheme.TEXT);pane.wrapped(identity,x+10,Math.max(y+57,ny+23),w-20,RpgTheme.TEXT);y+=header+10;
        boolean complete=PlayerUiModel.complete(draft);String status=complete?"Ready to submit to a campaign.":array(draft,"errors").isEmpty()?"Ready for Review & Finish in the creator.":"Continue editing to complete the missing choices.";pane.wrapped(status,x,y,w,RpgTheme.MUTED);y+=textRenderer.wrapLines(net.minecraft.text.Text.literal(status),w).size()*12+10;
        int columns=w>=440?2:1,cw=(w-(columns-1)*10)/columns,h=PlayerUiLayout.tileHeight(cw);String[] titles={"Open Character Sheet",complete?"Edit Character":"Continue Creating","Duplicate Character","Submit to Campaign"};String[] descriptions={"View this personal character",PlayerUiModel.creator(draft)?(complete?"Edit details; finish again to submit":"Continue your saved choices"):"Edit the name; manual pack details stay in Tabletop Tools","Start a separate draft with these choices","Create an independent copy for your table"};String[] icons={"characters","creator:skills","creator:custom","campaigns"};Runnable[] actions={()->client.setScreen(PlayerNavigation.sheet(this,new DashboardSource(true,id))),this::edit,()->{long expected=revision(draft);confirm("Duplicate Character?","Create a separate draft of "+name+". The original and campaign copies stay unchanged. If the reply is lost, check My Characters before trying again.",()->mutate(LIBRARY_DUPLICATE,args("character",id,"revision",expected),data->client.setScreen(new LibraryCharacterScreen(PlayerNavigation.afterDelete(this),text(data,"id")))));},()->client.setScreen(new CampaignBrowserScreen(this,this::submission))};
        for(int i=0;i<4;i++){boolean enabled=i==0?PlayerUiModel.sheet(draft):i==2?!ambiguousCreation:i!=3||complete;String why=i==0?(complete?PlayerUiModel.primaryReason(draft):"Finish Character before opening its sheet"):i==2?"Check My Characters for a possibly created duplicate before repeating":"Finish Character before submitting";final Runnable action=actions[i];var tile=pane.control("manage-action:"+i,new RpgNavigationTile(x+i%columns*(cw+10),y+i/columns*(h+10),cw,h,titles[i],descriptions[i],icons[i],()->{if(fresh&&!busy)action.run();}));tile.availability(fresh&&!busy&&enabled,!fresh||busy?"Refresh this personal character before acting.":why);tiles.add(tile);}y+=((4+columns-1)/columns)*(h+10)+4;
        pane.boldLabel("Campaign Copies",x,y,w,RpgTheme.GOLD);y+=18;String note="Edits here affect only this personal character. Campaign copies have their own approval and progress.";pane.wrapped(note,x,y,w,RpgTheme.MUTED);y+=textRenderer.wrapLines(net.minecraft.text.Text.literal(note),w).size()*12+8;
        for(var e:array(draft,"submissions")){var copy=e.getAsJsonObject();String title=text(copy,"name")+" · "+text(copy,"campaignName"),detail=flag(copy,"approved")?"Approved · Open Campaign Character":"Awaiting Approval · Open Campaign Character";var b=pane.control("copy:"+text(copy,"id"),new RpgButton(x,y,w,42,title,detail,()->{if(fresh&&!busy){String target=text(copy,"id");client.setScreen(parent instanceof CampaignCharacterScreen c&&c.recordId().equals(target)?parent:new CampaignCharacterScreen(this,target));}}).hint(title+"\n"+detail));buttons.add(b);b.active=fresh&&!busy;y+=48;}
        if(array(draft,"submissions").isEmpty()){pane.label("No campaign copies yet.",x,y,w,RpgTheme.MUTED);y+=22;}if(flag(draft,"submissionsTruncated")){pane.wrapped("More copies are available in your campaign rosters.",x,y,w,RpgTheme.MUTED);y+=28;}
        y+=10;pane.panel("",x,y,w,100);pane.boldLabel("Delete Personal Character",x+10,y+10,w-20,RpgTheme.ERROR);pane.wrapped("Existing independent campaign copies will remain.",x+10,y+28,w-20,RpgTheme.MUTED);var delete=pane.control("delete",new RpgButton(x+10,y+66,w-20,24,"Delete Character","",()->{if(!fresh||busy)return;long expected=revision(draft);confirm("Delete Character?",name+" will be removed from My Characters permanently. Existing independent campaign copies and their bindings will remain.",()->mutate(LIBRARY_DELETE,args("character",id,"revision",expected),data->client.setScreen(PlayerNavigation.afterDelete(this))));}).hint("Delete only this personal character; campaign copies remain"));buttons.add(delete);delete.active=fresh&&!busy;pane.position(offset);if(!focus.isEmpty()){pane.focus(focus);setInitialFocus(pane);}
    }
    private void edit(){if(!fresh||busy)return;client.setScreen(PlayerNavigation.edit(this,draft));}
    @Override protected void request(dev.tacticalcombat.uiapi.UiApiService.Operation op,JsonObject a,java.util.function.Consumer<dev.tacticalcombat.uiapi.UiApiService.Reply> result){super.request(op,a,r->{result.accept(r);if(!r.code().equals("OK")){fresh=false;if(r.code().equals("FORBIDDEN")||r.code().equals("NOT_FOUND")){draft=null;if(pane!=null){remove(pane);pane=null;}tiles.clear();buttons.clear();}else if(draft!=null)display();}});}
    private void validate() {read(LIBRARY_VALIDATE,args("character",id),data->{validation=ManagementState.validation(data);display();client.setScreen(new InfoScreen(this,"Validation results","Supported fields only; not complete guided legality",validation));});}
    private void name() {
        var fields=List.of(new RecordFormScreen.Field("name","Character name",text(draft,"name"),80));
        client.setScreen(new RecordFormScreen(this,"Edit Character Name",LIBRARY_GET,args("character",id),fields,(state,values)->args("character",id,"revision",revision(state),"section","basics","data",args("name",values.get("name"))),LIBRARY_UPDATE,data->client.setScreen(this),"Save this personal character's name. Campaign copies stay unchanged.").expected(draft));
    }
    private void combat() {
        var sheet=draft.getAsJsonObject("sheet");var fields=new ArrayList<RecordFormScreen.Field>();
        for(var f:SrdAuthoring.fields()) if(!f.key().equals("name") && f.choices().isEmpty()) fields.add(new RecordFormScreen.Field(f.key(),f.label(),SrdAuthoring.value(sheet,f),12));
        client.setScreen(new RecordFormScreen(this,"SRD library inputs",LIBRARY_GET,args("character",id),fields,(state,values)->{
            var next=state.getAsJsonObject("sheet").deepCopy();for(var f:SrdAuthoring.fields()) if(values.containsKey(f.key())) next=SrdAuthoring.change(next,f,values.get(f.key()));SrdCharacterCodec.parseSheet(next.toString());
            return args("character",id,"revision",revision(state),"section","combat","data",SrdAuthoring.data(next));
        },LIBRARY_UPDATE,data->client.setScreen(this),"Canonical SRD numeric inputs. Equipment/catalog choices retain saved values; full wizard deferred.").expected(draft));
    }
    private void submission(JsonObject campaign) {
        if(!fresh||busy||!PlayerUiModel.complete(draft)){message="Finish Character before submitting this personal character.";return;}
        String target=text(campaign,"id");long sourceRevision=revision(draft);SubmissionBook book;
        try {book=ClientSubmissionBook.get();}catch(RuntimeException unavailable) {message=unavailable.getMessage();return;}
        var existing=book.find(id,target);
        // LIBRARY_GET was refreshed on returning from campaign selection. Recover an acknowledged or lost copy first.
        if(existing!=null) {
            for(var e:array(draft,"submissions")) {var copy=e.getAsJsonObject();if(text(copy.getAsJsonObject("origin"),"submission").equals(existing.token().toString())) {client.setScreen(new CampaignCharacterScreen(this,text(copy,"id")));return;}}
            if(existing.revision()!=sourceRevision) {message="An older-source submission must be reconciled. Inspect campaign copies, or duplicate the source for an explicit new copy.";return;}
        }
        confirm("Submit independent campaign copy?",text(draft,"name")+" → "+text(campaign,"name")+". This creates a separate campaign character. Source HP, XP, equipment and resources will not synchronize with it. Campaign Inspiration begins at zero for player submissions and is awarded by the DM. Submission may require DM approval. If a reply is lost, refresh this source and retry with the same submission identity.",()->{
            if(revision(draft)!=sourceRevision) {message="Source changed while confirming. Reconcile before starting another copy.";return;}
            var intent=book.intent(id,target,sourceRevision); // Atomic write must succeed before the packet can be queued.
            mutate(LIBRARY_SUBMIT,args("character",id,"revision",intent.revision(),"campaign",target,"submission",intent.token()),data->client.setScreen(new CampaignCharacterScreen(this,text(data,"id"))));
        });
    }
}
