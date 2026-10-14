package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;

/** Inline money buffer. Item mutations and failed/reordered money saves cannot erase newer input. */
public final class CurrencyDraft {
    public static final List<String> KEYS=List.of("copper","silver","electrum","gold","platinum");
    public static final List<String> LABELS=List.of("CP","SP","EP","GP","PP");
    public final Map<String,String> values=new LinkedHashMap<>();
    private final Map<String,String> saved=new LinkedHashMap<>(),baseline=new LinkedHashMap<>();
    public CurrencyDraft(JsonObject inventory){for(String key:KEYS){String value=inventory.get(key).getAsString();values.put(key,value);saved.put(key,value);baseline.put(key,value);}}
    public boolean dirty(){return !values.equals(saved);}
    public boolean conflict(){return dirty()&&!saved.equals(baseline);}
    public void reviewed(){baseline.clear();baseline.putAll(saved);}
    public void accept(JsonObject inventory,Map<String,String> sent){boolean wasDirty=dirty();for(String key:KEYS){String current=inventory.get(key).getAsString();if(values.get(key).equals(saved.get(key))||sent!=null&&values.get(key).equals(sent.get(key)))values.put(key,current);saved.put(key,current);}if(sent!=null||!wasDirty){baseline.clear();baseline.putAll(saved);}}
    public JsonObject data(JsonObject latest){var out=latest.deepCopy();for(String key:KEYS){String value=values.get(key);if(!value.matches("[0-9]{1,10}"))throw new IllegalArgumentException("Enter nonnegative whole currency values");long n=Long.parseLong(value);if(n>1_000_000_000L)throw new IllegalArgumentException("Currency exceeds the stored integer bound");out.addProperty(key,n);}return out;}
    public void controls(CharacterCanvas pane,net.minecraft.client.font.TextRenderer font,int y,boolean editable,Runnable save){int x=7,w=pane.getWidth()-14,cw=(w-8)/5;pane.label("Currency",x,y,w,RpgTheme.GOLD);for(int i=0;i<5;i++){String key=KEYS.get(i);int fx=x+i*(cw+2);pane.label(LABELS.get(i),fx,y+14,cw,RpgTheme.MUTED);var f=new net.minecraft.client.gui.widget.TextFieldWidget(font,fx,y+26,cw,18,net.minecraft.text.Text.literal(key));f.setMaxLength(10);f.setTextPredicate(s->s.matches("[0-9]{0,10}"));f.setText(values.get(key));f.setEditable(editable);f.setChangedListener(v->values.put(key,v));pane.control("currency:"+key,f);}var b=pane.control("currency:save",new RpgButton(x,y+49,w,18,"Save Currency","",save).hint("Server-confirmed sheet money; no Minecraft items are created"));b.active=editable;}
}
