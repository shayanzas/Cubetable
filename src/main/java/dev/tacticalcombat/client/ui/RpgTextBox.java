package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.*;
import java.util.*;
import org.lwjgl.glfw.GLFW;

/** Wrapped, clipped readable help/empty-state text; never scales text to fit. */
public final class RpgTextBox extends ClickableWidget {
    private final String text;
    private final List<OrderedText> lines;
    private int first;
    public RpgTextBox(int x,int y,int w,int h,String text) {super(x,y,w,h,Text.literal("Information"));this.text=text;lines=MinecraftClient.getInstance().textRenderer.wrapLines(Text.literal(text),w-12);}
    public int position(){return first;}
    public void position(int value){first=Math.clamp(value,0,Math.max(0,lines.size()-capacity()));}
    private int capacity() {return Math.max(1,height/12);}
    private void scroll(int amount) {first=Math.clamp(first+amount,0,Math.max(0,lines.size()-capacity()));}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta) {
        try(var clip=ClipScope.open(()->g.enableScissor(getX(),getY(),getRight(),getBottom()),g::disableScissor)){
        for(int i=first;i<Math.min(lines.size(),first+capacity());i++) g.drawText(MinecraftClient.getInstance().textRenderer,lines.get(i),getX()+4,getY()+(i-first)*12,RpgTheme.MUTED,false);
        }if(lines.size()>capacity()) {int h=Math.max(8,height*capacity()/lines.size()),y=getY()+(height-h)*first/Math.max(1,lines.size()-capacity());g.fill(getRight()-3,y,getRight(),y+h,RpgTheme.GOLD);}
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {if(!isMouseOver(x,y)) return false;scroll(vertical>0?-2:2);return true;}
    @Override public boolean keyPressed(int key,int scan,int mods) {if(!isFocused()) return false;if(key==GLFW.GLFW_KEY_UP || key==GLFW.GLFW_KEY_DOWN) {scroll(key==GLFW.GLFW_KEY_UP?-1:1);return true;}if(key==GLFW.GLFW_KEY_PAGE_UP || key==GLFW.GLFW_KEY_PAGE_DOWN) {scroll(key==GLFW.GLFW_KEY_PAGE_UP?-capacity():capacity());return true;}return false;}
    @Override public boolean mouseClicked(double x,double y,int button) {if(button!=0 || !isMouseOver(x,y)) return false;setFocused(true);return true;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b) {b.put(NarrationPart.TITLE,Text.literal(text));}
}
