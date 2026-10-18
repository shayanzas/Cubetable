package dev.tacticalcombat.client;
import java.util.*;
/** Narrow actor-silhouette rays, never a camera-wide cone. Pure candidate geometry, not visibility authority. */
public final class SrdOcclusionMask {
    public record Block(int x,int y,int z) {}
    public static Set<Block> candidates(double cx,double cy,double cz,double tx,double ty,double tz,double feet){
        var result=new LinkedHashSet<Block>();double dx=tx-cx,dy=ty-cy,dz=tz-cz,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
        if(length<2||length>96)return Set.of();int steps=(int)Math.ceil(length/.4);
        // Nine parallel narrow lines cover the torso silhouette without widening near the camera.
        double horizontal=Math.hypot(dx,dz),rx=horizontal==0?1:-dz/horizontal,rz=horizontal==0?0:dx/horizontal;
        for(int i=1;i<steps;i++){double t=(double)i/steps;if(length*(1-t)<.7)continue;
            for(double side:new double[]{-.3,0,.3})for(double height:new double[]{-.35,0,.35}){
                int x=(int)Math.floor(cx+dx*t+rx*side),y=(int)Math.floor(cy+dy*t+height),z=(int)Math.floor(cz+dz*t+rz*side);
                if(y>=Math.floor(feet+.05))result.add(new Block(x,y,z));if(result.size()>=192)return Collections.unmodifiableSet(result);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}
