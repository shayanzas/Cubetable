package dev.tacticalcombat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;

public final class SrdAuthoringTests {
    private static int count;
    private static void check(boolean value,String why) { count++; if(!value) throw new AssertionError(why); }
    @FunctionalInterface private interface Task { void run() throws Exception; }
    private static void rejects(Task task,String why) throws Exception {
        try { task.run(); } catch(IllegalArgumentException expected) { count++; return; } throw new AssertionError(why);
    }
    private static SrdAuthoring.Field field(String key) { return SrdAuthoring.fields().stream().filter(f->f.key().equals(key)).findFirst().orElseThrow(); }
    public static void run() throws Exception {
        var template=SrdAuthoring.template(); var original=template.deepCopy();
        var stats=SrdCharacterCodec.parseSheet(template.toString());
        check(stats.hp()==10 && stats.armorClass()==10 && stats.speedFeet()==30,"neutral complete template");
        for(var f:SrdAuthoring.fields()) check(SrdAuthoring.value(template,f)!=null,"all authored fields have controls: "+f.key());
        var edited=SrdAuthoring.change(template,field("abilities.str"),"16");
        edited=SrdAuthoring.change(edited,field("armor"),"chain_mail");
        edited=SrdAuthoring.change(edited,field("shield"),"true");
        edited=SrdAuthoring.change(edited,field("armor_proficiencies"),"heavy");
        edited=SrdAuthoring.change(edited,field("weapons"),"dagger,longsword");
        check(SrdCharacterCodec.parseSheet(edited.toString()).armorClass()==18,"typed armor/shield choices feed validated AC");
        check(SrdAuthoring.value(edited,field("weapons")).equals("dagger,longsword"),"multi-choice form serializes without hand JSON");
        check(template.equals(original),"form edits do not mutate original sheet");
        rejects(()->SrdAuthoring.change(template,field("hp"),"d20+1"),"form formulas rejected");
        rejects(()->SrdAuthoring.change(template,field("hp"),"1.5"),"fractional HP rejected");
        rejects(()->SrdAuthoring.change(template,field("armor"),"diamond"),"unknown equipment rejected");
        rejects(()->SrdAuthoring.change(template,field("resistances"),"fire,fire"),"duplicate defenses rejected");
        rejects(()->SrdAuthoring.change(template,field("shield"),"yes"),"strict boolean choices");
        rejects(()->SrdAuthoring.change(template,new SrdAuthoring.Field("approved","Approved",List.of(),false),"1"),"form cannot author approval");
        rejects(()->SrdCharacterCodec.parseSheet(SrdAuthoring.change(template,field("speed_feet"),"999").toString()),"server codec validates final bounds");
        check(new SrdHealth(10,20,3).damage(List.of(new SrdRules.Damage(DamageType.FIRE,7)),stats).equals(new SrdHealth(6,20,0)),"temp HP absorbs before HP");
        check(new SrdHealth(10,20,9).damage(List.of(new SrdRules.Damage(DamageType.FIRE,7)),stats).equals(new SrdHealth(10,20,2)),"remaining temporary HP retained");
        check(new SrdHealth(0,20,4).heal(8).equals(new SrdHealth(8,20,4)),"healing restores HP not temporary HP");
        check(new SrdHealth(19,20,0).heal(10).current()==20,"healing caps at max");
        check(new SrdHealth(0,20,0).temporary(5,true).zeroHp(),"temporary HP does not revive zero HP");
        check(new SrdHealth(10,20,9).temporary(4,false).temporary()==9,"explicit choice can keep previous temporary HP");
        check(new SrdHealth(10,20,9).temporary(4,true).temporary()==4,"explicit replacement never stacks");
        check(new SrdHealth(10,20,3).damage(List.of(new SrdRules.Damage(DamageType.FIRE,100)),stats).zeroHp(),"damage floors HP at zero; no vanilla death");
        rejects(()->new SrdHealth(1,20,0).heal(-1),"no negative healing");
        rejects(()->new SrdHealth(21,20,0),"invalid health state rejected");
        var resistance=SrdAuthoring.change(template,field("resistances"),"fire");
        check(new SrdHealth(10,20,2).damage(List.of(new SrdRules.Damage(DamageType.FIRE,7),new SrdRules.Damage(DamageType.COLD,4)),SrdCharacterCodec.parseSheet(resistance.toString())).current()==5,"typed defenses before temporary absorption");
        repositoryChecks();
        System.out.println("SRD authoring/resource checks passed: "+count);
    }
    private static void repositoryChecks() throws Exception {
        Path dir=Files.createTempDirectory("cubetable-srd-resources-");
        try {
            var path=dir.resolve("state.json"); var repo=new CharacterRepository(path); var sync=new CharacterSyncService(repo);
            var dm=new CharacterRepository.Principal(UUID.randomUUID(),true); var player=new CharacterRepository.Principal(UUID.randomUUID(),false);
            var stranger=new CharacterRepository.Principal(UUID.randomUUID(),false);
            UUID campaign=repo.createCampaign(dm,"SRD"); repo.invite(dm,campaign,1,player.id(),false); repo.profile(dm,campaign,2,RulesProfiles.SRD);
            var forged=new JsonObject(); forged.addProperty("approved",true); forged.addProperty("hp",999);
            var created=sync.handle(player,campaign,"srd_create",forged);
            check(created.code().equals("OK"),"ordinary member creates typed draft");
            UUID id=UUID.fromString(created.data().get("id").getAsString());
            check(!repo.character(id).approved() && created.data().getAsJsonObject("srd").get("hp").getAsInt()==10,"client authority flags and stats ignored by template operation");
            check(!created.data().getAsJsonObject("srd").get("characterReady").getAsBoolean(),"draft not eligible");
            check(sync.handle(stranger,campaign,"srd_create",new JsonObject()).code().equals("FORBIDDEN"),"outsider cannot create in campaign");
            var get=new JsonObject(); get.addProperty("character",id.toString());
            check(sync.handle(stranger,campaign,"get",get).data().size()==0,"private eligibility/stats not disclosed");
            repo.approve(dm,id,1); UUID actor=UUID.randomUUID(); repo.bind(dm,id,2,actor,null);
            var ready=sync.snapshot(player,id).getAsJsonObject("srd");
            check(ready.get("characterReady").getAsBoolean() && !ready.get("worldCombatEnabled").getAsBoolean(),"character prerequisites are not engine activation");
            var snapshot=repo.srdSnapshot(actor,campaign); var health=SrdHealth.of(snapshot.stats()).damage(List.of(new SrdRules.Damage(DamageType.SLASHING,4)),snapshot.stats());
            var result=repo.commitSrdHealth(snapshot,health);
            check(result.stats().hp()==6 && result.runtimeRevision()==1 && result.revision()==2,"durable HP update changes only runtime revision");
            rejects(()->repo.commitSrdHealth(snapshot,health),"same expected effect cannot apply twice");
            check(new CharacterRepository(path).srdSnapshot(actor,campaign).equals(result),"HP and binding survive repository restart");
            repo.captureHealth(Map.of(actor,1d)); check(repo.srdSnapshot(actor,campaign).equals(result),"repository itself refuses vanilla capture into SRD");
            var resources=repo.commitSrdHealth(result,SrdHealth.of(result.stats()).temporary(3,true));
            var fresh=sync.snapshot(player,id); check(fresh.getAsJsonObject("srd").get("temporaryHp").getAsInt()==3,"authorized refresh shows authoritative temporary HP");
            check(SrdCharacterCodec.parseSheet(fresh.getAsJsonObject("sheet").toString()).hp()==6,"sheet readback matches persistent D&D HP");
            rejects(()->repo.commitSrdHealth(resources,new SrdHealth(6,30,0)),"runtime update cannot alter approved HP maximum");
            var noop=repo.commitSrdHealth(resources,SrdHealth.of(resources.stats()));
            check(noop.runtimeRevision()==3,"no-op effects also consume CAS revision");
            rejects(()->repo.commitSrdHealth(resources,SrdHealth.of(resources.stats())),"no-op replay rejected");
            var workspace=new CharacterWorkspace(); workspace.receive(created.data());
            workspace.stage("collections.srd_combat",SrdAuthoring.change(workspace.draft(),field("level"),"2").getAsJsonObject("collections").get("srd_combat"));
            workspace.receive(sync.snapshot(player,id)); check(workspace.dirty(),"refresh preserves unsaved form work");
            check(workspace.conflicts().contains("collections.srd_combat"),"concurrent HP/form edits explicitly conflict, never silently overwrite");
            repo.unbind(dm,actor);
            var stale=new JsonObject(); stale.add("collections.srd_combat",SrdAuthoring.template().getAsJsonObject("collections").get("srd_combat"));
            rejects(()->repo.edit(player,id,2,0,stale),"stale collection cannot restore old HP after unbind");
            var invalid=SrdAuthoring.template(); SrdAuthoring.data(invalid).addProperty("hp",9000);
            var bad=new JsonObject(); bad.add("collections.srd_combat",invalid.getAsJsonObject("collections").get("srd_combat"));
            rejects(()->repo.edit(player,id,2,3,bad),"server edit independently validates SRD schema");
            var current=repo.effectiveSheet(repo.character(id));
            var valid=SrdAuthoring.change(current,field("level"),"2");
            var patch=new JsonObject(); patch.add("collections.srd_combat",valid.getAsJsonObject("collections").get("srd_combat"));
            repo.edit(player,id,2,3,patch);
            check(!repo.character(id).approved(),"typed form edits invalidate approval under secure policy");
            repo.policy(dm,campaign,3,new ApprovalPolicy(true,true,true,Set.of("text.*","collections.*")));
            check(sync.handle(player,campaign,"srd_create",new JsonObject()).code().equals("FORBIDDEN"),"owner lock applies to typed creation");
            rejects(()->repo.edit(player,id,3,3,patch),"owner lock applies to typed edits");
            UUID incomplete=repo.submit(dm,campaign,RulesProfiles.SRD,"{\"format\":\"generic_d20\"}"); repo.approve(dm,incomplete,1);
            var status=sync.snapshot(dm,incomplete).getAsJsonObject("srd");
            check(!status.get("valid").getAsBoolean() && !status.get("characterReady").getAsBoolean(),"legacy approval cannot imply SRD readiness");
            repo.profile(dm,campaign,4,RulesProfiles.TACTICAL);
            check(sync.handle(player,campaign,"srd_create",new JsonObject()).code().equals("REJECTED"),"creation refuses non-SRD default");
        } finally { try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
    }
}
