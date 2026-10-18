package dev.tacticalcombat.client;
import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import java.util.*;

public final class SrdBattlefieldUiTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    private static boolean overlap(SrdBattlefieldModel.Rect a,SrdBattlefieldModel.Rect b){return a.x()<b.x()+b.w()&&a.x()+a.w()>b.x()&&a.y()<b.y()+b.h()&&a.y()+a.h()>b.y();}
    public static void run(){
        var actors=JsonParser.parseString("[{\"id\":\"player\",\"name\":\"ree_bunger\",\"controlled\":true,\"initiative\":6,\"hp\":10,\"maximumHp\":10,\"temporaryHp\":0},{\"id\":\"husk\",\"name\":\"Husk\",\"controlled\":true,\"initiative\":8}]").getAsJsonArray();
        var state=new JsonObject();state.add("actors",actors);state.addProperty("active","husk");
        check(SrdBattlefieldModel.next(state).get("name").getAsString().equals("ree_bunger"),"next actor follows server roster, not local player's identity");
        state.addProperty("active","player");check(SrdBattlefieldModel.next(state).get("name").getAsString().equals("Husk"),"initiative wraps");
        var model=new SrdBattlefieldModel();model.reconcile("encounter",1,true,actors);model.target="husk";model.tool=SrdBattlefieldModel.Tool.ATTACK;
        model.reconcile("encounter",1,true,actors.deepCopy());check(model.target.equals("husk")&&model.tool==SrdBattlefieldModel.Tool.ATTACK,"harmless refresh preserves target and action");
        model.reconcile("encounter",2,true,actors);check(model.target.isEmpty()&&model.tool==SrdBattlefieldModel.Tool.MOVE,"turn change clears old target and resets tool");
        model.target="player";model.reconcile("encounter",2,false,actors);check(model.target.isEmpty()&&model.tool==SrdBattlefieldModel.Tool.INSPECT,"permission loss removes actionable context");
        model.target="missing";model.reconcile("encounter",2,false,actors);check(model.target.isEmpty(),"unavailable target removed");
        model.target="husk";model.cancel();check(model.target.isEmpty()&&model.tool==SrdBattlefieldModel.Tool.INSPECT,"escape/right-click cancels without gameplay execution");
        check(SrdBattlefieldModel.hp(actors.get(1).getAsJsonObject()).equals("HP private"),"no invented monster HP");
        check(SrdBattlefieldModel.hp(actors.get(0).getAsJsonObject()).contains("10 / 10"),"authorized HP only");
        state.addProperty("state","ACTIVE");state.addProperty("actionUsed",false);state.addProperty("moving",false);
        actors.get(0).getAsJsonObject().add("weapons",JsonParser.parseString("[\"UNARMED\"]"));
        var args=SrdBattlefieldActions.preview(state,"husk",SrdAttackChoices.forWeapon("UNARMED",null).getFirst());
        check(args.keySet().equals(Set.of("target","weapon","ability","mode","twoHands")),"preview submits only selection intent, no dice/damage/HP");
        var serverPreview=new JsonObject();UUID intent=UUID.randomUUID();serverPreview.addProperty("id",intent.toString());serverPreview.addProperty("damage","999 is display only");
        var confirmation=SrdBattlefieldActions.confirmation(serverPreview);check(confirmation.keySet().equals(Set.of("intent"))&&confirmation.get("intent").getAsString().equals(intent.toString()),"explicit confirmation uses only original server intent");
        boolean denied=false;try{SrdBattlefieldActions.confirmation(null);}catch(IllegalArgumentException expected){denied=true;}check(denied,"cannot confirm without authorized preview");
        state.addProperty("actionUsed",true);denied=false;try{SrdBattlefieldActions.preview(state,"husk",SrdAttackChoices.forWeapon("UNARMED",null).getFirst());}catch(IllegalArgumentException expected){denied=true;}check(denied,"spent action disabled");
        state.addProperty("actionUsed",false);actors.get(0).getAsJsonObject().addProperty("controlled",false);denied=false;try{SrdBattlefieldActions.preview(state,"husk",SrdAttackChoices.forWeapon("UNARMED",null).getFirst());}catch(IllegalArgumentException expected){denied=true;}check(denied,"inspection grants no attack capability");
        actors.get(0).getAsJsonObject().addProperty("controlled",true);
        state.addProperty("dashFeet",30);state.addProperty("campaign",UUID.randomUUID().toString());
        check(SrdBattlefieldActions.canDash(state,""),"available normal action exposes Dash");
        var dash=SrdBattlefieldActions.dash(state,42,"");
        check(dash.keySet().equals(Set.of("campaign","actor","revision"))&&dash.get("revision").getAsLong()==42,"Dash sends scope and expected revision, never grant");
        for(String pending:List.of("dash","move","confirm","endturn"))check(!SrdBattlefieldActions.canDash(state,pending),"pending mutation disables Dash");
        state.addProperty("actionUsed",true);check(!SrdBattlefieldActions.canDash(state,""),"attack/reservation disables Dash");state.addProperty("actionUsed",false);
        state.addProperty("moving",true);check(!SrdBattlefieldActions.canDash(state,""),"route disables Dash");state.addProperty("moving",false);
        actors.get(0).getAsJsonObject().addProperty("controlled",false);check(!SrdBattlefieldActions.canDash(state,""),"revocation disables Dash");actors.get(0).getAsJsonObject().addProperty("controlled",true);
        check(!SrdBattlefieldActions.canDash(new JsonObject(),""),"disconnect clears Dash capability");
        state.remove("dashFeet");check(!SrdBattlefieldActions.canDash(state,""),"older server does not advertise Dash");
        var budget=new dev.tacticalcombat.combat.SrdActionBudget();budget.spend();
        check(budget.used()&&budget.action()==dev.tacticalcombat.combat.SrdActionBudget.Action.SPENT,"immediate action spends shared budget");
        denied=false;try{budget.reserve();}catch(IllegalArgumentException expected){denied=true;}check(denied,"Dash excludes weapon reservation");
        denied=false;try{budget.spend();}catch(IllegalArgumentException expected){denied=true;}check(denied,"double Dash forbidden");
        budget=new dev.tacticalcombat.combat.SrdActionBudget();budget.reserve();denied=false;try{budget.spend();}catch(IllegalArgumentException expected){denied=true;}check(denied,"prepared weapon excludes Dash");
        var refresh=new SrdGridRefresh();var encounter=UUID.randomUUID();refresh.requested(encounter,1,1,0);
        check(!refresh.due(encounter,2,1,true,21)&&refresh.due(encounter,2,1,true,22),"Dash revision schedules paced read-only grid refresh");
        refresh.requested(encounter,2,1,22);check(!refresh.due(encounter,2,1,true,100),"accepted Dash does not automatically repeat discovery or mutation");
        var start=new SrdMovement.Cell(0,0,0);var to=new SrdMovement.Cell(1,0,0);
        var grid=new SrdMovement.Preview(start,30,Map.of(start,new SrdMovement.Node(start,0,null),to,new SrdMovement.Node(to,5,start)),List.of(),false);
        check(SrdBattlefieldModel.retain(to,grid).equals(to),"same revision grid refresh retains legal destination");
        check(SrdBattlefieldModel.retain(new SrdMovement.Cell(9,9,0),grid)==null&&SrdBattlefieldModel.retain(null,grid)==null&&SrdBattlefieldModel.retain(to,null)==null,"missing/invalidated destinations dropped safely");
        var history=new SrdRecentActivity();history.add("attack1","Husk: HP 1 → 0; paused for DM",0);history.add("attack1","Duplicate must not animate",1);
        check(history.newest().size()==1&&history.toast(199)!=null&&history.toast(200)==null,"result deduplication and readable ten-second toast");
        history.add("attack2","Husk: HP 1 → 0; paused for DM",2);check(history.newest().size()==2&&!history.newest().get(0).id().equals(history.newest().get(1).id()),"identical text from distinct operations remains independently visible/dismissible");
        for(int i=0;i<400;i++)history.add("event"+i,"Event "+i,i);
        check(history.newest().size()==32&&history.newest().getFirst().text().equals("Event 399"),"bounded newest-first history");
        history.clear();check(history.newest().isEmpty()&&history.toast(500)==null,"revocation/disconnect clears private history");
        for(int width:new int[]{256,320,426,480,640,854,960,1280,1920})for(int height:new int[]{144,180,240,360,480,720,1080}){
            var l=SrdBattlefieldModel.layout(width,height);
            for(var r:List.of(l.status(),l.camera(),l.bar(),l.context(),l.toast()))check(r.x()>=0&&r.y()>=0&&r.w()>0&&r.h()>0&&r.x()+r.w()<=width&&r.y()+r.h()<=height,"panels bounded at "+width+"x"+height);
            check(!overlap(l.status(),l.camera()),"header and camera separate");
            check(!overlap(l.context(),l.bar()),"context never hides primary actions");
            check(!overlap(l.toast(),l.bar()),"toast never hides End Turn");
            int cols=l.narrow()?4:7,w=(l.bar().w()-10-3*(cols-1))/cols,h=l.narrow()?18:22;
            for(int i=0;i<7;i++){var r=new SrdBattlefieldModel.Rect(l.bar().x()+5+(i%cols)*(w+3),l.bar().y()+4+(i/cols)*(h+2),w,h);check(r.x()+r.w()<=l.bar().x()+l.bar().w()&&r.y()+r.h()<=l.bar().y()+l.bar().h(),"all primary buttons including End Turn bounded");}
        }
        System.out.println("SRD battlefield UI checks passed: "+count);
    }
}
