package dev.tacticalcombat.client.ui;
import java.util.*;
/** Shared vertical dashboard rail and contextual body. The rail scrolls in compact windows. */
public final class DashboardLayout {
    private DashboardLayout(){}
    public static List<UiLayout.Rect> tabs(UiLayout.Rect frame){var r=CharacterShell.layout(frame).rail();var out=new ArrayList<UiLayout.Rect>();for(int i=0;i<Math.min(DashboardModel.Tab.values().length,Math.max(1,r.height()/16));i++)out.add(new UiLayout.Rect(r.x(),r.y()+i*16,r.width(),15));return List.copyOf(out);}
    public static UiLayout.Rect body(UiLayout.Rect frame){return CharacterShell.layout(frame).content();}
}
