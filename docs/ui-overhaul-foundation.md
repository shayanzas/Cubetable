# Cubetable UI overhaul — foundation checkpoint

Date: October 11, 2026 (America/New_York). This branch implements the backend foundation and concrete client transport for a later graphical overhaul. It does not implement the visual redesign or Phase 4–6 gameplay.

## Repository and checkpoints

Repository: `shayanzas/Cubetable`. Work continued in the existing checkout from the clean, locally verified optimization checkpoint `a5758b506212b01c7e5dc70409dd037e64e7a5e8`, including all high-speed scheduler and combat/network/client optimization commits. New local branch: **`dev/ui-overhaul`**. No reclone, reset, branch overwrite, push or merge was performed. The earlier fork clarification remains unresolved; this task did not change remotes or publish anything.

| Checkpoint | Local commit | Verification at checkpoint |
|---|---|---|
| Baseline | `a5758b5` | Build successful; 927 assertions, 87 GameTests. |
| Foundation A | `b41438f` | Private library and structured inert records; 958 assertions, 87 GameTests. |
| Foundation B | `30d8cf8` | Versioned lifecycle API, schema-3 assignment transactions and substitute-player authority; 983 assertions, 89 GameTests. |
| Foundation C | `72b1659` | Manual spellbook/resource operations; 1,003 assertions, 89 GameTests. |
| Foundation D | `9b8b77a` | Events, presentation, rest adjudication records and client transport; 1,028 assertions, 92 GameTests. |
| Foundation E implementation | `117348b4e28aba83cb065bcea50e0444c54f6597` | Migration/approval safeguards and binding discovery; 1,033 assertions, 92 GameTests. |

The final test/documentation commit follows these source commits and adds explicit campaign-copy HP isolation, new-API owner-lock and failed-assignment-write assertions. Its SHA is reported in the final response; `git log` identifies it without a self-referential hash in this file. The final branch is committed with a clean worktree.

## Architecture and behavior

The independent library stores private player-owned drafts in `tacticalcombat/library.json`, schema 1. It has no campaign membership, approval, binding or live HP linkage. Owners can create, read, bulk-update sections, validate, finalize and duplicate drafts. GUIDED/MANUAL share the same sheet format and typed extensions; the mode does not change server authority. Incomplete narrative/creation drafts are supported, while supplied combat sections must pass the existing strict codec. Finalization means supported authoring data is structurally valid, not full D&D character-build legality.

Submission writes an independent campaign copy through `CharacterRepository.submit`, honoring membership, field/edit locks and campaign approval policy. The source library is unchanged. Campaign identity, editing/runtime revisions, approval, binding, resources and later progression are independent. An optional source ID/revision/submission UUID is stored in `collections.ui_origin`; it grants no authority. A stable submission UUID makes a retry return the same campaign copy. No cross-file mutation is needed for submission.

Existing campaign storage advances to schema 3 with a separate assignment map in the same atomic state as characters and bindings. Schemas 1/2 retain original identities, policies, approvals and bindings; no campaign character is forcibly moved into a library. Next successful mutation writes schema 3. Ownership, accepted controller, current selection and Minecraft binding are distinct. A DM can offer control, the target member accepts/declines, and the DM binds the unbound approved character to that player's entity. Clearing control restores ownership-based control without transferring ownership. Last 16 control changes persist. Both SRD encounter action and movement authorization now recognize accepted player controllers. Ordinary switching, approval rewrites, assignment changes and binding remain locked where required.

Typed `ui_*` collections hold identity/roleplaying, recorded proficiency/modifier data, progression, inventory/currency metadata, custom spell definitions and separate selections, manual slots, features/conditions/resources, Hit Dice, inspiration, death-save marks and exhaustion. Unknown fields, invalid counts/values/references, excessive descriptions and client-created AUTOMATED flags reject. These records never execute formulas or grant mechanics. Phase 3 `srd_combat` remains the automatic SRD source, while Tactical keeps its existing vanilla-health integration. Recorded spell modifiers/progression levels are explicitly informational. Sheet inventory cannot create physical Minecraft items.

The new UI v1 protocol exposes explicit operations, not an arbitrary database endpoint. Authenticated identity comes from Fabric's server connection, with bounded payloads/depth, four requests per 20 ticks and monotonic connection replay protection. UUIDs/revisions travel as internal fields from read models. Private library data stays owner-only; campaign sheets require owner/controller/DM authorization and membership. Owners' edit policies and locks still apply. Accepted control does not grant owner editing rights. Existing Character v2/SRD v1 protocols and commands remain available; the handoff explains exactly which existing APIs to reuse for campaign combat authoring and HP adjudication.

