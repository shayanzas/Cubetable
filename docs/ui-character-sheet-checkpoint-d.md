# Cubetable — Character Creator and Sheet Checkpoint D

Checkpoint D is implemented and automated verification passed. Minecraft visual acceptance remains with the user.

**Minecraft visual verification: NOT PERFORMED.**

## Branch and preserved foundation

- Repository: `shayanzas/Cubetable`; branch: [`dev/ui-character-sheet-checkpoint-d`](https://github.com/shayanzas/Cubetable/tree/dev/ui-character-sheet-checkpoint-d).
- Base: confirmed local and remote Checkpoint C, `3cf5650f87f05301ad75addd4bc17bd418522477`. The working tree was clean before creating D; C remains an ancestor.
- Tested implementation and regression-test commit: `6b00b937c80d02b09e85e17b54e72d6b90d683b1`. The documentation commit above it does not change the tested JAR. The exact delivery tip is reported in the delivery message and available through the branch link.
- Existing character data, nine creator destinations, six dashboard destinations, library/campaign independence, validated server acknowledgements and permission boundaries are preserved. No server implementation, combat package, networking implementation, mixin, dependency or build configuration changed in D.
- Logical commits cover UI refinements, six class asset repairs, focused regression coverage, and this report. Push the D branch only; no merge or pull request is part of this checkpoint.

## Implemented changes

| Area | Result | Verification boundary |
| --- | --- | --- |
| Identity | An unselected race shows Preview and one instruction only. Empty saved-selection, subrace and racial-trait sections are omitted. The existing race grid and alignment popup remain in use. | Source reviewed; existing real parent/child focus and alignment persistence tests pass. Rendered empty-state acceptance needs screenshots. |
| Class selection | Only the selected class has a gold border and label. Hover is neutral; keyboard focus has a separate pale-blue outline. A rebuilt grid anchors focus to the selected class rather than Barbarian. Preview distinguishes saved and unsaved selection. | New tests click all twelve entries repeatedly, rebuild, browse and confirm with the keyboard. All twelve existing class validation fixtures pass. |
| Class information | Each proficiency paragraph wraps in a panel whose height follows its line count. Rogue weapon text is no longer a trimmed single-line button. Choose N Skills opens the actual proficiency picker; individually inspectable features retain their level headings. | Source and compile verified; actual font wrapping and readability await Minecraft review. |
| Ability Points | Six horizontal rows remain. Method Help explains adjacent Standard Array swaps, Point Buy costs/budget and Manual bounds. Point Buy has a dedicated live budget line. The modifier has its own frame and tooltip; array arrows show their swap direction. | New tests exercise real array swap logic repeatedly; existing tests verify atomic score/method persistence and server bounds. In-game spacing remains unaccepted. |
| Proficiencies | A Class Skill Choices panel shows class, selected count, remaining choices and progress. Ordinary incomplete drafts are neutral guidance; invalid/excess retained choices remain errors. Selected skills and immutable class saving throws are gold; unavailable skills are muted with a reason. Expertise controls appear for entitlement or retained class Expertise, with trained-skill/level rules preserved. Historical/custom grants remain inspectable through the existing grant editor and breakdowns. | Existing all-class count/eligibility/save-pair, Rogue/Bard Expertise, reconciliation, ownership and persistence regressions pass. Screenshots must confirm the available/selected/disabled hierarchy. |
| Inventory | Currency and Selected Equipment are independent panels with matching outer margins, padding and gap. At two-pane widths, currency starts beside search rather than below the list filters. CP/SP/EP/GP/PP and Save Currency stay together. Catalog/custom addition and existing quantity/equip/edit/remove controls are preserved. | Existing currency acknowledgement/newer-typing/conflict tests, stack/separate-instance identity tests and server authority tests pass. New tests check the currency pane's top alignment. |
| Creator navigation | Root and specialized pages share footer anchors based on the outer creator frame. Back and Save have stable positions; Continue is added to the main specialized pages and requires saved, refreshed state. Relevant secondary actions occupy the shared slots; record-editor pages only show their applicable actions. Status remains below the button row. | Footer rectangles fit tested logical viewports and clear each other. Root/child content shifts do not change the footer anchors. Actual labels/hit targets need graphical review. |
| Main sheet | Wider compact stat cards spell out Current HP / Max HP and Proficiency Bonus. HP, temporary HP and Inspiration use matching 18×18 buttons grouped near their values. HP maximum and campaign authority locks remain intact. Death Saves has its own panel with Successes and Failures rows. | New tests construct the real sheet, inspect counter dimensions and check disabled maximum/zero controls. Existing service/GameTests cover tracking and authority. |
| Saves and detailed skills | Main/review saving throws show ability abbreviation plus total, with proficient abbreviations gold and full breakdown tooltips. Detailed Skills & Saving Throws removes P/E/tabletop suffixes from totals, uses readable Saving Throw labels and gold trained controls, and retains breakdown and editing paths. | Constructed sheet labels and check projections tested; proficiency calculations remain unchanged. |
| Review & Finish | HP, temporary HP, Inspiration and death-save markers are read-only values, without editing widgets. The compact core remains shared with the main sheet; Equipment Summary stays last and grows with equipment records. Existing blocking validation still routes to the required creator tab. | New tests inspect the actual constructed review control list and labels. Existing review-validation routing tests pass. |

## Class artwork audit

All twelve source sprites were inspected. This table records source inspection and byte comparisons, **not Minecraft visual acceptance**.

| Class | D action | Source inspection / correction |
| --- | --- | --- |
| Barbarian | Retained byte-for-byte | Existing C gold axe/red accent motif retained; no replacement required by source review. |
| Bard | Retained byte-for-byte | Existing C gold/blue lyre remains a quality benchmark. |
| Cleric | Retained byte-for-byte | Existing C gold sun motif retained. |
| Druid | Repaired | Tree, green leaves and gold crescent retained in a cleaned source; noisy olive/background fringe replaced. |
| Fighter | Repaired | Gold sword/red banners redrawn cleanly; dark/background remnants removed from the silhouette. |
| Monk | Retained byte-for-byte | Existing C gold fist/blue wrap remains a quality benchmark. |
| Paladin | Retained byte-for-byte | Approved `class-extra.png` helmet/wings source retained. |
| Ranger | Rebuilt | Golden limb bulges left, tips open right; drawn string meets the arrow nock on the right, arrowhead aims left. Inspected as a coherent bow/string/arrow relationship. |
| Rogue | Repaired | Hood, crossed gold daggers and purple accent retained; noisy exterior fringe replaced by deliberate contours. |
| Sorcerer | Retained byte-for-byte | Approved `class-extra.png` flame source retained. |
| Warlock | Repaired | Gold eye/purple magic retained; dirty exterior patches replaced with clean contour/transparent openings. |
| Wizard | Repaired | Gold book/blue flame retained; background remnants and stray fringe replaced. Only the left Wizard cell of the recorded generated source is mapped to a sprite. |

The image-editing tool produced the new source artwork; `tools/pack_ui_sprites.py` only packages its existing RGBA pixels losslessly. The approved original atlases remain unchanged. The manifest records each source and bounds; six D replacements, four retained C repairs and 38 original sprites make the same 48 runtime sprite mappings. Two transparent gutter pixels, full-aspect centered fitting and nearest-neighbor `.mcmeta` filtering are retained. No replacement race art was added.

Original atlas SHA-256 values remain:

- `icons.png`: `202c4620ed03a0b8879b2e94340919ff8546f14df7bf96bd8f42e2d351825b83`
- `class-extra.png`: `426206e35fce441fbe59705fdc7d5dc801a1a27590ab134dcd8ba661e554e33e`

## Automated verification

Final command: `bash ./gradlew --no-daemon --max-workers=4 build runGametest`, using Java **21.0.12.1**, the supported wrapper, Minecraft **1.21.1**, Fabric Loader **0.16.9** and Fabric API **0.116.1+1.21.1**.

- Full build: **PASS**, final run **1m 9s**.
- Foundation: **5,355 assertions across 25 suites**, including **879 new D presentation assertions**. These are assertion counts, not 5,355 graphical Minecraft tests.
- Minecraft dedicated-server GameTests: **111 required tests passed**, **0 failures/errors**, checked against `build/gametest-results.xml`.
- Prior server coverage includes all twelve starting class counts/eligible skills/save pairs, class-change reconciliation, Rogue/Bard recorded-level Expertise, Standard Array/Point Buy/manual enforcement, revision/ownership checks, historical grants, independent campaign copies, Inspiration DM awards and manual tracker persistence. No server validation was relaxed.
- New coverage exercises repeated class mouse/keyboard selections and rebuilds, absence of a second gold selection, footer fit/separation, inventory top alignment, actual constructed sheet/review controls, readable labels, uniform counter dimensions, HP maximum/temporary-HP zero controls, read-only review and array swap invariants.
- Existing alignment tests retain eight parent-event reopen/reselection cycles, keyboard confirmation and focus preservation after canvas replacement. Existing race/subrace and save/reload tests remain passing.
- All 48 packaged JAR sprite paths and nearest-neighbor metadata were checked. Existing per-pixel tests verify dimensions, source RGBA equality, transparency gutters, manifest pixel counts and aspect fitting at 12/16/28/40 pixels.
- `git diff --check` passed. D implementation changes are confined to client UI, creator resources, packaging tool, tests and this report.

An initial D test used the wrong catalog index for Ranger (Paladin occupies index 6); the fixture was corrected to index 7. Subsequent full runs passed. The final log is `/tmp/checkpoint-d-final-validation.log`.

## Build artifact

- Installable mod JAR: `build/libs/tactical-combat-0.1.0.jar`.
- Size: **13,614,094 bytes**.
- SHA-256: **`09515a7212221cc41030c5c8812f709683e8e6b553deb5de737e42b6c681e8ec`**.
- The sources JAR and dev JAR are not the installation artifact.

## Graphical limitations and approval

**Minecraft visual verification: NOT PERFORMED.** There is no DISPLAY, Wayland display or Xvfb available in this workspace. Dedicated-server GameTests, widget-event tests, geometry checks and image inspection do not certify rendered Minecraft screenshots.

Small logical viewports retain scrolling and fewer columns. At the tested 640×360 logical dashboard viewport, the essential compact core fits before Equipment Summary. Long recorded text and extreme numeric values may still require the existing tooltip/detail path. Three-column skill presentation is retained where the available width supports it; narrower panes reduce columns for readability. Source contour quality, apparent icon size, real font wrapping, hover/focus appearance and usable scale-8 layouts require the user's screenshots. Native text remains in use; no transform-based font fitting was introduced.

Awaiting approval: the actual 15 views below, practical GUI scales, icon balance/cleanliness and footer/readability acceptance. Stop after this delivery; no further checkpoint or mechanics work is authorized here.

## Minecraft testing and screenshot checklist

Back up the test world. Use Java 21 and Minecraft 1.21.1 with the Fabric versions above. Replace the previous Cubetable JAR with this installable JAR in client and server `mods` directories; keep one Cubetable mod JAR on each side and restart both.

Open **Pause → Cubetable → My Characters**. Create a guided SRD draft and reopen an existing C record. Confirm existing identity, story, inventory, proficiencies and campaign copies survive. Capture these fifteen views at GUI scales **2, 3 and 4**, and **8 where the resulting logical resolution permits usable controls**:

1. **Identity, no race:** Preview plus one instruction; no empty saved status/subrace/traits.
2. **Identity, selected race/subrace/alignment:** select Elf/High Elf, reopen and reselect alignment repeatedly, dismiss outside, save/refresh/reopen and use keyboard navigation. Popup clicks must not activate underlying controls.
3. **Class, repeated changes:** cycle Barbarian → Rogue → Wizard → Cleric → Ranger repeatedly. Only the selected card stays gold; hover is neutral and keyboard browsing has a distinct outline. Confirm the unsaved preview and saved summary agree only after acknowledgement.
4. **All twelve class icons:** compare apparent size, aspect, gutters, sharpness and contours against retained Bard/Monk/Paladin/Sorcerer. Confirm no dirty specks or background remnants in the six repairs.
5. **Ranger close inspection:** arrowhead left, fletching/nock right, limb curved left and string drawn right; no backward arrow/bow relationship.
6. **Rogue class preview:** complete weapon proficiency text wraps, followed by saves, Choose 4 Skills and level-grouped feature inspection.
7. **Standard Array:** six horizontal rows; help explains swaps; arrows exchange neighbors without duplication; score/modifier grouping stays readable after resize and save.
8. **Point Buy:** prominent remaining budget, 8–15 bounds and correct expensive 14/15 increases; spend all 27, save/refresh and confirm persisted values/method.
9. **Manual:** type valid 1–30 scores, verify modifiers and save; invalid scores must be rejected without losing unrelated data. Racial changes and external rolls remain manual.
10. **Incomplete Proficiencies:** normal class/count/remaining/progress guidance, eligible neutral rows, unavailable muted rows with reasons; no irrelevant Expert buttons for Cleric/Fighter/etc.
11. **Complete Proficiencies:** selected skills/checkmarks/modifiers, mandatory gold saves, Rogue/Bard entitlement and eligible Expertise, persisted selections. Change class and resolve retained incompatible choices rather than silently losing them.
12. **Inventory & Equipment:** left search/four filters/list, matching right Currency and Selected Equipment panels. Save all five currencies/reopen; add a catalog entry twice, create a separate instance, adjust quantity, equip/edit/remove. IDs stay stable; no physical item or combat statistic changes.
13. **Review & Finish:** HP, Temp HP, Inspiration and death markers read-only; complete labels, gold proficient saving-throw abbreviations without suffixes, final Equipment Summary and actionable blocking validation. Finish through the existing server flow.
14. **Main Character Sheet:** full stat labels and counter grouping; HP stops at maximum, Temp HP remains separate, Death Saves has two readable rows. Check tracker persistence and campaign HP locks; only an authorized DM can award campaign Inspiration.
15. **Skills & Saving Throws:** grouped abilities, readable names/totals and gold trained states, no P/E/tabletop suffixes, complete tooltips, existing authorized edit routes and no functional dice-roll claim.

Compare **Back / Save / Continue / Refresh or applicable secondary actions** across the root creator, Proficiencies, Inventory, Features, Spells, Details and record editors. Check status text clears the footer and no button moves horizontally because child content has a navigation rail. Send screenshots and observed differences before the next checkpoint.

## Deferred work

New class abilities, Rage, spellcasting automation, damage/action-economy changes, Minecraft item synchronization, equipment-to-combat integration, automatic leveling, racial score automation, large Features/Spells redesigns, complete custom-item form redesign, DM dashboard redesign and campaign Inspiration-cap configuration remain deferred. Existing manual resource and combat behavior remain governed by Checkpoint C and earlier implementations.
