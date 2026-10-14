# Cubetable — Character Creator and Dashboard, Checkpoint B

Implementation delivered on the new branch `dev/ui-character-sheet-checkpoint-b`. **GitHub push confirmed** to `shayanzas/Cubetable`: implementation tip `af1c66bcb73d1f63b6f51693145d840246f7a1e4` matched a fresh remote `git ls-remote` check. This report follows in a documentation commit; the final delivery message supplies the branch tip including documentation. No merge or pull request was created.

**Minecraft GUI rendering: NOT VERIFIED.** This workspace has no configured display or Xvfb. No actual screenshots of the new build were captured, and no graphical leave/rejoin test was performed. Server GameTests, service fixtures and layout/input checks are separate evidence, not visual acceptance.

## Base and commits

Continued from Checkpoint A on `dev/ui-identity-checkpoint-a`, commit `364620a038a42bf1bdc7d16f4ba649e840743941`. The new branch was created before source edits. Existing work was preserved; no reclone, reset, upstream modification or branch replacement occurred.

| Commit | Change |
| --- | --- |
| `d0ddd43` | Dropdown dismissal/capture, approved class icon fitting and larger cards, concise class overview, centered numeric ability controls and reusable panel/portrait primitives |
| `dec5ee6` | Compact proficiency/expertise screen, stable categorized inventory models/forms/picker and validated snapshot replies |
| `af1c66b` | Formatted review/main sheets, six dashboard tabs and separate management, narrative preservation, feature/spell inspection, fail-closed acknowledgement handling and regression tests |

No rules, combat, mob behavior, movement, damage, HUD, character repository, networking protocol, mixin or server UI API implementation was changed. UI operations still use the existing authorization and persistence boundaries.

## Completed versus deferred

PASS means implemented and automatically checked where feasible; every visual result still awaits Minecraft screenshots.

| Requirement | Status | Result / limit |
| --- | --- | --- |
| Nine creator tabs in exact order | PASS | Identity, Class, Ability Points, Proficiencies, Equipment, Features, Spells, Character Details, Review & Finish; no Background/Overview/Method tab |
| Identity alignment reselection and dismissal | PASS | Reopen/reselect repeatedly; click field to toggle, outside click dismisses without choosing the race underneath, Escape dismisses the popup before closing the creator. Hover has one focused/hovered row. Input tests cover repeated selection and click interception |
| Approved race selector and subrace hierarchy | PASS | Existing 3×3 measured race grid and Preview → Subrace → Racial Traits retained; base/variant compatibility and persistence tests remain. No replacement race artwork |
| Class icons and level-grouped details | PASS / NOT VISUALLY VERIFIED | Larger 40-pixel icon slots and uniform 62-pixel rows; centered titles, preserved aspect/UV fitting, direct class introduction, separated proficiencies and individually inspected level 1–3 SRD feature references. All approved sprite source pixels remain unchanged |
| Ability methods and numeric controls | PASS | Standard array, 27-point buy and manual/external entry retained. Compact centered value fields, visible plus/minus controls, modifiers and dynamic validation. No automatic race increases |
| In-game ability and check/save rolls | DEFERRED | No suitable character-authorized operation/assignment contract; generic manual dice accepts client-supplied labels/modifiers. No fabricated result or decorative roll action |
| Consolidated proficiencies | PASS | 18 skills, six saves and expertise in one compact matrix; governing ability and tabletop total shown. Expertise implies a single proficiency and removing proficiency removes its expertise. Duplicate/case aliases normalized. Tools/languages/custom entries have a separate small form, not another standard-skill chooser |
| Equipment and inventory | PASS / NOT VISUALLY VERIFIED | Search/category picker with selected-item summary, quantities, equipped state, descriptions, currency, edit/remove and SRD reference browsing. Dashboard inventory is a real tab, not a launcher. Stable record IDs avoid retry/navigation duplicates; identical names get item ordinals. Existing definition/properties/mapping notes preserved |
| Features and spells | PASS | Separate destinations, inspectable features/custom spell metadata, existing feature resource and spell-slot bookkeeping retained. No activation/casting automation introduced |
| Optional narrative | PASS / LIVE GUI NOT VERIFIED | Six narrative fields only, native multiline editor available for creator and campaign background. Basic Identity fields remain intact. Submitted text is compared with newer typing before a successful save can close the editor |
| Review & Finish sheet | PASS / NOT VISUALLY VERIFIED | Shared formatted sheet with identity/portrait, abilities, statistics, advancement, attacks, conditions/features, equipment and proficiency summaries. One Finish action still validates then finalizes with the saved revision; first-error route remains available |
| Six dashboard tabs | PASS | Character Sheet; Skills & Saving Throws; Inventory & Equipment; Features; Spells; Character Background. Manage Character is a separate lower-rail button opening existing lifecycle/approval/control/binding management |
| Main sheet interactions | PARTIAL | Library HP/temp HP plus permission-checked inspiration/death pips/hit dice/exhaustion/resource counters. Campaign health remains read-only: `CHARACTER_EDIT` does not accept combat/health sections and no appropriate health runtime operation exists in the current UI API |
| Combat & Actions within main sheet | PASS | Actual supported weapon names/base damage/range, armor and available ammunition; existing encounter link. Authorized SRD initiative readback supplies current action/movement only when the viewed bound actor is the active actor. No new attack or unsupported action control |
| Privacy, ownership, copy independence and revision checks | PASS | Existing server policies remain decisive. Partial/malformed replies cannot publish snapshots or re-enable trusted edits. Library/campaign copies remain independent; stale/unauthorized/bound inventory mutations are tested against the real service |
| GUI scales 2, 3, 4 and permitted 8 | NOT VISUALLY VERIFIED | Native-font-aware wrapping/fitting and logical geometry tested. No graphical client/display available; actual clipping, texture appearance, hover placement and interaction feel require screenshots |