`ClientUiApi` provides capability detection, at most eight pending callbacks, correlation, timeout and disconnect cleanup. It does not replace unsaved screen data or implement a GUI. HUD settings are versioned, bounded client-local records with atomic load/save/reset helpers; no layout changes affect character revisions.

Party/initiative models project existing state. Party sharing includes only player-bound campaign characters, excludes NPC enemies, and defaults to private HP. Managers may choose bars or exact HP. Initiative reads use the actual encounter's permission checks. Bounded session event history has stable identities, ordering, cursors and current permission filtering. It records actual committed SRD rolls/turns/healing, character approvals/assignments/edits, manual campaign-context dice and rest actions; it does not scrape chat or accept client-produced outcomes.

Rest requests live in separate schema-1 `ui-workflows.json`, with DM presentation/rest policies. The implemented workflow checks party presence, radius, dimension and encounter eligibility, then permits DM review to **APPROVED_FOR_ADJUDICATION**, REJECTED or EXPIRED. It never claims that a rest began or recovered resources. Full recovery/interruption/time rules remain unavailable.

## Requirement classification

| Requirement | Status | Practical boundary |
|---|---|---|
| Existing workspace, optimized baseline, separate local branch | Implemented | Pinned dependencies unchanged; no remote writes. |
| Independent private character library | Implemented | Bounded server-local library; no cross-server portable export UI yet. |
| Independent campaign copies and provenance | Implemented | Source survives; copies have separate resource/progression state; retry identity supported. |
| Versioned shared sheet data and strict validation | Implemented | Additive inert sections; no second automatic combat engine. |
| Guided/manual creation foundation | Partially Implemented | Shared persisted sections/mode and structural validation work. Full species/class/background catalogs, prerequisites and build recommendations are not implemented. |
| Campaign management and human-readable discovery | Implemented | Explicit API tables in handoff; existing command paths retained. |
| Review/request changes | Partially Implemented | Approval/revocation and private notifications work. No durable textual review conversation. |
| Ownership/control/selection/binding separation | Implemented | Out-of-encounter accepted player rebinding, durable history, server validation. |
| Live DM takeover/NPC substitute/reconnect restoration | Deferred | No mid-encounter transaction or non-DM remote NPC authority; no automatic reconnect transfer. |
| Spellbook/inventory/features/progression data | Implemented | Structured custom/manual records and bounds; no automatic effects/items/advancement. |
| Standard spell catalog | Deferred | Endpoint explicitly reports empty; no unverified/commercial definitions imported. |
| Inspiration/death marks/Hit Dice/slots/resources | Partially Implemented | Library/unbound character APIs operate and persist. Bound edits remain locked to preserve current approval and combat CAS semantics. |
| HP authority | Implemented/preserved | Existing unbound authoring and paused SRD DM heal; no generic client HP or damage override. |
| Party/initiative readback and HP visibility policy | Implemented | Permission-filtered, bounded and drawn from existing state; no HUD renderer. |
| Structured events/dice contract | Partially Implemented | Bounded queryable history and listed producers work. Not every Tactical damage/condition/rejection/zero-HP source is instrumented; remaining event kinds are contracts, not claimed producers. |
| Notifications | Partially Implemented | Pollable authorized events; no push subscription, durable notification inbox or reconnect notification automation. |
| Rest request/review data and eligibility | Implemented | Approval for manual adjudication only, not automatic rest/recovery. |
| Configurable offline/interruption/override/time rest rules | Deferred | Future concepts; online party capture and fixed request radius are current behavior. |
| HUD preferences foundation | Implemented | Local model/load/save/reset; screen must choose fixed config path and render it. |
| Migrations, failure recovery and regression verification | Implemented | Atomic stores and tested schema compatibility/fail-closed behavior. |
| Graphical and real two-client behavior | Not Verified | No graphical clients or remote multiplayer session launched. |
| Full UI and Phase 4–6 gameplay | Deferred, explicitly excluded | No decorative screens, automatic death saves/conditions/spells/classes or new combat engine. |

Bound manual tracking is the main architectural follow-up. Currently replacing a sheet advances its editing revision and may invalidate approval; SRD active snapshots include editing/runtime revisions. Bypassing this to make an Overview toggle appear successful could pause combat or undermine optimistic concurrency. This checkpoint preserves the lock. A future change needs independently revisioned inert/manual bookkeeping and explicit policy semantics, with accepted-action and conflict tests, before enabling bound manual controls. Live takeover similarly needs a dedicated encounter transaction; it cannot reuse a generic assignment mutation.

