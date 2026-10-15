package dev.tacticalcombat.client.ui;

/** Compact right-side viewport, with scrolling contents independent of screen scale. */
public final class QuickPanelLayout {
    private QuickPanelLayout() {}
    public static UiLayout.Rect frame(int width,int height){int w=Math.min(360,Math.max(1,width-16)),h=Math.min(420,Math.max(1,height-16));return new UiLayout.Rect(Math.max(0,width-w-8),Math.max(0,(height-h)/2),w,h);}
    public static boolean pagination(int page,boolean hasMore){return page>0||hasMore;}
}
