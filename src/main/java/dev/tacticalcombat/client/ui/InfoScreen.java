package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;

/** Clearly labelled pending-development destination with readable, scrollable context. */
public final class InfoScreen extends RpgScreen {
    private final String heading,description;
    public InfoScreen(Screen parent,String title,String heading,String description) {super(title,parent);this.heading=heading;this.description=description;}
    @Override protected void init() {super.init();clearChildren();addDrawableChild(new RpgTextBox(frame.x()+12,frame.y()+40,frame.width()-24,Math.max(38,frame.height()-78),heading+"\n\n"+description));footer();}
}
