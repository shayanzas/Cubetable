package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.uiapi.UiApiService;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.function.Consumer;

/** Explicit parent navigation and one procedural background pass before foreground content. */
public abstract class RpgScreen extends Screen {
    protected final Screen parent;
    protected UiLayout.Rect frame;
    protected String message="";
    protected int messageColor=RpgTheme.MUTED;
    protected long generation;
    protected boolean busy;
    protected RpgScreen(String title,Screen parent) {super(Text.literal(title));this.parent=parent;}
    @Override protected void init() {generation++;frame=UiLayout.frame(width,height,maximumWidth());}
    @Override public void removed() {generation++;}
    @Override public void close() {client.setScreen(parent);}
    @Override public boolean shouldPause() {return false;}
    @Override public void renderBackground(DrawContext g,int mx,int my,float delta) { }
    protected RpgButton button(String title,String detail,int x,int y,int w,int h,Runnable action) {return addDrawableChild(new RpgButton(x,y,w,h,title,detail,action));}
    protected TextFieldWidget search(int x,int y,int w,String value,Consumer<String> listener) {
        var field=addDrawableChild(new TextFieldWidget(textRenderer,x,y,w,20,Text.literal("Search this page")));
        field.setMaxLength(80);field.setText(value);field.setPlaceholder(Text.literal("Search this page…"));field.setChangedListener(listener);return field;
    }
    protected void footer() {
        button("Back","",frame.x()+12,frame.y()+frame.height()-30,66,20,this::close).hint("Return to the previous screen (Escape)");
    }
    protected void request(UiApiService.Operation op,JsonObject args,Consumer<UiApiService.Reply> result) {
        long current=generation;busy=true;message="Loading…";messageColor=RpgTheme.MUTED;
        UiRequestQueue.enqueue(op,args,()->client!=null && client.currentScreen==this && generation==current,reply->{
            busy=false;
            if(!reply.code().equals("OK")) {message=reply.code().equals("RATE_LIMIT")?"The server is busy. Wait briefly, then refresh; your character and edits are retained.":reply.code()+": "+reply.message();messageColor=RpgTheme.ERROR;}
            try {result.accept(reply);} catch(RuntimeException invalid) {org.slf4j.LoggerFactory.getLogger(RpgScreen.class).warn("Invalid UI response for {} ({})",op,reply.code(),invalid);message="Invalid server response. Refresh to retry.";messageColor=RpgTheme.ERROR;}
        });
    }
    protected void background(DrawContext g) {
        g.fill(0,0,width,height,RpgTheme.BACKGROUND);RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),0xFF191D23,false);
        g.fill(frame.x()+12,frame.y()+29,frame.x()+frame.width()-12,frame.y()+30,RpgTheme.GOLD);
        RpgTheme.text(g,textRenderer,getTitle().getString(),frame.x()+12,frame.y()+12,frame.width()-24,RpgTheme.GOLD);
    }
    protected int maximumWidth(){return 720;}
    protected int statusWidth() {return frame.width()-100;}
    protected void content(DrawContext g,int mx,int my,float delta) { }
    @Override public void render(DrawContext g,int mx,int my,float delta) {
        background(g);content(g,mx,my,delta);super.render(g,mx,my,delta);
        RpgTheme.text(g,textRenderer,(busy?"… ":"")+message,frame.x()+12,frame.y()+frame.height()-9,frame.width()-24,messageColor);
        if(new UiLayout.Rect(frame.x()+12,frame.y()+frame.height()-10,frame.width()-24,10).contains(mx,my) && !message.isEmpty()) setTooltip(Text.literal(message));
    }
    protected void paragraphs(DrawContext g,String text,int x,int y,int w,int maxHeight) {
        int bottom=y+maxHeight;for(var line:textRenderer.wrapLines(Text.literal(text),w)) {if(y+9>bottom) break;g.drawText(textRenderer,line,x,y,RpgTheme.MUTED,false);y+=12;}
    }
}
