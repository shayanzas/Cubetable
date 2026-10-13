package dev.tacticalcombat.client.ui;
import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.uiapi.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
public final class UiCorrectionTests {
    private static int count;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
    public static void run() throws Exception {
        int[] depth={0};Runnable push=()->depth[0]++,pop=()->{if(--depth[0]<0)throw new AssertionError("underflow");};
        try(var outer=ClipScope.open(push,pop)){try(var inner=ClipScope.open(push,pop)){check(depth[0]==2,"nested clips acquired");}check(depth[0]==1,"inner clip retains outer clip");}
        check(depth[0]==0,"empty and nested render exits balance");
        try{try(var clip=ClipScope.open(push,pop)){throw new IllegalArgumentException("render failure");}}catch(IllegalArgumentException expected){}
        check(depth[0]==0,"exception releases acquired clip");var scope=ClipScope.open(push,pop);scope.close();scope.close();check(depth[0]==0,"close cannot underflow");
        var dir=Files.createTempDirectory("ui-correction");var repo=new CharacterRepository(dir.resolve("campaigns.json"));var library=new LibraryRepository(dir.resolve("library.json"));var owner=new CharacterRepository.Principal(UUID.randomUUID(),true);
        var api=new UiApiService(repo,library,(op,a)->{throw new IllegalArgumentException("world unavailable");});
        var created=api.handle(owner,LIBRARY_CREATE,args("profile","srd_5_1","mode","GUIDED","sheet",null));check(created.code().equals("OK"),"create draft");String id=text(created.data(),"id");
        JsonObject canonical=DraftReplies.snapshot(created.data(),id);String saved=canonical.toString();
        var validation=api.handle(owner,LIBRARY_VALIDATE,args("character",id));check(validation.code().equals("OK")&&!validation.data().has("sheet"),"actual validation returns partial projection");
        DraftReplies.validation(validation.data());check(canonical.toString().equals(saved),"validation never replaces canonical draft");
        for(var stage:WizardModel.Stage.values())check(WizardModel.statuses(canonical).get(stage)!=null,"validate then render/navigate "+stage);
        check(WizardModel.statuses(new JsonObject()).size()==11,"incomplete snapshot has graceful summary");
        try{DraftReplies.snapshot(validation.data(),id);throw new AssertionError("partial finalize accepted");}catch(IllegalArgumentException expected){count++;}
        check(canonical.toString().equals(saved),"unexpected finalize leaves prior draft intact");
        var finalized=api.handle(owner,LIBRARY_FINALIZE,args("character",id,"revision",revision(canonical)));check(finalized.code().equals("OK"),"finalize server reply");canonical=DraftReplies.snapshot(finalized.data(),id);
        check(WizardModel.statuses(canonical).get(WizardModel.Stage.REVIEW).complete(),"finalize then render review");
        check(new LibraryRepository(dir.resolve("library.json")).get(owner,UUID.fromString(id)).sheet().equals(canonical.get("sheet").toString()),"restart retains finalized draft");
        var list=new RpgList(0,0,200,114,c->{});var rows=new ArrayList<HubState.Choice>();for(int i=0;i<20;i++)rows.add(new HubState.Choice("id"+i,"Row "+i,"",true,"",0));list.rows(rows);list.restorePosition(10,12);list.rows(rows);check(list.firstVisible()==10&&list.selectedIndex()==12,"refresh retains scroll/selection");list.rows(rows.subList(0,2));check(list.firstVisible()==0&&list.selectedIndex()==1,"shrinking list clamps safely");list.rows(rows);check(list.firstVisible()==0&&list.selectedIndex()==1,"regrowing list preserves clamped position");
        var hud=new HudSession();UUID session=UUID.randomUUID(),event=UUID.randomUUID();var page=args("session",session,"cursor",1,"events",new JsonArray());page.getAsJsonArray("events").add(args("id",event,"sequence",1,"kind","APPROVAL","summary","Authorized","actors",new JsonArray()));hud.events(page,1,20);hud.revalidateHistory();check(hud.historySize()==1,"periodic refresh retains authorized display");hud.events(page,2,20);check(hud.historySize()==1&&hud.nextNotice()==null,"atomic refresh does not replay notices");hud.invalidateHistory();check(hud.historySize()==0,"role revocation immediately removes private history");
        var selectedFile=dir.resolve("selections.json");var selections=new SelectionStore(selectedFile);UUID campaign=repo.createCampaign(owner,"Restore");UUID copy=repo.submit(owner,campaign,"srd_5_1",canonical.get("sheet").toString());selections.put(owner.id(),campaign,copy);var restarted=new SelectionStore(selectedFile);check(restarted.eligible(repo,owner,false).character().equals(copy),"selection restored after restart");check(restarted.eligible(repo,owner,true)==null&&restarted.get(owner.id())!=null,"encounter blocks restoration and retains intent");check(repo.binding(copy)==null,"selection never binds");var member=new CharacterRepository.Principal(UUID.randomUUID(),false);repo.invite(owner,campaign,1,member.id(),false);restarted.put(member.id(),campaign,copy);check(restarted.eligible(repo,member,false).character()==null,"unauthorized control cannot restore");repo.removeMember(owner,campaign,2,member.id());check(restarted.eligible(repo,member,false)==null&&restarted.get(member.id())==null,"revoked membership clears selection");
        check(WizardModel.validationStage("basics")==WizardModel.Stage.BASICS&&WizardModel.validationStage("combat")==WizardModel.Stage.ABILITIES&&WizardModel.validationStage("inventory")==WizardModel.Stage.EQUIPMENT,"validation links target supported stages");
        var victim=api.handle(owner,LIBRARY_CREATE,args("profile","srd_5_1","mode","MANUAL","sheet",null)).data();String victimId=text(victim,"id");var other=new CharacterRepository.Principal(UUID.randomUUID(),true);check(!api.handle(other,LIBRARY_DELETE,args("character",victimId,"revision",revision(victim))).code().equals("OK"),"administrator cannot delete another private source");check(!api.handle(owner,LIBRARY_DELETE,args("character",victimId,"revision",0)).code().equals("OK"),"delete rejects stale revision");var state=repo.state();check(api.handle(owner,LIBRARY_DELETE,args("character",id,"revision",revision(canonical))).code().equals("OK"),"owner deletes acknowledged source");check(repo.state().equals(state)&&repo.character(copy)!=null,"delete preserves independent campaign copies and resources");check(!api.handle(owner,LIBRARY_GET,args("character",id)).code().equals("OK"),"deleted source cannot be reopened");
        System.out.println("UI correction checks passed: "+count);
    }
}