## Main files and architecture

- `CharacterCanvas`: reusable framed panels, wrapped text, available Minecraft skin heads, retained scroll/focus, and popup capture. No 3D avatar or skin customization. Online vanilla skin entries are reused; unavailable offline/remote skins do not trigger new downloads or a misleading local-player portrait.
- `CreatorChoiceGrid`, `CreatorWorkspace`, `FeatureReference`, `feature-reference-v1.json`: existing approved assets and versioned SRD metadata. Class descriptions are original short introductions; no BG3 art/rules were copied. Identity’s approved grid is independent of the larger class card layout.
- `AbilityWorkspace`, `AlignmentDropdown`, `CharacterWizardScreen`: focused interaction/layout fixes, existing score contract and nine-step workflow, new inventory routing and formatted review.
- `ProficiencyModel`, `ProficiencyPickerScreen`, `DashboardEditors`: duplicate-safe canonical display choices, proficiency/expertise relationship, calculated tabletop totals and retained custom/tool/language data. Unknown imported score representations show an unavailable modifier rather than inventing a value.
- `InventoryModel`, `InventoryForms`, `InventoryDetails`, `InventoryWorkspace`: stable identity-based updates, category/search projection and shared selected-item panel. Quantity/equipment changes are submitted and displayed only after acknowledgement. Deleted records are not silently recreated by stale edit forms. Search does not apply a conflicting second filter; selection/scroll are preserved through updates and reopening.
- `CharacterSheetUi`: shared formatted review/dashboard presentation. Full dedicated views remain reachable; the main sheet bounds feature/equipment previews instead of dumping all records into its overview. Existing hit-dice, exhaustion and tracker resource controls remain present, as do feature pools and spell slots in their dedicated views.
- `CharacterDashboardScreen`, `DashboardModel`, `DashboardLayout`, `SectionRail`: exactly six destinations, wider wrapped navigation labels, separate management and a single home for health/death/combat statistics. Character Background combines narrative fields without duplicating basic Identity. Rest/history links remain reachable from campaign management.
- `CharacterDetailsScreen`, `SnapshotReplies`, `ManagementScreen`: optional campaign narrative routing, newer typing retention, complete-snapshot validation and failed-acknowledgement freshness guards.
- `SpellReference`: inspect existing custom definitions, preparation, components, range, duration and source. No Cast action.
- `CharacterSheetCheckpointTests`, `DashboardUiTests`, `UiCorrectionTests`, `FoundationTests`: new focused integration/input/layout checks and updated expectations for the six destinations. Obsolete ten-tab geometry assertions changed with the removed pages; unrelated mechanics coverage remains.

## Verification evidence

Full final Java 21/Fabric build: **PASS**.

- **4,111 assertions across 22 suites**, including **220 Checkpoint B checks**.
- **109/109 required GameTests passed**, zero failures in `build/gametest-results.xml`.
- The full `build runGametest` run passed; subsequent UI/narrative/selection refinements received further full build/assertion runs. Those refinements do not change the server/GameTest code.
- Real service checks exercise save/read/finalize, repeated reopen, repository restart, legacy background/narrative preservation, duplicate item retry, identical-name items, source/copy independence, stale revisions, ownership and bound-write rejection.
- Input checks exercise repeated alignment selection, outside/field dismissal, popup-versus-race interception and invalid acknowledgements. These are headless input/model checks, not rendered-screen tests.
- Static checks cover nine/six destination order, separated management hitbox, sheet/inventory geometry, sprite dimensions/gutters/original RGBA preservation and centered aspect fitting.
- `git diff --check` passed. The release JAR contains the new sheet, inventory, proficiency, snapshot and inspection classes. Generated test logs were preserved at `/tmp/cubetable-checkpoint-b-generated-logs/`, outside the clean checkout.

