package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import dev.tacticalcombat.uiapi.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Exercises the actual presentation predicates and server operations used by management screens. */
public final class ManagementUiTests {
    private static int count;
    private static void check(boolean ok,String message) {if(!ok) throw new AssertionError(message);count++;}
    private static JsonObject ok(UiApiService api,CharacterRepository.Principal p,UiApiService.Operation op,JsonObject args) {var r=api.handle(p,op,args);check(r.code().equals("OK"),op+": "+r.message());return r.data();}
    private static void denied(UiApiService api,CharacterRepository.Principal p,UiApiService.Operation op,JsonObject args) {check(!api.handle(p,op,args).code().equals("OK"),"Unauthorized/stale "+op);}
    public static void run() throws Exception {
        var root=Files.createTempDirectory("management-ui");var repo=new CharacterRepository(root.resolve("campaigns.json"));var library=new LibraryRepository(root.resolve("library.json"));
        var admin=new CharacterRepository.Principal(UUID.randomUUID(),true);var owner=new CharacterRepository.Principal(UUID.randomUUID(),false);var coDm=new CharacterRepository.Principal(UUID.randomUUID(),false);var recipient=new CharacterRepository.Principal(UUID.randomUUID(),false);var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
        var api=new UiApiService(repo,library,(op,a)->{throw new IllegalArgumentException("World operation unavailable in dependency-free tests");});
        var c=ok(api,admin,CAMPAIGN_CREATE,args("name","Campaign One"));String campaign=text(c,"id");
        check(manager(c) && flag(c,"canManageRoles"),"authenticated admin can manage roles");
        ok(api,admin,CAMPAIGN_INVITE,args("campaign",campaign,"revision",1,"player",owner.id(),"dm",false));
        ok(api,admin,CAMPAIGN_INVITE,args("campaign",campaign,"revision",2,"player",coDm.id(),"dm",true));
        ok(api,admin,CAMPAIGN_INVITE,args("campaign",campaign,"revision",3,"player",recipient.id(),"dm",false));
        var privateCampaign=ok(api,admin,CAMPAIGN_CREATE,args("name","Unrelated campaign"));
        var memberCampaign=ok(api,owner,CAMPAIGN_GET,args("campaign",campaign));check(!manager(memberCampaign) && !flag(memberCampaign,"canManageRoles") && !memberCampaign.has("members"),"member has read-only campaign projection");
        var managerCampaign=ok(api,coDm,CAMPAIGN_GET,args("campaign",campaign));check(manager(managerCampaign) && !flag(managerCampaign,"canManageRoles"),"co-DM cannot appoint or remove other co-DMs");
        denied(api,coDm,CAMPAIGN_CREATE,args("name","Unauthorized DM creation"));denied(api,owner,CAMPAIGN_CREATE,args("name","Unauthorized creation"));
        denied(api,owner,CAMPAIGN_GET,args("campaign",text(privateCampaign,"id")));
        var visible=ok(api,owner,CAMPAIGN_LIST,args("page",0));check(array(visible,"campaigns").size()==1,"campaign filter excludes unrelated memberships");
        var choices=campaigns(array(visible,"campaigns"),campaign);check(choices.getFirst().detail().contains("selected") && choices.getFirst().enabled(),"ordinary browser opens selected member campaign");check(HubState.filter(choices,"ONE").size()==1,"case-insensitive local search");check(HubState.filter(choices,"absent").isEmpty(),"no matches distinct from authorized empty list");
        var policy=memberCampaign.getAsJsonObject("policy").deepCopy();policy.addProperty("requireNewApproval",false);
        denied(api,owner,CAMPAIGN_POLICY,args("campaign",campaign,"revision",4,"policy",policy));
        denied(api,coDm,CAMPAIGN_INVITE,args("campaign",campaign,"revision",4,"player",outsider.id(),"dm",true));
        var d=ok(api,owner,LIBRARY_CREATE,args("profile","srd_5_1","mode","GUIDED","sheet",null));String source=text(d,"id");
        check(text(d,"stage").equals("DRAFT") && text(d,"mode").equals("GUIDED"),"persistent guided scaffold is explicitly a draft");
        check(array(ok(api,outsider,LIBRARY_LIST,args("page",0)),"characters").isEmpty(),"private libraries not listed to outsiders");denied(api,admin,LIBRARY_GET,args("character",source));
        d=ok(api,owner,LIBRARY_UPDATE,args("character",source,"revision",1,"section","basics","data",args("name","")));
        var validation=ok(api,owner,LIBRARY_VALIDATE,args("character",source));check(ManagementState.validation(validation).contains("Enter a character name"),"readable readiness errors");
        denied(api,owner,LIBRARY_FINALIZE,args("character",source,"revision",2));
        var stale=d.deepCopy();d=ok(api,owner,LIBRARY_UPDATE,args("character",source,"revision",2,"section","basics","data",args("name","Pierre")));
        check(needsEditReview(stale,d),"concurrent editing revision requires explicit reconciliation");check(!needsEditReview(d,d.deepCopy()),"same revision needs no overwrite review");
        denied(api,owner,LIBRARY_UPDATE,args("character",source,"revision",2,"section","basics","data",args("name","Lost unsaved text")));
        check(text(ok(api,owner,LIBRARY_GET,args("character",source)),"name").equals("Pierre"),"stale UI cannot silently overwrite saved name");
        d=ok(api,owner,LIBRARY_FINALIZE,args("character",source,"revision",3));check(text(d,"stage").equals("COMPLETE"),"server finalized supported draft");
        var duplicate=ok(api,owner,LIBRARY_DUPLICATE,args("character",source,"revision",4));check(!text(duplicate,"id").equals(source) && text(duplicate,"stage").equals("DRAFT"),"duplicate identity is independent");
        var book=new SubmissionBook();var intent=book.intent(source,campaign,4);check(intent.equals(book.intent(source,campaign,4)),"stable retry token across multiple choices");
        try {book.intent(source,campaign,5);throw new AssertionError("source revision cannot silently renew retry identity");}catch(IllegalArgumentException expected) {count++;}
        var submission=args("character",source,"revision",4,"campaign",campaign,"submission",intent.token());
        var copy=ok(api,owner,LIBRARY_SUBMIT,submission);String character=text(copy,"id");check(!flag(copy,"approved") && !character.equals(source),"submission creates independent unapproved campaign copy");
        check(text(ok(api,owner,LIBRARY_SUBMIT,submission),"id").equals(character) && repo.state().characters().size()==1,"lost acknowledgment retry creates only one copy");
        var loaded=ok(api,owner,LIBRARY_GET,args("character",source));check(array(loaded,"submissions").size()==1 && text(array(loaded,"submissions").get(0).getAsJsonObject(),"campaignName").equals("Campaign One"),"known source relationships available without UUID typing");
        check(array(ok(api,owner,LIBRARY_GET,args("character",text(duplicate,"id"))),"submissions").isEmpty(),"unrelated source cannot inherit copy relationship");
        var foreign=args("character",source,"revision",4,"campaign",text(privateCampaign,"id"),"submission",UUID.randomUUID());denied(api,owner,LIBRARY_SUBMIT,foreign);
        check(!canApprove(copy,true),"owner cannot approve through UI state");denied(api,owner,CHARACTER_APPROVE,args("character",character,"revision",1));
        copy=ok(api,coDm,CHARACTER_APPROVE,args("character",character,"revision",1));check(canApprove(copy,false) && !canApprove(copy,true),"manager approval/revocation controls reflect current status");
        check(canSelect(copy,coDm.id().toString(),campaign,false) && !canSelect(copy,coDm.id().toString(),campaign,true) && !canSelect(copy,coDm.id().toString(),"other",false),"selection requires correct campaign outside combat");
        var offer=ok(api,coDm,ASSIGNMENT_OFFER,args("character",character,"revision",2,"assignmentRevision",0,"player",recipient.id()));
        var a=offer.getAsJsonObject("assignment");check(canAnswer(a,recipient.id().toString()) && !canAnswer(a,owner.id().toString()),"only pending recipient gets answer controls");
        denied(api,owner,ASSIGNMENT_ANSWER,args("character",character,"revision",1,"accept",true));
        var pending=ok(api,recipient,ASSIGNMENT_LIST,args("campaign",campaign,"page",0));check(text(array(pending,"assignments").get(0).getAsJsonObject(),"name").equals("Pierre"),"pending recipient discovers name without private sheet inspection");
        denied(api,recipient,CHARACTER_GET,args("character",character));
        ok(api,recipient,ASSIGNMENT_ANSWER,args("character",character,"revision",1,"accept",false));
        check(repo.controller(UUID.fromString(character)).equals(owner.id()),"declining preserves old controller");
        ok(api,coDm,ASSIGNMENT_OFFER,args("character",character,"revision",2,"assignmentRevision",2,"player",recipient.id()));
        denied(api,recipient,ASSIGNMENT_ANSWER,args("character",character,"revision",1,"accept",true));
        ok(api,recipient,ASSIGNMENT_ANSWER,args("character",character,"revision",3,"accept",true));
        copy=ok(api,recipient,CHARACTER_GET,args("character",character));check(text(copy,"owner").equals(owner.id().toString()) && text(copy,"controller").equals(recipient.id().toString()),"accepted control preserves source ownership");
        check(!canManageCharacter(copy) && canSelect(copy,recipient.id().toString(),campaign,false),"controller selection does not grant management");
        repo.bind(coDm,UUID.fromString(character),2,recipient.id(),recipient.id());copy=ok(api,coDm,CHARACTER_GET,args("character",character));check(!canApprove(copy,false) && !canManageCharacter(copy),"bound approval and reassignment controls disabled");
        denied(api,coDm,ASSIGNMENT_CLEAR,args("character",character,"revision",4));denied(api,coDm,CHARACTER_REVOKE,args("character",character,"revision",2));
        repo.unbind(coDm,recipient.id());ok(api,coDm,ASSIGNMENT_CLEAR,args("character",character,"revision",4));copy=ok(api,coDm,CHARACTER_REVOKE,args("character",character,"revision",2));check(!flag(copy,"approved"),"revocation status accurate without inventing review conversation");
        var original=repo.character(UUID.fromString(character)).sheet();ok(api,owner,LIBRARY_UPDATE,args("character",source,"revision",4,"section","basics","data",args("name","Changed source")));check(repo.character(UUID.fromString(character)).sheet().equals(original),"source edit never overwrites campaign copy");
        check(new LibraryRepository(root.resolve("library.json")).get(owner,UUID.fromString(source)).revision()==5,"library creation/edit survives restart");
        var custom=SrdAuthoring.template();custom.getAsJsonObject("collections").add("ui_origin",new JsonArray());repo.submit(owner,UUID.fromString(campaign),"srd_5_1",custom.toString());
        check(UiApiService.origin(custom)==null && array(ok(api,owner,LIBRARY_GET,args("character",source)),"submissions").size()==1,"malformed informational legacy provenance cannot break authorized library readback");
        for(int[] size:new int[][] {{427,240},{640,360},{854,480},{320,180}}) {var frame=UiLayout.frame(size[0],size[1],720);for(boolean paged:List.of(true,false)) {var area=listArea(frame,paged);check(area.fits(size[0],size[1]) && area.y()+area.height()<=frame.y()+frame.height()-(paged?60:34),"management list fits its pagination/footer at "+Arrays.toString(size));}check(formCapacity(frame.height())>=1,"grouped fields remain reachable");}
        var names=args("playerNames",args("person","Pierre"));check(player(names,"person").equals("Pierre") && !player(names,"missing").contains("missing"),"unknown identities don't require UUID input");
        check(!selectablePlayer(args("member",false,"inCombat",false),true) && !selectablePlayer(args("member",true,"inCombat",true),true) && selectablePlayer(args("member",true,"inCombat",false),true),"offer picker rejects nonmembers/combat candidates");
        var journalPath=root.resolve("ui-submissions.json");var journal=new SubmissionBook(journalPath);var durable=journal.intent(source,campaign,4);
        check(new SubmissionBook(journalPath).intent(source,campaign,4).equals(durable),"full client restart retains submission identity");
        journal.intent(text(duplicate,"id"),campaign,1);Files.writeString(journalPath,"invalid interrupted primary");check(new SubmissionBook(journalPath).intent(source,campaign,4).equals(durable),"validated journal backup recovers original token");
        Files.writeString(journalPath,"{\"schema\":99,\"intents\":[]}");try {new SubmissionBook(journalPath);throw new AssertionError("future journal fails closed");}catch(java.io.IOException expected) {count++;}
        var blocked=root.resolve("blocked-parent");Files.writeString(blocked,"not a directory");var failedJournal=new SubmissionBook(blocked.resolve("journal.json"));try {failedJournal.intent(source,campaign,4);throw new AssertionError("failed journal cannot publish token");}catch(IllegalStateException expected) {count++;}check(failedJournal.size()==0,"failed token write leaves memory unchanged before any packet");
        for(int i=1;i<256;i++) book.intent(UUID.randomUUID().toString(),campaign,1);try {book.intent(UUID.randomUUID().toString(),campaign,1);throw new AssertionError("bounded intent store");}catch(IllegalArgumentException expected) {count++;}check(book.size()==256,"submission identities remain bounded");
        System.out.println("Management UI checks passed: "+count);
    }
}
