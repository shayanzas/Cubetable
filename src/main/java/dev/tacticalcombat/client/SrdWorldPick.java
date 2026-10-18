package dev.tacticalcombat.client;

import dev.tacticalcombat.rules.srd.SrdMovement;
import java.util.*;

/** Pure intersection with server-provided cell tops; no terrain search or invented reachable cells. */
public final class SrdWorldPick {
    private SrdWorldPick() {}
    public static SrdMovement.Cell cell(Collection<SrdMovement.Cell> cells,double ox,double oy,double oz,
            double dx,double dy,double dz,double obstructionDistance) {
        if(!Double.isFinite(obstructionDistance)||obstructionDistance<0 || dy>=-1e-6)return null;
        SrdMovement.Cell best=null;double nearest=obstructionDistance+.06;
        for(var c:cells){double t=(c.y()+.035-oy)/dy;if(t<0 || t>nearest)continue;
            double x=ox+dx*t,z=oz+dz*t;
            if(x>=c.x() && x<c.x()+1 && z>=c.z() && z<c.z()+1){best=c;nearest=t;}}
        return best;
    }
}
