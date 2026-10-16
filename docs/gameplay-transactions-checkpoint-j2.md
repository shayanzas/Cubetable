# Gameplay transactions — Checkpoint J2

Base commit: `d5057c937cc7e1e258f49b6ce1cebf71f471e5c5` (J1.5).
Branch: `dev/gameplay-transactions-checkpoint-j2` in `shayanzas/Cubetable`.
Implementation commit: 2fd52520cf55bc5bf5726424a22615abda5df25d.
Final delivery commit: the commit containing this report; resolve with `git log -1 --format=%H -- docs/gameplay-transactions-checkpoint-j2.md` on the delivered branch (a commit cannot contain its own hash).

## Scope and acceptance

J2 adds durable weapon transactions, replay protection, fixed-outcome retry, and DM recovery review. Existing weapon math, J1 rules projection, character authoring, Tactical mechanics and J1.5 launcher behavior are preserved. No equipment instances, class features, spells, concentration, effect timers or new combat actions are enabled. The sole new graphical entry is **DM Quick Tools → Gameplay Transaction Review**, needed to resolve durable reservations.

Automated acceptance: passed; see evidence below. Graphical Minecraft acceptance remains pending user review; no graphical client was available. No real saved world was opened, migrated or modified.

**Operational limit:** receipts are never evicted. The entire campaign store accepts at most **1,024 receipts**, including cancelled operations, and **64 simultaneous preparations**. New preparation fails closed at capacity. This is intentionally conservative, not an unlimited production archive. Do not delete receipts to regain capacity; a separately reviewed epoch/archive upgrade is needed.

## Audited path and timing

Before changes: client intent → authenticated `SrdNetworking` → active encounter/turn/controller checks → server preview snapshots and geometry → confirmation revalidation → in-memory action reservation → server RNG → resource calculation → `CharacterRepository.commitSrdAction` → one atomic campaign-file replacement → encounter/movement reconciliation → private result and events. Both participant runtime revisions advance even for misses. `TurnRequests` only protects in-memory transport requests. The former pending transaction retained dice across same-process save failures, but disappeared on restart.

Now confirmation still checks current character/runtime revisions, actor positions, geometry, active turn, controller, approved SRD v1 records, bindings and campaign membership. It also checks receipt capacity and outstanding participant reservations **before reserving the action and rolling**. The server builds `GameplayActionPlan`, persists PREPARED, then atomically commits resources and COMMITTED. Only after commit are encounter snapshots reconciled and results/events published. No dice, HP, AC, damage, deltas or budget supplied by a client are trusted; confirmation accepts only the server-issued intent ID.

Preview does not spend resources. Action reservation occurs before RNG. Preparation does not change character revisions or quantities. Commit changes each participant runtime revision by one and leaves build revision unchanged. Repository state becomes visible only after the file replacement succeeds. A failed commit pauses the encounter; a DM can retry in that live encounter using the retained plan. Ending or restarting closes ordinary encounter state and releases world claims, but does not erase committed quantities or durable reservations.

## Typed plan and storage format

Library schema **2** and SRD profile **v1** are unchanged. Campaign storage becomes **schema 5**, at the existing `<world>/tacticalcombat/campaigns.json` boundary. Existing campaigns, characters, bindings, assignments and manual revisions remain; `receipts` is added to the same document. All ordinary repository writes preserve that map.

Each version-1 receipt contains a version-1 plan, status, optional resolving DM UUID and cancellation reason (maximum 240 characters). The plan contains:

- Server-generated operation UUID (the preview intent UUID), authenticated requester, campaign, encounter and turn.
- Two typed participants: character/entity/controller UUIDs, expected build/runtime revisions, SRD rule-input snapshot and bounded intended resources.
- Validated weapon, attack mode, ability, hands, cover and advantage/disadvantage context.
- Fixed attack/damage faces and computed outcome; action cost is one. Effect version is zero; new effects require an explicit typed version upgrade.
- SHA-256 canonical fingerprint of the complete identity and fixed outcome. Object keys and enum sets are canonicalized; numeric dice order is preserved across JVM restarts.

Decode verifies the fingerprint, reconstructs the result from the **stored faces without RNG**, and verifies exact attacker/target deltas and resource bounds. Snapshots contain rules inputs, not complete private character sheets. Fingerprints detect inconsistency; they are not cryptographic signatures protecting against administrators editing saves.

