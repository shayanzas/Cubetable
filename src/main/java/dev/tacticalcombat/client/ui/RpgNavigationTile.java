package dev.tacticalcombat.client.ui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.*;
/** Illustrated navigation card with native wrapped text, a recessed emblem and explicit disabled/focus treatment. */
public final class RpgNavigationTile extends PressableWidget {
 private final String description,icon;private final Runnable action;private String reason="";private boolean availabilitySet;
 public RpgNavigationTile(int x,int y,int w,int h,String title,String description,String icon,Runnable action){super(x,y,w,h,Text.literal(title));this.description=description;this.icon=icon;this.action=action;availability(true,"");}
 public void availability(boolean enabled,String why){if(availabilitySet&&active==enabled&&reason.equals(why))return;availabilitySet=true;active=enabled;reason=why;setTooltip(Tooltip.of(Text.literal(getMessage().getString()+"\n"+description+(enabled?"":"\n"+why))));}
 @Override public void onPress(){if(active)action.run();}
 @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;boolean focus=isFocused(),hover=isHovered()&&active;int label=active?RpgTheme.TEXT:RpgButton.disabledColor();RpgTheme.panel(g,getX(),getY(),width,height,RpgButton.bodyColor(active,hover||focus),hover||focus);g.fill(getX()+1,getY()+1,getRight()-1,getY()+3,active?0xFF4C4433:0xFF24272B);if(focus)g.drawBorder(getX()+3,getY()+3,width-6,height-6,RpgTheme.GOLD);int size=width<180?24:32,ix=getX()+10,iy=getY()+13;g.fill(ix-3,iy-3,ix+size+3,iy+size+3,0xFF11151A);if(icon.startsWith("creator:"))CreatorIcons.draw(g,icon.substring(8),ix,iy,size);else HubIcons.draw(g,icon,ix,iy,size);int tx=getX()+(width<180?43:54),tw=Math.max(1,getRight()-tx-10),y=getY()+12;var title=Text.literal(getMessage().getString()).formatted(net.minecraft.util.Formatting.BOLD);for(var line:font.wrapLines(title,tw)){if(y>getY()+47)break;g.drawText(font,line,tx,y,active?RpgTheme.GOLD:label,false);y+=10;}int dx=width<180?getX()+10:tx,dy=width<180?getY()+62:Math.max(getY()+35,y+5),dw=width<180?width-20:tw;for(var line:font.wrapLines(Text.literal(active?description:reason),Math.max(1,dw))){if(dy+9>getBottom()-9)break;g.drawText(font,line,dx,dy,active?RpgTheme.MUTED:label,false);dy+=11;}}
 @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal(getMessage().getString()+". "+description+(active?"":". Unavailable: "+reason)));}
}
