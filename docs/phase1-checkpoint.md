# Phase 1 continuation checkpoint

Report date: October 8, 2026 (America/New_York).

## State and conclusion

Repository: `shayanzas/Cubetable`, existing checkout `/workspace/Cubetable`, branch `dev/combat-authority-foundation`. Starting implementation commit: `e6fe541`. This run preserved the existing work and dependencies. Verified implementation commit: `1b20a0819f9f148758534f9ad3733f494923672c`. Documentation follows in a separate local commit. The worktree was clean before this continuation; all changes described here belong to this run. No push or pull request is authorized or performed.

**Phase 1 remains Partially Implemented for release acceptance.** The expanded server regression gate passes for the supported mechanisms below. Real-client protocol, HUD, rendering and gameplay acceptance are **Not Verified**, and some vanilla mechanics are explicitly excluded rather than silently advertised as supported. The full release gate stays open. Phase 2 has not begun.

## Implemented mechanisms

| Area | Implemented behavior and bounds |
|---|---|
| Effect identity | A server-issued action can issue up to 64 distinct effects (including its initial effect), each deduplicated per victim. A second legitimate projectile/component can hit a previous recipient; replaying the same effect cannot. Maximum 8,192 primary/retaliation admission records per action. Vanilla hurt resistance still applies; admission does not force damage through it. |
| Ranged volley | `RangedWeaponItem.shootAll` shares one action across all projectiles. Each flight retains actor/encounter/effect identity after turn changes and owner removal. Flights remain bounded at 1,024 and expire after 200 world ticks. |
| Actual arrow impact | Persistent-projectile impact restores its accepted effect scope for damage/retaliation and checks target boundaries. An unowned outside projectile is not classified as environmental damage. |
| Creeper | One explosion admits multiple victims for one cost. Enrolled creepers do not spawn lingering status clouds: encounter-duration semantics are deferred. Terrain destruction remains vanilla. |
| Guardian | Beam warmup pauses outside the turn; completion validates target membership/range/geometry, then authorizes magic and physical components under one action. The physical component is not incorrectly rejected as a separate close-range melee action. |
| Slime | Contact damage validates vanilla contact range/visibility and combat legality before spending one action. Repeated/out-of-turn contact cannot deal another hit. |
| Blaze | The vanilla three-fireball burst shares one accepted action across its firing ticks; no second burst is admitted that turn. Its retained permit is scoped to encounter and turn. |
| Retaliation | Scoped vanilla thorns/guardian spikes are consequences of an admitted hit, deduplicated without spending the defender's turn action. Guardian spikes have an actual damage test. Exhaustive enchantment combinations remain unverified. |
| Splash effects | Accepted splash collisions preserve their action scope, filter eligible recipients for damage/healing/conditions and cannot execute twice. Outside and expired splashes cannot apply conditions to participants. Persistent clouds are excluded from encounter effects until encounter timing exists. |
| Item lifecycle | Supported item use reserves the action before vanilla execution; vanilla rejection returns the reservation. Multi-tick use/release keeps the same permit and is cancelled at turn end/removal. Failed/duplicate release cannot produce extra projectiles. |
| Protocol | Both initial and later player recruitment require the v2 state channel. Incompatible clients receive update guidance; `/tbc start` explains protocol incompatibility. Replay/turn identities remain server validated. |

Existing melee, packet bounds, dice/card limits, camera, sheet and pack systems were retained. New APIs contain no D&D AC, character binding, classes, spells, campaign storage or ruleset selection.

## Minecraft Tactical interaction policy

These are explicit Minecraft Tactical choices, not D&D Use an Object rules.

