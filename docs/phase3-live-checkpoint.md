# Phase 3 live SRD checkpoint

This report supersedes the [partial Stage C prerequisite checkpoint](phase3-completion-status.md). **Controlled SRD combat is implemented and headless-tested. Full Phase 3 release acceptance remains Not Verified.** Automatic survival-world SRD combat remains disabled. The supported encounter is an explicitly started, public-position free-for-all with approved server characters, DM-controlled NPCs, one attack per action, and a zero-HP pause.

## Repository and verification gates

Branch: `dev/phase3-srd-combat`, repository `shayanzas/Cubetable`. Starting clean HEAD was `0bba9a32fb3ec5ce3a1cc8940a926f8e2f169547`. Existing Stage A, Stage B, character authoring, storage, Tactical and manual systems were retained. No dependencies, published branches or remotes were changed. No push or merge was performed.

Local implementation checkpoints, each committed after its full build/GameTest gate:

| Commit | Verified scope | Assertions / GameTests |
|---|---|---:|
| `e638983dad035a2c8215e620f10aec4386b9d02c` | C1: shared ownership, isolation, unsupported-hook rejection, cleanup | 890 / 53 |
| `e1061b64b98571c4b3c2a1eb7ca92b3118ed17a1` | C: live turns, player movement, protocol preview/confirmation, durable HP, failure recovery | 890 / 60 |
| `f5fd3d70dc23af968744ce9a615cee6ed336eff4` | D: sampled cover, reach and live equipment restrictions | 890 / 63 |
| `4a989bc87a1f6e00b6b37a7bf9b98361b2225256` | D: ranged/thrown attacks, ammunition, client workflow, compatibility cleanup | 897 / 68 |
| `a16743a9923a8429284debcd4f4125451a61ce45` | E: complete command/protocol lifecycle, disconnect/capability/default isolation, flood and workload checks | 897 / 72 |

The Stage E implementation commit and final verification evidence are recorded below. This report follows in a documentation commit; obtain its exact HEAD with `git log -1 --format='%H %s'` rather than embedding a self-referential hash.

## Shared encounter authority and lifecycle

`Encounter` is the small common lifecycle contract; `Combat` remains the Minecraft Tactical implementation. `CombatManager` now holds **one** `EncounterIndex<Encounter>` and scheduler. `encounter(entity)` is the generic ownership lookup. `get(entity)` is the explicitly Tactical-only adapter. SRD does not maintain a competing ownership registry.

All owned actors have one stable encounter UUID and campaign/rules identity. Generic ownership drives existing interaction and movement protections. Melee, delayed projectiles, splash, retaliation, environment, beam, volley and active-item paths fail closed for SRD actors instead of falling through to vanilla damage. Tactical actions retain their existing resolver. Manual dice audiences use the same encounter identity, without becoming automated SRD actions.

`/tbc srd test-public-ffa <comma-separated actor UUIDs or online player names>` is the explicit, manager-authorized admission path. It accepts 2–16 loaded, unique actors in one world with valid approved SRD bindings, compatible campaign membership, positive D&D HP, supported logical footprints and centered walking positions. Players must advertise the SRD v1 state channel. Admission validates the complete set and rolls back failed claims. No automatic recruitment, legacy-sheet conversion, vanilla-stat inference or arbitrary late join is provided. NPCs use explicit approved server bindings; vanilla AI is suspended and restored on cleanup. The command name and acknowledgment disclose that **every participant is hostile and every position is public, even for invisible actors**.

Disconnect, lost protocol capability, removal, world transfer, vanilla death or unloading ends the controlled encounter and releases remaining actors. Unexpected character revision changes pause it. Out-of-turn and paused actors are pinned to authoritative anchors; active requests also validate anchors before resolution, closing a move-packet-before-tick gap. Campaign default changes cannot convert existing characters or mutate active encounter rules. Character rebinding/selection remains prohibited during ownership.

## Initiative and movement

The server uses the existing SRD Dexterity/initiative adapter and server dice. Natural 1/20 has no attack semantics in initiative. Rolls, current actor, rounds and turn identity are retained. Player-only initiative ties require matching choices from all tied players; NPC/mixed ties require an authorized manager. Until resolved, the encounter is pending. No hidden Dexterity/UUID fallback is used.

