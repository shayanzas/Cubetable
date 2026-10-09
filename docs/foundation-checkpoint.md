# Combat authority foundation checkpoint

Historical checkpoint: see the [Phase 1 continuation report](phase1-checkpoint.md) for current behavior and verification.

Date: 2026-10-08. Branch: `dev/combat-authority-foundation`.

## Scope and baseline

This is the first implementation checkpoint for the [user specification](implementation-spec.md), following the [SRD 5.1 audit](SRD-5.1-audit.md). The starting commit was `70c6035af152f8a105b94d77a0d6645e1fa6293a`; its only change from audited revision `d712cc0c23545ae54062d505debf415feb9fe490` was the audit document. The implementation remains local. No implementation push or PR was performed.

**Phase 0: Implemented for compilation and automated server testing; graphical/multiplayer validation is Not Verified. Phase 1: Partially Implemented.** The supported regression cases pass, but special attack adapters, complete interaction accounting, and real two-client validation remain before the phase can close. Phases 2–8 are Deferred. No automated SRD combat is claimed.

## Changes and behavior

- A server-thread UUID ownership index gives each entity at most one encounter. Encounter creation skips already-owned entities and rejects empty results. Removal/end releases only matching ownership. Encounters are capped at 128 participants.
- Encounters have UUIDs and monotonically advancing turn serials. Combat requests carry encounter, turn, and request UUID. Out-of-turn, duplicate, stale, and excessive requests are rejected. Replay storage is bounded at 128 requests per turn; rejected gameplay requests may use a replay entry, but do not spend gameplay resources. `/tbc endturn` can recover an exhausted request budget and still requires the caller's turn.
- Combat state, move, attack, and end-turn channels use `_v2` identifiers. Automatic recruitment requires the new state channel. Both client and server must be updated together; full protocol negotiation/version UI is deferred.
- Supported melee, spawned projectiles, and creeper explosions use server-created action permits. Admission spends the action before damage, so an accepted miss spends it. Multiple distinct victims share one permit. Repeated damage to the same victim from the same permit is rejected. A projectile retains its permit across a turn change; tagged flights without a live permit fail closed. Up to 1,024 flights are tracked, for 200 world ticks each.
- Mob melee is wrapped at `MobEntity.tryAttack`; creeper explosions are wrapped at `CreeperEntity.explode`. The existing tactical player attack invokes the same adapter. Unsupported damage mechanisms fail closed when they involve encounter participants.
- Outsider and cross-encounter attributed damage is rejected. Accepted encounter effects cannot damage an outsider. Environmental and self damage retain vanilla behavior pending the encounter-time policy.
- Melee checks alive/world/membership/reach and collision raycasts to the target's eyes or center. This is Minecraft geometry, not SRD cover/visibility automation.
- Move requests rebuild the grid before acceptance. Paths sample the full entity block-collision box every 0.125 blocks and recheck during movement, including participant occupancy and actual landing support. Stair transit waypoints permit ascent/descent without interpolating through the riser. Nonactive actors are pinned in XYZ; free-moving mobs spend three-dimensional distance. World-border and loaded-chunk checks reject unsafe segments.
- Item, block, and entity interactions are currently denied during encounters until explicit action adapters exist. This closes bypass paths but **restricts previously available active-turn interactions**; F07 is not complete. Vanilla inventory changes and all modded interactions have not been comprehensively integrated.
- Dice requests allow a burst of four per 80 ticks; cards allow one per 20 ticks. Dice audience is the encounter or nearby players (64 blocks in the same world); card audience is nearby players. Pending client queues hold 16 entries. Evicted dice results are printed immediately; card history remains bounded separately. Rate state clears on disconnect/server stop.
- Manual d20 presentation says “Natural 20”/“Natural 1”, rather than declaring every such roll critical. Unsupported dice multiplication/division, negative dice, fractional/out-of-range dice counts, and dice inside scalar functions are rejected instead of silently reinterpreted.
- Packet decoders bound participant, grid, threat, card and dice collections and validate relevant enums/path parent ordering. Operator permission level 2 permits `/tbc advance` for the operator's own encounter, with a server log entry. Campaign-scoped DM permissions and configurable AFK policy are deferred.

