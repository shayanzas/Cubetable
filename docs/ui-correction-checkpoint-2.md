# UI correction checkpoint 2 — unified creator and approved icon integration

Continued `dev/ui-correction-checkpoint-2` from `42dcf538817bc418a28ff2cfcf1bedce95350712`, the complete previous UI checkpoint. The user explicitly approved this new branch because remote `dev/ui-overhaul` remains at the earlier `7129b96`. All three reference ZIPs were extracted under `/workspace/attachments/checkpoint2-references/Cubetable_UI_Checkpoint_2`. The checkpoint prompt and manifest were read before implementation; the approved icons, BG3 screenshots and all current-build feedback were inspected. The pasted request and subsequent explicit branch decision take precedence over older attached branch/flow wording.

No push, merge, reset, reclone, dependency changes or combat-engine changes. Character/campaign schema 4, library schema 1, authoritative mutation operations, original permissions, approval/binding rules and checkpoint-1 fixes remain intact. No replacement race artwork was added. The starting checkout contained no race-icon texture assets; its text race representation is retained rather than importing rejected generated designs or BG3 race artwork.

This is a functioning presentation checkpoint for Minecraft acceptance, **not a claim of verified visual fidelity**. Real graphical rendering, GUI-scale behavior, caret restoration through the new GUI-only accessors, icon appearance/filtering, and two-client multiplayer are **Not verified** on this display-less machine.

## Local commits

- `74ab844` — unified creation entry; separate existing tabletop import path; removed opening method choice and separate end-user validation/finalization actions.
- `88f7d70` — reusable creator workspace, approved-derived icon sprites, combined identity/catalog panels, optional multiline story editor, sheet-style review and new regressions.
- `4f51196` — ability/proficiency controls, clearer form pagination and informational rows without fake button framing.
- This report is committed separately after verification. No upstream branch was changed.

## Implemented behavior

Create Character sends one existing server-authenticated SRD/GUIDED creation request and opens the creator directly. There is no Guided/Manual opening chooser or Method section. The stored GUIDED/MANUAL modes remain supported for compatibility with existing drafts. An uncertain creation is never automatically replayed; the user returns to My Characters to reconcile it. In-progress library rows resume the creator directly. Existing local-sheet import is retained under Tabletop Tools, independently of the new creator entry.

The new presentation flow is Identity → Class → Background → Abilities → Proficiencies → Equipment → Features & Feats → Spells → Character Details → Overview → Review & Finish. Wide layouts use persistent left navigation and a right skin-face/saved-character summary; compact layouts use direct section tabs and an updated identity strip. Extremely small layouts use a section chooser. Native Minecraft text is used without shrinking it to fit.

Identity combines the canonical name and race. Enter or Continue saves the name; there is no separate Save Name button. Unsaved names remain staged, are marked with an asterisk in the compact summary, survive refresh/resize, and require explicit review after a concurrent revision change. Supported subraces appear inline at the target 480×257 logical size: High Elf, Hill Dwarf, Lightfoot Halfling and Rock Gnome. They are recorded in the existing species string, not a new schema field. Other/custom identities remain intact. BG3 Drow or Mephistopheles tiefling rules were not imported. At very small 320×180 layouts, the compact subrace/inspection fallback is a modal.

Class uses a native icon grid, searchable choices and an independently scrollable details panel. All twelve supported class icons are present. The panel distinguishes the saved choice from the inspected choice; Choose saves through the existing revision-checked API. Original SRD summaries show proficiencies, key attributes, skill-count guidance and **limited level-1–3 progression introductions**. No class feature, subclass, proficiency or ability increase is automatically applied.

Background uses approved-derived Acolyte and Custom Origin icons with a readable details panel. Acolyte is the SRD worked background. Other supplied background motifs are art assets, not falsely advertised SRD rule presets. Custom background data remains available through a section-specific editor.

