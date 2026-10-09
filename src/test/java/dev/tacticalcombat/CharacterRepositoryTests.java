package dev.tacticalcombat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.*;
import java.nio.file.*;
import java.util.*;

/** Real temporary files and hostile principals; no test framework dependency. */
public final class CharacterRepositoryTests {
    private static int checks;
    private static void check(boolean b,String why) { checks++; if(!b) throw new AssertionError(why); }
    @FunctionalInterface private interface Task { void run() throws Exception; }
    private static void reject(Task t,String why) throws Exception {
        try { t.run(); } catch(IllegalArgumentException|java.io.IOException e) { checks++; return; }
        throw new AssertionError(why);
    }
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("cubetable-characters-");
        try {
            Path file=dir.resolve("state.json"); var repo=new CharacterRepository(file);
            var owner=new CharacterRepository.Principal(UUID.randomUUID(),true);
            var member=new CharacterRepository.Principal(UUID.randomUUID(),false);
            var outsider=new CharacterRepository.Principal(UUID.randomUUID(),false);
            reject(()->repo.createCampaign(member,"forged"),"member cannot create campaign");
            UUID campaign=repo.createCampaign(owner,"Test campaign");
            repo.invite(owner,campaign,1,member.id(),false);
            reject(()->repo.invite(member,campaign,2,outsider.id(),true),"member cannot grant DM");
            reject(()->repo.profile(member,campaign,2,RulesProfiles.MANUAL),"member cannot change profile");
            String sheet=Files.readString(Path.of("packs/generic_d20/characters/brannoc_veyle.json"));
            UUID character=repo.submit(member,campaign,RulesProfiles.TACTICAL,sheet);
            check(!repo.character(character).approved(),"untrusted submission is draft");
            check(repo.character(character).stats().get("hp_max")==32,"explicit raw numeric stat adapter");
            check(repo.character(character).sheet().equals(sheet),"manual format preserved");
            reject(()->repo.approve(member,character,1),"owner cannot self approve");
            reject(()->repo.visible(outsider,character),"outsider cannot read private sheet");
            reject(()->repo.stat(outsider,character,1,"ac",99),"outsider cannot forge stats");
            reject(()->repo.stat(member,character,99,"ac",99),"stale writes rejected");
            repo.stat(member,character,1,"movement_blocks",4);
            check(repo.character(character).revision()==2,"revision advances");
            reject(()->repo.stat(member,character,2,"movement_blocks",Double.NaN),"NaN rejected");
            reject(()->repo.stat(member,character,2,"movement_blocks",999),"unbounded movement rejected");
            UUID actor=UUID.randomUUID();
            reject(()->repo.bind(owner,character,2,actor,null),"draft cannot bind");
            repo.approve(owner,character,2);
            reject(()->repo.bind(owner,character,3,actor,outsider.id()),"cannot impersonate another player");
            repo.bind(owner,character,3,actor,member.id());
            reject(()->repo.bind(owner,character,3,UUID.randomUUID(),null),"character cannot have duplicate actors");
            reject(()->repo.stat(member,character,3,"hp_max",50),"bound stats immutable to client edits");
            var restored=new CharacterRepository(file);
            check(restored.state().bindings().get(actor).equals(character),"binding persisted");
            check(restored.character(character).stats().get("movement_blocks")==4,"approved values persist");
            repo.captureHealth(Map.of(actor,12d));
            check(repo.character(character).stats().get("hp")==12,"server resource update retained");
            repo.unbind(owner,actor);
            check(!repo.state().bindings().containsKey(actor),"unbind persists");
            repo.stat(member,character,4,"ac",17);
            check(!repo.character(character).approved(),"editing invalidates approval");
            repo.invite(owner,campaign,2,member.id(),true);
            check(repo.manages(member,campaign),"owner can appoint co-DM");
            reject(()->repo.invite(member,campaign,3,outsider.id(),true),"co-DM cannot escalate hierarchy");
            repo.revokeDm(owner,campaign,3,member.id());
            check(!repo.manages(member,campaign),"revocation enforced");
            reject(()->repo.submit(member,campaign,RulesProfiles.TACTICAL,"{\"format\":\"generic_d20\",\"owner\":\"forged\"}"),"import does not carry authority");
            reject(()->CharacterRepository.validateSheet("[".repeat(30)+"]".repeat(30)),"deep nesting rejected");
            check(!RulesProfiles.get(RulesProfiles.SRD).worldCombat(),"incomplete SRD automation cannot be selected as engine");
            check(!RulesProfiles.get(RulesProfiles.MANUAL).worldCombat(),"manual mode does not run world combat");
            double move=RulesProfiles.get(RulesProfiles.TACTICAL).movement(repo.character(character).stats(),true);
            check(move==4,"trusted profile consumes authoritative stats");
            String percentile=Files.readString(Path.of("packs/percentile/characters/ines_marlowe.json"));
            UUID manual=repo.submit(member,campaign,RulesProfiles.MANUAL,percentile);
            check(repo.character(manual).sheet().equals(percentile),"percentile data preserved without formula evaluation");
            repo.stat(member,manual,1,"resource_luck",55);
            check(new CharacterRepository(file).character(manual).stats().get("resource_luck")==55,"bounded generic resources survive reload");
            Files.writeString(dir.resolve(".orphan.tmp"),"interrupted write");
            check(new CharacterRepository(file).state().characters().size()==2,"orphan temp ignored");
            Files.writeString(file,"corrupt primary");
            var recovered=new CharacterRepository(file);
            check(recovered.recoveredFromBackup(),"backup recovery reported");
            check(recovered.state().characters().containsKey(character),"valid backup restored");
            Files.writeString(file,"{\"schema\":999}");
            reject(()->new CharacterRepository(file),"future schema must not silently roll back to backup");
            Files.delete(file); Files.createDirectory(file);
            var previous=repo.state();
            reject(()->repo.stat(member,character,5,"ac",18),"failed write reported");
            check(repo.state()==previous,"failed write does not publish new memory state");
            // A campaign with no valid primary or backup must not reset to an empty save.
            Files.writeString(dir.resolve("state.json.bak"),"broken backup");
            reject(()->new CharacterRepository(file),"both corrupt copies fail closed");
        } finally {
            try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
        System.out.println("Character repository checks passed: "+checks);
    }
}
