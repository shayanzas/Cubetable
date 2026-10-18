package dev.tacticalcombat.client;
import com.google.gson.*;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import java.util.*;
import static dev.tacticalcombat.client.SrdBattlefieldInteraction.*;
import static dev.tacticalcombat.client.SrdBattlefieldModel.Rect;

public final class SrdBattlefieldCorrectionTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    private static boolean overlaps(Rect a,Rect b){return a.x()<b.x()+b.w()&&a.x()+a.w()>b.x()&&a.y()<b.y()+b.h()&&a.y()+a.h()>b.y();}
    public static void run(){
        var scope=new Scope("enc",1,"player",5,"dm");var cell=new Cell(3,0,1);
        var selection=new SrdMovementPreview.Selection(Reason.OK,List.of(new Cell(1,0,1),new Cell(2,0,1),cell),OptionalInt.of(15),15,List.of());
        var context=new SrdBattlefieldInteraction();
        check(context.ground(scope,cell,selection,true,300,200),"ground click selects directly without a Move tool");
        check(context.kind()==Kind.MOVE&&context.destination().equals(cell)&&context.x()==300&&context.y()==200,"click preserves anchor and destination");
        context.clear();check(!context.consumeMove(scope,true),"cancel cannot mutate");
        check(!context.ground(scope,cell,selection,false,0,0),"pending/inspection input cannot select executable route");
        var invalid=new SrdMovementPreview.Selection(Reason.UNREACHABLE,List.of(),OptionalInt.empty(),30,List.of(Reason.OCCUPIED));
        check(!context.ground(scope,cell,invalid,true,0,0)&&context.kind()==Kind.IDLE,"invalid terrain never offers enabled Move");
        context.ground(scope,cell,selection,true,10,10);context.reconcile(scope);check(context.kind()==Kind.MOVE,"harmless same-scope refresh preserves confirmation");
        check(context.consumeMove(scope,true)&&!context.consumeMove(scope,true),"explicit confirmation consumes one local dispatch opportunity");
        for(var changed:List.of(new Scope("other",1,"player",5,"dm"),new Scope("enc",2,"player",5,"dm"),new Scope("enc",1,"husk",5,"dm"),new Scope("enc",1,"player",6,"dm"),new Scope("enc",1,"player",5,"none"))){context.ground(scope,cell,selection,true,0,0);context.reconcile(changed);check(!context.consumeMove(changed,true),"changed encounter/actor/turn/revision/permissions invalidates movement");}
        context.ground(scope,cell,selection,true,5,5);check(context.actor(scope,"husk",1,true,9,9)&&context.kind()==Kind.ACTOR&&context.destination()==null,"actor selection replaces movement and requests one-choice preview on deliberate click");
        check(!context.actor(scope,"husk",2,true,9,9),"multiple choices require deliberate choice");
        check(!context.actor(scope,"husk",0,true,9,9),"no choices produces no preview");
        check(!context.actor(scope,"husk",1,false,9,9),"pending or spent action cannot auto-preview");
        context.actor(scope,"husk",1,true,9,9);context.reconcile(new Scope("enc",1,"player",6,"dm"));check(context.matchesActor("husk"),"preview response revision retains selected actor");
        context.reconcile(new Scope("enc",1,"player",6,"none"));check(context.kind()==Kind.IDLE,"revocation removes actor context");
        var state=JsonParser.parseString("{\"state\":\"ACTIVE\",\"active\":\"player\",\"turn\":1,\"moving\":false,\"actionUsed\":false,\"actors\":[{\"id\":\"player\",\"name\":\"ree_bunger\",\"controlled\":true},{\"id\":\"husk\",\"name\":\"Husk\",\"controlled\":true}]}").getAsJsonObject();
        var preview=JsonParser.parseString("{\"actor\":\"player\",\"target\":\"husk\",\"turn\":1,\"revision\":5}").getAsJsonObject();preview.addProperty("id",UUID.randomUUID().toString());
        check(SrdBattlefieldActions.canConfirm(state,preview,"husk",5,""),"exact authorized server intent can confirm");
        for(var pending:List.of("preview","confirm","move","dash"))check(!SrdBattlefieldActions.canConfirm(state,preview,"husk",5,pending),"pending operation prevents double submission");
        check(!SrdBattlefieldActions.canConfirm(state,preview,"player",5,"")&&!SrdBattlefieldActions.canConfirm(state,preview,"husk",6,""),"target switch and new revision reject stale intent");
        state.addProperty("actionUsed",true);check(!SrdBattlefieldActions.canConfirm(state,preview,"husk",5,""),"spent action unavailable");state.addProperty("actionUsed",false);
        state.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("controlled",false);check(!SrdBattlefieldActions.canConfirm(state,preview,"husk",5,""),"ordinary player cannot borrow manager control");state.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("controlled",true);
        state.addProperty("active","husk");check(!SrdBattlefieldActions.canConfirm(state,preview,"husk",5,""),"same manager changing actor invalidates old intent");state.addProperty("active","player");
        var replies=new SrdReplyGate();var earlier=UUID.randomUUID();var later=UUID.randomUUID();replies.expect(earlier);replies.expect(later);
        check(!replies.matches(earlier.toString())&&replies.matches(later.toString()),"late rejection cannot release a newer request's pending guard");replies.clear();check(!replies.matches(later.toString()),"turn/permission/disconnect clear pending identity");
        var gate=new SrdPreviewGate();var request=UUID.randomUUID();gate.expect(request,"enc",1,"player","husk");
        check(gate.accepts("enc",1,"player",request.toString(),preview),"matching deliberate request accepted");
        check(!gate.accepts("enc",1,"player",UUID.randomUUID().toString(),preview)&&!gate.accepts("enc",2,"player",request.toString(),preview),"late/wrong-scope preview cannot reopen popup");
        gate.clear();check(!gate.accepts("enc",1,"player",request.toString(),preview),"cancel during pending preview suppresses eventual reply");
        gate.expect(request,"enc",1,"player","other");check(!gate.accepts("enc",1,"player",request.toString(),preview),"target switch cannot reuse prior preview");
        var order=new JsonArray();order.add("husk");order.add("player");state.add("initiativeOrder",order);
        check(SrdBattlefieldPresentation.initiative(state).getFirst().get("id").getAsString().equals("husk"),"actual server turn order wins over raw row order or initiative sorting");
        order=new JsonArray();order.add("player");order.add("husk");state.add("initiativeOrder",order);check(SrdBattlefieldPresentation.initiative(state).getFirst().get("id").getAsString().equals("player"),"server tie decision preserved");
        for(double yaw:new double[]{0,45,90,180,270}){
            var right=SrdBattlefieldPresentation.pan(yaw,0,1);var forward=SrdBattlefieldPresentation.pan(yaw,1,0);double r=Math.toRadians(yaw);
            check(Math.abs(right[0]*-Math.cos(r)+right[1]*-Math.sin(r)-1)<1e-9,"D always projects onto screen-right");
            check(Math.abs(right[0]*forward[0]+right[1]*forward[1])<1e-9,"pan axes remain perpendicular after orbit");
            var left=SrdBattlefieldPresentation.pan(yaw,0,-1);check(Math.abs(left[0]+right[0])<1e-9&&Math.abs(left[1]+right[1])<1e-9,"A is screen-left");
        }
        var orbit=new SrdBattlefieldPresentation.Orbit();check(!orbit.begin(1,0,0,true),"camera orbit never starts through UI");
        orbit.begin(1,10,10,false);check(!orbit.drag(1,12,12)&&orbit.release(1),"short right click cancels context");
        orbit.begin(1,10,10,false);check(orbit.drag(1,17,10)&&!orbit.release(1)&&!orbit.active(),"right drag rotates and does not cancel on release");
        orbit.begin(2,0,0,false);check(orbit.drag(2,1,0)&&!orbit.release(2),"middle drag retained");
        orbit.begin(1,0,0,false);orbit.reset();check(!orbit.active()&&!orbit.release(1),"screen closure releases captured gesture");
        check(SrdBattlefieldPresentation.hideHotbar(true,false,true,true),"hotbar suppressed only for SRD battlefield");
        for(int flags=0;flags<16;flags++){boolean s=(flags&1)!=0,t=(flags&2)!=0,b=(flags&4)!=0,w=(flags&8)!=0;check(SrdBattlefieldPresentation.hideHotbar(s,t,b,w)==(s&&!t&&b&&w),"end/world change/inventory/legacy restore HUD without persistent preference");}
        var mask=SrdOcclusionMask.candidates(0,12,-20,0,1,0,0);check(!mask.isEmpty()&&mask.size()<=192,"fade geometry bounded");
        for(var b:mask){check(b.y()>=0,"ground below actor never faded");check(Math.abs(b.x())<=1,"parallel silhouette does not widen into a cone");}
        check(!mask.contains(new SrdOcclusionMask.Block(3,10,-18)),"unrelated nearby wall remains opaque");
        check(SrdOcclusionMask.candidates(0,1,0,0,1,1,0).isEmpty(),"no stale fade when camera unobstructed close to actor");
        var history=new SrdRecentActivity();history.add("attack:1","Disadvantage; d20 [18, 2]; kept 2; miss",0);history.add("move:2","Moved",10);check(history.toast(20).id().equals("attack:1"),"damage result not replaced by routine movement status");history.clear();check(history.toast(20)==null,"permission cleanup removes private combat data");
        var result=JsonParser.parseString("{\"critical\":false,\"hit\":false,\"total\":4,\"chosen\":2,\"keep\":2,\"faces\":[18,2]}").getAsJsonObject();var text=SrdAttackFeedback.describe(result,state.getAsJsonArray("actors"));check(text.contains("Disadvantage")&&text.contains("kept 2")&&!text.contains("Dodge"),"final authorized mode visible without guessed source attribution");
        for(int width:new int[]{256,320,426,480,640,854,1280,1920})for(int height:new int[]{144,180,240,360,480,720}){
            var layout=SrdBattlefieldPresentation.layout(width,height);
            for(var box:List.of(layout.status(),layout.initiative(),layout.camera(),layout.bar(),layout.history()))check(box.x()>=0&&box.y()>=0&&box.w()>0&&box.h()>0&&box.x()+box.w()<=width&&box.y()+box.h()<=height,"responsive HUD bounds");
            check(!overlaps(layout.status(),layout.camera())&&!overlaps(layout.initiative(),layout.bar()),"HUD sections stay separate");
            for(int x:new int[]{0,5,width/2,width-5,width})for(int y:new int[]{0,5,height/2,height-5,height}){
                var popup=SrdBattlefieldPresentation.popup(x,y,252,174,width,height,layout.bar());
                check(popup.x()>=0&&popup.y()>=0&&popup.x()+popup.w()<=width&&popup.y()+popup.h()<=height&&!overlaps(popup,layout.bar()),"edge popup flips/clamps above persistent commands");
                check(popup.h()>=38&&popup.w()>=180,"confirm and cancel remain accessible in constrained viewport");
            }
        }
        System.out.println("SRD K-B interaction checks passed: "+count);
    }
}
