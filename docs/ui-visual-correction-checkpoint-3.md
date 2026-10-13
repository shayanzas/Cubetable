# Cubetable UI visual correction — Checkpoint 3

Implemented slice, awaiting Minecraft visual and behavioral approval. Graphical verification: **NOT DONE**. No actual Minecraft screenshots were produced. The cloud workspace has neither a configured graphical display nor Xvfb. Headless tests do not establish visual acceptance.

## Branch and preservation

Continued `dev/ui-correction-checkpoint-2` from `b05961ea4ecb1bebbcb449ea7fa1bb2381f701eb`. The complete previous checkpoint and Checkpoint 1 history remain ancestors. No reset, reclone, merge or push was performed. These changes are local commits:

| Commit | Change |
| --- | --- |
| `920278b` | Lossless approved sprite packaging and shared SRD feature inspection |
| `6e8ce80` | Shared responsive shell; direct Identity, Class and Ability controls |
| `b0947fd` | Three dashboard primary views, grouped checks and permitted manual controls |
| `d79fba4` | Shared race reference metadata and recorded feature inspection details |

The documentation commit follows these implementation commits. Combat rules, movement, attacks, AI, combat HUD, networking protocols, repository schemas, authorization and dice trust were not changed. Library source and campaign copies remain independent.

## Reference audit and mapping

Read the user’s pasted request separately from the attached implementation specification, then inspected the extracted reference guide, approved icon images, BG3 layouts, Cubetable concepts and negative Minecraft screenshots. References were extracted under `/workspace/attachments/checkpoint3-references/`; none of the BG3 graphics were shipped in the mod.

| Reference | Implemented mapping |
| --- | --- |
| BG3 race/subrace screens | One Identity workspace: name, age, height, alignment, race grid, contextual subrace buttons and individually inspected traits |
| BG3 class screen | Approved icon grid beside overview, Hit Die, primary abilities, proficiencies, skill choices and level-grouped feature entries |
| BG3 ability direction / textual specification | Six full ability names with direct values, visible accessible minus/plus controls, modifiers and in-page scoring modes |
| BG3 overview and three sheets | Character & Features, grouped Skills & Saving Throws, Character Statistics; seven retained sections in a scrolling vertical rail |
| Cubetable concepts | Charcoal surfaces, restrained gold selection, Minecraft font and skin head; no 3D model or skin editor |
| Current defect screenshots | Removed the creator’s top navigation grid, ability launcher and dashboard’s repeated full-page stat-card grid; corrected sprite bounds and narrow-button text padding |

`bg3_visual/04_ability_scores.jpg` actually depicts background selection. Ability implementation follows the written specification and the correct prior approved ability reference, rather than treating the mislabeled image as ability UI.

No dedicated race sprites existed in this checkout’s creator asset set. Native race choice presentation remains; no replacement/rejected race artwork was added or any existing race artwork replaced.

## Files and screens

