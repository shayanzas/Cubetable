# Gameplay usability and SRD reference catalog — J2.5 A–C

## Delivery identity and scope

- Verified starting commit: `40dc9a590f0be4f0b86a99a4b0dcdbe02e56e9f2`, J2.
- Branch: `dev/gameplay-usability-catalog-checkpoint-j25`, destination `shayanzas/Cubetable`.
- Encounter implementation: `f8f4d8c`; catalog schema/validator: `8ed9511`; source-population batch: `23ac813`; gameplay interface/final regression implementation: `f382557`.
- Final delivery commit is the commit containing this report; resolve with `git log -1 --format=%H -- docs/gameplay-usability-catalog-checkpoint-j25.md` on the delivered branch. A commit cannot embed its own hash.
- Separate reviewable commits cover encounter usability, catalog foundation, catalog batches, interfaces and documentation. Catalog foundation precedes the client reference consumer so their dependency is explicit.
- Requested model: Sol 6.1 Medium. The session provides no tool to switch or independently verify its model; no verified model-switch claim is made.

**A and B are implemented and automated checks pass. C delivers a validated source-verified catalog batch, with incomplete category coverage explicitly recorded.** There are 623 reference entries across all 18 categories, including all 319 printed spell descriptions. This is not a claim of a mechanically complete SRD or complete structured class progression/monster/magic-item catalogs. D and E are deferred. Graphical Minecraft acceptance remains pending; no graphical client was available.

No merge, force-push, upstream modification or primary-world installation is part of this delivery. Only the requested branch is pushed. No real saved world was opened or converted.

## A — Encounter usability

### Safe standing positions and alignment

`SrdEncounter.validateAdmission` now accepts supported ordinary standing positions using `SrdTerrain.admissionCell/alignment`. Discovery is side-effect-free. The closest horizontal grid anchor is selected deterministically at the actor's existing Y height. At confirmed encounter creation, movement is bounded to **0.5 blocks per horizontal axis**, at most approximately **0.707 blocks total**, with **no vertical correction**.

The server validates the complete logical footprint (at least the physical body), a conservative swept rectangle from the standing position to the anchor, full floor support, block clearance, fluids/hazards, loaded chunks, world border and living-entity occupancy. Actors cannot cross walls, share occupied logical space or snap onto unsupported terrain. Large/uneven footprints and unsupported modes still fail closed. Tight spaces may be rejected even when a narrow physical mob could fit: the D&D logical footprint is deliberately retained.

The entire participant set is checked before ownership/position changes. Each alignment is checked again while acquiring the set; any failure releases claims, restores saved original positions/velocities and restores the prior AI-disabled state. A begin/watch failure through the launcher also restores original positions/velocities after cleanup. Accepted admission stops existing navigation/use interactions as the prior encounter implementation did; it is not a promise to reconstruct a cancelled navigation path. Normal AI resumes under its previous enabled/disabled policy after cleanup.

Successful anchors are the same centers consumed by `SrdTerrain.currentCell`, encounter anchors and subsequent movement consistency checks. No client coordinate or arbitrary teleport target is accepted. Diagnostic encounter creation uses the same server path. This is synchronous admission rollback, **not** atomic storage with Minecraft entity/chunk save files or crash-resumable positioning; J2's restart policy remains unchanged.

### Discovery, filters and quick binding

Nearby discovery now collects loaded living actors in the 32-block sphere, sorts by exact server squared distance and breaks ties by UUID. Sorting happens before truncation. Both encounter discovery and the eligible-entity binding picker support server-scoped **All / Bound / Unbound / Eligible** and **All / Player / NPC** filters before applying the 64-row result limit. Dense unrelated creatures can no longer hide matching bound actors merely because of enumeration order. Responses explicitly flag truncation.

Rows lead with name/distance and show player/NPC type, authorized bound character, readiness/rejection and selection state. Coordinates/type and longer reasons are available in tooltips. Start Encounter is prominent. Two-to-sixteen distinct participants, current selected campaign, approval, client capability, current entity/character state, controlled free-for-all rules and existing initiative/tie flow remain enforced. Discovery tokens remain one-use, short-lived and server-revalidated.

Unbound nearby NPCs offer **Bind Character**. `ACTOR_CHARACTERS` lists approved, unbound, unreserved SRD characters in the current managed campaign, with current revisions and owner/controller membership. Selection and confirmation use the existing authenticated `BIND` service, not a local mutation. Binding rechecks character revision, approval, controller, membership and combat locks; the service now explicitly rejects a prepared reservation on the chosen character. The picker returns to refreshed discovery. No Minecraft mob is automatically assigned a monster stat block or generated mechanics.

