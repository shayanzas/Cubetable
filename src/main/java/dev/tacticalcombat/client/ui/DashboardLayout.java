package dev.tacticalcombat.client.ui;
import java.util.*;
/** Logical geometry for direct tabs and dense, scrollable dashboard cards. */
public final class DashboardLayout {
    private DashboardLayout(){}
    public static int columns(UiLayout.Rect frame){return frame.height()<200?5:frame.width()<600?3:5;}
    public static int top(UiLayout.Rect frame){return frame.height()<200?54:76;}
    public static int stride(UiLayout.Rect frame){return frame.height()<200?15:18;}
    public static List<UiLayout.Rect> tabs(UiLayout.Rect frame){int columns=columns(frame),w=(frame.width()-24-(columns-1)*3)/columns;var out=new ArrayList<UiLayout.Rect>();for(int i=0;i<9;i++)out.add(new UiLayout.Rect(frame.x()+12+i%columns*(w+3),frame.y()+top(frame)+i/columns*stride(frame),w,stride(frame)-1));return List.copyOf(out);}
    public static UiLayout.Rect body(UiLayout.Rect frame){int top=top(frame)+(9+columns(frame)-1)/columns(frame)*stride(frame)+4;return new UiLayout.Rect(frame.x()+12,frame.y()+top,frame.width()-24,Math.max(12,frame.height()-top-38));}
}