Individually authored NPCs roll individually. Grouped initiative for identical monster templates is **not implemented** because this scope does not yet have monster-group identity; it is not claimed as full SRD monster initiative support.

The existing Stage B `SrdMovementTurn` drives live execution and the client preview. One block is five feet; eight directions use five-foot diagonals. Weighted terrain, ordinary one-block elevation, footprints, collision, occupancy, dynamic revalidation, cancellation and completed-square accounting are preserved. The active turn gets character-derived feet; attack resource revisions update the controller snapshot without refunding movement. Request, actor, campaign, encounter and turn identities remain validated. Searches retain the 4,096-node bound and explicit truncation.

The client has a reachable-grid world overlay and a local map with selected-path cost/remaining feet. The map is a compact window, not a full-map navigation interface. Advanced climbing, jumping, swimming, flight, long-range map panning and broad creature passage remain outside this scope. The existing Tactical camera/grid/HUD was not replaced by the SRD screen.

## Confirmed attacks and durability

The implemented workflow is **weapon/mode/ability/hands → target → server preview → explicit confirmation → server result**. Ordinary entity clicks do not resolve SRD attacks. Preview includes attack formula, advantage/disadvantage, damage dice/type/range, reach/range, cover and action/ammunition cost. It does not reveal enemy AC or defenses, roll dice, or spend an action.

A server-issued intent captures requester, actor, target, turn, immutable character/runtime snapshots, positions and validated targeting geometry. Confirmation rechecks them and the current action/movement state. Invalid, stale, cross-player, old-turn or duplicate requests cannot spend, damage or reroll. The accepted action UUID also identifies this scoped single-target effect. The action is reserved before server dice; a miss spends it too. No multi-target action or extra attack is advertised.

`SrdEncounter.Transaction` explicitly tracks `PREPARED`, `SAVE_FAILED` and `COMMITTED`. The server retains fixed attack/damage rolls, expected snapshots and resulting health. `CharacterRepository.commitSrdAction` validates both actor records and atomically persists attacker ammunition plus target HP/temp and both runtime revisions before publishing memory or returning success. A miss still consumes stock and advances revisions. Editing revisions/approval are retained; stale whole-SRD-collection edits cannot restore old resources.

On write failure, no new HP/ammunition is published, the action stays reserved, and the encounter pauses. A manager may retry **the same** transaction against **the same** expected revisions, without rerolling or rebasing. A conflicting revision requires adjudication/end. Repeated successful confirmation/retry cannot apply the effect twice. Existing atomic replacement, backups, validation and fail-safe loading are reused; no second character database was added.

**Durability boundary:** committed HP/ammunition survives normal reload. Encounter order, remaining movement/actions, pending dice and action receipts remain in memory. A process crash or explicit end can abandon a pending failed transaction; backup recovery may roll back the last resource write. This is not persistent encounter restoration or crash-atomic action-ledger recovery. Phase 7 must coordinate durable action receipts and encounter state. The UI warns before ending a failed-save encounter.

## Damage, zero HP and equipment

Stage A hit/AC, critical dice, ability/proficiency, typed immunity/resistance/vulnerability and temporary-HP arithmetic are used in live attacks. D&D damage never calls vanilla damage. Supported scoped healing is manager-only while paused, with snapshot CAS and validation; it leaves the encounter paused for explicit termination. At zero D&D HP, the **whole encounter pauses**: neither PCs nor NPCs take ordinary actions, and no vanilla death, death save, unconsciousness, stabilization or automatic NPC deletion is inferred. Ending retains resources. Outside the controlled encounter, Minecraft exploration remains vanilla; this is not D&D environmental survival support.

Runtime plans enforce the existing supported weapon catalog, finesse choice, versatile/two-handed use, shield restrictions, heavy-Small disadvantage, armor-proficiency penalties, proficiency, critical dice and advantage/disadvantage cancellation. Ranged and thrown modes use the existing catalog ranges. Beyond-maximum targets reject before spending; long range and an eligible adjacent hostile impose disadvantage. One attack/action limits the supported crossbow loading case; broader loading interactions, Extra Attack, Multiattack, bonus actions and reactions are deferred.

