package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;

/** Shared keyboard/mouse accessible inspection, not a fake activation action. */
public final class FeatureInspectScreen extends RpgScreen {
    private final FeatureReference.Entry entry;
    public FeatureInspectScreen(Screen parent,FeatureReference.Entry entry){super(entry.name(),parent);this.entry=entry;}
    @Override protected void init(){super.init();clearChildren();addDrawableChild(new RpgTextBox(frame.x()+12,frame.y()+38,frame.width()-24,Math.max(32,frame.height()-78),entry.detail()));footer();}
}
