package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;

public final class RpgConfirmationScreen extends RpgScreen {
    private final String question;
    private final Runnable accepted;
    private final ConfirmationDecision decision=new ConfirmationDecision();
    private Runnable cancelled;
    public RpgConfirmationScreen onCancel(Runnable action){cancelled=action;return this;}
    @Override public void close(){if(decision.cancel()){client.setScreen(parent);if(cancelled!=null)cancelled.run();}}
    private String cancelLabel="Cancel",acceptLabel="Confirm";
    public RpgConfirmationScreen labels(String cancel,String accept){cancelLabel=cancel;acceptLabel=accept;return this;}
    public RpgConfirmationScreen(Screen parent,String title,String question,Runnable accepted) {super(title,parent);this.question=question;this.accepted=accepted;}
    @Override protected UiLayout.Rect footerFrame(){return frame;}
    @Override protected boolean creatorFooter(){return false;}
    @Override protected void background(net.minecraft.client.gui.DrawContext g){g.fill(0,0,width,height,RpgTheme.BACKGROUND);RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),RpgTheme.PANEL,false);RpgTheme.text(g,textRenderer,getTitle().getString(),frame.x()+12,frame.y()+12,frame.width()-24,RpgTheme.GOLD);}
    @Override protected void init() {
        super.init();clearChildren();frame=ConfirmationLayout.panel(width,height,question,text->textRenderer.getTextHandler().wrapLines(text,Math.max(1,Math.min(480,width-48)-24),net.minecraft.text.Style.EMPTY).size());int extra=0;addDrawableChild(new RpgTextBox(frame.x()+12,frame.y()+40,frame.width()-24,Math.max(12,frame.height()-82-extra),question));
        var cancel=button(cancelLabel,"",frame.x()+12,frame.y()+frame.height()-30-extra,(frame.width()-30)/2,20+extra,this::close).wrappedTitle().hint(cancelLabel);
        button(acceptLabel,"",frame.x()+18+(frame.width()-30)/2,frame.y()+frame.height()-30-extra,(frame.width()-30)/2,20+extra,()->{if(!decision.confirm())return;client.setScreen(parent);accepted.run();}).wrappedTitle().hint(acceptLabel);setInitialFocus(cancel);
    }
}
