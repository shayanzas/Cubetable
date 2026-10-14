package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.function.*;

/** Inline, bounded alignment popup. Legacy values remain until a conventional option is chosen. */
public final class AlignmentDropdown extends ClickableWidget {
    private final Supplier<String> value;
    private final Consumer<String> choose;
    private final Supplier<UiLayout.Rect> bounds;
    private boolean open;
    private int focus,first;
    public AlignmentDropdown(int x,int y,int width,Supplier<String> value,Consumer<String> choose,Supplier<UiLayout.Rect> bounds){super(x,y,width,17,Text.literal("Alignment"));this.value=value;this.choose=choose;this.bounds=bounds;focus=Math.max(0,IdentityModel.ALIGNMENTS.indexOf(value.get()));}
    public boolean isOpen(){return open;}
    public void dismiss(){open=false;}
    @Override public void setFocused(boolean focused){super.setFocused(focused);if(!focused)open=false;}
    public static UiLayout.Rect popup(UiLayout.Rect field,UiLayout.Rect bounds){int below=bounds.y()+bounds.height()-(field.y()+field.height()),above=field.y()-bounds.y();boolean up=above>below;int rows=Math.clamp(Math.max(above,below)/14,1,9),height=rows*14;int y=up?field.y()-height:field.y()+field.height();y=Math.clamp(y,bounds.y(),Math.max(bounds.y(),bounds.y()+bounds.height()-height));return new UiLayout.Rect(field.x(),y,field.width(),Math.min(height,bounds.height()));}
    private UiLayout.Rect popup(){return popup(new UiLayout.Rect(getX(),getY(),width,height),bounds.get());}
    private int capacity(){return Math.max(1,popup().height()/14);}
    private void ensure(){if(focus<first)first=focus;if(focus>=first+capacity())first=focus-capacity()+1;first=Math.clamp(first,0,9-capacity());}
    @Override public boolean isMouseOver(double x,double y){return super.isMouseOver(x,y)||open&&popup().contains(x,y);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;String current=value.get();var lines=IdentityLayout.lines(current.isBlank()?"Not selected":current,Math.max(1,width-16),font::getWidth);RpgTheme.panel(g,getX(),getY(),width,height,RpgTheme.PANEL,isFocused());RpgTheme.text(g,font,lines.getFirst(),getX()+4,getY()+4,width-15,RpgTheme.TEXT);g.drawText(font,open?"−":"+",getRight()-10,getY()+4,RpgTheme.GOLD,false);if(isMouseOver(mx,my))MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal("Alignment: "+(current.isBlank()?"Not selected":current)+(current.isBlank()||IdentityModel.ALIGNMENTS.contains(current)?"":" (existing custom value)")+"\nChoose one of the nine conventional alignments."));if(!open)return;ensure();var r=popup();RpgTheme.panel(g,r.x(),r.y(),r.width(),r.height(),RpgTheme.PANEL,true);for(int i=first;i<Math.min(9,first+capacity());i++){int y=r.y()+(i-first)*14;if(i==(r.contains(mx,my)?Math.min(8,first+(my-r.y())/14):focus))g.fill(r.x()+1,y+1,r.x()+r.width()-1,y+13,RpgTheme.HOVER);RpgTheme.text(g,font,IdentityModel.ALIGNMENTS.get(i),r.x()+4,y+3,r.width()-8,value.get().equals(IdentityModel.ALIGNMENTS.get(i))?RpgTheme.GOLD:RpgTheme.TEXT);if(new UiLayout.Rect(r.x(),y,r.width(),14).contains(mx,my))MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(IdentityModel.ALIGNMENTS.get(i)));}}
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;if(super.isMouseOver(x,y)){open=!open;ensure();return true;}if(open&&popup().contains(x,y)){int i=first+(int)(y-popup().y())/14;if(i<9){choose.accept(IdentityModel.ALIGNMENTS.get(i));focus=i;open=false;}return true;}return false;}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!open)return false;first=Math.clamp(first+(vertical>0?-1:1),0,9-capacity());focus=Math.clamp(focus,first,Math.min(8,first+capacity()-1));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!isFocused())return false;if(key==GLFW.GLFW_KEY_ESCAPE&&open){open=false;return true;}if(key==GLFW.GLFW_KEY_UP||key==GLFW.GLFW_KEY_DOWN){open=true;focus=Math.clamp(focus+(key==GLFW.GLFW_KEY_UP?-1:1),0,8);ensure();return true;}if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){if(open){choose.accept(IdentityModel.ALIGNMENTS.get(focus));open=false;}else open=true;return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal("Alignment: "+(open?IdentityModel.ALIGNMENTS.get(focus):value.get())));b.put(NarrationPart.USAGE,Text.literal("Enter opens, arrows select, Enter confirms, Escape closes the alignment list."));}
}
