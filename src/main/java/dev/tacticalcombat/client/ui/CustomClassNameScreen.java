package dev.tacticalcombat.client.ui;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.function.Consumer;
/** Captures a custom name only; the creator's normal class confirmation owns the authoritative transition. */
public final class CustomClassNameScreen extends RpgScreen {
    private final Consumer<String> choose;
    private String value;
    public CustomClassNameScreen(Screen parent,String initial,Consumer<String> choose){super("Custom Class",parent);value=initial;this.choose=choose;}
    @Override protected void init(){super.init();clearChildren();var input=addDrawableChild(new TextFieldWidget(textRenderer,frame.x()+12,frame.y()+48,frame.width()-24,20,Text.literal("Custom class name")));input.setMaxLength(128);input.setText(value);input.setChangedListener(v->value=v);footer();footerAction("Choose Class",1,()->{if(value.trim().isEmpty()){message="Enter a class name.";return;}choose.accept(value.trim());});setInitialFocus(input);}
    @Override protected void content(net.minecraft.client.gui.DrawContext g,int x,int y,float delta){RpgTheme.text(g,textRenderer,"Name",frame.x()+12,frame.y()+36,frame.width()-24,RpgTheme.MUTED);paragraphs(g,"Custom classes remain manual records. Changing an existing guided class requires confirmation and resets its creation allocation.",frame.x()+12,frame.y()+80,frame.width()-24,Math.max(0,frame.height()-120));}
}
