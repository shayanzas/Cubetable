package dev.tacticalcombat;

import dev.tacticalcombat.rules.srd.SrdMovement;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import java.util.*;

public final class SrdMovementTests {
    private static int count;
    private static void check(boolean value,String why) { count++; if(!value) throw new AssertionError(why); }
    private static void rejects(Runnable task,String why) {
        try { task.run(); } catch(IllegalArgumentException expected) { count++; return; }
        throw new AssertionError(why);
    }
    public static void run() {
        Cell start=new Cell(0,0,1);
        Terrain flat=(from,dx,dz)->new Step(new Cell(from.x()+dx,from.z()+dz,1),5,Reason.OK);
        for(int feet:new int[]{0,4,5,20,25,29,30,40}) {
            var preview=SrdMovement.search(start,feet,flat); int radius=feet/5;
            check(preview.remainingFeet()==feet,"remaining feet retained without rounding budget");
            check(preview.reachable().size()==(2*radius+1)*(2*radius+1),"eight-direction reachable square count");
            check(preview.pathTo(new Cell(radius,radius,1)).size()==radius,"diagonal costs one step, not Euclidean distance");
            check(preview.status(new Cell(radius+1,0,1))==Reason.UNREACHABLE,"outside budget is unreachable");
        }
        var one=SrdMovement.search(start,5,flat);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) if(dx!=0 || dz!=0)
            check(one.reachable().get(new Cell(dx,dz,1)).costFeet()==5,"all eight directions cost five");
        var six=SrdMovement.search(start,30,flat);
        check(six.pathTo(new Cell(6,0,1)).size()==6 && six.reachable().get(new Cell(6,6,1)).costFeet()==30,"30 feet = six moves");
        Terrain difficult=(from,dx,dz)-> {
            Cell next=new Cell(from.x()+dx,from.z()+dz,1);
            return new Step(next,next.equals(new Cell(1,0,1))?10:5,Reason.OK);
        };
        var weighted=SrdMovement.search(start,10,difficult);
        for(int batch:new int[]{1,2,7,8,9,127}) {
            var incremental=new SrdMovement.Search(start,10);int yields=0;
            while(!incremental.advance(difficult,batch,Long.MAX_VALUE)) yields++;
            check(incremental.result().equals(weighted),"yield boundaries preserve weighted Dijkstra results");
            check(yields>0,"bounded search actually yields");
        }
        check(weighted.reachable().get(new Cell(1,0,1)).costFeet()==10,"difficult terrain costs ten feet");
        check(weighted.reachable().get(new Cell(2,0,1)).costFeet()==10,"Dijkstra chooses cheaper diagonal detour");
        check(!weighted.pathTo(new Cell(2,0,1)).contains(new Cell(1,0,1)),"route actually avoids expensive square");
        Terrain blocked=(from,dx,dz)->Step.denied(new Cell(from.x()+dx,from.z()+dz,from.y()),Reason.BLOCKED);
        var closed=SrdMovement.search(start,30,blocked);
        check(closed.reachable().size()==1 && closed.boundaries().size()==8,"blocked edges available for preview");
        check(closed.boundaries().stream().allMatch(b->b.reason()==Reason.BLOCKED),"blocked distinct from budget failure");
        check(SrdMovement.search(start,0,flat).boundaries().stream().allMatch(b->b.reason()==Reason.OUT_OF_BUDGET),"zero speed labels budget rather than solid terrain");
        Terrain vertical=(from,dx,dz)->new Step(new Cell(from.x()+dx,from.z()+dz,from.y()+1),5,Reason.OK);
        check(SrdMovement.search(start,5,vertical).reachable().get(new Cell(1,1,2)).costFeet()==5,"ascent arithmetic normal cost");
        check(SrdMovement.search(start,5,(from,dx,dz)->new Step(new Cell(from.x()+dx,from.z()+dz,from.y()-1),5,Reason.OK))
                .reachable().containsKey(new Cell(1,1,0)),"descent arithmetic normal cost");
        rejects(()->SrdMovement.search(start,-1,flat),"negative allowance");
        rejects(()->SrdMovement.search(start,301,flat),"bounded search allowance");
        rejects(()->new Cell(0,0,Double.NaN),"invalid height");
        rejects(()->new Cell(Integer.MIN_VALUE,0,1),"coordinate overflow");
        rejects(()->new Step(start,6,Reason.OK),"unsupported fractional grid charge");
        rejects(()->SrdMovement.search(start,5,(from,dx,dz)->new Step(new Cell(from.x()+2,from.z(),1),5,Reason.OK)),"terrain cannot return a client teleport");
        var bounded=SrdMovement.search(start,300,flat);
        check(bounded.truncated() && bounded.reachable().size()==SrdMovement.MAX_NODES,"search cap explicit");
        check(bounded.status(new Cell(100,100,1))==Reason.SEARCH_LIMIT,"truncation not falsely reported as blocked geometry");
        var again=SrdMovement.search(start,30,flat);
        check(again.pathTo(new Cell(4,3,1)).equals(six.pathTo(new Cell(4,3,1))),"deterministic ties and route");
        check(six.reachable().size()==169,"independent searches do not share mutable state");
        var ui=new dev.tacticalcombat.client.SrdMovementPreview();
        check(ui.select(start).status()==Reason.INVALID_START,"no stale preview after construction");
        rejects(()->ui.apply("minecraft_tactical",six),"Tactical data cannot enter SRD presentation");
        rejects(()->ui.apply("manual_tabletop",six),"manual data cannot enter SRD presentation");
        ui.apply("srd_5_1",six);
        var selected=ui.select(new Cell(4,4,1));
        check(selected.costFeet().orElseThrow()==20 && selected.remainingFeet()==10 && selected.path().size()==4,"preview reports feet, route and remaining allowance");
        check(ui.select(new Cell(7,0,1)).boundaryReasons().contains(Reason.OUT_OF_BUDGET),"unreachable preview distinguishes budget");
        ui.apply("srd_5_1",closed);
        check(ui.select(new Cell(1,0,1)).boundaryReasons().contains(Reason.BLOCKED),"preview distinguishes blocked approaches");
        ui.clear(); check(ui.select(start).costFeet().isEmpty(),"clear cannot retain a previous encounter cost");
        var neighbor=new Cell(1,0,1);
        rejects(()->new Preview(start,30,Map.of(start,new Node(start,0,null),neighbor,new Node(neighbor,5,neighbor)),List.of(),false),"cyclic preview cannot hang path rendering");
        rejects(()->new Preview(start,30,Map.of(),List.of(),false),"preview requires origin");
        rejects(()->new Preview(start,0,Map.of(start,new Node(start,0,null),neighbor,new Node(neighbor,5,start)),List.of(),false),"preview cost cannot exceed budget");
        System.out.println("SRD movement checks passed: "+count);
    }
}