Approved virtual `arrows`, `bolts` and `daggers` are bounded 0–1,024. Bows/crossbows consume their stock on an accepted hit or miss; a thrown dagger consumes one dagger. Zero dagger stock also prevents subsequently using that dagger in melee. Physical inventory mapping and ammunition recovery/retrieval are not implemented. The schema remains version 1 with strictly validated additive optional fields: old missing arrows/bolts read as zero; an existing explicitly authored DAGGER defaults to one dagger, otherwise zero. This compatibility interpretation does not rewrite saves on read. New form fields use the same approval/edit workflow; client approval, formulas and derived combat values are never trusted.

## Targeting and visibility mapping

`SrdTargeting` uses five-foot logical spaces and server collision shapes, independently of camera fading. Distance is the closest logical-space grid distance, with vertical separation, capped at the supported 600-foot query range. All queried chunks must already be loaded; occupant queries reject above 256 creatures.

The Minecraft cover adaptation samples a 4×4 target-facing rectangle using target logical/physical width and physical height. Fewer than eight blocked samples gives no cover; eight or more gives half (+2 AC); twelve or more gives three-quarters (+5); sixteen gives total cover and rejection. Intervening creature hitboxes can contribute at most half cover. Cover bonuses do not stack. Walls, partial blocks, corners and changed geometry are rechecked at confirmation. This is an explicit voxel approximation, not a rule claiming that SRD cover is defined by sixteen rays.

In this **public-position** test mode, Minecraft invisibility supplies unseen-attacker advantage and unseen-target disadvantage; both cancel normally. Unseen does not itself mean untargetable. Total cover still prevents the attack. Hidden locations, guessed squares, darkness, blindsight/truesight, stealth adjudication, teams/factions and general condition automation are not implemented. Do not use this mode when participant positions must remain secret. Adjacent-hostile checks use this same limited sight model.

Rules target: 2014 SRD 5.1, equipment pp. 62–66, combat pp. 90–99 (initiative, movement/position, making an attack, cover, damage/healing). The Stage A/B reports retain detailed arithmetic and movement references. This is not SRD 5.2/2024 compliance.

## Networking and client presentation

`srd_request_v1` and `srd_state_v1` are separate bounded versioned channels; existing character v2 and Tactical protocols remain. Requests have encounter/turn/request identities, operation ≤16 characters and JSON ≤4,096 characters/depth 8. Replies limit JSON to 262,144 characters, message to 256 and code to 24. Client snapshots cap actors at 16 and grid nodes at 4,096 with validated preview reconstruction.

The authenticated server connection supplies authority. Character owners see their private resources; managers see authorized campaign actors; unrelated viewers receive no snapshot. Public order/positions are explicitly within the admitted test scope. Authoritative dice results go to the acting controller and authorized watching managers/acting PC, not all nearby players. Broad secret-DM roll controls are deferred.

Snapshots are coalesced by encounter revision for watchers; end/permission failure clears the view. Requests are capped at 12 per 20 ticks per connection, with expensive grid queries additionally capped at two per 20 ticks. Disconnect/server stop clears rate/watch state. Every mutation revalidates permissions; capability loss terminates admitted player encounters.

`/srdcombat` opens a separate three-page panel with preview confirmation, selected path, turn/initiative status, private HP/stock, tie choices and restricted DM controls. It does not replace or silently overwrite a sheet editor. New revisions invalidate stale previews/grids; rejection reports a reason. Server dice use the existing bounded animation queue with a distinct “SRD server / Accepted attack” label and action-ID presentation deduplication, separate from manual dice requests. Full small-screen, latency, graphical overlay/camera and remote transport behavior remain Not Verified.

## Requirement classification

