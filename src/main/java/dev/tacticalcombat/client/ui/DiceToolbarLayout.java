package dev.tacticalcombat.client.ui;

/** Layout invariants shared by passive and interactive dice presentation. */
public final class DiceToolbarLayout {
    public static UiLayout.Rect bounds(int width,int height){int w=Math.min(400,Math.max(1,width-16)),h=Math.min(108,Math.max(1,height-80));return new UiLayout.Rect(Math.max(0,(width-w)/2),Math.max(0,height-66-h),w,h);}
    public static int modeWidth(int innerWidth){return Math.max(25,innerWidth-147);}
    private DiceToolbarLayout(){}
}
