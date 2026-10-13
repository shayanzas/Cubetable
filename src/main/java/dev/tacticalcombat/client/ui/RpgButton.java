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
    private boolean selected;
    private String icon="",accessible="";
    public RpgButton(int x,int y,int w,int h,String title,String detail,Runnable action) {super(x,y,w,h,Text.literal(title));this.detail=detail;this.action=action;}
    public RpgButton hint(String text) {setTooltip(Tooltip.of(Text.literal(text)));return this;}
    public RpgButton selected(boolean selected) {this.selected=selected;return this;}
    public RpgButton accessible(String label){accessible=label;return this;}
    public static int labelPadding(int width){return width<40?2:8;}
    public RpgButton icon(String key) {icon=key;return this;}
    public void detail(String detail) {this.detail=detail;}
    @Override public void onPress() {if(active) action.run();}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta) {
        var font=MinecraftClient.getInstance().textRenderer;boolean hover=isHovered() || isFocused() || selected;
        RpgTheme.panel(g,getX(),getY(),width,height,active && hover?RpgTheme.HOVER:RpgTheme.PANEL,active && hover);
        int color=active?RpgTheme.TEXT:RpgTheme.MUTED;
        if(width<40&&icon.isEmpty()){String label=font.trimToWidth(getMessage().getString(),width-2*labelPadding(width));g.drawText(font,label,getX()+(width-font.getWidth(label))/2,getY()+(height-9)/2,color,false);return;}
        if(!icon.isEmpty())CreatorIcons.draw(g,icon,getX()+4,getY()+(height-14)/2,14);
        if(detail.isEmpty() || height<30) RpgTheme.text(g,font,getMessage().getString(),getX()+(icon.isEmpty()?8:22),getY()+(height-9)/2,width-(icon.isEmpty()?16:27),color);
        else {
            int textY=getY()+(height-23)/2;
            RpgTheme.text(g,font,getMessage().getString(),getX()+10,textY,width-20,active?RpgTheme.GOLD:color);
            RpgTheme.text(g,font,detail,getX()+10,textY+14,width-20,RpgTheme.MUTED);
        }
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b) {if(accessible.isEmpty())appendDefaultNarrations(b);else b.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,Text.literal(accessible));}
}
