# J2.5J — Prone, standing and crawling

Base: `d906bfb05a72ceac7987ac04f0ba1284ab03febd` (J2.5I).
Branch: `dev/gameplay-srd-prone-condition-j25j`. The delivery response supplies the verified remote commit.

## Authoritative posture and movement

An encounter-local set holds Prone for actual participant entity IDs, bounded by the encounter's participant limit. Drop Prone costs no action or movement. Standing costs half the authoritative walking speed, rounded down in whole feet under SRD rounding. Both require the current controller/manager, exact campaign/actor/revision, current encounter/turn and admitted request identity. Both reject an executing route, unresolved weapon transaction, persistent resource reservation, invalid binding, zero HP, or duplicate posture. Standing additionally rejects zero speed or insufficient movement. Failed operations leave posture, action and movement unchanged.

Prone survives End Turn and subsequent turns, independently for each actor; it does not expire like Dodge. Encounter end clears it. Restart closes encounters under the existing policy, without restoring posture. No character/campaign/library schema changes or real-world migration are involved. Posture is an immediate encounter operation, using existing bounded request admission, not a persistent resource transaction; administrator rollback has no global exactly-once guarantee.

The existing terrain adapter now uses a fixed server-owned posture for each movement search. Each five-foot step costs 5 feet standing, 10 crawling, or 15 crawling through supported difficult terrain. Difficulty and crawling add their extra costs rather than multiplying each other. Both incremental and direct searches use these costs, and each executed step revalidates live terrain and cost. Posture changes discard cached grids, invalidate attack intent and increment encounter revision. Old grid jobs cannot publish against the new revision. A destination from an old standing preview is revalidated at the current cost, never accepted at its old price.

Standing charges the existing spent-movement ledger without moving the entity. Dash still adds authoritative speed and does not refund spending. For speed 25, standing costs 12 and leaves 13 from a fresh turn; two ordinary squares leave 3. Remaining whole feet are retained, although routes require whole five-foot grid steps. Existing diagonal distance and terrain restrictions remain unchanged. Speed is the supported authoritative projection, including existing armor adjustment; no new effect-based speed modifiers are invented.

## Attacks and Dodge

The existing server target calculation combines these flags with all previous sources:

- Prone attacker: disadvantage.
- Prone defender at validated distance at most 5 feet: advantage.
- Prone defender farther away: disadvantage.

Any advantage plus any disadvantage cancels to a normal roll. Multiple sources never add extra dice. A visible Dodging, Prone defender within five feet therefore supplies cancelling sources. Confirmation recalculates geometry and defensive flags before reserving an action or consuming RNG. An actor's posture change also removes its old intent. Accepted flags and dice use the existing J2 prepared/committed weapon plan and recovery path without a second transaction implementation.

Executable Prone alone does not disable Dodge or its Dexterity-save benefit. Dodge still expires at that actor's next turn and retains its conservative existing condition/sight limitations. No descriptive CONDITION/TEMPORARY record becomes an executable rule. No automatic Prone at zero HP, new conditions, special abilities, or save modifiers were added.

## Battlefield and privacy

One contextual action-bar control changes between Drop Prone and Stand with its movement cost. Tooltips explain costs and prerequisites. The server-confirmed acknowledgement enters existing toast/history presentation; there is no automatic mutation retry. The existing pending guard disables posture while another operation is outstanding. Move, Attack, Dash, Dodge, End Turn and camera controls remain available under their existing rules.

Authorized controllers/DMs receive PRONE in actor, selection and world labels, plus current standing cost and availability. Other viewers do not receive private posture fields; shared encounter status stays generic. Movement preview labels identify crawling and display authoritative path cost and remaining feet. Existing attack preview shows the combined server roll mode. The narrow layout accommodates nine controls in three rows; automated bounds checks are not graphical approval.

## Limits

