package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.client.DiceAnimation;
import dev.tacticalcombat.client.dicevisual.DicePresentationSettings;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

/** Local dice presentation controls. No server request or gameplay state mutation. */
public final class DicePresentationScreen extends RpgScreen {
    public DicePresentationScreen(Screen parent){super("Dice Presentation",parent);}
    @Override protected int maximumWidth(){return 430;}
    @Override protected void init(){super.init();clearChildren();var s=DiceAnimation.settings();int x=frame.x()+16,y=frame.y()+38,w=frame.width()-32,step=Math.max(16,Math.min(23,(frame.height()-55)/9)),bh=Math.min(19,step-1);
        button("Manual: "+s.manualMode(),"",x,y,w,bh,()->change(s.withModes(next(s.manualMode()),s.checkMode())));y+=step;
        button("Character checks: "+s.checkMode(),"",x,y,w,bh,()->change(s.withModes(s.manualMode(),next(s.checkMode()))));y+=step;
        button("Speed: "+s.speed()+"×","",x,y,w,bh,()->change(s.withSpeed(s.speed()>=2?.5:Math.round((s.speed()+.25)*100)/100d)));y+=step;
        button("Reduced motion: "+(s.reducedMotion()?"On":"Off"),"",x,y,w,bh,()->change(s.withReduced(!s.reducedMotion())));y+=step;
        button("Animation audio: "+(s.sound()?"On":"Off"),"",x,y,w,bh,()->change(s.withSound(!s.sound())));y+=step;
        button("Audio volume: "+Math.round(s.volume()*100)+"%","",x,y,w,bh,()->change(s.withVolume(s.volume()>=1?0:Math.round((s.volume()+.2)*10)/10d)));y+=step;
        button("Result hold: "+s.holdSeconds()+"s","",x,y,w,bh,()->change(s.withHold(s.holdSeconds()>=8?1:s.holdSeconds()+1)));y+=step;
        button("Dice size: "+s.size()+"×","",x,y,w,bh,()->change(s.withSize(s.size()>=1.4?.7:Math.round((s.size()+.1)*10)/10d)));y+=step;
        button("Done","",x,y,w,20,this::close);
    }
    private static DicePresentationSettings.Mode next(DicePresentationSettings.Mode mode){return mode==DicePresentationSettings.Mode.QUICK?DicePresentationSettings.Mode.CINEMATIC:DicePresentationSettings.Mode.QUICK;}
    private void change(DicePresentationSettings next){message=DiceAnimation.settings(next)?"Saved locally":"Could not save local dice settings";init();}
    @Override protected void background(DrawContext g){super.background(g);}
}