Explicit readers support campaign schemas 1–4, retaining established character/approval/binding/controller/owner/creationClosed/manual/Tactical data. The next successful write uses schema 5; no historical inventory, grants, ammunition semantics or AC values are converted into new mechanics. Unknown future schemas, transaction versions, fields and enum values fail closed, including refusal to silently load an older backup for a future format. Legacy test fixtures were corrected to contain their actual historical fields; expected upgrade version changed to 5. Existing assertions were retained.

The existing 16 MiB file limit also applies. A byte-limit failure leaves the store unchanged, even below receipt-count capacity.

## Lifecycle and atomicity

| State | Durable contents | Gameplay quantities | Retry/restart behavior |
| --- | --- | --- | --- |
| VALIDATED | None yet; server-only plan and action reservation | Unchanged | Same-process failure retains rolls; no accepted result is published. If preparation never becomes durable, restart cannot recover that outcome. |
| PREPARED | Exact plan, fixed rolls, expected revisions, intended changes | Unchanged | Both participant records reserved; conflicts, new encounters, legacy health/action writes and character edits cannot bypass the reservation. |
| COMMITTED | Same plan and terminal receipt, atomically with both updated characters | Applied once | Identical requests return stored result; no write or RNG. |
| CANCELLED | Same plan, fixed rolls, resolving DM and reason | Unchanged by cancellation | Terminal receipt remains permanently replay-protected. |

`AtomicJsonFile` validates candidate bytes, writes a last-good backup, forces a same-directory temporary file and atomically replaces the primary. Only then is in-memory repository state published. Preparation and commit are two replacements; each replacement is coherent. Final commit includes attacker stock, defender HP/temporary HP, both runtime revisions and the receipt together. No second journal or independently committed ammunition file exists.

The commit marker is set before presentation reconciliation. A later movement/readback/event failure cannot relabel the resource transaction as uncommitted or enable spending again. Existing healing retains its legacy atomic single-record path and respects prepared reservations; it is not advertised as a new replay-safe operation type.

These guarantees cover the campaign document on a filesystem supporting atomic rename. They do **not** cover atomicity with Minecraft entity/world saves, machine power loss/filesystem damage, or administrator rollback. Temporary-file fsync is tested; directory fsync/power-loss behavior is not claimed. Existing last-good-backup recovery can restore an older coherent state after corruption; it is not global exactly-once recovery. Review backups and unresolved records before continuing after corruption. Never combine independently restored world and campaign files.

## Replay, permissions and recovery

Transport request UUIDs and operation UUIDs are distinct. An exact operation replay returns PREPARED/COMMITTED/CANCELLED status, subject to current authorization. A committed response contains the same result after lost acknowledgement or encounter closure. Altered parameters, encounter/turn scope or fingerprint are rejected. Expired preview IDs without receipts cannot become new actions: only current server previews can be confirmed. A fresh operation ID cannot bypass a pending participant reservation. Capacity exhaustion does not prune old identities.

Live recovery rechecks campaign membership, DM/controller authority, approval, supported profile, bindings, controller identity and exact participant snapshots/revisions. A revoked manager cannot retry or cancel. Read-only receipt access rechecks current campaign access and either manager permission or the original requester still controlling the attacker. Public acknowledgements/events never carry the full plan or private character records. DM review is restricted to the selected managed campaign and uses the existing permission invalidation notifications to clear a stale screen.

On restart, no encounter, position, movement, initiative or turn presentation is reconstructed. COMMITTED resources remain. PREPARED records retain outcomes and reserve both characters. Current DMs inspect these through **Gameplay Transaction Review**. While the original encounter is live, use its existing Retry control if safe. After it closes, the review screen permits **audited cancellation only**, and only when neither participant is currently in combat. It never applies stored damage. Cancellation keeps the roll and DM identity/reason and releases the reservation; the DM must adjudicate the situation. Ordinary players cannot cancel an unfavorable roll. Cancellation is a trusted DM decision, not an automatic refund or player reroll mechanism.

## Action economy

