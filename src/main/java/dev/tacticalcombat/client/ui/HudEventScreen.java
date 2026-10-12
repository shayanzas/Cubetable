package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import java.util.UUID;

/** Resolves details from the current authorized cache on every display; no retained private copy. */
public final class HudEventScreen extends RpgScreen {
    private final UUID event;
    public HudEventScreen(Screen parent,UUID event){super("Cubetable event details",parent);this.event=event;}
    @Override protected void init(){super.init();clearChildren();footer();}
    @Override protected void content(DrawContext g,int mx,int my,float delta){var line=CubetableHud.MODEL.event(event);String details=line==null?"Event unavailable while history is refreshed or permissions are rechecked.":line.kind()+" · event "+line.sequence()+"\n\n"+line.summary()+"\n\n"+line.calculation()+(line.roll()==null?"":"\n\nKept indices: "+line.roll().kept()+"\nServer critical result: "+line.roll().critical());paragraphs(g,details,frame.x()+12,frame.y()+40,frame.width()-24,frame.height()-80);}
}
