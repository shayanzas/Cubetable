package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Individually isolated sprites with explicit source provenance, uniform slots and nearest-neighbor texture sampling. */
public final class CreatorIcons {
    public record Sprite(int x,int y,int width,int height) {}
    public static final Map<String,Sprite> REGIONS;
    static {
        try(var stream=CreatorIcons.class.getResourceAsStream("/assets/tacticalcombat/textures/gui/creator/sprites.json")) {
            if(stream==null)throw new IllegalStateException("Missing UI sprite manifest");
            var root=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schema").getAsInt()!=1)throw new IllegalStateException("Unsupported sprite manifest");
            var map=new LinkedHashMap<String,Sprite>();
            root.getAsJsonObject("sprites").entrySet().forEach(e->{var s=e.getValue().getAsJsonObject();map.put(e.getKey(),new Sprite(0,0,s.get("width").getAsInt(),s.get("height").getAsInt()));});
            REGIONS=Map.copyOf(map);
        }catch(IOException error){throw new ExceptionInInitializerError(error);}
    }
    private CreatorIcons() {}
    public static UiLayout.Rect fitted(Sprite sprite,UiLayout.Rect slot,int padding) {
        int available=Math.max(1,Math.min(slot.width(),slot.height())-2*padding);
        int longest=Math.max(sprite.width()-4,sprite.height()-4);
        int width=Math.max(1,available*(sprite.width()-4)/longest),height=Math.max(1,available*(sprite.height()-4)/longest);
        return new UiLayout.Rect(slot.x()+(slot.width()-width)/2,slot.y()+(slot.height()-height)/2,width,height);
    }
    public static void draw(DrawContext g,String key,int x,int y,int size) {
        var sprite=REGIONS.get(key);if(sprite==null)return;
        var rect=fitted(sprite,new UiLayout.Rect(x,y,size,size),1);
        g.drawTexture(Identifier.of("tacticalcombat","textures/gui/creator/sprites/"+key+".png"),rect.x(),rect.y(),rect.width(),rect.height(),2f,2f,sprite.width()-4,sprite.height()-4,sprite.width(),sprite.height());
    }
}
