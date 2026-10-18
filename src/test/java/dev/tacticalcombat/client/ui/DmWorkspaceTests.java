package dev.tacticalcombat.client.ui;
import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

public final class DmWorkspaceTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    public static void run(){
        var m=new DmWorkspaceModel();check(DmWorkspaceModel.Tab.values().length==5,"five stable sections");
        var rows=JsonParser.parseString("[{\"actor\":\"h\",\"name\":\"Husk\",\"type\":\"minecraft:husk\",\"kind\":\"NPC\",\"bound\":true,\"eligible\":true,\"distance\":10},{\"actor\":\"p\",\"name\":\"ree_bunger\",\"kind\":\"Player\",\"bound\":true,\"eligible\":false,\"reason\":\"Already in combat\",\"distance\":1},{\"actor\":\"z\",\"name\":\"Zombie\",\"kind\":\"NPC\",\"bound\":false,\"eligible\":false,\"reason\":\"Needs binding\",\"distance\":4}]").getAsJsonArray();
        m.tab=DmWorkspaceModel.Tab.ACTORS;m.queries.put(m.tab,"husk");check(m.filter(rows,"actor").size()==1,"actor search across labels/types");m.queries.put(m.tab,"");
        for(String binding:List.of("ALL","BOUND","UNBOUND"))for(String kind:List.of("ALL","PLAYER","NPC"))for(String eligibility:List.of("ALL","READY","BLOCKED")){
            m.binding=binding;m.kind=kind;m.eligibility=eligibility;var selected=m.filter(rows,"actor");
            for(var v:rows){var a=v.getAsJsonObject();boolean expected=(binding.equals("ALL")||flag(a,"bound")==binding.equals("BOUND"))&&(kind.equals("ALL")||text(a,"kind").equalsIgnoreCase(kind))&&(eligibility.equals("ALL")||flag(a,"eligible")==eligibility.equals("READY"));check(selected.contains(a)==expected,"real independent binding/type/readiness filters");}
        }
        m.binding=m.kind=m.eligibility="ALL";m.sort="DISTANCE";check(text(m.filter(rows,"actor").getFirst(),"name").equals("ree_bunger"),"distance sorting uses server distance");
        m.selections.put(m.tab,"h");m.participants.add("h");m.reconcile(rows,"actor");check(m.participants.contains("h")&&m.selections.get(m.tab).equals("h"),"read-only refresh preserves valid selection");
        m.participants.add("z");m.participants.add("missing");m.reconcile(rows,"actor");check(m.participants.size()==1,"blocked/unavailable participants cannot remain selected");
        for(var tab:DmWorkspaceModel.Tab.values()){m.tab=tab;m.scrolls.put(tab,tab.ordinal()*10);m.queries.put(tab,tab.name());m.selections.put(tab,"selection"+tab);}
        m.tab=DmWorkspaceModel.Tab.PARTY;check(m.scrolls.get(m.tab)==0&&m.queries.get(m.tab).equals("PARTY"),"per-tab scroll/search preserved");
        m.campaign("campaignA");m.targets.add("character");m.recipients.add("member");m.participants.add("h");m.selections.put(m.tab,"private-character");m.campaign("campaignB");check(m.targets.isEmpty()&&m.recipients.isEmpty()&&m.participants.isEmpty()&&m.selections.isEmpty(),"campaign switch invalidates private identities");
        m.targets.add("c");m.revoke();check(m.targets.isEmpty()&&m.campaign.isEmpty(),"revocation clears authorized selection state");
        check(DmWorkspaceModel.sceneCoordinate(100,100,300)==150,"scene center uses actual discovery origin");check(DmWorkspaceModel.sceneCoordinate(132,100,300)==288&&DmWorkspaceModel.sceneCoordinate(68,100,300)==12,"32-block map is proportional and bounded");
        var members=JsonParser.parseString("[{\"id\":\"p\",\"name\":\"Player\"}]").getAsJsonArray();check(DmWorkspaceModel.member(members,"p").equals("Player")&&!DmWorkspaceModel.member(members,"missing").contains("missing"),"recipient labels do not expose raw identities");
        var roll=JsonParser.parseString("{\"source\":\"DM_REQUEST\",\"mode\":\"NORMAL\",\"faces\":[17],\"total\":16}").getAsJsonObject();String hidden=DmWorkspaceModel.roll(roll);check(!hidden.contains("DC")&&!hidden.contains("Success")&&!hidden.contains("Failure"),"absent private DC/success never inferred");roll.addProperty("dc",15);roll.addProperty("success",true);check(DmWorkspaceModel.roll(roll).contains("Success"),"authorized outcome only");
        var generation=new java.util.concurrent.atomic.AtomicInteger(1);var accepted=new java.util.concurrent.atomic.AtomicInteger();var replies=new ArrayList<java.util.function.Consumer<dev.tacticalcombat.uiapi.UiApiService.Reply>>();
        var sent=new ArrayList<dev.tacticalcombat.uiapi.UiApiService.Operation>();
        var pump=new UiRequestPump(new UiRequestPump.Transport(){public boolean supported(){return true;}public void send(dev.tacticalcombat.uiapi.UiApiService.Operation op,JsonObject args,java.util.function.Consumer<dev.tacticalcombat.uiapi.UiApiService.Reply> callback){sent.add(op);replies.add(callback);}});
        pump.enqueue(dev.tacticalcombat.uiapi.UiApiService.Operation.DM_QUICK,args("page",0),()->generation.get()==1,r->accepted.incrementAndGet());pump.tick();generation.incrementAndGet();
        replies.getFirst().accept(new dev.tacticalcombat.uiapi.UiApiService.Reply("OK","Old panel",new JsonObject()));check(accepted.get()==0,"late tab/campaign reply cannot publish private data");
        pump.enqueue(dev.tacticalcombat.uiapi.UiApiService.Operation.DM_QUICK,args("page",0),()->generation.get()==2,r->accepted.incrementAndGet());for(int i=0;i<10;i++)pump.tick();replies.getLast().accept(new dev.tacticalcombat.uiapi.UiApiService.Reply("OK","Fresh panel",new JsonObject()));check(accepted.get()==1,"read-only refresh safely recovers current generation");
        pump.enqueue(dev.tacticalcombat.uiapi.UiApiService.Operation.CHECK_EXECUTE,args("operation","existing-id"),()->generation.get()==2,r->accepted.incrementAndGet());for(int i=0;i<10;i++)pump.tick();replies.getLast().accept(new dev.tacticalcombat.uiapi.UiApiService.Reply("REJECTED","Uncertain operation",new JsonObject()));for(int i=0;i<60;i++)pump.tick();check(sent.stream().filter(op->op==dev.tacticalcombat.uiapi.UiApiService.Operation.CHECK_EXECUTE).count()==1,"rejected/ambiguous mutation is never automatically repeated");
        for(int width:new int[]{320,426,480,640,854,960,1280,1920})for(int height:new int[]{180,240,270,360,540,720,1080})for(boolean compact:List.of(true,false)){
            var layout=DmWorkspaceModel.layout(width,height,compact);check(layout.frame().fits(width,height),"workspace frame bounded");check(layout.body().fits(width,height),"scroll content bounded");check(layout.body().y()+layout.body().height()<=layout.frame().y()+layout.frame().height()-30,"content does not overlap Back/Battlefield");int tabs=(layout.frame().width()-12)/5;check(tabs>=50,"five tabs legible at minimum viewport");
        }
        System.out.println("DM workspace checks passed: "+count);
    }
}
