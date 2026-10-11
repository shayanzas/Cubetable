package dev.tacticalcombat.character;
import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
import static dev.tacticalcombat.character.UiLifecycleTests.args;
public final class UiRpgRecordTests {
    private static int count;
    static void check(boolean v,String m) {if(!v) throw new AssertionError(m);count++;}
    static void rejects(Runnable run,String m) {try {run.run();} catch(RuntimeException expected) {count++;return;}throw new AssertionError(m);}
    public static void run() throws Exception {
        var dir=Files.createTempDirectory("ui-rpg");var path=dir.resolve("library.json");var library=new LibraryRepository(path);var repo=new CharacterRepository(dir.resolve("campaign.json"));
        var p=new CharacterRepository.Principal(UUID.randomUUID(),true);var d=library.create(p,RulesProfiles.SRD,SheetSections.Mode.MANUAL,SrdAuthoring.template().toString());
        var original=SrdCharacterCodec.parseSheet(d.sheet());
        var spell=new SheetSections.SpellDefinition("custom:glimmer","Glimmer",0,"Evocation","1 action","30 feet","1 minute",true,true,false,"",false,false,"A custom recorded spell, without effects.","","homebrew-v1",true);
        var book=new SheetSections.Spellbook(List.of(spell),List.of(new SheetSections.SpellSelection(spell.id(),true,true,false,"manual","")),List.of(new SheetSections.Slot(1,2,2,"custom")),"INT",13,5);
        var data=SheetSections.JSON.toJsonTree(book).getAsJsonObject();d=library.update(p,d.id(),d.revision(),"spellbook",data);
        check(SheetSections.read(JsonParser.parseString(d.sheet()).getAsJsonObject(),"spellbook").equals(data),"custom definition and selection roundtrip");
        rejects(()->new SheetSections.Spellbook(List.of(),book.selections(),List.of(),"",0,0),"unknown standard spell references rejected");
        rejects(()->new SheetSections.SpellSelection("custom:x",false,true,false,"",""),"prepared eligibility structural check");
        rejects(()->new SheetSections.SpellDefinition("srd:invented","Invented",0,"","","","",false,false,false,"",false,false,"","","5.1",false),"client cannot forge standard catalog definitions");
        var pool=new SheetSections.Pool("custom:wind","Second Wind",1,1,"manual","short rest",SheetSections.Automation.MANUAL);
        var trackers=new SheetSections.Trackers(false,0,0,0,List.of(new SheetSections.HitDice(10,2,2,"fighter")),List.of(pool));
        d=library.update(p,d.id(),d.revision(),"trackers",SheetSections.JSON.toJsonTree(trackers).getAsJsonObject());
        var api=new UiApiService(repo,library,(op,a)->{throw new IllegalArgumentException("unavailable");});
        for(var action:List.of(new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"trackers","",0,1),new RpgRecords.Change(RpgRecords.Action.DEATH_SUCCESSES,"trackers","",0,3),new RpgRecords.Change(RpgRecords.Action.DEATH_FAILURES,"trackers","",0,2),new RpgRecords.Change(RpgRecords.Action.EXHAUSTION,"trackers","",0,6),new RpgRecords.Change(RpgRecords.Action.HIT_DICE,"trackers","",0,1),new RpgRecords.Change(RpgRecords.Action.SPELL_SLOT,"spellbook","",0,1),new RpgRecords.Change(RpgRecords.Action.RESOURCE,"trackers","custom:wind",0,0))) {
            var reply=api.handle(p,MANUAL_UPDATE,args("scope","LIBRARY","character",d.id(),"revision",d.revision(),"runtimeRevision",0,"change",action));check(reply.code().equals("OK"),"manual tracker operation: "+reply.message());d=library.get(p,d.id());
        }
        var saved=new LibraryRepository(path).get(p,d.id());check(saved.equals(d),"manual trackers persist on restart");check(SrdCharacterCodec.parseSheet(saved.sheet()).equals(original),"manual data never changes automated combat inputs");
        var finalSheet=JsonParser.parseString(saved.sheet()).getAsJsonObject();
        rejects(()->RpgRecords.apply(finalSheet,new RpgRecords.Change(RpgRecords.Action.DEATH_SUCCESSES,"trackers","",0,4)),"death saves bounded to three");
        rejects(()->RpgRecords.apply(finalSheet,new RpgRecords.Change(RpgRecords.Action.SPELL_SLOT,"spellbook","",0,3)),"slots cannot exceed maximum");
        rejects(()->RpgRecords.apply(finalSheet,new RpgRecords.Change(RpgRecords.Action.RESOURCE,"trackers","custom:wind",0,-1)),"resource cannot be negative");
        var item=new SheetSections.Item("custom:sword","","Homebrew sword",1,true,"main_hand",List.of("recorded only"),"","minecraft:diamond_sword");
        var inventory=new SheetSections.Inventory(List.of(item),1,2,3,4,5);var withInventory=SheetSections.update(finalSheet,"inventory",SheetSections.JSON.toJsonTree(inventory));check(SrdCharacterCodec.parseSheet(withInventory.toString()).equals(original),"equipment metadata does not grant combat weapons");
        var feature=new SheetSections.Feature("custom:feat",SheetSections.FeatureKind.FEAT,"Recorded feat","","homebrew",SheetSections.Automation.INFORMATIONAL,null,null,null,List.of());
        var features=new SheetSections.Features(List.of(feature),List.of(pool));check(SheetSections.read(SheetSections.update(finalSheet,"features",SheetSections.JSON.toJsonTree(features)),"features").equals(SheetSections.JSON.toJsonTree(features)),"feature optional metadata roundtrip");
        var campaign=repo.createCampaign(p,"Records");var id=repo.submit(p,campaign,RulesProfiles.SRD,withInventory.toString());repo.approve(p,id,1);repo.bind(p,id,2,UUID.randomUUID(),null);
        check(!api.handle(p,MANUAL_UPDATE,args("scope","CAMPAIGN","character",id,"revision",2,"runtimeRevision",0,"change",new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"trackers","",0,0))).code().equals("OK"),"bound character authority not bypassed by manual updates");
        check(api.handle(p,SPELL_CATALOG,args("query","","page",0)).data().get("total").getAsInt()==0,"unverified standard catalog explicitly empty");
        System.out.println("UI RPG record checks passed: "+count);
    }
}
