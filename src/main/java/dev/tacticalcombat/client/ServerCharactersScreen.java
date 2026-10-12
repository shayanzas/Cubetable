package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.CharacterReplyPayload;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;

/** Separate authoritative workspace: server refresh never mutates the local pack/editor object. */
public final class ServerCharactersScreen extends Screen {
    private final Screen parent;
    private final JsonObject local;
    private final CharacterWorkspace workspace=new CharacterWorkspace();
    private String view="campaigns", message="Choose a campaign. Local sheets remain independent.";
    private JsonArray rows=new JsonArray();
    private JsonObject campaign, pendingCampaign;
    private boolean fieldDirty;
    private UUID selectedCharacter;
    private int page,total,offset;
    private TextFieldWidget field,value;
    private final List<ClickableWidget> operations=new ArrayList<>();
    private boolean requested;
    private boolean srdAuthoring;
    public ServerCharactersScreen(Screen parent,JsonObject local) {
        super(Text.literal("Server characters")); this.parent=parent; this.local=local==null?null:local.deepCopy();
    }
    private int panelWidth() { return Math.min(440,width-16); }
    private int left() { return (width-panelWidth())/2; }
    /** Foreground is drawn before super.render; skip its late default background blur. */
    @Override public void renderBackground(DrawContext g,int mx,int my,float delta) { }
    @Override public boolean shouldPause() { return false; }
    @Override protected void init() {
        rebuild();
        if(!requested) { requested=true; campaigns(0); }
    }
    private void button(String label,int column,int columns,int y,Runnable action) {
        int gap=4,w=(panelWidth()-gap*(columns-1))/columns;
        var button=ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(left()+column*(w+gap),y,w,20).build();
        operations.add(addDrawableChild(button));
    }
    private void rebuild() {
        boolean newFields=field==null;
        String key=field==null?"text.name":field.getText(), val=value==null?"":value.getText();
        clearChildren(); operations.clear(); field=null; value=null;
        if(view.equals("detail") && workspace.loaded()) {
            field=addDrawableChild(new TextFieldWidget(textRenderer,left(),height-126,panelWidth()/2-46,20,Text.literal("Field path")));
            field.setMaxLength(90); field.setText(key);
            value=addDrawableChild(new TextFieldWidget(textRenderer,left()+panelWidth()/2+2,height-126,panelWidth()/2-2,20,Text.literal("New value")));
            value.setMaxLength(4096);
            var current=CharacterEdits.fields(workspace.draft()).get(key);
            value.setText(newFields?(current==null?"":current.isJsonPrimitive()?current.getAsString():current.toString()):val);
            operations.add(addDrawableChild(ButtonWidget.builder(Text.literal("<"),b->stepField(-1)).dimensions(left()+panelWidth()/2-44,height-126,20,20).build()));
            operations.add(addDrawableChild(ButtonWidget.builder(Text.literal(">"),b->stepField(1)).dimensions(left()+panelWidth()/2-22,height-126,20,20).build()));
            value.setChangedListener(text->fieldDirty=true);
            field.setChangedListener(path->{
                var entry=CharacterEdits.fields(workspace.draft()).get(path);
                value.setText(entry==null?"":entry.isJsonPrimitive()?entry.getAsString():entry.toString());
                fieldDirty=false;
            });
            button("Stage field",0,3,height-102,this::stage);
            button("Refresh",1,3,height-102,this::refresh);
            button("Use latest",2,3,height-102,()->confirm("Discard staged changes and use the latest server snapshot?",()->{workspace.useLatest(); fieldDirty=false; field=null; value=null; rebuild();}));
            button("Reconcile",0,3,height-78,()->attempt(()->workspace.rebase(false)));
            button("Keep my edits",1,3,height-78,()->confirm("Apply your changed fields over the latest snapshot, including conflicts "+workspace.conflicts()+"? Saving still requires server permission.",()->attempt(()->workspace.rebase(true))));
            if(workspace.latest().has("srd")) button("SRD fields",2,3,height-78,()->{
                if(fieldDirty) { message="Stage the typed field before opening the SRD form."; return; }
                client.setScreen(new SrdCharacterFormScreen(this,workspace.draft(),edited->{
                    var diff=CharacterEdits.diff(workspace.draft(),edited);
                    diff.entrySet().forEach(e->workspace.stage(e.getKey(),e.getValue()));
                    message="SRD fields staged locally. Save changes submits them for approval.";
                    field=null; value=null; rebuild();
                }));
            });
            else button("Import local",2,3,height-78,()->confirm("Stage fields from the local sheet? Server-only fields are retained. The local file stays unchanged.",()->attempt(()->{if(local==null) throw new IllegalArgumentException("No local sheet selected"); workspace.importLocal(local);})));
            button("Save changes",0,3,height-54,()->{if(fieldDirty) message="Stage the typed field before saving."; else if(!workspace.dirty()) message="No changes staged. Use latest to update the working draft."; else request("edit",workspace.request(),true);});
            button("Approve",1,3,height-54,()->{
                if(workspace.dirty() || fieldDirty) { message="Save or discard the staged changes before approving."; return; }
                var args=new JsonObject(); args.addProperty("character",selectedCharacter.toString()); args.add("revision",workspace.latest().get("revision")); request("approve",args,true);
            });
            operations.get(operations.size()-1).active=workspace.latest().get("manager").getAsBoolean();
            button("Characters",2,3,height-54,()->leaveDraft(()->characters(0)));
        } else {
            int capacity=Math.max(1,Math.min(8,(height-144)/22));
            for(int i=offset;i<Math.min(rows.size(),offset+capacity);i++) {
                var row=rows.get(i).getAsJsonObject(); int y=42+(i-offset)*22;
                String label=row.get("name").getAsString();
                if(view.equals("characters")) label+=" / "+(row.get("approved").getAsBoolean()?"approved":"draft")+(row.has("bound")?" / bound":"");
                String shown=textRenderer.trimToWidth(label,panelWidth()-12);
                button(shown,0,1,y,()->{
                    if(view.equals("campaigns")) {
                        pendingCampaign=row.deepCopy(); var args=new JsonObject(); args.add("campaign",row.get("id"));
                        request("select",args,false);
                    } else { selectedCharacter=UUID.fromString(row.get("id").getAsString()); refresh(); }
                });
            }
            button("Previous",0,3,height-76,()->{
                if(offset>0) { offset=Math.max(0,offset-capacity); rebuild(); }
                else if(page>0) { if(view.equals("campaigns")) campaigns(page-1); else characters(page-1); }
            });
            button("Next",1,3,height-76,()->{
                if(offset+capacity<rows.size()) { offset+=capacity; rebuild(); }
                else if((page+1)*8<total) { if(view.equals("campaigns")) campaigns(page+1); else characters(page+1); }
            });
            button("Refresh",2,3,height-76,()->{if(view.equals("campaigns")) campaigns(page); else characters(page);});
            boolean canCreateSrd=srdAuthoring && campaign!=null && campaign.get("profile").getAsString().equals("srd_5_1");
            int columns=canCreateSrd?3:2;
            button("Campaigns",0,columns,height-52,()->campaigns(0));
            button("New from local",1,columns,height-52,()->{
                if(campaign==null || local==null) { message="Select a campaign and open this screen from a local character sheet."; return; }
                confirm("Create a NEW server character from this local sheet? To revise an existing record, select it and use Import local instead.",()->{
                    var args=new JsonObject(); args.addProperty("sheet",local.toString()); request("create",args,true);
                });
            });
            if(canCreateSrd) button("New SRD",2,3,height-52,()->confirm("Create a new SRD server draft? It starts unarmored with average abilities and no class assumptions. Edit SRD fields, save and request approval.",()->request("srd_create",new JsonObject(),true)));
        }
        button("Back",0,1,height-28,this::close);
    }
    private void stepField(int direction) {
        Runnable change=()->{
            var keys=new ArrayList<>(CharacterEdits.fields(workspace.draft()).keySet()); keys.remove("format");
            if(keys.isEmpty()) return;
            int index=keys.indexOf(field.getText()); field.setText(keys.get(Math.floorMod(index+direction,keys.size())));
        };
        if(fieldDirty) confirm("Discard the typed, unstaged value before browsing another field?",change); else change.run();
    }
    private void stage() {
        attempt(()->{
            String key=field.getText(), raw=value.getText(); JsonElement v;
            if(key.startsWith("values.")) v=new JsonPrimitive(Double.parseDouble(raw));
            else if(key.equals("ui") || key.startsWith("collections.")) { CharacterRepository.checkDepth(raw,20); v=JsonParser.parseString(raw); }
            else v=new JsonPrimitive(raw);
            workspace.stage(key,v); fieldDirty=false;
        });
    }
    private void attempt(Runnable action) {
        try { action.run(); message="Working draft updated locally. Save changes submits it for server validation."; }
        catch(RuntimeException e) { message=e.getMessage()==null?"Invalid field value":e.getMessage(); }
    }
    private void campaigns(int page) {
        this.page=page; var args=new JsonObject(); args.addProperty("page",page); request("campaigns",args,false);
    }
    private void characters(int page) {
        if(campaign==null) { campaigns(0); return; }
        this.page=page; var args=new JsonObject(); args.add("campaign",campaign.get("id")); args.addProperty("page",page); request("list",args,false);
    }
    private void refresh() {
        var args=new JsonObject(); args.addProperty("character",selectedCharacter.toString()); request("get",args,false);
    }
    private void request(String operation,JsonObject args,boolean acceptedDraft) {
        message="Waiting for server: "+operation;
        if(!ClientCharacters.send(operation,args,reply->receive(operation,reply,acceptedDraft))) message="Wait for the pending request, or reconnect to a supported server.";
    }
    private void receive(String operation,CharacterReplyPayload reply,boolean acceptedDraft) {
        message=reply.code()+": "+reply.message();
        if(!reply.code().equals("OK")) return;
        try {
            CharacterRepository.checkDepth(reply.json(),24);
            JsonObject data=JsonParser.parseString(reply.json()).getAsJsonObject();
            switch(operation) {
                case "campaigns" -> { srdAuthoring=data.has("srdAuthoringVersion") && data.get("srdAuthoringVersion").getAsInt()==1; rows=data.getAsJsonArray("campaigns"); view="campaigns"; total=data.get("total").getAsInt(); offset=0; }
                case "select" -> { campaign=pendingCampaign; pendingCampaign=null; characters(0); return; }
                case "list" -> { rows=data.getAsJsonArray("characters"); view="characters"; total=data.get("total").getAsInt(); offset=0; }
                default -> {
                    selectedCharacter=UUID.fromString(data.get("id").getAsString());
                    boolean different=!workspace.loaded() || !workspace.latest().get("id").equals(data.get("id"));
                    workspace.receive(data);
                    if(different) { fieldDirty=false; field=null; value=null; }
                    if(acceptedDraft) { workspace.useLatest(); fieldDirty=false; field=null; value=null; } view="detail";
                }
            }
            rebuild();
        } catch(RuntimeException e) { message="Invalid server response; local draft retained."; }
    }
    private void leaveDraft(Runnable next) { if(workspace.dirty() || fieldDirty) confirm("Leave this working draft? Unsaved staged edits will be discarded when another character is selected.",next); else next.run(); }
    private void confirm(String question,Runnable action) {
        if(ClientCharacters.busy()) { message="Wait for the server response."; return; }
        client.setScreen(new ConfirmScreen(yes->{client.setScreen(this); if(yes) action.run();},Text.literal("Confirm character operation"),Text.literal(question)));
    }
    @Override public void close() {
        if(ClientCharacters.busy()) { message="Wait for the server response before closing."; return; }
        if(workspace.dirty() || fieldDirty) confirm("Close and discard staged server edits? Your original local sheet is unchanged.",()->client.setScreen(parent));
        else client.setScreen(parent);
    }
    @Override public void render(DrawContext g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xED181B20);
        line(g,"Server characters — "+view,8,0xFFE0B84C);
        if(view.equals("detail") && workspace.loaded()) {
            var state=workspace.latest();
            line(g,state.get("name").getAsString()+" / "+(state.get("approved").getAsBoolean()?"APPROVED":"DRAFT")+" / "+(state.has("bound")?"BOUND":"unbound"),25,0xFFFFFFFF);
            line(g,"Selected: "+selectedCharacter,37,0xFFCCCCCC);
            line(g,"Server edit rev "+state.get("revision")+" / HP rev "+state.get("runtimeRevision")+" / staged fields "+workspace.changes().size(),49,0xFFCCCCCC);
            if(height>235) {
                if(state.has("srd")) {
                    var srd=state.getAsJsonObject("srd");
                    line(g,srd.get("valid").getAsBoolean()?"D&D HP "+srd.get("hp")+"/"+srd.get("maximumHp")+" + "+srd.get("temporaryHp")+" temp / AC "+srd.get("ac")+" / Move "+srd.get("speedFeet")+" ft":"SRD schema invalid",61,0xFFCCCCCC);
                } else {
                    var stats=state.getAsJsonObject("stats");
                    line(g,"Live HP "+stats.get("hp")+"/"+stats.get("hp_max")+"  Move "+stats.get("movement_blocks")+"  Initiative "+stats.get("initiative_bonus"),61,0xFFCCCCCC);
                }
            }
            if(height>260) line(g,state.has("srd")?"SRD: "+state.getAsJsonObject("srd").get("reason").getAsString():"Profile: "+state.get("profile").getAsString()+" / owner lock: "+state.getAsJsonObject("policy").get("ownerLocked"),73,0xFFCCCCCC);
            if(height>280 && state.has("srd")) line(g,state.getAsJsonObject("srd").get("worldCombatEnabled").getAsBoolean()?"SRD world combat enabled":"SRD automatic world combat disabled; controlled tests require DM admission",85,0xFFFFCC66);
            line(g,"Field (< / > to browse) / working draft value",height-139,0xFFBBBBBB);
        } else line(g,(campaign==null?"No campaign selected": "Selected campaign: "+campaign.get("name").getAsString())+" / page "+(page+1),24,0xFFCCCCCC);
        int messageY=view.equals("detail")?height-164:height-98;
        int count=0;
        for(var line:textRenderer.wrapLines(Text.literal(message),panelWidth())) {
            if(count++==2) break;
            g.drawText(textRenderer,line,left(),messageY,0xFFFFFF88,false); messageY+=10;
        }
        boolean busy=ClientCharacters.busy();
        for(var widget:operations) {
            boolean approve=widget.getMessage().getString().equals("Approve");
            widget.active=!busy && (!approve || workspace.loaded() && workspace.latest().get("manager").getAsBoolean());
        }
        if(field!=null) { field.setEditable(!busy); value.setEditable(!busy); }
        super.render(g,mx,my,delta);
    }
    private void line(DrawContext g,String text,int y,int color) {
        if(y>=0) g.drawText(textRenderer,textRenderer.trimToWidth(text,panelWidth()),left(),y,color,false);
    }
}