## Files changed

| Area | Files |
|---|---|
| Build/testing | `.gitignore`, `build.gradle`, `src/test/java/dev/tacticalcombat/FoundationTests.java`, `src/gametest/java/dev/tacticalcombat/combat/AuthorityGameTests.java`, `src/gametest/resources/fabric.mod.json` |
| Pure foundation helpers | `src/main/java/dev/tacticalcombat/core/{EncounterIndex,TurnRequests,DamagePermit,RateLimit,BoundedQueue}.java` |
| Combat | `src/main/java/dev/tacticalcombat/combat/{Combat,Combatant,CombatManager,CombatActions,CombatGeometry}.java` |
| Server hooks | `src/main/java/dev/tacticalcombat/TacticalCombatMod.java`, `src/main/java/dev/tacticalcombat/mixin/{MobAttackMixin,CreeperExplosionMixin}.java`, `src/main/resources/{fabric.mod.json,tacticalcombat.server.mixins.json}` |
| Packets | `src/main/java/dev/tacticalcombat/net/{RequestStamp,PacketLimits,AttackRequestPayload,MoveRequestPayload,EndTurnPayload,CombatStatePayload,GridPayload,DiceRequestPayload,DiceRollPayload}.java` |
| Presentation | `src/main/java/dev/tacticalcombat/client/{ClientCombatState,TacticalCombatClient,TacticalScreen,DiceAnimation,ShareCardHud}.java` |
| Manual sheets/dice/cards | `src/main/java/dev/tacticalcombat/sheet/Expr.java`, `src/main/java/dev/tacticalcombat/dice/DiceService.java`, `src/main/java/dev/tacticalcombat/card/{CardService,ShareCard}.java` |
| Documentation | This checkpoint and `docs/implementation-spec.md` |

Existing camera/fading, sheet layout/editor, and pack files were retained. Automated pack validation is not visual regression validation.

## Verification evidence

Versions remain Java 21, Gradle 8.10.2, Loom 1.8.13, Minecraft 1.21.1, Fabric Loader 0.16.9 and Fabric API 0.116.1+1.21.1. No dependency downgrade or new test-library dependency was needed. A complete Oracle JDK 21.0.12.1 was downloaded from Oracle and verified against its published SHA-256; Java uses the system CA trust store for the platform proxy, with TLS verification enabled.

| Check | Actual result |
|---|---|
| Unchanged baseline `build` | Passed after resolving the missing usable JDK; original test task had no sources |
| Changed `build` | Passed, including compile, JAR remapping and regression runner |
| `foundationTest` | **249 assertions passed**; custom Java runner, not 249 JUnit test methods |
| `runGametest` | **11 required Minecraft server GameTests passed**, zero failures; latest suite completed in 1.620 seconds |
| Combined `build runGametest` | Gradle exit 0, `BUILD SUCCESSFUL in 26s` |
| Reusable environment install script | Executed successfully; build passed in 8 seconds with cached outputs |
| Dedicated graphical client / two real clients | **Not Verified**: no `DISPLAY` or `Xvfb`; no real network clients were launched |
| Restart persistence / corruption recovery | **Deferred**, not tested as an implemented feature |

The pure checks exercise exclusive ownership and stale release, turn/request replay bounds, damage permit scope/deduplication, rate limits, bounded queue ordering, decoder count bounds, expression regressions, both existing pack schemas and three example characters (including 80 derived evaluations).

The headless server tests cover:

