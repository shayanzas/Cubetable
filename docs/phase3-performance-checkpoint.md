# Phase 3 publication and grid performance checkpoint

Date: October 10, 2026 (America/New_York).

**Phase 3 was published at its verified commit. The separate performance branch substantially reduces ordinary SRD grid costs, with all regressions passing. Extreme 300-foot, search-limit requests remain expensive.** Graphical and real multiplayer acceptance remain Not Verified.

## Repository and publication

- Repository: `shayanzas/Cubetable`.
- Published acceptance branch: [dev/phase3-srd-combat](https://github.com/shayanzas/Cubetable/tree/dev/phase3-srd-combat).
- Exact published SHA: **`21e13a4eb78ae50bb94c4ef2215ce0fc7d46bec3`**.
- Retained Phase 3 implementation: `a16743a9923a8429284debcd4f4125451a61ce45`.
- Performance branch: **`dev/phase3-grid-performance`**, created from the exact published SHA.
- Optimization commit: `49688bad1fd3b3b15bf0cd5413b5aa4f1e133f0a`.
- Expanded regression/benchmark commit: `eb5f81d7bdacb5b169c4d87ff2411aafe3093eff`.
- This report/results follow in a documentation-only commit. Use `git log -1 --format='%H %s'` for that final HEAD, avoiding a self-referential SHA.
- Final documentation commit leaves a clean worktree. **Optimization changes were not pushed.** No main/upstream changes, merges, resets, force-pushes or history rewrites occurred.

Before publication, the current branch, clean status, reported commits and origin URL (`https://github.com/shayanzas/Cubetable.git`) were checked. Neither requested development branch existed remotely. The unchanged Phase 3 build was rerun: **897 assertions, 72 GameTests, BUILD SUCCESSFUL in 26s**. Then the explicitly authorized `git push -u origin dev/phase3-srd-combat` succeeded. `git ls-remote` confirmed the exact SHA above; subsequent checks continued to show that SHA and no remote performance branch. Local `dev/phase3-srd-combat` also remains at that SHA.

The published branch is the fixed candidate for the [Phase 3 multiplayer checklist](phase3-multiplayer-checklist.md). This report does not replace its acceptance results.

## Original measurements and profiling

The historical [Phase 3 checkpoint](phase3-live-checkpoint.md) is unchanged:

| Operation | Historical median | Historical p95 |
|---|---:|---:|
| Attack preview | 0.159 ms | 0.234 ms |
| Reachable grid | 58.896 ms | 86.942 ms |
| Idle encounter tick | 0.084 ms | 0.156 ms |

The publication verification rerun measured grid median **52.939 ms**, p95/max **60.129 ms** in that older 55-node fixture. This confirms the problem independently; it is not the same fixture as the new closed arenas below.

An initial unoptimized profiling pass identified repeated occupancy lookups and SRD character decoding as the dominant cost. For example, one preliminary 30-foot search performed 7,081 occupancy queries and 3,452 character decodes; occupancy consumed about 82% of its instrumented wall time. Those exploratory arenas could extend into surrounding test floors. The final comparison fixtures therefore use isolated arenas 512 blocks away and 64 blocks above the shared test layout, bounded by walls. **Exploratory timings are not used as the final before/after comparison.**

In the final closed small30 fixture:

| Diagnostic operation | Frozen published geometry | Optimized request |
|---|---:|---:|
| Explored neighbor steps | 632 | 632 |
| Explicit block-state reads | 29,247 | 505 |
| Minecraft collision queries | 6,941 | 479 |
| World occupancy queries | 5,089 | 1 |
| Occupant SRD decodes | 3,452 | 1 |
| Cached logical occupancy comparisons | N/A | 338 |
| Request cache hits | N/A | 8,783 |

The separate instrumented reference pass spent 67.424 ms in occupancy, including decoding, out of 77.794 ms total; collision queries took 5.858 ms. The 16-participant fixture performed 37,807 reference decodes versus 15 optimized decodes; occupancy accounted for 626.337 of 647.279 ms in its diagnostic pass. Timed benchmark samples disable reference timing counters. Explicit block reads exclude reads internal to Minecraft collision/shape implementations, so the counter is not a census of all world access.

## Implementation

Production changes are limited to `SrdTerrain` and the search call in `SrdMovementTurn.preview`.

1. **One bounded occupancy capture per search.** Minecraft's bounded entity collector captures at most 257 eligible nearby living entities; more than 256 rejects the request explicitly. The capture area covers the maximum permitted steps, the extra explored neighbor at the budget boundary, vertical sweeps, physical actor size and the original four-block occupancy-query margin. It does not load chunks.
2. **Decode each captured SRD occupant once.** The snapshot retains both actual physical bounds and conservative logical occupied bounds. It preserves actor exclusion, live/spectator filtering, approved SRD size handling and the existing four-block conservative fallback for invalid SRD data. Other encounters' creatures still block; there is no new pass-through permission.
3. **Memoize identical work during that search.** Immutable block-position keys cache block states; exact boxes cache clearance results; cells cache support/landing results; directed edges cache neighbor validation. Identical orthogonal checks made while validating diagonal movement are reused.
4. **Discard everything after the synchronous request.** The cached adapter never escapes `measuredSearch`; only immutable preview data and numeric metrics return. Each map stops retaining additional entries at 32,768; misses still perform all checks. At most four such maps and 256 occupancy records are retained per call. These are entry caps, not a promise about exact heap bytes.
5. **Keep execution live.** The persistent turn controller still owns an uncached terrain adapter. Selecting a destination recomputes a fresh search; every executed square revalidates the entire remaining route, costs, support, current occupancy and authority. Same-tick block/entity changes between requests are observed without an event-based invalidation dependency.

World reads and mutations remain on the server thread; an explicit search-thread guard rejects worker-thread use before world access. No background job, snapshot-worker system, shared multi-tick cache or pending-job queue was introduced.

The Dijkstra algorithm, priority ordering, five-foot diagonals, weighted costs, landing-height selection and **4,096-node limit are unchanged**. Profiling identified expensive repeated geometry/decoding rather than evidence requiring a different search algorithm. Reusing directed-edge evaluation removes duplicate work without changing minimum-cost paths or boundary reporting. Collision shapes still come from Minecraft with the original actor context; checks were not approximated or skipped.

## Scheduling, identity and networking

The existing 12-requests-per-20-ticks limit, two-grid-requests-per-20-ticks limit, request replay guard, active-actor authorization and encounter/turn identities are preserved. Two embedded players in separate active SRD encounters independently receive their permitted grids; duplicate packets and repeated excess requests reject synchronously, with no later queued work or resource expenditure.

Cross-request coalescing was evaluated but not introduced: correctly reusing results would require reliable invalidation for arbitrary block geometry, other entities, dimensions, borders and binding changes. Request-scoped reuse solves the measured ordinary-case problem without introducing that stale-result contract. Existing client revision handling and grid wire format remain unchanged.

No production serialization change was needed. In the final run, grid-array JSON serialization medians were 0.032–0.464 ms; the largest observed p95/max was 1.815 ms in the uneven-terrain case. These timings measure the existing grid-array representation, not compression or socket delivery. Grid array sizes are in the CSV; full embedded handler replies were 4,515 and 4,401 UTF-8 bytes and completed in 2.435 and 1.939 ms respectively. Those are two server-handler observations, not network latency percentiles or real-client round trips.

## Reproducible benchmarks

Commands:

```sh
cd /workspace/Cubetable
/workspace/cubetable-setup/gradle.sh build runGametest
/workspace/cubetable-setup/gradle.sh build runGametest -PsrdBenchmark
```

Outside this cloud workspace, use `./gradlew` with a complete Java 21 JDK. `-PsrdBenchmark` adds repeated measurements; normal tests still run all correctness fixtures once.

Environment: Java HotSpot 21.0.12.1, Linux x86-64/KVM, five visible virtual CPUs on AMD EPYC 9V74 hardware, approximately 34,087 MiB visible RAM and no swap. Pinned dependencies are unchanged: Java release 21, Minecraft 1.21.1, Yarn 1.21.1+build.3, Gradle 8.10.2, Loom 1.8.13, Loader 0.16.9, Fabric API 0.116.1+1.21.1. This is a shared cloud environment, not controlled dedicated benchmark hardware.

The development-only `ReferenceSrdTerrain` preserves the published implementation with optional diagnostic counters. Neither it nor the benchmark/fault helpers is packaged in the release mod. Tests compare entire previews: reachable cells, exact heights, costs, parent choices, failed-edge reasons and truncation. The search algorithm is shared because it was not changed.

Each case uses deterministic arena geometry and explicitly bound characters, with one initial reference/current equality check, **three warm-up searches per implementation**, then **12 measured searches per implementation**, alternating their order. There were three successful paired runs (36 measured searches per implementation per case, plus warm-ups). Statistics below are from the final run, not pooled across JVM processes. With 12 samples, nearest-rank p95 is the maximum; both columns remain explicit. The CSV contains all three runs for comparison.

Wall time uses `System.nanoTime`. Server-thread CPU time and allocation use the HotSpot thread management bean. Setup/spawning, repository writes, equality assertions and serialization are outside the primary search timer. Allocation counts are allocated bytes during the call, **not live heap retained**, total process RSS or leak measurements. Output creation is inside the search timer.

Fixture configuration:

| Cases | Arena | Feet | Bound participants | Terrain/body |
|---|---|---|---:|---|
| small20 / small30 / small60 | 12×12 | 20 / 30 / 60 | 2 | Flat, Medium mover |
| medium30 / medium60 | 24×24 | 30 / 60 | 4 | Flat |
| large60 / large120 | 40×40 | 60 / 120 | 8 | Open interior |
| walls30 / walls60 | 24×24 | 30 / 60 | 4 | Walls, door-width openings, corners |
| stairs30 / stairs60 | 24×24 | 30 / 60 | 4 | Repeated stairs and bottom slabs |
| uneven60 | 24×24 | 60 | 4 | Repeated one-block rises |
| largeBody30 / largeBody60 | 24×24 | 30 / 60 | 4 | Large 2×2 mover |
| occupied8 / occupied16 | 24×24 | 30 / 60 | 8 / 16 | Mixed Medium/Large blockers |
| weighted29 | 24×24 | 29 | 4 | Soul-sand difficult terrain, nonmultiple allowance |
| limit300 | 80×80 | 300 | 2 | Centered mover, reaches node cap |

Ordinary arena movers begin near one corner, at the same location for both implementations; largeBody actors use footprint-centered coordinates. Standing columns are supported and cleared for every actor. Absolute GameTest world coordinates differ between JVM runs, so serialized byte counts can vary with coordinate string length. Each paired comparison has identical coordinates, geometry and state. The 2/4/8/16 cases benchmark bound participants as occupants; the separate concurrent test actually starts two SRD encounters.

## Before/after results

All durations below are milliseconds, shown as **median / p95 / maximum**. Ordinary rows did not truncate.

| Fixture | Nodes | Reference before | Optimized after | Median speedup |
|---|---:|---:|---:|---:|
| small20 | 49 | 43.247 / 87.651 / 87.651 | 1.482 / 1.795 / 1.795 | 29.2× |
| small30 | 79 | 66.520 / 72.729 / 72.729 | 1.917 / 2.087 / 2.087 | 34.7× |
| small60 | 143 | 101.870 / 111.505 / 111.505 | 3.379 / 4.056 / 4.056 | 30.1× |
| medium30 | 79 | 94.443 / 101.749 / 101.749 | 1.886 / 1.954 / 1.954 | 50.1× |
| medium60 | 219 | 251.432 / 253.850 / 253.850 | 5.225 / 6.133 / 6.133 | 48.1× |
| large60 | 197 | 460.468 / 483.386 / 483.386 | 4.777 / 5.132 / 5.132 | 96.4× |
| large120 | 700 | 639.277 / 649.774 / 649.774 | 17.414 / 18.015 / 18.015 | 36.7× |
| walls30 | 61 | 51.177 / 52.926 / 52.926 | 1.491 / 1.562 / 1.562 | 34.3× |
| walls60 | 157 | 162.694 / 165.400 / 165.400 | 3.616 / 5.475 / 5.475 | 45.0× |
| stairs30 | 79 | 96.026 / 98.524 / 98.524 | 2.244 / 2.413 / 2.413 | 42.8× |
| stairs60 | 219 | 250.642 / 253.924 / 253.924 | 5.999 / 6.262 / 6.262 | 41.8× |
| uneven60 | 219 | 211.932 / 215.725 / 215.725 | 5.672 / 7.590 / 7.590 | 37.4× |
| largeBody30 | 75 | 118.584 / 138.935 / 138.935 | 2.497 / 4.041 / 4.041 | 47.5× |
| largeBody60 | 208 | 264.518 / 267.051 / 267.051 | 6.234 / 6.418 / 6.418 | 42.4× |
| occupied8 | 73 | 166.081 / 171.230 / 171.230 | 1.843 / 2.106 / 2.106 | 90.1× |
| occupied16 | 171 | 644.502 / 658.905 / 658.905 | 4.314 / 5.895 / 5.895 | 149.4× |
| weighted29 | 53 | 49.573 / 50.562 / 50.562 | 1.339 / 1.505 / 1.505 | 37.0× |

Source data: [phase3-performance-results.csv](phase3-performance-results.csv).

The first two successful optimized runs had ordinary-case medians of 1.152–6.015 ms; the final run had 1.339–6.234 ms. Across all three runs, ordinary-case p95/max remained below 20 ms (the largest was 15.992 ms in the second run's medium30 case). **The ordinary supported fixtures met the <10 ms median and <20 ms p95 targets, without repeated >50 ms search spikes in measured samples.** This does not reserve the rest of the server tick or guarantee the same result on every machine.

The 120-foot case had medians of 11.995, 15.307 and 17.414 ms, with final p95/max 18.015 ms. It meets the p95 target but **does not meet the median target**.

### Extreme search and concurrent workload

The 300-foot, centered 80×80 case preserved the reference's exact 4,096 nodes and explicit truncation. The final optimized repeated run (three warm-ups, 12 samples) measured **121.997 ms median, 129.513 ms p95/max**, about 69.267 MB allocated per query and a 219,631-byte grid array. It retained 71,463 cache entries in total, performed 21,263 collision checks and 32,768 neighbor steps, with one occupancy query. Its ordinary-rate limit does **not** solve the single-request tick spike.

Two actual SRD encounters, four actors, were queried sequentially on the same server thread: after three warm-ups, 20 samples measured **3.119 ms median, 3.356 ms p95 and 3.606 ms maximum for both grids combined**. A prior run measured 3.020 / 3.182 / 3.426 ms. This is a small concurrent-encounter exercise, not a many-client stress guarantee.

### Allocation and other combat costs

| Fixture | Reference median allocation | Optimized median allocation |
|---|---:|---:|
| small30 | 113.970 MB | 1.707 MB |
| large120 | 1,115.368 MB | 14.660 MB |
| occupied16 | 1,187.460 MB | 3.935 MB |

MB here means decimal million bytes. Fewer repeated parses and geometry allocations substantially reduced allocation in these fixtures. Cache retention is bounded and request-local, but long-run heap/GC behavior with many connections was not independently profiled.

The retained Phase 3 100-sample preview/idle exercise also ran. Published verification measured attack preview 0.161/0.239 ms and idle tick 0.085/0.130 ms (median/p95). Optimized runs varied: preview medians 0.151–0.341 ms, idle medians 0.082–0.178 ms; the final run measured preview 0.275/0.489 ms and idle 0.151/0.213 ms. There is variation, including runs above the old median, so no universal “zero slowdown” claim is made. These are sub-millisecond observations, and attack resolution code was not changed. No substantial absolute tick-budget regression was observed, but a controlled long-running CPU/latency study would be needed to rule out smaller regressions.

## Correctness and full verification

Final command:

```sh
/workspace/cubetable-setup/gradle.sh build runGametest -PsrdBenchmark
```

**Compilation/build successful in 1m 39s. 897 pure assertions and 77 Minecraft GameTests passed; zero failures/errors/skips.** No existing test was removed, disabled or weakened. The normal build/GameTest command also passed at the first optimization checkpoint with 897 assertions and 73 tests, before the four additional stress tests were added. Final benchmark execution includes the complete expanded suite.

| Verification category | Result |
|---|---|
| Java client/server compilation and mod packaging | Passed |
| Foundation/repository/sync/persistence assertions | 320 + 39 + 58 + 6 passed |
| SRD rules/movement/authoring/resource assertions | 319 + 69 + 80 + 6 passed |
| Existing Minecraft integration tests | All 72 retained and passed |
| New grid performance/correctness GameTests | 5 passed |
| Repeated performance runs | Three paired 17-case runs passed |
| Embedded player protocol, concurrent grids and flood rejection | Passed; simulated advertised capability |
| Real graphical clients | Not Verified |
| Remote two-client multiplayer/latency | Not Verified |

The new tests cover:

- Complete old/new preview equivalence for all 17 fixtures, including cardinal/diagonal costs, elevation, stairs/slabs, corners, occupied cells, Large footprints and weighted 29-foot routes.
- Same-adapter, same-tick consecutive requests after adding/removing walls, moving an occupant, changing difficult terrain and removing support.
- Off-thread rejection before world access, unloaded chunks remaining unloaded, current world-border enforcement and fail-closed occupancy capture above 256 nearby creatures.
- Exact reference agreement at the 4,096-node cap, bounded retained entries and repeatable truncated results.
- Two active encounters and authenticated embedded players, isolated movement budgets/starts, replay denial, per-player limits, repeated flood rejection and no delayed work or resource expenditure.

The prior movement tests still execute live route interruption, changed occupants/terrain, full remaining-route validation, cancellation charges, unsupported modes, stale turns and authorization. The entire combat suite still covers initiative/ties, server dice/AC, typed damage, temporary HP/ammunition, failed-save transactions, ownership/isolation, zero HP, cleanup and disconnect. Tactical/manual profiles, character permissions/policies, packs, synchronization and persistence retain their previous regression coverage. Rendering, camera/fading, HUD, dice animation, cards and inventory retain existing automated/static coverage; real graphical behavior is not newly verified.

An early benchmark fixture used uppercase size strings, which the codec correctly rejected. The fixture was corrected to the existing lowercase schema; no production validation was relaxed. All final runs pass. Existing Authlib public-key DNS, deprecated mock-player helper and Gradle warnings remain; online authentication is not verified by these tests.

Logs retained in this workspace: `/tmp/phase3-publish-verification.log`, `/tmp/srd-grid-before.log`, `/tmp/srd-grid-optimized-check.log`, `/tmp/srd-grid-after-1.log`, `/tmp/srd-grid-after-2.log`, `/tmp/srd-grid-final.log`. XML results: `build/gametest-results.xml`. Durable summary statistics are committed in this report and the CSV.

Optimized artifact: `build/libs/tactical-combat-0.1.0.jar`.

```text
SHA-256 ba0cf8e1386787acd43c04f6c36aa628bb5fe4867b8549c8013d1f0a7463917c
```

This artifact contains the local performance changes; it is distinct from the published Phase 3 candidate artifact.

## Files changed

- `src/main/java/dev/tacticalcombat/grid/SrdTerrain.java`: request-local occupancy capture, bounded memoization, numeric metrics and thread guard; live execution adapter retained.
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`: preview uses the optimized synchronous search.
- `src/gametest/java/dev/tacticalcombat/grid/ReferenceSrdTerrain.java`: frozen development-only reference and diagnostic counters.
- `src/gametest/java/dev/tacticalcombat/combat/SrdGridPerformanceGameTests.java`: paired measurements, equivalence and stress tests.
- `src/gametest/java/dev/tacticalcombat/combat/SrdEncounterGameTests.java`: fixture overload allowing isolated benchmark coordinates; existing tests unchanged.
- `src/gametest/resources/fabric.mod.json`: register the new tests.
- `build.gradle`: optional benchmark flag for the existing headless run; dependency versions unchanged.
- This report and `docs/phase3-performance-results.csv`.

## Readiness and recommendations

**Implemented:** verified Phase 3 publication, separate local branch, measured request-local optimization, reproducible comparisons and expanded regression coverage. **Partially Implemented:** performance acceptance across the entire supported speed range; ordinary fixtures meet targets, 120-foot median and extreme 300-foot requests do not. **Not Verified:** graphical/remote multiplayer acceptance and production-scale latency/heap behavior. **Deferred:** asynchronous/budgeted large searches and all Phase 4 features.

The optimized branch is ready for separate, controlled real-client testing. Keep the already published acceptance branch fixed while its independent testing proceeds. Use the existing Phase 3 checklist and additionally compare grid responsiveness, terrain changes immediately after preview, occupied cells, cancellations, rate-limit feedback and simultaneous encounters.

A Phase 3 stabilization follow-up is still needed before broad deployment: address 300-foot/near-cap searches with a server-thread work budget or another correctness-preserving design, then measure high-load behavior and full packet/renderer latency. Until that is addressed, conduct performance acceptance in bounded 20–60-foot scenarios, and explicitly treat high-speed open arenas as a known stall risk. No runtime speed/node cap was silently changed to improve numbers.

The Phase 1, Phase 2 and Phase 3 real-client acceptance gates remain open. Phase 4 can be planned after user acceptance of these checkpoints and remaining limitations; **no Phase 4 mechanics are implemented by this work**. Publishing the performance branch requires further authorization.

## October 10 stabilization continuation — checkpoint A

The evidence above remains the historical cba6b37 checkpoint. This continuation starts at `cba6b37134f2a37473c9e5abf9ef6d51038245e6`, on the same local performance branch. No remote branch is changed. The reproduced baseline passed 897 assertions and 77 GameTests; its flat 300-foot search measured 115.024 ms median / 127.601 ms maximum, allocating 78.961 MB per request.

High-speed network grid requests (remaining movement above 60 feet) now use a cooperative server-thread scheduler. It retains at most eight jobs, one per requester, rotates jobs after a 1 ms slice, and stops after an 8 ms aggregate work deadline. A slice also caps neighbor evaluations at 256. These are cooperative deadlines: an individual Minecraft query, validation, final preview construction/serialization, OS scheduling or GC can overrun them. Requests expire after 400 world ticks; overload returns an explicit retry message. Original request/replay and grid rate limits remain enforced. Ordinary <=60-foot requests retain the existing fast synchronous path; simultaneous ordinary requests are not covered by the aggregate high-speed scheduler budget.

`SrdMovement.Search` retains the original weighted Dijkstra ordering, parent choices, boundaries and 4,096-node cap across yields. A frozen development-only search oracle now complements the frozen geometry oracle. Terrain memoization lasts one slice, not the whole job. Pending jobs cancel on region block mutations (observed at chunk mutation, including suppressed neighbor notifications), changed occupancy/actor bounds, loaded chunk identities, border, encounter revision/turn, permission or character state. Disconnect, explicit cancellation and server stop release pending jobs; replacement requests supersede the old job. No world access runs off-thread. Unusual modded dynamic block-entity collision changes without block mutations are not exhaustively modeled; real movement always rechecks current geometry and occupancy.

The high-speed Move operation consumes only a completed server-owned preview for the current anchor/allowance; it no longer starts another synchronous full-grid calculation. Every executed step still validates the entire remaining route and its current costs before moving or charging. A completed preview is advisory and never permits a stale wall/occupancy bypass. Additive pending/cancelled status messages preserve the existing v1 payload layout and full-state refresh. The client clears old grids while a replacement is pending.

Paired synchronous/incremental measurements use identical fixtures, three warm-ups and 12 measured runs. 30/60/120-foot cases use weighted terrain with four occupants; the 300-foot case is the centered flat 80×80 arena with two occupants and explicit truncation. Raw output is retained in `phase3-stabilization-results.csv` alongside the original baseline. Medians below average the middle two samples; p95 with 12 samples equals maximum.

| Feet | Synchronous median / max ms | Incremental total median / max ms | Largest measured work slice ms | Modeled 20-TPS completion median / max ms |
|---|---|---|---|---|
| 30 | 1.501 / 1.598 | 1.885 / 2.417 | 2.362 | 1 / 2 |
| 60 | 3.792 / 4.011 | 5.492 / 5.882 | 5.817 | 5 / 5 |
| 120 | 11.958 / 12.571 | 20.080 / 20.984 | 8.013 | 103 / 104 |
| 300 | 98.516 / 109.233 | 127.945 / 133.566 | 9.764 | 755.5 / 803 |

These slice measurements include occupancy/chunk capture and geometry/search work, but exclude production gateway validation, packet encoding, other server work and socket delivery. Completion latency is a **modeled schedule** (50 ms between simulated work ticks), not a measured client round trip or actual paced server session. The 300-foot change trades lower blocking time for more total work and allocation: roughly 92.928 MB allocated per incremental request. Retained caches are bounded per slice; this is not a retained-heap or leak measurement. The synchronous ordinary path remains faster and is deliberately retained.

Verification: benchmark build passed 909 assertions and 80 GameTests, including the original 77. New tests cover weighted results across different yield boundaries, complete frozen-reference agreement, block/occupancy invalidation, bounded pending capacity, supersession, completed request identity, turn/end/cancel cleanup. Graphical, actual paced high-load and remote two-client performance remain Not Verified. The existing Phase 1–3 client acceptance gates remain open. Checkpoints B–D follow only after this checkpoint's regression gate passes.