## Security and persistence evidence

- Tests reject forged admin/membership/ownership, ordinary self-approval, unauthorized library/character inspection, another recipient accepting an offer, controller-as-owner edits, stale revisions, out-of-bounds resources, invalid spell references and client automation flags.
- Library/campaign IDs are preserved across reload. Schema-1 campaign secure defaults remain; schema-2 assignment migration preserves characters. Future store or section versions refuse rollback even with a valid older backup.
- Atomic validation, bounded UTF-8 storage, fsynced same-directory replacement, backups and write-before-publish remain. Failed library, assignment and presentation-policy writes cannot publish their intended in-memory state. Existing HP/ammunition interruption and recovery tests remain intact.
- Campaign data is now schema 3; older mod builds cannot read it. Keep a pre-upgrade world backup for rollback, rather than downgrading the file in place.
- Sheet limit remains 32,768 characters; each full store is at most 16 MiB. Per-entry/list limits are documented in the handoff. Full-file rewrites retain the earlier near-limit store cost; this task does not claim a new persistence-performance improvement.
- All world access is on the server path; no worker accesses live world state. Rest approval rechecks actual players and locations. Stale eligibility lists never authorize a later binding without revalidation.
- Events retain at most 512 entries, rest requests 128, control history 16 per character, request callbacks eight per client. Events restart empty with a new session ID. They are not durable audit history or crash-time encounter recovery.

## Verification

Baseline and all checkpoint builds used the existing complete Java 21 JDK and `/workspace/cubetable-setup/gradle.sh`. Java/Minecraft 1.21.1/Fabric Loader 0.16.9/Fabric API 0.116.1+1.21.1/Gradle 8.10.2/Loom 1.8.13 pins were not changed.

The latest final command and exact counts are recorded below after its completion. The original 927 assertions and 87 GameTests remain. The schema migration assertion was intentionally updated from “writes schema 2” to “writes schema 3”; it was not removed or weakened, and explicit schema-2 preservation tests were added.

New dependency-free test groups:

- `UiLibraryTests`: ownership, persistence, incomplete/finalized drafts, section round trips, strict fields, stale updates, independent identities, backup/future-version refusal and interrupted writes.
- `UiLifecycleTests`: campaign permissions, independent copies/HP, durable submit retries, approval, assignment/acceptance/restoration, ownership separation, bound locks, owner edit policy, schema migration and failed assignment writes.
- `UiRpgRecordTests`: spell references/preparation, custom validation, manual trackers/slots/Hit Dice/resources, restart, bounds, inert inventory/features and unchanged authoritative SRD inputs.
- `UiPresentationTests`: authoritative dice totals, event visibility/order/deduplication/retention/cursors/session reset, rest authorization/revisions/expiration/reload, default private HP, failed policy writes and local HUD settings.

Five new Minecraft GameTests exercise the authenticated UI gateway/replay/size limits, accepted controller movement with original ownership retained, party privacy and live rest eligibility, committed-attack structured-roll identity/deduplication, and rest review without character recovery plus actual packet-codec bounds. Mock players are embedded test connections, not two graphical clients.

Intermediate failures were investigated: an old expected schema number after the intentional migration; compile-only test helper exception handling/name shadowing; a real second owner-only movement check corrected to accepted controller authority; Gson serializing an empty immutable collection view as null (now concrete bounded lists); and the packet-size assertion catching the wrong codec exception. The final suite must pass after those fixes. No existing test was disabled and no dependency was changed to make tests pass.

No visual correctness, skin loading, animation, small-screen layout, drag behavior, real RTT, production-load performance or prolonged multiplayer memory behavior is claimed. The earlier Phase 1, Phase 2 and Phase 3 real-client acceptance gates remain open.

## Practical two-client acceptance for the future UI

Use a disposable backed-up world, Java 21 and the same JAR on server and two actual clients. A is campaign DM, B ordinary player. Record commit/JAR hash, logs, GUI scale and Pass/Fail/Not Tested per case. Until the menus exist, use a development harness calling the documented ClientUiApi operations; do not describe the current branch as having a graphical hub.

