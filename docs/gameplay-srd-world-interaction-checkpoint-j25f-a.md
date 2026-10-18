# J2.5F-A — SRD world interaction foundation

Base branch: `dev/srd51-reference-completion-checkpoint-j25c2`.
Base commit: `c191c6ac247f2a9aab33d128a7d32cadb9aff58d`.
Local branch: `dev/gameplay-srd-world-interaction-j25f-a`.
Implementation commits: `393f435487b2f93013ceef711b9f51b705065c06` (server contracts and GameTests), `213b9f366f816fda6165fc4d6093707de9593665` (client foundation and tests).
Final delivery commit: the commit containing this report (`git log -1 --format=%H -- docs/gameplay-srd-world-interaction-checkpoint-j25f-a.md`). Its exact hash is also supplied in the delivery message; a commit cannot contain its own hash.

**Stage A only. Local commits; no push or merge. Stage B requires review and approval.**
Automated validation: passed (full final command, 1 minute 5 seconds). Graphical Minecraft acceptance: **NOT DONE**. No screenshots, capture filenames, tested resolutions or GUI scales are claimed. This environment has no DISPLAY, Wayland display or Xvfb. No primary world was opened or modified.

## Verified starting state and diagnosis

The checkout was clean at the required C2 SHA, and `git ls-remote` confirmed the same remote HEAD. Both D2 `59b75ce2dc9842452db7eb2215a5b2fefbc758e2` and E `9da783fd05aca033f55593ce39bd5432c671a827` are ancestors. The new branch was created without resetting or replacing the checkout. Previous reports and the production SRD gateway, encounter, movement, camera, renderer, launcher and transaction code were inspected.

The repeat-movement symptom has a concrete client failure: each encounter revision invalidates the received reachable grid, but the previous client never requested a replacement. A server-completed step increments that revision. The server movement controller already retains spent feet, and J2 reconciliation after an attack does not reset them. The new real-world test demonstrates 5 + 5 + attack + 20 feet on one player turn, then transitions the same manager account to its independently prepared Husk. This is automated reproduction of the relevant server workflow, not a recreation of the user's graphical session.

The original camera consumes Tactical packets and cannot infer SRD control. The SRD `controlled` field already implements current manager/player permissions. Campaign managers can control the NPC and the player actor; an ordinary player cannot assume NPC control just because an NPC is bound. No ownership/controller policy was relaxed.

## Camera and world interaction

`SrdCamera` adapts the viewer-filtered SRD state to the existing camera mixin and block-fading renderer. It checks dimension and both entity ID and UUID, follows the authoritative current actor, and resets pan on actor changes. Initial pitch is 55 degrees and distance 18 blocks; middle-drag changes orbit/pitch (25–80 degrees), wheel adjusts zoom (8–40 blocks), and WASD pans within 48 blocks. Follow smoothing, yaw/zoom interpolation and camera blend follow the existing Tactical behavior. The original Tactical state is never populated with fake SRD entries or activated to enable the camera. Tactical takes priority, including its existing fade-out.

Encounter termination, disconnect and lost viewer authorization clear transient SRD state and restore the normal camera. A temporarily unloaded/mismatched entity or dimension disables SRD camera engagement. First-person hands and the crosshair are suppressed while using the camera. Keyboard walking is suppressed only for the local player actually present in the authorized participant list; a watching manager is not mistaken for the NPC's physical player entity. Server movement enforcement remains authoritative.

`SrdBattlefieldScreen` is a deliberately minimal Stage A integration surface, not the finished Stage B HUD. Launcher entry transitions to it when initiative is active. Pending ties retain the existing detailed controls. Details/DM remain optional, with a Battlefield return button and End Turn available in every detailed page. Direct terrain clicks select a server-provided cell, show its path/cost, and require Confirm Move. Actor clicks inspect only the authorized participant data; Attack Preview opens existing server-preview/explicit-confirm controls. Chat, inventory, Quick Sheet, DM tools and the character sheet remain reachable through their keys; camera pan polling is confined to the battlefield screen.

Picking uses the actual render projection and camera rotation. Terrain collision bounds the ray; only server-provided cell tops can become movement destinations. Heights, negative coordinates and nearest elevated surfaces are handled. Participant hit boxes are picked only before the blocking terrain. Camera occluders can fade, as in Tactical; **solid terrain still blocks SRD picking**. This intentional distinction is explained in the Recenter tooltip. There is no new x-ray grid pass. Current-turn, self, other controlled and other participants have depth-tested markers; these indicate identity/control, not invented alliance rules. Selected path and destination use the received grid.

## Grid discovery and paced movement

New additive snapshot fields are `dimension` and `moving`. The client requests a read-only grid only when the current actor is controlled, loaded in the correct dimension, not moving and has at least five feet left. Discovery is coalesced to at least 22 client ticks between requests and once per observed encounter/revision/turn. A failed request at the same revision is not retried in a loop; explicit Refresh remains available. The existing grid request correlation and revision checks still reject obsolete results. State/permission changes invalidate selections. No automatic Move, Confirm, End Turn or RNG request is issued by the refresh scheduler.

