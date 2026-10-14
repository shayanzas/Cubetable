package dev.tacticalcombat.client.ui;
import java.util.function.ToIntFunction;
/** Content-sized modal, bounded by the viewport; long comparisons scroll in the text box. */
public final class ConfirmationLayout {
    private ConfirmationLayout(){}
    public static UiLayout.Rect panel(int width,int height,String question,ToIntFunction<String> lines){int w=Math.max(60,Math.min(480,width-24)),h=Math.min(Math.max(80,height-24),Math.max(126,Math.min(260,lines.applyAsInt(question)*12+88)));return new UiLayout.Rect((width-w)/2,(height-h)/2,w,h);}
}