Abilities keeps the existing standard-array, correct 27-point-buy, manual and external-roll calculators. Six scores remain visible together; framed cards show approved ability icons, modifiers and +/- controls. Focus/cursor survive reconstruction. External rolls are explicitly unverified. Server-verified ability generation remains deferred because the current dice service has no durable ability-generation/assignment contract. Race increases are not added automatically.

Proficiencies remains a dedicated staged checkbox menu for skills, saving throws and expertise, now with approved system icons and selected-tab indicators. Tools/languages remain custom records within that sub-flow. Class/background limits are guidance rather than complete legality enforcement. Equipment retains the searchable supported weapon/armor picker, quantities, equipped toggles and custom entries; no Minecraft inventory or canonical combat equipment is changed.

Optional Character Details separates appearance, biography, personality, ideals, bonds and flaws from early identity selection. Alignment, age and height are also retained. It uses larger multiline native editors. Text stays staged until server acknowledgement. On width changes, GUI-only accessors preserve the native caret/selection anchor while rebuilding wrapping; the native scroll offset is retained/clamped. Concurrent changes require explicit comparison. Features/feats and spells remain clearly manual; no new automation/catalog completeness is claimed.

Overview presents high-level saved identity, combat inputs, abilities and recorded proficiencies. Review & Finish presents scrollable framed sheet sections for identity/progression, combat inputs, abilities, proficiencies, equipment/currency, features, custom spells, narrative and validation issues. Every wrapped line remains reachable. It does not expose a raw JSON utility panel as the normal review.

Finish Character internally validates supported fields, retains validation separately from the canonical draft, exposes issue destinations, finalizes with the saved revision, and waits for the server response before offering Keep in My Library or Submit Independent Copy to Campaign. Existing durable submission intents, DM approval and separate entity binding are preserved. Finish is not complete D&D build-legality certification or DM approval.

Shared help clipping now uses the existing exception-safe scope. Noninteractive informational list rows no longer look like buttons. Repeated detail/reason tooltip text is removed, and form paging says Previous page / Next page. Creator search, inspection, scroll, staged names and field focus remain section-specific across updates and navigation.

## Icon assets

Runtime assets are `assets/tacticalcombat/textures/gui/creator/icons.png` and `class-extra.png`, with nearest-filtering metadata. They are generated pixel-art adaptations of the three approved reference sheets, not the reference sheets themselves. Sprite UV regions are explicit in `CreatorIcons`; tests verify all 48 regions fit their corresponding image and contain visible artwork. The generated main atlas contains 46 motifs; the separate Paladin/Sorcerer asset completes the twelve class motifs. No generated race/subrace sprites are included.

- Main atlas SHA-256: `202c4620ed03a0b8879b2e94340919ff8546f14df7bf96bd8f42e2d351825b83`.
- Additional class asset SHA-256: `426206e35fce441fbe59705fdc7d5dc801a1a27590ab134dcd8ba661e554e33e`.

All twelve class icons are used by the class grid. Six ability icons and skills/saves/expertise icons are used by dedicated editors. Equipment, feat, spell and inspect icons appear on section actions. Acolyte/Custom Origin appear in background selection. Remaining available system/background motifs do not imply implemented mechanics or supported rule presets. Their actual in-game legibility and UV composition require graphical acceptance.

## Reference mapping

