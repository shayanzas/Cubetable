package dev.tacticalcombat.uiapi;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Versioned, explicit application operations. Network adapter supplies the authenticated principal and world hooks. */
public final class UiApiService {
    public enum Operation {
        CAPABILITIES, ACTIVE, PRESENTATION_POLICY, LIBRARY_LIST, LIBRARY_CREATE, LIBRARY_GET, LIBRARY_UPDATE, LIBRARY_VALIDATE, LIBRARY_FINALIZE, LIBRARY_DUPLICATE, LIBRARY_SUBMIT, LIBRARY_DELETE,
        CAMPAIGN_LIST, CAMPAIGN_CREATE, CAMPAIGN_GET, CAMPAIGN_SELECT, CAMPAIGN_INVITE, CAMPAIGN_REMOVE, CAMPAIGN_REVOKE_DM, CAMPAIGN_POLICY, CAMPAIGN_PROFILE,
        ROSTER, CHARACTER_GET, CHARACTER_SECTION, CHARACTER_EDIT, CHARACTER_APPROVE, CHARACTER_REVOKE,
        ASSIGNMENT_LIST, ASSIGNMENT_OFFER, ASSIGNMENT_ANSWER, ASSIGNMENT_CLEAR, CHARACTER_SELECT, BIND, UNBIND, ELIGIBLE_PLAYERS, ELIGIBLE_ENTITIES,
        MANUAL_COUNTER, MANUAL_UPDATE, SPELL_CATALOG, EVENTS, PARTY, INITIATIVE, REST_REQUEST, REST_REVIEW, REST_LIST
    }
    public record Reply(String code,String message,JsonObject data) {}
    /** Minecraft actions remain behind the existing service, never simulated as successful database edits. */
    public interface WorldAccess {
        JsonObject perform(Operation operation,JsonObject args) throws java.io.IOException;
    }
    private final CharacterRepository repo;
    private final LibraryRepository library;
    private final WorldAccess world;
    public UiApiService(CharacterRepository repo,LibraryRepository library,WorldAccess world) {this.repo=repo;this.library=library;this.world=world;}
    public Reply handle(CharacterRepository.Principal p,Operation op,JsonObject a) {
        try {
            require(a.toString().length()<=49152,"Request too large");CharacterRepository.checkDepth(a.toString(),24);
            JsonObject out=new JsonObject();
            switch(op) {
                case CAPABILITIES -> {fields(a,"");out.addProperty("version",1);out.addProperty("hubQuickSwitch",true);out.addProperty("sectionsVersion",1);out.addProperty("trackerVersion",2);out.addProperty("classChoiceValidation",true);out.addProperty("abilityAllocation",true);out.addProperty("inspirationCounts",true);out.add("operations",SheetSections.JSON.toJsonTree(Operation.values()));out.addProperty("liveTakeover",false);out.addProperty("spellAutomation",false);out.addProperty("restRecovery",false);out.add("srdFields",SheetSections.JSON.toJsonTree(SrdAuthoring.fields()));out.add("sectionDefaults",SheetSections.JSON.toJsonTree(SheetSections.defaults()));}
                case LIBRARY_LIST -> {fields(a,"page");var rows=new JsonArray();for(var d:library.list(p,integer(a,"page"))) {var row=draft(d,false);var errors=library.validate(p,d.id());row.addProperty("supportedValid",errors.isEmpty());row.addProperty("validationErrors",errors.size());rows.add(row);}out.add("characters",rows);out.addProperty("pageSize",8);}
                case LIBRARY_CREATE -> {
                    fields(a,"profile mode sheet");String profile=string(a,"profile");String sheet=a.get("sheet").isJsonNull()?SrdAuthoring.template().toString():string(a,"sheet");
                    if(a.get("sheet").isJsonNull()) require(profile.equals(RulesProfiles.SRD),"Supply a manual pack sheet for this profile");
                    if(a.get("sheet").isJsonNull()&&string(a,"mode").equals("GUIDED")) {var guided=JsonParser.parseString(sheet).getAsJsonObject();sheet=SheetSections.update(guided,"creation",SheetSections.raw(guided,"creation")).toString();}
                    out=draft(library.create(p,profile,SheetSections.Mode.valueOf(string(a,"mode")),sheet),true);
                }
                case LIBRARY_GET -> {
                    fields(a,"character");var d=library.get(p,id(a,"character"));out=draft(d,true);var copies=new JsonArray();int total=0;
                    for(var c:repo.state().characters().values().stream().sorted(Comparator.comparing(c->c.id().toString())).toList()) {
                        if(!c.owner().equals(p.id()) || !repo.member(p,c.campaign())) continue;
                        var sheet=JsonParser.parseString(c.sheet()).getAsJsonObject();var origin=origin(sheet);
                        if(origin==null) continue;
                        if(!origin.get("library").getAsString().equals(d.id().toString())) continue;
                        total++;if(copies.size()>=128) continue;
                        var row=new JsonObject();row.addProperty("id",c.id().toString());row.addProperty("name",name(c.sheet()));row.addProperty("campaign",c.campaign().toString());row.addProperty("campaignName",repo.campaign(c.campaign()).name());row.addProperty("approved",c.approved());row.add("origin",origin.deepCopy());copies.add(row);
                    }
                    out.add("submissions",copies);out.addProperty("submissionsTruncated",total>128);out.add("errors",SheetSections.JSON.toJsonTree(library.validate(p,d.id())));out.addProperty("guidedRulesComplete",false);
                }
                case LIBRARY_UPDATE -> {fields(a,"character revision section data");out=draft(library.update(p,id(a,"character"),revision(a),string(a,"section"),a.getAsJsonObject("data")),true);}
                case LIBRARY_DELETE -> {fields(a,"character revision");library.delete(p,id(a,"character"),revision(a));out.addProperty("deleted",true);}
                case LIBRARY_VALIDATE -> {fields(a,"character");out.add("errors",SheetSections.JSON.toJsonTree(library.validate(p,id(a,"character"))));out.addProperty("guidedRulesComplete",false);}
                case LIBRARY_FINALIZE -> {fields(a,"character revision");out=draft(library.finalizeDraft(p,id(a,"character"),revision(a)),true);}
                case LIBRARY_DUPLICATE -> {fields(a,"character revision");out=draft(library.duplicate(p,id(a,"character"),revision(a)),true);}
                case LIBRARY_SUBMIT -> {
                    fields(a,"character revision campaign submission");var d=library.expected(p,id(a,"character"),revision(a));UUID campaign=id(a,"campaign"),submission=id(a,"submission");
                    require(d.stage()==LibraryRepository.Stage.COMPLETE,"Finalize library character before submission");require(repo.member(p,campaign),"Campaign membership permission required");
                    UUID existing=null;
                    for(var c:repo.state().characters().values()) if(c.campaign().equals(campaign) && c.owner().equals(p.id())) {
                        var sheet=JsonParser.parseString(c.sheet()).getAsJsonObject();var origin=origin(sheet);
                        if(origin!=null) {
                            if(origin.get("submission").getAsString().equals(submission.toString())) {require(origin.get("library").getAsString().equals(d.id().toString()) && origin.get("revision").getAsLong()==d.revision(),"Submission identity reused with different source");existing=c.id();break;}
                        }
                    }
                    if(existing==null) {
                        var sheet=JsonParser.parseString(d.sheet()).getAsJsonObject();if(!sheet.has("collections")) sheet.add("collections",new JsonObject());var origin=new JsonObject();
                        origin.addProperty("library",d.id().toString());origin.addProperty("revision",d.revision());origin.addProperty("submission",submission.toString());var rows=new JsonArray();rows.add(origin);sheet.getAsJsonObject("collections").add("ui_origin",rows);
                        existing=repo.submit(p,campaign,d.profile(),sheet.toString());
                    }
                    out=character(p,existing);
                }
                case CAMPAIGN_LIST -> {fields(a,"page");require(integer(a,"page")<=8,"Invalid page");out=new CharacterSyncService(repo).handle(p,null,"campaigns",a).data();}
                case CAMPAIGN_CREATE -> {fields(a,"name");out=campaign(p,repo.createCampaign(p,string(a,"name")));}
                case CAMPAIGN_GET -> {fields(a,"campaign");out=campaign(p,id(a,"campaign"));}
                case CAMPAIGN_INVITE -> {fields(a,"campaign revision player dm");repo.invite(p,id(a,"campaign"),revision(a),id(a,"player"),bool(a,"dm"));out=campaign(p,id(a,"campaign"));}
                case CAMPAIGN_REMOVE -> {fields(a,"campaign revision player");repo.removeMember(p,id(a,"campaign"),revision(a),id(a,"player"));out=campaign(p,id(a,"campaign"));}
                case CAMPAIGN_REVOKE_DM -> {fields(a,"campaign revision player");repo.revokeDm(p,id(a,"campaign"),revision(a),id(a,"player"));out=campaign(p,id(a,"campaign"));}
                case CAMPAIGN_POLICY -> {fields(a,"campaign revision policy");repo.policy(p,id(a,"campaign"),revision(a),ApprovalPolicy.parse(a.getAsJsonObject("policy")));out=campaign(p,id(a,"campaign"));}
                case CAMPAIGN_PROFILE -> {fields(a,"campaign revision profile");repo.profile(p,id(a,"campaign"),revision(a),string(a,"profile"));out=campaign(p,id(a,"campaign"));}
                case ROSTER -> {
                    fields(a,"campaign page");UUID campaign=id(a,"campaign");require(repo.member(p,campaign),"Campaign permission denied");int page=integer(a,"page");require(page>=0 && page<=128,"Invalid page");
                    var rows=new JsonArray();repo.state().characters().values().stream().filter(c->c.campaign().equals(campaign)).sorted(Comparator.comparing(c->c.id().toString())).skip(page*8L).limit(8).forEach(c->{
                        var row=new JsonObject();row.addProperty("id",c.id().toString());row.addProperty("name",name(c.sheet()));row.addProperty("profile",c.profile());row.addProperty("approved",c.approved());row.addProperty("owner",c.owner().toString());row.addProperty("controller",repo.controller(c.id()).toString());row.addProperty("revision",c.revision());
                        var binding=repo.binding(c.id());if(binding!=null) row.addProperty("bound",binding.toString());row.addProperty("readiness",!c.approved()?"Needs approval":binding==null?"Needs binding":"Inspect for live readiness");row.addProperty("privateView",c.owner().equals(p.id()) || repo.controller(c.id()).equals(p.id()) || repo.manages(p,campaign));rows.add(row);
                    });out.add("characters",rows);
                }
                case CHARACTER_GET -> {fields(a,"character");out=character(p,id(a,"character"));}
                case CHARACTER_SECTION -> {fields(a,"character section");var c=repo.visible(p,id(a,"character"));out.add("section",SheetSections.read(repo.effectiveSheet(c),string(a,"section")));out.addProperty("revision",c.revision());out.addProperty("runtimeRevision",c.runtimeRevision());out.addProperty("manualRevision",repo.manualRevision(c.id()));out.addProperty("automation","INFORMATIONAL_OR_MANUAL");}
                case CHARACTER_EDIT -> {
                    fields(a,a.has("manualRevision")?"character revision runtimeRevision manualRevision section data":"character revision runtimeRevision section data");UUID id=id(a,"character");var c=repo.visible(p,id);String section=string(a,"section");JsonObject next=SheetSections.update(repo.effectiveSheet(c),section,a.getAsJsonObject("data"));
                    repo.editSections(p,id,revision(a),number(a,"runtimeRevision"),a.has("manualRevision")?number(a,"manualRevision"):0,CharacterEdits.diff(repo.effectiveSheet(c),next));out=character(p,id);
                }
                case SPELL_CATALOG -> {fields(a,"query page");out=RpgRecords.catalog(string(a,"query"),integer(a,"page"));}
                case MANUAL_COUNTER -> {
                    fields(a,"character manualRevision change");UUID id=id(a,"character");
                    repo.track(p,id,number(a,"manualRevision"),SheetSections.parse(a.get("change"),RpgRecords.Change.class));out=character(p,id);
                }
                case MANUAL_UPDATE -> {
                    fields(a,"scope character revision runtimeRevision change");var change=SheetSections.parse(a.get("change"),RpgRecords.Change.class);UUID id=id(a,"character");String scope=string(a,"scope");
                    if(scope.equals("LIBRARY")) {var d=library.expected(p,id,revision(a));var data=RpgRecords.apply(JsonParser.parseString(d.sheet()).getAsJsonObject(),change);out=draft(library.update(p,id,revision(a),change.section(),data),true);}
                    else {
                        require(scope.equals("CAMPAIGN"),"Unknown character scope");var c=repo.visible(p,id);var sheet=repo.effectiveSheet(c);var data=RpgRecords.apply(sheet,change);
                        var next=SheetSections.update(sheet,change.section(),data);repo.edit(p,id,revision(a),number(a,"runtimeRevision"),CharacterEdits.diff(sheet,next));out=character(p,id);
                    }
                }
                case CHARACTER_APPROVE, CHARACTER_REVOKE -> {
                    fields(a,"character revision");UUID id=id(a,"character");if(op==Operation.CHARACTER_APPROVE) repo.approve(p,id,revision(a));else repo.revokeApproval(p,id,revision(a));out=character(p,id);
                }
                case ASSIGNMENT_LIST -> {
                    fields(a,"campaign page");UUID campaign=id(a,"campaign");require(repo.member(p,campaign),"Campaign permission denied");int page=integer(a,"page");require(page>=0 && page<=128,"Invalid page");
                    var rows=new JsonArray();repo.state().assignments().values().stream().filter(x->repo.character(x.character()).campaign().equals(campaign) && (repo.manages(p,campaign) || p.id().equals(x.offered()) || p.id().equals(x.controller()) || p.id().equals(repo.character(x.character()).owner())))
                        .sorted(Comparator.comparing(x->x.character().toString())).skip(page*8L).limit(8).forEach(x->{var row=SheetSections.JSON.toJsonTree(x).getAsJsonObject();var c=repo.character(x.character());row.addProperty("name",name(c.sheet()));row.addProperty("profile",c.profile());row.addProperty("bound",repo.binding(c.id())!=null);rows.add(row);});out.add("assignments",rows);
                }
                case ASSIGNMENT_OFFER -> {fields(a,"character revision assignmentRevision player");UUID id=id(a,"character");repo.offerControl(p,id,revision(a),number(a,"assignmentRevision"),id(a,"player"));out.add("assignment",SheetSections.JSON.toJsonTree(repo.assignment(id)));}
                case ASSIGNMENT_ANSWER -> {fields(a,"character revision accept");UUID id=id(a,"character");repo.answerControl(p,id,revision(a),bool(a,"accept"));out.add("assignment",SheetSections.JSON.toJsonTree(repo.assignment(id)));}
                case ASSIGNMENT_CLEAR -> {fields(a,"character revision");UUID id=id(a,"character");repo.clearControl(p,id,revision(a));out.add("assignment",SheetSections.JSON.toJsonTree(repo.assignment(id)));}
                default -> out=world.perform(op,a);
            }
            require(out.toString().length()<=65536,"Result too large; request a smaller section");return new Reply("OK",op+" successful",out);
        } catch(java.io.IOException e) {return new Reply("STORAGE_ERROR","Save failed; no success was acknowledged. Reload before retrying.",new JsonObject());}
        catch(RuntimeException e) {
            String msg=e.getMessage()==null?"Invalid request":e.getMessage();String lower=msg.toLowerCase(Locale.ROOT);
            String code=msg.startsWith("Stale")?"CONFLICT":lower.contains("unavailable")?"UNAVAILABLE":lower.matches(".*(permission|only |ownership|membership|locked|forbidden|unbind).*" )?"FORBIDDEN":"REJECTED";
            return new Reply(code,msg.substring(0,Math.min(240,msg.length())),new JsonObject());
        }
    }
    private JsonObject campaign(CharacterRepository.Principal p,UUID id) {require(repo.member(p,id),"Campaign permission denied");var c=repo.campaign(id);var out=new JsonObject();out.addProperty("id",id.toString());out.addProperty("name",c.name());out.addProperty("profile",c.profile());out.addProperty("revision",c.revision());out.addProperty("manager",repo.manages(p,id));out.addProperty("canManageRoles",p.admin() || c.owner().equals(p.id()));out.add("policy",SheetSections.JSON.toJsonTree(c.policy()));if(repo.manages(p,id)) {out.addProperty("owner",c.owner().toString());out.add("members",SheetSections.JSON.toJsonTree(c.members()));out.add("dms",SheetSections.JSON.toJsonTree(c.dms()));}return out;}
    private JsonObject character(CharacterRepository.Principal p,UUID id) {var out=new CharacterSyncService(repo).snapshot(p,id);out.addProperty("combatAuthority",repo.character(id).profile().equals(RulesProfiles.SRD)?"collections.srd_combat":"server stats/profile");out.addProperty("extensionAuthority","ui_* sections are recorded/manual only");out.addProperty("manualRevision",repo.manualRevision(id));var manualSections=new JsonArray();for(String section:List.of("trackers","features","spellbook"))if(repo.mayTrack(p,id,section))manualSections.add(section);out.add("manualSections",manualSections);out.addProperty("canTrack",!manualSections.isEmpty());out.addProperty("controller",repo.controller(id).toString());out.add("assignment",SheetSections.JSON.toJsonTree(repo.assignment(id)));return out;}
    public static JsonObject draft(LibraryRepository.Draft d,boolean full) {var o=new JsonObject();o.addProperty("id",d.id().toString());o.addProperty("name",name(d.sheet()));o.addProperty("profile",d.profile());o.addProperty("mode",d.mode().name());o.addProperty("stage",d.stage().name());o.addProperty("revision",d.revision());if(full) o.add("sheet",JsonParser.parseString(d.sheet()));return o;}
    public static String name(String sheet) {var o=JsonParser.parseString(sheet).getAsJsonObject();String n=o.has("text") && o.getAsJsonObject("text").has("name")?o.getAsJsonObject("text").get("name").getAsString():"Unnamed character";return n.substring(0,Math.min(80,n.length()));}
    /** Provenance is informational and may be absent/malformed in legacy custom sheets. It grants no authority. */
    public static JsonObject origin(JsonObject sheet) {
        try {
            var collections=sheet.getAsJsonObject("collections");if(collections==null || !collections.has("ui_origin")) return null;
            var rows=collections.getAsJsonArray("ui_origin");if(rows.size()!=1 || !rows.get(0).isJsonObject()) return null;var o=rows.get(0).getAsJsonObject();
            if(!o.keySet().equals(Set.of("library","revision","submission"))) return null;id(o,"library");id(o,"submission");if(number(o,"revision")<1) return null;return o;
        }catch(RuntimeException invalid) {return null;}
    }
    public static void fields(JsonObject a,String allowed) {require(a.keySet().equals(allowed.isEmpty()?Set.of():Set.of(allowed.split(" "))),"Missing or unknown request fields");}
    public static UUID id(JsonObject a,String key) {return UUID.fromString(string(a,key));}
    public static String string(JsonObject a,String key) {var e=a.get(key);require(e!=null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString(),"Expected text: "+key);return e.getAsString();}
    public static long number(JsonObject a,String key) {var e=a.get(key);require(e!=null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() && e.getAsString().matches("[0-9]{1,18}"),"Expected nonnegative integer: "+key);return e.getAsLong();}
    public static int integer(JsonObject a,String key) {return Math.toIntExact(number(a,key));}
    public static long revision(JsonObject a) {return number(a,"revision");}
    public static boolean bool(JsonObject a,String key) {var e=a.get(key);require(e!=null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean(),"Expected boolean: "+key);return e.getAsBoolean();}
}
