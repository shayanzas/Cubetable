package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import java.util.*;

/** Sprite regions, not reference sheets. Race artwork is deliberately excluded. */
public final class CreatorIcons {
    public record Sprite(int x,int y,int width,int height) {}
    public static final Map<String,Sprite> REGIONS;
    private static final Identifier TEXTURE=Identifier.of("tacticalcombat","textures/gui/creator/icons.png");
    static {
        var map=new LinkedHashMap<String,Sprite>();
        String[] classes={"barbarian","bard","cleric","druid","fighter","monk","ranger","rogue","warlock","wizard"};
        int[] starts={0,147,280,425,566,705,849,999,1143,1293,1448};
        for(int i=0;i<classes.length;i++)map.put(classes[i],new Sprite(starts[i],35,starts[i+1]-starts[i],192));
        String[] systems={"str","dex","con","int","wis","cha","skills","saves","expertise","weapons","armor","tools"};
        for(int i=0;i<systems.length;i++)map.put(systems[i],new Sprite(i*120,247,120,172));
        String[] rest={"languages","equipment","features","feats","spells","cantrips","action","bonus","reaction","hp","proficiency","inspect"};
        for(int i=0;i<rest.length;i++)map.put(rest[i],new Sprite(i*120,447,120,170));
        String[] backgrounds={"custom","acolyte","charlatan","criminal","entertainer","folk_hero","guild_artisan","noble"};
        for(int i=0;i<backgrounds.length;i++)map.put(backgrounds[i],new Sprite(i*181,630,181,214));
        map.put("outlander",new Sprite(0,844,196,242));map.put("sage",new Sprite(200,844,200,242));
        map.put("soldier",new Sprite(485,835,224,251));map.put("urchin",new Sprite(773,844,210,242));
        map.put("paladin",new Sprite(0,0,887,887));map.put("sorcerer",new Sprite(887,0,887,887));
        REGIONS=Map.copyOf(map);
    }
    private CreatorIcons() {}
    public static void draw(DrawContext g,String key,int x,int y,int size) {
        var r=REGIONS.get(key);if(r==null)return;
        boolean extra=key.equals("paladin")||key.equals("sorcerer");
        var texture=extra?Identifier.of("tacticalcombat","textures/gui/creator/class-extra.png"):TEXTURE;
        int longest=Math.max(r.width(),r.height()),w=Math.max(1,size*r.width()/longest),h=Math.max(1,size*r.height()/longest);
        g.drawTexture(texture,x+(size-w)/2,y+(size-h)/2,w,h,(float)r.x(),(float)r.y(),r.width(),r.height(),extra?1774:1448,extra?887:1086);
    }
}
