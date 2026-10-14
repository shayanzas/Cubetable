package dev.tacticalcombat.client.ui;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Always vertical. Smaller windows scroll the rail instead of replacing it with a button grid. */
public final class SectionRail extends ClickableWidget {
    public record Item(String id,String label) {}
    private final List<Item> items;
    private final Consumer<String> choose;
    private int first,selected;
    private final int ROW;
    public SectionRail(UiLayout.Rect r,List<Item> items,Consumer<String> choose){this(r,items,choose,16);}
    public SectionRail(UiLayout.Rect r,List<Item> items,Consumer<String> choose,int rowHeight){super(r.x(),r.y(),r.width(),r.height(),Text.literal("Character sections"));this.items=List.copyOf(items);this.choose=choose;this.ROW=rowHeight;}
    public void selected(String id){for(int i=0;i<items.size();i++)if(items.get(i).id().equals(id)){selected=i;if(i<first)first=i;if(i>=first+capacity())first=i-capacity()+1;}}
    private int capacity(){return Math.max(1,height/ROW);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;g.fill(getX(),getY(),getRight(),getBottom(),0xFF15191E);
        try(var clip=ClipScope.open(()->g.enableScissor(getX(),getY(),getRight(),getBottom()),g::disableScissor)){
            for(int i=first;i<Math.min(items.size(),first+capacity());i++){int y=getY()+(i-first)*ROW;boolean active=i==selected;if(active){g.fill(getX(),y,getRight(),y+ROW-1,0xFF32302A);g.fill(getX(),y,getX()+2,y+ROW-1,RpgTheme.GOLD);}if(ROW<24)RpgTheme.text(g,font,items.get(i).label(),getX()+6,y+4,width-10,active?RpgTheme.GOLD:RpgTheme.MUTED);else{var lines=font.wrapLines(Text.literal(items.get(i).label()),width-10);for(int line=0;line<Math.min(2,lines.size());line++)g.drawText(font,lines.get(line),getX()+6,y+4+line*10,active?RpgTheme.GOLD:RpgTheme.MUTED,false);}}
        }
        if(isMouseOver(mx,my)){int i=first+(my-getY())/ROW;if(i>=0&&i<items.size())MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(items.get(i).label()));}
    }
    @Override public boolean mouseClicked(double x,double y,int button){int i=first+(int)((y-getY())/ROW);if(button!=0||!isMouseOver(x,y)||i>=items.size())return false;setFocused(true);choose.accept(items.get(i).id());return true;}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(!isMouseOver(x,y))return false;first=Math.clamp(first+(v>0?-1:1),0,Math.max(0,items.size()-capacity()));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!isFocused())return false;if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){selected=Math.clamp(selected+(key==GLFW.GLFW_KEY_UP?-1:1),0,items.size()-1);selected(items.get(selected).id());return true;}if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){choose.accept(items.get(selected).id());return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal(items.get(selected).label()));b.put(NarrationPart.USAGE,Text.literal("Arrow keys choose a section, Enter opens."));}
}
