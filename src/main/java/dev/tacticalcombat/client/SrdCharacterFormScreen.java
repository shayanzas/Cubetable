package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.widget.*;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.Consumer;

/** Local staged editing only. Parent workspace owns revision/reconciliation and sends the save. */
public final class SrdCharacterFormScreen extends Screen {
    private final Screen parent;
    private final Consumer<JsonObject> stage;
    private JsonObject sheet;
    private final JsonObject original;
    private final List<SrdAuthoring.Field> fields=SrdAuthoring.fields();
    private int index,optionPage;
    private TextFieldWidget input;
    private String inputKey;
    private boolean initialized;
    private String message="Edit fields, then stage into the Server workspace. Nothing is saved automatically.";
    public SrdCharacterFormScreen(Screen parent,JsonObject draft,Consumer<JsonObject> stage) {
        super(Text.literal("SRD character form")); this.parent=parent; sheet=draft.deepCopy(); original=draft.deepCopy(); this.stage=stage;
        try {
            var data=SrdAuthoring.data(sheet);
            initialized=data.has("version") && data.get("version").getAsInt()==1 && data.has("abilities") && data.get("abilities").isJsonObject();
        } catch(RuntimeException ignored) { }
    }
    private int left() { return (width-panelWidth())/2; }
    private int panelWidth() { return Math.min(420,width-16); }
    private void button(String title,int x,int y,int w,Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(title),b->action.run()).dimensions(x,y,w,20).build());
    }
    /** Foreground is drawn before super.render; skip its late default background blur. */
    @Override public void renderBackground(DrawContext g,int mx,int my,float delta) { }
    @Override public boolean shouldPause() { return false; }
    @Override protected void init() {
        String pending=input==null?null:input.getText(), previousKey=inputKey;
        clearChildren(); input=null;
        if(!initialized) {
            button("Initialize SRD fields explicitly",left(),64,panelWidth(),()->client.setScreen(new ConfirmScreen(yes->{
                if(yes) {
                    if(!sheet.has("collections")) sheet.add("collections",new JsonObject());
                    sheet.getAsJsonObject("collections").add("srd_combat",SrdAuthoring.template().getAsJsonObject("collections").get("srd_combat"));
                    initialized=true;
                }
                client.setScreen(this);
            },Text.literal("Initialize SRD data?"),Text.literal("Existing SRD data, if malformed, will be replaced in this local draft only. Other sheet fields remain. Approval is still required."))));
        } else {
            var field=fields.get(index);
            if(field.choices().isEmpty()) {
                input=addDrawableChild(new TextFieldWidget(textRenderer,left(),66,panelWidth(),20,Text.literal(field.label())));
                input.setMaxLength(field.key().equals("name")?80:12);
                inputKey=field.key();
                try { input.setText(pending!=null && inputKey.equals(previousKey)?pending:SrdAuthoring.value(sheet,field)); } catch(RuntimeException e) { input.setText(""); }
            } else {
                int capacity=Math.max(1,Math.min(4,(height-174)/22));
                for(int i=optionPage*capacity;i<Math.min(field.choices().size(),(optionPage+1)*capacity);i++) {
                    String choice=field.choices().get(i); String current;
                    try { current=SrdAuthoring.value(sheet,field); } catch(RuntimeException ignored) { current=""; }
                    boolean selected=Arrays.asList(current.split(",")).contains(choice);
                    button((selected?"[x] ":"[ ] ")+choice,left(),66+(i%capacity)*22,panelWidth(),()->{
                        try {
                            String next=choice;
                            if(field.multiple()) {
                                var set=new LinkedHashSet<String>(); String value=SrdAuthoring.value(sheet,field);
                                if(!value.isBlank()) set.addAll(Arrays.asList(value.split(",")));
                                if(!set.remove(choice)) set.add(choice); next=String.join(",",set);
                            }
                            sheet=SrdAuthoring.change(sheet,field,next); init();
                        } catch(RuntimeException e) { message=e.getMessage(); }
                    });
                }
                if(field.choices().size()>capacity) button("More choices",left(),height-98,panelWidth(),()->{optionPage=(optionPage+1)%((field.choices().size()+capacity-1)/capacity); init();});
            }
            int half=(panelWidth()-4)/2;
            button("Previous field",left(),height-74,half,()->browse(-1));
            button("Next field",left()+half+4,height-74,half,()->browse(1));
            button("Validate and stage",left(),height-50,panelWidth(),()->{
                if(!storeInput()) return;
                try { SrdCharacterCodec.parseSheet(sheet.toString()); stage.accept(sheet.deepCopy()); client.setScreen(parent); }
                catch(RuntimeException e) { message=e.getMessage(); }
            });
        }
        button("Cancel form edits",left(),height-26,panelWidth(),this::close);
    }
    private boolean storeInput() {
        if(input==null) return true;
        try { sheet=SrdAuthoring.change(sheet,fields.get(index),input.getText()); return true; }
        catch(RuntimeException e) { message=e.getMessage(); return false; }
    }
    private void browse(int direction) { if(storeInput()) { index=Math.floorMod(index+direction,fields.size()); optionPage=0; init(); } }
    @Override public void close() {
        if(input!=null || !sheet.equals(original)) client.setScreen(new ConfirmScreen(yes->client.setScreen(yes?parent:this),Text.literal("Discard form edits?"),Text.literal("The Server workspace and original local sheet will not be changed.")));
        else client.setScreen(parent);
    }
    @Override public void render(DrawContext g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xEE181B20);
        g.drawText(textRenderer,"SRD 5.1 — staged character inputs",left(),10,0xFFE0B84C,false);
        g.drawText(textRenderer,initialized?(index+1)+" / "+fields.size()+": "+fields.get(index).label():"SRD data is missing or malformed",left(),28,0xFFFFFFFF,false);
        g.drawText(textRenderer,textRenderer.trimToWidth(message,panelWidth()),left(),44,0xFFFFFF88,false);
        super.render(g,mx,my,delta);
    }
}
