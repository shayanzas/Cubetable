package dev.tacticalcombat.client.ui;
import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
/** Dense native-font cards, keyboard/mouse navigation, bounded clipping and full tooltips. */
public final class RpgCardGrid extends ClickableWidget {
    public record Card(String title,String value,String detail,Runnable action){}
    private List<Card> cards=List.of();private int first,selected;
    private static final int ROW=32;
    public RpgCardGrid(UiLayout.Rect r){super(r.x(),r.y(),r.width(),r.height(),Text.literal("Character overview"));}
    int scrollRow(){return first;}
    int selectedCard(){return selected;}
    void restore(int row,int card){first=Math.max(0,row);selected=Math.max(0,card);}
    private int columns(){return width>=600?4:3;}
    private int capacity(){return Math.max(1,height/ROW)*columns();}
    public void cards(List<Card> next){cards=List.copyOf(next);first=Math.clamp(first,0,Math.max(0,(cards.size()+columns()-1)/columns()-Math.max(1,height/ROW)));selected=Math.clamp(selected,0,Math.max(0,cards.size()-1));}
    private int index(double x,double y){int w=(width-6)/columns();return (first+(int)((y-getY())/ROW))*columns()+Math.clamp((int)((x-getX())/w),0,columns()-1);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var mc=MinecraftClient.getInstance();int columns=columns(),w=(width-6)/columns(),start=first*columns();try(var clip=ClipScope.open(()->g.enableScissor(getX(),getY(),getRight(),getBottom()),g::disableScissor)){for(int i=start;i<Math.min(cards.size(),start+capacity());i++){var c=cards.get(i);int x=getX()+i%columns*w,y=getY()+(i-start)/columns*ROW;RpgTheme.panel(g,x,y,w-3,ROW-3,RpgTheme.PANEL,isFocused()&&selected==i);RpgTheme.text(g,mc.textRenderer,c.title(),x+6,y+4,w-15,RpgTheme.GOLD);RpgTheme.text(g,mc.textRenderer,c.value(),x+6,y+17,w-15,RpgTheme.TEXT);}int rows=(cards.size()+columns-1)/columns();if(rows>Math.max(1,height/ROW)){int h=Math.max(8,height*Math.max(1,height/ROW)/rows),y=getY()+(height-h)*first/Math.max(1,rows-height/ROW);g.fill(getRight()-3,y,getRight(),y+h,RpgTheme.GOLD);}}if(isMouseOver(mx,my)){int i=index(mx,my);if(i<cards.size())mc.currentScreen.setTooltip(Text.literal(cards.get(i).title()+"\n"+cards.get(i).value()+"\n"+cards.get(i).detail()));}}
    @Override public boolean mouseClicked(double x,double y,int button){int i=index(x,y);if(button!=0||!isMouseOver(x,y)||i>=cards.size())return false;selected=i;setFocused(true);cards.get(i).action().run();return true;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!isMouseOver(x,y))return false;first=Math.clamp(first+(vertical>0?-1:1),0,Math.max(0,(cards.size()+columns()-1)/columns()-Math.max(1,height/ROW)));return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(!isFocused()||cards.isEmpty())return false;int delta=switch(key){case GLFW.GLFW_KEY_LEFT->-1;case GLFW.GLFW_KEY_RIGHT->1;case GLFW.GLFW_KEY_UP->-columns();case GLFW.GLFW_KEY_DOWN->columns();case GLFW.GLFW_KEY_PAGE_UP->-capacity();case GLFW.GLFW_KEY_PAGE_DOWN->capacity();default->0;};if(delta!=0){selected=Math.clamp(selected+delta,0,cards.size()-1);int row=selected/columns();if(row<first)first=row;if(row>=first+Math.max(1,height/ROW))first=row-Math.max(1,height/ROW)+1;return true;}if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){cards.get(selected).action().run();return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal(cards.isEmpty()?"No overview data":cards.get(selected).title()+" "+cards.get(selected).value()));b.put(NarrationPart.USAGE,Text.literal("Arrow keys select, Enter opens, mouse wheel scrolls."));}
}