## B — Common gameplay interfaces

### Player Quick Sheet and roll menus

Default **J** opens the compact, non-pausing Quick Sheet for the character actually bound to the player. Selected and bound remain separate. The shared `CompactSheet` renders identity/skin header, six statistic cells (HP/temp HP, AC, speed, initiative, proficiency), responsive ability tiles, weapon summary and virtual stock. The authorized J1 projection supplies supported values. Secondary actions scroll rather than expanding a plain-text document.

Full Sheet, watched Combat Controls, Manual Dice, recorded Abilities/Skills/Saves and SRD Reference shortcuts are available. The roll menu shows ability totals and recorded skill/save breakdowns, with explicit informational wording. **No enabled official check button delegates to a client-selected manual modifier.** Authenticated character checks belong to D.

### Manual dice toolbar

Default **V** opens/closes a compact interactive dice toolbar above the vanilla hotbar/health/control area. It supports d4/d6/d8/d10/d12/d20/d100, quantity 1–10, manual modifier -999…999, normal rolls and one-d20 advantage/disadvantage. Roll Window expands the view; Compact returns it. Pin saves a local version-1 preference at `config/tacticalcombat/dice-toolbar.json`; Escape closes interaction while a pinned result strip remains passive. A passive strip never captures movement keys or the cursor. All keys remain independently configurable.

Requests use existing `DiceRequestPayload` and `DiceService`; the server owns the final faces and bounded manual modifier. The toolbar displays returned faces, keep policy, modifier and total. It does not introduce RNG, official character checks, private rolls or cinematic animations. Current manual audience remains nearby compatible players or encounter peers, explained in the UI. Disconnect clears displayed results. Existing commands, manual sheets, DiceAnimation, HUD events, skip controls and privacy behavior remain available.

### DM Workspace

Assign the independent **DM Workspace** key under Minecraft Controls (initially unbound), or open Cubetable → DM Tools → DM Workspace. Five sections organize real existing operations:

- **Party:** authorized campaign summaries with available HP/AC, recorded conditions, approval and binding status. Up to six individual inspection tabs retain identities and fetch current authorized character data; no private-sheet cache is carried between tabs. Full-sheet and character-management shortcuts remain available.
- **Actors:** nearest/filterable actor discovery, approved-character quick binding and a monster-reference shortcut. No monster activation is implied.
- **Encounters:** participant creation, active encounters, existing watched initiative/combat controls, confirmed ending and J2 Transaction Review. Ending retains committed resources.
- **Scene:** read-only top-down discovery map, server location/dimension, public actor coordinates and eligibility tooltips. It previews the nearby area; no fake Save Stage button or persistent placement service exists.
- **Rolls:** existing authorized history and permitted manual DM dice. DM-only/targeted/forced requests are explicitly unavailable pending D.

The manager/selected-campaign watch clears privileged content and invalidates callbacks on revocation. Character inspections also use current authorized reads. Panels render the live world behind them and scroll in constrained/narrow layouts. Escape returns through the active parent. Main Cubetable management remains available. Tabletop Tools now also exposes Manual Dice and SRD Reference directly.

### Campaign character management

The common view now leads with identity/level, approval/binding, owner/controller and state-relevant Open Sheet, Select, Approve/Request Changes and Bind/Unbind actions. **Advanced** retains ownership/control offers, assignment clearing/offers, rests, activity, source-library navigation, readiness and technical inspection. Supported actions were not deleted. Current revision/permission checks remain server-side. The screen subscribes to character invalidation so revoked access clears displayed data.

## C — Catalog batches and licensing

See [category coverage](srd51-catalog-coverage.md) for the exact inventory, limitations and 14 unmatched legacy feature summaries. Static version-1 resources use stable source-qualified IDs, source pages, text-verification provenance, category metadata and validated related IDs. They are separate from character records and executable rules. Existing authoring choices, race/class icons and reference summaries remain intact; matched features retain legacy reference IDs.

Counts: 319 spells, 8 spell lists, 15 conditions, 37 weapons, 13 armor/shield entries, 133 equipment price/weight rows, 9 races, 4 subraces, 12 class identities, 12 subclass opening sections, 47 features, 3 monsters, 3 magic items, 2 combat sections, 2 movement sections, 2 rest sections, 1 advancement section and Grappler.

