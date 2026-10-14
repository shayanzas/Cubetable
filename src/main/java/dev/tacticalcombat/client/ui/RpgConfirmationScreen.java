package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;

public final class RpgConfirmationScreen extends RpgScreen {
    private final String question;
    private final Runnable accepted;
    private boolean resolved;
    private Runnable cancelled;
    public RpgConfirmationScreen onCancel(Runnable action){cancelled=action;return this;}
    @Override public void close(){if(!resolved){resolved=true;client.setScreen(parent);if(cancelled!=null)cancelled.run();}}
    private String cancelLabel="Cancel",acceptLabel="Confirm";
    public RpgConfirmationScreen labels(String cancel,String accept){cancelLabel=cancel;acceptLabel=accept;return this;}
    public RpgConfirmationScreen(Screen parent,String title,String question,Runnable accepted) {super(title,parent);this.question=question;this.accepted=accepted;}
    @Override protected void init() {
        super.init();clearChildren();int extra=footerExtra();addDrawableChild(new RpgTextBox(frame.x()+12,frame.y()+40,frame.width()-24,Math.max(12,frame.height()-82-extra),question));
        var cancel=button(cancelLabel,"",frame.x()+12,frame.y()+frame.height()-30-extra,(frame.width()-30)/2,20+extra,this::close).wrappedTitle().hint(cancelLabel);
        button(acceptLabel,"",frame.x()+18+(frame.width()-30)/2,frame.y()+frame.height()-30-extra,(frame.width()-30)/2,20+extra,()->{if(resolved) return;resolved=true;client.setScreen(parent);accepted.run();}).wrappedTitle().hint(acceptLabel);setInitialFocus(cancel);
    }
}
