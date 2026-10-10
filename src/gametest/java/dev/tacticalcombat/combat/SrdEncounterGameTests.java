package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.item.*;
import net.minecraft.test.*;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.util.*;

public class SrdEncounterGameTests implements FabricGameTest {
    static void check(boolean value,String why) { if(!value) throw new IllegalStateException(why); }
    static void rejects(Runnable operation,String why) {
        try { operation.run(); } catch(IllegalArgumentException expected) { return; }
        throw new IllegalStateException(why);
    }
    static final class Fixture implements AutoCloseable {
        final TestContext ctx; final Path dir; final java.lang.reflect.Field field; final Object old;
        final CharacterRepository repo; final CharacterRepository.Principal dm;
        final UUID campaign; final BlockPos origin;
        final List<LivingEntity> entities=new ArrayList<>();
        final List<Encounter> encounters=new ArrayList<>();
        Fixture(TestContext ctx) throws Exception {
            this(ctx,ctx.getAbsolutePos(new BlockPos(0,1,0)));
        }
        Fixture(TestContext ctx,BlockPos origin) throws Exception {
            this.ctx=ctx; dir=Files.createTempDirectory("cubetable-srd-encounter-");
            field=CharacterService.class.getDeclaredField("repository"); field.setAccessible(true); old=field.get(null);
            repo=new CharacterRepository(dir.resolve("state.json")); field.set(null,repo);
            dm=new CharacterRepository.Principal(UUID.randomUUID(),true); campaign=repo.createCampaign(dm,"Controlled SRD");
            this.origin=origin;
            for(int x=0;x<12;x++) for(int z=0;z<9;z++) {
                ctx.getWorld().setBlockState(origin.add(x,0,z),Blocks.STONE.getDefaultState());
                for(int y=1;y<5;y++) ctx.getWorld().setBlockState(origin.add(x,y,z),Blocks.AIR.getDefaultState());
            }
        }
        ZombieEntity actor(int x,int z,boolean bind) throws Exception {
            var actor=EntityType.ZOMBIE.create(ctx.getWorld());
            actor.refreshPositionAndAngles(origin.getX()+x+.5,origin.getY()+1,origin.getZ()+z+.5,0,0);
            actor.setAiDisabled(true); ctx.getWorld().spawnEntity(actor); entities.add(actor);
            if(bind) {
                UUID id=repo.submit(dm,campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());
                repo.approve(dm,id,1); repo.bind(dm,id,2,actor.getUuid(),null);
            }
            return actor;
        }
        SrdEncounter start(LivingEntity... actors) {
            var encounter=SrdEncounter.start(ctx.getWorld(),dm,campaign,List.of(actors)); encounters.add(encounter); return encounter;
        }
        public void close() throws Exception {
            for(var encounter:encounters) CombatManager.endCombat(encounter);
            for(var actor:entities) actor.discard(); field.set(null,old);
            try(var paths=Files.walk(dir)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void sharedOwnershipAcrossTacticalAndTwoSrdEncounters(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true); var b=f.actor(3,1,true); var c=f.actor(6,1,true); var d=f.actor(8,1,true);
            var first=f.start(a,b); var second=f.start(c,d);
            check(first!=second && !first.id().equals(second.id()),"stable independent encounter identities");
            check(CombatManager.encounter(a)==first && CombatManager.encounter(c)==second,"one shared lookup for both SRD fights");
            check(CombatManager.isInCombat(a) && CombatManager.isInCombat(a.getUuid()) && CombatManager.get(a)==null,"generic ownership distinct from Tactical adapter");
            rejects(()->f.start(a,c),"overlap must reject");
            var tacticalActor=f.actor(10,5,false);
            var tactical=Combat.start(ctx.getWorld(),List.of(),List.of(tacticalActor)); f.encounters.add(tactical);
            check(tactical!=null && !CombatManager.claim(a,tactical) && !CombatManager.claim(tacticalActor,first),"Tactical and SRD cannot claim one entity");
            CombatManager.release(a,second);
            check(CombatManager.encounter(a)==first,"stale release cannot evict owner");
            CombatManager.endCombat(first);
            check(!CombatManager.isInCombat(a) && CombatManager.encounter(c)==second && CombatManager.encounter(tacticalActor)==tactical,"cleanup leaves unrelated encounters intact");
            var replacement=f.start(a,b); first.end();
            check(CombatManager.encounter(a)==replacement,"old cleanup cannot evict replacement encounter");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void srdOwnershipBlocksVanillaDamageAndUnsupportedEffects(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true); var b=f.actor(3,1,true); var c=f.actor(6,1,true); var d=f.actor(8,1,true);
            f.start(a,b); f.start(c,d); var outsider=f.actor(10,5,false);
            float hp=b.getHealth();
            check(!b.damage(a.getDamageSources().mobAttack(a),5) && b.getHealth()==hp,"same SRD encounter cannot use vanilla HP");
            check(!c.damage(a.getDamageSources().mobAttack(a),5),"cross-SRD damage rejected");
            check(!b.damage(b.getDamageSources().onFire(),5),"environment damage cannot bypass controlled SRD isolation");
            check(!outsider.damage(a.getDamageSources().mobAttack(a),5) && !a.damage(outsider.getDamageSources().mobAttack(outsider),5),"inbound/outbound boundary");
            java.util.function.Supplier<Boolean> forbidden=()->{throw new IllegalStateException("Vanilla effect executed");};
            check(!CombatActions.melee(a,b,forbidden) && !CombatActions.melee(outsider,a,forbidden),"melee adapter refuses unsupported actors/targets");
            check(!CombatActions.resolve(a,forbidden) && !CombatActions.volley(a,forbidden) && !CombatActions.beam(a,b,forbidden),"resolve/volley/beam adapters blocked");
            var arrow=new ArrowEntity(ctx.getWorld(),a,new ItemStack(Items.ARROW),null);
            check(!CombatActions.canProjectileAffect(arrow,b) && !CombatActions.canProjectileAffect(arrow,outsider),"old or newly created projectiles cannot cross boundary");
            CombatActions.projectileLoaded(arrow,0); check(arrow.isRemoved(),"unsupported SRD projectile discarded before ticking");
            check(!CombatInteractions.equipmentAllowed(a),"unsupported equipment changes blocked");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void admissionIsAtomicAndLifecycleReleasesUnavailableActors(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true); var b=f.actor(3,1,false);
            rejects(()->f.start(a,b),"unbound vanilla mob not silently converted");
            check(!CombatManager.isInCombat(a),"failed set admission leaves no partial ownership");
            var c=f.actor(6,1,true);
            rejects(()->SrdEncounter.start(ctx.getWorld(),new CharacterRepository.Principal(UUID.randomUUID(),false),f.campaign,List.of(a,c)),"outsider cannot start an encounter");
            rejects(()->f.start(a,a),"duplicate actor rejected");
            a.setAiDisabled(false);
            var encounter=f.start(a,c); check(a.isAiDisabled(),"SRD never runs vanilla NPC AI");
            var anchor=a.getPos(); a.setPosition(anchor.add(1,2,0)); encounter.tick();
            check(a.getPos().equals(anchor),"unauthorized vertical/horizontal displacement corrected");
            c.discard(); check(encounter.tick(),"unloaded actor requests orderly termination");
            CombatManager.endCombat(encounter);
            check(!CombatManager.isInCombat(a) && !CombatManager.isInCombat(c) && !a.isAiDisabled(),"ownership released and prior AI restored");
            check(!RulesProfiles.get(RulesProfiles.SRD).worldCombat(),"internal lifecycle does not enable uncontrolled world combat");
        }
        ctx.complete();
    }
}
