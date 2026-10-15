package dev.tacticalcombat.character;

import dev.tacticalcombat.LegacyRulesParityTests;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.client.ui.RulesRefreshState;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public final class RulesProjectionTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    private interface Task {void run() throws Exception;}
    private static void rejects(Task task,String why)throws Exception{try{task.run();}catch(IllegalArgumentException|IllegalStateException|UnsupportedOperationException expected){count++;return;}throw new AssertionError(why);}
    public static void run() throws Exception {
        for(var sheet:LegacyRulesParityTests.armorFixtures()){
            var old=SrdCharacterCodec.parseSheet(sheet.toString());var rules=CharacterRules.read(sheet.toString());
            check(rules.inputs().equals(old),"adapter preserves all typed inputs");
            check(rules.armorClass()==old.armorClass()&&rules.walkingSpeedFeet()==old.walkingSpeedFeet()&&rules.proficiency()==old.proficiency(),"legacy derived parity");
            for(var ability:SrdCharacter.Ability.values())check(rules.modifiers().get(ability)==old.modifier(ability),"ability parity");
            check(rules.initiative(n->11).equals(SrdRules.initiative(old,n->11)),"initiative parity");
            for(var weapon:SrdEquipment.Weapon.values())for(var mode:SrdRules.AttackMode.values())for(var ability:List.of(SrdCharacter.Ability.STR,SrdCharacter.Ability.DEX))for(boolean hands:List.of(false,true)){
                SrdRules.AttackPlan plan;
                try{plan=SrdRules.plan(old,weapon,mode,ability,hands);}catch(IllegalArgumentException e){rejects(()->rules.plan(weapon,mode,ability,hands),"same rejected attack boundary");continue;}
                check(rules.plan(weapon,mode,ability,hands).equals(plan),"attack plan parity");
                check(rules.attack(weapon,mode,ability,hands,17,true,true,n->Math.min(10,n)).equals(SrdRules.attack(old,weapon,mode,ability,hands,17,true,true,n->Math.min(10,n))),"same attack RNG and damage");
            }
        }
        var sheet=LegacyRulesParityTests.fixture();var rules=CharacterRules.read(sheet.toString());
        rejects(()->rules.modifiers().clear(),"immutable modifiers");rejects(()->rules.provenance().get("ac").clear(),"immutable nested provenance");
        var invalid=sheet.deepCopy();SrdAuthoring.data(invalid).addProperty("version",2);rejects(()->CharacterRules.read(invalid.toString()),"unknown version rejected");
        var progression=SheetSections.raw(sheet,"progression");progression.addProperty("recordedLevel",9);sheet=SheetSections.update(sheet,"progression",progression);
        var read=RulesReadModel.of(rules,sheet);check(read.get("levelMismatch").getAsBoolean()&&read.get("effectiveLevel").getAsInt()==5,"recorded level discrepancy without advancement");
        check(!read.get("checksExecutable").getAsBoolean()&&!read.get("inventoryIntegrated").getAsBoolean(),"informational boundaries");
        Path dir=Files.createTempDirectory("j1-projection-");
        try{
            Path path=dir.resolve("campaign.json");var repo=new CharacterRepository(path);var library=new LibraryRepository(dir.resolve("library.json"));
            var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
            UUID campaign=repo.createCampaign(dm,"J1"),actor=UUID.randomUUID();UUID id=repo.submit(dm,campaign,RulesProfiles.SRD,sheet.toString());
            rejects(()->repo.bind(dm,id,1,actor,null),"approval required");repo.approve(dm,id,1);repo.bind(dm,id,2,actor,null);
            var snapshot=repo.srdSnapshot(actor,campaign);check(snapshot.rules()==repo.srdSnapshot(actor,campaign).rules(),"unchanged projection cached");
            var service=new CharacterSyncService(repo);var view=service.snapshot(dm,id).getAsJsonObject("rules");
            check(view.get("ac").getAsInt()==snapshot.rules().armorClass()&&view.get("hp").getAsInt()==snapshot.rules().resources().hp(),"sheet and encounter share projection");
            var displayed=dev.tacticalcombat.client.ui.DashboardModel.combat(service.snapshot(dm,id),false);
            check(displayed.stream().anyMatch(stat->stat.label().equals("Armor Class")&&stat.value().equals(Integer.toString(snapshot.rules().armorClass()))),"dashboard displays authoritative AC");
            check(dev.tacticalcombat.client.ui.DashboardModel.modifier(service.snapshot(dm,id),"int")==-5,"dashboard uses negative server modifier");
            rejects(()->service.snapshot(outsider,id),"private read rejected");rejects(()->repo.srdSnapshot(actor,UUID.randomUUID()),"cache cannot bypass campaign scope");
            var watch=new CharacterWatchService(repo,library);UUID token=UUID.randomUUID();watch.watch(dm,token,false,id,CharacterWatchService.campaignStamp(repo,dm,id));
            check(watch.drain(viewer->dm).isEmpty(),"current sheet has no redundant notification");rejects(()->watch.watch(outsider,UUID.randomUUID(),false,id,""),"unauthorized watch denied");
            long generation=repo.viewGeneration();var updated=repo.commitSrdHealth(snapshot,new SrdHealth(12,37,2));
            check(repo.viewGeneration()>generation&&updated.rules()!=snapshot.rules(),"acknowledged resources invalidate projection");
            check(watch.drain(viewer->dm).equals(List.of(new CharacterWatchService.Notice(dm.id(),token,false))),"targeted resource invalidation");check(watch.drain(viewer->dm).isEmpty(),"notification coalesced");
            rejects(()->repo.commitSrdHealth(snapshot,new SrdHealth(1,37,0)),"stale resource write rejected");check(watch.drain(viewer->dm).isEmpty(),"rejected mutation publishes no notification");
            var failed=new CharacterRepository(new AtomicJsonFile(path,destination->{if(destination.equals(path))throw new IOException("injected");}));var before=failed.srdSnapshot(actor,campaign);long beforeGeneration=failed.viewGeneration();
            boolean fault=false;try{failed.commitSrdHealth(before,new SrdHealth(1,37,0));}catch(IOException expected){fault=true;}
            check(fault&&failed.viewGeneration()==beforeGeneration&&failed.srdSnapshot(actor,campaign).rules()==before.rules(),"failed persistence keeps cached state and generation");
            check(new CharacterRepository(path).srdSnapshot(actor,campaign).equals(updated),"old schema reload no migration");
            UUID other=repo.submit(dm,campaign,RulesProfiles.SRD,sheet.toString());repo.approve(dm,other,1);check(watch.drain(viewer->dm).isEmpty(),"unrelated character changes do not notify");
            check(repo.srdRules(repo.character(other)).resources().hp()==19,"independent copy health");
            UUID tactical=repo.submit(dm,campaign,RulesProfiles.TACTICAL,"{\"format\":\"generic_d20\"}");check(!service.snapshot(dm,tactical).has("rules"),"Tactical isolated");
            repo.unbind(dm,actor);check(watch.drain(viewer->dm).size()==1,"unbind invalidates sheet");rejects(()->repo.srdSnapshot(actor,campaign),"unbind cannot reuse cached encounter");
            watch.watch(dm,token,false,id,"stale read");check(watch.drain(viewer->dm).size()==1,"subscribe catches change between read and watch");
            watch.close(dm.id(),UUID.randomUUID());check(watch.size()==1,"old token cannot close current watch");watch.drain(viewer->null);check(watch.size()==0,"disconnect clears watch");
            var draft=library.create(dm,RulesProfiles.SRD,SheetSections.Mode.MANUAL,sheet.toString());
            watch.watch(dm,token,true,draft.id(),CharacterWatchService.libraryStamp(library,dm,draft.id()));check(watch.drain(viewer->dm).isEmpty(),"library watch starts current");
            rejects(()->watch.watch(outsider,UUID.randomUUID(),true,draft.id(),""),"private library watch denied");
            var name=new com.google.gson.JsonObject();name.addProperty("name","Updated personal character");library.update(dm,draft.id(),1,"basics",name);
            check(watch.drain(viewer->dm).size()==1,"library save invalidates sheet");library.delete(dm,draft.id(),2);
            check(watch.drain(viewer->dm).getFirst().revoked(),"library deletion revokes open sheet");
            var elevated=new CharacterRepository.Principal(outsider.id(),true);watch.watch(elevated,token,false,id,CharacterWatchService.campaignStamp(repo,elevated,id));watch.drain(viewer->elevated);
            check(watch.drain(viewer->outsider).equals(List.of(new CharacterWatchService.Notice(outsider.id(),token,true))),"permission revocation sends opaque notice");
        }finally{try(var files=Files.walk(dir)){for(var p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        var refresh=new RulesRefreshState();refresh.changed();check(!refresh.shouldRefresh(false,true),"unsaved edits defer auto refresh");check(!refresh.shouldRefresh(true,false),"busy read coalesces");long observed=refresh.begin();refresh.changed();refresh.completed(observed);check(refresh.shouldRefresh(false,false),"change during read remains pending");refresh.failed();check(!refresh.shouldRefresh(false,false),"failed read does not loop");refresh.completed(refresh.begin());check(!refresh.pending(),"manual refresh clears pending");refresh.changed();refresh.revoked();check(!refresh.shouldRefresh(false,false),"revocation stops retries");
        System.out.println("J1 projection and refresh checks passed: "+count);
    }
}
