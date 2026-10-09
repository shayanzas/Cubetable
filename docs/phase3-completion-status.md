# Phase 3 status — Stage C authoring and resource prerequisites

Report date: October 9, 2026 (America/New_York).

**Readiness: Phase 3 partially implemented; additional development required.** This is a tested partial Stage C checkpoint, not a playable SRD encounter release. Stage C's live encounter verification gate has not passed, so Stage D integration was not started. Stage E regression checks were run for the changes actually made, not claimed as end-to-end SRD combat acceptance.

## Repository checkpoint

- Repository: `shayanzas/Cubetable`.
- Branch: `dev/phase3-srd-combat`.
- Starting HEAD: `dab018e85d04727c44c19b4629af221dfbc785e2` (Stage B documentation, after implementation `2119d1076dfa9b728f014929fe3c026dad099ac4`).
- Retained Stage A implementation: `0d5b1f66eaf6b9b1712d301e6c57470b9f7dc328`.
- Retained Phase 2 base: `6b28e42caeb447ba3578cac60c0d0b58e6e4e5a0`.
- New implementation: **`31f0a3b43b501c73b1ae5a03bbd01e98c560309f`**, “Add SRD character authoring and revision-safe persistent HP prerequisites”.
- This report is committed immediately afterward in a documentation-only commit. `git log -2 --format='%H %s'` provides the exact final documentation HEAD without a self-referential commit hash in its own contents.

The starting worktree was clean. No reset, reclone, dependency update, main/upstream change or remote write was performed. **Phase 3 has not been pushed.** The published Phase 1/2 branches are untouched. Code/test changes were committed only after their build/GameTest run succeeded; the final documentation commit leaves a clean worktree.

## Implemented in this checkpoint

### SRD character creation and form

The Server character workspace discovers `srdAuthoringVersion: 1` from the existing private character protocol. With an SRD campaign selected, **New SRD** creates a server template through `srd_create`, without requiring a local sheet or handwritten JSON. Other campaign profiles are rejected. An older server that does not advertise the feature does not expose this creation control.

The server builds the template itself; client-supplied approval/HP flags do not configure it. It uses the existing Generic D20 sheet envelope without converting any existing Generic D20 data. Defaults are explicitly neutral: six scores of 10, level 1, 10/10 HP, no temporary HP, speed 30 feet, Medium size, reach five feet, no armor/shield, no granted weapon/armor proficiencies or class assumptions. Unarmed proficiency remains the Stage A rule. This is a starting draft to review, not an automatically generated class or monster stat block.

The new **SRD fields** form edits the working draft with numeric inputs, enumerated choices and multi-select controls. It covers name, all abilities, level, bonuses, HP/max/temp, speed/reach, size, armor/shield, proficiencies, available weapons and typed defenses. Fields and larger choice lists are paginated. No complex JSON is required for the supported schema. Intermediate numeric edits can be incomplete, but **Validate and stage** requires the complete Stage A codec to pass. Initializing missing/malformed SRD data requires explicit confirmation and preserves other sheet fields. Unsupported schema data is not silently converted.

The form only stages into the parent workspace. **Save changes** still uses the existing revision-checked `edit` request, permissions, owner locks, approval invalidation, dirty-draft preservation and explicit reconciliation. The original local/manual sheet is never written. Bound-character edits remain forbidden for players and DMs. No unsafe DM override endpoint was added.

The server independently validates edits replacing `collections.srd_combat`; client form validation is not authority. Existing incomplete legacy SRD records are retained. Their prior approval does not grant automation readiness. Invalid records receive a reason instead of being rewritten or deleted. Legacy submit/approve operations can still hold incomplete records, but the SRD input adapter refuses them.

### Private eligibility and resource readback

Authorized SRD character snapshots now contain a separate `srd` status object with schema validity/reason, derived AC, adjusted movement feet, initiative modifier, authoritative D&D HP/max/temp, character prerequisite readiness and a separate world-engine flag. Ordinary viewers still need campaign membership and ownership; managers retain scoped campaign access. Unrelated viewers receive no private statistics.

