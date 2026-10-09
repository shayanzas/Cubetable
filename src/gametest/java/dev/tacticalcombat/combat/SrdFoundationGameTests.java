package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import net.minecraft.entity.EntityType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;

/** Actual server binding/lifecycle, not remote clients or an enabled SRD encounter. */
public class SrdFoundationGameTests implements FabricGameTest {
    private static void check(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
    private static void rejects(Runnable task,String message) {
        try { task.run(); } catch(IllegalArgumentException expected) { return; }
        throw new IllegalStateException(message);
    }
    private static final class Fixture implements AutoCloseable {
        final TestContext ctx; final Path dir; final java.lang.reflect.Field field; final Object original;
        final ServerPlayerEntity player; final CharacterRepository.Principal dm;
        final UUID campaign; final String sheet;
        Fixture(TestContext ctx) throws Exception {
            this.ctx=ctx; dir=Files.createTempDirectory("cubetable-srd-world-");
            field=CharacterService.class.getDeclaredField("repository"); field.setAccessible(true); original=field.get(null);
            field.set(null,new CharacterRepository(dir.resolve("state.json")));
            player=ctx.createMockCreativeServerPlayerInWorld(); dm=new CharacterRepository.Principal(player.getUuid(),true);
            campaign=repo().createCampaign(dm,"SRD calculation tests");
            try(var stream=Objects.requireNonNull(getClass().getResourceAsStream("/srd-test-character.json"))) {
                sheet=new String(stream.readAllBytes(),StandardCharsets.UTF_8);
            }
        }
        CharacterRepository repo() { return CharacterService.repository(); }
        UUID character(String profile,String sheet) throws Exception {
            UUID id=repo().submit(dm,campaign,profile,sheet); repo().approve(dm,id,1); return id;
        }
        public void close() throws Exception {
            CharacterService.forget(player.getUuid()); ctx.getWorld().getServer().getPlayerManager().remove(player);
            field.set(null,original);
            try(var files=Files.walk(dir)) { for(Path p:files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void srdBindingUsesApprovedDataWithoutVanillaHealthIntegration(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            UUID id=f.character(RulesProfiles.SRD,f.sheet);
            var mob=ctx.spawnEntity(EntityType.ZOMBIE,new BlockPos(2,2,2)); mob.setAiDisabled(true);
            mob.setHealth(7); float maximum=mob.getMaxHealth();
            CharacterService.bind(f.player,id,2,mob);
            var snapshot=CharacterService.srdStats(mob,f.campaign);
            check(snapshot.stats().hp()==12 && snapshot.stats().armorClass()==13,"explicit server SRD inputs");
            CharacterService.apply(mob); CharacterService.apply(mob);
            check(mob.getMaxHealth()==maximum && mob.getHealth()==7,"SRD binding neither changes vanilla HP nor stacks Tactical modifiers");
            mob.setHealth(3); CharacterService.captureEntity(mob); CharacterService.capture(ctx.getWorld().getServer());
            check(CharacterService.srdStats(mob,f.campaign).equals(snapshot),"vanilla health capture does not overwrite D&D HP");
            var result=SrdRules.attack(snapshot.stats(),Weapon.LONGSWORD,SrdRules.AttackMode.MELEE,
                    SrdCharacter.Ability.STR,false,13,false,false,sides->sides);
            check(result.critical() && result.damage().amount()==19,"server-owned maximum-face dice resolve critical");
            check(mob.getHealth()==3 && CharacterService.srdStats(mob,f.campaign).equals(snapshot),"pure resolution does not apply either vanilla or SRD damage twice");
            fieldReload(f);
            check(CharacterService.srdStats(mob,f.campaign).equals(snapshot),"approved bound SRD state survives repository reload");
            CharacterService.unbind(f.player,mob);
            rejects(()->CharacterService.srdStats(mob,f.campaign),"unbound adapter must reject");
        }
        ctx.complete();
    }
    private static void fieldReload(Fixture f) throws Exception { f.field.set(null,new CharacterRepository(f.dir.resolve("state.json"))); }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void malformedAndWrongProfileBindingsFailClosed(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var mob=ctx.spawnEntity(EntityType.ZOMBIE,new BlockPos(2,2,2)); mob.setAiDisabled(true);
            UUID invalid=f.character(RulesProfiles.SRD,"{\"format\":\"generic_d20\",\"values\":{\"dex\":20}}");
            CharacterService.bind(f.player,invalid,2,mob);
            rejects(()->CharacterService.srdStats(mob,f.campaign),"legacy D20 input must not implicitly become SRD");
            CharacterService.unbind(f.player,mob);
            UUID tactical=f.character(RulesProfiles.TACTICAL,f.sheet);
            CharacterService.bind(f.player,tactical,2,mob);
            rejects(()->CharacterService.srdStats(mob,f.campaign),"Tactical bindings cannot use SRD calculations");
            CharacterService.unbind(f.player,mob);
            UUID srd=f.character(RulesProfiles.SRD,f.sheet); CharacterService.bind(f.player,srd,2,mob);
            rejects(()->CharacterService.srdStats(mob,UUID.randomUUID()),"cross campaign rejected");
            check(!CharacterService.mayJoin(mob,f.campaign,RulesProfiles.get(RulesProfiles.TACTICAL)),"SRD actor cannot enter Tactical combat");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void srdWorldCombatRemainsGatedPendingAuthoritativeAttackPipeline(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            f.repo().profile(f.dm,f.campaign,1,RulesProfiles.SRD); CharacterService.select(f.player,f.campaign);
            UUID id=f.character(RulesProfiles.SRD,f.sheet); CharacterService.bind(f.player,id,2,f.player);
            check(!RulesProfiles.get(RulesProfiles.SRD).worldCombat(),"core calculations alone do not enable SRD engine");
            check(CombatManager.startAround(ctx.getWorld(),f.player,false)==null,"SRD default cannot accidentally run vanilla encounter");
            check(CombatManager.startAround(ctx.getWorld(),f.player,false,new CharacterService.Context(f.campaign,RulesProfiles.TACTICAL))==null,"explicit Tactical override cannot convert bound SRD actor");
            check(!CombatManager.isInCombat(f.player),"failed starts leave actor unclaimed");
            check(!RulesProfiles.get(RulesProfiles.MANUAL).worldCombat(),"manual profile stays isolated");
            check(RulesProfiles.get(RulesProfiles.TACTICAL).movement(Map.of(),true)==8,"legacy Tactical movement untouched");
        }
        ctx.complete();
    }
}
