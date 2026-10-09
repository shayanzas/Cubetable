# Phase 3 checkpoint — Stage A SRD 5.1 calculations

October 9, 2026 (UTC). Repository: `shayanzas/Cubetable`.

Continuation: [Stage B movement checkpoint](phase3-stage-b-checkpoint.md). This document retains the historical Stage A implementation and test results.

## Publication and local checkpoint

Phase 2 was verified before publication: Java 21 `build runGametest` succeeded, with **423 assertions and 35 Minecraft GameTests**. The working tree was clean, commits `91437ac` and `6b28e42` were present, and origin was `https://github.com/shayanzas/Cubetable.git`. The remote development branch did not yet exist.

Executed the explicitly authorized command:

```sh
git push -u origin dev/phase2-character-foundation
```

Publication succeeded. A subsequent `git ls-remote --heads origin dev/phase2-character-foundation` returned exactly:

```text
6b28e42caeb447ba3578cac60c0d0b58e6e4e5a0
```

Published branch: https://github.com/shayanzas/Cubetable/tree/dev/phase2-character-foundation

Created **`dev/phase3-srd-combat` directly from that exact commit**, without modifying Phase 2. Local implementation commit:

```text
0d5b1f66eaf6b9b1712d301e6c57470b9f7dc328
Begin Phase 3 with validated SRD calculations and authoritative character adapter
```

This report is a subsequent documentation-only commit; use `git log -2 --oneline` to identify it. **Phase 3 has not been pushed.** No main/upstream branch, dependency version, existing pack, client camera, HUD or combat action path was changed. No force-push, merge, reset or reclone occurred.

## Readiness and requirement status

This is the independently verified **Stage A calculation/adapter checkpoint**, not a playable D&D combat release. The user explicitly authorized stopping at a smaller tested stage. Core arithmetic is ready for movement and action integration; **SRD automated world combat remains disabled**. Do not start Phase 4 or claim full SRD compliance.

| Requirement | Status at this checkpoint |
|---|---|
| Verify and publish Phase 2; create separate Phase 3 branch | Implemented; remote exact SHA checked |
| Preserve existing Tactical/manual behavior | Existing automated regression coverage passes; real clients Not Verified |
| Stage A typed SRD policy, validation, ability/proficiency/AC/weapon/attack/damage calculations | Implemented as pure rules and a server character adapter |
| Approved character authority | Implemented adapter; requires approved SRD binding, matching campaign, supported profile version and current owner membership |
| Character authoring/management integration for new SRD fields | Partially Implemented: existing collection editing/storage works; no SRD pack, specialized authoring UI or derived-stat display |
| Initiative | Partially Implemented: Dex plus bonus and explicit unresolved tie groups; encounter roll/storage/order/adjudication UI not connected |
| Stage B movement | Deferred to next checkpoint; only feet-to-square budget arithmetic and ordinary armor speed penalty implemented |
| Stage C attack preview/confirmation/action/HP/network integration | Deferred; pure target-independent preview data and attack calculation are not live actions |
| Stage D range, reach, cover, visibility, logical spaces | Deferred; equipment range/reach metadata only, no world targeting changes |
| Advantage/disadvantage and typed defenses | Implemented arithmetic; world-derived causes and conditional defenses Deferred |
| Persistent D&D HP, healing, temporary HP use | Partially Implemented inputs only: bounded distinct HP/max/temp values persist in approved sheet; runtime updates/application/healing Deferred |
| Zero HP and environmental safety | World automation disabled; pure attack planning rejects HP=0. No D&D unconsciousness/death/environment conversion claimed |
| Multiplayer roll presentation and DM visibility | Deferred; no new combat packets or roll broadcast added |
| Stage E verification/reporting | Implemented for Stage A; full Phase 3 stabilization Deferred |
| Phase 1/2 graphical and two-client gates | Not Verified; remain open independently |
| Classes, reactions, spells, conditions, death saves, encounter recovery | Deferred to approved later phases |

## Architecture and authoritative inputs

`rules/srd` is a separate, Minecraft-independent calculation package. `SrdRulesProfile` replaces the inert reserved SRD profile with a typed policy but keeps `worldCombat() == false`. Generic `Map<String,Double>` movement/initiative entry points reject use with clear errors rather than accepting Tactical statistics. Tactical and Manual implementations remain unchanged.

`SrdCharacterCodec` reads an explicit opt-in schema:

```text
sheet.collections.srd_combat = [ { version: 1, ...required SRD fields... } ]
```