| Requested area | Status | Boundary |
|---|---|---|
| Baseline preservation and C1 shared ownership gate | Implemented | One registry/scheduler; prior tests retained |
| Authorized encounter admission, campaign/profile isolation, cleanup | Implemented | Explicit 2–16 actor test encounters only |
| Recruitment and general monster support | Partially Implemented | Explicit approved bindings; no dynamic recruitment/templates/AI |
| Initiative, rounds, turn ownership and tie decisions | Implemented for individual actors | Identical-monster grouped initiative Deferred |
| Live Stage B movement and synchronized previews | Implemented | Conservative Stage B terrain/passage limits retained |
| Attack selection, confirmation, revalidation, expenditure | Implemented | One single-target weapon attack/action |
| Durable HP/ammo and failed-save retry | Implemented | In-memory action receipt; full crash recovery Deferred |
| Multiple-target/extra damage effects | Deferred | No such action is offered; no partial multi-target success claimed |
| Typed damage, criticals, temporary HP, scoped healing | Implemented | Tested live and through existing pure arithmetic |
| Zero-HP/environmental interim policy | Implemented | Pause/end; unrestricted SRD survival Deferred |
| Bounded private protocol and authoritative dice presentation | Implemented | Graphical presentation Not Verified; broad secret controls Deferred |
| Reach/range, weapon properties and virtual ammunition | Implemented within supported catalog | Inventory/retrieval/broader loading interactions Deferred |
| Cover and unseen-attacker/target mechanics | Partially Implemented | Documented geometry and disclosed-position invisibility adapter; broader sight/hidden rules Deferred |
| Stage C and D Minecraft integration gates | Implemented and passed | Headless actors and embedded mock players |
| Stage E end-to-end and regression gate | Implemented | Real command/handlers with mock players, not two clients |
| Stage E performance exercise | Partially Implemented | Small-fixture measurements; production stress/load Not Verified |
| Phase 1/2/3 real-client acceptance | Not Verified | Graphical environment unavailable; checklist provided |
| Phase 4+ rules and full encounter recovery | Deferred | No classes, spells, reactions, death saves or broad conditions added |

No requirement is reported as blocked by build/dependency setup. General Phase 3 release acceptance is pending real-client validation and the scope limitations above.

## Automated evidence and performance

Before changes, `/workspace/cubetable-setup/gradle.sh build runGametest` passed with **890 assertions / 50 GameTests** (`/tmp/phase3-resume-baseline.log`). The same command on the final implementation passed: **BUILD SUCCESSFUL in 34s**, **897 assertions / 72 GameTests**, **zero failures, errors or skips** in `build/gametest-results.xml`. Final log: `/tmp/phase3-stage-e-gate.log`. No previous test was removed, weakened or disabled. These workspace logs are local artifacts; the summary is committed here for durable review.

| Pure suite | Assertions |
|---|---:|
| Foundation | 320 |
| Character repository | 39 |
| Character synchronization | 58 |
| Persistence interruption | 6 |
| SRD core rules | 319 |
| SRD movement | 69 |
| SRD authoring/resources | 80 |
| SRD resource fault recovery | 6 |
| **Total** | **897** |

The 22 new GameTests comprise three shared-ownership tests, seven live/protocol tests, three targeting/equipment tests, five ranged/resource tests and four Stage E tests. They cover shared claims, cross-boundary effects, invalid admission, tie agreement, real movement, confirmed hits/misses, forged fields, replay/stale rejection, codec bounds, private state, zero HP, fixed-roll write failure/retry, cover changes, long/thrown/adjacent-hostile range, two hands, loading scope, invisibility cancellation, atomic ammunition+HP reload, disconnect, campaign changes and flood limits. Existing Phase 1/2, Stage A/B and character lifecycle tests all reran.

`campaignCharacterCommandAndProtocolCompleteCombatLoop` uses embedded mock DM/player connections, the production character handlers, service binding, actual Brigadier start/end commands and actual SRD request handler: campaign select → template → approval → binding → admission → initiative → grid/movement → preview → confirmation → server RNG/AC/damage → durable HP → authorized DM state → next NPC turn → cleanup. A development-only capability helper simulates channel advertisement; it does not prove a real handshake. The server RNG is seeded in this deterministic test, never supplied by the client. The first test version incorrectly assumed NPC array index instead of initiative order; it was corrected to select by UUID, then the complete suite passed. No product behavior was weakened for that assertion.

Small-fixture measurements from the final run, server-thread `System.nanoTime`, two bound actors, flat 12×9 floor, 55 reachable nodes:

| Operation | Samples | Median | p95 | Maximum |
|---|---:|---:|---:|---:|
| Attack preview (including current snapshots and geometry) | 100 | 159 µs | 234 µs | 570 µs |
| Reachable-grid query | 10 | 58,896 µs | 86,942 µs | 86,942 µs |
| Idle encounter tick | 100 | 84 µs | 156 µs | 697 µs |

These are wall-time observations in a shared headless environment, not a benchmark guarantee. **Grid queries exceeded a 50 ms tick budget even in this small fixture.** The additional two-queries-per-20-ticks limit bounds a repeated-request burst but does not solve single-query latency or many-client load. Profile and optimize Stage B terrain/search work before wider deployment; preserve dynamic revalidation and bounds. No production-throughput, 16-actor stress, remote packet latency or storage-throughput claim is made. Persistence/fault recovery, large footprints, bounded decoding, queues and concurrent encounters were exercised by tests, not all separately profiled. Synchronous whole-store atomic writes remain a load risk.

