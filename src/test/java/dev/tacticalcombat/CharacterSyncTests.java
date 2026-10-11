package dev.tacticalcombat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import java.nio.file.*;
import java.util.*;

public final class CharacterSyncTests {
    private static int checks;
    private static void check(boolean value,String why) { checks++; if(!value) throw new AssertionError(why); }
    @FunctionalInterface private interface Task { void run() throws Exception; }
    private static void reject(Task t,String why) throws Exception {
        try { t.run(); } catch(IllegalArgumentException|java.io.IOException e) { checks++; return; }
        throw new AssertionError(why);
    }
    private static JsonObject args(UUID id) { var a=new JsonObject(); a.addProperty("character",id.toString()); return a; }
    private static JsonObject changes(String key,Object value) {
        var o=new JsonObject(); if(value instanceof Number n) o.addProperty(key,n); else o.addProperty(key,value.toString()); return o;
    }
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("cubetable-sync-");
        try {
            var repo=new CharacterRepository(dir.resolve("state.json")); var api=new CharacterSyncService(repo);
            var admin=new CharacterRepository.Principal(UUID.randomUUID(),true);
            var owner=new CharacterRepository.Principal(admin.id(),false);
            var player=new CharacterRepository.Principal(UUID.randomUUID(),false);
            var other=new CharacterRepository.Principal(UUID.randomUUID(),false);
            UUID campaign=repo.createCampaign(admin,"Campaign"), unrelated=repo.createCampaign(admin,"Unrelated");
            repo.invite(owner,campaign,1,player.id(),false);
            repo.invite(owner,campaign,2,other.id(),false);
            String original=Files.readString(Path.of("packs/generic_d20/characters/brannoc_veyle.json"));
            var create=new JsonObject(); create.addProperty("sheet",original); create.addProperty("approved",true); create.addProperty("owner",admin.id().toString());
            var created=api.handle(player,campaign,"create",create);
            check(created.code().equals("OK"),"create acknowledged");
            UUID id=UUID.fromString(created.data().get("id").getAsString());
            var isolated=api.snapshot(player,id);isolated.getAsJsonObject("sheet").addProperty("injected",true);
            check(!api.snapshot(player,id).getAsJsonObject("sheet").has("injected"),"refresh JSON remains independently mutable; cached inputs never expose a shared sheet");
            check(!repo.character(id).approved() && repo.character(id).owner().equals(player.id()),"client authority flags ignored");
            check(api.handle(other,campaign,"get",args(id)).code().equals("FORBIDDEN"),"member cannot inspect another owner's character");
            check(api.handle(other,campaign,"get",args(UUID.randomUUID())).data().size()==0,"unknown ID discloses no sheet");
            var list=new JsonObject(); list.addProperty("campaign",unrelated.toString()); list.addProperty("page",0);
            check(api.handle(player,campaign,"list",list).code().equals("FORBIDDEN"),"unrelated campaign rejected");
            check(api.handle(player,campaign,"campaigns",new JsonObject()).data().getAsJsonArray("campaigns").size()==1,"campaign discovery filters private campaigns");
            var workspace=new CharacterWorkspace(); workspace.receive(created.data()); workspace.stage("text.name",new JsonPrimitive("Owner draft"));
            repo.edit(owner,id,1,0,changes("values.ac",17));
            var refresh=api.handle(player,campaign,"get",args(id)); workspace.receive(refresh.data());
            check(workspace.dirty() && workspace.draft().getAsJsonObject("text").get("name").getAsString().equals("Owner draft"),"refresh preserves unsaved edits");
            check(api.handle(player,campaign,"edit",workspace.request()).code().equals("CONFLICT"),"stale edit explicitly rejected");
            workspace.rebase(false);
            check(workspace.draft().getAsJsonObject("values").get("ac").getAsInt()==17,"disjoint merge keeps concurrent DM change");
            var edited=api.handle(player,campaign,"edit",workspace.request());
            check(edited.code().equals("OK") && repo.state().characters().size()==1,"revision updates the same record without duplicate draft");
            workspace.receive(edited.data()); workspace.useLatest();
            workspace.stage("text.name",new JsonPrimitive("Mine"));
            repo.edit(owner,id,repo.character(id).revision(),0,changes("text.name","Theirs"));
            workspace.receive(api.snapshot(player,id));
            check(workspace.conflicts().equals(Set.of("text.name")),"overlapping edits reported precisely");
            reject(()->workspace.rebase(false),"overlap never silently overwritten");
            check(workspace.draft().getAsJsonObject("text").get("name").getAsString().equals("Mine"),"rejected merge retains draft");
            workspace.rebase(true);
            check(api.handle(player,campaign,"edit",workspace.request()).code().equals("OK"),"explicit keep-mine reconciles using new revision");
            check(repo.character(id).sheet().contains("Mine"),"explicit reconciled name saved");
            workspace.receive(api.snapshot(player,id)); workspace.useLatest();
            workspace.importLocal(JsonParser.parseString(original).getAsJsonObject());
            check(workspace.draft().getAsJsonObject("values").has("movement_blocks"),"local import preserves server-only fields");
            reject(()->workspace.importLocal(JsonParser.parseString("{\"format\":\"percentile\"}").getAsJsonObject()),"incompatible local format rejected");
            var oldPolicy=repo.campaign(campaign).policy();
            reject(()->ApprovalPolicy.parse(JsonParser.parseString("{\"ownerFields\":[]}").getAsJsonObject()),"partial policy cannot default to automatic approval");
            reject(()->new ApprovalPolicy(false,false,false,Set.of("owner")),"policy cannot grant authority-field edits");
            var permissive=new ApprovalPolicy(false,false,false,Set.of("text.*","values.hp","values.hp_max","values.movement_blocks"));
            reject(()->repo.policy(player,campaign,3,permissive),"member cannot change approval policy");
            repo.policy(owner,campaign,3,permissive);
            reject(()->repo.policy(owner,campaign,3,oldPolicy),"simultaneous stale policy edit rejected");
            check(!repo.character(id).approved(),"policy change never retroactively approves a draft");
            var defaultOnly=new JsonObject(); defaultOnly.addProperty("sheet","{\"format\":\"generic_d20\"}");
            UUID auto=UUID.fromString(api.handle(player,campaign,"create",defaultOnly).data().get("id").getAsString());
            check(repo.character(auto).approved(),"server policy can automatically approve new characters");
            repo.edit(player,auto,1,0,changes("text.name","Auto allowed"));
            check(repo.character(auto).approved(),"permitted self-service edit retains approval under policy");
            reject(()->repo.edit(player,auto,2,0,changes("values.ac",99)),"owner field allowlist enforced");
            reject(()->repo.submit(player,campaign,RulesProfiles.TACTICAL,"{\"format\":\"generic_d20\",\"values\":{\"ac\":99}}"),"new submission cannot bypass restricted fields");
            repo.policy(owner,campaign,4,new ApprovalPolicy(false,true,true,permissive.ownerFields()));
            reject(()->repo.edit(player,auto,2,0,changes("text.name","Locked")),"owner edit lock enforced");
            check(api.handle(player,campaign,"create",defaultOnly).code().equals("FORBIDDEN"),"owner lock covers creation");
            repo.edit(owner,auto,2,0,changes("text.name","DM correction"));
            check(!repo.character(auto).approved(),"renewed approval required by policy, including DM edits");
            repo.approve(owner,auto,3);
            UUID actor=UUID.randomUUID(); repo.bind(owner,auto,4,actor,player.id());
            reject(()->repo.edit(owner,auto,4,0,changes("values.hp_max",80)),"DM cannot bypass bound-stat guard");
            repo.captureHealth(Map.of(actor,8d));
            check(repo.character(auto).revision()==4 && repo.character(auto).runtimeRevision()==1,"HP has separate revision from edit configuration");
            check(api.snapshot(player,auto).getAsJsonObject("sheet").getAsJsonObject("values").get("hp").getAsDouble()==8,"snapshot overlays live HP onto old raw sheet");
            repo.unbind(owner,actor);
            repo.edit(owner,auto,4,0,changes("text.name","Unrelated to health"));
            check(repo.character(auto).stats().get("hp")==8,"unrelated edit preserves newer HP without spurious conflict");
            reject(()->repo.edit(owner,auto,5,0,changes("values.hp",20)),"stale explicit HP edits still conflict");
            repo.edit(owner,auto,5,1,changes("values.hp",10));
            check(repo.character(auto).stats().get("hp")==10,"current health revision permits unbound HP edit");
            reject(()->repo.edit(owner,auto,6,1,changes("approved",true)),"patch cannot set authority fields");
            reject(()->repo.edit(owner,auto,6,1,changes("format","percentile")),"patch cannot silently convert character format");
            repo.policy(owner,campaign,5,oldPolicy);
            for(int i=0;i<8;i++) repo.submit(player,campaign,RulesProfiles.MANUAL,"{\"format\":\"percentile\"}");
            list.addProperty("campaign",campaign.toString());
            var first=api.handle(player,campaign,"list",list).data();
            check(first.getAsJsonArray("characters").size()==8 && first.get("total").getAsInt()==10,"bounded character listing");
            list.addProperty("page",1);
            check(api.handle(player,campaign,"list",list).data().getAsJsonArray("characters").size()==2,"pagination reaches remaining characters");
            check(api.handle(other,campaign,"list",list).data().getAsJsonArray("characters").isEmpty(),"same-campaign ordinary member sees only own characters");
            check(!RulesProfiles.get(RulesProfiles.SRD).worldCombat() && !RulesProfiles.get(RulesProfiles.MANUAL).worldCombat(),"only tactical engine enabled");
            UUID manual=repo.submit(player,campaign,RulesProfiles.MANUAL,"{\"format\":\"percentile\",\"values\":{\"experience\":5000}}");
            repo.edit(player,manual,1,0,changes("values.experience",6000));
            check(api.snapshot(player,manual).getAsJsonObject("sheet").getAsJsonObject("values").get("experience").getAsInt()==6000,"manual pack numbers keep their own scale");
            check(!repo.character(manual).stats().containsKey("experience"),"manual pack numbers do not become combat-derived stats");
            var restored=new CharacterRepository(dir.resolve("state.json"));
            check(restored.character(auto).stats().equals(repo.character(auto).stats()),"normal repository restart preserves runtime stats");
            check(restored.campaign(campaign).policy().equals(oldPolicy),"approval policy survives restart");
            // A schema-1 fixture upgrades in memory with secure defaults, identities preserved.
            var legacy=JsonParser.parseString(Files.readString(dir.resolve("state.json"))).getAsJsonObject(); legacy.addProperty("schema",1);
            legacy.getAsJsonArray("campaigns").forEach(c->c.getAsJsonObject().remove("policy"));
            legacy.getAsJsonArray("characters").forEach(c->c.getAsJsonObject().remove("runtimeRevision"));
            Path legacyFile=dir.resolve("legacy.json"); Files.writeString(legacyFile,legacy.toString());
            var migrated=new CharacterRepository(legacyFile);
            check(migrated.campaign(campaign).policy().equals(ApprovalPolicy.secureDefault()),"schema-1 migration uses secure approval default");
            check(migrated.character(id).id().equals(id) && migrated.character(auto).runtimeRevision()==0,"migration preserves identity and initializes HP revision");
            migrated.policy(owner,campaign,migrated.campaign(campaign).revision(),permissive);
            check(JsonParser.parseString(Files.readString(legacyFile)).getAsJsonObject().get("schema").getAsInt()==3,"first mutation writes schema 3 with assignment support");
            Files.delete(legacyFile);
            check(new CharacterRepository(legacyFile).recoveredFromBackup(),"missing primary recovers valid schema-1 backup");
            var revoked=JsonParser.parseString(Files.readString(dir.resolve("state.json"))).getAsJsonObject();
            for(var row:revoked.getAsJsonArray("campaigns")) if(row.getAsJsonObject().get("id").getAsString().equals(campaign.toString())) {
                var members=row.getAsJsonObject().getAsJsonArray("members");
                for(int i=members.size()-1;i>=0;i--) if(members.get(i).getAsString().equals(player.id().toString())) members.remove(i);
            }
            Path revokedFile=dir.resolve("revoked.json"); Files.writeString(revokedFile,revoked.toString());
            var revokedApi=new CharacterSyncService(new CharacterRepository(revokedFile));
            check(revokedApi.handle(player,campaign,"get",args(id)).code().equals("FORBIDDEN"),"ownership does not bypass revoked campaign membership");
            CharacterSessions sessions=new CharacterSessions();
            check(sessions.admit(player.id(),1,0).equals("OK"),"first request accepted");
            check(sessions.admit(player.id(),1,0).equals("ALREADY_PROCESSED"),"lost ack retry cannot duplicate mutation");
            check(sessions.admit(player.id(),2,0).equals("OK"),"next request accepted");
            check(sessions.admit(player.id(),1,0).equals("ALREADY_PROCESSED"),"older request cannot be replayed");
            check(sessions.admit(player.id(),3,0).equals("RATE_LIMIT"),"flood gets explicit rate acknowledgement");
            check(sessions.admit(player.id(),3,20).equals("OK"),"throttled sequence may be retried later");
            sessions.forget(player.id()); check(sessions.admit(player.id(),1,20).equals("OK"),"disconnect releases replay state");
            reject(()->CharacterRepository.checkDepth("[".repeat(25)+"]".repeat(25),24),"bounded save/request nesting");
        } finally { try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        System.out.println("Character synchronization checks passed: "+checks);
    }
}
