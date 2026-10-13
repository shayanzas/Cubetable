package dev.tacticalcombat.client.ui;

import java.util.*;
import java.util.function.ToIntFunction;

/** Race card geometry is measured against the active Minecraft font, never a character count. */
public final class IdentityLayout {
    public record Layout(UiLayout.Rect grid,UiLayout.Rect information,boolean stacked,List<UiLayout.Rect> cards) {}
    private IdentityLayout() {}
    public static List<String> lines(String label,int width,ToIntFunction<String> measure){var lines=new ArrayList<String>();StringBuilder line=new StringBuilder();for(char c:label.toCharArray()){if(!line.isEmpty()&&measure.applyAsInt(line.toString()+c)>width){lines.add(line.toString());line.setLength(0);}line.append(c);}if(!line.isEmpty())lines.add(line.toString());return List.copyOf(lines);}
    public static Layout layout(int width,int height,ToIntFunction<String> measure){int x=6,w=width-12,gap=4,top=62;int widest=Srd2014Catalog.SPECIES.stream().mapToInt(c->measure.applyAsInt(c.name())).max().orElse(60);int minimum=3*(widest+14)+2*gap;boolean stacked=w<minimum+170+12;int left=stacked?w:Math.max(minimum,(w-12)/2),right=stacked?w:w-left-12;int cw=(left-2*gap)/3,gridWidth=3*cw+2*gap;int lineCount=Srd2014Catalog.SPECIES.stream().mapToInt(c->lines(c.name(),Math.max(1,cw-12),measure).size()).max().orElse(1);int ch=Math.max(lineCount*10+12,Math.min(150,(height-top-28-2*gap)/3));int gh=3*ch+2*gap;var grid=new UiLayout.Rect(x,top,gridWidth,gh);var cards=new ArrayList<UiLayout.Rect>();for(int i=0;i<9;i++)cards.add(new UiLayout.Rect(x+i%3*(cw+gap),top+i/3*(ch+gap),cw,ch));return new Layout(grid,new UiLayout.Rect(stacked?x:x+left+12,stacked?top+gh+30:top,right,Math.max(22,height-top)),stacked,List.copyOf(cards));}
}
