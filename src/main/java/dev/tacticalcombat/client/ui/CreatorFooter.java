package dev.tacticalcombat.client.ui;

/** Stable footer anchors use the outer creator frame, including specialized pages. */
public final class CreatorFooter {
    private CreatorFooter() {}
    public static int extra(UiLayout.Rect frame){return frame.width()<420?12:0;}
    public static UiLayout.Rect back(UiLayout.Rect frame,boolean adaptive){return adaptive&&extra(frame)>0?new UiLayout.Rect(frame.x()+12,frame.y()+frame.height()-42,44,32):back(frame);}
    public static UiLayout.Rect action(UiLayout.Rect frame,int slot,boolean adaptive){if(!adaptive||extra(frame)==0)return action(frame,slot);if(slot==2)return new UiLayout.Rect(frame.x()+frame.width()-56,frame.y()+8,44,20);int w=(frame.width()-80)/2;return new UiLayout.Rect(frame.x()+62+slot*(w+6),frame.y()+frame.height()-42,w,32);}
    public static UiLayout.Rect back(UiLayout.Rect frame){return new UiLayout.Rect(frame.x()+12,frame.y()+frame.height()-30,66,20);}
    public static UiLayout.Rect action(UiLayout.Rect frame,int slot){if(slot<0||slot>2)throw new IllegalArgumentException("Footer slot");int available=frame.width()-96,secondary=Math.min(66,(available-12)/3),w=(available-secondary-12)/2;return new UiLayout.Rect(frame.x()+84+(slot==2?2*(w+6):slot*(w+6)),frame.y()+frame.height()-30,slot==2?secondary:w,20);}
}
