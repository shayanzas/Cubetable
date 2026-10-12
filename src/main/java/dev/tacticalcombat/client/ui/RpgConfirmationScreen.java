package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;

public final class RpgConfirmationScreen extends RpgScreen {
    private final String question;
    private final Runnable accepted;
    private boolean resolved;
    public RpgConfirmationScreen(Screen parent,String title,String question,Runnable accepted) {super(title,parent);this.question=question;this.accepted=accepted;}
    @Override protected void init() {
        super.init();clearChildren();addDrawableChild(new RpgTextBox(frame.x()+12,frame.y()+40,frame.width()-24,Math.max(38,frame.height()-82),question));
        var cancel=button("Cancel","",frame.x()+12,frame.y()+frame.height()-30,(frame.width()-30)/2,20,this::close);
        button("Confirm","",frame.x()+18+(frame.width()-30)/2,frame.y()+frame.height()-30,(frame.width()-30)/2,20,()->{if(resolved) return;resolved=true;client.setScreen(parent);accepted.run();});setInitialFocus(cancel);
    }
}