The new UI shows D&D HP/AC/movement from that object rather than presenting generic Tactical HP as SRD HP. `characterReady` means schema, approval, binding, owner membership and positive D&D HP are satisfied. It is **not** a check of the loaded entity's location/client capability or permission to start an encounter. `worldCombatEnabled` remains false, and the screen labels the disabled engine separately. Long messages are trimmed on the current screen; full small-screen usability needs graphical acceptance.

### D&D resource arithmetic and durable storage

`SrdHealth` provides bounded current/max/temporary HP arithmetic using 2014 SRD 5.1 pp. 97–99:

- Damage applies Stage A typed defenses first, then consumes temporary HP before current HP.
- Healing restores current HP up to maximum, not temporary HP.
- Temporary HP does not stack; the caller must explicitly choose to keep the current amount or replace it, including replacement with a lower amount.
- Temporary HP at zero current HP does not make the character eligible for ordinary actions.
- Current HP floors at zero. This is a resource boundary, not unconsciousness, death saves, stabilization or massive-damage death automation.

`CharacterRepository.commitSrdHealth(expectedSnapshot, health)` is an **internal server persistence operation**, not a packet or command. It rereads approved binding/campaign/profile data, compares the complete expected snapshot and revisions, disallows maximum-HP changes, validates the resulting sheet, atomically writes it and only then publishes memory. HP/temp update the authoritative `srd_combat` collection. No vanilla HP is subtracted or restored. Even an unchanged resource result advances the runtime revision, preventing a retry against that same expected state from applying again. The editing revision and approval remain unchanged.

This is compare-and-set protection, **not a completed accepted-action transaction**. The future caller must run on the server thread, retain Phase 1 action/effect identity, validate permissions/geometry, commit resources once and handle a save failure before acknowledging the action. It must not regenerate a fresh snapshot to replay an old effect, reroll after a failed save, or refund an already accepted action without an explicit transaction policy. No live attack/heal caller exists in this checkpoint, and no general client resource mutation endpoint was added.

The repository itself now ignores vanilla-health capture for non-Tactical profiles, in addition to the existing service-layer profile check. SRD values survive normal repository reload and supported unbind/rebind without being restored from older sheet defaults. Generic `values.hp`/`hp_max` remain legacy fields and are not rewritten as D&D resources.

### Editing conflicts and failure recovery

Replacing `collections.srd_combat` now requires the expected runtime revision as well as the existing editing revision. Thus an old form cannot restore old HP after a runtime update, even after unbinding. Unrelated text changes still use normal editing revisions. Because the collection is a single reconciliation field, simultaneous form and HP changes can require explicit whole-collection conflict resolution; finer-grained SRD field reconciliation is not implemented.

Schema 2 remains unchanged: new runtime values live in the existing validated collection, with the existing `runtimeRevision`. No migration or duplicate HP database was introduced. Interrupted-write tests confirm unchanged primary bytes, unchanged in-memory HP/revision, successful normal reload, subsequent successful persistence and explicit valid-backup recovery. Backup recovery can roll back the latest resource mutation, just as it can roll back a Phase 2 policy edit; operators must heed recovery warnings. There is no persistent action receipt or complete encounter restoration. Phase 7 remains responsible for full encounter recovery.

## Stage C gate: not completed

Inspection confirms the remaining integration work cannot safely be replaced by toggling the SRD profile flag:

- `CombatManager` currently stores exclusive ownership as `EncounterIndex<Combat>`; `Combat` and existing interaction/damage hooks implement the Tactical lifecycle.
- `CombatActions` resolves melee, projectiles and damage through that ownership lookup. A parallel SRD encounter collection without shared ownership/hook routing would allow the existing code to treat SRD participants as outside combat.
- `CombatManager.requestAttack` still routes a stamped click directly to the Tactical attack path. It is not a preview/confirm protocol.
- `CombatStatePayload` and the existing grid/HUD route still describe the Tactical snapshot. No versioned SRD encounter/intent/confirmation/result channel or compatible-client admission gate exists.

