package dev.tacticalcombat.client.ui;

import com.google.gson.JsonParser;
import dev.tacticalcombat.client.dicevisual.*;
import dev.tacticalcombat.dice.DiceType;
import dev.tacticalcombat.net.DiceRollPayload;
import java.util.*;

/** Deterministic presentation contracts; no graphics context or client dice generation. */
public final class CinematicDiceTests {
    private static int assertions;
    private static void check(boolean ok,String why){assertions++;if(!ok)throw new AssertionError(why);}
    public static void run(){
        for(int sides:new int[]{4,6,8,10,12,20}){
            var mesh=PolyhedralMesh.forSides(sides);check(mesh.faceCount()==sides,"face count d"+sides);
            check(mesh.vertices().size()-mesh.edgeCount()+mesh.faceCount()==2,"Euler topology d"+sides);
            var edges=new HashMap<String,Integer>();
            for(int[] face:mesh.faces()){check(face.length>=3,"polygon");var origin=mesh.vertices().get(face[0]);var normal=mesh.vertices().get(face[1]).sub(origin).cross(mesh.vertices().get(face[2]).sub(origin)).unit();for(int vertex:face)check(Math.abs(normal.dot(mesh.vertices().get(vertex).sub(origin)))<1e-6,"planar face");for(int i=0;i<face.length;i++){int a=face[i],b=face[(i+1)%face.length];check(a!=b&&a>=0&&b>=0&&a<mesh.vertices().size()&&b<mesh.vertices().size(),"valid edge");edges.merge(Math.min(a,b)+":"+Math.max(a,b),1,Integer::sum);}}
            for(int count:edges.values())check(count==2,"closed manifold");
            for(int i=0;i<sides;i++){check(mesh.normal(i).dot(mesh.center(i))>0,"outward face");var facing=DieOrientation.mul(DieOrientation.align(mesh.normal(i)),DieOrientation.vector(mesh.normal(i)));check(Math.abs(facing[0])<1e-9&&Math.abs(facing[1])<1e-9&&Math.abs(facing[2]-1)<1e-9,"selected face points to viewer");}
        }
        for(var type:DiceType.values()){
            int face=type.sides;var roll=PresentationRoll.manual(new DiceRollPayload("Player","Manual",type,2,0,new int[]{face}),1);
            check(roll.faces().getFirst()==face&&roll.total()==face+2,"authoritative manual face d"+face);
            check(roll.kept().equals(List.of(0)),"normal manual kept");
        }
        var adv=PresentationRoll.manual(new DiceRollPayload("P","Check",DiceType.D20,-1,1,new int[]{4,17}),2);
        var dis=PresentationRoll.manual(new DiceRollPayload("P","Check",DiceType.D20,-1,2,new int[]{4,17}),3);
        check(adv.kept().equals(List.of(1))&&adv.total()==16,"advantage retained");
        check(dis.kept().equals(List.of(0))&&dis.total()==3,"disadvantage retained");
        var multi=PresentationRoll.manual(new DiceRollPayload("P","Manual",DiceType.D6,0,0,new int[]{1,2,3}),4);
        check(multi.kept().equals(List.of(0,1,2))&&multi.total()==6,"multi-die not advantage");
        var official=PresentationRoll.check(JsonParser.parseString("""
            {"version":1,"operation":"00000000-0000-0000-0000-000000000001","check":{"category":"SAVE","id":"dex"},"purpose":"Avoid the trap","source":"DM_REQUEST","mode":"ADVANTAGE","sides":20,"faces":[5,17],"kept":[1],"modifier":{"ability":"dex","abilityModifier":-1,"proficiencyBonus":2,"multiplier":0,"total":-1},"total":16,"dc":15,"success":true}
            """).getAsJsonObject());
        check(official.official()&&official.faces().equals(List.of(5,17))&&official.kept().equals(List.of(1)),"D1 faces and kept copied");
        check(official.total()==16&&official.dc()==15&&official.success(),"D1 total and authorized DC copied");
        check(PresentationRoll.check(null)==null,"restricted ACK has no result");
        var event=PresentationRoll.event(UUID.fromString(official.identity()),"CHARACTER_CHECK","Summary",new dev.tacticalcombat.uiapi.UiEvents.Roll(20,List.of(5,17),List.of(1),-1,16,"Avoid the trap",false));
        check(event.identity().equals(official.identity())&&event.dc()==null,"permission-filtered event deduplicates without inventing DC");
        var queue=new PresentationQueue(2,4);check(queue.offer(official)==null&&queue.size()==1,"first authorized result queued");
        check(queue.offer(event)==null&&queue.offer(official)==null&&queue.size()==1,"same operation does not replay");
        queue.offer(multi);var overflow=queue.offer(adv);check(overflow!=null&&overflow.identity().equals(official.identity()),"overflow exposes fallback result");
        check(queue.size()==2&&queue.poll().identity().equals(multi.identity()),"bounded visual queue preserves order");
        queue.invalidatePrivate();check(queue.size()==1,"private pending result invalidated");
        var replayGuard=new PresentationQueue(1,2);replayGuard.offer(official);for(int i=0;i<20;i++)replayGuard.offer(PresentationRoll.manual(new DiceRollPayload("P","M",DiceType.D6,0,0,new int[]{2}),i+10));check(replayGuard.seen(official.identity()),"manual traffic cannot evict accepted operation identity");
        for(int slot=0;slot<4;slot++){
            var before=RollMotion.at(.13-1e-6,1.15,3,false,slot);var after=RollMotion.at(.13,1.15,3,false,slot);
            check(Math.abs(before.x()-after.x())<.01&&Math.abs(before.rx()-after.rx())<.01,"intro tumble continuous");
            var land=RollMotion.at(1.15,1.15,3,false,slot);check(Math.abs(land.rx())<1e-9,"land orientation fixed");
            var reveal=RollMotion.at(1.35,1.15,3,false,slot);check(reveal.phase()==RollMotion.Phase.REVEAL&&reveal.lift()==0,"settles");
            check(RollMotion.at(.1,1.15,3,true,slot).phase()==RollMotion.Phase.REVEAL,"reduced motion clear immediately");
        }
        for(double invalid:new double[]{0,.49,2.01,Double.NaN}){try{new DicePresentationSettings(1,DicePresentationSettings.Mode.QUICK,DicePresentationSettings.Mode.CINEMATIC,invalid,false,true,.5,3,1);throw new AssertionError("invalid speed allowed");}catch(IllegalArgumentException expected){assertions++;}}
        System.out.println("Cinematic dice presentation checks passed: "+assertions);
    }
}