Crawling uses the existing collision footprint. It does not shrink the entity, permit squeezing under blocks, or fake a vanilla crawl animation. Only supported grid terrain is automated. Prone is encounter-local and is cleared when combat closes; persisting a general condition outside combat is deferred. Existing Dodge visibility and recorded-condition restrictions remain as documented in J2.5I. Graphical acceptance of prior F/G/H/I work remains pending.

## Validation

Final full run: **BUILD SUCCESSFUL in 1 minute 5 seconds**.

- 38,979 foundation assertions across 44 suites (baseline 38,520 / 44).
- 169/169 GameTests, zero failures, errors or skipped tests (baseline 160).
- 2,748 catalog checks + 15,115 coverage checks = 17,863.
- J2 transaction/fault recovery, D1 checks/privacy, Dash/Dodge, Tactical/Generic D20, creator, DM Workspace, dice and catalog regressions passed.

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `c7170548a5e357247d56da1e4c80fcbf29f9aab48725753e22ac9484ef4c4212`.
Evidence: `build/j25j-final-validation.log` and `build/gametest-results.xml`. Existing deprecated test API/Gradle warnings and the optional authentication public-key DNS warning do not represent test failures.

Command: `bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage` (Java 21).

New foundation coverage checks additive costs, all supported speeds' standing rounding, fifteen-foot path edges, mode-source combinations and distance boundary, pending UI safety and nine-control layout bounds. Nine new real-server GameTests cover lifecycle, independent actors, Dodge preservation, Dash/crawling, insufficient/zero-speed standing, odd-speed remainders, terrain changes during routes, stale previews before RNG, committed disadvantaged attacks, high-speed incremental grid invalidation, prepared Husk authority, request/revision rejection, participant privacy and revoked nonparticipant access denial. The existing real prepared-transaction failure test also rejects posture changes while a weapon transaction is unresolved. Existing tests and assertions remain enabled. Development corrected a Java test lambda capture and a privacy fixture that incorrectly expected a revoked nonparticipant to retain snapshot access; the server correctly denies that snapshot. A separate bound-participant test verifies field filtering without weakening viewer authorization.

## Minecraft acceptance checklist

**GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.** No graphical display is available; mathematical layout and headless server tests do not verify rendering or mouse interaction.

Use matching client/server JARs in a separate test world:

1. Start a controlled encounter with an approved player and prepared Husk. Check camera, world picking and ordinary split movement.
2. Drop Prone: verify PRONE, unchanged action/movement, and crawling preview of 10 feet per ordinary square (15 on supported difficult terrain). Move twice and inspect remaining movement.
3. Stand: verify half-speed cost (15 for speed 30, 10 for Zombie speed 20), no teleport, action still available. Try standing with insufficient movement.
4. Drop and Dash: verify doubled fresh-turn movement budget, current spending retained, crawling still charged correctly, and normal action spent.
5. End turns while Prone. Verify posture remains for both independently controlled actors until each stands. Check manager control without switching accounts.
6. Preview attacks by a Prone attacker and against near/far Prone targets. Check disadvantage, near advantage and Dodge cancellation. Change posture after preview; confirm a fresh preview is required.
7. Compare authorized indicators with an ordinary unauthorized viewer. Check pending/denied actions do not toggle posture, spend movement or submit automatically.
8. Check action-bar readability at GUI scales 2/3/4, turn/camera transitions, DM Workspace return, Dodge saves and existing combat history. End combat and verify posture cleanup.

## Changed files

- `docs/gameplay-srd-prone-condition-checkpoint-j25j.md`
- `src/gametest/java/dev/tacticalcombat/combat/GameplayTransactionGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdProneGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldActions.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldModel.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/combat/SrdTargeting.java`
- `src/main/java/dev/tacticalcombat/grid/SrdTerrain.java`
- `src/main/java/dev/tacticalcombat/rules/srd/SrdMovement.java`
- `src/test/java/dev/tacticalcombat/SrdMovementTests.java`
- `src/test/java/dev/tacticalcombat/client/SrdBattlefieldUiTests.java`