These are **unimplemented integration dependencies, not newly reproduced regressions or an environmental build failure**. This run stops at the tested prerequisite checkpoint. Stage C's user-specified gate (live initiative, movement, confirmed attacks, damage, duplicate handling and cleanup) has not been met. Dependent Stage D features were not built over that missing gate.

| Requested area | Status after this run |
|---|---|
| Practical SRD character template/form | Implemented; graphical acceptance Not Verified |
| Server validation, approval/binding readiness and private refresh | Implemented for character prerequisites; actual encounter admission Deferred |
| D&D HP/max/temp arithmetic and persistence | Implemented internal operations; live effect integration Partially Implemented |
| Runtime/edit conflicts and storage failures | Implemented and tested for new operations |
| Live encounter ownership/lifecycle/recruitment/cleanup | Deferred; still Tactical-only |
| Initiative integration and player/DM tie resolution | Deferred; Stage A arithmetic remains isolated |
| Live Stage B scheduler/grid/preview wiring | Deferred; Stage B internal engine remains isolated |
| Attack selection, preview intent, confirmation and action expenditure | Deferred; no SRD attack interface or authoritative action pipeline |
| Damage/HP synchronization during a live SRD encounter | Deferred; only private character refresh is implemented |
| Zero-HP encounter pause/adjudication and NPC defeat policy | Deferred; zero-HP prerequisite rejection is not a playable recovery system |
| Minimal SRD creatures/NPC turn control | Deferred; no vanilla-stat inference added |
| Stage D reach/range, cover, visibility and world targeting | Deferred behind Stage C gate |
| Stage D live weapon/ammunition/loading/defense enforcement | Deferred; existing isolated Stage A calculations retained |
| Stage E end-to-end encounter testing | Not Verified / no playable SRD encounter yet |
| Stage E regression verification for implemented changes | Implemented; results below |
| Graphical/remote-client acceptance, hidden/secret DM roll controls | Not Verified / Deferred |

## Verification evidence

Commands executed before changes and for the final checkpoint:

```sh
/workspace/cubetable-setup/gradle.sh build runGametest
```

Intermediate `build` runs also compiled the new form and ran pure assertions. Final full run: **BUILD SUCCESSFUL in 26s**. Dependencies unchanged: Java 21, Gradle 8.10.2, Loom 1.8.13, Minecraft 1.21.1, Loader 0.16.9, Fabric API 0.116.1+1.21.1.

| Suite | Passing count |
|---|---:|
| Previous foundation, repository, synchronization, persistence, Stage A and Stage B assertions | 811 |
| New SRD authoring/resource assertions | 73 |
| New SRD interrupted-write/recovery assertions | 6 |
| **Total pure assertions** | **890** |
| Previous Minecraft GameTests | 48 |
| New SRD character/resource Minecraft GameTests | 2 |
| **Total GameTests** | **50** |

No previous tests were removed, weakened or disabled. All executed tests passed; no GameTest failures/errors/skips. Pure counts are assertions, not JUnit test methods. Logs: `/tmp/phase3-c-baseline.log`, `/tmp/phase3-authoring-tests.log`, `/tmp/phase3-c-prerequisites.log`. Structured headless results: `build/gametest-results.xml`.

New coverage includes every form field, catalog choices, no formula inputs, complete schema validation, server-created neutral templates, ignored client approval/HP flags, policy locks, private reads, existing-draft edits, readiness versus engine activation, typed defenses/temporary absorption/healing, zero HP, expected-revision replay rejection, max-HP protection, dirty-workspace preservation, collection conflicts, reload, unbind/rebind and write-before-publish failures.

