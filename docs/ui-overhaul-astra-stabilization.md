# UI overhaul — Backend stabilization

Date: October 12, 2026 (America/New_York).

## Repository and status

Continued `/workspace/Cubetable`, branch `dev/ui-overhaul`, from clean HEAD `6ced13a8726493eea68c936ed1434c9fa2fba086` (Checkpoint 5 source `eb3ee9d0973346db3fd1c96443c541f37333a443`). No clone, reset, dependency change, push or merge. Implementation: `a976040e1b6ca24138f185985c7ee8cc1b131689`. This report and the updated Sol handoff are committed separately; the documentation SHA is reported in the final response. The final worktree is clean.

| Area | Status | Result |
|---|---|---|
| Independent bound manual counters | Implemented | Typed, permission-checked durable CAS; active SRD combat snapshot unchanged. |
| Dashboard integration | Implemented | Existing controls select the new campaign operation, with server-projected per-section permission and conflict comparison. Structural bound edits remain locked. |
| Supported event coverage | Partially implemented | Additional actual transitions are instrumented; exact coverage and remaining gaps below. |
| Larger player parties | Implemented | Bounded generation-checked aggregation, compact overflow and full searchable party view. |
| Shared rolls and synchronization | Implemented | Existing bounded polling, stable IDs, private readback and improved session reconstruction. No push architecture. |
| Persistence/migration/failure recovery | Implemented | Atomic schema-4 revision metadata, previous sheets retained; schemas 1–3 migrate. |
| Graphical and real two-client acceptance | Not verified | Embedded GameTests are not real multiplayer clients. Earlier Phase 1–3/UI gates remain open. |
| Excluded gameplay automation | Deferred | No new spells, class/condition/death-save/rest/leveling rules, takeover or NPC delegation. |

## Manual resource architecture and permissions

The campaign store retains a single copy of manual values in `collections.ui_trackers`, `ui_features` and `ui_spellbook`. Its new `manualRevisions` map independently versions inert bookkeeping. `CharacterRepository.track` applies only the existing `RpgRecords.Change` whitelist, validates the resulting sheet, and atomically commits the sheet plus counter revision before publishing memory or a success event.

The current accepted controller (the owner when no replacement controller exists) may set supported current values within existing maxima. Campaign managers may adjudicate the same counters. Current membership, campaign owner-edit lock and the section's field allowlist apply to controllers; managers retain their existing override. An assigned-away owner retains existing read/structural rights but cannot alter the controller's play counters through this path. Unapproved sheets stay unapproved. Ownership, control and bindings never change.

Supported current-value changes: inspiration, manual death successes/failures, exhaustion marker, Hit Dice, spell slots, and MANUAL pools in tracker/feature resource lists. Increasing a counter is an explicit manual bookkeeping choice, not automatic rest recovery. Pool definitions/maxima, feature `uses` metadata, spell preparation/definitions, equipment and narrative stay on the existing structural-edit path. HP, temporary HP, AC, ammunition, movement, initiative, attack/action/reaction budgets and combat-derived fields are not accepted.

Counter updates leave editing/runtime revisions and `SrdSnapshot` inputs unchanged. Generic sheet edits advance both editing and counter revisions. Full counter-bearing section replacement checks the counter revision, preventing an older sheet from overwriting newer bookkeeping or an indexed update from addressing a reordered list. Live HP/ammunition transactions preserve the counter revision and latest manual sections.

The new operation is `MANUAL_COUNTER {character,manualRevision,change}`. Authorized snapshots advertise `manualRevision`, `canTrack`, and `manualSections`. `CHARACTER_SECTION` exposes the revision too. `CHARACTER_EDIT` accepts additive `manualRevision`. Old generic writes of counter-bearing sections without that CAS reject after bookkeeping begins; unrelated legacy editing remains available. The old `MANUAL_UPDATE` bound lock is deliberately retained, and library counter editing remains on its existing independent library revision.

The dashboard uses `DashboardSource` and the shared forms/request queue. Counter controls are separate from sheet editing. Failed/timed-out writes do not display success; staged numeric text survives. Reload/comparison includes independent counter changes. No automatic retry, per-keystroke update, unbinding, approval override or client combat calculation is introduced. Older servers without the new read fields disable campaign counter controls.

## Event producer inventory

All events use the existing session-limited 512-entry `UiEvents` service and permission-filtered readback. No durable event database or chat scraper was added.

