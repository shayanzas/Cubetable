# J2.5K-A — Movement pathfinding and presentation correction

Base: `e5f76e96e1eacd8f611825b81d3e737a1ff562b7` (J2.5J).
Branch: `dev/gameplay-srd-movement-correction-j25k-a`. Exact remote delivery SHA is in the final response and branch history.

## Findings and corrections

1. The old Dijkstra relaxation retained the first equal-cost parent, chosen through coordinate ordering. With equal five-foot cardinal/diagonal charges, this could produce diagonal excursions on a straight cardinal trip. Authoritative feet remain the primary key. Equal-cost candidates now prefer shorter geometric length (1000 cardinal / 1414 diagonal scoring units), then fewer turns, then the existing deterministic discovery order. This is a bounded parent preference, not a new globally optimal heading-state search. Clear cardinal/diagonal routes are direct; clear mixed routes have at most one turn in the tested arena. Search/cost caps and cooperative edge scheduling remain intact.
2. `WalkAnimation` only runs for legacy Tactical. SRD player movement uses server teleports, which do not reliably drive vanilla local-player limb animation/interpolation. Merely enabling that helper would still leave discrete square-to-square position jumps. The new SRD render adapter instead consumes already committed server steps, applies matrix-only translation and native model/feature walking parameters, and faces along each segment. Legacy Tactical's helper remains untouched.
3. Server grid boundaries were discarded during serialization and reconstructed as an empty list on the client. Up to 256 bounded approach-edge reasons now accompany the authorized grid, with explicit truncation and packet headroom. They describe rejected approaches, not proof that every possible route is blocked. Server movement rejection also includes approach reasons. A rejected movement clears the stale local grid and schedules a fresh read-only discovery, never a movement retry.
4. Displayed actor picking/labels/markers and camera follow now use the same visual position as the rendered actor. Actor selection still sends only actor identity; server attack geometry continues to use real positions. Terrain picking continues to intersect only server-provided cell tops and respect opaque collision rays, including faded walls.

Flat-grass, decorative short-grass, eight-neighbor alignment, occupied cells, corners, walls and one-block elevation were exercised on the real server. No general clear-grass collision defect was reproduced, so collision/occupancy restrictions were not relaxed to make questionable destinations appear available. Unavailable examples unique to the user's arena still need a new client test with the improved reasons.

## Authority, costs and traversal

Normal squares still cost 5 feet; difficult terrain 10; crawling 10; difficult crawling 15. Dash, standing, split movement and all action budgets remain unchanged. No free two-block ascent, climbing, jumping, reactions or new rules are introduced.

Each server tick still commits a complete validated square, charges its actual current cost and revalidates the entire remaining route. A per-turn monotonically increasing step sequence publishes the most recently completed segment and its captured cadence. Route acceptance alone publishes no uncommitted animation. This is additive SRD v1 presentation metadata, not a client movement command or persistence schema change. Both players and NPCs use the same contract. On turn change the sequence resets within a new encounter/turn scope.

## Render-only movement

`SrdMotionTrack` queues at most eight adjacent confirmed segments. Fast/Brisk/Walk/Slow retain 1/2/4/6 ticks per square (50/100/200/300 ms at the normal server tick rate). Every segment follows its own accepted corner; early arrivals queue instead of cutting across corners. Flat traversal stays at constant height. Elevation uses the server sweep's lift/across/lower ordering, rather than interpolating through a raised block. No jump bob is added.

`SrdEntityRenderMixin` translates the world-render matrix only, restoring it in `finally`. `SrdLivingRenderMixin` supplies phase, stride and facing to Minecraft's model/feature rendering for the local player, other players and NPCs. It does not set entity position, bounding box, velocity, gameplay yaw or send movement packets. Camera follow, ground indicators and entity picking share the presented position. Limb movement stops at the last committed point. Existing attack animation remains driven by the native hand-swing fields.

Refreshes do not replay a sequence. Joining/reconnecting initializes at the current position without reconstructing historical steps. Missing/nonadjacent segments or queue overflow reconcile to the latest committed endpoint instead of inventing a path. Encounter/turn changes, disconnect and invalid/paused state clear transient presentation. A cancelled route finishes presentation only for already committed steps; unexecuted steps are never shown. Server attacks, collision, terrain and costs always use authoritative positions.

Matching client/server builds are needed for the new step presentation. Older clients ignore additive metadata; newer clients do not fabricate step history if an old server omits it.

## Preview information

The read-only movement details adapter exposes destination, authoritative path/cost/remainder, approach reasons, snapshot revision, stale flag and route status. Invalidated grids provide no old path authority. Existing explicit confirmation and UI remain; no cursor-adjacent popup or pacing-menu redesign was added. Same-snapshot valid grid refresh preserves selection; changed authoritative state invalidates it.

## Validation

Final full validation: **BUILD SUCCESSFUL in 1 minute 21 seconds**.

