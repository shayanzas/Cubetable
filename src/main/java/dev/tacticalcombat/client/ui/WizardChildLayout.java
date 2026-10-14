package dev.tacticalcombat.client.ui;

/** Guided editing pages share one rail and frame, even when specialized native editors own the content. */
public final class WizardChildLayout {
    private WizardChildLayout(){}
    public static UiLayout.Rect rail(UiLayout.Rect outer){return new UiLayout.Rect(outer.x()+10,outer.y()+36,outer.width()<500?78:104,Math.max(1,outer.height()-74-CreatorFooter.extra(outer)));}
    public static UiLayout.Rect content(UiLayout.Rect outer){int side=rail(outer).width()+16;return new UiLayout.Rect(outer.x()+side,outer.y(),Math.max(1,outer.width()-side),outer.height());}
}
