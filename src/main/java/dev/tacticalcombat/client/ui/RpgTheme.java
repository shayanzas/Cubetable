package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;

/** Original procedural panels, native-size Minecraft text, no borrowed artwork or font transforms. */
public final class RpgTheme {
    public static final int BACKGROUND=0xFF111419,PANEL=0xFF20242A,HOVER=0xFF2C323A,EDGE=0xFF505057,GOLD=0xFFE3BE73,TEXT=0xFFF0EDE7,MUTED=0xFFB2B7BE,ERROR=0xFFFF9A91,SUCCESS=0xFFA8D5AC;
    private RpgTheme() {}
    public static void panel(DrawContext g,int x,int y,int w,int h,int color,boolean highlighted) {
        g.fill(x,y,x+w,y+h,highlighted?GOLD:EDGE);g.fill(x+1,y+1,x+w-1,y+h-1,color);
        g.fill(x+2,y+2,x+w-2,y+3,0xFF373C43);
    }
    public static void text(DrawContext g,TextRenderer font,String value,int x,int y,int w,int color) {g.drawText(font,font.trimToWidth(value,Math.max(0,w)),x,y,color,false);}
}
