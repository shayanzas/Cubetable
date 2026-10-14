package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;

/** Compact icon choices with an independent inspect panel; no prompt-shaped buttons. */
public final class CreatorChoiceGrid extends ClickableWidget {
    public record Entry(String id,String name,String icon) {}
    private List<Entry> entries=List.of();
    private String selected="";
    private int first,focus;
    private final Consumer<Entry> choose;
    private final int cellWidth,cellHeight,iconSize;
    public CreatorChoiceGrid(UiLayout.Rect r,Consumer<Entry> choose){this(r,choose,58,44,28);}
    public CreatorChoiceGrid(UiLayout.Rect r,Consumer<Entry> choose,int cellWidth,int cellHeight,int iconSize){super(r.x(),r.y(),r.width(),r.height(),Text.literal("Character choices"));this.choose=choose;this.cellWidth=cellWidth;this.cellHeight=cellHeight;this.iconSize=iconSize;}
    private int columns(){return Math.max(1,width/cellWidth);}
    private int rowHeight(){return cellHeight;}
    public int position(){return first;}
    public int focusedIndex(){return focus;}
    public void focusedIndex(int value){focus=Math.clamp(value,0,Math.max(0,entries.size()-1));}
    public void position(int first){this.first=Math.max(0,first);}
    private int visibleRows(){return Math.max(1,height/rowHeight());}
    public void entries(List<Entry> values,String selected){this.entries=List.copyOf(values);this.selected=selected;first=Math.clamp(first,0,Math.max(0,(entries.size()+columns()-1)/columns()-visibleRows()));focus=Math.clamp(focus,0,Math.max(0,entries.size()-1));for(int i=0;i<entries.size();i++)if(entries.get(i).id().equals(selected)){focus=i;break;}}
    public boolean selectedIndex(int index){return index>=0&&index<entries.size()&&entries.get(index).id().equals(selected);}
    private int index(double x,double y){return (first+(int)((y-getY())/rowHeight()))*columns()+Math.clamp((int)((x-getX())/(width/columns())),0,columns()-1);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;int columns=columns(),w=width/columns(),start=first*columns();
        try(var clip=ClipScope.open(()->g.enableScissor(getX(),getY(),getRight(),getBottom()),g::disableScissor)){
            for(int i=start;i<Math.min(entries.size(),start+columns*visibleRows());i++){var e=entries.get(i);int x=getX()+i%columns*w,y=getY()+(i-start)/columns*rowHeight();boolean highlight=selectedIndex(i),hover=isMouseOver(mx,my)&&index(mx,my)==i;
                RpgTheme.panel(g,x,y,w-3,rowHeight()-2,highlight||hover?RpgTheme.HOVER:RpgTheme.PANEL,highlight);if(isFocused()&&i==focus&&!highlight)g.drawBorder(x+2,y+2,w-7,rowHeight()-6,RpgTheme.FOCUS);
                if(!e.icon().isEmpty()&&rowHeight()>=35){int drawSize=Math.min(iconSize,Math.max(8,w-9));CreatorIcons.draw(g,e.icon(),x+(w-3-drawSize)/2,y+4+(iconSize-drawSize)/2,drawSize);}
                String title=font.trimToWidth(e.name(),w-9);g.drawText(font,title,x+(w-3-font.getWidth(title))/2,y+(e.icon().isEmpty()?(rowHeight()-9)/2:iconSize+9),highlight?RpgTheme.GOLD:RpgTheme.TEXT,false);
            }
        }
        if(isMouseOver(mx,my)){int i=index(mx,my);if(i>=0&&i<entries.size())MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(entries.get(i).name()));}
        if((entries.size()+columns-1)/columns>visibleRows())g.fill(getRight()-2,getY(),getRight(),getBottom(),RpgTheme.GOLD);
    }
    @Override public boolean mouseClicked(double x,double y,int button){int i=index(x,y);if(button!=0||!isMouseOver(x,y)||i<0||i>=entries.size())return false;focus=i;setFocused(true);choose.accept(entries.get(i));return true;}
    @Override public boolean mouseScrolled(double x,double y,double h,double v){if(!isMouseOver(x,y))return false;first=Math.clamp(first+(v>0?-1:1),0,Math.max(0,(entries.size()+columns()-1)/columns()-visibleRows()));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!isFocused()||entries.isEmpty())return false;int d=switch(key){case GLFW.GLFW_KEY_LEFT->-1;case GLFW.GLFW_KEY_RIGHT->1;case GLFW.GLFW_KEY_UP->-columns();case GLFW.GLFW_KEY_DOWN->columns();default->0;};if(d!=0){focus=Math.clamp(focus+d,0,entries.size()-1);int row=focus/columns();if(row<first)first=row;if(row>=first+visibleRows())first=row-visibleRows()+1;return true;}if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){choose.accept(entries.get(focus));return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal(entries.isEmpty()?"No matches":entries.get(focus).name()));b.put(NarrationPart.USAGE,Text.literal("Arrow keys browse, Enter previews. Save confirms your choice."));}
}