| Event | Producer and commit point | Audience/limits |
|---|---|---|
| Manual dice | Existing `DiceService`, after authoritative roll | Existing campaign/context visibility; unchanged legacy packet limitations. |
| SRD attack/hit/miss | Existing `SrdEncounter.persistTransaction`, only COMMITTED | Requester/actor and current campaign managers; actual raw/kept/modifier/total. Preview is not a committed attack. |
| SRD damage | After the same successful HP/ammunition transaction | Actual HP loss and temporary HP expenditure; target/owner/controller and managers. Intent-derived child identity; no disclosure of enemy HP to attacker. |
| SRD zero HP | Committed positive-to-zero transition | Target/owner/controller and managers. Describes DM pause, never automatic death saves. |
| SRD healing | After durable DM healing commit | Actual gain after clamping, not requested amount; existing target/DM audience. |
| SRD turn | Existing authoritative turn start | Existing player participants and managers. |
| Tactical action | After existing `beginAction` reserves the action | Encounter players/DM; explicitly acceptance, not invented hit/miss/dice resolution. |
| Tactical turn | Existing `beginTurn` | Encounter players/DM; existing turn counter. |
| Tactical damage/healing/zero HP | `HealthPresentationMixin` observes health/absorption before and after actual vanilla methods | Affected entity/owner/controller and DM, only campaign-scoped Tactical encounters. Live vanilla application, not a claim that character persistence has already flushed. No event for refused/no-change damage. |
| Approval/revocation | Existing repository approval paths; added edit-driven invalidation | After persistence; authorized character parties/DM. |
| Assignment | Existing assignment transaction | Persisted offer/answer/clear; current role/character access rechecked on history reads. |
| Manual resource | Successful independent counter commit | Owner/current controller and DM; stale/failing writes emit no success. |
| Recorded condition | Changed condition/temporary entries in a permitted structural edit, or exhaustion marker change | Private character audience after commit; explicit no automatic effects. |
| Rest | Existing persisted request/review (including review expiration) | Participants/DM; approval for manual adjudication only. Presentation failure cannot relabel a committed review as failure. |
| Rejection | Admitted SRD operation rejected by server validation | Requester/DM; deterministic request-derived identity, no secret details in shared summary. Direct response retains the error. |

Remaining unimplemented coverage: automatic vanilla status-effect gain/loss/duration events; every Tactical invalid-action reason and hit/miss taxonomy; general out-of-encounter health changes; direct `setHealth` changes outside observed damage/heal methods; generic DM-action audit coverage; autonomous rest-expiry notifications; pre-admission malformed/unauthorized/rate-limit rejection events; new gameplay systems that do not exist. No client guesses fill these gaps. Unscoped legacy Tactical encounters have no campaign event destination and are not broadcast into an arbitrary campaign.

SRD failure/retry tests prove no success events before persistence, and one damage event after a recovered commit. Original accepted intent identity is retained. Tactical observation never authorizes an attack, mutates health or changes action admission. Events may be lost on server crash between committed gameplay and publication; session history is intentionally non-durable.

## Party pagination and synchronization

PARTY keeps `{campaign,page}` and now returns `rosterToken,total,page,pageSize,hasMore`. Eligible player bindings are sorted by character UUID; NPCs are excluded. A request-scoped reverse binding map avoids repeated full binding scans. Each page independently checks membership and PRIVATE/BARS/EXACT disclosure. Private views still receive their authorized exact HP.

The roster token covers ordered identities/controllers, character editing/manual revisions, online state, campaign authority revision and presentation-policy revision. It is consistency metadata, not permission. The HUD aggregates at most 128 pages/1,024 records, rejects duplicate/out-of-order/malformed pages and publishes only a complete matching traversal. A changed token discards partial rows and restarts; campaign/session/disconnect invalidation clears pending data. Offline bound players remain explicitly offline; unbound characters disappear. Live health is sampled on each page rather than frozen globally. Frequent roster/counter changes can restart traversal and delay completion; this limitation is explicit.

The compact HUD shows up to eight rows subject to window height, with an overflow count. L → Inspect party character opens a searchable scrollable full party view displaying permitted health, class, offline state and initiative. Clicking still calls CHARACTER_GET before opening a private sheet. This list uses the current authorized HUD cache and drops stale rows on invalidation.

Polling stays on the existing client-thread request pump: one HUD request pending, 12 client ticks between background submissions, shared eight-tick pacing/16 queued-request bound, existing four-per-20-server-tick gateway limit and rate backoff. PARTY pages progress across normal poll cycles instead of monopolizing the queue; ACTIVE/campaign/initiative/event reads continue. A five-operation cycle has a lower bound near 60 client ticks plus transport/queue latency. A 17-member traversal takes three cycles (roughly nine seconds at 20 client ticks/sec before extra delays); the maximum theoretical roster is deliberately slower. No real-client latency/FPS measurement was performed.