| Interaction | Policy |
|---|---|
| Character sheets, manual dice/cards, reading books | Retained; book UI is free. Existing manual service limits still apply. |
| Viewing chest/barrel/shulker UI | Free, including outside the player's turn; use an empty hand. Inventory mutation is checked separately. |
| Inventory/hotbar/offhand/drop, armor and armor-stand equipment | Free only on the actor's turn, while stationary and not actively using an item. Packet handlers gate slot/creative changes, hotbar selection, drops/swaps, inventory picking, trade autofill, recipe autofill, menu buttons and crafter slot state. Rejected changes trigger inventory synchronization; hotbar rejection also sends the selected-slot correction. |
| Merchant UI | Empty-hand interaction on the player's turn. Vanilla trade/inventory operations remain subject to the equipment gate. |
| Food except chorus fruit; drinkable potions; milk; bow/crossbow; snowballs/eggs | An accepted use consumes the action. Completion/release retains that action's scope. Charging and later firing a crossbow are separate accepted uses in this policy. Cancelling an accepted draw/drink does not refund the action; rejected use does. |
| Doors, trapdoors, fence gates, buttons, levers | Empty-hand use costs the action; vanilla PASS/FAIL refunds the reservation. |
| Mining/block placement, beacon configuration, teleport items, shields/tridents/fishing and unsupported item/entity interactions | Explicitly unsupported during encounters. Mining completion is guarded too, including a break started before recruitment. Unsupported uses fail without spending resources. Outside encounters the vanilla methods still run. |
| Unknown modded item/block interactions | Fail closed when enrolled. Third-party mods that mutate state outside these vanilla entry points require dedicated adapters; universal mod compatibility is not claimed. |

Server callbacks do not provide full real-client UI acceptance. In particular, predicted inventory/interaction feedback under latency must still be verified using the linked checklist.

## Movement corrections and accounting

The existing four-direction grid, Minecraft block scale and fixed budgets remain. D&D movement has not been introduced.

An accepted path retains its original total grid cost, but charges it proportionally to actual distance traversed along that fixed route, including stair transit segments. Completed paths therefore cost exactly the original grid cost; unused route distance is never charged. Cancellation keeps charges for completed progress. New requests cannot refund earlier progress or request another path while moving. Pure accounting tests reject negative/non-finite progress and cap charges at the accepted route cost.

Before each movement tick, the server rechecks **all remaining segments and real landing points**, so a removed intermediate floor can cancel a route before the actor enters its unsupported stair transit. Full hitboxes, hostile traversal, occupied destinations, terrain, hazards/fluids, world border and loaded chunks are checked. The sampled geometry check refuses segments over 64 blocks rather than doing unbounded work. Rebuilding path previews does not replace server revalidation.

Tests execute one-block ascent/descent, slabs and stairs; interrupt a real accepted move after partial travel; remove an intermediate floor; introduce an occupant; test ravager/enderman dimensions, diagonal corner geometry (without enabling diagonal grid movement), fluids/fire/berry bushes, border and unloaded geometry, and inactive vertical pinning. Large-creature pathfinding may still reject a route without searching a shape-aware alternative. Cancellation at a partially traversed airborne transit can leave an actor pinned there; generalized falling/forced movement and encounter time remain later work.

## Tests and outcomes

The exact final command outcomes are recorded in the verification record below. The baseline was rerun before edits: build plus the original 249 assertions and 11 GameTests passed. The expanded pure runner executes **320 assertions**; it is not 320 JUnit methods. The expanded headless suite contains **29 required GameTests**, including the original 11.

| Verification category | Outcome / evidence |
|---|---|
| Compile/package/remap | `build` executes on the complete existing Java 21 toolchain; no dependency versions changed. |
| Pure regression execution | 320 assertions cover original packs/expressions/limits plus distinct and unissued effect IDs, effect limits and incremental movement accounting. |
| Minecraft server execution | 29 GameTests exercise real mixins/events/world geometry and codecs. Zero failures in the final successful run. |
| Embedded/mock player execution | Inventory packets, item start/finish/release, commands, encounter turns, movement, simultaneous encounters and player removal are exercised with Minecraft embedded mock server players. |
| Actual graphical/network clients | **Not Verified**. No DISPLAY or Xvfb is available; no two-client server test or visual regression was performed. |
| Static review only | Broader third-party compatibility, AI cadence and untested enchantment combinations; no measured load/performance claim. |

