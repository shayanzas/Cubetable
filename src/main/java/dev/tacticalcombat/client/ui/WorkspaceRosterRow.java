package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Dense two-line roster. Inspect and encounter-selection are separate deliberate inputs. */
public final class WorkspaceRosterRow extends ClickableWidget {
    public static final int HEIGHT=28;
    private final String name,detail,status,hint,glyph;
    private final boolean selected,checked,checkable,toggleVisible;
    private final Runnable inspect,toggle;
    public WorkspaceRosterRow(int x,int y,int width,String name,String detail,String status,String glyph,String hint,boolean selected,boolean checked,boolean toggleVisible,boolean checkable,Runnable inspect,Runnable toggle){
        super(x,y,width,HEIGHT,Text.literal(name));this.name=name;this.detail=detail;this.status=status;this.glyph=glyph;this.hint=hint;this.selected=selected;this.checked=checked;this.toggleVisible=toggleVisible;this.checkable=checkable;this.inspect=inspect;this.toggle=toggle;
    }
    public record Columns(int nameX,int nameWidth,int statusX,int statusWidth,int checkboxX) {}
    public static Columns columns(int width,boolean toggle){int end=width-(toggle?26:5),status=width>=380?Math.min(112,width/4):0;return new Columns(24,Math.max(20,end-28-status),end-status,status,width-24);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){
        var font=MinecraftClient.getInstance().textRenderer;var c=columns(width,toggleVisible);
        g.fill(getX(),getY(),getRight(),getBottom()-1,selected?0xDD34352C:isHovered()?RpgTheme.HOVER:0xB51E232A);
        if(selected)g.fill(getX(),getY()+1,getX()+2,getBottom()-2,RpgTheme.GOLD);
        RpgTheme.text(g,font,glyph,getX()+6,getY()+9,14,RpgTheme.GOLD);
        RpgTheme.text(g,font,name,getX()+c.nameX(),getY()+4,c.nameWidth(),active?RpgTheme.TEXT:RpgTheme.MUTED);
        RpgTheme.text(g,font,detail,getX()+c.nameX(),getY()+16,width-(toggleVisible?52:30),RpgTheme.MUTED);
        if(c.statusWidth()>0)RpgTheme.text(g,font,status,getX()+c.statusX(),getY()+4,c.statusWidth()-4,RpgTheme.MUTED);
        if(toggleVisible)RpgButton.drawMarker(g,getX()+c.checkboxX()+3,getY()+6,16,checked,checked?RpgTheme.GOLD:checkable?RpgTheme.TEXT:RpgTheme.MUTED);
        if(active&&isFocused())g.drawBorder(getX()+1,getY()+1,width-2,height-3,RpgTheme.FOCUS);
        if(isMouseOver(mx,my))MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(name+"\n"+hint+(toggleVisible?"\nRight marker: add/remove encounter selection. Row: inspect.":"")));
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(!active||button!=0||!isMouseOver(x,y))return false;if(toggleVisible&&x>=getX()+columns(width,true).checkboxX()){if(checkable||checked)toggle.run();}else inspect.run();return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!active||!isFocused())return false;if(key==GLFW.GLFW_KEY_SPACE&&toggleVisible){if(checkable||checked)toggle.run();return true;}if(key==GLFW.GLFW_KEY_ENTER){inspect.run();return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder){builder.put(NarrationPart.TITLE,Text.literal(name+". "+detail+". "+status+". "+hint));builder.put(NarrationPart.USAGE,Text.literal(toggleVisible?"Enter inspects. Space toggles encounter selection.":"Enter inspects."));}
}