Approved source artwork remains byte-for-byte unchanged against Checkpoint A:

- `textures/gui/creator/icons.png` (1448 × 1086), SHA256 `202c4620ed03a0b8879b2e94340919ff8546f14df7bf96bd8f42e2d351825b83`.
- `textures/gui/creator/class-extra.png` (1774 × 887), SHA256 `426206e35fce441fbe59705fdc7d5dc801a1a27590ab134dcd8ba661e554e33e`.

The existing [48-sprite inventory and dimensions](ui-checkpoint-3-sprites.md) still applies. GUI appearance, including the specifically reported Barbarian/Bard/Cleric/Ranger silhouettes, needs actual Minecraft review despite the passing source/UV checks.

## Artifact and build instructions

Release JAR: `build/libs/tactical-combat-0.1.0.jar`.

SHA256: `a8a26507cfb70aa31c2ae36bc059236d79c53c082fe2f012ba54552829efae46`.

On a local Java 21 installation, from the repository root:

```bash
./gradlew --no-daemon --max-workers=4 build runGametest
# Windows: gradlew.bat --no-daemon --max-workers=4 build runGametest
```

For this cloud workspace, the existing verified toolchain/cache/proxy settings were reused with TLS validation enabled:

```bash
export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

Install only the release JAR with Minecraft 1.21.1, Java 21, Fabric Loader 0.16.9 and Fabric API 0.116.1+1.21.1. Replace the older mod JAR; do not load duplicate releases, `-dev`, `-sources` or the GameTest mod.

## Remaining limitations and Minecraft acceptance

The redesigned views use Minecraft-native fonts, approved original sprites and procedural dark/gold panels. Narrow windows reflow and use internal scrolling; some long labels have full hover/detail access. Offline remote skin previews, actual pixel appearance, viewport-edge clipping and popup placement cannot be established without the graphical client. Class references remain a level 1–3 introduction, and sheet progression does not implement a leveling engine. Equipment definitions/mapping notes are future integration data; quantity/equipped flags do not grant items or alter combat inputs. Spell/feature effects, ability generation and character check/save execution remain unavailable.

1. Back up a disposable world, install the release JAR and reopen an existing draft. Check all **nine** creator tabs and all **six** dashboard tabs; Manage Character must be separate. Verify the old background and existing personal data remain.
2. Reopen/reselect alignment several times. Click outside and press Escape while open; neither should select the race underneath or unexpectedly exit the creator. Switch base/subraces and compare preview versus saved state.
3. Inspect every class, especially Barbarian/Bard/Cleric/Ranger, then the six abilities. Check complete motifs, centered labels/controls, point-buy budget, invalid/duplicate standard-array assignments and unchanged saved effective scores. Confirm no fake in-game roll action.
4. Toggle skills, saves and expertise. Check governing abilities/totals, no duplicate grants, expertise removal behavior, class/background guidance and retained custom/tool/language entries. Save and reopen.
5. Add two intentional items with the same name. Search names/descriptions, switch categories, change quantity/equipped state, edit properties/notes and navigate/refresh/reopen. Confirm exactly the intended records remain and no Minecraft items/combat stats are granted.
6. Write multiline background/details. Save while typing more text or with delayed server acknowledgement; newer typing must remain unsaved rather than disappear. Resize/change scale and verify caret/selection/paragraph retention. Test a concurrent edit/rejected save from a second client.
7. Review the formatted sheet and finish through validation. Exercise a validation error and the first-error route. Keep the library character or submit an independent campaign copy. Compare separate inventories/resources afterward.
8. On the dashboard, test library HP/temp HP, permission-allowed inspiration/death pips/hit dice/exhaustion/resources, feature pools and spell slots. Campaign HP must remain read-only. Compare owner/controller/DM/denied roles and bound/encounter policy; no change may falsely appear saved.
9. Inspect supported attacks/current-turn information and use the existing encounter link; check approval, assignment, binding, rest/history and exceptional control semantics through Manage Character. Verify gameplay/combat/HUD remain unchanged. Leave/rejoin the world and reopen selections/drafts—this live client check remains unperformed in the cloud.
10. Capture creator Identity/dropdown, Class, Ability Points, Proficiencies, Equipment, Review & Finish, and each dashboard destination at GUI scales **2, 3, 4**, plus **8** where the resolution permits. Include window dimensions, any clipped/misaligned areas, and client/server crash logs. Send screenshots and approval before further UI/mechanics development.

Stop after this checkpoint. The implementation branch is pushed to the user’s fork; visual acceptance and live-client feedback remain outstanding.