The battlefield offers session-local movement pacing: 1, 2, 4 or 6 server ticks per square, default 4. `move.stepTicks` is optional, exact-integer and bounded to 1–6. Older clients and existing call sites omit it and retain one tick per square. The first step executes immediately; later steps wait the selected interval. During waits the server continues to check actor identity, permissions, anchor, current character state and the entire remaining route. Only a completed whole step spends feet; interruption preserves completed charges. This is an explicitly tested change to server route timing, not a delayed client animation hiding the true server position. It introduces no new time-based gameplay effects. Player movement remains discrete server teleports; smooth walking animation is not claimed. Pace settings are not persisted across application restarts.

## Attack outcomes and privacy

Live confirmation and durable receipt replays use the same result formatter. Existing faces/chosen/total/keep/critical/hit fields remain. Additions:

- `actor`, `target`: authoritative entity UUIDs, resolved to authorized roster names by the client.
- `modifier`: server total minus the server chosen face.
- `rolledDamage`, `damageType`: the server's rolled damage before target defenses; never labeled as applied HP loss.
- `hpBefore`, `hpAfter`, `temporaryBefore`, `temporaryAfter`, `zeroHp`: only for a currently authorized manager or the target's authorized player controller, with campaign membership checked again.

Each watching recipient gets a separately filtered object. Ordinary attackers do not receive private enemy HP, defenses or a new private zero-HP flag. J2 receipt reads still reauthorize before formatting. A latest-result panel retains the authorized result through grid refresh and clears on encounter/disconnect or changed control permissions. Existing dice/event routing and server RNG remain unchanged. Zero HP is described as a DM adjudication pause, not automatic death, death saves or encounter completion.

Supported weapon/mode/ability/hand shapes constrain the detailed attack selectors. Unarmed offers only STR/melee/one hand; finesse, thrown, two-handed and versatile shapes remain supported where appropriate. Monster ability selection uses the server-authored reviewed ability. Equipment ownership, shield conflicts, ammunition, geometry, target validity, action costs and all authoritative values still require server preview and confirmation. No Dash, spell, feat, reaction or unsupported monster ability was added.

## Persistence and compatibility

Library schema 2, campaign schema 5, SRD combat v1, J2 prepared/committed receipts, D2 stage storage and the C2 reference resources are unchanged. No save migration, new persistent gameplay data, resource refund or automatic encounter recovery is introduced. New snapshot/result/movement fields are additive to existing v1 payload JSON. Install matching client/server JARs for the new presentation; old servers without `dimension` cannot provide the new camera, but detailed controls remain available. Tactical and Generic D20 mechanics are not routed through the SRD adapter.

## Automated evidence

Command:

