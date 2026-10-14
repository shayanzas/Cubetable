# Cubetable — UI Checkpoint E

Checkpoint E implements the requested creator save workflow, compatible ability-method changes, revision-scoped Point Buy confirmation, and targeted presentation refinements. Automated verification passed. Graphical acceptance remains with the user.

**Minecraft visual verification: NOT PERFORMED.**

## Branch and delivery

- Repository: `shayanzas/Cubetable`.
- Branch: [`dev/ui-character-sheet-checkpoint-e`](https://github.com/shayanzas/Cubetable/tree/dev/ui-character-sheet-checkpoint-e).
- Confirmed local/remote D base: `010a27c33fa6e1f983e3c37d105f868bf0e39903`; the initial working tree was clean and E was created from this exact commit.
- Final tested implementation commit: **`1adac2e3c383bda54f137e0010a5a246e869adf9`**. The report-only delivery commit above it is identified by its exact hash in the delivery message and the branch link. It does not change the tested installable JAR.
- Logical commits: `7b3d597` Point Buy/server confirmation; `78f384a` save workflow and UI; `28b4c23` class-source cleanup; `b87f162` regression coverage; `1adac2e` preserve approved Ranger after audit; then this report.
- Push only E to the user's repository. No reset, force-push, merge, upstream modification or pull request is part of this delivery.

## Implemented refinements

| Request | Implemented behavior |
| --- | --- |
| Save Draft / Save & Continue | The root creator, Proficiencies, Inventory, Features/Spells record pages, Details and their library record editors share adjacent Back / Save Draft / Save & Continue actions. Review uses Finish Character. A shared completion gate accepts only checked server acknowledgements before continuation; failures retain edits and do not navigate. Successful library writes must acknowledge the requested character and exact next revision. |
| Draft incompleteness | Structurally valid incomplete selections and underspent Point Buy allocations save and advance. Ineligible/excess class choices, invalid scores and overspending still reject. Features/Spells lists have no unsaved local records: their Save Draft/Continue fetches confirmed server state; changes happen through the real record editors. |
| Back and tabs | Back retains root identity/class/ability buffers when moving to an earlier root step; specialized editors retain explicit discard confirmation. Root tabs preserve those buffers, so an invalid staged ability score cannot trap the player away from its editor. Entering server-backed child pages saves valid identity/class context first. Navigation itself does not certify persistence of unrelated staged values; the shared bottom status marks unsaved changes. |
| Newer edits / conflicts | Serial acknowledgements preserve newer typing and save it before a pending continuation. A lost reply is reconciled through the next authoritative read. Proficiency/story reloads recognize values already saved after a lost reply; untouched story fields merge fresh server values. Currency replacement confirmation checks the reviewed revision again, rather than authorizing an unrelated later change. |
| Footer geometry | Specialized and nested record pages use the same outer creator frame. Refresh is a smaller secondary action. Narrow creator viewports use taller wrapped navigation labels and reserve content clearance; Refresh moves to the header there. Inventory additions and Tools/Languages are removed from the navigation group. |
| Identity | Preview → race name → description → applicable subrace → traits is retained. Inline repeated save status is removed from the description area. Base buttons use “Base Elf — No Subrace” and equivalent labels; 2014/manual handling stays in contextual guidance. Existing alignment implementation is preserved. |
| Class grid / features | Single gold selection, neutral hover and distinct focus remain unchanged. Level-feature composition is extracted behind a dedicated region adapter, so a future independently clipped region can replace it without moving class selection/description/proficiencies. No new levels or full browser is implemented. |
| Ability methods | New blank guided SRD characters default to Point Buy with six base scores of 8. Their visible choices are Point Buy and Manual / External. Old Standard Array records keep their method and values and expose a clearly labeled legacy compatibility choice. Method conversions require explicit, revision/buffer-anchored confirmation. |
| Ability editor | Six horizontal rows remain. Ability icons are larger, names use stronger native-font emphasis, numerical fields use the smaller native size, and symmetric +/- controls are wider. Derived modifiers keep their separate frame and explanatory tooltip. Point budget has a dedicated framed status; small widths use a compact count and controlled vertical scrolling. No rolls or racial increases are automated. |
| Proficiencies | Unavailable rows have a darker body/muted label and no hover affordance. Reasons distinguish ineligibility, full skill slots, absent entitlement and missing proficiency. Entitled skills have a separate full-width “Expertise” action with its explicit double-proficiency tooltip. Selected grants, mandatory saves, Rogue tools, level gates and historical grant separation remain intact. |
| Inventory | Both outer content surfaces start at exactly the same top/height at two-column widths. Search, filters, Catalog/Custom toolbar and item list are in the left Inventory panel. Currency and Selected Equipment remain on the right with consistent padding. Dirty currency is saved by both footer actions; inline Save Currency remains. Small surfaces scroll instead of placing their toolbar over the footer. |
| Header overflow | Header rows wrap using measured text widths and reserve height before abilities/stats. Name/XP, species/class/subclass and Age/Height/Alignment cannot extend through the score-panel border. Main sheet and Review share the calculation. Compact Review reuses its redundant saved-summary strip space for the sheet. Long values deliberately increase height and scroll. |
| Proficient saves | Proficient abbreviations and totals use gold/bold emphasis without P/Proficient suffixes. Nonproficient saves stay ordinary text. Full calculation tooltips remain. |
| Review actions | Save Draft stays independent. A separate “Go to [missing step]” action precedes validation details and routes through the existing section map. Finish Character distinguishes blocking requirements from the unused-point confirmation. Continue Editing in that confirmation returns to Ability Points. |
| HP / Temp HP | Values are left-aligned; matching 18×18 minus/plus controls are adjacent at the right of their tiles. HP minimum/maximum, separate temporary HP and campaign health authority remain unchanged. Inspiration remains manual. |
| Death Saves | Successes and Failures remain separate readable rows. Symmetric pixel markers replace font glyph bearing/ascent for exact centering. Interactive hitboxes have gold/dark focus feedback, selected state and accessible marked/unmarked labels. Read-only Review uses the same marker geometry without editing widgets. |
| Equipment Summary | A final bordered panel contains recorded item quantities, equipped/carried state and CP/SP/EP/GP/PP. It grows with inventory while avoiding a duplicate inventory editor. Editing stays on Inventory. |
| Skills & Saving Throws | Larger icons, emphasized ability names, consistent baselines and more balanced row spacing retain practical three-column grouping, trained highlights, breakdowns and authorized edit routes. No dice automation is added. |

## Point Buy validation and compatibility

`CreationRules` still validates six base scores in **8–15**, the 2014 cost table and spending **at most 27**. It no longer treats a positive remaining budget as a final validation error. Standard Array uniqueness and Manual **1–30** remain enforced; invalid writes still fail before persistence.

`LIBRARY_VALIDATE` returns the authoritative revision and remaining ability-point count alongside blocking errors. For a draft with unused points, `LIBRARY_FINALIZE` requires **`confirmedUnusedPoints`** to match the current count at the requested revision. The repository checks ownership, revision, all mandatory requirements and scores independently of the UI before completing it. Changing a name, story, currency or any other field changes the revision, so the old confirmation cannot approve that later state. Finalized underspent records survive reload without requiring a new confirmation just to be read.

Only a newly created blank guided SRD template changes its default method/scores. Imported/provided sheets and existing records keep their saved values. Standard Array remains in the data model/server rules. Conversion to Manual preserves values; conversion to Point Buy preserves allocations already valid within its budget, otherwise explicitly confirms a reset to six 8s. No conversion is silently applied on opening a screen. No-op root navigation no longer enrolls a historical sheet into new validation rules; explicit supported creation edits still use the validated contracts.

## Class icon audit

All twelve original runtime sprites, cleanup sources and final isolated sprites were inspected. The image editor was used for source cleanup. Original approved atlases and C/D source images remain unchanged; packaging copies source RGBA without resampling and retains transparent gutters/nearest-neighbor metadata.

The diagnostic below counts eight-connected components of at most eight pixels whose maximum RGB channel is below 80. It is a **candidate-fragment diagnostic**, not proof that every counted fragment is unwanted: legitimate shading/contours can qualify. It is not Minecraft visual acceptance.

| Sprite | Final action / preserved motif | Candidate dark fragments, D → E |
| --- | --- | --- |
| `barbarian.png` | Cleanup; gold axe/red accents | 498 → 245 |
| `bard.png` | Cleanup; gold lyre/blue trails/music detail | 357 → 112 |
| `cleric.png` | Cleanup; gold sun/star accents | 749 → 143 |
| `druid.png` | Cleanup; gold crescent/tree/green leaves | 896 → 79 |
| `fighter.png` | Cleanup; gold sword/red banners | 934 → 83 |
| `monk.png` | Cleanup; fist/blue wrap | 638 → 182 |
| `paladin.png` | Cleanup; winged helmet/blue accents | 801 → 310 |
| `ranger.png` | **Retained D byte-for-byte**; approved left-aimed arrow, left-bulging limb and drawn string to the right | 303 → 303 |
| `rogue.png` | Cleanup; hood/crossed daggers/purple wisps | 617 → 72 |
| `sorcerer.png` | Cleanup; spiral flame/star | 986 → 175 |
| `warlock.png` | Cleanup; golden eye/purple magic | 486 → 57 |
| `wizard.png` | Cleanup; golden book/blue flame/stars | 684 → 224 |

Aggregate candidates decreased **7,949 → 1,985**. The trial Ranger cleanup increased its candidate count, so it was rejected. A new RGBA digest regression protects the retained approved Ranger design. Eleven sprite PNGs changed; Ranger did not change relative to D.

The three E source sheets contain cleanup candidates. Whole connected principal motifs, rather than blindly cut quadrants, isolate decorations and preserve limbs/book edges that cross nominal cell midpoints. The final Ranger mapping explicitly uses `ranger-d.png`, not the rejected E candidate. Every final sprite has recorded source bounds and actual dimensions. The original non-class 36 sprites stay in use; the same 48 runtime mappings remain. No replacement race art is introduced.

**Artwork limitation:** the image editor changes pixel details; the eleven edited sources are not pixel-identical to D. Motifs/palettes were retained by inspection, and packing tests prove equality to the selected new sources. Fine contours and apparent scale need user screenshot approval. Remaining dark fragments were not blindly erased, because some belong to intentional outlines/interior details.

## Automated verification

Final command: `bash ./gradlew --no-daemon --max-workers=4 build runGametest`, Java **21.0.12.1**, existing Gradle wrapper/Loom, Minecraft **1.21.1**, Fabric Loader **0.16.9**, Fabric API **0.116.1+1.21.1**. Dependencies and combat implementation were not changed.

- Full build: **PASS**, final run **1m 37s**.
- Foundation: **6,077 assertions across 27 suites**, versus D's 5,355 across 25. E adds **111 workflow assertions** and **611 presentation assertions**.
- Minecraft dedicated-server GameTests: **113 required tests passed**, **0 failures/errors**, verified against `build/gametest-results.xml`. Two new gateway tests reject absent/wrong unused-point consent and stale consent, then accept matching current-revision consent.
- Workflow tests use real repositories/UI service operations and the actual shared delayed-ACK gate for nine creator section paths: Save Draft stays, Save & Continue waits, incomplete Rogue choices save, Finish enforces completeness, three unused points require confirmation, stale/rejected/partial/unavailable replies never advance, and a committed lost reply is reconciled after reconnect. Newer currency typing blocks continuation until its next revision is saved; IDs/metadata persist.
- Compatibility tests cover new Point Buy defaults, imported historical method preservation, saved/finalized Standard Array reload, explicit conversions, incompatible confirmed resets, conversion consent invalidated by later buffer/revision changes, Point Buy overspending and Manual bounds.
- UI tests inspect actual constructed sheet/review controls, measured header wrapping, right-grouped health controls, symmetric marker pixels, darker disabled styles, full currency summary, final equipment-panel order, aligned inventory regions, adaptive footer clearance, compact Review fit and the exact retained Ranger RGBA.
- Prior class counts/eligibility/saves, Expertise, alignment focus/reselection/persistence, trackers, inventory, library/campaign independence, permission/revision conflicts and combat suites remain passing.
- All 48 packaged sprite paths and nearest-neighbor metadata were checked. Existing per-pixel source equality/gutter/dimension/aspect checks remain passing. `git diff --check` passed.
- Runtime changes outside client UI are limited to `LibraryRepository`, `CreationRules` and `UiApiService` for the requested authoring/finalization policy. No combat engine, encounter authority, networking implementation, mixin or dependency changes were made.

During incremental verification, compilation caught a record-accessor naming collision, a `JsonArray` accessor and a test declaration typo; those were corrected. The existing wizard finalization fixture now supplies explicit consent for its new default underspent Point Buy allocation. Subsequent full verification passed. Final log: `/tmp/checkpoint-e-final-validation.log`.

**Test boundary:** the server-backed harness and constructed-widget/layout tests do not launch and click every graphical Minecraft page. Real disconnect/reconnect behavior and visual navigation across all screens still require the checklist below. Local unsaved buffers are not a new durable offline-storage system; reconcile an uncertain save with the server after reconnecting.

## Installable artifact

- JAR: **`build/libs/tactical-combat-0.1.0.jar`**.
- Size: **16,062,172 bytes**.
- SHA-256: **`c8dfc9a96360ed2c2083fa2c246723e5801ca811e1b3de4de0244ee388473299`**.
- Install this JAR, not the sources/dev JAR.

## Minecraft testing and approval checklist

**Minecraft visual verification: NOT PERFORMED.** This workspace has no graphical display available. Source image review, layout assertions and dedicated-server tests do not certify rendered screenshots.

Back up the test world. Replace the old Cubetable mod JAR in client and server `mods` folders, keeping one Cubetable JAR per side, and restart both on Java 21 / Minecraft 1.21.1 / the Fabric versions above. Open **Pause → Cubetable → My Characters**. Test a new guided draft, an existing D draft, an old Standard Array source and an independent campaign copy. Use GUI scales **2, 3, 4**, and **8 when the resulting logical viewport permits practical use**.

1. **Identity/subrace:** preview order, natural No Subrace labels, no repeated inline save instruction; reopen/reselect alignment, dismiss outside, keyboard-select, save/reload and verify popup isolation.
2. **Class artwork:** compare all twelve icons for clean exterior alpha, intact deliberate shading, sharpness and apparent scale; Ranger must be the unchanged approved design.
3. **Class selection/features:** repeated selections keep exactly one gold card; focus/hover stay distinct; existing description/proficiencies and level groups remain readable.
4. **Point Buy:** default selected, six 8s for a new blank guided source, six balanced rows, live remaining budget, symmetric controls and distinct derived modifier. Save with unused points and reopen.
5. **Manual / External and legacy Array:** direct entry/bounds, smaller score typography, no fake rolls. Open old Array without changes; cancel conversion, deliberately convert and verify reset/preservation matches the confirmation.
6. **Available/unavailable skills:** class progress/count, darker disabled rows and explicit reasons at a full limit. Selected skills remain gold and removable; partial valid choices save and advance.
7. **Expertise:** full label, clear tooltip, eligible trained skill/tool only, correct Rogue/Bard recorded-level entitlements and selected styling. Historical grants remain inspectable.
8. **Inventory:** equal panel tops, left search/four filters/list/creation toolbar, right Currency/Selected Equipment; save all five currencies, add a stack/separate instance, adjust quantity/equip/edit/remove, verify stable IDs and metadata.
9. **Save controls on all nine steps and record editors:** no-change, unsaved text, incomplete choices, underspent allocation and dirty currency. Save Draft stays; Save & Continue persists before navigating. Type again during a delayed save; failures/stale revisions/timeouts must retain intent and not advance. Reopen a partially completed draft and reconcile a disconnected save before retrying.
10. **Review/validation:** independent Save Draft, Go to the correct missing step, Finish Character. Missing required skills block Finish. With three unused points, Continue Editing returns to Ability Points; Finish Anyway succeeds only after current-revision confirmation. Change another field while confirming and require fresh review/confirmation.
11. **Main sheet/header:** long names, species/subclass, Age/Height/Alignment and missing values remain within the header; ability panels start below it. Review has the same correction and read-only values.
12. **HP/Temp HP/Inspiration:** values left, +/- together right, matching size; HP stops at zero/max and temp HP stays separate. Campaign health remains locked to existing authority; only an authorized DM awards campaign Inspiration.
13. **Death Saves:** centered circles, consistent hitboxes/spacing, gold/dark keyboard focus, marked/unmarked appearance, manual save/reload. Review must not expose interactive tracker controls.
14. **Equipment Summary:** final bordered panel, quantities/equipped states plus CP/SP/EP/GP/PP; only this section grows with equipment, without duplicate inventory editing controls.
15. **Skills & Saving Throws:** larger aligned icons, emphasized names/totals, practical columns, trained gold/bold styling without proficiency suffixes, full tooltips and existing edit paths. No automatic rolls.

At small resolutions, inspect wrapped navigation labels, scrolling surfaces, tooltip access and footer/status clearance. Very long recorded values intentionally expand/scroll; headless fit assertions are not proof of readable rendered text.

## Deferred work and remaining acceptance

All targeted workflow/server changes are implemented. The full independently scrollable feature browser is deferred as requested; only its composition seam is prepared. New class levels, multiclass progression, automatic class abilities/Rage, spellcasting, item synchronization, equipment-to-combat mapping, racial bonus automation, automatic leveling/XP, full DM/custom-item redesigns and automatic Inspiration effects remain deferred.

Graphical verification and final icon/typography/navigation acceptance are not complete. Stop after pushing E and delivering this report; await the user's screenshots and approval before the next checkpoint.