Dependencies unchanged: Java 21, Gradle 8.10.2, Loom 1.8.13, Minecraft 1.21.1, Yarn 1.21.1+build.3, Loader 0.16.9, Fabric API 0.116.1+1.21.1. Compilation of client/server code succeeded. Existing Authlib public-key DNS warnings, deprecated mock-player helper warnings and Gradle deprecation warnings remain; online authentication was not tested.

Artifact: `build/libs/tactical-combat-0.1.0.jar`.

```text
SHA-256 eac554089c0ea4f1e8a9c94991da927e056a4a6a8b005d0351c5536305f026cc
```

`DISPLAY` is unset and `Xvfb` is unavailable. No graphical client or real two-client session ran. Rendering, real transport/handshake, small-screen usability and Phase 1/2/3 multiplayer acceptance are **Not Verified**. No regression was observed in executed tests; unexecuted client behavior is not asserted to be regression-free.

## Files changed since the starting checkpoint

Paths below are relative to the repository. `git diff --name-only 0bba9a32fb3ec5ce3a1cc8940a926f8e2f169547 HEAD` is the exact manifest, including documentation.

- Shared authority/routing: `src/main/java/dev/tacticalcombat/combat/{Encounter,Combat,CombatManager,CombatActions,CombatInteractions}.java`; `mixin/{ActiveItemMixin,BlazeVolleyMixin,GuardianBeamMixin}.java`; `dice/DiceService.java`.
- Live SRD: `combat/{SrdEncounter,SrdCommands,SrdNetworking,SrdTargeting,SrdMovementTurn}.java`; `net/{SrdRequestPayload,SrdStatePayload}.java`; `TacticalCombatMod.java`.
- Characters/rules: `character/{CharacterRepository,CharacterCommands}.java`; `rules/srd/{SrdCharacter,SrdCharacterCodec,SrdAuthoring,SrdRules}.java`.
- Client: `client/{ClientSrdCombat,SrdCombatScreen,SrdGridRenderer,TacticalCombatClient,ServerCharactersScreen}.java`.
- Pure tests: `src/test/java/dev/tacticalcombat/SrdAuthoringTests.java`.
- GameTests: `src/gametest/java/dev/tacticalcombat/combat/{SrdEncounterGameTests,SrdLiveGameTests,SrdTargetingGameTests,SrdRangedGameTests,SrdAcceptanceGameTests}.java`; development-only `character/SrdSaveFault.java`; `src/gametest/resources/fabric.mod.json`. Test-only fault/capability helpers are not release endpoints.
- Documentation: this report, the [multiplayer checklist](phase3-multiplayer-checklist.md), and a supersession notice in the historical partial checkpoint.

## Audit status and readiness

The original read-only audit remains historical evidence. Shared ownership/action/geometry/movement/networking findings F01–F07/F17 now have SRD-specific integration and regression coverage in the supported scope. F09 authoritative critical/result presentation is implemented but graphical acceptance is open. F14 gains a bounded SRD overlay, with real rendering unverified. F15 now has measured small-fixture costs and a tighter grid-query limit; load readiness is not established.

**F08 remains Partially Implemented overall:** approved server sheets now drive live SRD HP/movement/initiative/equipment, private synchronization and runtime revisions. Manual/local data is preserved; full character/class/monster modeling and real-client acceptance remain outside this checkpoint.

**F13 remains Partially Implemented overall:** validated atomic character/resource writes, backup recovery, reload and fixed-roll failure retry are tested. Full encounter/action-receipt durability, crash-time recovery and legacy local-save limitations are unresolved. No claim is made that a committed HP record restores an encounter.

Ready for **controlled local two-client acceptance** using the [checklist](phase3-multiplayer-checklist.md). The Phase 1 and Phase 2 real-client gates remain open. Do not enable automatic SRD world combat or begin unrestricted survival testing. Resolve client acceptance failures and the measured grid-query cost before broadening deployment; Phase 4 can build on the tested interfaces only with a separate scope decision. No Phase 4 implementation or remote publication is part of this checkpoint.