- `CharacterShell`, `SectionRail`, `CharacterCanvas`: shared vertical navigation, contextual controls, clipped internal scrolling, focus/cursor retention and responsive saved-summary placement. Creator/dashboard use a wider maximum frame; other screens keep their existing frame width.
- `CharacterWizardScreen`: replaces the previous navigation/catalog/launcher structure. Name/race/subrace/age/height/alignment share Identity. Class choices stage a preview, while the summary explicitly shows saved values. Save/Continue submit revision-checked server operations; Enter saves field edits. Validation remains a separate result, with a link to the first failing section before finalization.
- `CreatorWorkspace`, `CreatorDraftState`, `AbilityWorkspace`: actual controls and retained editing buffers. Refresh merges untouched fields and retains local edits; external revision changes require review. Acknowledgements cannot erase newer input. Standard array validates each value once; point buy retains the existing 27-point/8–15 rules; manual/external values remain unverified. Racial increases are references and never automatically applied to existing effective scores.
- `CreatorIcons`, `CreatorChoiceGrid`, `RpgButton`: individual sprite textures, aspect-preserving centered fitting, uniform card slots, two-pixel transparent gutters, and visible labels on narrow plus/minus controls.
- `FeatureReference`, `FeatureInspectScreen`, `CreatorLore`, `ui/feature-reference-v1.json`: one versioned SRD 2014 reference catalog for race traits and class level 1–3 introductions. Stable IDs, prerequisites, actions, uses, recovery, formulas, effects and informational status are inspectable from creator and dashboard. Existing custom feature descriptions, recorded use pools and duration are preserved in inspection. This catalog does not execute mechanics or migrate saved characters.
- `CharacterDashboardScreen`, `DashboardModel`, `DashboardLayout`: replaces identical stat boxes with compact sheet rows and grouped views. Skills/saves group by ability into responsive columns; recorded proficiency/expertise/adjustment totals have an explicit manual breakdown. Overview separates conditions, resistances and notable features, bounding record previews and retaining the complete Features section. Statistics shows health, identity/proficiencies, conditions, exhaustion and resources. XP bars appear only in actual XP mode; DM-managed/milestone labels remain genuine. No relationship Approval meter.
- Creator Background, Proficiencies, Equipment, Features, Spells and Details navigate directly to their existing dedicated editors. Their deeper UI was not rebuilt. Dashboard Equipment, Combat, Features, Spells, Details, Notes and Manage remain reachable. Approval, assignment, binding, rest/activity and independent-copy management remain in the existing sections.
- `CreatorCheckpointTests`, `DashboardUiTests`, `UiCorrectionTests`: updated actual-layout expectations and added sprite/source preservation, retained-buffer/concurrent-edit, feature metadata, manual total, library health, campaign denial and copy independence coverage.

## Asset verification

See [complete sprite paths and dimensions](ui-checkpoint-3-sprites.md). All **48** textures and their sampling metadata, sprite manifest and feature catalog were verified inside the release JAR. Static checks verify actual dimensions, transparent gutters, original RGBA pixels, centered fitting and slot bounds. All **1,576,481** visible pixels from the two approved source images remain represented once.

Original source images are byte-for-byte unchanged against Checkpoint 2:

- `textures/gui/creator/icons.png`, 1448 × 1086; SHA256 `202c4620ed03a0b8879b2e94340919ff8546f14df7bf96bd8f42e2d351825b83`.
- `textures/gui/creator/class-extra.png`, 1774 × 887; SHA256 `426206e35fce441fbe59705fdc7d5dc801a1a27590ab134dcd8ba661e554e33e`.

`tools/pack_ui_sprites.py` reproduces the lossless packaging. Pillow, NumPy and SciPy are needed only to regenerate textures, not to build or run the mod. No dependency declarations were changed.

## Verification

Baseline before implementation: full build, **2,340 assertions**, **109 required GameTests**, all passing.

After implementation: full Java 21/Fabric build and **2,965 assertions across 20 suites**, all passing. **109/109 required GameTests** passed, with zero failures in `build/gametest-results.xml`. The final presentation/reference refinements received another full build and assertion run; they do not change the headless combat/GameTest code. `git diff --check` and release-JAR resource checks pass.

Commands use the existing Java 21 installation and Gradle cache; TLS verification stays enabled:

