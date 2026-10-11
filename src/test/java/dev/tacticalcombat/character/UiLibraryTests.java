package dev.tacticalcombat.character;
import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.util.*;

public final class UiLibraryTests {
    private static int count;
    static void check(boolean ok,String msg) {if(!ok) throw new AssertionError(msg);count++;}
    interface Action {void run() throws Exception;}
    static void rejects(Action action,String msg) throws Exception {try {action.run();} catch(Exception expected) {count++;return;}throw new AssertionError(msg);}
    public static void run() throws Exception {
        var dir=Files.createTempDirectory("ui-library");var path=dir.resolve("library.json");var p=new CharacterRepository.Principal(UUID.randomUUID(),false);
        var stranger=new CharacterRepository.Principal(UUID.randomUUID(),true);var repo=new LibraryRepository(path);
        var sheet=SrdAuthoring.template();sheet.getAsJsonObject("text").addProperty("name","");
        var d=repo.create(p,RulesProfiles.SRD,SheetSections.Mode.GUIDED,sheet.toString());
        check(d.stage()==LibraryRepository.Stage.DRAFT,"incomplete draft persisted");check(repo.validate(p,d.id()).size()==1,"human-readable missing-name error");
        rejects(()->repo.finalizeDraft(p,d.id(),1),"cannot finalize incomplete");rejects(()->repo.get(stranger,d.id()),"even admin cannot browse unrelated private library");
        var name=new JsonObject();name.addProperty("name","Café 🐉");var named=repo.update(p,d.id(),1,"basics",name);
        rejects(()->repo.update(p,d.id(),1,"basics",name),"stale edits rejected");
        var ready=repo.finalizeDraft(p,d.id(),named.revision());check(ready.stage()==LibraryRepository.Stage.COMPLETE,"finalization durable");
        var copy=repo.duplicate(p,ready.id(),ready.revision());check(!copy.id().equals(ready.id()) && copy.stage()==LibraryRepository.Stage.DRAFT,"duplicate independent draft");
        check(new LibraryRepository(path).get(p,ready.id()).equals(ready),"normal restart preserves exact identity/revision");
        for(var entry:SheetSections.defaults().entrySet()) {
            var data=SheetSections.JSON.toJsonTree(entry.getValue()).getAsJsonObject();
            var updated=repo.update(p,copy.id(),repo.get(p,copy.id()).revision(),entry.getKey(),data);
            check(SheetSections.read(JsonParser.parseString(updated.sheet()).getAsJsonObject(),entry.getKey()).equals(data),"typed section roundtrip "+entry.getKey());
            data.addProperty("approved",true);rejects(()->repo.update(p,copy.id(),repo.get(p,copy.id()).revision(),entry.getKey(),data),"unknown authority field rejected");
        }
        var pool=new JsonObject();pool.addProperty("id","custom:rage");pool.addProperty("name","Rage");pool.addProperty("current",2);pool.addProperty("maximum",1);pool.addProperty("source","custom");pool.addProperty("recharge","long rest");pool.addProperty("status","MANUAL");
        rejects(()->SheetSections.parse(pool,SheetSections.Pool.class),"resource bounds");pool.addProperty("maximum",3);pool.addProperty("status","AUTOMATED");rejects(()->SheetSections.parse(pool,SheetSections.Pool.class),"manual records cannot activate rules");
        var corrupt=dir.resolve("corrupt.json");Files.copy(path,corrupt);Files.copy(path,dir.resolve("corrupt.json.bak"));Files.writeString(corrupt,"bad");
        check(new LibraryRepository(corrupt).recoveredFromBackup(),"valid backup recovery");Files.delete(corrupt);check(new LibraryRepository(corrupt).get(p,ready.id()).equals(ready),"missing primary recovery");
        Files.writeString(corrupt,"{\"schema\":99}");rejects(()->new LibraryRepository(corrupt),"future schema refuses rollback");Files.writeString(corrupt,"bad");Files.writeString(dir.resolve("corrupt.json.bak"),"bad");rejects(()->new LibraryRepository(corrupt),"both invalid fail closed");
        var failedPath=dir.resolve("failed.json");var failing=new LibraryRepository(new AtomicJsonFile(failedPath,destination->{throw new java.io.IOException("injected");}));
        rejects(()->failing.create(p,RulesProfiles.SRD,SheetSections.Mode.MANUAL,SrdAuthoring.template().toString()),"interrupted write");check(failing.list(p,0).isEmpty(),"failed save never publishes");
        var oldCampaign=new CharacterRepository(dir.resolve("campaign.json"));var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);var campaign=oldCampaign.createCampaign(dm,"Existing");
        var id=oldCampaign.submit(dm,campaign,RulesProfiles.SRD,ready.sheet());oldCampaign.approve(dm,id,1);var actor=UUID.randomUUID();oldCampaign.bind(dm,id,2,actor,null);
        check(new CharacterRepository(dir.resolve("campaign.json")).srdSnapshot(actor,campaign).character().equals(id),"legacy approved binding survives additive schema");
        var future=JsonParser.parseString(Files.readString(path)).getAsJsonObject();var row=future.getAsJsonArray("drafts").get(0).getAsJsonObject();var futureSheet=JsonParser.parseString(row.get("sheet").getAsString()).getAsJsonObject();
        var wrapper=new JsonObject();wrapper.addProperty("version",99);wrapper.add("data",new JsonObject());var rows=new JsonArray();rows.add(wrapper);futureSheet.getAsJsonObject("collections").add("ui_identity",rows);row.addProperty("sheet",futureSheet.toString());
        var futurePath=dir.resolve("future.json");Files.writeString(futurePath,future.toString());Files.copy(path,dir.resolve("future.json.bak"));rejects(()->new LibraryRepository(futurePath),"future section refuses rollback even with old valid backup");
        System.out.println("UI library checks passed: "+count);
    }
}