Added runtime cases cover three charged crossbow projectiles and actual impacts after owner removal, duplicate impacts, creeper multiple victims/cloud restriction, guardian two-component beams and spike retaliation, slime contact, blaze bursts, splash condition boundaries/replay/expiry, potion completion, bow rejection/release, equipment and inventory packets, free container UI/paid lever use, cancelled/unsupported use, routes/geometry, stale requests/operator permissions, independent nearby encounters and disconnect cleanup.

One expanded run failed because the fire fixture had no supporting floor: vanilla removed it before the check. The fixture now supplies a valid floor and asserts the hazard still exists before checking geometry; the test was not removed or weakened. This was not a confirmed production collision defect. Gradle deprecation, deprecated mock-player API, and Authlib public-key DNS warnings remain. They do not prevent the headless suite; authenticated external connections remain unverified.

Evidence: `build/gametest-results.xml`, `/tmp/phase1-build-final.log` and `/tmp/phase1-gametest-final.log`. Both are generated local artifacts, not committed test-result claims for another machine.

## Files changed since e6fe541

| Component | Paths |
|---|---|
| Core/geometry | `src/main/java/dev/tacticalcombat/core/{DamagePermit,MovementCharge}.java`, `combat/{Combat,Combatant,CombatActions,CombatInteractions,CombatGeometry,CombatManager}.java`, `grid/Grid.java` (all packages beneath `src/main/java/dev/tacticalcombat/`) |
| Server setup | `src/main/java/dev/tacticalcombat/TacticalCombatMod.java`, `src/main/resources/tacticalcombat.server.mixins.json` |
| Combat mixins | `src/main/java/dev/tacticalcombat/mixin/{RangedWeaponMixin,ArrowImpactMixin,GuardianBeamMixin,SlimeAttackMixin,BlazeVolleyMixin,CreeperExplosionMixin,PotionBoundaryMixin,CloudBoundaryMixin}.java` |
| Interaction mixins | `src/main/java/dev/tacticalcombat/mixin/{ItemInteractionMixin,ActiveItemMixin,PlayerInteractionMixin,ArmorStandInteractionMixin,InventoryAuthorityMixin}.java` |
| Tests | `src/test/java/dev/tacticalcombat/FoundationTests.java`, `src/gametest/java/dev/tacticalcombat/combat/PhaseOneGameTests.java`, `src/gametest/java/dev/tacticalcombat/testmixin/*.java`, `src/gametest/resources/{fabric.mod.json,tacticalcombat.test.mixins.json}` |
| Reports | `docs/phase1-checkpoint.md`, `docs/phase1-multiplayer-checklist.md`, superseding link in `docs/foundation-checkpoint.md` |

The development-only accessors allow tests to invoke private/protected vanilla effects and set windup counters. They are not shipped in the release JAR. Their direct invocation verifies effect execution; it does not establish natural AI scheduling or end-to-end client networking.

## Remaining F01–F18 findings

| ID | Current status | Remaining scope |
|---|---|---|
| F01 | Implemented in supported server tests | Exclusive UUID ownership, nearby independent encounters and removal tested; real recruitment races/latency acceptance unverified. |
| F02 | Partially Implemented | Supported melee/beam geometry checks; full SRD cover/visibility deferred. Wider mob/obstacle playtesting remains. |
| F03 | Implemented for listed adapters; partial overall | Distinct effects, volleys, beams, contact, explosions, delayed impact and retaliation tested. Unsupported mechanics listed below; broad enchanted/mob AI combinations unverified. |
| F04 | Implemented for supported damage/splash boundaries; partial overall | Independent encounters, outsider/unowned shots, status splashes and clouds covered. Controlled reinforcements, pets, nonliving/world effects and policy expansion remain. |
| F05 | Partially Implemented | XYZ pinning and movement budget tested headlessly; real movement packets, knockback, latency, flight/swimming acceptance still open. |
| F06 | Implemented for expanded regression scope; partial overall | Current terrain, footprints, landing, hazards, occupancy and accounting tested. Shape-aware alternative search and broader natural terrain remain. |
| F07 | Partially Implemented | Blanket denial replaced with explicit free/paid/unsupported adapters. Full item/entity/modded coverage and real UI reconciliation remain. |
| F08 | Deferred | Authoritative character binding is Phase 2. |
| F09 | Partially Implemented, unchanged | Neutral manual natural-roll labels; authoritative roll purposes later. |
| F10 | Implemented for supported grammar, unchanged | Existing formula and pack regressions continue passing. |
| F11 | Implemented for existing services, unchanged | Bounded queues/traffic retained; visual/load acceptance not verified. |
| F12 | Deferred | Manual local casting remains; server spell automation is later. |
| F13 | Deferred | Versioned atomic character storage starts in Phase 2. |
| F14 | Deferred | Threat overlay remains approximate/truncated; do not treat it as attack legality. |
| F15 | Deferred | Snapshot frequency unchanged; no optimization/performance claims. |
| F16 | Partially Implemented | Operator advance permissions/logging now executed in tests; campaign DM roles and configurable AFK policy later. |
| F17 | Partially Implemented | Effect/flight/replay bounds, decoder tests, active-use cleanup and recruitment compatibility covered; packet fuzzing/restart recovery later. |
| F18 | Deferred | Environmental/ongoing effects still use world ticks. Persistent clouds are explicitly excluded; no SRD duration system. |