`SrdActionBudget` represents AVAILABLE → RESERVED → SPENT, reset only by a server-controlled new turn. Unsupported bonus actions, reactions and interactions have zero available slots. Client packets cannot reset this state. `SrdMovementTurn.reconcileResources` preserves distance already spent; the regression test moves five feet, fails/retries a ranged action, and still has 25 feet remaining.

Tactical `Combatant` instead owns legacy `actionUsed`/`bonusActionUsed`, block/square movement counters, mob path/action timing and a broad `resetTurnResources`. Those semantics are not imported into controlled SRD combat and the Tactical implementation is unchanged. Future turn-bound features/effects need explicit typed extensions; no real-world clocks are invented.

## Automated evidence

Java 21 command:

```bash
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

- **14,208 foundation assertions, 35 suites** (J1.5: 14,029 / 33).
- **132/132 required GameTests passed**, zero failures in `build/gametest-results.xml` (J1.5: 128).
- Final full command: **BUILD SUCCESSFUL in 49s**, 2026-10-16 UTC. Foundation execution was already successful in the preceding run and Gradle correctly reused that unchanged task; the final run recompiled/reran the changed GameTests.
- Local evidence: `/tmp/j2-baseline.log`, `/tmp/j2-faults-2.log`, `/tmp/j2-full-validation.log`, `/tmp/j2-final-validation.log`, `/tmp/j2-delivery-validation.log`. The penultimate log includes the explicitly corrected test-fixture failure.

Before production edits, commit `6aecc5e` froze 36 checks against the old transaction implementation: melee/ranged/thrown hit/miss quantities, paired runtime revisions, stale retries and reload parity. That baseline passed 14,065 assertions across 34 suites. Final foundation coverage adds 143 durable-transaction checks, including real filesystem failures and child-process crashes.

| Requirement | Evidence |
| --- | --- |
| Invalid/stale/unauthorized actions have no cost | Existing live/admission/targeting tests; stale revision and forged confirmation checks; no extra RNG on invalid preview confirmation. |
| Melee hit and ranged hit/miss once | Frozen six-case parity tests; typed-plan hit/miss tests; live ranged critical commit; both participant revisions and reload checks. |
| Duplicate/altered operation, lost ACK | Repository same-plan/no-byte-change checks; packet replay with identical fixed result; altered arguments and altered turn denied; committed replay after reload. |
| Preparation and final-commit write failures | **12 cases:** preparation/final commit × backup/primary × before temp write/after temp force/before atomic rename. Hooks exercise real `AtomicJsonFile` writes. Exact primary bytes and published state stay unchanged on failure; reload remains coherent. |
| Fixed outcomes after failure | Repeat failing retry, eventual commit, and persisted plan reload preserve faces; no extra RNG; both stock and HP unchanged until commit. |
| Process crash/restart | Two child JVMs terminate via `Runtime.halt(71)` immediately after durable preparation or commit. Parent reload verifies receipt phase and both quantities. Post-commit replay does not write again. This tests abrupt process exit, not physical power failure. |
| Reservations/disconnect/revocation | New-ID preparation and new encounter admission blocked; disconnect event cleanup preserves pending receipt/action reservation; revoked DM cannot retry/cancel; outsider cannot inspect results. |
| Retention/nonnegative/atomic updates | Full 1,024-receipt fixture rejects new preparation without pruning; oldest receipt remains replay-safe. Typed resources validate bounds; existing SRD resource tests and ranged-last-arrow test remain passing. |
| Post-commit presentation failure | Injected immediately after durable commit: COMMITTED remains authoritative, HP changes once, and Retry cannot spend again. |
| Movement/projection/old UI/Tactical | Existing movement, J1 projection/parity, J1.5 launcher, G/H creator/library/management and Tactical GameTests remain included; movement-spent retry regression retained. |

No tests or assertions were deleted to pass. One former duplicate-confirm rejection assertion now checks the intentionally changed idempotent result **and unchanged HP/RNG**; altered-scope rejection remains tested. During validation, a recovery test fixture sent a string page number and correctly failed strict parsing; the fixture was corrected to send a number. Earlier implementation failures were fixed before delivery. Minecraft public-key lookup/deprecation warnings are not reported as successful graphical testing.

## Upgrade, backup and rollback

1. Stop the test server cleanly. Make and verify a **complete coherent pre-upgrade world backup**, including `tacticalcombat/campaigns.json`, its backup and all entity/world data. Keep the old JAR separately.
2. Copy the world for testing. Install the J2 JAR only in that test instance, with Minecraft 1.21.1, Fabric and Java 21. Use matching client/server builds.
3. Load and inspect approved characters, bindings, selected campaign, trackers and Quick Sheet. Schema 1–4 reads are supported; the first successful mutation writes schema 5.
4. Inspect Gameplay Transaction Review after any interrupted session. Do not manually delete reservations/receipts or edit resource quantities to bypass them.
5. **Rolling back the JAR alone is not sufficient.** The older binary does not support schema 5. Restore the complete coherent pre-upgrade world backup together with the compatible old JAR. This intentionally rolls back later gameplay; exactly-once guarantees do not extend across that rollback.

## Minecraft acceptance checklist

Use a disposable copy with a player and zombie bound to separate approved SRD characters, the same selected campaign and DM manager permissions.

1. Start combat through the unchanged J1.5 graphical encounter launcher; resolve initiative.
2. Open SRD attack controls, preview and confirm a supported melee attack. Check target D&D HP.
3. In another eligible turn/encounter, use supported ranged equipment/ammunition. Check one ammunition consumed on hit or miss and correct target HP.
4. Open **DM Quick Tools → Gameplay Transaction Review** and inspect the committed record. It must offer no cancellation or re-execution for COMMITTED entries. Duplicate/lost-ACK packet diagnostics are automated in `GameplayTransactionGameTests`; no manual packet forging or save corruption is needed.
5. If an ordinary save error actually occurs in the disposable test, use the existing DM Retry control while the encounter remains paused. Verify the same roll and no additional cost. Do not manufacture corruption; automated faults cover it.
6. End combat, reopen the campaign characters and verify persisted HP/ammunition. Check manual trackers and other character details remain intact.
7. Stop and restart the test server. Confirm the encounter is not resumed, committed quantities remain, and no additional action/damage appears.
8. Review any PREPARED records only if present. After closure, only a DM may cancel; inspect the retained fixed outcome and cancellation audit. No damage should be automatically applied.
9. Verify Quick Sheet, DM Quick Tools, character library/creator/dashboard and ordinary encounter discovery still function. Change manager permission/selected campaign while the recovery screen is open and verify it clears/revalidates.
10. Report graphical results before moving to the next gameplay checkpoint. No production-world installation is part of this delivery.

## Artifact and remaining limits

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `1769cb253dea22533537b90ecf12de0d3ec94fb1219df3b40412f52c9636fb59`.

No merge or force-push. Only the requested development branch is delivered. Full graphical acceptance is pending. History capacity, single-file filesystem guarantees and trusted-DM cancellation are the explicit conservative limits. Equipment, multiple future resource pools, spell slots, class features, concentration, reaction decisions, effect clocks and history archival remain deferred.

## Complete files changed from the approved base

- `docs/gameplay-transactions-checkpoint-j2.md`
- `src/gametest/java/dev/tacticalcombat/character/SrdSaveFault.java`
- `src/gametest/java/dev/tacticalcombat/combat/GameplayTransactionGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdLiveGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/character/AtomicJsonFile.java`
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java`
- `src/main/java/dev/tacticalcombat/character/GameplayActionPlan.java`
- `src/main/java/dev/tacticalcombat/character/GameplayReceipt.java`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/ui/GameplayRecoveryScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/QuickPanelScreen.java`
- `src/main/java/dev/tacticalcombat/combat/EncounterLauncher.java`
- `src/main/java/dev/tacticalcombat/combat/GameplayRecovery.java`
- `src/main/java/dev/tacticalcombat/combat/SrdActionBudget.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/test/java/dev/tacticalcombat/CharacterSyncTests.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/character/GameplayCrashHarness.java`
- `src/test/java/dev/tacticalcombat/character/GameplayTransactionBaselineTests.java`
- `src/test/java/dev/tacticalcombat/character/GameplayTransactionTests.java`
- `src/test/java/dev/tacticalcombat/character/UiLifecycleTests.java`
- `src/test/java/dev/tacticalcombat/character/UiStabilizationTests.java`
