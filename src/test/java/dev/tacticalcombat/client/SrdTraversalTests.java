package dev.tacticalcombat.client;

import dev.tacticalcombat.rules.srd.SrdMovement;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import java.util.*;

public final class SrdTraversalTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    public static void run(){
        var start=new Cell(0,0,1);
        Terrain flat=(from,dx,dz)->new Step(new Cell(from.x()+dx,from.z()+dz,1),5,Reason.OK);
        var grid=SrdMovement.search(start,40,flat);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0){
            var end=new Cell(dx*6,dz*6,1);var path=grid.pathTo(end);
            check(path.size()==6,"shortest SRD distance unchanged");
            for(int i=0;i<6;i++)check(path.get(i).equals(new Cell(dx*(i+1),dz*(i+1),1)),"cardinal and diagonal routes do not zigzag");
        }
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)if(x!=0||z!=0){
            var path=grid.pathTo(new Cell(x,z,1));int turns=0,lastDx=0,lastDz=0;var before=start;
            for(var c:path){int dx=c.x()-before.x(),dz=c.z()-before.z();if(before!=start&&(lastDx!=dx||lastDz!=dz))turns++;lastDx=dx;lastDz=dz;before=c;}
            check(turns<=1,"clear mixed route has no unnecessary turning");
            check(grid.reachable().get(new Cell(x,z,1)).costFeet()==Math.max(Math.abs(x),Math.abs(z))*5,"cost is still five-foot Chebyshev grid");
        }
        var incremental=new SrdMovement.Search(start,40);while(!incremental.advance(flat,3,Long.MAX_VALUE)){}
        check(incremental.result().reachable().equals(grid.reachable()),"yielding cannot change deterministic parent selection");
        Set<Cell> walls=Set.of(new Cell(1,0,1),new Cell(1,1,1),new Cell(1,-1,1));
        Terrain obstacle=(from,dx,dz)->{
            var to=new Cell(from.x()+dx,from.z()+dz,1);
            if(walls.contains(to))return Step.denied(to,Reason.CLEARANCE);
            if(dx!=0&&dz!=0&&(walls.contains(new Cell(from.x()+dx,from.z(),1))||walls.contains(new Cell(from.x(),from.z()+dz,1))))return Step.denied(to,Reason.CORNER);
            return new Step(to,5,Reason.OK);
        };
        var detour=SrdMovement.search(start,40,obstacle);var path=detour.pathTo(new Cell(3,0,1));check(!path.isEmpty(),"legal obstacle detour available");
        var before=start;for(var c:path){check(obstacle.step(before,c.x()-before.x(),c.z()-before.z()).reason()==Reason.OK,"detour obeys collision and corners");before=c;}
        check(detour.reachable().equals(SrdMovement.search(start,40,obstacle).reachable()),"detours deterministic");
        // Picking uses exactly the cell rectangles drawn, including negative coordinates and elevated grass.
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(double y:new double[]{0,1,.875}){
            var cell=new Cell(x,z,y);check(cell.equals(SrdWorldPick.cell(List.of(cell),x+.5,y+8,z+.5,0,-1,0,8)),"cell center maps to intended highlight");
            check(SrdWorldPick.cell(List.of(cell),x+.5,y+8,z+.5,0,-1,0,2)==null,"wall occlusion cannot be bypassed by highlight");
        }
        var p0=new SrdMotionTrack.Point(0,1,0);var p1=new SrdMotionTrack.Point(1,1,1);var p2=new SrdMotionTrack.Point(2,1,1);
        for(int pace:new int[]{1,2,4,6}){
            var motion=new SrdMotionTrack(p0,0,0);double duration=pace*50;
            check(motion.accept(1,p0,p1,pace,0),"confirmed adjacent diagonal accepted");
            for(int i=0;i<=10;i++){var p=motion.sample(duration*i/10);check(Math.abs(p.point().x()-i/10d)<1e-8&&p.point().y()==1&&p.point().z()==p.point().x(),"linear diagonal has no flat-ground jump");}
            check(motion.sample(duration).strength()==0&&motion.queued()==0,"limbs stop at final committed point");
            check(!motion.accept(1,p0,p1,pace,duration),"refresh does not replay step");
            check(motion.accept(2,p1,p2,pace,duration),"next split movement accepted");
            check(motion.sample(duration*1.5).point().z()==1,"direction change follows confirmed segment, no corner cutting");
            check(motion.sample(duration*1.5).strength()>0,"players and NPCs use same walking model inputs");
            check(motion.sample(duration*2).point().equals(p2),"final position reconciles exactly");
        }
        var queued=new SrdMotionTrack(p0,0,0);queued.accept(1,p0,p1,4,0);queued.accept(2,p1,p2,4,50);
        check(queued.sample(200).point().equals(p1),"early next step never cuts across a turn");
        check(queued.sample(400).point().equals(p2),"queued segments finish in sequence");
        check(!queued.accept(4,p2,new SrdMotionTrack.Point(4,1,1),4,410)&&queued.queued()==0,"missing steps snap safely, do not invent traversal");
        var high=new SrdMotionTrack.Point(1,2,0);var up=new SrdMotionTrack(p0,0,0);up.accept(1,p0,high,4,0);
        check(up.sample(50).point().equals(new SrdMotionTrack.Point(0,2,0)),"ascent lifts before horizontal passage as server sweep requires");
        check(up.sample(200).point().equals(high),"one-block landing exact");
        var down=new SrdMotionTrack(high,0,0);down.accept(1,high,p0,4,0);
        check(down.sample(150).point().equals(new SrdMotionTrack.Point(0,2,0)),"descent crosses before lowering, following safe server sweep");
        check(down.sample(200).point().equals(p0),"descent lands without an invented second step");
        var overflow=new SrdMotionTrack(p0,0,0);var from=p0;
        for(int i=1;i<=9;i++){var to=new SrdMotionTrack.Point(i,1,0);overflow.accept(i,from,to,6,0);from=to;check(overflow.queued()<=8,"visual backlog bounded");}
        check(overflow.queued()==0&&overflow.sample(0).point().equals(from),"overflow reconciles to accepted endpoint without guessing missing path");
        var invalid=new SrdMotionTrack(p0,0,0);check(!invalid.accept(1,p0,new SrdMotionTrack.Point(0,3,0),4,0),"two-block traversal never interpolated as walking");
        var noReplay=new SrdMotionTrack(p2,19,0);check(!noReplay.accept(19,p1,p2,4,0)&&noReplay.queued()==0,"joining historical snapshot never replays accepted route");
        var ui=new SrdMovementPreview();ui.apply("srd_5_1",grid);ui.context(8,"Idle",false);
        var details=ui.details(new Cell(2,0,1));check(!details.stale()&&details.revision()==8&&details.selection().costFeet().getAsInt()==10,"read-only details carry authoritative cost and revision");
        ui.clear();ui.context(9,"Route cancelled",true);check(ui.details(new Cell(2,0,1)).stale()&&ui.details(new Cell(2,0,1)).selection().costFeet().isEmpty(),"invalidation exposes stale status without old route authority");
        System.out.println("SRD traversal presentation checks passed: "+count);
    }
}