```bash
export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

Existing environment prerequisites were reused. This checkpoint does not change the saved environment configuration.

Release artifact: `build/libs/tactical-combat-0.1.0.jar`. Do not install the `-dev`, `-sources` or GameTest JARs.

## Supported interactions and explicit blockers

- Library HP/temp HP controls use existing `LIBRARY_UPDATE` combat authoring with expected revision. Only those health fields change; other inputs and independent campaign copies remain intact.
- **Campaign HP/temp HP is read-only.** `CHARACTER_EDIT` accepts existing UI record sections through `SheetSections.update`; it does not accept `combat`. There is no suitable authorized health runtime operation in this UI API. Controls are disabled with an explanation; no new server API or combat behavior was introduced.
- Inspiration, three success/failure death-save pips and manual resource controls use the existing authorized manual operations, their relevant revisions and allowed sections. Pending/rejected updates never publish client-only authoritative results. Campaign policy still decides permission. These counters do not apply feature effects or heal/recover automatically.
- **Ability generation and skill/save execution are unavailable.** Generic manual dice accepts client-provided labels/modifiers and lacks character/stat authorization, six durable 4d6-drop-lowest assignments and reroll controls. Existing attack actions are not a check/save endpoint. The UI identifies these as unavailable/manual and offers inspection, never a simulated roll.
- Feature references are level 1–3 SRD introductions, not the full class progression or executable actions. Existing saved feature schema has recorded descriptions/status/use pools, not typed execution formulas/prerequisite enforcement; the new read-only reference prepares inspection without changing that schema.
- Compact windows reflow and scroll internally. Full right summary appears where width permits; smaller windows use a saved-summary strip. Long labels are trimmed with hover/detail access. Actual sharpness, hover placement, visual density, text glyphs and GUI-scale behavior remain **Not verified** until Minecraft testing.
- Live two-client latency/conflict/permission-revocation and reconnect acceptance remain **Not verified** graphically. Existing authorization/revision regression checks passed; these do not replace multiplayer testing.

## Minecraft acceptance checklist — stop here for approval

1. Back up a disposable world. Run Java 21, Minecraft 1.21.1, Fabric Loader 0.16.9 and Fabric API 0.116.1+1.21.1. Replace the previous mod JAR with the release artifact; keep only one Cubetable/tactical-combat mod JAR installed.
2. Open Cubetable → My Characters → Create Character. Confirm Identity opens directly, with a vertical rail and no Guided/Manual chooser or Method step. Open an existing draft too.
3. Enter name/age/height/alignment. Select Elf → High Elf, Dwarf → Hill Dwarf, Halfling → Lightfoot and Gnome → Rock Gnome. Inspect individual traits. Compare preview versus saved summary before/after Save. Verify no racial bonus changed scores. Check custom race/class records survive without being mislabeled Human/Barbarian.
4. Inspect all twelve class icons, especially Barbarian, Fighter, Paladin and Sorcerer. Check full motifs, equal slots, centered art and readable titles. Inspect Rage, Unarmored Defense, Second Wind and level-2/3 features; verify reference/manual wording and 2014 formulas.
5. Open Abilities directly. Apply standard array, try a duplicate assignment, switch to point buy, test 15/15/15/8/8/8 (27 spent), and reject a base 16/over-budget configuration. Test both visible buttons and typing, modifier updates, unverified manual values and saved effective-score labels. In-game rolling must remain explicitly unavailable.
6. Type unsaved edits; refresh and change GUI scale/window size. Verify text, cursor, focus, section and scroll survive. Use Tab/arrows/Enter/Escape. Save and reopen. Test a stale revision from a second client: retain edits, refresh/review and retry; never report rejected data as saved.
7. Check other creator sections still open their actual editors without launcher pages. Write multiline Details, save/reopen, inspect Overview/Review, validate/finalize and use the first-error link when appropriate. Choose Keep in Library or submit an independent campaign copy.
8. On the dashboard, capture all three primary views. Inspect conditions/features and manual skill/save breakdowns. Verify all six saves and 18 skills are grouped correctly, P/E markers are recorded-only, and there is no fake Roll/Cast/Activation or relationship Approval bar.
9. Change library HP/temp HP, inspiration and death pips, then refresh/reopen. Create/use a manual resource pool and test its bounds. Confirm campaign HP is read-only. Verify independent campaign copy health/resources stay unchanged by library edits; test allowed and denied manual tracking with owner/controller/DM roles and actual policy.
10. Recheck Equipment, Combat, Features, Spells, Details, Notes and Manage navigation; DM approval/assignment/binding and reconnect selection restoration. Confirm combat behavior and exceptional DM control semantics remain unchanged.
11. Set real XP progression and verify interval/bar; switch to Milestone or DM-managed and verify no invented XP bar. Check portrait/name/race/class/level/campaign state in the saved summary.
12. Capture **Identity with subrace, Class, Abilities, Character & Features, Skills & Saving Throws, Character Statistics** at GUI scales **2, 3, 4**, and **8** where the actual window permits. Include a compact logical window around 480 × 257 and normal/wide windows. Suggested filenames: `cp3_identity_scale2.png`, `cp3_class_scale3.png`, `cp3_abilities_scale4.png`, `cp3_dashboard_overview_scale2.png`, `cp3_dashboard_skills_scale3.png`, `cp3_dashboard_stats_scale4.png`, plus compact/scale8 variants. Send the screenshots and any crash/client/server logs with feedback.

Stop after this slice. Further visual work and other creator/combat checkpoints await the user’s Minecraft screenshots and approval. No push or merge is authorized for this checkpoint.
