package dev.tacticalcombat.client;

import java.util.ArrayDeque;

/** Presentation of already committed adjacent steps; never predicts or changes an entity position. */
public final class SrdMotionTrack {
    public record Point(double x,double y,double z) {
        public Point {if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z))throw new IllegalArgumentException("Invalid point");}
        Point lerp(Point b,double t){return new Point(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t);}
    }
    public record Pose(Point point,float yaw,float phase,float strength) {}
    private record Segment(Point from,Point to,double begins,double duration,float yaw,float fromYaw,float phase) {}
    private final ArrayDeque<Segment> queue=new ArrayDeque<>();
    private Point endpoint;
    private long sequence;
    private float yaw,phase;
    public SrdMotionTrack(Point point,long sequence,float yaw){endpoint=point;this.sequence=sequence;this.yaw=yaw;}
    public int queued(){return queue.size();}
    public boolean accept(long id,Point from,Point to,int ticks,double now){
        if(id<=sequence)return false;
        boolean adjacent=Math.abs(to.x-from.x)<=1.00001&&Math.abs(to.z-from.z)<=1.00001&&Math.abs(to.y-from.y)<=1.00001&&(to.x!=from.x||to.z!=from.z);
        sample(now);
        if(id!=sequence+1||!endpoint.equals(from)||!adjacent||ticks<1||ticks>6||queue.size()>=8){queue.clear();endpoint=to;sequence=id;return false;}
        float facing=(float)Math.toDegrees(Math.atan2(to.z-from.z,to.x-from.x))-90;
        var last=queue.peekLast();double begins=last==null?now:last.begins+last.duration;
        float previous=last==null?yaw:last.yaw;float startPhase=last==null?phase:last.phase+4;
        queue.add(new Segment(from,to,begins,ticks*50.0,previous+wrap(facing-previous),previous,startPhase));
        endpoint=to;sequence=id;return true;
    }
    private static float wrap(float angle){return (angle%360+540)%360-180;}
    public Pose sample(double now){
        while(!queue.isEmpty()&&now>=queue.peek().begins+queue.peek().duration){var s=queue.remove();yaw=s.yaw;phase=s.phase+4;}
        var s=queue.peek();if(s==null)return new Pose(endpoint,yaw,phase,0);
        double t=Math.max(0,Math.min(1,(now-s.begins)/s.duration));
        // The server sweep lifts first, moves across at the higher surface, then descends.
        // Flat steps stay exactly flat; no sinusoidal jump/bob is introduced.
        Point p;
        if(s.to.y>s.from.y){p=t<.25?s.from.lerp(new Point(s.from.x,s.to.y,s.from.z),t*4):new Point(s.from.x,s.to.y,s.from.z).lerp(s.to,(t-.25)/.75);}
        else if(s.to.y<s.from.y){p=t<.75?s.from.lerp(new Point(s.to.x,s.from.y,s.to.z),t/.75):new Point(s.to.x,s.from.y,s.to.z).lerp(s.to,(t-.75)*4);}
        else p=s.from.lerp(s.to,t);
        float strength=(float)Math.min(1,100/s.duration);
        return new Pose(p,(float)(s.fromYaw+(s.yaw-s.fromYaw)*Math.min(1,t*2)),s.phase+(float)t*4,strength);
    }
}