- 39,959 foundation assertions across 45 suites (J: 38,979 / 44).
- 172/172 GameTests, zero failures, errors or skipped tests (J: 169).
- 2,748 catalog checks + 15,115 coverage checks = 17,863.
- Compile and targeted foundation validation passed. Minecraft 1.21.1 renderer method descriptors and generated remapping entries for both new client mixins were inspected; graphical execution remains unverified.

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `5474966cf11a6e8a348faadfd5ec89f8a3c459ab93d3009d0df0ed639daa4bbb`.
Evidence: `build/j25ka-validation.log`, `build/j25ka-targeted.log`, `build/gametest-results.xml`. Existing test API/Gradle deprecation warnings and the optional authentication public-key DNS warning are not test failures.

Command (Java 21): `bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage`.

The new foundation suite covers direct routes in all eight directions, mixed-route turns/costs, deterministic incremental search, obstacle/corner detours, negative/elevated cell picking, all pacing modes, diagonal flatness, turn continuity, idle stopping, sequence replay/gaps, queue overflow, ascent/descent sweep presentation and stale details. New GameTests exercise grass/decoration alignment, supported/rejected elevations, occupied/corner rejection, bounded boundary serialization, NPC timing/cost/cancellation at every pace and local-player committed-step publication. Existing Dash/Dodge/Prone, permission revocation, transactions, character, dice, Tactical/Generic D20 and catalog tests remain enabled.

The first full run found four frozen-oracle failures because they required the deliberately replaced coordinate-biased parent paths. The frozen search itself is retained unchanged. An independent second-pass oracle now takes its exact reachable cells, costs, boundaries and truncation, then enumerates legal minimum-cost parent candidates and scores their full paths. Existing full-preview equality assertions remain, now comparing the required new parent preference as well as all frozen geometry/cost data. Production performs its preference during relaxation and uses accumulated metadata; the oracle does not call that implementation. No test was deleted or disabled.

## Limitations and acceptance

**J2.5K-A GRAPHICAL ACCEPTANCE: NOT VERIFIED.** No graphical client/display is available. Compilation and deterministic animation tests do not establish visual smoothness, native client rendering-hook behavior, skin/armor appearance, GUI-scale acceptance or mouse-input quality. Actual walking, facing and camera feel must be judged in a real client.

The server still moves discretely; the renderer intentionally trails committed movement by up to a normal segment plus bounded network backlog. High latency, dropped step history or overload can cause safe reconciliation snaps. Render-only movement cannot provide atomicity with client world/block updates or eliminate every network hitch. Body collision dimensions are unchanged. The tie preference reduces unnecessary turns but does not add heading-expanded global optimization.

J2.5J user acceptance is retained as reported: movement costs, crawling/standing, Dash grants, rejected attacks preserving action, and encounter-end HP persistence passed. Dodge effectiveness remains inconclusive and GUI-scale testing was not performed; this checkpoint does not upgrade either result to a pass.

Use matching JARs in a separate test world:

1. Start the ree_bunger/Husk encounter in the same grass arena. Select distant cardinal, diagonal and mixed destinations; inspect the exact route before confirming.
2. Test adjacent walls, occupied squares, decorative grass, diagonal corners and uneven terrain. Verify one-block ascent/descent and rejection of free two-block ascent.
3. At Fast/Brisk/Walk/Slow, watch local-player, remote-player and Husk legs, facing, diagonal travel, stopping and camera follow. Look for teleport/jump artifacts on flat ground.
4. Move 5 feet twice, Dash, crawl and stand; verify the previously accepted movement costs remain correct.
5. Cancel a route and obstruct an unexecuted step. Verify no uncommitted visual travel, resource refund or automatic move retry. Refresh an unchanged grid and confirm selection accuracy.
6. Compare rendered actor selection with the server attack preview; confirm walls and real authoritative range still govern targeting.
7. Change turns/controllers, reopen DM tools, end combat and reconnect. Verify no stale walking or replayed route, and recheck HP persistence. Dodge visual effectiveness and GUI scales still need their own acceptance checks.

## Changed files

- `docs/gameplay-srd-movement-correction-checkpoint-j25k-a.md`
- `src/gametest/java/dev/tacticalcombat/combat/SrdGridPerformanceGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdTraversalGameTests.java`
- `src/gametest/java/dev/tacticalcombat/grid/ReferenceSrdSearch.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/SrdCamera.java`
- `src/main/java/dev/tacticalcombat/client/SrdGridRenderer.java`
- `src/main/java/dev/tacticalcombat/client/SrdMotion.java`
- `src/main/java/dev/tacticalcombat/client/SrdMotionTrack.java`
- `src/main/java/dev/tacticalcombat/client/SrdMovementPreview.java`
- `src/main/java/dev/tacticalcombat/client/SrdWorldPicker.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdGridJobs.java`
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`
- `src/main/java/dev/tacticalcombat/mixin/client/SrdEntityRenderMixin.java`
- `src/main/java/dev/tacticalcombat/mixin/client/SrdLivingRenderMixin.java`
- `src/main/java/dev/tacticalcombat/rules/srd/SrdMovement.java`
- `src/main/resources/tacticalcombat.client.mixins.json`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/SrdTraversalTests.java`