| Reference | Implementation and limits |
|---|---|
| BG3 01 character summary card | Saved-character side summary on wide layouts; updated identity strip on compact layouts. No new 3D model. |
| BG3 02 Elf race | Identity race grid, trait descriptions and inline High Elf choice; existing race presentation retained. |
| BG3 03 Tiefling race | Readable SRD race/trait panel; no BG3-specific variants or spell automation. |
| BG3 04 Drow race | Selection/details layout inspiration only; Drow is not added as a supported SRD preset. |
| BG3 05 Mephistopheles subrace | Conditional subrace interaction inspiration only; supported SRD subraces stay inline at normal/target sizes. |
| BG3 06 Barbarian class | Icon grid, saved/inspected distinction, proficiencies and level-1–3 reference panel. |
| BG3 07 Fighter class | Fighter icon, proficiencies, Fighting Style/Second Wind/Action Surge/Champion introductions; all manual. |
| BG3 08 Fighting Style | Left progression navigation and dedicated choice-subflow pattern; no automatic fighting-style effects. |
| BG3 09 Criminal background | Icon selection plus readable background details; Acolyte/custom only, no Criminal rule preset invented. |
| BG3 10 Ability points | Six scores together, +/- controls, budget validation, approved ability glyphs. No BG3 racial bonus system. |
| BG3 11 Skills modal | Dedicated checkbox proficiency screen with skills/saves/expertise tabs and class/background guidance. |
| BG3 12 full creator overview | Persistent navigation, focused central workspace, updated summary; no 3D/equipment appearance work. |
| Approved class chart | Twelve derived pixel-art class sprites, individually UV-addressed. |
| Approved abilities/system chart | Derived system sprites integrated in abilities, proficiency tabs and section actions. |
| Approved background chart | Derived background sprites; Acolyte/custom are functional choices. Other glyphs do not add non-SRD mechanics. |
| Approved Cubetable hub/creator/ability/dashboard pictures | Charcoal panels, restrained gold, native text and sheet hierarchy. New checkpoint decisions override old Method/Manual/Validate/portrait-change controls. Hub/dashboard behavior remains preserved. |
| Feedback primary 01–03 and manual-mode extra | Opening Method/Guided/Manual removed; combined identity, custom fallbacks and dedicated late narrative editor. |
| Feedback primary 04–08 | Six-score icon cards, preserved point-buy rules, checkbox proficiencies, equipment picker/toggles and clearer paging. |
| Feedback primary 09–16 | Previous dashboard/DM/hub corrections preserved; draft resumption goes directly to creator; no new legacy UUID-staging route. |
| Feedback primary 17–20 and combat/log crops | Existing checkpoint-1 HUD stability/empty-log fixes preserved. Combat layout, gameplay and dice work remain outside this checkpoint. |

## Verification

Baseline before changes passed: **1,681 assertions and 109 required Minecraft GameTests**. Log: `/tmp/ui-checkpoint2-baseline.log`. Intermediate compilation/assertion runs passed after flow, layout and polish changes. Native GUI resizing required two read-only accessors because Minecraft's multiline editor wrapping width and selection anchor are private; compile errors encountered during that implementation were corrected before final validation.

Final command, from `/workspace/Cubetable`:

