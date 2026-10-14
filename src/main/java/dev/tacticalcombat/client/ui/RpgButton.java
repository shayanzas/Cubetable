package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.text.Text;

public final class RpgButton extends PressableWidget {
    private final Runnable action;
    private String detail;
    private boolean selected,round,wrapped;
    public RpgButton wrappedTitle(){wrapped=true;return this;}
    public RpgButton round(){round=true;return this;}
    public static boolean markerPixel(int x,int y,int size,boolean filled){int dx=2*x+1-size,dy=2*y+1-size,r=Math.max(4,size-6),d=dx*dx+dy*dy;return d<=r*r&&(filled||d>(r-2)*(r-2));}
    public static void drawMarker(DrawContext g,int x,int y,int size,boolean filled,int color){for(int py=0;py<size;py++)for(int px=0;px<size;px++)if(markerPixel(px,py,size,filled))g.fill(x+px,y+py,x+px+1,y+py+1,color);}
    public static int disabledColor(){return 0xFF747983;}
    public static int bodyColor(boolean active,boolean hover){return !active?0xFF14171B:hover?RpgTheme.HOVER:RpgTheme.PANEL;}
    private String icon="",accessible="";
    public RpgButton(int x,int y,int w,int h,String title,String detail,Runnable action) {super(x,y,w,h,Text.literal(title));this.detail=detail;this.action=action;}
    public RpgButton hint(String text) {setTooltip(Tooltip.of(Text.literal(text)));return this;}
    public RpgButton selected(boolean selected) {this.selected=selected;return this;}
    public RpgButton accessible(String label){accessible=label;return this;}
    public static int labelPadding(int width){return width<64?2:8;}
    public RpgButton icon(String key) {icon=key;return this;}
    public void detail(String detail) {this.detail=detail;}
    @Override public void onPress() {if(active) action.run();}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta) {
        var font=MinecraftClient.getInstance().textRenderer;boolean hover=isHovered() || isFocused() || selected;
        RpgTheme.panel(g,getX(),getY(),width,height,bodyColor(active,hover),selected);if(active&&isFocused()&&(!selected||round))g.drawBorder(getX()+2,getY()+2,width-4,height-4,round?RpgTheme.GOLD:RpgTheme.FOCUS);
        int color=selected?RpgTheme.GOLD:active?RpgTheme.TEXT:disabledColor();
        if(round){int size=Math.min(width,height),ox=getX()+(width-size)/2,oy=getY()+(height-size)/2;drawMarker(g,ox,oy,size,selected,selected?RpgTheme.GOLD:color);return;}

        if(wrapped&&height>=30){var lines=font.wrapLines(getMessage(),Math.max(1,width-8));int y=getY()+(height-lines.size()*10)/2;for(var line:lines){g.drawText(font,line,getX()+(width-font.getWidth(line))/2,y,color,false);y+=10;}return;}
        if(width<64&&icon.isEmpty()){String label=font.trimToWidth(getMessage().getString(),width-2*labelPadding(width));g.drawText(font,label,getX()+(width-font.getWidth(label))/2,getY()+(height-9)/2,color,false);return;}
        if(!icon.isEmpty())CreatorIcons.draw(g,icon,getX()+4,getY()+(height-14)/2,14);
        if(detail.isEmpty() || height<30) RpgTheme.text(g,font,getMessage().getString(),getX()+(icon.isEmpty()?8:22),getY()+(height-9)/2,width-(icon.isEmpty()?16:27),color);
        else {
            int textY=getY()+(height-23)/2;
            RpgTheme.text(g,font,getMessage().getString(),getX()+10,textY,width-20,color);
            RpgTheme.text(g,font,detail,getX()+10,textY+14,width-20,RpgTheme.MUTED);
        }
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b) {if(accessible.isEmpty())appendDefaultNarrations(b);else b.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,Text.literal(accessible));}
}
