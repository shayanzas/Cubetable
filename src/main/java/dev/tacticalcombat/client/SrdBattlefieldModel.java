package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.SrdMovement;
import java.util.*;

/** Local UI only: no dice, rules, packets or character mutation. */
public final class SrdBattlefieldModel {
    public enum Tool { MOVE, ATTACK, INSPECT }
    public Tool tool=Tool.MOVE;
    public String target="";
    private String scope="";
    public void reconcile(String encounter,long turn,boolean controlled,JsonArray actors){
        String next=encounter+":"+turn+":"+controlled;
        if(!scope.equals(next)){scope=next;target="";tool=controlled?Tool.MOVE:Tool.INSPECT;}
        if(!target.isEmpty() && actor(actors,target)==null)target="";
    }
    public void cancel(){target="";tool=Tool.INSPECT;}
    public static JsonObject actor(JsonArray actors,String id){for(var a:actors)if(a.getAsJsonObject().get("id").getAsString().equals(id))return a.getAsJsonObject();return null;}
    public static JsonObject next(JsonObject state){
        if(!state.has("actors")||!state.has("active"))return null;
        var actors=state.getAsJsonArray("actors");
        for(int i=0;i<actors.size();i++)if(actors.get(i).getAsJsonObject().get("id").equals(state.get("active")))return actors.size()>1?actors.get((i+1)%actors.size()).getAsJsonObject():null;
        return null;
    }
    public static SrdMovement.Cell retain(SrdMovement.Cell selected,SrdMovement.Preview grid){return selected!=null&&grid!=null&&grid.reachable().containsKey(selected)?selected:null;}
    public static String hp(JsonObject actor){return actor!=null&&actor.has("hp")?"HP "+actor.get("hp")+" / "+actor.get("maximumHp")+"  +"+actor.get("temporaryHp")+" temp":"HP private";}
    public record Rect(int x,int y,int w,int h){public boolean contains(double x,double y){return x>=this.x&&x<this.x+w&&y>=this.y&&y<this.y+h;}}
    public record Layout(Rect status,Rect camera,Rect bar,Rect context,Rect toast,boolean narrow) {}
    public static Layout layout(int width,int height){
        int margin=6,barH=width<560?64:46,top=Math.min(76,height-barH-12);
        boolean narrow=width<560;
        int side=Math.min(258,width-12),contextY=height<240?6:top+4,contextH=Math.min(190,height-contextY-barH-20);
        return new Layout(new Rect(margin,margin,Math.min(width-94,narrow?270:330),top-6),
            new Rect(width-82,margin,76,top-6),new Rect(margin,height-barH-margin,width-12,barH),
            new Rect(width-side-margin,contextY,side,contextH),
            new Rect(margin,height<240?Math.max(6,height-barH-60):Math.max(top+4,height-barH-64),Math.min(width-12,narrow?width-12:340),48),narrow);
    }
}
