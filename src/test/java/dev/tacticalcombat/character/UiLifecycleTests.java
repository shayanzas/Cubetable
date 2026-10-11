package dev.tacticalcombat.character;
import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
public final class UiLifecycleTests {
    private static int count;
    static void check(boolean v,String m) {if(!v) throw new AssertionError(m);count++;}
    static JsonObject args(Object... pairs) {var o=new JsonObject();for(int i=0;i<pairs.length;i+=2) o.add(pairs[i].toString(),SheetSections.JSON.toJsonTree(pairs[i+1]));return o;}
    public static void run() throws Exception {
        var dir=Files.createTempDirectory("ui-lifecycle");var path=dir.resolve("campaigns.json");var repo=new CharacterRepository(path);var library=new LibraryRepository(dir.resolve("library.json"));
        var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);var owner=new CharacterRepository.Principal(UUID.randomUUID(),false);var substitute=new CharacterRepository.Principal(UUID.randomUUID(),false);var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
        var campaign=repo.createCampaign(dm,"UI");repo.invite(dm,campaign,1,owner.id(),false);repo.invite(dm,campaign,2,substitute.id(),false);
        var api=new UiApiService(repo,library,(op,a)->{throw new IllegalArgumentException("World operation unavailable in pure test");});
        check(!api.handle(owner,CAMPAIGN_LIST,args("page",99)).code().equals("OK"),"invalid pagination cannot return fake success");
        check(api.handle(owner,CAMPAIGN_CREATE,args("name","Forged")).code().equals("FORBIDDEN"),"ordinary user cannot create campaign");
        check(!api.handle(owner,CAMPAIGN_INVITE,args("campaign",campaign,"revision",3,"player",outsider.id(),"dm",true)).code().equals("OK"),"cannot forge co-DM authority");
        var d=library.create(owner,RulesProfiles.SRD,SheetSections.Mode.MANUAL,SrdAuthoring.template().toString());d=library.finalizeDraft(owner,d.id(),1);
        var submit=args("character",d.id(),"revision",d.revision(),"campaign",campaign,"submission",UUID.randomUUID());
        var reply=api.handle(owner,LIBRARY_SUBMIT,submit);check(reply.code().equals("OK"),"submit succeeds: "+reply.message());UUID id=UUID.fromString(reply.data().get("id").getAsString());
        check(!repo.character(id).approved(),"secure default still requires DM approval");check(api.handle(owner,LIBRARY_SUBMIT,submit).data().get("id").getAsString().equals(id.toString()) && repo.state().characters().size()==1,"durable submission retry does not duplicate");
        submit.addProperty("submission",UUID.randomUUID().toString());var second=api.handle(owner,LIBRARY_SUBMIT,submit);UUID copy=UUID.fromString(second.data().get("id").getAsString());
        check(!copy.equals(id) && library.get(owner,d.id()).stage()==LibraryRepository.Stage.COMPLETE,"explicit second copy retains source");
        var name=args("name","Updated source");library.update(owner,d.id(),d.revision(),"basics",name);check(!UiApiService.name(repo.character(id).sheet()).equals("Updated source"),"source edit cannot modify campaign copy");
        check(!api.handle(owner,CHARACTER_APPROVE,args("character",id,"revision",1)).code().equals("OK"),"owner cannot approve");
        check(api.handle(dm,CHARACTER_APPROVE,args("character",id,"revision",1)).code().equals("OK"),"DM approves");
        check(!api.handle(outsider,CHARACTER_GET,args("character",id)).code().equals("OK"),"private data denied");
        check(!api.handle(owner,ASSIGNMENT_OFFER,args("character",id,"revision",2,"assignmentRevision",0,"player",substitute.id())).code().equals("OK"),"owner cannot assign");
        check(api.handle(dm,ASSIGNMENT_OFFER,args("character",id,"revision",2,"assignmentRevision",0,"player",substitute.id())).code().equals("OK"),"DM offers control");
        check(!api.handle(owner,ASSIGNMENT_ANSWER,args("character",id,"revision",1,"accept",true)).code().equals("OK"),"only recipient accepts");
        check(api.handle(substitute,ASSIGNMENT_ANSWER,args("character",id,"revision",1,"accept",true)).code().equals("OK"),"recipient accepts");
        check(repo.controller(id).equals(substitute.id()) && repo.character(id).owner().equals(owner.id()),"control does not transfer ownership");
        check(api.handle(substitute,CHARACTER_GET,args("character",id)).code().equals("OK"),"controller authorized read");
        var trackers=SheetSections.JSON.toJsonTree(SheetSections.defaults().get("trackers")).getAsJsonObject();
        check(!api.handle(substitute,CHARACTER_EDIT,args("character",id,"revision",2,"runtimeRevision",0,"section","trackers","data",trackers)).code().equals("OK"),"controller cannot edit owner's sheet");
        repo.bind(dm,id,2,substitute.id(),substitute.id());
        check(!api.handle(dm,CHARACTER_APPROVE,args("character",id,"revision",2)).code().equals("OK"),"bound approval revision cannot be rewritten");
        check(!api.handle(dm,ASSIGNMENT_CLEAR,args("character",id,"revision",2)).code().equals("OK"),"bound control changes locked");
        check(!api.handle(dm,CHARACTER_REVOKE,args("character",id,"revision",2)).code().equals("OK"),"bound approval cannot invalidate binding");
        check(new CharacterRepository(path).controller(id).equals(substitute.id()),"assignment/binding reload atomically");
        repo.unbind(dm,substitute.id());check(api.handle(dm,ASSIGNMENT_CLEAR,args("character",id,"revision",2)).code().equals("OK"),"restore original owner while unbound");
        check(repo.controller(id).equals(owner.id()),"owner controller restored");
        var old=JsonParser.parseString(Files.readString(path)).getAsJsonObject();old.addProperty("schema",2);old.remove("assignments");var legacy=dir.resolve("legacy.json");Files.writeString(legacy,old.toString());
        var migrated=new CharacterRepository(legacy);check(migrated.character(id).id().equals(id) && migrated.controller(id).equals(owner.id()),"schema2 adds empty assignment metadata without changing identities");
        migrated.approve(dm,id,2);check(JsonParser.parseString(Files.readString(legacy)).getAsJsonObject().get("schema").getAsInt()==3,"next durable write migrates to schema3");
        var forged=args("character",id);forged.addProperty("admin",true);check(!api.handle(outsider,CHARACTER_GET,forged).code().equals("OK"),"unknown privilege fields rejected");
        System.out.println("UI lifecycle checks passed: "+count);
    }
}
