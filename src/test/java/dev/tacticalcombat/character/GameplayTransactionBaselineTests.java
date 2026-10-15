package dev.tacticalcombat.character;

import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.util.*;

/** Frozen before J2 production changes: both participants advance, with no rebase or partial save. */
public final class GameplayTransactionBaselineTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    public static void run()throws Exception{
        Path dir=Files.createTempDirectory("j2-frozen-");
        try{
            for(boolean hit:List.of(false,true))for(var mode:SrdRules.AttackMode.values()){
                Path path=dir.resolve(mode+"-"+hit+".json");var repo=new CharacterRepository(path);var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);UUID campaign=repo.createCampaign(dm,"Frozen");UUID attacker=UUID.randomUUID(),target=UUID.randomUUID();
                var sheet=SrdAuthoring.template();var data=SrdAuthoring.data(sheet);data.addProperty("arrows",2);data.addProperty("daggers",2);data.add("weapons",com.google.gson.JsonParser.parseString("[\"unarmed\",\"dagger\",\"shortbow\"]"));
                for(UUID actor:List.of(attacker,target)){UUID id=repo.submit(dm,campaign,RulesProfiles.SRD,sheet.toString());repo.approve(dm,id,1);repo.bind(dm,id,2,actor,null);}
                var a=repo.srdSnapshot(attacker,campaign);var b=repo.srdSnapshot(target,campaign);var weapon=mode==SrdRules.AttackMode.RANGED?SrdEquipment.Weapon.SHORTBOW:mode==SrdRules.AttackMode.THROWN?SrdEquipment.Weapon.DAGGER:SrdEquipment.Weapon.UNARMED;
                var saved=repo.commitSrdAction(a,b,new SrdHealth(hit?7:10,10,0),weapon,mode);
                check(saved.get(0).runtimeRevision()==a.runtimeRevision()+1&&saved.get(1).runtimeRevision()==b.runtimeRevision()+1,"both runtime revisions advance even on miss");
                check(saved.get(1).stats().hp()==(hit?7:10),"target HP committed");check(saved.get(0).stats().arrows()==(mode==SrdRules.AttackMode.RANGED?1:2),"arrow spend on hit and miss");check(saved.get(0).stats().daggers()==(mode==SrdRules.AttackMode.THROWN?1:2),"thrown stock spend");
                String bytes=Files.readString(path);boolean rejected=false;try{repo.commitSrdAction(a,b,new SrdHealth(1,10,0),weapon,mode);}catch(IllegalArgumentException expected){rejected=true;}check(rejected&&Files.readString(path).equals(bytes),"stale snapshot cannot spend again");
                var restart=new CharacterRepository(path);check(restart.srdSnapshot(attacker,campaign).equals(saved.getFirst())&&restart.srdSnapshot(target,campaign).equals(saved.getLast()),"restart keeps both records coherent");
            }
        }finally{try(var files=Files.walk(dir)){for(var p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}}
        System.out.println("J2 frozen transaction checks passed: "+count);
    }
}