```sh
JAVA_HOME=/workspace/.tools/jdk21 GRADLE_USER_HOME=/workspace/.gradle \
JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

- **36,907 foundation assertions across 42 suites**, including 52 new world-interaction assertions.
- **145/145 GameTests**, zero failures, errors or skipped cases (143 prior tests plus two new tests).
- Catalog validator: **2,748 checks**; source coverage/completion: **15,115 checks**; **17,863 combined**. Repeated Gradle task executions are not double-counted.
- Build and packaged JAR creation succeeded. No existing test assertions were weakened or removed.

Evidence: `build/j25fa-release-validation.log`, `build/gametest-results.xml`, and `build/reports/srd51-catalog.json`. Test worlds are generated under ignored build output only. Existing deprecation warnings and the offline server's optional external authentication public-key lookup warning remain; they did not prevent the GameTests from completing.

New foundation coverage exercises bounded grid refresh across revisions/turns, denial/no-loop behavior, disconnect reset, picking of elevated/slab/negative-coordinate cells, solid obstruction, missing cell rejection, authoritative NPC control selection, revoked/missing authorization, dimension isolation, Tactical priority, participant-only keyboard locking, private-result invalidation, allowed attack shapes, and private versus authorized result descriptions.

New GameTests exercise one real mock player manager with a separately source-prepared Husk: repeat movement, same-turn attack and remaining movement, committed NPC HP, turn switching, NPC speed, manager revocation and removal of private HP. The pacing test covers forged cadence, wait ticks, exact cost timing, and a route obstacle appearing during a wait. Existing production-protocol and zero-HP tests additionally assert result privacy and target identity. Existing short/long/multilevel routes, stale requests, disconnect, unauthorized targets, J2 replay/fault/restart, D1 checks, E dice, D2 stages, launcher, Tactical, Generic D20 and catalog tests all remain required.

Two initial **new-test fixture failures** were investigated rather than removed: NPC preparation requires an SRD-selected campaign; and the first chosen post-attack destination needed a detour around the adjacent Husk, exceeding the proposed four-step path. The corrected fixture sets the SRD profile and uses an unobstructed four-step route. The existing gameplay rules were not weakened to make the fixture pass.

## Stage B integration contract and remaining limits

Stage B must start from this branch's reviewed commit on a new branch, after explicit approval. It should consume `ClientSrdCombat` state/current/selected/grid/movement and `SrdViewPolicy` capabilities; `SrdCamera` owns SRD presentation only. Mutations continue through `ClientSrdCombat.send` with the current server turn identity, while `SrdGridRefresh` performs discovery only. Attack outcomes come from authorized server fields and must keep rolled damage distinct from applied HP changes.

The final compact HUD, initiative timeline/up-next, portraits, complete contextual attack confirmation without opening Details, expanded dismissible history/toasts, persisted presentation preferences, and GUI-scale polish are **deferred to Stage B**. The old Details panel remains opaque by design in Stage A. Very constrained windows can truncate/wrap the foundation panel and latest-result display; no scale-specific visual acceptance is asserted. Self/current markers can overlap at the same location; final styling is for Stage B. Camera-ray projection, wall occlusion, mouse input and transitions compile but still need actual graphical testing. No new monster AI or broader DM Workspace redesign was attempted. No new art/audio assets were used.

## Minecraft acceptance — NOT DONE

Use a backed-up disposable test world and matching client/server JARs. Do not replace the primary-world installation for this review.

1. Use the selected SRD campaign, an approved bound `ree_bunger` and a separately prepared/bound Husk using the Zombie stat block. The one human account must have current campaign DM/manager authority to control both.
2. Start through the existing graphical launcher; resolve initiative ties if present. Verify the camera lifts and follows the **actual current actor**, including the Husk, and the battlefield is visible outside optional Details.
3. Test wheel zoom, middle-drag orbit/tilt, WASD pan and Recenter. Open chat, inventory, Quick Sheet and DM tools; confirm camera keys do not hijack those screens. Check ordinary camera restoration after ending combat and disconnecting.
4. On a 30-foot player turn, choose a clear 5-foot destination and Confirm Move. Wait for grid refresh (up to about 1.1 seconds plus network/server processing). Verify 25 feet remains, then move another 5 feet on the same turn.
5. Preview and confirm a legal melee attack. Inspect actor/target, hit/miss, roll breakdown and damage. As DM, inspect exact HP/temp-HP changes. Move another legal route with the remaining feet; do not require Dash or a new turn.
6. Test Pace at 1, 2, 4 and 6 ticks per step. Costs must agree despite timing changes. Confirm no second movement is sent on refresh, resize or opening/closing Details. Test slopes/slabs and walls; a visible faded wall must still block picking through it.
7. End Turn from the battlefield and each Details page. Verify the active label, camera and remaining movement change to the Husk (Zombie speed 20 feet), without transferring ownership or selecting the player actor as the mover.
8. Confirm invalid unarmed mode/ability options are unavailable. At zero D&D HP, verify the target is identified only to authorized viewers, ordinary actions stop, and DM adjudication remains required; vanilla death/automatic combat completion is not implied.
9. With a separate ordinary player, verify inspect-only behavior on another actor's turn, no private target HP in results, and denial of stale/unauthorized actions. Revoke manager permission and verify NPC control/private latest-result presentation disappear.
10. Test GUI scales 2/3/4 and record actual window resolution plus screenshots. Exercise Tactical and Generic D20, D1 private rolls, E dice, C2 browsing and D2 saved stages. Report defects separately from Stage B styling requests.

## Artifact

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `0119360f0be1a245179fbabc6372794682696068686f9e10074c7a615893c683`.
No artifact was installed into the user's world.

## Changed files

- `docs/gameplay-srd-world-interaction-checkpoint-j25f-a.md`
- `src/gametest/java/dev/tacticalcombat/combat/SrdLiveGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdWorldInteractionGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/BlockFade.java`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/SrdAttackChoices.java`
- `src/main/java/dev/tacticalcombat/client/SrdAttackFeedback.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdCamera.java`
- `src/main/java/dev/tacticalcombat/client/SrdCombatScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdGridRefresh.java`
- `src/main/java/dev/tacticalcombat/client/SrdGridRenderer.java`
- `src/main/java/dev/tacticalcombat/client/SrdViewPolicy.java`
- `src/main/java/dev/tacticalcombat/client/SrdWorldPick.java`
- `src/main/java/dev/tacticalcombat/client/SrdWorldPicker.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/client/ui/EncounterLauncherScreen.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/mixin/client/CameraMixin.java`
- `src/main/java/dev/tacticalcombat/mixin/client/GameRendererMixin.java`
- `src/main/java/dev/tacticalcombat/mixin/client/InGameHudMixin.java`
- `src/main/java/dev/tacticalcombat/mixin/client/KeyboardInputMixin.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/SrdWorldInteractionTests.java`
