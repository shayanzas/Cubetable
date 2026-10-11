package dev.tacticalcombat.character;
import dev.tacticalcombat.uiapi.*;
import java.nio.file.*;
import java.util.*;
public final class UiPresentationTests {
    private static int count;
    static void check(boolean v,String m) {if(!v) throw new AssertionError(m);count++;}
    interface Action {void run() throws Exception;}
    static void rejects(Action run,String m) throws Exception {try {run.run();} catch(Exception expected) {count++;return;}throw new AssertionError(m);}
    public static void run() throws Exception {
        var dir=Files.createTempDirectory("ui-presentation");var repo=new CharacterRepository(dir.resolve("campaign.json"));
        var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);var p=new CharacterRepository.Principal(UUID.randomUUID(),false);var other=new CharacterRepository.Principal(UUID.randomUUID(),false);var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
        UUID campaign=repo.createCampaign(dm,"Events");repo.invite(dm,campaign,1,p.id(),false);repo.invite(dm,campaign,2,other.id(),false);
        var events=new UiEvents();var roll=UiEvents.Roll.from(20,List.of(18,7),1,5,"Attack",false);check(roll.total()==23 && roll.faces().get(0)==18 && roll.kept().equals(List.of(0)),"raw and final totals remain distinct");
        check(UiEvents.Roll.from(20,List.of(18,7),2,-2,"Attack",false).total()==5,"disadvantage and negative modifier");
        rejects(()->new UiEvents.Roll(20,List.of(18),List.of(0),5,18,"Bad",false),"forged total rejected");
        rejects(()->UiEvents.Roll.from(20,List.of(21),0,0,"Bad",false),"invalid face rejected");
        UUID id=UUID.randomUUID();check(events.publish(id,campaign,null,1,UiEvents.Kind.ATTACK,List.of(p.id()),"Private attack",roll,UiEvents.Visibility.RECIPIENTS_AND_DM,Set.of(p.id())),"first event admitted");
        check(!events.publish(id,campaign,null,1,UiEvents.Kind.ATTACK,List.of(p.id()),"Duplicate",roll,UiEvents.Visibility.CAMPAIGN,Set.of()),"stable event identity deduplicates");
        check(events.query(repo,p,campaign,0,null).events().size()==1 && events.query(repo,dm,campaign,0,null).events().size()==1,"owner and DM see private event");
        check(events.query(repo,other,campaign,0,null).events().isEmpty(),"another member cannot inspect private roll or modifiers");rejects(()->events.query(repo,outsider,campaign,0,null),"unrelated campaign denied");
        for(int i=0;i<520;i++) events.publish(UUID.randomUUID(),campaign,null,0,UiEvents.Kind.TURN,List.of(),"Turn",null,UiEvents.Visibility.CAMPAIGN,Set.of());
        var page=events.query(repo,p,campaign,1,null);check(page.events().size()==16 && page.historyLost(),"bounded history and page loss signaled");check(page.events().getFirst().sequence()<page.events().getLast().sequence(),"events ordered");
        check(events.query(repo,p,campaign,page.cursor(),UiEvents.Kind.ATTACK).events().isEmpty(),"kind filter and cursor");var session=page.session();events.clear();check(!events.query(repo,p,campaign,0,null).session().equals(session),"restart/reset has new cursor session");
        var path=dir.resolve("workflows.json");var store=new UiWorkflowStore(path);check(store.policy(campaign).hp()==UiWorkflowStore.HpVisibility.PRIVATE,"private party HP default");
        rejects(()->store.policy(repo,p,new UiWorkflowStore.Policy(campaign,1,UiWorkflowStore.HpVisibility.EXACT,16)),"ordinary member cannot share party HP");
        store.policy(repo,dm,new UiWorkflowStore.Policy(campaign,1,UiWorkflowStore.HpVisibility.BARS,16));
        var rest=store.request(repo,p,UUID.randomUUID(),campaign,UiWorkflowStore.RestKind.SHORT,List.of(p.id(),other.id()),"minecraft:overworld",0,64,0,1000);
        check(store.request(repo,p,rest.id(),campaign,rest.kind(),rest.participants(),rest.dimension(),0,64,0,1001).equals(rest),"durable rest identity retry");
        rejects(()->store.review(repo,p,rest.id(),1,true,1002),"player cannot approve own rest");
        rejects(()->store.review(repo,dm,rest.id(),2,true,1002),"stale rest review");
        check(store.review(repo,dm,rest.id(),1,true,1002).state()==UiWorkflowStore.RestState.APPROVED_FOR_ADJUDICATION,"approval never claims recovery or active rest");
        check(new UiWorkflowStore(path).get(repo,p,rest.id()).state()==UiWorkflowStore.RestState.APPROVED_FOR_ADJUDICATION,"rest approval persists");
        var expiring=store.request(repo,p,UUID.randomUUID(),campaign,UiWorkflowStore.RestKind.LONG,List.of(p.id()),"minecraft:overworld",0,64,0,2000);
        check(store.review(repo,dm,expiring.id(),1,true,expiring.expires()).state()==UiWorkflowStore.RestState.EXPIRED,"expired approval cannot activate workflow");
        rejects(()->store.get(repo,outsider,rest.id()),"rest privacy");
        var preferences=HudPreferences.defaults();var prefPath=dir.resolve("hud.json");preferences.save(prefPath);check(HudPreferences.load(prefPath).equals(preferences),"local HUD settings roundtrip");
        rejects(()->new HudPreferences.Panel(Double.NaN,0,1,true),"invalid layout bounds");
        check(HudPreferences.defaults().equals(preferences),"reset-to-default is deterministic");
        var failed=new UiWorkflowStore(new AtomicJsonFile(dir.resolve("failed.json"),destination->{throw new java.io.IOException("injected failure");}));
        rejects(()->failed.policy(repo,dm,new UiWorkflowStore.Policy(campaign,1,UiWorkflowStore.HpVisibility.EXACT,16)),"workflow write failure is surfaced");
        check(failed.policy(campaign).revision()==0 && failed.policy(campaign).hp()==UiWorkflowStore.HpVisibility.PRIVATE,"failed policy write cannot publish sharing permission");
        System.out.println("UI presentation/workflow checks passed: "+count);
    }
}