The first new GameTest calls the **production character request handler** with embedded mock server players: discovery → create → duplicate rejection → unauthorized read denial → typed edit → rejected owner approval → DM approval → real service binding → private refresh. The second uses a real Minecraft entity and server character service to verify persistent D&D HP separation, vanilla capture isolation, reload, rebind, zero-HP eligibility and stale mutation rejection. Neither test creates a live SRD combat encounter, and neither is a remote two-client test.

No graphical client or remote clients were launched. `DISPLAY` is unset and `Xvfb` is not available in this environment. Form layout, camera/HUD rendering and real multiplayer remain **Not Verified**. The Phase 1 and Phase 2 real-client gates remain open. The existing Authlib DNS/test-helper/Gradle warnings remain; remote authentication is not proven by headless tests.

Artifact: `build/libs/tactical-combat-0.1.0.jar`.

```text
SHA-256 4177775a30a7b7374517f55d7016b8fc2ff74659333cae2e40873d47bfe81ca8
```

## Performance and stability

No performance improvement or multiplayer stress result is claimed. Stage B's 4,096-node bound remains covered by existing tests. Character requests retain the existing bounded version-2 messages, request rate/identity guard and private replies. The new operation is additive and advertised before the new client creation control is shown. No combat preview/animation broadcasts were added.

HP commits retain whole-store validation and synchronous atomic writes. They are appropriate to exercise correctness, but per-attack storage latency under many encounters has not been measured. Before activation, test bounded preview frequency, multiple encounters, save failures and server-thread latency; do not remove security limits to improve apparent throughput.

The new guards address two integration hazards: stale whole-collection edits restoring old HP, and direct repository vanilla capture reaching a non-Tactical record. Existing regression tests continue passing. This does not establish the safety of an encounter integration that has not yet been implemented.

## Changed files

