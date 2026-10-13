package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Read-only flowing sheet panels. Every wrapped line remains reachable by scrolling. */
public final class CreatorSheetView extends ClickableWidget {
    private record Block(String title,List<OrderedText> lines,int y,int height) {}
    private final List<Block> blocks=new ArrayList<>();
    private int offset,total;
    public CreatorSheetView(UiLayout.Rect rect,List<CreatorSheet.Section> sections){super(rect.x(),rect.y(),rect.width(),rect.height(),Text.literal("Character sheet review"));int y=0;var font=MinecraftClient.getInstance().textRenderer;
        for(var s:sections){var lines=font.wrapLines(Text.literal(s.text()),width-20);int h=Math.max(36,25+lines.size()*11);blocks.add(new Block(s.title(),lines,y,h));y+=h+6;}total=y;
    }
    public int position(){return offset;}
    public void position(int value){offset=Math.clamp(value,0,Math.max(0,total-height));}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;
        try(var clip=ClipScope.open(()->g.enableScissor(getX(),getY(),getRight(),getBottom()),g::disableScissor)){
            for(var b:blocks){int y=getY()+b.y()-offset;if(y+b.height()<getY()||y>getBottom())continue;RpgTheme.panel(g,getX(),y,width-4,b.height(),RpgTheme.PANEL,false);RpgTheme.text(g,font,b.title(),getX()+8,y+6,width-20,RpgTheme.GOLD);g.fill(getX()+8,y+18,getRight()-12,y+19,0xFF66563D);for(int i=0;i<b.lines().size();i++)g.drawText(font,b.lines().get(i),getX()+8,y+23+i*11,RpgTheme.TEXT,false);}
        }
        if(isMouseOver(mx,my))for(var b:blocks){int y=getY()+b.y()-offset;if(my>=y&&my<y+20)MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(b.title()));}
        if(total>height){int h=Math.max(8,height*height/total);int y=getY()+(height-h)*offset/Math.max(1,total-height);g.fill(getRight()-3,y,getRight(),y+h,RpgTheme.GOLD);}
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!isMouseOver(x,y))return false;position(offset+(vertical>0?-22:22));return true;}
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0||!isMouseOver(x,y))return false;setFocused(true);return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!isFocused())return false;int d=switch(key){case GLFW.GLFW_KEY_UP->-11;case GLFW.GLFW_KEY_DOWN->11;case GLFW.GLFW_KEY_PAGE_UP->-height;case GLFW.GLFW_KEY_PAGE_DOWN->height;default->0;};if(d==0)return false;position(offset+d);return true;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal("Character sheet review"));b.put(NarrationPart.USAGE,Text.literal("Scroll or use Page Up and Page Down to read every section."));}
}
