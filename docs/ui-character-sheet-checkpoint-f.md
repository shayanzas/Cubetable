# Cubetable — Character Sheet Checkpoint F

## Delivery and ancestry

Branch: `dev/ui-character-sheet-checkpoint-f` on `shayanzas/Cubetable`.
Verified base: `856d4894b585955b67651d83bfd98461936d2be1` (`dev/ui-character-sheet-checkpoint-e`); local and remote E matched before edits and the initial tree was clean.

Logical implementation commits:

- `2dcaa7422ade08e6cc3d966925fdeeed87224c10` — acknowledged creator navigation, compact one-shot confirmations, Rogue proficiency layout, subrace policy and presentation.
- `a45248acc6ed703216ac9885697b55ce32e3137f` — authorized lossless class-pixel cleanup, reproducible sources and audit.
- `69b28e7dddf0030fae701a5a0f73dc24c6023be2` — focused foundation and authenticated Minecraft gateway regressions.

This report is a subsequent documentation-only commit. The final branch tip can be obtained with `git rev-parse dev/ui-character-sheet-checkpoint-f`. No merge, force-push, upstream write or PR is part of this checkpoint.

## Implemented changes

### Navigation and conflict handling

- Creator actions now read **Back | Save Draft | Next**; Review retains **Finish Character**. Next is emphasized and Save Draft remains secondary, with the existing consistent footer geometry and keyboard focus.
- Proficiencies, Details and staged Inventory currency use their save operation for direct tab clicks. Structurally valid incomplete drafts can advance only after checked acknowledgement; invalid, failed, stale or unavailable saves retain input and do not advance.
- Root editable tabs also save the active page before navigation. Back retains its existing editing behavior and discard confirmation on exit.
- Removed the root reload hook that manufactured another save/navigation intent when returning to a specialized page. Child navigation no longer changes the root step before its operation succeeds.
- Pending actions are explicit, one-shot and consumed before dispatch. Cancel clears the operation and returns to editing; it cannot automatically reopen the same conflict dialog. A new user action can still request another review of an unresolved genuine conflict.
- Confirm checks the reviewed revision again after the authoritative return read. Another intervening edit cancels the intent instead of repeatedly reopening a dialog or silently overwriting it. Repository compare-and-set revisions remain enforced.
- Confirmation panels are centered and sized to content, up to 480 logical pixels wide and 260 high. Long comparisons scroll inside them; Cancel starts focused, Escape cancels and rapid duplicate inputs cannot resolve a modal twice. Root conflicts show proposed saved-to-staged field changes; proficiency conflicts include current and proposed choices.

### Proficiencies

Section order is **Class Skill Choices → Skills → Expertise (when eligible) → Tools & Languages → Saving Throws**.

- Rogue level 1 still requires exactly four eligible 2014 class skills before finishing, while partial selections can be saved. Three columns remain at practical widths, with the existing narrower responsive fallback.
- Expertise is one dedicated section containing trained skill choices and, for Rogue, thieves' tools. The explanation states that it doubles proficiency for an eligible skill or tool. Two choices at Rogue level 1 allow either two proficient skills or one skill plus thieves' tools.
- Removing a class skill removes dependent class Expertise only when that skill has no retained historical proficiency. Raw historical/custom grants are not rewritten. Former choices incompatible with a changed class remain removable for explicit reconciliation.
- Bard Expertise remains gated at levels 3 and 10; Rogue's later entitlement and all twelve class skill/save restrictions remain unchanged. Ineligible classes receive no new Expertise.
- Rogue, its level-3 Thief subclass and thieves' tools have distinct explanatory labels. Rogue's granted tool proficiency is visible without a separate editor.
- Recorded tool/language lists remain visible. The normal guided page no longer opens an unrestricted free-form grants editor. New F guided standard-class writes also reject arbitrary additional tools/languages; existing and explicitly custom manual records retain their compatibility workflow. Unsupported species/class language or tool-choice automation is deferred.
- All six calculated Saving Throws appear last. Proficient rows use gold bold text with source/breakdown tooltips, without repeated “class” suffixes or “mandatory class grants” wording.