1. Walls block attack rays.
2. New terrain blocks previously clear segments.
3. Head-height obstacles block full-sized actors.
4. Request identities survive packet codec round trips.
5. Excess card counts fail decoding.
6. Excess grid counts fail before entry allocation.
7. Encounter ownership excludes overlaps and cleans up; fully overlapping creation returns null.
8. Outsider damage is rejected by the real damage event and mob melee adapter.
9. Removing a floor invalidates a landing.
10. Mock-server-player encounter: out-of-turn rejection, accepted miss spending, repeated-action rejection, projectile admission on entity load, delayed and multiple-victim permits, outside/expired-flight rejection, and actual mob melee damage/action spending.
11. Mock-server-player encounter: inactive mob height is restored and an active path is cancelled when a wall appears.

Tests 10–11 use Minecraft's embedded mock server player. They are not two-client multiplayer tests. Creeper explosions load their mixin but do not yet have a dedicated multi-victim runtime test. Projectile damage admission is tested directly after spawning; actual flight/impact physics is not tested.

Warnings: Gradle reports deprecated features; the existing GameTest mock-player API is deprecated in this pinned Minecraft version. Authlib cannot resolve `api.minecraftservices.com` when retrieving public keys in the test JVM; the server tests still pass. Authenticated external client connections remain unverified. Earlier environment-only failures (missing usable compiler, proxy/TLS setup) were resolved; they are not recorded as source defects.

Evidence in this workspace: `build/gametest-results.xml`, `/tmp/cubetable-foundation-verification.log`, `/tmp/cubetable-setup-validation.log`. Generated evidence and artifacts are ignored, not committed.

## F01–F18 status

| Finding | Status | Evidence / remaining work |
|---|---|---|
| F01 ownership | Implemented | UUID registry, guarded claim/release, overlap GameTest. Real concurrent-client recruitment still unverified. |
| F02 attack geometry | Partially Implemented | Server melee collision rays and reach; SRD cover, unseen targets and special attacks deferred. |
| F03 accepted actions | Partially Implemented | Supported melee/projectile/explosion permits; miss/delay/multi-victim tests. Special mob attacks, multishot admission as one attack and multi-hit semantics need adapters. |
| F04 boundaries | Partially Implemented | Outsider/cross-encounter damage fails closed; outsider runtime rejection tested. Recruitment/pets/PvP policy remains incomplete. |
| F05 movement authority | Partially Implemented | XYZ pinning and 3D free movement budget; inactive height GameTest. Player packet jitter, forced movement and flying/swimming need multiplayer checks. |
| F06 paths | Partially Implemented | Fresh grid, swept body collision/occupancy and landing checks; terrain/headroom/floor tests. Broad stair/slab, large-creature and moving-occupant matrices pending. |
| F07 interactions | Partially Implemented | Encounter item/block/entity callbacks deny access. Explicit equipment/consumable interaction actions and inventory authority deferred. |
| F08 sheets as actor state | Deferred | Phase 2; sheets remain manual/local. |
| F09 critical presentation | Partially Implemented | Neutral manual natural-roll labels. Purpose-aware authoritative combat rolls deferred. |
| F10 expressions | Implemented for supported grammar | Unsupported forms reject; deterministic regressions and existing pack evaluation pass. Full new expression language not added. |
| F11 traffic/queues | Implemented for existing manual services | Tick rate limits, scoped audiences, bounded queues, lifecycle cleanup. Real many-client load and visual eviction behavior not verified. |
| F12 spell expenditure | Deferred | Existing local casting/resource behavior unchanged; Phase 6 authority prerequisites not present. |
| F13 safe persistence | Deferred | Existing character storage unchanged; start safe character storage in Phase 2. |
| F14 threat overlay | Deferred | Still approximate and truncated. It is not a legality prediction; UI labeling/correction remains required. |
| F15 snapshots/performance | Deferred | Frequent snapshots unchanged; no measured optimization claim. |
| F16 AFK/DM advance | Partially Implemented | Operator-only logged advance command; default unlimited turns retained. Campaign permissions, DM UI and configurable policy deferred. |
| F17 decoding/lifecycle | Partially Implemented | Bounded decoders/replay/flight/queues, path parent checks and limiter cleanup. Complete packet fuzzing and restart recovery deferred. |
| F18 encounter time | Deferred | World continues; environmental effects and flight expiry use world ticks. No coherent SRD duration policy yet. |

