# Phase 3 Stage B — isolated SRD movement checkpoint

October 9, 2026 (UTC). Repository `shayanzas/Cubetable`; branch `dev/phase3-srd-combat`.

## Checkpoint and verification

Continued the existing clean workspace at `0d4a29b5bf2f5e025f15ccd8ff03f8196875efb6`, retaining Stage A implementation `0d5b1f66eaf6b9b1712d301e6c57470b9f7dc328` and Phase 2 base `6b28e42caeb447ba3578cac60c0d0b58e6e4e5a0`. Read the Stage A checkpoint, approved movement specification, audit F05/F06 and SRD 5.1 movement/creature-space rules (pp. 91–92) before implementing. Baseline rerun passed **742 assertions and 38 GameTests**.

Local implementation commit:

```text
2119d1076dfa9b728f014929fe3c026dad099ac4
Add isolated SRD movement engine, authoritative step execution and preview model
```

This report and the Stage A report link are in a following documentation commit. Use `git log -2 --oneline` for its identifier. **Nothing in Phase 3 has been pushed.** No reclone, reset, dependency update, remote mutation or Phase 2 branch change occurred.

Final command:

```sh
/workspace/cubetable-setup/gradle.sh build runGametest
```

**BUILD SUCCESSFUL in 30s**. Compilation, packaging and the headless server completed. Java 21, Minecraft 1.21.1, Gradle/Fabric and all pinned dependencies are unchanged.

| Suite | Passed |
|---|---:|
| Existing foundation | 320 assertions |
| Existing repository | 39 assertions |
| Existing synchronization | 58 assertions |
| Existing persistence interruptions | 6 assertions |
| Stage A SRD calculations | 319 assertions |
| New Stage B movement/presentation | 69 assertions |
| **Total** | **811 assertions** |
| Existing Minecraft GameTests | 38 |
| New Stage B Minecraft GameTests | 10 |
| **Total** | **48 GameTests** |

Zero failed, errored or skipped GameTests; `build/gametest-results.xml` contains 48 testcase entries. No previous tests were removed or disabled. Pure assertions are not individual JUnit test methods. Final log: `/tmp/stage-b-verified.log` (baseline: `/tmp/stage-b-baseline.log`). Existing deprecated test-helper/Gradle warnings and Authlib public-key DNS warning remain; remote authentication is not verified by the headless server.

Artifact: `build/libs/tactical-combat-0.1.0.jar`.

```text
SHA-256 73f9488c0f977219d5981a49c442b53382aa997a1ba4d0bd194cff90dc808b87
```

## Status and scope

**Stage B's isolated movement engine, world geometry, server turn controller and preview data model are implemented and tested. Live encounter/network/rendering integration remains deliberately deferred.** `SrdRulesProfile.worldCombat()` remains false. No command or packet creates an SRD movement controller, and no unfinished SRD encounter was enabled to exercise the tests.

| Requirement | Status / evidence |
|---|---|
| Preserve Tactical/manual systems | Implemented isolation; no existing movement, rendering, packets, packs or character storage logic changed; prior tests pass |
| Approved SRD speed and armor penalty | Implemented using the Stage A server snapshot, never `movement_blocks` or local formulas |
| Five-foot scale, eight directions, ordinary ascent/descent | Implemented, including weighted pathfinding and real-world traversal tests |
| Dimensions, logical footprints and occupancy | Implemented conservatively; squeezing and permissive creature passage remain unsupported |
| Request/turn authority and route execution | Implemented internal server controller with replay/identity/permission checks; future encounter ownership and packet routing still required |
| Changed terrain, occupants, support and interrupted charges | Implemented; whole remaining route revalidated before every completed step |
| Hazards/difficult terrain | Partially Implemented: explicit walking-only exclusion reasons and two difficult-terrain mappings; no environmental damage |
| Grid/preview preparation | Implemented pure search and separate client presentation model; renderer/transport integration and graphical verification Deferred/Not Verified |
| Stage A calculations/Phase 1–2 regression tests | Passing unchanged |
| Real two-client acceptance | Not Verified; Phase 1 and Phase 2 gates stay open |
| Stage C/attacks, spells, classes, reactions, conditions, death saves, encounter recovery | Deferred; not implemented here |