Rules text is checked against the user-uploaded 403-page SRD 5.1 PDF. Distribution elects **CC BY 4.0**, following the user's official dual-license confirmation. OGL legal pages/running notices are excluded; the uploaded file itself is not relabeled. Attribution, source hash, changes and URLs are packaged and displayed. Official and archived CC URL retrieval returned HTTP 403, so independent CC-edition comparison remains explicitly pending in provenance. No 5.2 or non-SRD content was substituted, and PDF access did not block the independent UI work.

Spells retain level/school, casting time, range, components, duration/concentration, ritual flag, class availability, scaling and descriptions. Weapons/armor preserve table metadata. Goblin, Skeleton and Zombie retain stat blocks, defenses, ability scores, senses/languages and traits/actions, but remain reference-only. Classes are identity/Hit Die entries, not complete progression tables. Feature minimum levels/class links are not complete grant predicates. Other incomplete categories are clearly identified; no whole-SRD 100% claim is made.

The maintainer extractor is checksum-locked to the supplied source. Re-running it after adding direct source checks for class Hit Dice and race size produced byte-identical catalog resources. No PDF parser/network dependency was added to Minecraft or ordinary builds.

## Compatibility and handoff

Library schema **2**, campaign schema **5**, SRD combat **v1**, G creationClosed protections, H independent characters/management and J1 projections remain intact. No character conversion, migration or new transaction/effect type is introduced. J2 PREPARED/COMMITTED receipts, retention bounds, fixed outcomes, atomic participant resources, restart policy and movement-spent behavior remain covered by the unchanged durability suite.

Use a complete coherent backup before upgrading a real world. J2's rollback warning still applies: rolling back to a pre-schema-5 binary requires its coherent pre-upgrade world backup, not merely an old JAR. This delivery does not install anything into the user's primary world.

[Authoritative rolls/stage handoff](j25-authoritative-rolls-stage-handoff.md) documents D's intent/result/privacy needs, stage service boundary, monster adapters and catalog assumptions. No new checks, class/spell/equipment mechanics, forced roll requests, saved stages or cinematic dice pass are represented as implemented.

## Automated verification

Java 21.0.12.1, Minecraft 1.21.1, existing Fabric/Loom dependencies:

```bash
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog
```

Final result: **BUILD SUCCESSFUL in 1m 9s**, 2026-10-16. Evidence: `/tmp/j25-final-validation.log`, `build/gametest-results.xml`, `build/reports/srd51-catalog.json`.

- **16,948 foundation assertions across 37 suites**: all 14,208/35 J2 baseline checks retained, plus 126 gameplay/filter/layout/dice-expression checks and 2,614 catalog checks.
- **135/135 GameTests passed**, zero failures/errors. All prior 132 remain; two encounter-usability tests and one server-dice test were added. Existing admission assertions were updated to check the intentionally changed safe-alignment behavior, not deleted.
- Real server tests cover off-center start, original position/AI/claim rollback, overlapping actors, obstructed swept footprint, unsupported floor, nearest filtering before the 64-row cap with 66 irrelevant creatures, approved-character quick binding, malformed filters and server dice faces/keep/count/modifier bounds.
- Dedicated `validateSrdCatalog` passed **2,614 checks / 623 entries** and writes category coverage JSON. Its repeated execution is not double-counted as additional foundation assertions. It verifies parsing, fields/types, uniqueness, source/attribution, references, levels, damage/properties, full inventoried table/heading coverage and reciprocal class lists; negative fixtures reject malformed data.
- All J2 real-filesystem fault, abrupt-child-process exit, receipt/replay, permission, paired-resource and movement checks remain enabled. J1/J1.5, G/H, library and Tactical regressions pass.
- `git diff --check` is clean. Existing mock-player/Gradle deprecation and Minecraft public-key host warnings do not represent graphical acceptance.

Earlier runs identified the expected old strict-center assertion and a catalog test fixture that removed a non-caster instead of a referenced class; both were corrected to assert the intended behavior. No test suite was removed or disabled. Graphical Minecraft, actual key/focus feel and multi-client screenshots remain pending user testing.

## Minecraft acceptance checklist

Use a disposable copy with matching client/server JARs, Java 21 and current Fabric dependencies; retain a coherent backup.

