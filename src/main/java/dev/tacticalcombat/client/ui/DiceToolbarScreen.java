package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.dice.*;
import dev.tacticalcombat.net.DiceRequestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Presentation of the existing server DiceService; manual modifiers carry no character authority. */
public final class DiceToolbarScreen extends RpgScreen {
    private static final List<DiceType> DICE=List.of(DiceType.D4,DiceType.D6,DiceType.D8,DiceType.D10,DiceType.D12,DiceType.D20,DiceType.D100);
    private static DiceType die=DiceType.D20;private static int count=1,mode;private static String manualModifier="0";
    private final boolean larger;private TextFieldWidget modifier;private RpgButton roll;private long cooldown;
    public DiceToolbarScreen(Screen parent,boolean larger){super("Manual Dice",parent);this.larger=larger;}
    @Override protected void init(){super.init();frame=DiceToolbar.bounds(width,height);if(larger){int h=Math.min(180,height-16);frame=new UiLayout.Rect(frame.x(),Math.max(8,(height-h)/2),frame.width(),h);}clearChildren();int x=frame.x()+6,y=frame.y()+22,w=frame.width()-12,cw=w/7;
        for(int i=0;i<DICE.size();i++){var type=DICE.get(i);button(type.label,"",x+i*cw,y,cw-2,20,()->{die=type;if(type!=DiceType.D20)mode=0;init();}).selected(die==type);}y+=24;
        button("× "+count,"",x,y,38,20,()->{count=count%10+1;if(count!=1)mode=0;init();}).hint("Quantity: 1–10");
        modifier=addDrawableChild(new TextFieldWidget(textRenderer,x+43,y,38,20,Text.literal("Manual modifier")));modifier.setMaxLength(4);modifier.setText(manualModifier);modifier.setChangedListener(v->manualModifier=v);modifier.setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Manual modifier only; never an authenticated ability/check bonus")));
        button(List.of("Normal","Advantage","Disadvantage").get(mode),"",x+86,y,DiceToolbarLayout.modeWidth(w),20,()->{mode=(mode+1)%3;init();}).active=die==DiceType.D20&&count==1;
        roll=button("Roll","",x+w-56,y,56,20,this::roll).primary(true);y+=24;
        button(larger?"Compact":"Roll Window","",x,y,Math.max(66,(w-8)/3),18,()->client.setScreen(new DiceToolbarScreen(parent,!larger)));
        button(DiceToolbar.pinned()?"Unpin":"Pin","",x+(w-8)/3+4,y,Math.max(50,(w-8)/3),18,()->{if(!DiceToolbar.pin(!DiceToolbar.pinned()))message="Could not save pin preference";init();});
        button("Close","",x+2*((w-8)/3+4),y,Math.max(50,(w-8)/3),18,this::close);
    }
    private void roll(){if(client.world==null||client.world.getTime()<cooldown)return;int n;try{n=Integer.parseInt(manualModifier);}catch(NumberFormatException invalid){message="Enter a manual modifier from -999 to 999";return;}if(n< -999||n>999){message="Manual modifier must be -999 to 999";return;}if(!ClientPlayNetworking.canSend(DiceRequestPayload.ID)){message="Server dice unavailable";return;}ClientPlayNetworking.send(new DiceRequestPayload("Manual dice",die,count,n,mode));cooldown=client.world.getTime()+20;message="Requested server-generated manual roll";}
    @Override protected void background(DrawContext g){RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),0xEF191D23,false);RpgTheme.text(g,textRenderer,"MANUAL DICE · client-entered modifier · nearby audience",frame.x()+6,frame.y()+7,frame.width()-12,RpgTheme.GOLD);if(larger){RpgTheme.text(g,textRenderer,DiceToolbar.result(),frame.x()+6,frame.y()+105,frame.width()-12,RpgTheme.TEXT);RpgTheme.text(g,textRenderer,"Character checks and private roll requests arrive in J2.5D.",frame.x()+6,frame.y()+122,frame.width()-12,RpgTheme.MUTED);}}
    @Override protected String statusText(){return message.isEmpty()?DiceToolbar.result():message+" · "+DiceToolbar.result();}
    @Override public boolean keyPressed(int key,int scan,int mods){if(getFocused()!=modifier&&dev.tacticalcombat.client.TacticalCombatClient.DICE_TOOLBAR_KEY.matchesKey(key,scan)){close();return true;}return super.keyPressed(key,scan,mods);}
    @Override public void tick(){super.tick();if(roll!=null)roll.active=client.world!=null&&client.world.getTime()>=cooldown;}
}
