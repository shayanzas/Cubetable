package dev.tacticalcombat.character;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public final class PersistenceFaultTests {
    private static int checks;
    private static void check(boolean condition,String why) { checks++; if(!condition) throw new AssertionError(why); }
    public static void run() throws Exception {
        Path dir=Files.createTempDirectory("cubetable-atomic-fault-"); Path path=dir.resolve("state.json");
        try {
            var admin=new CharacterRepository.Principal(UUID.randomUUID(),true);
            var repo=new CharacterRepository(path); UUID campaign=repo.createCampaign(admin,"Before interruption");
            String good=Files.readString(path);
            var failing=new CharacterRepository(new AtomicJsonFile(path,destination->{
                if(destination.equals(path)) throw new IOException("Injected failure after temporary data flush and before primary rename");
            }));
            var old=failing.state(); boolean failed=false;
            try { failing.policy(admin,campaign,1,new ApprovalPolicy(false,false,false,Set.of())); }
            catch(IOException expected) { failed=true; }
            check(failed,"pre-rename interruption reported");
            check(failing.state()==old,"interrupted write never publishes in memory");
            check(Files.readString(path).equals(good),"interruption preserves primary bytes");
            check(Files.readString(path.resolveSibling("state.json.bak")).equals(good),"interruption preserves validated backup");
            try(var paths=Files.list(dir)) { check(paths.noneMatch(p->p.toString().endsWith(".tmp")),"failed write cleans temporary file"); }
            check(new CharacterRepository(path).campaign(campaign).policy().requireNewApproval(),"normal reload sees previous policy");
            UUID unicode=repo.createCampaign(admin,"Café 🐉");
            check(new CharacterRepository(path).campaign(unicode).name().equals("Café 🐉"),"atomic byte encoding preserves multibyte text");
            Path oversized=dir.resolve("oversized.json");boolean bounded=false;
            try {new AtomicJsonFile(oversized).write("é".repeat(8*1024*1024+1),value->value);} catch(IOException expected) {bounded=true;}
            check(bounded && !Files.exists(oversized),"16 MiB bound counts UTF-8 bytes before publication");
        } finally { try(var paths=Files.walk(dir)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); } }
        System.out.println("Persistence interruption checks passed: "+checks);
    }
}