## Explicit limitations and release gate

- Evokers (fang/summon actions), wardens (sonic/AI mechanics), withers and ender dragons are excluded from tactical recruitment. Their normal outside-world behavior is retained. Enrollment exclusion is regression-tested for evokers/wardens. This is a declared supported-creature limit, not full vanilla parity.
- Persistent area clouds do not affect enrolled actors; clouds owned by enrolled actors do not affect outside actors. An enrolled creeper's cloud is suppressed. These limitations are tested and must be revisited with encounter-time effects.
- The remaining item restrictions are listed above. In particular, shields, tridents, fishing, teleport food/items, placing/mining and player-thrown potions are not claimed supported. Witch-created splash projectiles have a supported effect path. Vanilla world damage, ongoing status durations and self/environmental damage retain the earlier timing policy.
- Flight/effect admission is in-memory; expiration is world-time based. Save/reload, encounter recovery and disconnected-character continuity are not implemented. Disconnect currently prunes the actor, which is tested cleanup rather than future persistence behavior.
- No automated D&D mechanics were introduced, and vanilla mechanics are not labeled SRD compliant. Full network/visual acceptance and real AI cadence remain open.

## Resume and Phase 2 entry point

Use the current branch; inspect status before editing. Run:

```sh
/workspace/cubetable-setup/gradle.sh build
/workspace/cubetable-setup/gradle.sh runGametest
```

On a normal development machine use a complete Java 21 JDK and `./gradlew` for the same tasks. Perform the [two-client and visual checklist](phase1-multiplayer-checklist.md); record actual outcomes, logs and exact commit. Fix any authority/synchronization failure before closing Phase 1.

Only after that acceptance should Phase 2 begin with **rules interfaces plus authoritative actor/character binding**, followed by server permissions and versioned atomic character storage. Preserve both manual game packs and this regression suite. Do not start D&D classes, spells, campaign recovery or a broad combat rewrite to bypass the outstanding Phase 1 gate.

## Final verification record

Both requested commands exited successfully after the final source changes:

| Command | Actual outcome |
|---|---|
| `/workspace/cubetable-setup/gradle.sh build` | Exit 0; **BUILD SUCCESSFUL in 13s**; **320 assertions passed** |
| `/workspace/cubetable-setup/gradle.sh runGametest` | Exit 0; **BUILD SUCCESSFUL in 19s**; **29 required tests passed**, zero XML failures/errors |
| `git diff --check` | Passed |
| Release archive inspection | Test classes, test mixins and test accessors absent; production mixin configuration present |

Artifact: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `7e2a478850284de6302e168f5d0a133cfcc556a581eb9ecf55011bc7cd644e3b`.

The implementation commit and following report commit are local on `dev/combat-authority-foundation`. Generated builds/logs remain ignored; no other edits are intended to remain in the worktree. Use `git status --short` and `git log -2 --oneline` to confirm the delivered state before continuing. Nothing was pushed.
