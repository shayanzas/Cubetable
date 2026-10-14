package dev.tacticalcombat.client.ui;

/** Shared vertical character rail, contextual workspace and saved-character summary. */
public final class CharacterShell {
    public record Layout(UiLayout.Rect rail,UiLayout.Rect content,UiLayout.Rect summary,boolean compactSummary) {}
    private CharacterShell() {}
    public static UiLayout.Rect workspace(Layout layout,boolean review){var content=layout.content();return review&&layout.compactSummary()?new UiLayout.Rect(content.x(),layout.summary().y(),content.width(),content.y()+content.height()-layout.summary().y()):content;}
    public static Layout layout(UiLayout.Rect frame) {
        int x=frame.x()+10,y=frame.y()+36,w=frame.width()-20,h=frame.height()-74-CreatorFooter.extra(frame);
        int rail=frame.width()<500?78:104,summary=frame.width()>=760?136:0;
        int center=w-rail-6-(summary==0?0:summary+6),cx=x+rail+6;
        if(summary==0){int strip=h<90?16:25,gap=h<90?3:4;return new Layout(new UiLayout.Rect(x,y,rail,h),new UiLayout.Rect(cx,y+strip+gap,center,Math.max(1,h-strip-gap)),new UiLayout.Rect(cx,y,center,strip),true);}
        return new Layout(new UiLayout.Rect(x,y,rail,h),new UiLayout.Rect(cx,y,center,h),new UiLayout.Rect(cx+center+6,y,summary,h),false);
    }
}
