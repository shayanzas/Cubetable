package dev.tacticalcombat.client.ui;
import java.util.*;
/** Six readable wrapped destinations, separated from the management button. */
public final class DashboardLayout {
    private DashboardLayout(){}
    public static CharacterShell.Layout shell(UiLayout.Rect frame){int x=frame.x()+10,y=frame.y()+36,w=frame.width()-20,h=frame.height()-74,rail=frame.width()<500?112:132,summary=frame.width()>=900?128:0,cx=x+rail+8,cw=w-rail-8-(summary==0?0:summary+8);if(summary==0){int strip=h<90?16:25,gap=h<90?3:4;return new CharacterShell.Layout(new UiLayout.Rect(x,y,rail,h),new UiLayout.Rect(cx,y+strip+gap,cw,Math.max(1,h-strip-gap)),new UiLayout.Rect(cx,y,cw,strip),true);}return new CharacterShell.Layout(new UiLayout.Rect(x,y,rail,h),new UiLayout.Rect(cx,y,cw,h),new UiLayout.Rect(cx+cw+8,y,summary,h),false);}
    public static UiLayout.Rect navigation(UiLayout.Rect frame){var r=shell(frame).rail();return new UiLayout.Rect(r.x(),r.y(),r.width(),Math.max(16,r.height()-26));}
    public static UiLayout.Rect management(UiLayout.Rect frame){var r=shell(frame).rail();return new UiLayout.Rect(r.x(),r.y()+r.height()-22,r.width(),20);}
    public static List<UiLayout.Rect> tabs(UiLayout.Rect frame){var r=navigation(frame);var out=new ArrayList<UiLayout.Rect>();for(int i=0;i<Math.min(6,Math.max(1,r.height()/28));i++)out.add(new UiLayout.Rect(r.x(),r.y()+i*28,r.width(),27));return List.copyOf(out);}
    public static UiLayout.Rect body(UiLayout.Rect frame){return shell(frame).content();}
}
