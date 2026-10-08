package dev.tacticalcombat.combat;

import dev.tacticalcombat.net.*;
import dev.tacticalcombat.card.ShareCard;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.PacketByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;

public class AuthorityGameTests implements FabricGameTest {
    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
    private ZombieEntity actor(TestContext ctx, int x, int z) {
        ZombieEntity mob = ctx.spawnEntity(EntityType.ZOMBIE, new BlockPos(x, 2, z));
        mob.setAiDisabled(true);
        return mob;
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wallBlocksMelee(TestContext ctx) {
        var a = actor(ctx, 1, 2); var b = actor(ctx, 3, 2);
        require(CombatGeometry.clearAttack(a, b), "clear attack before wall");
        ctx.setBlockState(new BlockPos(2, 2, 2), Blocks.STONE);
        ctx.setBlockState(new BlockPos(2, 3, 2), Blocks.STONE);
        require(!CombatGeometry.clearAttack(a, b), "wall must block actor sightline");
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void terrainChangeInvalidatesSegment(TestContext ctx) {
        var a = actor(ctx, 1, 2);
        Vec3d start = a.getPos(), end = start.add(2, 0, 0);
        require(CombatGeometry.clearSegment(a, start, end), "empty corridor");
        ctx.setBlockState(new BlockPos(2, 2, 2), Blocks.STONE);
        require(!CombatGeometry.clearSegment(a, start, end), "new wall must invalidate saved path");
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fullHeightCollision(TestContext ctx) {
        var a = actor(ctx, 1, 2);
        ctx.setBlockState(new BlockPos(2, 3, 2), Blocks.STONE);
        require(!CombatGeometry.clearSegment(a, a.getPos(), a.getPos().add(2, 0, 0)), "head collision");
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void requestCodecRoundTrip(TestContext ctx) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), ctx.getWorld().getRegistryManager());
        try {
            var original = new AttackRequestPayload(17, new RequestStamp(UUID.randomUUID(), 32, UUID.randomUUID()));
            AttackRequestPayload.CODEC.encode(buf, original);
            require(original.equals(AttackRequestPayload.CODEC.decode(buf)), "request identity round trip");
            require(!buf.isReadable(), "consume complete payload");
        } finally { buf.release(); }
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectsMalformedCards(TestContext ctx) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        try {
            buf.writeString("character"); buf.writeString("kind"); buf.writeString("title"); buf.writeVarInt(13);
            boolean rejected = false;
            try { ShareCard.read(buf); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "excess stat count must fail at the count, not reinterpret following bytes");
        } finally { buf.release(); }
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rejectsOversizedGrid(TestContext ctx) {
        RegistryByteBuf buf = new RegistryByteBuf(Unpooled.buffer(), ctx.getWorld().getRegistryManager());
        try {
            buf.writeVarInt(PacketLimits.GRID_CELLS + 1);
            boolean rejected = false;
            try { GridPayload.CODEC.decode(buf); } catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected, "oversized grid rejected before allocating entries");
        } finally { buf.release(); }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void encounterOwnershipIsExclusive(TestContext ctx) {
        var a = actor(ctx, 1, 2); var b = actor(ctx, 3, 2);
        Combat first = Combat.start(ctx.getWorld(), java.util.List.of(), java.util.List.of(a));
        Combat second = Combat.start(ctx.getWorld(), java.util.List.of(), java.util.List.of(a, b));
        try {
            require(CombatManager.get(a) == first, "original ownership retained");
            require(!second.contains(a) && second.contains(b), "overlap excluded");
            require(Combat.start(ctx.getWorld(), java.util.List.of(), java.util.List.of(a)) == null,
                    "fully overlapping start rejected without an empty-turn crash");
            second.end();
            require(CombatManager.get(a) == first, "ending other encounter preserves ownership");
        } finally { first.end(); second.end(); }
        require(CombatManager.get(a) == null && CombatManager.get(b) == null, "ownership cleanup");
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void outsideDamageIsRejected(TestContext ctx) {
        var a = actor(ctx, 1, 2); var b = actor(ctx, 3, 2);
        Combat encounter = Combat.start(ctx.getWorld(), java.util.List.of(), java.util.List.of(a));
        try {
            float hp = a.getHealth();
            require(!a.damage(ctx.getWorld().getDamageSources().mobAttack(b), 2), "outside damage rejected");
            require(a.getHealth() == hp, "health unchanged through real ALLOW_DAMAGE hook");
            require(!b.tryAttack(a), "mob melee adapter rejects outside attacker");
        } finally { encounter.end(); }
        ctx.complete();
    }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removedLandingIsRejected(TestContext ctx) {
        var a = actor(ctx, 1, 2);
        ctx.setBlockState(new BlockPos(1, 1, 2), Blocks.STONE);
        require(CombatGeometry.validLanding(a, a.getPos()), "supported landing");
        ctx.removeBlock(new BlockPos(1, 1, 2));
        require(!CombatGeometry.validLanding(a, a.getPos()), "removed floor invalidates landing");
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void acceptedActionsAndDelayedDamage(TestContext ctx) {
        var player = ctx.createMockCreativeServerPlayerInWorld();
        var a = actor(ctx, 1, 2); var b = actor(ctx, 2, 2); var c = actor(ctx, 3, 2);
        player.refreshPositionAndAngles(a.getX(), a.getY(), a.getZ() + 2, 0, 0);
        Combat encounter = Combat.start(ctx.getWorld(), java.util.List.of(player), java.util.List.of(a, b, c));
        try {
            while (!encounter.isTurnOf(a)) encounter.endTurn();
            require(!CombatActions.resolve(b, () -> { throw new IllegalStateException("out of turn effect executed"); }),
                    "out of turn rejected");
            require(!encounter.get(b).actionUsed, "rejection does not spend action");
            require(!CombatActions.resolve(a, () -> false), "accepted miss");
            require(encounter.get(a).actionUsed, "miss spends action");
            require(!CombatActions.resolve(a, () -> { throw new IllegalStateException("second action executed"); }),
                    "second action rejected");
            encounter.endTurn();
            while (!encounter.isTurnOf(a)) encounter.endTurn();
            var arrow = net.minecraft.entity.EntityType.ARROW.create(ctx.getWorld());
            arrow.setOwner(a);
            arrow.setPosition(a.getPos());
            require(ctx.getWorld().spawnEntity(arrow), "projectile spawned");
            require(encounter.get(a).actionUsed, "projectile admission spends action");
            encounter.endTurn();
            var damage = ctx.getWorld().getDamageSources().arrow(arrow, a);
            require(CombatActions.allowDamage(b, damage), "accepted projectile survives turn change");
            require(CombatActions.allowDamage(c, damage), "one action permits multiple victims");
            require(!CombatActions.allowDamage(b, damage), "same victim callback cannot repeat");
            var outsider = actor(ctx, 4, 3);
            require(!CombatActions.allowDamage(outsider, damage), "accepted projectile cannot hit outsiders");
            CombatActions.tick(ctx.getWorld().getServer().getOverworld().getTime() + 200);
            require(!CombatActions.allowDamage(c, damage), "expired projectile fails closed");
            arrow.discard();
            while (!encounter.isTurnOf(a)) encounter.endTurn();
            float hp = b.getHealth();
            require(a.tryAttack(b), "supported mob melee resolves through mixin");
            require(b.getHealth() < hp && encounter.get(a).actionUsed, "one action and real damage");
        } finally {
            encounter.end();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
        }
        ctx.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void turnMovementPinsHeightAndStopsAtNewWall(TestContext ctx) {
        var player = ctx.createMockCreativeServerPlayerInWorld();
        var a = actor(ctx, 1, 2);
        player.refreshPositionAndAngles(a.getX(), a.getY(), a.getZ() + 2, 0, 0);
        Combat encounter = Combat.start(ctx.getWorld(), java.util.List.of(player), java.util.List.of(a));
        try {
            while (!encounter.isTurnOf(player)) encounter.endTurn();
            Vec3d anchor = a.getPos();
            a.setPosition(anchor.add(0, 3, 0));
            encounter.tick();
            require(a.getPos().squaredDistanceTo(anchor) < 0.0025, "inactive actor height pinned");
            var pc = encounter.get(player);
            Vec3d before = player.getPos();
            pc.path = java.util.List.of(before.add(2, 0, 0));
            pc.pathPos = before;
            pc.pathIdx = 0;
            ctx.getWorld().setBlockState(BlockPos.ofFloored(before.add(1, 0, 0)), Blocks.STONE.getDefaultState());
            encounter.tick();
            require(pc.path == null, "changed terrain cancels active path");
            require(player.getPos().squaredDistanceTo(before) < 0.0025, "no movement through new wall");
        } finally {
            encounter.end();
            ctx.getWorld().getServer().getPlayerManager().remove(player);
        }
        ctx.complete();
    }
}
