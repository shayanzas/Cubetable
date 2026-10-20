package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Read-only positions from one authorized discovery. Clicking selects, never places an actor. */
public final class WorkspaceSceneMap extends ClickableWidget {
    private final List<JsonObject> actors;private final Set<String> participants;private final String selected;private final double ox,oz;private final Consumer<String> choose;
    public WorkspaceSceneMap(int x,int y,int size,double ox,double oz,List<JsonObject> actors,Set<String> participants,String selected,Consumer<String> choose){super(x,y,size,size,Text.literal("Nearby scene, north up"));this.ox=ox;this.oz=oz;this.actors=List.copyOf(actors);this.participants=Set.copyOf(participants);this.selected=selected;this.choose=choose;}
    public static double coordinate(double value,double origin,int size){return Math.clamp(size/2d+(value-origin)*(size-32)/64d,16,size-16);}
    public static boolean mappable(JsonObject row,double ox,double oz){return row.has("x")&&row.has("z")&&Double.isFinite(row.get("x").getAsDouble())&&Double.isFinite(row.get("z").getAsDouble())&&Math.abs(row.get("x").getAsDouble()-ox)<=32&&Math.abs(row.get("z").getAsDouble()-oz)<=32;}
    private double x(JsonObject actor){return getX()+coordinate(actor.get("x").getAsDouble(),ox,width);}
    private double z(JsonObject actor){return getY()+coordinate(actor.get("z").getAsDouble(),oz,height);}
    private JsonObject hovered(double mx,double my){JsonObject best=null;double distance=81;for(var a:actors)if(mappable(a,ox,oz)){double d=Math.pow(mx-x(a),2)+Math.pow(my-z(a),2);if(d<distance){best=a;distance=d;}}return best;}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;RpgTheme.panel(g,getX(),getY(),width,height,0xCC20272B,false);RpgTheme.text(g,font,"N ↑ · 32 blocks",getX()+6,getY()+5,width-12,RpgTheme.MUTED);g.fill(getX()+width/2,getY()+20,getX()+width/2+1,getBottom()-8,0x554D5558);g.fill(getX()+8,getY()+height/2,getRight()-8,getY()+height/2+1,0x554D5558);g.drawText(font,"+",getX()+width/2-2,getY()+height/2-4,RpgTheme.MUTED,false);
        int index=0;for(var a:actors){index++;if(!mappable(a,ox,oz))continue;boolean chosen=selected.equals(text(a,"actor")),participant=participants.contains(text(a,"actor"));int x=(int)x(a),z=(int)z(a);int color=chosen||participant?RpgTheme.GOLD:flag(a,"eligible")?RpgTheme.TEXT:RpgTheme.MUTED;RpgButton.drawMarker(g,x-5,z-5,10,participant,color);g.drawText(font,""+index,Math.min(x+5,getRight()-font.getWidth(""+index)-4),z-4,color,false);if(chosen)g.drawBorder(x-7,z-7,14,14,RpgTheme.GOLD);}
        if(isMouseOver(mx,my)){var a=hovered(mx,my);if(a!=null)MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(text(a,"name")+" · "+text(a,"position")+"\n"+text(a,"reason")));}
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(!active||button!=0||!isMouseOver(x,y))return false;var actor=hovered(x,y);if(actor!=null)choose.accept(text(actor,"actor"));return true;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal("North-up discovery map. Gold markers are selected participants. Actor roster provides keyboard selection."));}
}
