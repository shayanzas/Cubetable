package dev.tacticalcombat.character;

import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.*;
import java.util.*;
import java.io.IOException;

/** Real atomic-file failures, restart and hostile principals. */
public final class MonsterStageTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    @FunctionalInterface interface Task{void run()throws Exception;}
    private static void reject(Task task)throws Exception{try{task.run();}catch(IllegalArgumentException|IOException expected){count++;return;}throw new AssertionError("Expected rejection");}
    public static void run()throws Exception{
        var dir=Files.createTempDirectory("monster-stages-");
        try{
            var file=dir.resolve("campaign.json");var repo=new CharacterRepository(file);
            var dm=new CharacterRepository.Principal(UUID.randomUUID(),true);var player=new CharacterRepository.Principal(UUID.randomUUID(),false);
            var campaign=repo.createCampaign(dm,"Monsters");repo.profile(dm,campaign,1,RulesProfiles.SRD);repo.invite(dm,campaign,2,player.id(),false);
            var monsterActors=new HashMap<String,UUID>();
            for(var profile:MonsterProfiles.ALL){
                MonsterProfiles.verify(profile);
                var catalog=dev.tacticalcombat.reference.SrdReferenceCatalog.get().find(profile.id()).data();
                String actions=switch(profile.name()){
                    case "Zombie" -> "Slam. Melee Weapon Attack: +3 to hit, reach 5 ft., one target. Hit: 4 (1d6 + 1) bludgeoning damage.";
                    case "Skeleton" -> "Shortsword. Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) piercing damage. Shortbow. Ranged Weapon Attack: +4 to hit, range 80/320 ft., one target. Hit: 5 (1d6 + 2) piercing damage.";
                    default -> "Scimitar. Melee Weapon Attack: +4 to hit, reach 5 ft., one target. Hit: 5 (1d6 + 2) slashing damage. Shortbow. Ranged Weapon Attack: +4 to hit, range 80/320 ft., one target. Hit: 5 (1d6 + 2) piercing damage.";
                };
                check(catalog.get("actions").getAsString().equals(actions),"reviewed action text matches verified catalog, no runtime parsing");
                check(catalog.get("immunities").getAsString().equals(String.join(", ",profile.immunities()))&&catalog.get("vulnerabilities").getAsString().equals(String.join(", ",profile.vulnerabilities())),"reviewed defenses match source");
                var actor=UUID.randomUUID();var op=UUID.randomUUID();monsterActors.put(profile.name(),actor);
                reject(()->repo.prepareMonster(player,campaign,op,profile.id(),actor));
                repo.prepareMonster(dm,campaign,op,profile.id(),actor);var record=repo.character(op);var stats=repo.srdSnapshot(actor,campaign).stats();
                check(record.approved()&&record.owner().equals(dm.id())&&actor.equals(repo.binding(op)),"atomic approved identity and binding");
                check(stats.hp()==profile.hp()&&stats.armorClass()==profile.ac()&&stats.speedFeet()==profile.speed(),"source statistics");
                var before=repo.state();check(repo.prepareMonster(dm,campaign,op,profile.id(),actor).equals(op)&&repo.state()==before,"retry does not mutate");
                reject(()->repo.prepareMonster(dm,campaign,op,profile.id(),UUID.randomUUID()));
                reject(()->repo.submit(dm,campaign,RulesProfiles.SRD,record.sheet()));
                for(var weapon:stats.weapons()){
                    var mode=weapon==SrdEquipment.Weapon.SHORTBOW?SrdRules.AttackMode.RANGED:SrdRules.AttackMode.MELEE;
                    var ability=profile.name().equals("Zombie")?SrdCharacter.Ability.STR:SrdCharacter.Ability.DEX;
                    var plan=SrdRules.plan(stats,weapon,mode,ability,weapon==SrdEquipment.Weapon.SHORTBOW);
                    check(plan.attackModifier()==(profile.name().equals("Zombie")?3:4)&&plan.dice()==1&&plan.sides()==6&&plan.flatDamage()==(profile.name().equals("Zombie")?1:2),"reviewed monster attack");
                }
                var forged=com.google.gson.JsonParser.parseString(record.sheet()).getAsJsonObject();SrdAuthoring.data(forged).addProperty("ac_bonus",99);reject(()->SrdCharacterCodec.parseSheet(forged.toString()));
                if(profile.name().equals("Zombie"))check(dev.tacticalcombat.checks.CharacterChecks.resolve(record.sheet(),new dev.tacticalcombat.checks.CharacterChecks.Key(dev.tacticalcombat.checks.CharacterChecks.Category.SAVE,"wis")).total()==0,"Zombie wisdom save");
                if(profile.name().equals("Goblin"))check(dev.tacticalcombat.checks.CharacterChecks.resolve(record.sheet(),new dev.tacticalcombat.checks.CharacterChecks.Key(dev.tacticalcombat.checks.CharacterChecks.Category.SKILL,"Stealth")).total()==6,"Goblin stealth");
                var snapshot=repo.srdSnapshot(actor,campaign);repo.commitSrdHealth(snapshot,new SrdHealth(1,profile.hp(),0));
                check(new CharacterRepository(file).srdSnapshot(actor,campaign).stats().hp()==1,"monster HP persists across restart");
            }
            var attacker=repo.srdSnapshot(monsterActors.get("Skeleton"),campaign);var target=repo.srdSnapshot(monsterActors.get("Zombie"),campaign);
            repo.commitSrdHealth(target,new SrdHealth(22,22,0));target=repo.srdSnapshot(monsterActors.get("Zombie"),campaign);
            var attack=new GameplayActionPlan.Attack(SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true,0,false,false);
            var outcome=SrdRules.attack(attacker.stats(),attack.weapon(),attack.mode(),attack.ability(),true,target.stats().armorClass(),false,false,sides->sides==20?15:3);
            var plan=GameplayActionPlan.weapon(UUID.randomUUID(),dm.id(),campaign,UUID.randomUUID(),1,attacker,dm.id(),target,dm.id(),attack,outcome);
            repo.prepareGameplay(dm,plan);reject(()->repo.unbind(dm,attacker.actor()));
            var failedCommit=new CharacterRepository(new AtomicJsonFile(file,destination->{if(destination.equals(file))throw new IOException("Injected monster action commit failure");}));
            reject(()->failedCommit.commitGameplay(dm,plan.operation(),plan.fingerprint()));
            check(new CharacterRepository(file).srdSnapshot(attacker.actor(),campaign).stats().arrows()==20&&new CharacterRepository(file).srdSnapshot(target.actor(),campaign).stats().hp()==22,"prepared monster attack failure retains both resources");
            repo.commitGameplay(dm,plan.operation(),plan.fingerprint());repo.commitGameplay(dm,plan.operation(),plan.fingerprint());
            var restart=new CharacterRepository(file);check(restart.srdSnapshot(attacker.actor(),campaign).stats().arrows()==19&&restart.srdSnapshot(target.actor(),campaign).stats().hp()==17,"monster damage and ammunition commit once and survive restart");
            var fault=new CharacterRepository(new AtomicJsonFile(file,destination->{if(destination.equals(file))throw new IOException("Injected NPC commit failure");}));
            var op=UUID.randomUUID();var actor=UUID.randomUUID();var before=Files.readString(file);reject(()->fault.prepareMonster(dm,campaign,op,MonsterProfiles.ALL.getFirst().id(),actor));
            check(Files.readString(file).equals(before)&&!fault.state().characters().containsKey(op)&&!fault.state().bindings().containsKey(actor),"failed preparation changes neither records nor bindings");
            check(!new CharacterRepository(file).state().characters().containsKey(op),"restart after failed preparation");
            var path=dir.resolve("stages.json");var store=new EncounterStageStore(path);check(store.list(repo,dm,campaign).isEmpty(),"missing stage file starts empty without changing campaign");var stage=new EncounterStageStore.Stage(UUID.randomUUID(),1,campaign,dm.id(),"Crypt","Test","minecraft:overworld",0,0,64,List.of(),1,"SRD5.1-2014",1,1);
            reject(()->store.create(repo,player,stage));store.create(repo,dm,stage);check(store.create(repo,dm,stage).equals(stage),"stage create replay");
            var slot=new EncounterStageStore.Slot(UUID.randomUUID(),"Guard",2,2,64,MonsterProfiles.ALL.getFirst().id(),null);
            var updated=store.replace(repo,dm,stage.id(),1,"Crypt","Test",List.of(slot),2);
            check(new EncounterStageStore(path).get(repo,dm,stage.id()).equals(updated),"stage restart retains slots/provenance");
            reject(()->store.replace(repo,dm,stage.id(),1,"stale","",List.of(),3));
            reject(()->store.replace(repo,dm,stage.id(),2,"duplicate","",List.of(slot,slot),3));
            reject(()->new EncounterStageStore.Slot(UUID.randomUUID(),"bad",0,0,Double.NaN,null,null));
            reject(()->new EncounterStageStore.Slot(UUID.randomUUID(),"bad",30000000,0,64,null,null));
            reject(()->new EncounterStageStore.Stage(UUID.randomUUID(),1,campaign,dm.id(),"Bad","","../overworld",0,0,64,List.of(),1,"SRD5.1-2014",1,1));
            reject(()->store.replace(repo,dm,stage.id(),2,"outside","",List.of(new EncounterStageStore.Slot(UUID.randomUUID(),"far",20,0,64,null,null)),3));
            var coDm=new CharacterRepository.Principal(UUID.randomUUID(),false);repo.invite(dm,campaign,3,coDm.id(),true);check(store.get(repo,coDm,stage.id()).equals(updated),"co-DM stage access");repo.revokeDm(dm,campaign,4,coDm.id());reject(()->store.get(repo,coDm,stage.id()));
            var failing=new EncounterStageStore(new AtomicJsonFile(path,destination->{if(destination.equals(path))throw new IOException("Injected stage commit failure");}));
            before=Files.readString(path);reject(()->failing.delete(repo,dm,stage.id(),2));check(Files.readString(path).equals(before)&&failing.get(repo,dm,stage.id()).equals(updated),"failed delete retains disk and memory");
            store.delete(repo,dm,stage.id(),2);reject(()->new EncounterStageStore(path).create(repo,dm,stage));
            Files.writeString(path,"broken primary");check(new EncounterStageStore(path).recoveredFromBackup(),"corrupt stage primary reports backup recovery");
            Files.writeString(path,"{\"schema\":999}");reject(()->new EncounterStageStore(path));
        }finally{try(var files=Files.walk(dir)){for(var file:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(file);}}
        System.out.println("Monster and stage checks passed: "+count);
    }
}
