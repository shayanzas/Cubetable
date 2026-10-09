package dev.tacticalcombat.combat;

import dev.tacticalcombat.testmixin.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.*;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import net.minecraft.util.Hand;
import net.minecraft.item.*;
import net.minecraft.block.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ChargedProjectilesComponent;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import java.util.*;

public class PhaseOneGameTests implements FabricGameTest {
    private static void check(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    private static <T extends MobEntity> T mob(TestContext ctx, EntityType<T> type, int x, int z) {
        T mob = ctx.spawnEntity(type, new BlockPos(x, 2, z)); mob.setAiDisabled(true); return mob;
    }
    private static class Fight implements AutoCloseable {
        final ServerPlayerEntity player;
        final Combat combat;
        Fight(TestContext ctx, MobEntity... mobs) {
            for (int x=0;x<7;x++) for (int z=0;z<7;z++) ctx.setBlockState(new BlockPos(x,1,z), Blocks.STONE);
            player = ctx.createMockCreativeServerPlayerInWorld();
            player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
            Vec3d pos = ctx.getAbsolute(new Vec3d(1.5,2,5.5));
            player.refreshPositionAndAngles(pos.x,pos.y,pos.z,0,0);
            combat = Combat.start(ctx.getWorld(), List.of(player), List.of(mobs));
        }
        void turn(net.minecraft.entity.Entity actor) { while (!combat.isTurnOf(actor)) combat.endTurn(); }
        public void close() { combat.end(); player.getServer().getPlayerManager().remove(player); }
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void crossbowVolleyRetainsEffectsAfterOwnerRemoval(TestContext ctx) {
        var shooter=mob(ctx,EntityType.PILLAGER,1,2); var target=mob(ctx,EntityType.ZOMBIE,4,2);
        try (Fight f=new Fight(ctx,shooter,target)) {
            f.turn(shooter);
            ItemStack crossbow=new ItemStack(Items.CROSSBOW);
            crossbow.set(DataComponentTypes.CHARGED_PROJECTILES,ChargedProjectilesComponent.of(List.of(
                    new ItemStack(Items.ARROW),new ItemStack(Items.ARROW),new ItemStack(Items.ARROW))));
            ((CrossbowItem)Items.CROSSBOW).shootAll(ctx.getWorld(),shooter,Hand.MAIN_HAND,crossbow,1,0,target);
            var arrows=ctx.getWorld().getEntitiesByClass(PersistentProjectileEntity.class,shooter.getBoundingBox().expand(4),
                    a->a.getOwner()==shooter && !a.isRemoved());
            check(arrows.size()==3,"all three volley arrows accepted");
            check(f.combat.get(shooter).actionUsed,"one volley costs one action");
            f.combat.endTurn();
            shooter.discard();
            for (var arrow:arrows) {
                arrow.setOwner(null);
                arrow.setVelocity(1,0,0); arrow.setDamage(2); target.timeUntilRegen=0;
                float hp=target.getHealth();
                ((ArrowAccess)arrow).impact(new net.minecraft.util.hit.EntityHitResult(target));
                check(target.getHealth()<hp,"actual impact after owner removal");
                hp=target.getHealth(); target.timeUntilRegen=0;
                ((ArrowAccess)arrow).impact(new net.minecraft.util.hit.EntityHitResult(target));
                check(target.getHealth()==hp,"each arrow effect deduplicated");
                arrow.discard();
            }
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void creeperExplosionHasMultipleVictims(TestContext ctx) {
        var creeper=mob(ctx,EntityType.CREEPER,3,3);
        var a=mob(ctx,EntityType.ZOMBIE,2,3); var b=mob(ctx,EntityType.ZOMBIE,4,3);
        try(Fight f=new Fight(ctx,creeper,a,b)) {
            f.turn(creeper); float ah=a.getHealth(),bh=b.getHealth();
            creeper.addStatusEffect(new net.minecraft.entity.effect.StatusEffectInstance(net.minecraft.entity.effect.StatusEffects.SLOWNESS,200));
            ((CreeperAccess)creeper).explodeNow();
            check(a.getHealth()<ah && b.getHealth()<bh,"actual explosion damages both victims");
            check(f.combat.get(creeper).actionUsed,"explosion spends one action");
            check(ctx.getWorld().getEntitiesByClass(net.minecraft.entity.AreaEffectCloudEntity.class,creeper.getBoundingBox().expand(4),cloud->true).isEmpty(),"creeper lingering cloud explicitly unsupported");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void guardianBeamAndSlimeContact(TestContext ctx) {
        var guardian=mob(ctx,EntityType.GUARDIAN,1,2); var target=mob(ctx,EntityType.ZOMBIE,5,2);
        var slime=mob(ctx,EntityType.SLIME,4,2); slime.setSize(2,true);
        try(Fight f=new Fight(ctx,guardian,target,slime)) {
            var goal=((MobGoalsAccess)guardian).goals().getGoals().stream().map(g->g.getGoal())
                    .filter(g->g instanceof BeamAccess).findFirst().orElseThrow();
            f.turn(guardian); guardian.setTarget(target);
            ((BeamAccess)goal).beamTicks(guardian.getWarmupTime()-1);
            float hp=target.getHealth(); goal.tick();
            check(f.combat.get(guardian).actionUsed && hp-target.getHealth()>1,"beam magic and physical component admitted");
            guardian.setTarget(target); ((BeamAccess)goal).beamTicks(guardian.getWarmupTime()-1);
            hp=target.getHealth(); goal.tick(); check(target.getHealth()==hp,"beam cannot repeat action");
            target.timeUntilRegen=0;
            f.turn(slime); hp=target.getHealth(); ((SlimeAccess)slime).contact(target);
            check(target.getHealth()<hp && f.combat.get(slime).actionUsed,"slime contact costs action");
            f.combat.endTurn(); target.timeUntilRegen=0; hp=target.getHealth(); ((SlimeAccess)slime).contact(target);
            check(target.getHealth()==hp,"out-of-turn contact blocked");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void guardianRetaliationIsConsequenceOfMelee(TestContext ctx) {
        var a=mob(ctx,EntityType.ZOMBIE,2,2); var guardian=mob(ctx,EntityType.GUARDIAN,3,2);
        try(Fight f=new Fight(ctx,a,guardian)) {
            f.turn(a); float hp=a.getHealth();
            check(a.tryAttack(guardian),"melee hits guardian");
            check(a.getHealth()<hp,"guardian spike retaliation admitted out of turn");
            check(!f.combat.get(guardian).actionUsed,"retaliation does not spend guardian action");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void itemUseEquipmentAndEntityInteractions(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            var p=f.player;
            f.turn(enemy); int slot=p.getInventory().selectedSlot;
            p.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket((slot+1)%9));
            check(p.getInventory().selectedSlot==slot,"out-of-turn equipment packet rejected");
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.POTION));
            check(!p.interactionManager.interactItem(p,ctx.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND).isAccepted(),"out-of-turn item use rejected");
            f.turn(p);
            p.networkHandler.onUpdateSelectedSlot(new UpdateSelectedSlotC2SPacket((slot+1)%9));
            check(p.getInventory().selectedSlot==(slot+1)%9,"active-turn equipment packet accepted");
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.POTION));
            check(p.interactionManager.interactItem(p,ctx.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND).isAccepted(),"potion starts");
            check(f.combat.get(p).actionUsed,"use starts one paid action");
            ((ConsumeAccess)p).consumeNow();
            check(p.getMainHandStack().isOf(Items.GLASS_BOTTLE),"authorized potion consumption completes");
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.SNOWBALL,2));
            check(!p.interactionManager.interactItem(p,ctx.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND).isAccepted(),"second paid use rejected");
            check(p.getMainHandStack().getCount()==2,"rejection preserves inventory");
            var stand=ctx.spawnEntity(EntityType.ARMOR_STAND,new BlockPos(1,2,4));
            check(CombatInteractions.entity(p,stand,()->net.minecraft.util.ActionResult.SUCCESS).isAccepted(),"equipment interaction free on turn");
            f.combat.endTurn();
            check(!CombatInteractions.entity(p,stand,()->{throw new IllegalStateException("out-of-turn mutation");}).isAccepted(),"entity equipment out of turn blocked");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void cancelledUseAndUnsupportedItemsPreserveAuthority(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            var p=f.player; f.turn(p);
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.ENDER_PEARL));
            check(!p.interactionManager.interactItem(p,ctx.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND).isAccepted(),"teleport items explicitly unsupported");
            check(!f.combat.get(p).actionUsed,"unsupported item costs nothing");
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.POTION));
            p.interactionManager.interactItem(p,ctx.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND);
            f.combat.endTurn();
            ((ConsumeAccess)p).consumeNow();
            check(p.getMainHandStack().isOf(Items.POTION),"late consume cannot finish cancelled use");
        }
        ctx.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void actualRouteInterruptionOnlyChargesProgress(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            f.turn(f.player);
            var pc=f.combat.get(f.player);
            var destination=ctx.getAbsolutePos(new BlockPos(5,2,5));
            f.combat.requestMove(f.player,destination);
            check(pc.moving(),"route accepted");
            check(pc.moveUsed==0,"route not charged before movement");
            f.combat.tick();
            double paid=pc.moveUsed;
            check(paid>0 && paid<4,"only traversed fraction charged");
            Vec3d stopped=f.player.getPos();
            ctx.setBlockState(new BlockPos(4,2,5),Blocks.STONE);
            f.combat.tick();
            check(!pc.moving() && pc.moveUsed==paid,"interruption preserves unspent allowance");
            check(f.player.getPos().squaredDistanceTo(stopped)<0.001,"no advance into invalid remaining route");
            f.combat.requestMove(f.player,destination);
            check(pc.moveUsed==paid,"retry never refunds completed progress");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void ascentDescentSlabsAndStairs(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            f.turn(f.player);
            ctx.setBlockState(new BlockPos(2,2,5),Blocks.STONE);
            f.combat.requestMove(f.player,ctx.getAbsolutePos(new BlockPos(2,3,5)));
            var pc=f.combat.get(f.player); check(pc.moving(),"one-block ascent accepted");
            for(int i=0;i<20 && pc.moving();i++) f.combat.tick();
            check(!pc.moving() && Math.abs(f.player.getY()-ctx.getAbsolutePos(new BlockPos(2,3,5)).getY())<0.01,"ascent reaches landing");
            check(Math.abs(pc.moveUsed-1)<0.001,"ascent costs one grid square");
            f.combat.requestMove(f.player,ctx.getAbsolutePos(new BlockPos(3,2,5)));
            check(pc.moving(),"ordinary descent accepted");
            for(int i=0;i<20 && pc.moving();i++) f.combat.tick();
            check(Math.abs(pc.moveUsed-2)<0.001,"descent costs one grid square");
            ctx.setBlockState(new BlockPos(4,2,5),Blocks.STONE_SLAB);
            var slab=dev.tacticalcombat.grid.Grid.centerOf(ctx.getWorld(),ctx.getAbsolutePos(new BlockPos(4,2,5)));
            check(CombatGeometry.validLanding(f.player,slab),"slab landing valid");
            f.combat.requestMove(f.player,ctx.getAbsolutePos(new BlockPos(4,2,5)));
            check(pc.moving(),"slab route accepted");
            for(int i=0;i<20 && pc.moving();i++) f.combat.tick();
            check(f.player.getPos().squaredDistanceTo(slab)<0.001,"slab reached");
            ctx.setBlockState(new BlockPos(5,2,5),Blocks.STONE_STAIRS);
            var stair=dev.tacticalcombat.grid.Grid.centerOf(ctx.getWorld(),ctx.getAbsolutePos(new BlockPos(5,3,5)));
            check(CombatGeometry.validLanding(f.player,stair),"stair top landing valid");
            f.combat.requestMove(f.player,ctx.getAbsolutePos(new BlockPos(5,3,5)));
            check(pc.moving(),"stair route accepted");
            for(int i=0;i<20 && pc.moving();i++) f.combat.tick();
            check(f.player.getPos().squaredDistanceTo(stair)<0.001,"stair top reached");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void intermediateFloorAndOccupiedDestinationCancel(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            f.turn(f.player); var pc=f.combat.get(f.player);
            var destination=ctx.getAbsolutePos(new BlockPos(5,2,5));
            f.combat.requestMove(f.player,destination); check(pc.moving(),"initial route");
            ctx.removeBlock(new BlockPos(3,1,5)); f.combat.tick();
            check(!pc.moving() && pc.moveUsed==0,"removed intermediate floor cancels before movement");
            ctx.setBlockState(new BlockPos(3,1,5),Blocks.STONE);
            f.combat.requestMove(f.player,destination); check(pc.moving(),"restored route");
            Vec3d occupied=Vec3d.ofBottomCenter(destination);
            enemy.setPosition(occupied); f.combat.get(enemy).anchor=occupied;
            f.combat.tick();
            check(!pc.moving() && pc.moveUsed==0,"new occupant cancels route");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bodyDimensionsCornersHazardsAndBoundaries(TestContext ctx) {
        var wide=mob(ctx,EntityType.RAVAGER,1,2);
        var tall=mob(ctx,EntityType.ENDERMAN,1,5);
        ctx.setBlockState(new BlockPos(2,2,3),Blocks.STONE);
        check(!CombatGeometry.clearSegment(wide,wide.getPos(),wide.getPos().add(1,0,0)),"wide body collides beside nominal cell");
        ctx.setBlockState(new BlockPos(2,4,5),Blocks.STONE);
        check(!CombatGeometry.clearSegment(tall,tall.getPos(),tall.getPos().add(1,0,0)),"tall body checks headroom");
        var walker=mob(ctx,EntityType.ZOMBIE,4,2);
        ctx.setBlockState(new BlockPos(5,2,2),Blocks.STONE);
        check(!CombatGeometry.clearSegment(walker,walker.getPos(),walker.getPos().add(1,0,1)),"diagonal cannot cut solid corner");
        ctx.removeBlock(new BlockPos(5,2,2));
        ctx.setBlockState(new BlockPos(5,1,2),Blocks.NETHERRACK);
        for (Block hazard:List.of(Blocks.WATER,Blocks.LAVA,Blocks.FIRE,Blocks.SWEET_BERRY_BUSH)) {
            ctx.setBlockState(new BlockPos(5,2,2),hazard);
            check(ctx.getBlockState(new BlockPos(5,2,2)).isOf(hazard),"hazard fixture persists");
            check(!CombatGeometry.clearSegment(walker,walker.getPos(),walker.getPos().add(1,0,0)),"grid path rejects fluid/hazard "+hazard);
        }
        ctx.removeBlock(new BlockPos(5,2,2));
        var border=ctx.getWorld().getWorldBorder(); double size=border.getSize(),x=border.getCenterX(),z=border.getCenterZ();
        try {
            border.setCenter(walker.getX(),walker.getZ()); border.setSize(2);
            check(!CombatGeometry.clearSegment(walker,walker.getPos(),walker.getPos().add(2,0,0)),"world border checked");
        } finally { border.setCenter(x,z); border.setSize(size); }
        Vec3d remote=new Vec3d(25000000,100,25000000);
        check(!ctx.getWorld().isChunkLoaded(25000000>>4,25000000>>4),"fixture is unloaded");
        check(!CombatGeometry.clearSegment(walker,remote,remote.add(1,0,0)),"unloaded geometry rejected without generation");
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void protocolReplayOperatorAndRemoval(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            f.turn(f.player);
            check(!CombatManager.canFight(f.player),"mock with no declared v2 channel cannot auto-recruit");
            var stamp=f.combat.commandStamp();
            CombatManager.requestEndTurn(f.player,stamp);
            long serial=f.combat.commandStamp().turn();
            CombatManager.requestEndTurn(f.player,stamp);
            check(f.combat.commandStamp().turn()==serial,"duplicate end turn cannot advance twice");
            f.turn(f.player); CombatManager.requestEndTurn(f.player,stamp);
            check(f.combat.isTurnOf(f.player),"stale earlier turn cannot advance a new turn");
            serial=f.combat.commandStamp().turn();
            var manager=ctx.getWorld().getServer().getCommandManager();
            manager.executeWithPrefix(f.player.getCommandSource().withLevel(0),"tbc advance");
            check(f.combat.commandStamp().turn()==serial,"ordinary player cannot force advance");
            manager.executeWithPrefix(f.player.getCommandSource().withLevel(2),"tbc advance");
            check(f.combat.commandStamp().turn()==serial+1,"operator can advance current encounter");
            enemy.discard(); f.combat.tick();
            check(CombatManager.get(enemy)==null,"removed actor ownership released");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void freeUiAndPaidBlockInteractions(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            var p=f.player; f.turn(p);
            p.setStackInHand(Hand.MAIN_HAND,ItemStack.EMPTY);
            BlockPos pos=ctx.getAbsolutePos(new BlockPos(1,2,4));
            ctx.getWorld().setBlockState(pos,Blocks.LEVER.getDefaultState());
            var hit=new net.minecraft.util.hit.BlockHitResult(Vec3d.ofCenter(pos),Direction.UP,pos,false);
            check(p.interactionManager.interactBlock(p,ctx.getWorld(),ItemStack.EMPTY,Hand.MAIN_HAND,hit).isAccepted(),"lever action accepted");
            check(f.combat.get(p).actionUsed,"lever costs action");
            boolean powered=ctx.getWorld().getBlockState(pos).get(LeverBlock.POWERED);
            p.interactionManager.interactBlock(p,ctx.getWorld(),ItemStack.EMPTY,Hand.MAIN_HAND,hit);
            check(ctx.getWorld().getBlockState(pos).get(LeverBlock.POWERED)==powered,"second block action cannot mutate");
            f.combat.endTurn();
            ctx.getWorld().setBlockState(pos,Blocks.CHEST.getDefaultState());
            check(p.interactionManager.interactBlock(p,ctx.getWorld(),ItemStack.EMPTY,Hand.MAIN_HAND,hit).isAccepted(),"container UI opens outside turn");
            check(!CombatInteractions.equipmentAllowed(p),"open UI does not authorize slot mutations");
        }
        ctx.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void blazeBurstSharesOneAction(TestContext ctx) {
        var blaze=mob(ctx,EntityType.BLAZE,1,2); var target=mob(ctx,EntityType.ZOMBIE,5,2);
        try(Fight f=new Fight(ctx,blaze,target)) {
            f.turn(blaze); blaze.setTarget(target);
            var goal=((MobGoalsAccess)blaze).goals().getGoals().stream().map(g->g.getGoal())
                .filter(g->g instanceof BlazeAccess).findFirst().orElseThrow();
            ((BlazeAccess)goal).fired(1);
            for(int i=0;i<3;i++) { ((BlazeAccess)goal).cooldown(0); goal.tick(); }
            var shots=ctx.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.SmallFireballEntity.class,
                    blaze.getBoundingBox().expand(4),a->a.getOwner()==blaze && !a.isRemoved());
            check(shots.size()==3 && f.combat.get(blaze).actionUsed,"blaze burst authorizes three shots for one action");
            ((BlazeAccess)goal).cooldown(0); goal.tick();
            check(ctx.getWorld().getEntitiesByClass(net.minecraft.entity.projectile.SmallFireballEntity.class,
                    blaze.getBoundingBox().expand(4),a->a.getOwner()==blaze && !a.isRemoved()).size()==3,"no second burst");
            shots.forEach(net.minecraft.entity.Entity::discard);
        }
        ctx.complete();
    }

    private net.minecraft.entity.projectile.thrown.PotionEntity potion(TestContext ctx, net.minecraft.entity.LivingEntity owner, Vec3d at) {
        var potion=EntityType.POTION.create(ctx.getWorld()); potion.setOwner(owner); potion.setPosition(at);
        ItemStack stack=new ItemStack(Items.SPLASH_POTION);
        stack.set(DataComponentTypes.POTION_CONTENTS,new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.SLOWNESS));
        potion.setItem(stack); ctx.getWorld().spawnEntity(potion); return potion;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void splashStatusEffectsRespectBoundariesAndReplay(TestContext ctx) {
        var witch=mob(ctx,EntityType.WITCH,1,2); var target=mob(ctx,EntityType.ZOMBIE,3,2);
        var outsider=mob(ctx,EntityType.ZOMBIE,4,2);
        try(Fight f=new Fight(ctx,witch,target)) {
            f.turn(witch);
            var potion=potion(ctx,witch,target.getPos()); f.combat.endTurn();
            var hit=new net.minecraft.util.hit.EntityHitResult(target);
            ((PotionAccess)potion).impact(hit);
            check(target.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"accepted splash applies after turn change");
            check(!outsider.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"splash cannot affect outsiders");
            target.clearStatusEffects(); ((PotionAccess)potion).impact(hit);
            check(!target.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"splash replay rejected");
            var outsidePotion=potion(ctx,outsider,target.getPos()); ((PotionAccess)outsidePotion).impact(hit);
            check(!target.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"outside splash cannot apply conditions to encounter");
            f.turn(witch);
            var expired=potion(ctx,witch,target.getPos());
            CombatActions.tick(ctx.getWorld().getServer().getOverworld().getTime()+200);
            ((PotionAccess)expired).impact(hit);
            check(!target.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"expired splash cannot apply conditions");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unsupportedSpecialMobsAndCloudsAreExplicit(TestContext ctx) {
        var evoker=mob(ctx,EntityType.EVOKER,1,2); var warden=mob(ctx,EntityType.WARDEN,2,2);
        check(!CombatManager.isEligibleEnemy(evoker) && !CombatManager.isEligibleEnemy(warden),"special unsupported mobs excluded from recruitment");
        var target=mob(ctx,EntityType.ZOMBIE,3,2);
        try(Fight f=new Fight(ctx,target)) {
            var cloud=EntityType.AREA_EFFECT_CLOUD.create(ctx.getWorld()); cloud.setPosition(target.getPos());
            cloud.setWaitTime(0); cloud.setRadius(3); cloud.setPotionContents(new net.minecraft.component.type.PotionContentsComponent(net.minecraft.potion.Potions.SLOWNESS));
            ctx.getWorld().spawnEntity(cloud);
            for(int i=0;i<10;i++) cloud.tick();
            check(!target.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SLOWNESS),"outside persistent clouds cannot apply encounter effects");
            cloud.discard();
        }
        ctx.complete();
    }

    @GameTest(templateName=EMPTY_STRUCTURE)
    public void bowReleaseAndRejectedUseCosts(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            var p=f.player; f.turn(p);
            p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.BOW));
            check(!p.interactionManager.interactItem(p,ctx.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND).isAccepted(),"bow without arrows rejected");
            check(!f.combat.get(p).actionUsed,"failed vanilla use refunds reservation");
            p.getInventory().setStack(10,new ItemStack(Items.ARROW,3));
            check(p.interactionManager.interactItem(p,ctx.getWorld(),p.getMainHandStack(),Hand.MAIN_HAND).isAccepted(),"bow drawing accepted");
            ((ConsumeAccess)p).remainingUseTicks(71980);
            p.stopUsingItem();
            var arrows=ctx.getWorld().getEntitiesByClass(PersistentProjectileEntity.class,p.getBoundingBox().expand(3),a->a.getOwner()==p);
            check(arrows.size()==1 && f.combat.get(p).actionUsed,"bow release retains accepted use permit");
            p.stopUsingItem();
            check(ctx.getWorld().getEntitiesByClass(PersistentProjectileEntity.class,p.getBoundingBox().expand(3),a->a.getOwner()==p).size()==1,"duplicate release cannot fire again");
            arrows.forEach(net.minecraft.entity.Entity::discard);
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void inventorySlotsDropsAndMiningAreServerGuarded(TestContext ctx) {
        var enemy=mob(ctx,EntityType.ZOMBIE,2,2);
        try(Fight f=new Fight(ctx,enemy)) {
            var p=f.player; p.setStackInHand(Hand.MAIN_HAND,new ItemStack(Items.STONE,5));
            f.turn(enemy);
            p.networkHandler.onClickSlot(new net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket(
                p.currentScreenHandler.syncId,p.currentScreenHandler.getRevision(),36+p.getInventory().selectedSlot,0,
                net.minecraft.screen.slot.SlotActionType.PICKUP,ItemStack.EMPTY,new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));
            check(p.getMainHandStack().getCount()==5 && p.currentScreenHandler.getCursorStack().isEmpty(),"out-of-turn slot packet cannot move items");
            p.networkHandler.onPlayerAction(new net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket(
                net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action.DROP_ALL_ITEMS,BlockPos.ORIGIN,Direction.UP));
            check(p.getMainHandStack().getCount()==5,"out-of-turn drop packet blocked");
            f.turn(p);
            var block=ctx.getAbsolutePos(new BlockPos(1,1,5));
            check(!p.interactionManager.tryBreakBlock(block) && ctx.getWorld().getBlockState(block).isOf(Blocks.STONE),"mining begun before encounter cannot finish inside it");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void simultaneousEncountersAndDisconnectCleanup(TestContext ctx) {
        var a=mob(ctx,EntityType.ZOMBIE,1,2); var b=mob(ctx,EntityType.ZOMBIE,4,2);
        try(Fight first=new Fight(ctx,a); Fight second=new Fight(ctx,a,b)) {
            check(CombatManager.get(a)==first.combat && CombatManager.get(b)==second.combat,"nearby encounters have disjoint ownership");
            first.turn(first.player); second.turn(second.player);
            long otherTurn=second.combat.commandStamp().turn();
            CombatManager.requestEndTurn(first.player,first.combat.commandStamp());
            check(second.combat.commandStamp().turn()==otherTurn,"one encounter cannot advance another");
            check(!CombatActions.allowDamage(b,ctx.getWorld().getDamageSources().mobAttack(a)),"cross-encounter damage blocked");
            var unowned=EntityType.ARROW.create(ctx.getWorld());
            check(!CombatActions.allowDamage(b,ctx.getWorld().getDamageSources().arrow(unowned,null)),"unowned projectile is not environmental damage");
            first.player.getServer().getPlayerManager().remove(first.player);
            check(first.combat.tick(),"encounter without players requests end");
            check(CombatManager.get(first.player)==null,"disconnected ownership released");
            check(CombatManager.get(b)==second.combat,"disconnect leaves other encounter intact");
        }
        ctx.complete();
    }
}