## Files changed

- `src/main/java/dev/tacticalcombat/rules/srd/SrdMovement.java` — immutable grid/search/preview records and bounded weighted eight-neighbor search.
- `src/main/java/dev/tacticalcombat/grid/SrdTerrain.java` — server-world geometry, support, environment, logical footprints and current occupancy.
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java` — internal authenticated-principal movement workflow, turn context checks, request deduplication, cancellation and atomic steps.
- `src/main/java/dev/tacticalcombat/client/SrdMovementPreview.java` — isolated presentation selection model; no renderer or networking hooks.
- `src/test/java/dev/tacticalcombat/SrdMovementTests.java` — 69 deterministic assertions, including preview validation and rendering-model data.
- `src/test/java/dev/tacticalcombat/FoundationTests.java` — invokes the new suite.
- `src/gametest/java/dev/tacticalcombat/combat/SrdMovementGameTests.java` — ten Minecraft integration tests.
- `src/gametest/resources/fabric.mod.json` — registers those tests.
- `docs/phase3-stage-b-checkpoint.md` — this report.
- `docs/phase3-checkpoint.md` — link to this newer movement checkpoint; historical Stage A results preserved.

## Movement calculations and pathfinding

One block is five feet. All eight horizontal neighbors cost **five feet**, including diagonals and ordinary one-block rises/descents. Neither Euclidean distance nor alternating diagonal costs are used. Feet remain the ledger unit: 20/25/30/40 feet permit 4/5/6/8 normal steps. A 29-foot budget permits five steps with four feet left; four feet cannot buy a sixth step. Zero-speed actors can occupy their origin but cannot request a path.

The turn controller reads `CharacterService.srdStats`, including `walkingSpeedFeet()` and the supported heavy-armor Strength penalty. It captures the immutable approved binding, character identity and revisions. It never uses Tactical's legacy `movement_blocks` value.

`SrdMovement.search` uses Dijkstra search because some steps cost ten feet. It produces reachable cells with cost/parent, explicit failed-edge reasons, the remaining-foot budget and a truncation indicator. Search is capped at **4,096 nodes**, with at most eight boundary entries per processed node; allowance is bounded to the existing 300-foot input maximum. Some high-speed/open-area searches can truncate. Such results explicitly report `SEARCH_LIMIT`, not false proof that unseen destinations are blocked. Already discovered routes remain validated; search order is deterministic.

Grid cells retain exact landing height rather than truncating slab height to an integer. Coordinates are bounded to ±30,000,000 horizontally and ±4,096 vertically. The origin must already be centered on its footprint and supported. This checkpoint does not silently teleport arbitrary exploration positions onto a grid; the future encounter entry operation must validate initial positioning.

## Creature space and world collision

Logical footprints use one cell for Small/Medium, two-by-two for Large, three-by-three for Huge and four-by-four for Gargantuan. **Tiny is conservatively reserved one whole cell** pending half-cell positioning. Gargantuan sizes above the minimum 20-foot square require a future explicit footprint field. These limitations must not be represented as full SRD size support.

The cell coordinate identifies the minimum X/Z corner; center is anchor plus half the footprint width. Physical width/depth can enlarge the tested space beyond the logical footprint, and current physical entity height determines headroom. Terrain validation conservatively requires both logical clearance and physical clearance; it does not treat controlled space as permission for a smaller hitbox to slip through a blocked corridor. Every intersected footprint column requires physical support at the landing height. Large creatures can fail on uneven floors/stairs where a smaller actor fits; rejection is intentional rather than clipping or unsupported floating.

Walking candidates are collision-shape surfaces within one vertical block. Slabs and stairs are supported. Each route edge validates a lifted/horizontal/lowered swept volume; the diagonal swept box is deliberately conservative. Both orthogonal corner passages must also be valid. No wall-edge clipping or diagonal corner cutting is allowed. Fences/walls are not supported walking surfaces. Entire bodies and swept volumes must fit the world border and loaded chunks; validation does not request chunk loads.

Occupancy uses current living-entity positions, excluding the actor, dead entities and spectators. Approved SRD occupants contribute logical size in addition to their actual dimensions. Invalid SRD occupant data reserves a conservative four-block width rather than allowing clipping. All such creatures currently block traversal and destination occupation, regardless of allegiance. **Nonhostile passage, two-size-difference hostile passage, Tiny sharing and squeezing are not implemented** (SRD p. 92 permits some of these). The future encounter must supply authoritative relationships and passage policy before those cases can be enabled. The present conservative behavior is not claimed as complete SRD creature-passage compliance.

## Terrain policy

- Ordinary supported solid floors, slabs and stairs: five-foot destination steps.
- **Soul sand and honey blocks:** provisional Minecraft-to-SRD difficult-terrain mapping, ten feet to enter a destination touching those floor types; leaving into normal terrain costs five. Multiple difficult sources do not stack. This is a documented Cubetable mapping, not terrain named by the SRD. SRD p. 91 supplies the extra-foot-per-foot rule.
- Water/lava, fire, magma, cactus, berry bushes, cobwebs, powder snow, wither roses and campfire-tagged blocks: `UNSUPPORTED_ENVIRONMENT`, not “solid wall.” These require swimming, hazards, conditions or consequence handling that this stage does not implement. Campfire classification is currently conservative even when unlit.
- Missing support, obstructed headroom/collision, occupied space, corners, world-border failures and unloaded chunks have separate reason codes.

Hazard rejection is a **temporary walking-engine safety gate**, not an SRD ruling that hazardous terrain is universally impassable. Before live SRD activation, implement the appropriate cost/consequence adapters or obtain an explicit campaign policy for unsupported terrain. No incomplete environmental damage was added. Climbing, swimming, flight/gliding, jumping, forced movement and falling remain Phase 4 scope; the turn controller rejects swimming, gliding, riding and active player flight.

## Authority, interruption and costs

`SrdMovementTurn` is a trusted server-side integration component, not a new encounter manager. Its owner must supply a live `Frame` from the authoritative encounter and retain **one controller per actor/turn**, rather than constructing a fresh budget on each request. There is deliberately no packet/command constructor. Future integration must also retain the existing encounter ownership index and inactive-participant freeze policy.

Each request supplies only encounter/campaign/profile/turn/actor/request identity and a destination—not a client path or movement total. The principal must be derived from the authenticated server connection. A player may control their own approved bound actor; campaign managers may control actors in their campaign. Owner membership, character binding, profile, campaign selection and snapshot revisions are checked again. Creation and public operations require the authoritative server thread. Changed dimension, actor removal/death/spectator state, changed context, lost binding or changed character data stop movement.

The Phase 1 `TurnRequests` guard supplies bounded deduplication (128 identities). External turn identity is checked before accepting into the controller's scoped guard. Rejected geometry consumes request identity but **no movement**. New identities cannot refund spent movement. While a route is pending, another route is refused until cancellation/completion.

Execution is deliberately **whole-square atomic**, one step per controller tick:

1. Recheck actor/context/permissions and that the actor remains at the last authoritative anchor.
2. Validate the anchor, then the entire remaining route against current world support, collision, occupancy, heights and costs.
3. If any step is invalid or total remaining cost exceeds the allowance, cancel without moving or charging pending steps.
4. Move to the next validated landing, preserve player look, stop navigation/velocity, then charge that step once.

There is **no authoritative partial square or interpolation ledger** in this checkpoint. Cancellation before a step costs zero; cancellation after two ordinary steps retains ten feet spent. A future visual tween must be cosmetic and must not change these charges. Unauthorized fractional/vertical displacement cancels the route and restores the last safe supported anchor without refunding completed steps. If the anchor itself loses support/clearance, the controller suspends instead of teleporting into invalid terrain; recovery requires future encounter/DM handling, not fabricated falling rules.

When the active turn changes, this controller closes and stops issuing movement. The future encounter owner must manage freezing/handoff for all participants; this isolated component is not a globally installed exploration/multiplayer movement hook. SRD world combat remains disabled, so this boundary cannot expose an unfinished live encounter path.

## Preview preparation and privacy

`SrdMovement.Preview` is immutable and bounded, validates its parent chains against cycles, and carries costs/paths and failed-edge reasons. `SrdMovementPreview` accepts only `srd_5_1` data. Selecting a reachable destination returns path, feet cost and remaining allowance; other selections distinguish missing data, unreachable/search-limited state and failed approach reasons such as blocked or out-of-budget. Failed approaches are not mislabeled as proof that every possible approach is blocked.

This is **supporting client data integration only**. No live SRD grid packet, rendering hook, hover widget or attack UI was added. Existing `ClientGrid`, `GridRenderer`, Tactical packets and HUD remain untouched. Server requests always recompute a path; old preview data cannot authorize a stale route.

Although preview records contain no private character stats or entity IDs, occupancy geometry can itself reveal hidden creatures. A future packet sender must apply campaign/viewer and visibility policy before transmitting previews. Do not broadcast these internal records unfiltered. No new preview traffic is sent in this checkpoint.

## Test coverage and remaining acceptance

The 69 pure assertions check feet conversion, all eight neighbors, diagonal and vertical grid cost, weighted shortest routes, zero/nonmultiple allowance, deterministic independent searches, search limits, malformed coordinates/edges, preview selection/clear/profile isolation and cyclic-parent rejection.

The ten new GameTests exercise:

1. Eight directions, a real six-square route and unchanged Tactical four-neighbor grid.
2. One-block ascent/descent, slab and stair surfaces, and source headroom.
3. Diagonal corner blocking, walls, fences and missing support.
4. Large logical footprint, far-edge support, tall physical entity and oversized physical width.
5. A moving occupant invalidating an accepted route and logical occupancy beyond its hitbox.
6. Completed-step charges, a late wall, cancellation/replay, fractional displacement and removed intermediate floor.
7. Actor/encounter/campaign/profile/turn identity, unauthorized requester, duplicate request and active-turn change.
8. Four-foot/zero-speed budgets, approved heavy-armor penalty and passable difficult soul sand.
9. An authenticated ordinary **mock** server player, actual server teleport/look preservation, rejected flight, lost binding and campaign selection mismatch.
10. Explicit unsupported-environment reasons, borders and unloaded chunks without loading them; ruleset gates remain closed.

These are actual headless world/character-adapter tests, not evidence of graphical or real two-client acceptance. No graphical clients were launched. The [Phase 1 checklist](phase1-multiplayer-checklist.md) and [Phase 2 checklist](phase2-multiplayer-checklist.md) remain open. No regressions were observed in the executed coverage; performance under large multiplayer loads, renderer compatibility, latency and cosmetic movement remain Not Verified. Search bounds prevent unbounded work but do not constitute a performance benchmark.

For local testing, retain the pinned Java 21/Minecraft/Fabric setup, use a disposable copied world and run the existing Tactical/manual and Phase 2 acceptance checklists. Verify Tactical still has four-direction movement and original allowances; manual D20/Percentile packs, dice/cards, camera/HUD/inventory and character approval/refresh behavior must remain intact. There is no live SRD movement mode to toggle yet. Reproduce Stage B geometry through `./gradlew build runGametest`, not by enabling the incomplete SRD profile.

## Resume and remaining Phase 3 work

```sh
cd /workspace/Cubetable
git status --short --branch
git log -3 --oneline
/workspace/cubetable-setup/gradle.sh build runGametest
```

Outside this environment, use the checked-in `./gradlew build runGametest` with a complete Java 21 JDK. Expected checkpoint baseline: **811 assertions / 48 GameTests**.

Next Stage C work must connect the retained movement controllers to encounter lifecycle and privacy-filtered previews, then implement explicit attack preview/confirmation and accepted-action resolution, server RNG, persistent D&D HP and permitted result synchronization. Stage D still needs world range/reach, cover/unseen targeting, equipment resource handling and broader multiplayer integration. Resolve conservative movement policies and controlled zero-HP behavior before enabling SRD world combat. Complete Stage E and the real-client acceptance gates before release readiness. Do not begin Phase 4 or push Phase 3 without further authorization.

SRD reference/attribution remains in [the Stage A report](phase3-checkpoint.md#reference-and-attribution) and the packaged `SRD-5.1-NOTICE.txt`. This checkpoint adapts selected 2014 SRD movement concepts with the explicitly approved block/diagonal convention and the limitations above; it does not claim full SRD compliance.