```bash
export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

Final result: **build passed; 2,340 assertions; 109 required GameTests**. Fresh `build/gametest-results.xml` contains **109 cases, zero failures, zero errors and zero skips**. Log: `/tmp/checkpoint2-final.log`. Final Gradle run completed successfully in 43 seconds. The 659 new creator assertions cover the actual shared geometry model, sprite bounds/visible art, SRD-only subrace handling, persisted combined identity, multiline review content, API validation/finalization, stale revisions, restart recovery and unchanged campaign state. Geometry is checked at 320×180, 480×257, 640×360, 854×480, 1280×720 and 1920×1080 logical sizes. Existing schema migrations, backups, permissions, source/copy independence, clipping and combat tests remain enabled.

Artifact: `/workspace/Cubetable/build/libs/tactical-combat-0.1.0.jar` (**3,884,669 bytes**).

SHA-256: `2d3750533cb309500ca6d0a68913b5900e37011814bf018ce014c23592a22b95`.

JAR integrity passed inspection; both creator textures and filtering metadata are packaged. Development GameTest/Foundation test classes are not packaged. Graphical rendering and real two-client acceptance are **Not verified**; model/mock-player assertions do not establish pixel fidelity, skin loading, real focus/caret behavior, tooltips, texture filtering or FPS.

## Remaining limits

- Class progression descriptions are introductions through level 3, not complete progression tables or subclass automation.
- Acolyte/custom backgrounds, limited existing equipment catalog, custom-only spells and manual features/feats remain deliberate scope limits.
- Dedicated abilities/proficiency/equipment editors and custom forms remain sub-flows; not every editor is embedded into one screen.
- Extremely small layouts use modal navigation/inspection/subrace fallbacks; target 480×257 uses direct tabs and inline subrace controls.
- No automatic racial/stat grants, full guided legality, server-verified ability rolls, level-up system, 3D customizer, equipment appearance, export, ownership transfer or combat changes.
- Graphical parity with the references still requires user acceptance. This environment has no graphical display/Xvfb; no screenshots of actual Minecraft UI were produced.

## Minecraft solo testing

1. Back up a disposable world. Use Java 21, Minecraft 1.21.1, Fabric Loader 0.16.9 and Fabric API 0.116.1+1.21.1. Install the release JAR above, replacing the earlier Cubetable JAR rather than loading both copies. Keep `-dev`/`-sources` and test-mod JARs out of the mods folder.
2. Enter the world and open Cubetable → My Characters → Create Character. It should open Identity directly, with no Guided/Manual chooser or Method step. Existing drafts should also open the creator directly.
3. Enter a name, press Enter or Continue, and verify acknowledgement. Type a different unsaved name, resize/change GUI scale and refresh: the text must remain staged with `*`, not silently overwrite newer data.
4. Inspect Elf and choose High Elf inline. Repeat Hill Dwarf, Lightfoot Halfling and Rock Gnome; choose Human/Tiefling and verify there are no fabricated subraces. Confirm race artwork was not replaced and no racial bonuses silently change ability values.
5. Inspect/search all twelve classes. Check each icon, readable scrolling descriptions, the saved-versus-preview distinction and Choose action. Compare Fighter's proficiencies/level-1–3 guidance without expecting automated features. Test keyboard arrows/Enter in the grid.
6. Inspect Acolyte and Custom background. Save custom race/class/background data through the relevant small form; story fields should not be repeated there.
7. Open Abilities: standard array uses each value once; point buy allows 8–15 and costs 27 for 15/15/15/8/8/8. Over-budget/invalid values must be rejected. All six scores, +/- controls, icons and modifier labels must remain reachable. External rolls must stay labeled unverified.
8. Open Proficiencies: toggle skills/saves/expertise, scroll, switch tabs and save. Verify class/background guidance is clearly manual. Open equipment, search a supported entry, set quantity/equipped state, and verify no Minecraft item or automatic combat stat is granted.
9. Open Character Details. Write several paragraphs, select a range of text, change scale/resolution, switch narrative tabs and return. Check text, caret/selection, wrapping and scroll. Save/reopen/reconnect and confirm story persistence. Cancel discard confirmation and verify the text survives.
10. Inspect Overview and Review & Finish. Read every framed sheet section by wheel/Page Up/Page Down, including long notes/spell descriptions. Finish should internally validate and only show completion after server acknowledgement. Errors must retain the draft and offer a relevant section link.
11. Choose Keep in My Library or Submit Independent Copy to Campaign. Existing campaign copies must remain independent. Recheck DM approval, selection versus binding, source deletion safeguards and reconnect selection recovery from checkpoint 1.
12. Under Tabletop Tools, import an existing local Generic D20/Percentile sheet and confirm its source/profile are preserved; the new SRD creator must not rewrite its combat representation.
13. Capture Identity (including subrace), Fighter/Class grid, Background, six Abilities, Proficiencies, multiline Details, Overview and Review at GUI scales **2, 3, 4 and 8**, including approximately **480×257 logical size**. Check icon silhouettes/UVs, text sharpness, clipping, status separation, focus and scrolling. Supply crash logs if any GUI accessor or field reconstruction fails.
14. Stop and send screenshots/feedback for approval. Real two-client conflicts, ownership/controller/DM, latency and revocation remain a separate Not verified acceptance gate. Do not proceed to combat/dice work from this checkpoint.
