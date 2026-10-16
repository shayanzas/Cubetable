package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.client.ui.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.character.UiLifecycleTests.args;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class UiStabilizationTests {
    private static int count;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
    private interface Work {void run()throws Exception;}
    private static void rejects(Work work,String message)throws Exception{try{work.run();}catch(RuntimeException|java.io.IOException expected){count++;return;}throw new AssertionError(message);}
    public static void run()throws Exception{
        UiEvents.SESSION.clear();var dir=Files.createTempDirectory("ui-stabilization");var path=dir.resolve("characters.json");
        boolean[] fail={false};var repo=new CharacterRepository(new AtomicJsonFile(path,destination->{if(fail[0]&&destination.equals(path))throw new java.io.IOException("Injected interruption");}));
        var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);var owner=new CharacterRepository.Principal(UUID.randomUUID(),false);var controller=new CharacterRepository.Principal(UUID.randomUUID(),false);var stranger=new CharacterRepository.Principal(UUID.randomUUID(),false);
        UUID campaign=repo.createCampaign(dm,"Counters");repo.invite(dm,campaign,1,owner.id(),false);repo.invite(dm,campaign,2,controller.id(),false);repo.invite(dm,campaign,3,stranger.id(),false);
        var sheet=SrdAuthoring.template();
        sheet=SheetSections.update(sheet,"trackers",SheetSections.JSON.toJsonTree(new SheetSections.Trackers(false,0,0,0,List.of(new SheetSections.HitDice(10,2,2,"manual")),List.of(new SheetSections.Pool("manual:rage","Rage",2,2,"manual","adjudication",SheetSections.Automation.MANUAL)))));
        sheet=SheetSections.update(sheet,"spellbook",SheetSections.JSON.toJsonTree(new SheetSections.Spellbook(List.of(),List.of(),List.of(new SheetSections.Slot(1,2,2,"manual")),"",0,0)));
        UUID id=repo.submit(owner,campaign,RulesProfiles.SRD,sheet.toString());repo.approve(dm,id,1);repo.bind(dm,id,2,owner.id(),owner.id());
        var before=repo.srdSnapshot(owner.id(),campaign);var old=repo.character(id);
        var changes=List.of(new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"trackers","",0,1),new RpgRecords.Change(RpgRecords.Action.DEATH_SUCCESSES,"trackers","",0,2),new RpgRecords.Change(RpgRecords.Action.DEATH_FAILURES,"trackers","",0,1),new RpgRecords.Change(RpgRecords.Action.EXHAUSTION,"trackers","",0,3),new RpgRecords.Change(RpgRecords.Action.HIT_DICE,"trackers","",0,1),new RpgRecords.Change(RpgRecords.Action.SPELL_SLOT,"spellbook","",0,1),new RpgRecords.Change(RpgRecords.Action.RESOURCE,"trackers","manual:rage",0,1));
        for(var change:changes){repo.track(change.action()==RpgRecords.Action.INSPIRATION?dm:owner,id,repo.manualRevision(id),change);check(repo.srdSnapshot(owner.id(),campaign).equals(before),"manual "+change.action()+" preserves complete combat snapshot");}
        check(UiEvents.SESSION.query(repo,owner,campaign,0,UiEvents.Kind.CONDITION).events().size()==1,"manual exhaustion has recorded-only condition event");
        check(repo.manualRevision(id)==7&&repo.character(id).approved()&&repo.character(id).revision()==old.revision()&&repo.character(id).runtimeRevision()==old.runtimeRevision(),"independent revision and approval");
        check(repo.character(id).owner().equals(owner.id())&&repo.binding(id).equals(owner.id()),"identity binding preserved");
        String boundSave=Files.readString(path);var persisted=new CharacterRepository(path);check(persisted.character(id).equals(repo.character(id))&&persisted.manualRevision(id)==7,"restart persists sheet and manual CAS together");
        long events=UiEvents.SESSION.query(repo,owner,campaign,0,UiEvents.Kind.RESOURCE).events().size();
        rejects(()->repo.track(owner,id,6,changes.getFirst()),"stale counter write rejected");check(UiEvents.SESSION.query(repo,owner,campaign,0,UiEvents.Kind.RESOURCE).events().size()==events,"retry emits no duplicate event");
        rejects(()->repo.track(stranger,id,7,changes.getFirst()),"member cannot edit another character");
        rejects(()->repo.track(owner,id,7,new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"combat","",0,1)),"cannot select combat section");
        rejects(()->repo.track(owner,id,7,new RpgRecords.Change(RpgRecords.Action.HIT_DICE,"trackers","",0,3)),"counter maximum validated");
        var unchanged=repo.state();fail[0]=true;rejects(()->repo.track(owner,id,7,changes.getFirst()),"failed replacement");fail[0]=false;
        check(repo.state().equals(unchanged)&&new CharacterRepository(path).state().equals(unchanged),"failure publishes neither memory nor disk state");
        check(UiEvents.SESSION.query(repo,owner,campaign,0,UiEvents.Kind.RESOURCE).events().size()==events,"failure emits no success");
        var newer=repo.commitSrdHealth(before,SrdHealth.of(before.stats()).heal(1));check(repo.manualRevision(id)==7,"runtime HP does not conflict with bookkeeping");repo.track(owner,id,7,changes.getFirst());check(repo.srdSnapshot(owner.id(),campaign).equals(newer),"counter after runtime update preserves fresh combat snapshot");
        repo.unbind(dm,owner.id());repo.offerControl(dm,id,repo.character(id).revision(),0,controller.id());repo.answerControl(controller,id,1,true);repo.bind(dm,id,repo.character(id).revision(),controller.id(),controller.id());
        rejects(()->repo.track(owner,id,8,changes.getFirst()),"owner cannot counter-edit assigned controller's character");repo.track(controller,id,8,changes.getFirst());check(repo.manualRevision(id)==9,"accepted controller may track");repo.track(dm,id,9,changes.getFirst());check(repo.manualRevision(id)==10,"DM adjudication may track");
        repo.policy(dm,campaign,repo.campaign(campaign).revision(),new ApprovalPolicy(true,true,true,Set.of("collections.*")));
        rejects(()->repo.track(controller,id,10,changes.getFirst()),"campaign lock honored");repo.track(dm,id,10,changes.getFirst());check(repo.manualRevision(id)==11,"DM retains locked campaign adjudication");
        repo.policy(dm,campaign,repo.campaign(campaign).revision(),new ApprovalPolicy(true,true,false,Set.of("text.*")));
        rejects(()->repo.track(controller,id,11,changes.getFirst()),"field whitelist honored");
        repo.policy(dm,campaign,repo.campaign(campaign).revision(),ApprovalPolicy.secureDefault());
        repo.unbind(dm,controller.id());var stale=repo.character(id);long manual=repo.manualRevision(id);var oldTrackers=SheetSections.read(repo.effectiveSheet(stale),"trackers");repo.track(controller,id,manual,new RpgRecords.Change(RpgRecords.Action.DEATH_FAILURES,"trackers","",0,2));
        var diff=CharacterEdits.diff(repo.effectiveSheet(stale),SheetSections.update(repo.effectiveSheet(stale),"trackers",oldTrackers));diff.add("collections.ui_trackers",SheetSections.update(repo.effectiveSheet(stale),"trackers",oldTrackers).getAsJsonObject("collections").get("ui_trackers"));
        rejects(()->repo.editSections(dm,id,stale.revision(),stale.runtimeRevision(),manual,diff),"stale section cannot overwrite counter");
        rejects(()->repo.edit(dm,id,stale.revision(),stale.runtimeRevision(),diff),"old generic client cannot bypass manual CAS");
        var fresh=repo.character(id);repo.editSections(dm,id,fresh.revision(),fresh.runtimeRevision(),repo.manualRevision(id),diff);check(repo.manualRevision(id)==manual+2&&!repo.character(id).approved(),"explicit reconciled structural edit uses normal approval policy");
        check(UiEvents.SESSION.query(repo,owner,campaign,0,UiEvents.Kind.APPROVAL).events().stream().anyMatch(e->e.summary().contains("invalidated")),"approval invalidation has committed event");
        var api=new UiApiService(repo,new LibraryRepository(dir.resolve("library.json")),(op,a)->{throw new IllegalArgumentException("world unavailable");});
        var response=api.handle(controller,CHARACTER_GET,args("character",id));check(response.data().get("canTrack").getAsBoolean()&&response.data().has("manualRevision"),"typed dashboard read permissions and counter revision");
        var source=new DashboardSource(false,id.toString());var write=source.manualArgs(response.data(),changes.getFirst());check(source.manualOperation()==MANUAL_COUNTER&&api.handle(controller,source.manualOperation(),write).code().equals("OK"),"dashboard uses precise new counter operation");
        check(api.handle(controller,MANUAL_COUNTER,write).code().equals("CONFLICT"),"same counter request has explicit conflict");write.addProperty("approved",true);check(!api.handle(controller,MANUAL_COUNTER,write).code().equals("OK"),"privileged extra fields rejected");
        var latest=api.handle(controller,CHARACTER_GET,args("character",id)).data();
        fail[0]=true;var failedReply=api.handle(controller,MANUAL_COUNTER,source.manualArgs(latest,changes.getFirst()));fail[0]=false;
        check(failedReply.code().equals("STORAGE_ERROR")&&api.handle(controller,CHARACTER_GET,args("character",id)).data().equals(latest),"failed API save leaves complete read model unchanged");
        check(!repo.character(id).approved(),"counter never approves a draft");check(ManagementState.needsEditReview(response.data(),latest),"forms detect independent revision change");
        repo.clearControl(dm,id,repo.assignment(id).revision());check(UiEvents.SESSION.query(repo,controller,campaign,0,UiEvents.Kind.RESOURCE).events().isEmpty(),"old controller no longer receives private character resource history");
        String saved=Files.readString(path);for(int version:List.of(1,2,3)){
            var root=JsonParser.parseString(boundSave).getAsJsonObject();root.addProperty("schema",version);root.remove("manualRevisions");root.remove("receipts");if(version<3)root.remove("assignments");if(version==1)for(var c:root.getAsJsonArray("campaigns"))c.getAsJsonObject().remove("policy");
            Path migrated=dir.resolve("schema"+version+".json");Files.writeString(migrated,root.toString());var loaded=new CharacterRepository(migrated);check(loaded.character(id).equals(persisted.character(id))&&loaded.manualRevision(id)==0&&loaded.binding(id).equals(owner.id())&&loaded.character(id).approved(),"schema "+version+" retains character identity/data");loaded.track(dm,id,0,changes.getFirst());check(JsonParser.parseString(Files.readString(migrated)).getAsJsonObject().get("schema").getAsInt()==5,"schema "+version+" writes version5");
        }
        var future=JsonParser.parseString(saved).getAsJsonObject();future.addProperty("schema",6);Path newerFile=dir.resolve("future.json");Files.writeString(newerFile,future.toString());Files.writeString(dir.resolve("future.json.bak"),saved);rejects(()->new CharacterRepository(newerFile),"future version refuses older backup rollback");
        Path recovery=dir.resolve("recovery.json");Files.writeString(dir.resolve("recovery.json.bak"),saved);check(new CharacterRepository(recovery).manualRevision(id)==repo.manualRevision(id),"missing primary recovers manual revision");Files.writeString(recovery,"invalid");check(new CharacterRepository(recovery).recoveredFromBackup(),"corrupt primary recovers validated backup");Files.writeString(dir.resolve("recovery.json.bak"),"invalid");rejects(()->new CharacterRepository(recovery),"both invalid fail closed");
        var c=repo.character(id);var condition=new SheetSections.Feature("manual:condition",SheetSections.FeatureKind.CONDITION,"Recorded prone","manual only","DM",SheetSections.Automation.MANUAL,null,null,null,List.of());
        var conditionSheet=SheetSections.update(repo.effectiveSheet(c),"features",SheetSections.JSON.toJsonTree(new SheetSections.Features(List.of(condition),List.of())));
        repo.editSections(dm,id,c.revision(),c.runtimeRevision(),repo.manualRevision(id),CharacterEdits.diff(repo.effectiveSheet(c),conditionSheet));
        check(UiEvents.SESSION.query(repo,owner,campaign,0,UiEvents.Kind.CONDITION).events().stream().anyMatch(e->e.summary().startsWith("Recorded conditions")),"condition record event follows approved edit transaction");
        var eventsStore=new UiEvents();var publicId=UUID.randomUUID();eventsStore.safePublish(publicId,campaign,null,0,UiEvents.Kind.DICE,List.of(owner.id()),"public roll",UiEvents.Roll.from(20,List.of(19),0,2,"test",false),UiEvents.Visibility.CAMPAIGN,Set.of());
        var publicPage=eventsStore.query(repo,owner,campaign,9999,null);check(publicPage.events().size()==1&&publicPage.cursor()==1,"cursor from previous session resets safely");
        var eventHud=new HudSession();eventHud.events(SheetSections.JSON.toJsonTree(publicPage).getAsJsonObject(),1,20);check(eventHud.nextDice()==null,"reconnect bootstrap does not animate old history");
        UUID oldSession=publicPage.session();eventsStore.clear();eventsStore.safePublish(UUID.randomUUID(),campaign,null,0,UiEvents.Kind.DICE,List.of(owner.id()),"new session roll",UiEvents.Roll.from(20,List.of(4),0,0,"test",false),UiEvents.Visibility.CAMPAIGN,Set.of());
        var newPage=eventsStore.query(repo,owner,campaign,publicPage.cursor()+100,null);eventHud.events(SheetSections.JSON.toJsonTree(newPage).getAsJsonObject(),2,20);check(!oldSession.equals(newPage.session())&&eventHud.historySize()==1&&eventHud.nextDice()==null,"session reset discards old identity and suppresses replay");
        check(eventHud.cursor()==0&&eventHud.catchingUp(),"changed session forces complete cursor-zero reconstruction even if sequence grew past old cursor");
        eventHud.events(SheetSections.JSON.toJsonTree(eventsStore.query(repo,owner,campaign,0,null)).getAsJsonObject(),3,20);check(eventHud.historySize()==1&&eventHud.nextDice()==null,"session reconstruction deduplicates without animation");
        repo.invite(dm,campaign,repo.campaign(campaign).revision(),stranger.id(),true);eventsStore.safePublish(UUID.randomUUID(),campaign,null,0,UiEvents.Kind.DICE,List.of(),"secret",UiEvents.Roll.from(20,List.of(1),0,99,"secret",false),UiEvents.Visibility.DM,Set.of());check(eventsStore.query(repo,stranger,campaign,0,null).events().size()==2,"co-DM authorized for hidden history");repo.revokeDm(dm,campaign,repo.campaign(campaign).revision(),stranger.id());check(eventsStore.query(repo,stranger,campaign,0,null).events().size()==1,"role revocation removes hidden rolls at server");
        partyPages();System.out.println("UI stabilization checks passed: "+count);
    }
    private static JsonObject page(String token,int index,int total){var out=args("rosterToken",token,"page",index,"total",total,"hasMore",(index+1)*8<total);var rows=new JsonArray();for(int i=index*8;i<Math.min(total,(index+1)*8);i++)rows.add(args("character","character"+i,"skinPlayer","actor"+i,"name","Player "+i,"characterClass","Fighter","online",false));out.add("members",rows);return out;}
    private static void partyPages()throws Exception{
        var pages=new PartyPages();check(pages.accept(page("a",0,17))==null&&pages.page()==1,"first page pending");check(pages.accept(page("a",1,17))==null&&pages.page()==2,"second page pending");var full=pages.accept(page("a",2,17));check(full.getAsJsonArray("members").size()==17&&pages.page()==0,"atomic 17 member aggregation");var hud=new HudSession();hud.party(full);check(hud.members().size()==17&&hud.members().get(16).health().equals("HP private"),"full roster private health projection");
        pages.accept(page("a",0,17));check(pages.accept(page("b",1,17))==null&&pages.restarted()&&pages.page()==0,"roster or role revision change restarts traversal");
        pages.accept(page("a",0,17));rejects(()->pages.accept(page("a",0,17)),"out-of-order page rejected");pages.clear();
        var duplicate=page("a",0,8);duplicate.getAsJsonArray("members").set(1,duplicate.getAsJsonArray("members").get(0));rejects(()->pages.accept(duplicate),"duplicate identities refused");pages.clear();
        JsonObject all=null;for(int i=0;i<128;i++)all=pages.accept(page("large",i,1024));check(all.getAsJsonArray("members").size()==1024,"maximum bounded roster drains all pages");
        pages.accept(page("a",0,9));pages.clear();check(pages.page()==0&&!pages.loading(),"campaign disconnect reset drops partial roster");check(pages.accept(page("empty",0,0)).getAsJsonArray("members").isEmpty(),"empty roster accepted");
    }
}