The array must contain exactly one object. The complete executable example is [the test fixture](../src/gametest/resources/srd-test-character.json). It is test data, not a new game pack or a survival-ready character template. Its existing `generic_d20` format identifier preserves compatibility with the sheet envelope; it does not enable D&D rules for other Generic D20 sheets.

Every schema field is required. Six named ability scores, level, initiative bonus, HP/max/temp, speed in feet, size, reach, armor/shield, proficiencies, weapon availability, explicit additive bonuses and damage-defense sets have strict types and bounds. Missing/unknown fields, derived AC/proficiency/modifiers, formulas, unknown catalog entries, duplicate set entries, fractional numbers, stringified numbers and unsupported schema versions fail validation.

Ability scores are 1–30, level 1–20, HP max 1–1,024, HP 0–max, temporary HP 0–1,024, speed 0–300 feet, reach 5–30 feet in multiples of five, initiative/attack/damage bonuses −100..100, AC bonus −20..20 and resulting AC 0–100. These are explicit implementation bounds, not claims that SRD defines all these limits. Sets are bounded by their catalogs. Existing sheet size/nesting limits still apply. No formulas execute.

**No automatic conversion:** generic `values.hp`, `values.ac`, `values.dex`, `values.prof`, `movement_blocks` and Minecraft attributes are not inputs to this adapter. Do not enter D&D HP through the old generic `stat hp` command and expect it to change `srd_combat.hp`. Specialized SRD authoring and synchronized display belong to the next integration work.

`CharacterRepository.srdSnapshot(actor, expectedCampaign)` resolves the binding from server storage and returns immutable character/actor/campaign identities, edit/runtime revisions and validated inputs. `CharacterService.srdStats(entity, campaign)` is the server-world adapter. Neither accepts client approval flags or client-derived totals. These are **trusted internal server APIs**, not authorization for a client request; the future action gateway must still validate requester ownership, encounter membership, turn, equipment, geometry and request identity.

Existing server character discovery/readback remains permission-filtered through Phase 2. No new endpoint exposes this private snapshot. Existing DM approval, owner edit policies, bound-edit prohibition and revision checks protect the whole `collections.srd_combat` field. Parsing alone does not confer approval.

Legacy or incomplete SRD drafts/approved bindings are preserved rather than migrated or deleted. Their new combat-input lookup fails clearly if the explicit schema is absent or invalid. Submission/approval itself does not yet enforce this optional SRD schema; **approval alone is not proof a legacy SRD character can be automated**.

## Implemented rules and limits

Rules reference is the attached **2014 SRD 5.1**, not SRD 5.2 or 2024 rules.

| Calculation | Behavior / SRD reference |
|---|---|
| Ability modifiers | Floor `(score − 10) / 2`, including negative odd results; p. 76 |
| Proficiency | Level-derived +2 through +6; applied once to proficient weapon attacks, not damage; character advancement and p. 77 |
| Initiative | d20 + Dex modifier + explicit bonus; no automatic success/failure on 1/20; p. 90 |
| Initiative ties | Group equal totals without silently favoring players, Dex or UUID. Future gateway must obtain player order for tied PCs and DM determination for GM/mixed ties, per p. 90. Grouped identical-monster rolling is not connected |
| AC | Unarmored 10+Dex; light adds Dex; medium caps only positive Dex at +2; heavy ignores Dex; one shield +2; explicit bonus once; pp. 63–64 |
| Armor penalties | Nonproficient armor/shield imposes attack disadvantage; heavy armor Strength requirement reduces walking speed by 10 feet; pp. 62–64. Racial/class exceptions, checks, spellcasting and Stealth penalties are not automated |
| Weapons | Unarmed, dagger, longsword, shortsword, greatsword, shortbow, longbow, plus light crossbow; pp. 64–66, 95 |
| Weapon ability | Strength melee, Dex ranged, selectable Strength/Dex finesse used for both attack and damage, dagger thrown retains its melee/finesse ability |
| Hands/properties | Versatile die, required two-handed use, shield-hand exclusion, Small heavy-weapon disadvantage. Light/ammunition/loading are catalog metadata, not resource/action automation |
| Attack | Total equal to AC hits; selected natural 1 misses and 20 hits/criticals; pp. 94–95 |
| Critical | Double damage dice, not flat modifiers; unarmed 1+Strength remains flat even on a critical; pp. 95–97 |
| Advantage | Roll two and select high/low; any advantage plus any disadvantage cancels to one d20; p. 76 |
| Damage | Nonnegative typed results. Aggregate same-type components of one simultaneous effect, apply immunity/resistance/vulnerability, resistance before vulnerability, round halves down; pp. 96–97 |