1. Select the test campaign as DM. Use an approved bound player and Zombie on ordinary safe floor, slightly off-center. Open New Encounter, filter Bound/Eligible and Player/NPC, select both and Start. Verify only a small horizontal alignment, initiative/tie flow and existing combat controls.
2. Repeat near a wall, unsupported floor and overlapping actors. Unsafe starts must reject without repositioning or leaving combat claims/changed AI. Larger logical footprints remain conservative.
3. Discover several nearby creatures at different distances. Confirm nearest-first order, distances and coordinate tooltips. Check Bound/Unbound/Eligible filters. Select an unbound NPC → Bind Character → approved available character → confirm; verify refreshed discovery and no generated monster statistics.
4. Confirm normal attacks/movement, HP/ammunition, end/reopen resources and J2 Transaction Review still work. Do not corrupt a real save; automated fault tests cover failures.
5. Press J. Compare compact HP/AC/speed/abilities with the full authorized sheet. Change selection without rebinding and confirm the bound sheet remains the default. Check scrolling and Abilities/Skills/Saves informational breakdowns; no official check should execute.
6. Press V. Roll every die, change quantity/manual modifier, use one-d20 advantage/disadvantage, inspect faces/total, switch Roll Window/Compact and pin/unpin. Close with Escape; passive pin must leave normal movement/mouse controls intact and stay above vanilla controls. Check after reconnect.
7. Assign the DM Workspace key. Exercise Party pages and multiple inspection tabs, full-sheet links, Actors, Encounters/initiative/end confirmation, Scene map and Rolls/history. Scene must not save/move a stage, and Rolls must not fake private/forced checks.
8. Revoke DM permission/change selected campaign while a private panel is open. Verify contents clear/revalidate; ordinary players must be denied NPC/DM inspection and keep access only to their own authorized bound Quick Sheet.
9. Open campaign management. Verify common actions follow approval/binding state and all less-common workflows remain in Advanced, with stale revisions rejected.
10. Open Tabletop Tools → SRD Reference (or Quick Sheet/DM Actors). Search spells and monsters, inspect Zombie and reference-only notices/attribution. Confirm no reference entry changes a saved character or activates an unsupported ability.
11. Test GUI scales and narrow windows, Escape/return paths, long names, search, keyboard text focus and normal world rendering. Smoke-test G/H creator/library protections and Tactical behavior.
12. Report graphical results before D/E or any primary-world upgrade.

## Artifact, limits and changed files

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `9b98c16e96bc9265f11e307ce00cf374a24f5566786acd97a248e69e015e4144`.

Known limits: graphical acceptance pending; conservative alignment may reject tight/uneven spaces; discovery is limited to loaded nearby actors and may be expensive in extremely dense areas; manual dice use the existing audience/engine, not authenticated checks; scene state is not persistent; catalog batches/structured progression are incomplete; independent CC-edition comparison is pending. J2's 1,024-receipt hard stop and single-file durability limits still apply.

- `build.gradle`
- `docs/gameplay-usability-catalog-checkpoint-j25.md`
- `docs/j25-authoritative-rolls-stage-handoff.md`
- `docs/srd51-catalog-coverage.md`
- `src/gametest/java/dev/tacticalcombat/combat/EncounterLauncherGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/ManualDiceGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/character/CharacterService.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/client/ui/ActorBindingScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/BindingPickerScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignCharacterScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CharacterRollMenuScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CompactSheet.java`
- `src/main/java/dev/tacticalcombat/client/ui/DestinationScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DiceToolbar.java`
- `src/main/java/dev/tacticalcombat/client/ui/DiceToolbarLayout.java`
- `src/main/java/dev/tacticalcombat/client/ui/DiceToolbarScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DmWorkspaceScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/EncounterLauncherScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/QuickPanelScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ReferenceCatalogScreen.java`
- `src/main/java/dev/tacticalcombat/combat/EncounterLauncher.java`
- `src/main/java/dev/tacticalcombat/combat/NearbyActors.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/grid/SrdTerrain.java`
- `src/main/java/dev/tacticalcombat/reference/SrdReferenceCatalog.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/main/resources/SRD-5.1-NOTICE.txt`
- `src/main/resources/assets/tacticalcombat/lang/en_us.json`
- `src/main/resources/data/tacticalcombat/reference/catalog-v1.json`
- `src/main/resources/data/tacticalcombat/reference/source-inventory-v1.json`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/ui/CheckpointJ25Tests.java`
- `src/test/java/dev/tacticalcombat/reference/CatalogValidationTests.java`
- `tools/build_srd51_reference.py`