### Race/subrace policy and content audit

The normal **Base Race — No Subrace** button is removed. Choosing a race previews its base identity; a required subrace can remain missing in a draft but blocks standard finalization for new F guided characters.

A versioned `ui_race_policy` collection is attached to new blank guided records. It persists through reload, duplication and independent campaign copies and cannot be stripped by a later sheet transition. Its absence preserves prior E/legacy/imported base-race records, including previously finalized records. Known built-in subraces with the wrong parent are rejected for policy-enrolled identity edits. Existing standalone subrace labels and explicit manual/custom identities remain compatible.

| Parent race | Built-in 2014 SRD 5.1 option | Recorded references |
| --- | --- | --- |
| Elf | High Elf | Elf base traits, Elf Weapon Training, Cantrip, Extra Language |
| Dwarf | Hill Dwarf | Dwarf base traits, Dwarven Toughness; manual ability implications |
| Halfling | Lightfoot Halfling | Halfling base traits, Naturally Stealthy; manual ability implications |
| Gnome | Rock Gnome | Gnome base traits, Artificer's Lore, Tinker; manual ability implications |

SRD 5.1 includes these four subraces. The audit did not identify additional built-in 2014 subraces covered by the project's current authorized source, so no invented or proprietary options were added. **Mountain Dwarf, Wood Elf, Drow, Stout Halfling and Forest Gnome** need an additional authorized source before their mechanics or descriptions can be redistributed. Their names in existing/custom records are preserved as manual identities, not advertised as supported built-in implementations. 2024/SRD 5.2 variants and BG3-specific variants were not substituted.

Human, Dragonborn, Half-Elf, Half-Orc and Tiefling have no redundant subrace selector. The existing nine race cards are preserved, with no unapproved race artwork. Trait descriptions/references remain informational; no racial scores, spells, resistance or combat effects are applied.

### Artwork, ability layout and inventory

- The user explicitly authorized lossless pixel cleanup during this checkpoint. Removed only 8-connected alpha components of at most eight pixels, entirely dark (RGB channels at most 48), with every pixel at least four pixels from colored artwork. Larger/connected shading and outlines are preserved.
- All retained RGBA pixels, E sprite bounds, dimensions, gutters and displayed scale remain exact. Ranger uses the unchanged approved D texture. Nearest-neighbor metadata and all twelve actual JAR texture references were verified. All 36 non-class sprites remain unchanged.
- `tools/cleanup_class_pixels_f.py` plus `tools/pack_ui_sprites.py` reproduce the sources and packaging. The committed `class-cleanup-f-audit.json` records the cleanup criteria and per-icon counts.

| Icon | Pixels removed |
| --- | ---: |
| Barbarian | 422 |
| Bard | 194 |
| Cleric | 231 |
| Druid | 107 |
| Fighter | 129 |
| Monk | 270 |
| Paladin | 496 |
| Ranger | 0 |
| Rogue | 121 |
| Sorcerer | 274 |
| Warlock | 77 |
| Wizard | 292 |
| **Total** | **2,613** |

- Ability allocation retains six horizontal rows, normal Point Buy / Manual modes and legacy Standard Array compatibility. Icons grow to 24 pixels at normal widths, names remain bold, and derived modifiers use a larger bold centered display with responsive padding. Numeric score fields remain native-sized/editable; modifiers remain read-only.
- Saved sidebar abilities use label and right-aligned numeric columns with a consistent 14-pixel row spacing. Saved/unsaved preview distinctions and completion counts remain visible when the sidebar has space.
- Add from Catalog is emphasized with taller readable actions; Add Custom Item remains secondary. **Keep as Separate Item** replaces technical instance terminology and explains ordinary stack merging versus a distinct item record. Stable IDs, quantities, metadata and persistence remain unchanged.
- The approved class grid, descriptions, Hit Die, primary abilities and grouped features remain. `ClassFeatureRegion` remains the preparation seam for a future independently scrollable feature area; no progression browser was added.
- The approved Character Sheet and grouped dashboard skill/save content are unchanged. There is no functional slot system, combat integration, Minecraft inventory synchronization or new DM management scope.