1. Verify unsupported-server controls stay disabled; test callback errors/timeouts and reconnect sequence reset.
2. B creates a private library draft, changes sections, reconnects/restarts and reloads. A must not discover B's library through the API. Test two stale editors without losing dirty local text.
3. Create/select a campaign, invite B and submit the finalized draft. Repeat the same submission identity: one copy. Explicitly submit a second copy: distinct identity. Change library/campaign HP/progression separately and confirm no propagation.
4. B cannot approve, forge a role or inspect unrelated sheets. A reviews/approves. Test owner locks and auto-approval policies without silently modifying existing approvals.
5. DM offers control to B; B accepts/declines. Bind through candidate discovery. Owner identity stays fixed. Active selection does not itself bind. While in combat, selection/rebinding/reassignment/re-approval must fail without altering turn, action or resource state. End/unbind before restoration.
6. Save custom spell/slot/resource/inventory/condition records. Confirm no casting, physical item grant, automatic damage/AC, rest recovery, death saves or condition effects. Bound manual controls must show the documented unavailability, not succeed optimistically.
7. Compare private, bars and exact party HP; NPC enemy records must not appear. Revoke a co-DM role and refresh event/encounter views; private information must disappear.
8. Request a short/long rest, move a member away or enter combat, then approve: eligibility must reject. Valid approval must say adjudication, with no healed HP/slots. Test expiration/restart and duplicate review.
9. Compare SRD dice faces/kept indices/modifier/total with existing results, including advantage and a storage retry. Animate once per event identity. Verify another ordinary player cannot read private attack modifiers. Test event history pagination and reset after restart.
10. Re-run existing Phase 1 Tactical movement/inventory/HUD, Phase 2 sync/conflicts and Phase 3 combat/grid acceptance checklists, including 120/300-foot pending grids. After Sol builds screens, test 854×480, common GUI scales, resizable sheets, manual Generic D20/Percentile packs, camera/fading/dice/cards and layout persistence/reduced motion.

## Readiness

The branch is a tested foundation for beginning the UI work in [Sol's operation map](ui-overhaul-sol-handoff.md). The explicit limitations above are part of that contract. Sol can build the hub, campaign/library browsers, shared authoring sections, unbound manual records and existing combat readback now. Broader graphical acceptance and the identified bound-edit/live-control architecture follow-ups remain necessary. No permission is implied to push, merge or start excluded gameplay phases.

## Final verified result

`/workspace/cubetable-setup/gradle.sh build runGametest` completed **BUILD SUCCESSFUL**. Compilation succeeded; **1,037 assertions**, **92 required Minecraft GameTests**, **zero failures, errors or skipped tests**. New coverage is 110 assertions and five GameTests above the original baseline. Final assertion groups: foundation 320; repository 39; UI library 32; UI lifecycle 31; UI RPG records 20; UI presentation/workflow 27; prior synchronization 59; persistence interruption 8; SRD core 319; movement 91; authoring/resources 80; resource fault 11. XML results independently confirm 92 cases and no failed/skipped entries.

The final test-only additions do not change the release JAR. Artifact: `build/libs/tactical-combat-0.1.0.jar`, SHA-256 `57c2605ce08a8b21f76160277aeb58b601ccf61be93c3fb97e36c3702eee78bc`. Graphical/client and remote multiplayer verification remain **Not Verified**. No observed automated-suite regressions remain. Full performance profiling was not repeated for this foundation; existing optimized movement correctness/budget tests still execute.

## Changed files relative to the optimization checkpoint

- `docs/ui-overhaul-foundation.md`
- `docs/ui-overhaul-sol-handoff.md`
- `src/gametest/java/dev/tacticalcombat/combat/UiFoundationGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/TacticalCombatMod.java`
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java`
- `src/main/java/dev/tacticalcombat/character/LibraryRepository.java`
- `src/main/java/dev/tacticalcombat/character/UiWorkflowStore.java`
- `src/main/java/dev/tacticalcombat/client/ClientUiApi.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/combat/Combat.java`
- `src/main/java/dev/tacticalcombat/combat/CombatManager.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`
- `src/main/java/dev/tacticalcombat/dice/DiceService.java`
- `src/main/java/dev/tacticalcombat/net/UiReplyPayload.java`
- `src/main/java/dev/tacticalcombat/net/UiRequestPayload.java`
- `src/main/java/dev/tacticalcombat/uiapi/HudPreferences.java`
- `src/main/java/dev/tacticalcombat/uiapi/RpgRecords.java`
- `src/main/java/dev/tacticalcombat/uiapi/SheetSections.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiEvents.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiPresentation.java`
- `src/test/java/dev/tacticalcombat/CharacterSyncTests.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/character/UiLibraryTests.java`
- `src/test/java/dev/tacticalcombat/character/UiLifecycleTests.java`
- `src/test/java/dev/tacticalcombat/character/UiPresentationTests.java`
- `src/test/java/dev/tacticalcombat/character/UiRpgRecordTests.java`