## Limits and risks

- This is Minecraft Tactical foundation work. Initiative remains plain d20; movement remains the existing four-direction grid and fixed allowances; vanilla damage/health still resolve supported combat. The approved one-block/five-foot SRD scale, eight-direction costs, logical footprints, abilities, AC, reactions, conditions, classes and spells are **Deferred** with their dependent phases.
- Geometry checks are conservative and sampled, not an exact continuous collision solver. Body collision rejects a route but the grid search does not yet find an alternative for every large creature. Occupancy validation considers enrolled actors, not every outside entity. Removed support beneath the starting/current pinned position, partial obstacles, and fluid/hazard intersections across the full swept footprint still need policy and tests.
- Accepted routes currently charge their full movement cost up front. Cancellation does not refund unused distance; this is retained behavior, not the future authoritative resource-refund design.
- Special damage paths such as guardian beams and slime contact have no accepted-action adapter and can therefore be suppressed. Multishot projectiles spawned outside a shared action scope may be rejected after the first. Do not call all vanilla mob behavior preserved. Add explicit adapters and regression cases before broad playtesting.
- A permit currently allows one damage callback per victim, which is unsuitable for future multi-hit attacks or damage components without richer effect identity. Projectile expiry is a provisional bounded-workload rule, not an SRD duration rule.
- Item denial is a temporary compatibility restriction. It needs supported action adapters, not a blanket claim that action economy is complete.
- No campaign DM hierarchy, server character binding, spell resources, unconsciousness, encounter persistence or reconnect ownership restoration exists. Disconnect pruning remains the original model. No save migration was attempted.
- The archive's test-only mod is excluded from release packaging. The mod's existing version string remains 0.1.0; distribute client/server builds together and do not infer wire compatibility from that string.

## Resume and next gate

From `/workspace/Cubetable`, inspect `git status` and the local branch before editing. Do not reset local work or push without explicit authorization. Build with:

```sh
/workspace/cubetable-setup/gradle.sh build
/workspace/cubetable-setup/gradle.sh runGametest
```

On another machine, install a complete Java 21 JDK and run the checked-in wrapper with `./gradlew build` and `./gradlew runGametest`. The cloud helper and verified JDK live outside the checkout; reproducible install/start instructions are saved in the cloud environment draft. Review/save that draft and publish the environment to retain it; publication/fresh-task restoration is not verified by this checkpoint.

**Next recommended work is to finish Phase 1 before starting Phase 2.**

1. Add supported item and special-mob action adapters, explicit multi-projectile/multi-hit effects, and a creeper multi-target GameTest. Verify misses and invalid target rejection consume correctly.
2. Extend path tests to stairs, slabs, wide/tall entities, changed intermediate landings, live occupancy, fluid hazards, and movement cancellation; define interrupted-movement charging.
3. Run two updated clients against a dedicated server: spam duplicate/stale requests, switch turns during projectile flight, attempt vertical movement and vanilla attacks/interactions, create two nearby encounters, remove/disconnect participants, and exercise operator advance.
4. Visually verify camera/fading, initiative/HUD, grid paths, both manual packs, sheet resizing, dice animations and card eviction. Test unmodified/old-client non-recruitment and clear upgrade messaging.
5. Only after that gate, introduce Phase 2 rules interfaces, authoritative actor/sheet binding, campaign permissions and versioned atomic character saves. Preserve manual packs. Then follow Phases 3–8 in the saved specification without claiming unsupported automation.