## Automated verification

Supported toolchain: Java 21 (`/workspace/.tools/jdk-21.0.12.1`), Gradle wrapper 8.10.2, existing Fabric Loom/dependency pins.

```bash
export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

Final full run: **PASS, 54 seconds**. **6,244 assertions across 28 foundation suites**, including **167 new Checkpoint F checks**; **115 required dedicated-server Minecraft GameTests**, zero failures/errors. The final XML report was inspected rather than relying only on process exit status. The prior E baseline also passed its existing 6,077 foundation assertions before implementation.

Focused regressions cover explicit one-shot continuation/cancellation, repeated rapid modal resolution, current-revision confirm guards, Proficiencies save-before-navigation with partial drafts, rejected/stale/unavailable saves, Rogue's four choices, eligible Expertise and thieves' tools, Bard level restrictions, class reconciliation, new-guided subrace finalization/restart and parent validation, legacy/custom identities, point-buy persistence and bounds, arbitrary tool/language rejection, inventory merging/distinct records and ownership. Existing Standard Array, campaign, persistence, authority and combat tests all remain passing. Asset checks verify conservative pixel changes, source transparency, packaged dimensions/gutters and unchanged Ranger artwork.

Runtime combat sources, mixins, build configuration and dependency pins were not modified. Two authenticated gateway GameTests were added; existing GameTests were not weakened.

Final local evidence: `/tmp/checkpoint-f-complete-validation.log`, `build/gametest-results.xml`, `/tmp/checkpoint-f-runtime-icon-audit.json`. Generated root logs were archived outside the checkout at `/tmp/cubetable-checkpoint-f-generated-logs/logs`.

## JAR and visual status

Installable JAR: `build/libs/tactical-combat-0.1.0.jar` (Minecraft 1.21.1 Fabric / Java 21).

- Bytes: 19316783
- SHA-256: `d129dca84e884b3cb6f3ed5293e5a0f8d19fe8355f18edca6ac8ba9eb8cc22be`
- Tested implementation tip: `69b28e7dddf0030fae701a5a0f73dc24c6023be2`

**Minecraft visual verification: NOT PERFORMED.** No display, Wayland session or Xvfb was available. Texture inspection and headless tests do not verify Minecraft layout appearance, mouse/focus behavior, multiplayer conflict reproduction or screenshot acceptance.

## Known limitations and deferred work

- Source/runtime pixel equality confirms preservation, not that every dark pixel is an artifact. Conservative cleanup deliberately leaves connected or ambiguous fragments and the approved Ranger untouched. Final artwork acceptance needs in-game screenshots.
- Non-SRD subraces need additional rights/content review; no new lawful built-in subrace was identified beyond the four already present. Custom records remain manually authorized informational content, without invented mechanical support.
- Existing tool/language lists do not encode detailed source attribution per entry. Where the record lacks provenance, the UI shows recorded information rather than inventing a species/background grant. New automatic tool/language choices are deferred.
- Old/imported records intentionally lack the new required-subrace policy; they are not retroactively made invalid. Long values/comparisons and small GUI scales may require scrolling. Existing local draft buffers are not a durable offline store.
- Level-feature scrolling/browser, equipment slots/stack splitting/hand restrictions, equipment-to-stat mapping, racial effects, automated class mechanics, spellcasting, leveling, dice automation and new DM tools remain deferred as requested.

## Minecraft acceptance checklist

Replace the previous Cubetable JAR in both client/server `mods` folders with the JAR above; keep Minecraft 1.21.1, Fabric Loader/API and Java 21 compatible with the project. Remove the old mod JAR to avoid duplicate loading, restart both sides, and retain a backup of your existing world/character data. Open the existing Cubetable hub and My Library creator. Test your normal GUI scale and at least one narrower scale.

1. **Identity:** New Elf, Dwarf, Halfling and Gnome show only the supported subrace button, without “No Subrace.” Save a base-race draft, reopen it, verify Finish reports the missing choice, then choose the supported subrace and verify save/reload and Review identity/trait references. Human, Dragonborn, Half-Elf, Half-Orc and Tiefling should have no redundant selector. There are no additional built-in non-SRD subraces to test; verify an existing/custom Wood Elf or base Elf remains unchanged.
2. **Class artwork:** Inspect all twelve class cards at normal/large GUI scale, especially Bard, Cleric, Monk and Wizard. Capture close screenshots of silhouettes, decorative stars/notes, outlines, transparency and focus. Ranger's bow should match E exactly. Race cards should have no new placeholders.
3. **Ability Points:** Point Buy and Manual show six consistently aligned rows. Verify native-sized scores, larger read-only centered modifiers, icon/name prominence and +/- hitboxes. Save and reopen different one/two-digit scores. Check overspend/out-of-range rejection and existing Standard Array compatibility.
4. **Sidebar:** At a width where the saved sidebar appears, save mixed one/two-digit scores and check their numeric right edges and label spacing. Unsaved preview values should not masquerade as saved values.
5. **Rogue skills:** Select Rogue level 1, choose fewer than four skills, click another tab directly and verify the partial choices persist after acknowledgement. Try ineligible/fifth choices; they should be unavailable or rejected. Four correct choices should satisfy the skill requirement before finishing.
6. **Expertise:** Check that one dedicated section shows only trained skills plus thieves' tools. Choose two skills, then test one skill plus thieves' tools. Removing a chosen skill should reconcile dependent class Expertise while keeping recorded historical grants. Verify no third choice, no Expertise for Wizard, and Bard level-1 absence/level-3 eligibility on an authorized recorded-level sheet.
7. **Tools & Languages:** Verify the section follows Expertise and Rogue thieves' tools are identifiable. Existing recorded lists remain; there is no ordinary unrestricted grants editor. Rogue/Thief/thieves' tools descriptions must be distinct.
8. **Saving Throws:** Scroll to the bottom and verify all six calculated values, gold bold proficient rows, useful source tooltips, and no repetitive class suffixes. Check class change reconciliation before finishing.
9. **Direct navigation/Next:** On each editable page stage a valid change, click a different creator tab without Save Draft, and verify one save then navigation. Repeat using Next. During delayed/rejected saves the page must retain edits and stay put; Save Draft must remain on the current page.
10. **Conflict Cancel/Confirm:** Exercise a real changed-revision case. One single-owner creator reproduction is to leave local Ability edits using Back, save choices in a specialized page and navigate so the root must reconcile its retained edit against the changed record. Cancel the review repeatedly, use Escape, wait several ticks and click rapidly: it must return to editing without automatically reopening. A fresh save action may reopen an unresolved genuine conflict. Confirm should apply only the reviewed operation once; another intervening revision must require a new explicit review and not overwrite it. Use a DM/owner concurrent campaign edit where permitted to test actual multiplayer conflict handling too.
11. **Inventory:** Add the same ordinary catalog item twice and verify quantity merging with its existing ID; use Keep as Separate Item and verify a second distinct record. Edit unique notes/properties, reopen, and verify IDs/quantities and currency survive. Catalog should be visually primary; Custom Item secondary. No new equipment slots or automatic physical inventory changes should appear.
12. **Review and Dashboard:** Finish a complete underspent Point Buy character through the remaining-point confirmation. Check Review and final Character Sheet identity/level/XP, six scores, HP/Temporary HP, AC/initiative/speed/proficiency, Inspiration, Death Saves, gold saves, conditions and bottom Equipment Summary/currency. Inspect grouped Skills & Saving Throws. Capture screenshots and any error/client/server logs for approval.

Stop here for Minecraft screenshot review and user approval; no gameplay expansion or merge follows this checkpoint.
