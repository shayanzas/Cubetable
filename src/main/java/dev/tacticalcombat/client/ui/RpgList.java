package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;

/** Clipped native-size summary cards with mouse-wheel and arrow/Enter navigation. */
public final class RpgList extends ClickableWidget {
    private List<HubState.Choice> rows=List.of();
    private final Consumer<HubState.Choice> choose;
    private final int rowHeight;
    private int first,selected;
    private String selectedId="";
    public RpgList(int x,int y,int w,int h,Consumer<HubState.Choice> choose) {this(x,y,w,h,38,choose);}
    public RpgList(int x,int y,int w,int h,int rowHeight,Consumer<HubState.Choice> choose){super(x,y,w,h,Text.literal("Available choices"));this.rowHeight=rowHeight;this.choose=choose;}
    public void rows(List<HubState.Choice> rows) {this.rows=List.copyOf(rows);for(int i=0;i<rows.size();i++)if(rows.get(i).id().equals(selectedId)){selected=i;break;}selected=Math.clamp(selected,0,Math.max(0,rows.size()-1));first=Math.clamp(first,0,Math.max(0,rows.size()-capacity()));selectedId=rows.isEmpty()?"":rows.get(selected).id();}
    int firstVisible(){return visibleFirst();}
    int selectedIndex(){return rows.isEmpty()?0:Math.clamp(selected,0,rows.size()-1);}
    void restorePosition(int offset,int selection){first=Math.max(0,offset);selected=Math.max(0,selection);if(!rows.isEmpty())selectedId=rows.get(selectedIndex()).id();}
    private int capacity() {return Math.max(1,height/rowHeight);}
    private void scroll(int delta) {first=Math.clamp(first+delta,0,Math.max(0,rows.size()-capacity()));}
    private int visibleFirst(){return Math.clamp(first,0,Math.max(0,rows.size()-capacity()));}
    private int rowAt(double y) {return visibleFirst()+(int)((y-getY())/rowHeight);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta) {
        var font=MinecraftClient.getInstance().textRenderer;
        int first=visibleFirst();
        try(var clip=ClipScope.open(()->g.enableScissor(getX(),getY(),getRight(),getBottom()),g::disableScissor)){
        for(int i=first;i<Math.min(rows.size(),first+capacity());i++) {
            var row=rows.get(i);int y=getY()+(i-first)*rowHeight;boolean hover=isMouseOver(mx,my) && rowAt(my)==i;
            if(row.enabled()||!row.reason().equals(row.detail()))RpgTheme.panel(g,getX(),y,width-5,rowHeight-4,hover?RpgTheme.HOVER:RpgTheme.PANEL,isFocused() && selected==i);
            RpgTheme.text(g,font,row.name(),getX()+8,y+6,width-22,row.enabled()?RpgTheme.TEXT:RpgTheme.MUTED);
            RpgTheme.text(g,font,row.detail(),getX()+8,y+20,width-22,RpgTheme.MUTED);
        }
        if(rows.size()>capacity()) {
            int bar=Math.max(12,height*capacity()/rows.size()),range=Math.max(1,rows.size()-capacity());
            int y=getY()+(height-bar)*first/range;g.fill(getRight()-3,getY(),getRight(),getBottom(),RpgTheme.PANEL);g.fill(getRight()-3,y,getRight(),y+bar,RpgTheme.GOLD);
        }
        }
        if(isMouseOver(mx,my) && rowAt(my)<rows.size()) {
            var row=rows.get(rowAt(my));MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(row.name()+"\n"+row.detail()+(row.reason().isEmpty()||row.reason().equals(row.detail())?"":"\n"+row.reason())));
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button) {
        if(button!=0 || !isMouseOver(x,y) || rowAt(y)>=rows.size()) return false;
        selected=rowAt(y);selectedId=rows.get(selected).id();setFocused(true);choose.accept(rows.get(Math.clamp(selected,0,rows.size()-1)));return true;
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical) {if(!isMouseOver(x,y)) return false;scroll(vertical>0?-1:1);return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(!isFocused() || rows.isEmpty()) return false;
        if(key==GLFW.GLFW_KEY_UP || key==GLFW.GLFW_KEY_DOWN || key==GLFW.GLFW_KEY_PAGE_UP || key==GLFW.GLFW_KEY_PAGE_DOWN) {
            int direction=key==GLFW.GLFW_KEY_UP || key==GLFW.GLFW_KEY_PAGE_UP?-1:1;
            selected=Math.clamp(selected+direction*(key==GLFW.GLFW_KEY_PAGE_UP || key==GLFW.GLFW_KEY_PAGE_DOWN?capacity():1),0,rows.size()-1);
            selectedId=rows.get(selected).id();if(selected<first) first=selected;else if(selected>=first+capacity()) first=selected-capacity()+1;return true;
        }
        if(key==GLFW.GLFW_KEY_ENTER || key==GLFW.GLFW_KEY_SPACE || key==GLFW.GLFW_KEY_KP_ENTER) {choose.accept(rows.get(Math.clamp(selected,0,rows.size()-1)));return true;}
        return false;
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b) {
        b.put(NarrationPart.TITLE,Text.literal(rows.isEmpty()?"No choices":rows.get(Math.clamp(selected,0,rows.size()-1)).name()+". "+rows.get(Math.clamp(selected,0,rows.size()-1)).detail()+". "+rows.get(Math.clamp(selected,0,rows.size()-1)).reason()));
        b.put(NarrationPart.USAGE,Text.literal("Arrow keys select, Enter opens. Mouse wheel scrolls."));
    }
}