Armor examples are leather, studded leather, chain shirt, scale mail, chain mail and plate. Equipment is a single approved virtual catalog choice per calculation, preventing duplicate physical/virtual bonus application. No Minecraft item mapping is inferred; physical equipment, ammunition consumption, loading action limits, swapping, magic/silvered defenses, offhand attacks, feats and custom weapons need future adapters. Unsupported improvised attack modes are rejected rather than guessed. Monster CR-derived proficiency and natural armor/templates are not provided by this level-based character adapter.

`AttackPlan` is target-independent and includes weapon, mode, chosen ability, attack modifier, damage dice/flat/min/max, intrinsic disadvantage and reach/range metadata. It includes no enemy AC, defenses or HP. It is not an in-game preview screen or network payload.

`SrdRules.attack` accepts an injected dice source; deterministic tests use fixed faces, and integration tests invoke it on the server. It does not use client roll totals. **There is no production action caller yet.** The future caller must supply server RNG only after atomic action acceptance and spend a miss exactly once. These pure calculators do not replace or bypass Phase 1 authority.

## Movement, HP and persistence boundaries

Speed arithmetic uses the approved **one block = five feet** scale: 30 feet permits six complete squares; fractional remaining squares round down. Tactical movement is untouched. Eight-direction pathfinding, equal-cost diagonal/ascent traversal, logical large footprints, live revalidation, terrain/occupancy checks and grid previews are **not added in this checkpoint**. Existing Tactical terrain tests remain unchanged and passing; they are not evidence of SRD pathfinding.

SRD HP/max/temp are distinct fields persisted verbatim in the existing approved sheet under schema-2 atomic storage. No new world HP pool, damage application, healing, temp-HP absorption, runtime persistence mutation or HP synchronization is wired. The generic Phase 2 status UI still shows generic repository stats, not these new SRD values. Stage C must unify the server D&D runtime pool, revisions and client display before activation; do not create separate exploration/combat pools or silently copy vanilla health.

GameTests verify that SRD binding does not add Tactical max-health modifiers or change vanilla current HP, and vanilla HP capture does not overwrite the SRD fields. Pure damage calculation does not mutate either pool. This is **isolation**, not a completed D&D survival-health integration. Vanilla damage can still occur in the world; do not use this branch as a D&D survival mode. The safe present gate is disabled SRD encounters, plus rejection of zero-HP actors by the pure attack planner. A controlled zero-HP test-encounter pause policy is still needed when Stage C enables actual damage; unconsciousness/death saves are later work.

No storage-schema bump or destructive migration was needed. New data uses the existing bounded collection envelope, approval/reconciliation and atomic write machinery. Tests cover explicit SRD inputs and binding identity through repository reload. Crash-time runtime HP capture and full encounter restart recovery remain unresolved as documented in Phase 2; encounter restoration belongs to Phase 7.

## Verification evidence

Final command:

```sh
/workspace/cubetable-setup/gradle.sh build runGametest
```

**BUILD SUCCESSFUL in 24s**, Java 21; compilation and packaged mod succeeded. Versions unchanged: JDK 21.0.12.1, Gradle 8.10.2, Loom 1.8.13, Minecraft 1.21.1, Loader 0.16.9, Fabric API 0.116.1+1.21.1.

| Suite | Passing count |
|---|---:|
| Existing foundation assertions | 320 |
| Existing character repository assertions | 39 |
| Existing character synchronization assertions | 58 |
| Existing persistence interruption assertions | 6 |
| New SRD assertions | 319 |
| **All pure assertions** | **742** |
| Existing Minecraft GameTests | 35 |
| New SRD Minecraft GameTests | 3 |
| **All Minecraft GameTests** | **38** |

Zero failed/error/skipped GameTests; structured report has 38 testcase entries. No existing tests were removed or disabled. Pure counts are assertions, not JUnit test methods. Graphical/manual scenarios were **not run**, rather than counted as passing or skipped automated tests.

New tests cover all 30 ability scores and 20 proficiency levels; armor Dex behavior; initiative extremes/bonuses/ties/independent calculations; selected d20 behavior; a complete 1–20 attack-vs-AC table; critical dice; finesse/versatile/hands/proficiency; defense rounding/type aggregation; zero HP; strict malformed-data rejection; approved bindings; private access; stale edits; approval invalidation; profile/campaign mismatch; and persistence. Three headless GameTests exercise the real character/world adapter, vanilla HP isolation, malformed/wrong-profile bindings and the disabled SRD encounter gate. They do not exercise new SRD action packets, action expenditure, cover or movement, because those do not exist yet.

