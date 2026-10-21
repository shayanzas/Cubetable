package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.combat.SrdActionBudget;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class GameplayTransactionTests {
    static int count;
    static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    interface Task{void run()throws Exception;}
    static void rejects(Task task,String why)throws Exception{try{task.run();}catch(IllegalArgumentException|UnsupportedOperationException|IOException expected){count++;return;}throw new AssertionError(why);}
    record Fixture(Path path,CharacterRepository repo,CharacterRepository.Principal dm,UUID campaign,UUID actor,UUID target){}
    static Fixture fixture(Path path)throws Exception{
        var repo=new CharacterRepository(path);var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);UUID campaign=repo.createCampaign(dm,"J2"),actor=UUID.randomUUID(),target=UUID.randomUUID();
        var sheet=SrdAuthoring.template();var data=SrdAuthoring.data(sheet);data.addProperty("arrows",2);data.add("weapons",JsonParser.parseString("[\"unarmed\",\"shortbow\"]"));data.add("weapon_proficiencies",JsonParser.parseString("[\"unarmed\",\"shortbow\"]"));
        for(UUID entity:List.of(actor,target)){UUID id=repo.submit(dm,campaign,RulesProfiles.SRD,sheet.toString());repo.approve(dm,id,1);repo.bind(dm,id,2,entity,null);}
        return new Fixture(path,repo,dm,campaign,actor,target);
    }
    static GameplayActionPlan plan(CharacterRepository repo,CharacterRepository.Principal dm,UUID campaign,UUID actor,UUID target,boolean ranged,boolean hit){
        var a=repo.srdSnapshot(actor,campaign);var b=repo.srdSnapshot(target,campaign);var spec=new GameplayActionPlan.Attack(ranged?SrdEquipment.Weapon.SHORTBOW:SrdEquipment.Weapon.UNARMED,ranged?SrdRules.AttackMode.RANGED:SrdRules.AttackMode.MELEE,SrdCharacter.Ability.DEX,ranged,0,false,false);
        if(!ranged)spec=new GameplayActionPlan.Attack(SrdEquipment.Weapon.UNARMED,SrdRules.AttackMode.MELEE,SrdCharacter.Ability.STR,false,0,false,false);
        var roll=SrdRules.attack(a.stats(),spec.weapon(),spec.mode(),spec.ability(),spec.twoHands(),b.stats().armorClass(),false,false,n->n==20?(hit?15:1):2);
        return GameplayActionPlan.weapon(UUID.randomUUID(),dm.id(),campaign,UUID.randomUUID(),1,a,repo.controller(a.character()),b,repo.controller(b.character()),spec,roll);
    }
    static GameplayActionPlan plan(Fixture f){return plan(f.repo,f.dm,f.campaign,f.actor,f.target,true,true);}
    public static void run()throws Exception{
        Path dir=Files.createTempDirectory("j2-durable-");try{
            for(boolean hit:List.of(false,true)){
                var f=fixture(dir.resolve("reaction-"+hit+".json"));
                var normal=plan(f.repo,f.dm,f.campaign,f.actor,f.target,false,hit);var reaction=normal.asReaction();
                check(normal.actionCost()==1&&normal.reactionCost()==0&&reaction.actionCost()==0&&reaction.reactionCost()==1,"versioned action versus reaction semantics");
                check(!normal.fingerprint().equals(reaction.fingerprint()),"cost semantics included in canonical fingerprint");
                rejects(()->plan(f).asReaction(),"ranged plan cannot become opportunity attack");
                f.repo.prepareGameplay(f.dm,reaction);var prepared=new CharacterRepository(f.path);
                check(prepared.gameplayReceipt(reaction.operation()).plan().equals(reaction)&&prepared.srdSnapshot(f.target,f.campaign).stats().hp()==10,"version2 prepared restart retains fixed outcome with unchanged HP");
                prepared.commitGameplay(f.dm,reaction.operation(),reaction.fingerprint());var loaded=new CharacterRepository(f.path);
                check(loaded.gameplayReceipt(reaction.operation()).plan().reactionCost()==1&&loaded.srdSnapshot(f.target,f.campaign).stats().hp()==(hit?9:10),"version2 committed restart preserves correct hit/miss");
                var bytes=Files.readString(f.path);loaded.commitGameplay(f.dm,reaction.operation(),reaction.fingerprint());check(Files.readString(f.path).equals(bytes),"reaction receipt replay has no second write");
                rejects(()->loaded.prepareGameplay(f.dm,normal),"same operation cannot switch reaction into normal action");
                rejects(()->new GameplayActionPlan(3,reaction.operation(),reaction.requester(),reaction.campaign(),reaction.encounter(),reaction.turn(),reaction.attacker(),reaction.target(),reaction.attack(),reaction.outcome(),null),"unknown future action version rejected");
            }

            for(boolean ranged:List.of(false,true))for(boolean hit:List.of(false,true)){
                var f=fixture(dir.resolve("parity-"+ranged+hit+".json"));var plan=plan(f.repo,f.dm,f.campaign,f.actor,f.target,ranged,hit);var before=f.repo.state();var prepared=f.repo.prepareGameplay(f.dm,plan);
                check(prepared.status()==GameplayReceipt.Status.PREPARED&&f.repo.state().characters().equals(before.characters()),"prepare changes no gameplay quantities");
                check(f.repo.reserved(plan.attacker().character())&&f.repo.reserved(plan.target().character()),"both participants reserved");
                rejects(()->f.repo.prepareGameplay(f.dm,plan(f)),"new operation cannot bypass reservation");
                rejects(()->f.repo.commitSrdHealth(f.repo.srdSnapshot(f.target,f.campaign),new SrdHealth(1,10,0)),"legacy health write cannot bypass reservation");
                var committed=f.repo.commitGameplay(f.dm,plan.operation(),plan.fingerprint());String bytes=Files.readString(f.path);
                check(committed.status()==GameplayReceipt.Status.COMMITTED&&!f.repo.reserved(plan.attacker().character()),"commit releases preparation reservation");
                check(f.repo.srdSnapshot(f.actor,f.campaign).stats().arrows()==(ranged?1:2),"hit/miss ammo charged once");
                check(f.repo.srdSnapshot(f.target,f.campaign).stats().hp()==plan.target().after().hp(),"fixed target HP applied");
                check(f.repo.commitGameplay(f.dm,plan.operation(),plan.fingerprint()).equals(committed)&&Files.readString(f.path).equals(bytes),"lost acknowledgement retry does not write again");
                check(f.repo.prepareGameplay(f.dm,plan).equals(committed),"same operation returns terminal receipt");
                var changed=new GameplayActionPlan(1,plan.operation(),plan.requester(),plan.campaign(),plan.encounter(),2,plan.attacker(),plan.target(),plan.attack(),plan.outcome(),null);
                rejects(()->f.repo.prepareGameplay(f.dm,changed),"same UUID altered payload rejected");rejects(()->f.repo.commitGameplay(f.dm,plan.operation(),"0".repeat(64)),"wrong fingerprint rejected");
                var restart=new CharacterRepository(f.path);check(restart.gameplayReceipt(plan.operation()).equals(committed)&&restart.srdSnapshot(f.actor,f.campaign).equals(f.repo.srdSnapshot(f.actor,f.campaign)),"restart keeps resource and receipt together");
                var stranger=new CharacterRepository.Principal(UUID.randomUUID(),false);rejects(()->restart.visibleReceipt(stranger,plan.operation()),"receipt private to authorized actor/DM");rejects(()->restart.commitGameplay(stranger,plan.operation(),plan.fingerprint()),"unauthorized committed replay cannot read result");
            }
            // Exercise both backup and primary boundaries, before temp write, after fsync, before rename.
            for(boolean primary:List.of(false,true))for(int stage=0;stage<3;stage++)for(boolean preparation:List.of(false,true)){
                var f=fixture(dir.resolve("fault-"+primary+stage+preparation+".json"));var plan=plan(f);if(!preparation)f.repo.prepareGameplay(f.dm,plan);
                String bytes=Files.readString(f.path);var armed=new boolean[]{true};int selected=stage;
                var failing=new CharacterRepository(new AtomicJsonFile(f.path,destination->{if(armed[0]&&selected==2&&destination.equals(f.path)==primary)throw new IOException("before replace");},(step,destination)->{if(armed[0]&&destination.equals(f.path)==primary&&(selected==0&&step==AtomicJsonFile.WriteStep.BEFORE_TEMP_WRITE||selected==1&&step==AtomicJsonFile.WriteStep.AFTER_TEMP_FORCE))throw new IOException("temp boundary");}));
                var before=failing.state();rejects(()->{if(preparation)failing.prepareGameplay(f.dm,plan);else failing.commitGameplay(f.dm,plan.operation(),plan.fingerprint());},"injected persistence interruption");
                check(failing.state()==before&&Files.readString(f.path).equals(bytes),"failed boundary publishes neither state nor bytes");var restart=new CharacterRepository(f.path);check(restart.state().characters().equals(before.characters()),"restart sees unchanged coherent quantities");
                check(preparation?restart.gameplayReceipt(plan.operation())==null:restart.gameplayReceipt(plan.operation()).plan().outcome().equals(plan.outcome()),"only durable prepared dice survive interruption");
                armed[0]=false;if(preparation)failing.prepareGameplay(f.dm,plan);failing.commitGameplay(f.dm,plan.operation(),plan.fingerprint());check(failing.srdSnapshot(f.actor,f.campaign).stats().arrows()==1,"exact fixed operation succeeds once after fault");
            }
            var f=fixture(dir.resolve("cancel.json"));var plan=plan(f);f.repo.prepareGameplay(f.dm,plan);var restart=new CharacterRepository(f.path);var before=restart.state().characters();var cancelled=restart.cancelGameplay(f.dm,plan.operation(),"DM cancellation after restart");check(cancelled.status()==GameplayReceipt.Status.CANCELLED&&cancelled.plan().outcome().equals(plan.outcome())&&restart.state().characters().equals(before),"audited cancellation keeps fixed rolls and quantities");rejects(()->restart.commitGameplay(f.dm,plan.operation(),plan.fingerprint()),"cancelled outcome cannot execute");check(!restart.reserved(plan.attacker().character()),"audited cancellation releases lock");
            var stale=plan(restart,f.dm,f.campaign,f.actor,f.target,true,true);restart.commitSrdHealth(restart.srdSnapshot(f.target,f.campaign),new SrdHealth(8,10,0));rejects(()->restart.prepareGameplay(f.dm,stale),"stale revisions cannot prepare");
            var raw=JsonParser.parseString(Files.readString(f.path)).getAsJsonObject();for(int version:List.of(1,2,3,4)){var old=raw.deepCopy();old.addProperty("schema",version);old.remove("receipts");if(version<4)old.remove("manualRevisions");if(version<3)old.remove("assignments");if(version==1)old.getAsJsonArray("campaigns").forEach(c->c.getAsJsonObject().remove("policy"));Path path=dir.resolve("schema-"+version+".json");Files.writeString(path,old.toString());var loaded=new CharacterRepository(path);check(loaded.state().characters().equals(restart.state().characters())&&loaded.state().bindings().equals(restart.state().bindings())&&loaded.state().receipts().isEmpty(),"all supported schemas retain character data and bindings");}
            for(String field:List.of("unknown","version")){var future=raw.deepCopy();var receipt=future.getAsJsonArray("receipts").get(0).getAsJsonObject();if(field.equals("version"))receipt.addProperty("version",99);else receipt.addProperty("unknown",true);Path path=dir.resolve("future-"+field+".json");Files.writeString(path,future.toString());Files.writeString(path.resolveSibling(path.getFileName()+".bak"),raw.toString());rejects(()->new CharacterRepository(path),"future transaction metadata refuses older backup rollback");}
            var budget=new SrdActionBudget();check(!budget.used()&&budget.supportedBonusActions()==0&&budget.supportedReactions()==0&&budget.supportedInteractions()==0,"unsupported action types not enabled");budget.reserve();rejects(budget::reserve,"cannot reserve twice");budget.committed();check(budget.used()&&budget.action()==SrdActionBudget.Action.SPENT,"committed action stays spent");
            crashTests(dir);capacityTest(dir);
        }finally{try(var files=Files.walk(dir)){for(var p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("J2 durable transaction checks passed: "+count);
    }
    private static void crashTests(Path dir)throws Exception{
        for(String phase:List.of("prepare","commit")){var f=fixture(dir.resolve("crash-"+phase+".json"));var command=List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",System.getProperty("java.class.path"),GameplayCrashHarness.class.getName(),f.path.toString(),f.dm.id().toString(),f.campaign.toString(),f.actor.toString(),f.target.toString(),phase);var child=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(dir.resolve("child-"+phase+".log").toFile()).start();check(child.waitFor(30,java.util.concurrent.TimeUnit.SECONDS)&&child.exitValue()==71,"child JVM abruptly exits at durable boundary");var loaded=new CharacterRepository(f.path);check(loaded.state().receipts().size()==1,"abrupt process termination retains receipt");var receipt=loaded.state().receipts().values().iterator().next();check(receipt.status()==(phase.equals("prepare")?GameplayReceipt.Status.PREPARED:GameplayReceipt.Status.COMMITTED),"crash retains exact durable phase");check(loaded.srdSnapshot(f.actor,f.campaign).stats().arrows()==(phase.equals("prepare")?2:1),"crash never partially spends stock");check(loaded.srdSnapshot(f.target,f.campaign).stats().hp()==(phase.equals("prepare")?10:receipt.plan().target().after().hp()),"crash target coherent with attacker");if(phase.equals("commit")){String bytes=Files.readString(f.path);loaded.commitGameplay(f.dm,receipt.plan().operation(),receipt.plan().fingerprint());check(Files.readString(f.path).equals(bytes),"post-crash lost ACK retry no second write");}}
    }
    private static void capacityTest(Path dir)throws Exception{
        var f=fixture(dir.resolve("capacity.json"));var plan=plan(f);f.repo.prepareGameplay(f.dm,plan);f.repo.commitGameplay(f.dm,plan.operation(),plan.fingerprint());var root=JsonParser.parseString(Files.readString(f.path)).getAsJsonObject();var rows=new JsonArray();var json=new GsonBuilder().serializeNulls().create();rows.add(json.toJsonTree(f.repo.gameplayReceipt(plan.operation())));
        for(int i=1;i<CharacterRepository.MAX_GAMEPLAY_RECEIPTS;i++){var copy=new GameplayActionPlan(1,UUID.randomUUID(),plan.requester(),plan.campaign(),plan.encounter(),i+1,plan.attacker(),plan.target(),plan.attack(),plan.outcome(),null);rows.add(json.toJsonTree(GameplayReceipt.prepared(copy).committed()));}root.add("receipts",rows);Files.writeString(f.path,root.toString());var loaded=new CharacterRepository(f.path);var next=plan(loaded,f.dm,f.campaign,f.actor,f.target,true,true);String before=Files.readString(f.path);rejects(()->loaded.prepareGameplay(f.dm,next),"bounded full history fails closed rather than evicting");check(loaded.state().receipts().size()==CharacterRepository.MAX_GAMEPLAY_RECEIPTS&&Files.readString(f.path).equals(before),"capacity attempt cannot prune history");check(loaded.commitGameplay(f.dm,plan.operation(),plan.fingerprint()).status()==GameplayReceipt.Status.COMMITTED&&Files.readString(f.path).equals(before),"old receipt still replay-safe at capacity");
    }
}