Event IDs/session and ordering remain authoritative. Oversized cursors reset server-side; detection of a changed server session forces a complete cursor-zero history reconstruction even when the new sequence already exceeds the old cursor. Historical reconstruction suppresses animations and sorts by server sequence. Existing bounded dice queues, deduplication and late-callback generations remain. These are polling clients with local animation timing, not synchronized server push.

DM role/membership is rechecked on each read. Private character events additionally recheck current owner/controller/offer access, removing former-controller resource history. Personal historical combat/dice recipients remain entitled to those original events while campaign membership persists; encounter termination alone does not erase history. Client invalidation remains bounded by reads and the existing 100-client-tick authorization lease, not instantaneous push. Server-side privacy filters operate before transmission. Legacy dice packets still lack a universal event identity; selected-campaign event presentation and legacy fallback remain separate paths.

## Persistence and rollback

Schema **4** adds only the bounded manual revision map to the existing atomic campaign store. Schemas 1/2/3 load with revision zero; identities, approvals, bindings, original sheets and schema-3 assignments remain. The next successful write upgrades the whole store. Library/workflow/typed-section schemas remain unchanged. Counter values and revision commit together in the same validated, fsynced, atomic replacement with last-good backup; no cross-file manual-resource transaction exists.

Missing/corrupt primaries recover from valid backups; both invalid files fail closed. Future schemas refuse rollback even when an older backup is readable. A failed write leaves the repository and response unchanged and emits no success event. Tests inject interruption before primary replacement.

**Rollback requires a complete pre-upgrade world backup.** Older builds reject schema 4. Do not edit the schema field to downgrade; the rolling backup may itself have upgraded. Full-file write cost near the existing 16 MiB limit remains an inherited constraint. This task does not implement encounter restoration, crash-time vanilla HP capture guarantees, or permanent history.

## Verification

Baseline command: `/workspace/cubetable-setup/gradle.sh build runGametest`.
Baseline: build successful, **1,456 assertions**, **104 required GameTests**, no failures or required skips.

Final command: the same full build/GameTest command. **BUILD SUCCESSFUL in 42 seconds; 1,523 assertions and 107 required GameTests passed; zero failures, errors or skipped tests.** XML independently confirms 107 test cases with no failed/error/skipped entries. The new foundation group adds 67 assertions and the Minecraft suite adds three GameTests. Log: `/tmp/stabilization-release.log`. Existing tests remain enabled. Two schema-version assertions now require the justified schema 4 rather than schema 3; the legacy bound-update test still sends its actual old request shape and confirms refusal.

New deterministic tests cover all counter actions, owner/controller/DM distinction, policy locks/fields, stale/repeated writes, independent revisions, unchanged complete SRD snapshots, failed write/readback/event nonpublication, restart, migrations with approved bindings, backup/future-schema refusal, source/copy invariants inherited from prior tests, counter-aware form reconciliation, post-reassignment event privacy, role revocation, server-session reconstruction, pagination including 1,024 members and malformed/duplicate pages.

Three additional embedded Minecraft GameTests cover:

1. Accepted controller counter update through the actual authenticated gateway during an active SRD encounter; retained preview commits once, movement grid stays available, approval/revisions remain correct, private damage and admitted rejection events are filtered/deduplicated.
2. A 17-member offline player-bound roster plus excluded NPC, generation changes on unbinding, PRIVATE/BARS/EXACT fields and complete aggregation.
3. Real Tactical action admission, refused damage with no success record, applied damage/healing/zero HP and turn event production through the mixin.

Existing SRD zero-HP/healing and failed-storage/retry GameTests additionally verify event correspondence without removing any previous assertions. Existing combat, movement/performance-bound tests, all five UI checkpoints, library/assignment/approval/persistence and manual-pack suites run in the full command.

Compilation, foundation tests and embedded GameTests are distinct from graphical rendering and real multiplayer. Actual client layout/focus/skin, latency, prolonged polling, FPS, real two-client synchronization and role changes remain **Not verified**. The headless server's existing external Yggdrasil key-fetch warning does not validate or invalidate actual authenticated multiplayer; no remote session was run.

## Real two-client acceptance and Sol handoff

Use a disposable pre-upgrade-backed-up world and the same pinned Java 21/Minecraft 1.21.1/Fabric JAR on server and both clients. Record commit/JAR, roles, GUI scales and Pass/Fail/Not tested.

