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
    public RpgButton(int x,int y,int w,int h,String title,String detail,Runnable action) {super(x,y,w,h,Text.literal(title));this.detail=detail;this.action=action;}
    public RpgButton hint(String text) {setTooltip(Tooltip.of(Text.literal(text)));return this;}
    public RpgButton selected(boolean selected) {this.selected=selected;return this;}
    public void detail(String detail) {this.detail=detail;}
    @Override public void onPress() {if(active) action.run();}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta) {
        var font=MinecraftClient.getInstance().textRenderer;boolean hover=isHovered() || isFocused() || selected;
        RpgTheme.panel(g,getX(),getY(),width,height,active && hover?RpgTheme.HOVER:RpgTheme.PANEL,active && hover);
        int color=active?RpgTheme.TEXT:RpgTheme.MUTED;
        if(detail.isEmpty() || height<30) RpgTheme.text(g,font,getMessage().getString(),getX()+8,getY()+(height-9)/2,width-16,color);
        else {
            int textY=getY()+(height-23)/2;
            RpgTheme.text(g,font,getMessage().getString(),getX()+10,textY,width-20,active?RpgTheme.GOLD:color);
            RpgTheme.text(g,font,detail,getX()+10,textY+14,width-20,RpgTheme.MUTED);
        }
    }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b) {appendDefaultNarrations(b);}
}
