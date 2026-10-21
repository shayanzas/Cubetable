package dev.tacticalcombat.client;

import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.client.SrdBattlefieldModel.*;

/** Deterministic viewport, camera and visibility policies, isolated from Minecraft input and authority. */
public final class SrdBattlefieldPresentation {
    private SrdBattlefieldPresentation(){}
    public record Layout(Rect status,Rect initiative,Rect camera,Rect bar,Rect history) {}
    public static Layout layout(int w,int h){
        int barH=w<700?46:25,barY=h-barH-5;
        int statusW=Math.min(240,w-88),statusH=h<220?34:47;
        return new Layout(new Rect(5,5,statusW,statusH),new Rect(5,statusH+8,w-10,22),
                new Rect(w-78,5,73,22),new Rect(5,barY,w-10,barH),new Rect(5,Math.max(5,barY-Math.min(150,barY-10)-4),Math.min(320,w-10),Math.min(150,barY-10)));
    }
    public static Rect popup(int x,int y,int wantedW,int wantedH,int screenW,int screenH,Rect bar){
        int w=Math.min(wantedW,screenW-10),h=Math.min(wantedH,Math.max(38,bar.y()-10));
        int px=x+14;if(px+w>screenW-5)px=x-w-14;
        int py=y+14;if(py+h>bar.y()-4)py=y-h-14;
        return new Rect(Math.max(5,Math.min(px,screenW-w-5)),Math.max(5,Math.min(py,bar.y()-h-4)),w,h);
    }
    /** Minecraft camera yaw 0 looks +Z: screen-right is -X, not +X. */
    public static double[] pan(double yaw,double forward,double right){double r=Math.toRadians(yaw);return new double[]{-Math.sin(r)*forward-Math.cos(r)*right,Math.cos(r)*forward-Math.sin(r)*right};}
    public static List<JsonObject> initiative(JsonObject state){
        if(!state.has("actors"))return List.of();var actors=state.getAsJsonArray("actors");var result=new ArrayList<JsonObject>();
        // New snapshots publish actual tie-resolved sequence. Legacy SRD snapshots already send rows in order.
        if(state.has("initiativeOrder"))for(var id:state.getAsJsonArray("initiativeOrder")){var a=actor(actors,id.getAsString());if(a!=null&&!result.contains(a))result.add(a);}
        else for(var a:actors)result.add(a.getAsJsonObject());
        return List.copyOf(result);
    }
    public static boolean hideHotbar(boolean srd,boolean tactical,boolean battlefield,boolean world){return srd&&!tactical&&battlefield&&world;}
    public static final class Orbit {
        private int button=-1;private double x,y;private boolean dragged;
        public boolean begin(int b,double x,double y,boolean overUi){if(overUi||b!=1&&b!=2)return false;button=b;this.x=x;this.y=y;dragged=b==2;return true;}
        public boolean drag(int b,double x,double y){if(b!=button)return false;if(Math.hypot(x-this.x,y-this.y)>=5)dragged=true;return dragged;}
        public boolean release(int b){boolean cancel=b==1&&button==1&&!dragged;reset();return cancel;}
        public boolean active(){return button!=-1;}
        public boolean dragging(){return active()&&dragged;}
        public void reset(){button=-1;dragged=false;}
    }
}
