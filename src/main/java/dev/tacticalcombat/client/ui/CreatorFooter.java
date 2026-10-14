package dev.tacticalcombat.client.ui;

/** Stable footer anchors use the outer creator frame, including specialized pages. */
public final class CreatorFooter {
    private CreatorFooter() {}
    public static UiLayout.Rect back(UiLayout.Rect frame){return new UiLayout.Rect(frame.x()+12,frame.y()+frame.height()-30,66,20);}
    public static UiLayout.Rect action(UiLayout.Rect frame,int slot){if(slot<0||slot>2)throw new IllegalArgumentException("Footer slot");int w=(frame.width()-108)/3;return new UiLayout.Rect(frame.x()+84+slot*(w+6),frame.y()+frame.height()-30,w,20);}
}
