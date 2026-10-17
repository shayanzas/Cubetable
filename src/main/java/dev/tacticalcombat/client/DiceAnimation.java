package dev.tacticalcombat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.tacticalcombat.client.dicevisual.*;
import dev.tacticalcombat.net.DiceRollPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import java.util.*;

/** Client-only projection of authorized results. The face and total are fixed before the first frame. */
public final class DiceAnimation {
    private static final PresentationQueue QUEUE=new PresentationQueue(12,4096);
    private static PresentationRoll current;
    private static DicePresentationSettings settings;
    private static int ticks;
    private static long manualSequence;
    private static boolean clattered,landed,bounced,settled;
    private static final SoundEvent MOVE=sound("dice_move"),CLATTER=sound("dice_clatter"),IMPACT=sound("dice_impact"),BOUNCE=sound("dice_bounce"),SETTLE=sound("dice_settle");
    private DiceAnimation(){}
    private static SoundEvent sound(String name){return SoundEvent.of(Identifier.of("tacticalcombat",name));}
    public static DicePresentationSettings settings(){if(settings==null){try{settings=DicePresentationSettings.load();}catch(Exception ignored){settings=DicePresentationSettings.defaults();}}return settings;}
    public static boolean settings(DicePresentationSettings updated){try{updated.save();settings=updated;return true;}catch(Exception failure){return false;}}
    public static void enqueue(DiceRollPayload payload){enqueue(PresentationRoll.manual(payload,++manualSequence));}
    public static void enqueueCheck(com.google.gson.JsonObject authorizedResult){if(authorizedResult!=null)enqueue(PresentationRoll.check(authorizedResult));}
    public static void enqueueEvent(dev.tacticalcombat.client.ui.HudSession.Line line){if(line.roll()!=null)enqueue(PresentationRoll.event(line.id(),line.kind(),line.summary(),line.roll()));}
    public static void enqueue(PresentationRoll roll){if(roll==null)return;
        if(QUEUE.seen(roll.identity())&&current!=null&&current.identity().equals(roll.identity())&&roll.official()&&!roll.source().equals("CHARACTER_CHECK"))current=roll;
        var omitted=QUEUE.offer(roll);if(omitted!=null)accessible(MinecraftClient.getInstance(),omitted,true);
    }
    public static void clear(){QUEUE.clear();current=null;ticks=0;}
    public static void invalidatePrivate(){QUEUE.invalidatePrivate();if(current!=null&&current.official()){current=null;ticks=0;}}
    public static void skip(){if(current!=null)ticks=Math.max(ticks,(int)Math.ceil((tumble(current)+.20)*20/settings().speed()));}
    private static double tumble(PresentationRoll roll){return (roll.official()?settings().checkMode():settings().manualMode())==DicePresentationSettings.Mode.QUICK?.48:1.15;}
    private static double lifetime(PresentationRoll roll){return (settings().reducedMotion()?0:tumble(roll)+.20)+settings().holdSeconds()+.25;}
    public static void tick(MinecraftClient mc){if(mc.player==null){clear();return;}if(current==null){current=QUEUE.poll();ticks=0;clattered=landed=bounced=settled=false;if(current!=null&&!settings().reducedMotion())play(mc,MOVE);}
        if(current==null)return;ticks++;double seconds=ticks/20d*settings().speed(),tumble=tumble(current);
        if(!settings().reducedMotion()){
            if(!clattered&&seconds>=.13){play(mc,CLATTER);clattered=true;}
            if(!landed&&seconds>=tumble){play(mc,IMPACT);landed=true;}
            if(!bounced&&seconds>=tumble+.09){play(mc,BOUNCE);bounced=true;}
            if(!settled&&seconds>=tumble+.20){play(mc,SETTLE);settled=true;}
        }
        if(seconds>=lifetime(current)){accessible(mc,current,false);current=null;}
    }
    private static void play(MinecraftClient mc,SoundEvent event){if(settings().sound()&&settings().volume()>0)mc.getSoundManager().play(PositionedSoundInstance.master(event,1f,(float)settings().volume()));}
    private static void accessible(MinecraftClient mc,PresentationRoll roll,boolean forced){if(mc.player!=null&&(forced||dev.tacticalcombat.client.ui.CubetableHud.options().diceChat()))mc.inGameHud.getChatHud().addMessage(Text.literal(roll.title()+" · "+roll.calculation()));}
    public static void render(DrawContext g,RenderTickCounter counter){if(MinecraftClient.getInstance().currentScreen!=null)return;draw(g,counter.getTickDelta(false));}
    public static void drawOverScreen(DrawContext g,float delta){var screen=MinecraftClient.getInstance().currentScreen;if(screen instanceof dev.tacticalcombat.client.ui.RpgScreen||screen instanceof TacticalScreen)draw(g,delta);}
    private static void draw(DrawContext g,float delta){var roll=current;if(roll==null)return;var mc=MinecraftClient.getInstance();int w=g.getScaledWindowWidth(),h=g.getScaledWindowHeight();
        boolean quick=(roll.official()?settings().checkMode():settings().manualMode())==DicePresentationSettings.Mode.QUICK;
        int count=Math.min(roll.faces().size()*(roll.sides()==100?2:1),6),perRow=Math.min(count,3),rows=(count+perRow-1)/perRow;
        int die=(int)Math.clamp((quick?31:44)*settings().size(),22,58),spacing=die*2+8;
        int pw=Math.min(w-8,Math.max(quick?170:230,perRow*spacing+24)),ph=Math.min(h-8,54+rows*spacing+38+(roll.official()?20:0));
        if(pw<100||ph<85)return;
        int x=quick?Math.max(4,w-pw-6):(w-pw)/2,y=quick?Math.max(4,h-ph-42):Math.max(4,(h-ph)/3);
        double elapsed=(ticks+delta)/20d*settings().speed(),tumble=tumble(roll);
        var pose=RollMotion.at(elapsed,tumble,settings().holdSeconds(),settings().reducedMotion(),0);
        int alpha=(int)(225*pose.opacity());if(alpha<=0)return;
        g.fill(x,y,x+pw,y+ph,(alpha<<24)|0x14191E);
        g.fill(x,y,x+pw,y+1,0xFFD4AD60);g.fill(x,y+ph-1,x+pw,y+ph,0xFF775C36);
        var tr=mc.textRenderer;
        g.drawTextWithShadow(tr,tr.trimToWidth(roll.title(),pw-14),x+7,y+6,0xFFECD6A4);
        g.drawText(tr,tr.trimToWidth(roll.source(),pw-14),x+7,y+18,0xFFB4ADA0,false);
        for(int slot=0;slot<count;slot++){
            int index=roll.sides()==100?slot/2:slot,value=roll.faces().get(index);
            int shown=roll.sides()==100?(slot%2==0?(value==100?10:(value/10)%10):(value==100?10:value%10)):value;
            double cx=x+pw/2d+(slot%perRow-(perRow-1)/2d)*spacing,cy=y+39+(slot/perRow)*spacing+die;
            var p=RollMotion.at(elapsed,tumble,settings().holdSeconds(),settings().reducedMotion(),slot);
            renderDie(g,cx+p.x(),cy+p.y(),die,roll.sides()==100?10:roll.sides(),shown,p,roll.retained(index),slot,roll.sides()==100?slot%2==0?"TENS":"ONES":null);
        }
        if(pose.phase()==RollMotion.Phase.REVEAL||pose.phase()==RollMotion.Phase.DISMISS||settings().reducedMotion()){
            int yy=y+ph-(roll.official()?38:27);
            g.drawTextWithShadow(tr,tr.trimToWidth(roll.calculation(),pw-14),x+7,yy,0xFFFFD875);
            if(roll.official())g.drawText(tr,tr.trimToWidth(roll.breakdown(),pw-14),x+7,yy+11,0xFFE2D7BC,false);
            if(roll.dc()!=null&&roll.success()!=null)g.drawTextWithShadow(tr,"DC "+roll.dc()+" · "+(roll.success()?"SUCCESS":"FAILURE"),x+7,y+ph-14,roll.success()?0xFF91D9A4:0xFFFFAB9A);
        }
    }
    private record FaceDraw(int index,double depth,double[][] points,int color,double cx,double cy){}
    private static void renderDie(DrawContext g,double cx,double cy,int radius,int sides,int face,RollMotion.Pose motion,boolean kept,int slot,String percentile){
        var mesh=PolyhedralMesh.forSides(sides);int target=(face-1)%mesh.faceCount();var normal=mesh.normal(target);
        // Rotate the selected face normal to the viewer, then add damped three-axis tumbling.
        double[][] align=DieOrientation.align(normal),spin=DieOrientation.rotation(motion.rx(),motion.ry(),motion.rz());
        double[][] projected=new double[mesh.vertices().size()][3];
        for(int i=0;i<projected.length;i++){var v=mesh.vertices().get(i);double[] a=DieOrientation.mul(spin,DieOrientation.mul(align,new double[]{v.x(),v.y(),v.z()}));double scale=2.8/(2.8-a[2]);projected[i]=new double[]{cx+a[0]*radius*scale,cy-motion.lift()-a[1]*radius*scale,a[2]};}
        int shadow=(int)(Math.clamp(motion.shadow(),0,1)*75);g.fill((int)(cx-radius*.8),(int)(cy+radius*.75),(int)(cx+radius*.8),(int)(cy+radius*.75+2),(shadow<<24)|0x030304);
        var visible=new ArrayList<FaceDraw>();var faces=mesh.faces();
        for(int fi=0;fi<faces.size();fi++){int[] f=faces.get(fi);var n=DieOrientation.mul(spin,DieOrientation.mul(align,DieOrientation.vector(mesh.normal(fi))));if(n[2]<=.015)continue;double[][] pts=new double[f.length][2];double dep=0,fx=0,fy=0;for(int j=0;j<f.length;j++){var pt=projected[f[j]];pts[j][0]=pt[0];pts[j][1]=pt[1];dep+=pt[2];fx+=pt[0];fy+=pt[1];}dep/=f.length;double shade=.47+.38*Math.max(0,n[0]*-.35+n[1]*.55+n[2]*.65);int base=(int)(28*shade),gold=(int)(19*shade);int color=((int)(255*motion.opacity())<<24)|((base+gold)<<16)|((base+gold/2)<<8)|base;visible.add(new FaceDraw(fi,dep,pts,color,fx/f.length,fy/f.length));}
        visible.sort(Comparator.comparingDouble(FaceDraw::depth));
        g.draw();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f matrix=g.getMatrices().peek().getPositionMatrix();
        for(var f:visible)polygon(matrix,f.points(),f.color());
        var edges=Tessellator.getInstance().begin(VertexFormat.DrawMode.LINES,VertexFormats.POSITION_COLOR);
        int ec=kept?0xFFAD8847:0xFF756A5C;float er=((ec>>>16)&255)/255f,eg=((ec>>>8)&255)/255f,eb=(ec&255)/255f;
        for(var f:visible){double[][] p=f.points();for(int i=0;i<p.length;i++){double[] a=p[i],b=p[(i+1)%p.length];edges.vertex(matrix,(float)a[0],(float)a[1],.01f).color(er,eg,eb,1f);edges.vertex(matrix,(float)b[0],(float)b[1],.01f).color(er,eg,eb,1f);}}
        BufferRenderer.drawWithGlobalProgram(edges.end());
        RenderSystem.enableCull();RenderSystem.disableBlend();g.draw();
        boolean revealed=motion.phase()==RollMotion.Phase.REVEAL||motion.phase()==RollMotion.Phase.DISMISS;
        var front=revealed?visible.stream().filter(f->f.index()==target).findFirst():visible.stream().max(Comparator.comparingDouble(FaceDraw::depth));
        if(front.isPresent()){var f=front.get();int numbered=f.index()+1;String label=percentile==null?Integer.toString(numbered):percentile.equals("TENS")?(numbered==10?"00":Integer.toString(numbered*10)):Integer.toString(numbered==10?0:numbered);
            g.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer,label,(int)f.cx(),(int)f.cy()-4,revealed?(kept?0xFFFFD779:0xFFAAA49A):0xFFC9C0A9);}
        if(!kept)g.drawText(MinecraftClient.getInstance().textRenderer,"DISCARDED",(int)(cx-radius),(int)(cy+radius+2),0xFF8C8883,false);
        else if(visible.size()>0&&motion.phase()==RollMotion.Phase.REVEAL&&slot<2)g.drawText(MinecraftClient.getInstance().textRenderer,"KEPT",(int)(cx-radius/2),(int)(cy+radius+2),0xFFE6C273,false);
    }
    private static void polygon(Matrix4f m,double[][] p,int c){float a=((c>>>24)&255)/255f,r=((c>>>16)&255)/255f,g=((c>>>8)&255)/255f,b=(c&255)/255f;var buf=Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);for(var q:p)buf.vertex(m,(float)q[0],(float)q[1],0).color(r,g,b,a);buf.vertex(m,(float)p[0][0],(float)p[0][1],0).color(r,g,b,a);BufferRenderer.drawWithGlobalProgram(buf.end());}
}