- `src/main/java/dev/tacticalcombat/rules/srd/SrdAuthoring.java` — neutral template and typed form model.
- `src/main/java/dev/tacticalcombat/rules/srd/SrdHealth.java` — bounded D&D resource arithmetic.
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java` — runtime CAS persistence, collection conflict/validation checks and profile-isolated vanilla capture.
- `src/main/java/dev/tacticalcombat/character/CharacterSyncService.java` — advertised creation operation and authorized readiness/resource snapshots.
- `src/main/java/dev/tacticalcombat/character/CharacterNetworking.java` — audit logging for the new creation operation.
- `src/main/java/dev/tacticalcombat/client/SrdCharacterFormScreen.java` — paginated typed form and explicit staging/cancellation.
- `src/main/java/dev/tacticalcombat/client/ServerCharactersScreen.java` — New SRD/SRD fields controls and D&D resource/status display.
- `src/test/java/dev/tacticalcombat/SrdAuthoringTests.java` — 73 assertions.
- `src/test/java/dev/tacticalcombat/character/SrdResourceFaultTests.java` — six interruption/recovery assertions.
- `src/test/java/dev/tacticalcombat/FoundationTests.java` — runs both suites.
- `src/gametest/java/dev/tacticalcombat/combat/SrdCharacterLifecycleGameTests.java` — two headless integration tests.
- `src/gametest/resources/fabric.mod.json` — test registration.
- `docs/phase3-completion-status.md` — this current report.
- `docs/phase3-stage-b-checkpoint.md` — continuation link; historical Stage B evidence retained.

## Audit status update

These are current implementation statuses, not amendments to the historical read-only audit's evidence. “Tactical retained” means prior implemented safeguards/tests remain, not that every real-client scenario is closed.

| Finding | Current status / Phase 3 boundary |
|---|---|
| F01 exclusive ownership | Tactical retained; SRD shared lifecycle integration still required |
| F02 attack geometry | Tactical retained; SRD world reach/cover/visibility Deferred |
| F03 action accounting | Tactical accepted-action safeguards retained; SRD preview/confirm transaction Deferred. HP CAS is not a substitute |
| F04 damage boundaries | Tactical retained; SRD active-encounter vanilla/environment suppression not implemented |
| F05 movement authority | Stage B isolated controller implemented/tested; live SRD scheduler/handoff Deferred |
| F06 path validation | Stage B isolated geometry/revalidation tested; live SRD wiring Deferred |
| F07 inventory/interactions | Tactical retained; SRD equipment/action integration Deferred |
| F08 authoritative sheets | Advanced by typed authoring, private derived-status readback and durable SRD resources; still Partial without live SRD combat |
| F09 critical presentation | Stage A attack-specific critical arithmetic retained; authoritative SRD dice/result presentation Deferred |
| F10 unsupported formulas | Existing manual expression tests retained; new form accepts no executable formulas |
| F11 dice/card traffic | Existing bounds retained; new operation private and bounded; no SRD roll broadcast added |
| F12 spells | Deferred; no automated spell/cast-resource scope added |
| F13 persistence | Atomic server character storage retained; SRD HP/revision interruption/reload coverage added. Persistent action receipts/encounters and legacy local-save limitations remain |
| F14 threat overlays | Unchanged Tactical limitations; no live SRD overlay yet |
| F15 performance | Bounds retained; no new profiling/large-encounter throughput claim |
| F16 DM/turn controls | Existing Tactical controls retained; SRD tie/turn/zero-HP adjudication Deferred |
| F17 networking/lifecycle | Existing private character protocol reused and tested; live SRD intent/result compatibility protocol Deferred |
| F18 encounter timing | Unchanged; SRD world timing/environment/condition integration not implemented |

## Practical local checks and continuation

On a disposable copied world with the same pinned client/server dependencies:

1. Run the existing [Phase 1](phase1-multiplayer-checklist.md) and [Phase 2](phase2-multiplayer-checklist.md) checklists; do not treat headless results as closing them.
2. Create/select an SRD campaign and invite a normal player using existing management commands.
3. Open the Server character workspace, choose the campaign, and use **New SRD**. Verify it is a draft under the secure policy.
4. Open **SRD fields**. Edit abilities, equipment, defenses, HP and speed with the form; validate/stage, then **Save changes**. Check invalid numbers/catalog choices and small GUI sizes. Confirm cancelling the form leaves the workspace/local sheet unchanged, and refreshing does not discard unsaved staged edits.
5. Approve as DM, bind through the existing command, refresh as owner, and verify readiness, D&D HP/max/temp/AC/feet and the **engine disabled** notice. Check an unrelated campaign/player cannot inspect the character.
6. Verify bound edits remain rejected. On an unbound copy, test stale revisions and explicit reconciliation. Do not use generic Tactical `values.hp` as a D&D resource control.
7. Recheck manual D20/Percentile, local sheets, shared dice/cards and Tactical combat/camera/grid/HUD/inventory. There is no live SRD attack or encounter to test yet.

Resume from this branch without resetting:

```sh
cd /workspace/Cubetable
git status --short --branch
git log -3 --oneline
/workspace/cubetable-setup/gradle.sh build runGametest
```

Equivalent outside this workspace: `./gradlew build runGametest` with a complete Java 21 JDK. Expected baseline: **890 assertions / 50 GameTests**.

Next implement a shared encounter/authority integration through the existing exclusive index and all damage/interaction/lifecycle hooks; then the compatible SRD client protocol, initiative tie adjudication, Stage B scheduler/grid wiring, server-issued attack intent/confirmation, action/resource commit coordination and restricted zero-HP policy. Only after the full Stage C test gate passes should Stage D targeting/cover/visibility and Stage E end-to-end acceptance proceed. Keep `worldCombat()` disabled until the activation criteria are met. The currently playable scope remains Minecraft Tactical and manual tabletop; **there is still no playable automated SRD encounter scope**.

Phase 4 entry requires completed C/D/E automated gates, an explicit minimum SRD encounter safety policy and truthful tracking of outstanding real-client acceptance. Do not begin later classes/spells/reactions/death systems or push Phase 3 without authorization.
