package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.UiApiService;
import java.util.*;
import java.util.function.Consumer;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class HubUiTests {
    private static int count;
    private static void check(boolean ok,String message) {if(!ok) throw new AssertionError(message);count++;}
    private static JsonObject json(String s) {return JsonParser.parseString(s).getAsJsonObject();}
    public static void run() {
        var state=new HubState();state.reset(false);check(state.status==HubState.Status.UNSUPPORTED && !state.dmTools() && !state.canSwitch(),"unsupported server has no manager/selection authority");
        state.reset(true);check(!state.dmTools(),"loading never grants manager access");
        state.active(json("{\"campaign\":\"a\",\"character\":\"c\",\"boundCharacter\":\"b\",\"inCombat\":true}"));
        state.campaign(json("{\"id\":\"a\",\"name\":\"Campaign A\",\"profile\":\"srd_5_1\",\"manager\":true}"));state.status=HubState.Status.READY;
        check(state.dmTools() && !state.canSwitch(),"DM may view tools while selection remains combat locked");
        state.campaign(json("{\"id\":\"old\",\"name\":\"Old campaign\",\"profile\":\"manual_tabletop\",\"manager\":false}"));
        check(state.manager && state.campaignName.equals("Campaign A"),"late different-campaign details cannot replace current selection");
        state.character(json("{\"id\":\"old-character\",\"name\":\"Old character\",\"approved\":true}"));check(state.characterName.equals("No character selected"),"late unrelated character detail ignored");
        state.character(json("{\"id\":\"c\",\"name\":\"Mira\",\"approved\":false}"));check(state.characterName.contains("awaiting approval") && state.bound.equals("b"),"selection display never silently changes binding");
        state.campaign(json("{\"id\":\"a\",\"name\":\"Campaign A\",\"profile\":\"srd_5_1\",\"manager\":false}"));check(!state.dmTools(),"revoked manager role disables tools");
        state.failed("TIMEOUT","Refresh before retrying");check(!state.canSwitch() && !state.dmTools() && state.message.contains("Refresh"),"uncertain replies disable quick actions and expose recovery message");
        state.active(json("{\"campaign\":\"a\",\"boundCharacter\":\"b\",\"inCombat\":false}"));state.character(json("{\"id\":\"b\",\"campaign\":\"different\",\"name\":\"Bound Hero\",\"approved\":true,\"bound\":\"player\"}"));
        check(state.character==null && state.characterName.startsWith("Bound:"),"bound fallback is distinguished from an explicitly selected character");
        check(state.characterName.contains("other campaign"),"binding from another campaign is disclosed without automatically rebinding");
        var campaigns=JsonParser.parseString("[{\"id\":\"a\",\"name\":\"Campaign A\",\"profile\":\"srd_5_1\",\"revision\":2,\"manager\":true},{\"id\":\"b\",\"name\":\"Campaign B\",\"profile\":\"manual_tabletop\",\"revision\":1,\"manager\":false}]").getAsJsonArray();
        check(HubState.campaigns(campaigns,true,false).size()==1,"quick dropdown lists DM-authorized campaigns only");
        check(HubState.campaigns(campaigns,false,false).size()==2,"ordinary campaign browser retains permitted membership choices");
        check(HubState.campaigns(campaigns,true,true).stream().noneMatch(HubState.Choice::enabled),"combat lock applies to every quick choice");
        check(HubState.filter(HubState.campaigns(campaigns,false,false),"MANUAL").size()==1,"local search matches human ruleset name without a network request");
        var chars=JsonParser.parseString("[{\"id\":\"c\",\"name\":\"Mira\",\"privateView\":true,\"controller\":\"player\",\"approved\":true,\"revision\":3},{\"id\":\"d\",\"name\":\"Private\",\"privateView\":false,\"controller\":\"other\",\"approved\":true,\"revision\":1},{\"id\":\"e\",\"name\":\"Assigned elsewhere\",\"privateView\":true,\"controller\":\"other\",\"approved\":false,\"revision\":1}]").getAsJsonArray();
        var choices=HubState.characters(chars,"player",false);check(choices.size()==2 && choices.getFirst().enabled() && !choices.getLast().enabled(),"private outsiders are filtered; viewable noncontrolled choices explain assignment");
        check(HubState.allowManager(choices,true,false).stream().allMatch(HubState.Choice::enabled),"authoritative manager may select eligible campaign characters");
        check(HubState.allowManager(HubState.characters(chars,"player",true),true,true).stream().noneMatch(HubState.Choice::enabled),"manager cannot bypass active selection lock");
        for(int[] size:new int[][]{{427,240},{854,480},{640,360},{1280,720}}) {
            var f=UiLayout.frame(size[0],size[1],720);var tiles=UiLayout.hubTiles(f);check(tiles.size()==6,"six hub destinations at supported logical size");
            for(var r:tiles) check(r.fits(size[0],size[1]) && r.height()>=28 && r.y()+r.height()<=f.y()+f.height()-30,"tile stays readable and above footer");
            for(int i=0;i<tiles.size();i++) for(int j=i+1;j<tiles.size();j++) check(!tiles.get(i).overlaps(tiles.get(j)),"hub cards cannot overlap");
        }
        check(UiLayout.hubTiles(UiLayout.frame(320,180,720)).isEmpty(),"smaller screens use scrollable destination list instead of tiny buttons");
        var controls=List.of(new UiLayout.Rect(111,52,204,20),new UiLayout.Rect(111,180,204,20),new UiLayout.Rect(149,18,128,18));
        var placement=UiLayout.pauseButton(427,240,controls).orElseThrow();check(placement.fits(427,240) && controls.stream().noneMatch(placement::overlaps),"pause entry avoids vanilla controls/title at 427x240");
        check(UiLayout.pauseButton(160,100,List.of(new UiLayout.Rect(0,0,160,100))).isEmpty(),"fully occupied third-party menu is preserved");
        var transport=new Fake();var pump=new UiRequestPump(transport);var replies=new ArrayList<UiApiService.Reply>();boolean[] alive={true};
        var args=json("{\"page\":0}");pump.enqueue(CAMPAIGN_LIST,args,()->alive[0],replies::add);args.addProperty("page",99);pump.tick();check(transport.sentArgs.get("page").getAsInt()==0,"queued args are isolated from later local edits");
        pump.enqueue(ACTIVE,new JsonObject(),()->alive[0],replies::add);for(int i=0;i<20;i++) pump.tick();check(transport.sent==1,"only one request remains in flight");
        alive[0]=false;transport.reply.accept(new UiApiService.Reply("OK","late",new JsonObject()));check(replies.isEmpty(),"closed/resized screen cannot accept a late reply");pump.tick();check(pump.queued()==0,"closed screen work is discarded");
        alive[0]=true;pump.enqueue(CAMPAIGN_SELECT,json("{\"campaign\":\"a\"}"),()->true,replies::add);pump.tick();transport.reply.accept(new UiApiService.Reply("TIMEOUT","Refresh",new JsonObject()));for(int i=0;i<30;i++) pump.tick();check(transport.sent==2 && replies.size()==1,"uncertain mutation is never automatically retried");
        pump.enqueue(ACTIVE,new JsonObject(),()->true,replies::add);pump.tick();var old=transport.reply;pump.clear();pump.enqueue(ACTIVE,new JsonObject(),()->true,replies::add);pump.tick();old.accept(new UiApiService.Reply("OK","old connection",new JsonObject()));check(pump.waiting(),"old connection reply cannot release new session's request");
        transport.reply.accept(new UiApiService.Reply("RATE_LIMIT","Wait",new JsonObject()));pump.enqueue(ACTIVE,new JsonObject(),()->true,replies::add);int sent=transport.sent;for(int i=0;i<24;i++) pump.tick();check(transport.sent==sent,"server rate rejection imposes bounded backoff");pump.tick();check(transport.sent==sent+1,"queued refresh resumes after backoff");
        pump.clear();transport.supported=false;pump.enqueue(ACTIVE,new JsonObject(),()->true,replies::add);pump.tick();check(replies.getLast().code().equals("UNAVAILABLE") && !pump.waiting(),"unsupported transport has a readable state without a request");
        pump.clear();for(int i=0;i<17;i++) pump.enqueue(ACTIVE,new JsonObject(),()->true,replies::add);check(pump.queued()==16 && replies.getLast().code().equals("BUSY"),"rapid reopen/refresh cannot grow queue without bound");
        System.out.println("Hub UI logic checks passed: "+count);
    }
    private static final class Fake implements UiRequestPump.Transport {
        boolean supported=true;int sent;JsonObject sentArgs;Consumer<UiApiService.Reply> reply;
        public boolean supported() {return supported;}
        public void send(UiApiService.Operation op,JsonObject args,Consumer<UiApiService.Reply> callback) {sent++;sentArgs=args;reply=callback;}
    }
}
