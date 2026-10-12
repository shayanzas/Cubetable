package dev.tacticalcombat.client.ui;

import net.fabricmc.fabric.api.client.screen.v1.*;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Fabric-supported additive integration; never moves/removes vanilla or other mods' controls. */
public final class PauseMenuEntry {
    private PauseMenuEntry() {}
    public static void register() {
        ScreenEvents.AFTER_INIT.register((client,screen,width,height)->{
            if(!(screen instanceof GameMenuScreen menu) || !menu.shouldShowMenu() || client.player==null) return;
            var controls=Screens.getButtons(screen);
            if(controls.stream().anyMatch(b->b.getMessage().getString().equals("Cubetable"))) return;
            var rects=new ArrayList<UiLayout.Rect>();for(var b:controls) if(b.visible) rects.add(new UiLayout.Rect(b.getX(),b.getY(),b.getWidth(),b.getHeight()));
            // Reserve the vanilla menu title as well as widgets.
            rects.add(new UiLayout.Rect(Math.max(0,width/2-64),18,128,18));
            UiLayout.pauseButton(width,height,rects).ifPresent(r->controls.add(ButtonWidget.builder(Text.literal("Cubetable"),b->client.setScreen(new HubScreen(screen))).dimensions(r.x(),r.y(),r.width(),r.height()).build()));
        });
    }
}