1. Create manual pool/Hit Dice/slot definitions while unbound, approve and bind. During SRD combat toggle inspiration/death marks and set counters; HP, AC, ammunition, initiative, movement/action budgets and approval must remain unchanged.
2. Preview an attack, change a manual counter, then confirm. It must commit once; repeating either the old counter revision or intent must not apply twice.
3. Have DM and controller edit the same counter from stale dashboards. Require a conflict, retained text, explicit reload/comparison and no optimistic success. Repeat with delayed/dropped replies and reconnect.
4. Assign control while legally unbound; former owner cannot use controller bookkeeping, substitute can, and DM can adjudicate. Apply owner lock/field restrictions and revoke co-DM authority; test stale screens against server rejection.
5. Restart after saves and verify counters, approval, identities and bindings. Inject storage failure only in a disposable test environment: no success/event/runtime change. Test rollback using the complete backup.
6. Exercise 9 and 17 party bindings, offline/reconnect/unbind, class changes and campaign switch during traversal. Check loading, compact overflow, full party list, correct initiative and private sheet rejection.
7. Change PRIVATE/BARS/EXACT during page traversal. New unauthorized exact HP must never be sent; prior cached views must expire/revalidate. Confirm no NPC party entry.
8. Compare authorized shared roll identity/faces/modifiers across clients. Test private rolls, DM revocation, history pagination, reconnect and server restart without old animation replay. Measure actual latency under normal and slow traffic.
9. Confirm SRD hit/miss, HP/temp loss, zero HP and DM healing events match committed state. Repeat a failed transaction/retry. Test Tactical blocked damage, applied health/healing, turns and lethal events. No D&D death/rest/condition automation should appear.
10. Review assignment, approval invalidation, manual counter, recorded condition and rest events. Approved rest still says manual adjudication and performs no recovery.
11. Regress all prior real-client gates: character creation/dashboard, approvals/bindings, SRD/Tactical combat, optimized movement, camera/grid/HUD, dice/cards and Generic D20/Percentile sheets.
12. Sol's polish pass should check counter-enabled versus structural-read-only labels, full-party navigation/search, small-screen overflow, scrolling/focus, typography and dice/log timing. Keep these APIs and authorization boundaries; do not replace the UI architecture.

## Remaining backend work

- Lower-latency delivery and immediate permission invalidation require an explicitly reviewed push/invalidation contract; polling is retained here.
- A frozen whole-party health snapshot or more responsive traversal at extreme roster sizes would require a bounded snapshot protocol. Current tokens guarantee roster/permission consistency, not simultaneous HP sampling.
- Universal legacy dice/event deduplication still needs stable identity in the legacy packet.
- Rich event actor names/target references, complete vanilla status-effect coverage and safe remote skin metadata need dedicated contracts.
- Counter-definition editing while bound, gameplay-resource integration, automatic spells/classes/conditions/leveling/rest recovery, live takeover and remote NPC delegation remain outside this implementation.
- Permanent event history and persistent encounter recovery remain separate future work.

Recommended next step: execute real two-client acceptance and stabilize any findings, then perform Sol's visual-reference polish on the existing screens. No further gameplay phase is implemented or implied.

## Changed files and local checkpoint

Implementation commit: `a976040e1b6ca24138f185985c7ee8cc1b131689`. The following source/test files comprise that commit:

- `src/gametest/java/dev/tacticalcombat/combat/SrdLiveGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/UiFoundationGameTests.java`
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java`
- `src/main/java/dev/tacticalcombat/client/ui/CharacterDashboardScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CubetableHud.java`
- `src/main/java/dev/tacticalcombat/client/ui/DashboardSource.java`
- `src/main/java/dev/tacticalcombat/client/ui/HudLogScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/HudSession.java`
- `src/main/java/dev/tacticalcombat/client/ui/ManagementState.java`
- `src/main/java/dev/tacticalcombat/client/ui/PartyHudScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/PartyPages.java`
- `src/main/java/dev/tacticalcombat/combat/Combat.java`
- `src/main/java/dev/tacticalcombat/combat/CombatPresentation.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/mixin/HealthPresentationMixin.java`
- `src/main/java/dev/tacticalcombat/uiapi/RpgRecords.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiEvents.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiPresentation.java`
- `src/main/resources/tacticalcombat.server.mixins.json`
- `src/test/java/dev/tacticalcombat/CharacterSyncTests.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/character/UiLifecycleTests.java`
- `src/test/java/dev/tacticalcombat/character/UiStabilizationTests.java`
- `src/test/java/dev/tacticalcombat/client/ui/DashboardUiTests.java`

Documentation: `docs/ui-overhaul-astra-stabilization.md` and `docs/ui-overhaul-sol-handoff.md`. No remote publication or merge was performed. The final report commit follows the tested implementation and changes documentation only.
