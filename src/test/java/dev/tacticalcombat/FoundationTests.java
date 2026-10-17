package dev.tacticalcombat;

import dev.tacticalcombat.core.*;
import dev.tacticalcombat.dice.*;
import dev.tacticalcombat.net.PacketLimits;
import dev.tacticalcombat.sheet.*;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;

/** No test-library dependency; each assertion checks a public contract and failures fail Gradle check. */
public final class FoundationTests {
    private static int count;
    private static void check(boolean condition, String message) {
        count++; if (!condition) throw new AssertionError(message);
    }
    private static void rejects(Runnable operation, String message) {
        try { operation.run(); } catch (IllegalArgumentException | Expr.ExprException expected) { count++; return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        UUID actor = UUID.randomUUID(), encounter = UUID.randomUUID();
        EncounterIndex<String> owners = new EncounterIndex<>();
        check(owners.claim(actor, "one"), "first ownership");
        check(!owners.claim(actor, "two"), "reject overlap");
        owners.release(actor, "two");
        check(owners.get(actor).equals("one"), "stale release must preserve owner");
        owners.release(actor, "one");
        check(owners.claim(actor, "two"), "released actor can join again");
        owners.clear(); check(owners.get(actor) == null, "stop cleanup");
        TurnRequests requests = new TurnRequests(encounter); requests.nextTurn();
        UUID request = UUID.randomUUID();
        check(requests.accept(encounter, 1, request), "accept first request");
        check(!requests.accept(encounter, 1, request), "reject duplicate");
        check(!requests.accept(UUID.randomUUID(), 1, UUID.randomUUID()), "reject other encounter");
        requests.nextTurn();
        check(!requests.accept(encounter, 1, UUID.randomUUID()), "reject delayed request in later turn");
        for (int i = 0; i < TurnRequests.MAX_REQUESTS; i++) check(requests.accept(encounter, 2, UUID.randomUUID()), "bounded admission");
        check(!requests.accept(encounter, 2, UUID.randomUUID()), "request cap");
        RateLimit rate = new RateLimit(2, 80);
        check(rate.allow(actor, 0) && rate.allow(actor, 0), "burst");
        check(!rate.allow(actor, 79), "window restriction");
        check(rate.allow(actor, 80), "window refresh");
        rate.forget(actor); check(rate.allow(actor, 80), "disconnect cleanup");
        BoundedQueue<Integer> queue = new BoundedQueue<>(2);
        check(queue.add(1) == null && queue.add(2) == null, "queue within limit");
        check(queue.add(3) == 1 && queue.size() == 2, "queue evicts oldest for immediate logging");
        check(queue.poll() == 2 && queue.poll() == 3 && queue.poll() == null, "queue order");
        DamagePermit permit = new DamagePermit(encounter, actor);
        UUID victim = UUID.randomUUID();
        check(!permit.admit(UUID.randomUUID(), victim), "reject cross-encounter effect");
        check(permit.admit(encounter, victim), "admit authorized effect");
        check(!permit.admit(encounter, victim), "no duplicate effect");
        check(permit.admit(encounter, UUID.randomUUID()), "multi-victim action costs only once");
        UUID secondEffect = permit.newEffect();
        check(permit.admit(encounter, secondEffect, victim, false), "distinct effect can hit same victim");
        check(!permit.admit(encounter, secondEffect, victim, false), "effect replay rejected");
        check(!permit.admit(encounter, UUID.randomUUID(), victim, false), "unissued effect rejected");
        for (int i = 2; i < DamagePermit.MAX_EFFECTS; i++) check(permit.newEffect() != null, "bounded effect issuance");
        check(permit.newEffect() == null, "effect issuance capped");
        MovementCharge charge = new MovementCharge(3, 6);
        check(charge.advance(1) == 0.5, "partial route cost");
        check(charge.advance(1) == 0.5, "incremental cost, unused allowance not charged");
        check(charge.advance(100) == 2 && charge.advance(1) == 0, "route cost cannot overcharge");
        rejects(() -> charge.advance(-1), "cannot refund completed travel");
        rejects(() -> new MovementCharge(1, Double.NaN), "invalid route length");
        rejects(() -> PacketLimits.count(-1, 10), "negative length");
        rejects(() -> PacketLimits.count(11, 10), "excess length");
        check(PacketLimits.count(10, 10) == 10, "boundary length");
        for (String formula : List.of("2*d6", "-d6", "d6/2", "0d6", "11d6", "1.5d6", "floor(d6)", "2*(d6+3)", "2-d6", "d6+d4"))
            rejects(() -> Expr.roll(formula, n -> null), "invalid dice expression: " + formula);
        check(Expr.roll("2d6+3*2", n -> null).modifier() == 6, "scalar multiplication after dice is valid");
        check(Expr.roll("d20 + floor((15-10)/2) + 2", n -> null).modifier() == 4, "ability roll");
        for (String id : List.of("generic_d20", "percentile")) {
            Path dir = Path.of("packs", id);
            SheetFormat format = SheetFormat.parse(JsonParser.parseString(Files.readString(dir.resolve("format.json"))).getAsJsonObject(), id);
            check(format.id.equals(id), "existing pack identity");
            try (var paths = Files.list(dir.resolve("characters"))) {
                for (Path p : paths.toList()) {
                    CharacterData c = CharacterData.parse(JsonParser.parseString(Files.readString(p)).getAsJsonObject(), p.getFileName().toString());
                    check(CharacterData.parse(c.toJson(), c.file).toJson().equals(c.toJson()), "character round trip");
                    SheetContext sc = new SheetContext(format, c);
                    for (String field : format.derived.keySet()) check(Double.isFinite(sc.number(field)), "existing formula: " + field);
                }
            }
        }
        System.out.println("Foundation regression checks passed: " + count);
        CharacterRepositoryTests.run();
        dev.tacticalcombat.character.MonsterStageTests.run();
        dev.tacticalcombat.character.UiLibraryTests.run();
        dev.tacticalcombat.character.UiLifecycleTests.run();
        dev.tacticalcombat.character.UiRpgRecordTests.run();
        dev.tacticalcombat.character.UiPresentationTests.run();
        dev.tacticalcombat.client.ui.HubUiTests.run();
        dev.tacticalcombat.client.ui.ManagementUiTests.run();
        dev.tacticalcombat.client.ui.WizardUiTests.run();
        dev.tacticalcombat.client.ui.UiCorrectionTests.run();
        dev.tacticalcombat.client.ui.CreatorCheckpointTests.run();
        dev.tacticalcombat.client.ui.IdentityCheckpointTests.run();
        dev.tacticalcombat.client.ui.CharacterSheetCheckpointTests.run();
        dev.tacticalcombat.client.ui.CharacterCheckpointCTests.run();
        dev.tacticalcombat.client.ui.CheckpointCPresentationTests.run();
        dev.tacticalcombat.client.ui.CheckpointDPresentationTests.run();
        dev.tacticalcombat.client.ui.CheckpointEWorkflowTests.run();
        dev.tacticalcombat.client.ui.CheckpointEPresentationTests.run();
        dev.tacticalcombat.client.ui.CheckpointFTests.run();
        dev.tacticalcombat.character.CheckpointGTests.run();
        dev.tacticalcombat.client.ui.CheckpointHTests.run();
        dev.tacticalcombat.client.ui.CheckpointJ15Tests.run();
        dev.tacticalcombat.client.ui.DashboardUiTests.run();
        dev.tacticalcombat.client.ui.HudUiTests.run();
        dev.tacticalcombat.character.UiStabilizationTests.run();
        CharacterSyncTests.run();
        dev.tacticalcombat.character.PersistenceFaultTests.run();
        LegacyRulesParityTests.run();
        dev.tacticalcombat.character.RulesProjectionTests.run();
        SrdRulesTests.run();
        SrdMovementTests.run();
        SrdAuthoringTests.run();
        dev.tacticalcombat.character.SrdResourceFaultTests.run();
        dev.tacticalcombat.character.GameplayTransactionBaselineTests.run();
        dev.tacticalcombat.character.GameplayTransactionTests.run();
        dev.tacticalcombat.client.ui.CheckpointJ25Tests.run();
        dev.tacticalcombat.client.ui.CinematicDiceTests.run();
        dev.tacticalcombat.reference.CatalogValidationTests.run();
        dev.tacticalcombat.reference.CatalogCompletionTests.run();
        dev.tacticalcombat.checks.CharacterCheckTests.run();
    }
}
