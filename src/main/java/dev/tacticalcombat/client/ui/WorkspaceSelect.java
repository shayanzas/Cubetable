package dev.tacticalcombat.client.ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.*;

/** Ordinary scrollable dropdown. Values are labels/identities, never authoritative rules. */
public final class WorkspaceSelect extends ClickableWidget {
    public record Option(String id,String label) {}
    private final String label;private final List<Option> options;private final Supplier<String> value;private final Consumer<String> changed;private final UiLayout.Rect bounds;
    private boolean open;private int first,selected;
    public WorkspaceSelect(int x,int y,int w,String label,List<Option> options,Supplier<String> value,Consumer<String> changed,UiLayout.Rect bounds){super(x,y,w,18,Text.literal(label));this.label=label;this.options=List.copyOf(options);this.value=value;this.changed=changed;this.bounds=bounds;for(int i=0;i<options.size();i++)if(options.get(i).id().equals(value.get()))selected=i;}
    public boolean isOpen(){return open;}public void dismiss(){open=false;}
    public UiLayout.Rect popup(){return AlignmentDropdown.popup(new UiLayout.Rect(getX(),getY(),width,height),bounds);}
    private int capacity(){return Math.max(1,popup().height()/14);}
    private void ensure(){first=Math.clamp(first,0,Math.max(0,options.size()-capacity()));if(selected<first)first=selected;if(selected>=first+capacity())first=selected-capacity()+1;}
    @Override public void setFocused(boolean focused){super.setFocused(focused);if(!focused)open=false;}
    @Override public boolean isMouseOver(double x,double y){return super.isMouseOver(x,y)||open&&popup().contains(x,y);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;String current=options.stream().filter(o->o.id().equals(value.get())).map(Option::label).findFirst().orElse("Choose");RpgTheme.panel(g,getX(),getY(),width,height,RpgTheme.PANEL,isFocused());RpgTheme.text(g,font,label+": "+current,getX()+4,getY()+5,width-16,RpgTheme.TEXT);g.drawText(font,open?"−":"+",getRight()-10,getY()+4,RpgTheme.GOLD,false);if(isMouseOver(mx,my))MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(label+": "+current));if(!open)return;ensure();var r=popup();g.getMatrices().push();g.getMatrices().translate(0,0,400);RpgTheme.panel(g,r.x(),r.y(),r.width(),r.height(),RpgTheme.PANEL,true);for(int i=first;i<Math.min(options.size(),first+capacity());i++){int y=r.y()+(i-first)*14;if(i==selected||new UiLayout.Rect(r.x(),y,r.width(),14).contains(mx,my))g.fill(r.x()+1,y,r.x()+r.width()-1,y+14,RpgTheme.HOVER);RpgTheme.text(g,font,options.get(i).label(),r.x()+4,y+3,r.width()-8,options.get(i).id().equals(value.get())?RpgTheme.GOLD:RpgTheme.TEXT);}g.getMatrices().pop();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0||!active)return false;if(super.isMouseOver(x,y)){open=!open;ensure();return true;}if(open&&popup().contains(x,y)){int i=first+(int)(y-popup().y())/14;if(i<options.size()){open=false;selected=i;changed.accept(options.get(i).id());}return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!open)return false;first=Math.clamp(first+(vertical>0?-1:1),0,Math.max(0,options.size()-capacity()));selected=Math.clamp(selected,first,Math.min(options.size()-1,first+capacity()-1));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!isFocused()||!active||options.isEmpty())return false;if(key==GLFW.GLFW_KEY_ESCAPE&&open){open=false;return true;}if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){open=true;selected=Math.clamp(selected+(key==GLFW.GLFW_KEY_UP?-1:1),0,options.size()-1);ensure();return true;}if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){if(open){open=false;changed.accept(options.get(selected).id());}else open=true;return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal(label+": "+value.get()));b.put(NarrationPart.USAGE,Text.literal("Enter opens, arrows select, Enter confirms, Escape closes."));}
}