Final log: `/tmp/phase3-checkpoint-verification.log`; report: `build/gametest-results.xml`. Development test classes and the JSON fixture are absent from the release JAR. The SRD attribution notice is present.

Built artifact: `build/libs/tactical-combat-0.1.0.jar`.

```text
SHA-256 75da3bff722d623cbcac34b05a32f0f1bf6ac127e90eb180aa47111ea41614c4
```

The existing deprecated mock-player/Gradle warnings and headless Authlib public-key DNS warning remain; real remote authentication is not established by these tests. No Phase 1/2 automated regressions were observed. Graphical rendering, real clients, latency and mod interoperability remain Not Verified.

## Changed files

- `src/main/java/dev/tacticalcombat/rules/srd/SrdCharacter.java`: immutable validated SRD inputs and derived AC/speed.
- `src/main/java/dev/tacticalcombat/rules/srd/SrdCharacterCodec.java`: explicit bounded version-1 collection adapter.
- `src/main/java/dev/tacticalcombat/rules/srd/SrdEquipment.java`: weapons, armor, properties and damage catalogs.
- `src/main/java/dev/tacticalcombat/rules/srd/SrdRules.java`: deterministic-injectable calculations and target-independent plan.
- `src/main/java/dev/tacticalcombat/rules/srd/SrdRulesProfile.java`: typed, disabled-for-world-combat policy.
- `src/main/java/dev/tacticalcombat/rules/RulesProfiles.java`: register typed SRD policy only.
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java`: approved binding/campaign/revision snapshot lookup.
- `src/main/java/dev/tacticalcombat/character/CharacterService.java`: server-world adapter entry point.
- `src/main/resources/SRD-5.1-NOTICE.txt`: attribution included in mod JAR.
- `src/test/java/dev/tacticalcombat/SrdRulesTests.java`: 319 new assertions.
- `src/test/java/dev/tacticalcombat/FoundationTests.java`: run the new assertion suite.
- `src/gametest/java/dev/tacticalcombat/combat/SrdFoundationGameTests.java`: three headless integration tests.
- `src/gametest/resources/fabric.mod.json`: test registration.
- `src/gametest/resources/srd-test-character.json`: explicit schema fixture.
- `docs/phase3-checkpoint.md`: this checkpoint, limitations and resume instructions.

## Manual acceptance and resume instructions

The open [Phase 1 two-client checklist](phase1-multiplayer-checklist.md) and [Phase 2 two-client checklist](phase2-multiplayer-checklist.md) still apply. No graphical clients were launched in this headless cloud environment; mock players are not real two-client acceptance.

For local regression testing, use a copied/disposable Minecraft 1.21.1 Fabric world and the built JAR on both server and clients, retaining the same dependency versions and existing packs. Run the existing Tactical combat/camera/grid/HUD/inventory scenarios, then Phase 2 campaign/character approval, binding, refresh/conflict, disconnect/respawn and small-GUI checks. Confirm manual D20/Percentile sheets and dice/cards remain usable. A campaign set to `srd_5_1` must still refuse automated world combat. **There is no new D&D attack UI to test yet.** Record actual results instead of closing either gate from headless tests.

Resume without recloning or resetting:

```sh
cd /workspace/Cubetable
git status --short --branch
git log -3 --oneline
/workspace/cubetable-setup/gradle.sh build runGametest
```

On a complete Java 21 JDK outside this environment, use `./gradlew build runGametest`.

Next implement **Stage B** on the existing Phase 3 branch: profile-specific eight-neighbor paths/previews with five-foot costs, one-block rise/descent, slabs/stairs, blocked corners, entity clearance plus logical creature space, occupancy, stale-path revalidation and interrupted-movement accounting. Keep Tactical cardinal movement independent. Validate Stage B before connecting Stage C.

Then implement Stage C's bounded preview/confirm protocol and accepted-action flow using Phase 1 identities and Phase 2 permissions; server RNG; persistent D&D HP/runtime revisions; privacy-filtered results; and an explicit safe zero-HP testing policy. Stage D must supply world range/cover/unseen targeting and equipment resource rules. Do not enable SRD encounters merely by flipping `worldCombat()` before those safety dependencies are verified. Finish Stage E and real-client acceptance before release readiness or Phase 4. Request separate authorization before pushing Phase 3.

## Reference and attribution

This work includes material taken from the System Reference Document 5.1 (“SRD 5.1”) by Wizards of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode. Selected equipment data and rules have been adapted into incomplete software calculations with explicit product validation bounds; no endorsement is implied.
