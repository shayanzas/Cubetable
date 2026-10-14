package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.uiapi.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Deterministic wizard calculators, section persistence and authority-boundary checks. */
public final class WizardUiTests {
    private static int count;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);count++;}
    private static void rejects(Runnable run,String message){try{run.run();}catch(RuntimeException expected){count++;return;}throw new AssertionError(message);}
    private static JsonObject ok(UiApiService api,CharacterRepository.Principal p,UiApiService.Operation op,JsonObject args){var r=api.handle(p,op,args);check(r.code().equals("OK"),op+": "+r.message());return r.data();}
    public static void run() throws Exception {
        check(WizardModel.modifier(1)==-5&&WizardModel.modifier(9)==-1&&WizardModel.modifier(10)==0&&WizardModel.modifier(20)==5,"2014 ability modifiers floor correctly");
        int[] cost={0,1,2,3,4,5,7,9};for(int score=8;score<=15;score++)check(WizardModel.pointCost(score)==cost[score-8],"point cost "+score);
        rejects(()->WizardModel.pointCost(16),"point buy upper bound");
        var point=new LinkedHashMap<String,Integer>();for(String k:List.of("str","dex","con","int","wis","cha"))point.put(k,8);check(WizardModel.pointRemaining(point)==27,"empty point-buy budget");
        point.put("str",15);point.put("dex",15);point.put("con",15);point.put("int",8);point.put("wis",8);point.put("cha",8);check(WizardModel.pointRemaining(point)==0,"27 point allocation");WizardModel.validateScores(WizardModel.ScoreMethod.POINT_BUY,point);
        point.put("cha",9);rejects(()->WizardModel.validateScores(WizardModel.ScoreMethod.POINT_BUY,point),"point overspend rejected");
        var standard=new LinkedHashMap<String,Integer>();for(int i=0;i<6;i++)standard.put(List.of("str","dex","con","int","wis","cha").get(i),WizardModel.STANDARD_ARRAY.get(i));WizardModel.validateScores(WizardModel.ScoreMethod.STANDARD_ARRAY,standard);count++;
        standard.put("cha",10);rejects(()->WizardModel.validateScores(WizardModel.ScoreMethod.STANDARD_ARRAY,standard),"duplicate standard-array value rejected");
        check(Srd2014Catalog.SPECIES.size()==9&&Srd2014Catalog.CLASSES.size()==12&&Srd2014Catalog.BACKGROUNDS.size()==1,"bounded verified guidance catalogs");
        check(Srd2014Catalog.forStage(WizardModel.Stage.SPELLS).isEmpty(),"no fabricated standard spell catalog");
        var slots=WizardEditors.parseSlots("1:2/4, 3:1/2");check(slots.size()==2&&slots.get(1).getAsJsonObject().get("level").getAsInt()==3,"manual spell slots parse into typed records");rejects(()->WizardEditors.parseSlots("1:5/4"),"slot current cannot exceed maximum");rejects(()->WizardEditors.parseSlots("1:1/1,1:1/1"),"duplicate slot levels rejected");
        for(int[] size:new int[][]{{320,180},{427,240},{854,480}}){var frame=UiLayout.frame(size[0],size[1],720);var parts=WizardModel.layout(frame);for(var part:parts)if(part.width()>0)check(part.fits(size[0],size[1]),"wizard region fits "+Arrays.toString(size));check(parts[1].width()>0&&parts[1].height()>0,"main panel visible");}

        Path dir=Files.createTempDirectory("wizard-ui");var repo=new CharacterRepository(dir.resolve("campaigns.json"));var library=new LibraryRepository(dir.resolve("library.json"));var api=new UiApiService(repo,library,(op,a)->{throw new IllegalArgumentException("world unavailable");});var owner=new CharacterRepository.Principal(UUID.randomUUID(),true);var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
        JsonObject draft=ok(api,owner,LIBRARY_CREATE,args("profile",RulesProfiles.SRD,"mode","GUIDED","sheet",null));String id=text(draft,"id");check(text(draft,"stage").equals("DRAFT")&&text(draft,"mode").equals("GUIDED"),"guided draft created");
        var identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");identity.addProperty("species","Elf — High Elf");identity.addProperty("characterClass","Wizard");identity.addProperty("background","Acolyte");draft=ok(api,owner,LIBRARY_UPDATE,args("character",id,"revision",revision(draft),"section","identity","data",identity));
        var proficiency=SheetSections.raw(draft.getAsJsonObject("sheet"),"creation");proficiency.add("skills",WizardEditors.array("Arcana, Investigation",32));draft=ok(api,owner,LIBRARY_UPDATE,args("character",id,"revision",revision(draft),"section","creation","data",proficiency));
        var item=new SheetSections.Item("custom:book","","Spellbook",1,true,"carried",List.of("manual"),"record only","");var inventory=new SheetSections.Inventory(List.of(item),0,0,0,10,0);draft=ok(api,owner,LIBRARY_UPDATE,args("character",id,"revision",revision(draft),"section","inventory","data",SheetSections.JSON.toJsonTree(inventory)));
        var spell=new SheetSections.SpellDefinition("custom:light","Light",0,"Evocation","1 action","Touch","1 hour",true,false,true,"firefly",false,false,"Manual custom reference.","","custom/manual",true);var book=new SheetSections.Spellbook(List.of(spell),List.of(new SheetSections.SpellSelection(spell.id(),true,false,false,"custom/manual","")),List.of(),"INT",13,5);draft=ok(api,owner,LIBRARY_UPDATE,args("character",id,"revision",revision(draft),"section","spellbook","data",SheetSections.JSON.toJsonTree(book)));
        var saved=ok(api,owner,LIBRARY_GET,args("character",id));var status=WizardModel.statuses(saved);check(status.get(WizardModel.Stage.SPECIES).complete()&&status.get(WizardModel.Stage.CLASS).complete()&&status.get(WizardModel.Stage.BACKGROUND).complete(),"saved guided choices resume");check(status.get(WizardModel.Stage.EQUIPMENT).detail().contains("1")&&status.get(WizardModel.Stage.SPELLS).detail().contains("1"),"review counts structured records");
        check(SheetSections.read(saved.getAsJsonObject("sheet"),"inventory").equals(SheetSections.JSON.toJsonTree(inventory)),"inventory survives get/resume");check(SheetSections.read(saved.getAsJsonObject("sheet"),"spellbook").equals(SheetSections.JSON.toJsonTree(book)),"spellbook survives get/resume");
        var before=saved.deepCopy();saved=ok(api,owner,LIBRARY_UPDATE,args("character",id,"revision",revision(saved),"section","basics","data",args("name","Guided Hero")));check(needsEditReview(before,saved),"concurrent revision requires reconciliation");check(!api.handle(owner,LIBRARY_UPDATE,args("character",id,"revision",revision(before),"section","basics","data",args("name","Lost"))).code().equals("OK"),"stale wizard save rejected");count++;
        check(!api.handle(outsider,LIBRARY_GET,args("character",id)).code().equals("OK"),"unauthorized draft access rejected");count++;
        saved=ok(api,owner,LIBRARY_FINALIZE,args("character",id,"revision",revision(saved),"confirmedUnusedPoints",27));check(text(saved,"stage").equals("COMPLETE"),"finalize remains separate operation");
        var campaign=ok(api,owner,CAMPAIGN_CREATE,args("name","Wizard Campaign"));var submission=ok(api,owner,LIBRARY_SUBMIT,args("character",id,"revision",revision(saved),"campaign",text(campaign,"id"),"submission",UUID.randomUUID()));check(!text(submission,"id").equals(id)&&!flag(submission,"approved"),"submission creates independent unapproved copy");
        var sourceAfter=ok(api,owner,LIBRARY_GET,args("character",id));check(SheetSections.read(sourceAfter.getAsJsonObject("sheet"),"inventory").getAsJsonArray("items").size()==1,"campaign submission leaves source records intact");
        check(new LibraryRepository(dir.resolve("library.json")).get(owner,UUID.fromString(id)).revision()==revision(sourceAfter),"draft/final source survives repository restart");
        System.out.println("Wizard UI checks passed: "+count);
    }
}
