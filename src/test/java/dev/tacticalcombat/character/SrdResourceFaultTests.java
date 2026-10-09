package dev.tacticalcombat.character;

import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class SrdResourceFaultTests {
    private static int count;
    private static void check(boolean value,String why) { count++; if(!value) throw new AssertionError(why); }
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("cubetable-srd-write-fault-"); Path path=dir.resolve("state.json");
        try {
            var dm=new CharacterRepository.Principal(UUID.randomUUID(),true); var repo=new CharacterRepository(path);
            UUID campaign=repo.createCampaign(dm,"Fault"),actor=UUID.randomUUID();
            UUID id=repo.submit(dm,campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());
            repo.approve(dm,id,1); repo.bind(dm,id,2,actor,null);
            var snapshot=repo.srdSnapshot(actor,campaign); String bytes=Files.readString(path);
            var failing=new CharacterRepository(new AtomicJsonFile(path,destination->{if(destination.equals(path)) throw new IOException("Injected SRD HP save interruption");}));
            var before=failing.state(); boolean failed=false;
            try { failing.commitSrdHealth(snapshot,new SrdHealth(4,10,2)); } catch(IOException expected) { failed=true; }
            check(failed,"HP storage error propagates to accepted-effect caller");
            check(failing.state()==before && failing.srdSnapshot(actor,campaign).equals(snapshot),"failed HP write publishes neither health nor revision");
            check(Files.readString(path).equals(bytes),"failed HP write leaves primary bytes intact");
            check(new CharacterRepository(path).srdSnapshot(actor,campaign).equals(snapshot),"restart sees prior HP and revision");
            var updated=repo.commitSrdHealth(snapshot,new SrdHealth(4,10,2));
            check(new CharacterRepository(path).srdSnapshot(actor,campaign).equals(updated),"successful recovery persists HP/temp and identity");
            Files.writeString(path,"broken primary");
            var recovered=new CharacterRepository(path);
            check(recovered.recoveredFromBackup() && recovered.srdSnapshot(actor,campaign).equals(snapshot),"backup recovery explicitly rolls back HP and runtime revision together");
        } finally { try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        System.out.println("SRD resource fault checks passed: "+count);
    }
}
