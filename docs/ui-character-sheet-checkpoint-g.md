# Cubetable — Checkpoint G

## Branch, ancestry and tested commits

Repository: `shayanzas/Cubetable`. Branch: `dev/ui-character-sheet-checkpoint-g`.

Fetched Checkpoint F and verified both local HEAD and FETCH_HEAD were `4fcb73a0a749e0f4027d6b96e0ace91fac742e77` before editing. The starting working tree was clean. G descends directly from that verified F tip; no restart, reclone, merge, force-push or upstream write was performed.

Implementation commits:

- `2f7cd4bc64ba125566c16036b1dfa60bf020641d` — atomic guided class change, persistent completion safeguard and base-only validation.
- `500d7dccb3e3771fdfc1ca0bd20eb046f3a7413e` — confirmation and lost-reply recovery, explicit Base Traits selection, Saving Throws panel and concise tools presentation.
- `f21ee44be18b79b2695847373343d5a4efcb4510` — final tested implementation, including all G regressions and three authenticated gateway GameTests.

This report is the subsequent documentation-only commit. The delivered branch tip is reported in the final delivery message and can be verified with `git rev-parse dev/ui-character-sheet-checkpoint-g`; documentation does not change the tested JAR.

## Implementation and preservation rules

### Race selection

Elf, Dwarf, Halfling and Gnome each offer an explicit **Race — Base Traits** button alongside High Elf, Hill Dwarf, Lightfoot Halfling or Rock Gnome respectively. “No Subrace” is not used. Selecting the race card previews its parent traits and never substitutes a subrace. Selecting Base Traits stores an intentional identity such as `Elf — Base Traits`; selecting High Elf stores `Elf — High Elf`. Switching between them immediately changes the local preview and trait references.

Explicit base-only selections save, reload and duplicate without substitution. They are valid partial/custom drafts, not falsely complete standard 2014 characters. Existing `ui_race_policy` version-1 metadata remains unchanged: policy-enrolled standard guided finalization still requires the supported subrace. The preview and finalization error explain that this creator has no DM-approved base-only override. It does not silently add High Elf features or manufacture an approval.

The existing manual/custom library workflow remains separate: a manual record can retain an explicit base identity, finalize under its existing validation and submit an independent campaign copy. Existing campaign DM approval is still a separate authorized operation. This does not convert a policy-enrolled guided draft into an approved exception. Historical bare parent-race identities and standalone/custom subrace strings are preserved; wrong-parent supported subraces remain rejected. No new race catalog, art or automatic racial effects were added. Races without subraces retain no redundant chooser.

### One authoritative class-change transaction

The creator intercepts selection of another class on an eligible guided creation draft and opens **Change Class? → Cancel | Change Class**. It lists the reset and preservation boundary. Hover/tooltips and inspecting features do not submit a change. Selecting the same canonical class is a no-op. The custom-class name editor uses the same confirmation path instead of bypassing it through a free-form identity update.

`ClassChangeIntent` captures the saved revision, saved/selected classes, current allocation method and staged score values. Returning from the one-shot modal fetches the authoritative record before dispatch. Cancel preserves saved and staged values. Changed revisions or score buffers invalidate confirmation and require a new user action. Duplicate modal input cannot submit twice.

The client submits **one** `LIBRARY_UPDATE` with section `classChange` and only `fromClass`, `characterClass`, `abilityMethod`. `LibraryRepository` checks authenticated ownership, expected revision, profile/mode and unfinished lifecycle. `GuidedClassChange` builds a complete replacement sheet in memory, then the repository validates and writes it using the existing same-directory atomic JSON replacement. Only the complete persisted result advances the revision by one and returns an acknowledgement. There are no intermediate class/skills/ability writes.

The transaction:

1. Applies the new canonical class and clears its former subclass.
2. Clears guided class skill and guided Expertise arrays together.
3. Retains the compatible method and resets all six scores: Point Buy **8/8/8/8/8/8**, Manual/External **10/10/10/10/10/10** (the existing SRD template defaults), historical Standard Array **15/14/13/12/10/8** in the established ability order.
4. Recomputes effective saving throws, skill eligibility/counts, Expertise entitlement and Rogue thieves' tools from the new class using unchanged 2014 rules.
5. Validates a structurally valid draft, allowing new required choices to remain incomplete until Finish.

The request cannot supply arbitrary scores, skill arrays, authority flags or an unknown method. Standard Array cannot be newly introduced through this operation on a non-Array record. Same-class reset requests are rejected without writing. Raw class edits on an eligible draft are rejected once a saved class exists, preventing other editors from retaining an incompatible mixture or bypassing the reset. Initial selection of a previously blank class remains a normal staged selection.

Name, race/subrace, age, height, alignment, appearance, biography, background, raw independently granted skills/Expertise/saves/tools/languages, manual feature/progression records, inventory and currency are preserved. A separately granted Stealth proficiency is retained even when guided Rogue Stealth is cleared. No grant is erased merely because its name matches a former class choice. Only explicitly class-derived creation fields are reset; manually authored feature/progression records have no reliable automatic class provenance and remain records for review.

On success, the creator replaces old staged ability buffers with the authoritative defaults while retaining unrelated staged identity edits. Ability Points explains that the allocation was reset. Navigation and final review use the updated snapshot. A committed change whose reply was lost can be recognized on refresh only at its exact next revision, target class, empty guided choices, retained method and complete fresh allocation. That receipt clears the obsolete staged scores without replaying the transaction or auto-advancing. Failed persistence and stale revisions preserve the saved record; they do not retry automatically.

### Completed characters and persistence migration

Destructive resets apply only to enrolled, unfinished **GUIDED / SRD** library creation drafts. Completed library records, manual records and campaign records retain their existing non-reset editing, manual reconciliation, authorization, binding locks and approval behavior. The campaign section editor cannot invoke the library-only `classChange` transaction.

The library now persists `creationClosed` after completion, even if later edits return the visible stage to DRAFT. This prevents a formerly completed character from accidentally regaining creation-reset eligibility. An explicit independent duplicate is a new draft with its own identity; resetting that duplicate does not change the source or an existing campaign copy.

**Storage compatibility:** schema 1 is read and migrated in memory; its COMPLETE rows become closed. The first library write uses **schema 2** to preserve lifecycle history. Prior F/older JARs intentionally refuse this newer schema rather than silently falling back to stale data. Back up `<world>/tacticalcombat/library.json`, its `.bak` and preferably the whole stopped world before installing G. Downgrading requires restoring the corresponding pre-G backup, not opening the upgraded library with F.

Legacy schema-1 DRAFT rows do not encode whether they were once completed and then edited by an older version. G respects their recorded stage rather than inventing missing history. Re-finalize such a valid record to establish its protected completed lifecycle before treating it as a completed source. Existing invalid F choices are not silently discarded on load; a confirmed actual class change resets them, while reselecting the current class deliberately remains a no-op.

### Targeted presentation

- Saving Throws now has a compact dark framed panel, with all six values in three/two/one responsive columns. Full names are used where space permits, otherwise unambiguous ability abbreviations. Proficient values remain bold gold, untrained values neutral, and each retains its breakdown/source tooltip. Rows are read-only and have no “class” suffix.
- The Rogue debugging paragraph is removed. **Thieves' Tools — Proficient** has a concise proficiency/Expertise tooltip; it is not repeated in the generic tools list.
- Section order remains Class Skill Choices → Skills → eligible Expertise → Tools & Languages → Saving Throws. The approved dedicated Expertise choices/counts and Bard level restrictions remain intact.
- **Back | Save Draft | Next**, checked save-before-navigation, Finish validation and unused-point confirmation remain. The F repeating-Cancel fix and shared one-shot modal control remain intact.
- Class artwork, class/ability grids, overall allocation layout, inventory organization, approved Review/Character Sheet and grouped dashboard skills/saves are preserved. Runtime combat, campaign permission code, resources, mixins, dependencies and build pins are unchanged. No equipment slots or H/I work was started.

## Exact changed files

- `src/gametest/java/dev/tacticalcombat/combat/UiFoundationGameTests.java`
- `src/main/java/dev/tacticalcombat/character/LibraryRepository.java`
- `src/main/java/dev/tacticalcombat/client/ui/AbilityWorkspace.java`
- `src/main/java/dev/tacticalcombat/client/ui/CharacterWizardScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ClassChangeIntent.java`
- `src/main/java/dev/tacticalcombat/client/ui/CreationSavingThrows.java`
- `src/main/java/dev/tacticalcombat/client/ui/CreatorDraftState.java`
- `src/main/java/dev/tacticalcombat/client/ui/CreatorFlow.java`
- `src/main/java/dev/tacticalcombat/client/ui/CreatorWorkspace.java`
- `src/main/java/dev/tacticalcombat/client/ui/CustomClassNameScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/IdentityModel.java`
- `src/main/java/dev/tacticalcombat/client/ui/ProficiencyPickerScreen.java`
- `src/main/java/dev/tacticalcombat/uiapi/GuidedClassChange.java`
- `src/main/java/dev/tacticalcombat/uiapi/GuidedRaceRules.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/character/CheckpointGTests.java`
- `docs/ui-character-sheet-checkpoint-g.md`

## Verification

Baseline F foundation suite passed **6,244 assertions** before implementation.

Final Java 21 `build runGametest`: **PASS in 59 seconds**. Actual totals: **6,702 assertions across 29 foundation suites**, including **458 G transaction/presentation assertions**; **118 required Minecraft GameTests**, zero failures/errors. All prior 115 GameTests and prior foundation checks remain; three gateway GameTests were added, without weakening earlier checks.

Focused coverage includes Rogue→Fighter, Fighter→Rogue, Bard→Wizard, Cleric→Paladin, Barbarian→Barbarian no-op, standard↔custom classes, incomplete/completed Expertise choices, independent historical grants, all method defaults, unsaved buffers, Cancel/rapid input, delayed/lost ACK, revision conflicts, raw-editor bypass, injected write failure before atomic rename, restart and lifecycle migration, completed/campaign edit locks, independent copies, explicit base/subrace identity roundtrips/traits/validation and actual Saving Throws panel captions/padding/tooltips.

The test server gateway separately verifies authenticated atomic resets, stale confirmation rejection and explicit base-trait persistence without false standard completion. Existing combat/authority suites pass. Headless tests do not establish graphical appearance or mouse/focus acceptance in Minecraft.

Supported command:

```bash
export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

Evidence: `/tmp/checkpoint-g-final-validation.log`, `build/gametest-results.xml`, `/tmp/checkpoint-g-delivery-metadata.json`. The current XML was checked for all 118 test cases and zero failures/errors. Generated root logs were archived at `/tmp/cubetable-checkpoint-g-generated-logs/logs`.

## Installable JAR and graphical status

- JAR: `/workspace/Cubetable/build/libs/tactical-combat-0.1.0.jar`
- Size: **19329159 bytes**
- SHA-256: `94976d019d83b85a1ab71a7442ec4e257f357c58304c5e7d459f40082cbb7d22`
- Final tested implementation: `f21ee44be18b79b2695847373343d5a4efcb4510`
- Minecraft **1.21.1 / Fabric / Java 21**, using the repository's existing Loader/API versions.

**Minecraft visual testing: NOT PERFORMED.** This environment has no display/Wayland session or Xvfb. No in-game screenshot approval, live mouse/focus acceptance or multiplayer visual acceptance is claimed. The code and tests are ready for user review.

## Manual Minecraft acceptance checklist

Stop client/server, retain the pre-G backup described above, replace the prior Cubetable JAR in both `mods` folders with this JAR, remove duplicate old copies, then restart. Use the same G build on both sides. Test the normal GUI scale and a narrower scale through the existing hub → My Library creator.

1. **Elf:** Click Elf, verify base preview without automatic High Elf. Choose Elf — Base Traits, Save Draft, reopen and check base identity/traits. Choose High Elf and verify the extra references; toggle back. Standard Finish should clearly block base-only completion rather than substitute High Elf or invent approval.
2. **Dwarf:** Repeat Base Traits ↔ Hill Dwarf, save/reopen and compare trait references.
3. **Halfling/Gnome:** Repeat Base Traits ↔ Lightfoot Halfling / Rock Gnome. Duplicate a saved base draft and verify the exact identity persists.
4. **Other races/legacy:** Human, Dragonborn, Half-Elf, Half-Orc and Tiefling have no redundant chooser. Check historical base/subrace/custom records remain intact. The existing manual/custom copy workflow must keep base identity and require its normal independent campaign approval.
5. **Rogue → Fighter:** Start with four Rogue skills, two Expertise choices and allocated abilities. Select Fighter: no saved/local values change before confirming. Confirm once. Expect 0/2 Fighter class skills, no class Expertise, Strength/Constitution saves, no former Rogue tool grant unless independently recorded. Biography, race, inventory/currency and independent grants remain.
6. **Fighter → Rogue:** Confirm Rogue. Expect 0/4 skills and 0/2 Expertise, Dexterity/Intelligence saves and thieves' tools proficiency. Select eligible skills, then two skills or one skill plus thieves' tools for Expertise. No ineligible or extra choices should be awarded.
7. **Ability reset:** Stage different scores before changing class. Confirm and inspect all six default values and reset notice; save/reopen. Point Buy has 27 remaining, Manual/External has six 10s, and an existing Array character keeps the valid array. No random or racial increases appear.
8. **Cancel/same-class/custom:** Cancel with mouse and Escape, repeat rapidly, wait several ticks; original saved and staged class/scores/choices remain and the modal does not reopen without another action. Reselecting the current class changes nothing. Test standard↔custom class names through the new name entry and the same confirmation path.
9. **Other classes/counts:** Check Bard→Wizard, Cleric→Paladin and a partially completed class. The new class starts with its correct required count, without former-class contradictory errors. Bard Expertise remains absent before level 3 and appears only at its eligible recorded levels.
10. **Saving Throws:** Capture the new panel at practical and narrow scales. All six names/abbreviations and correct modifiers should fit, with gold bold trained values, neutral untrained values and working tooltips, no class suffixes or misleading selectable state.
11. **Tools/Expertise:** The concise thieves' tools line replaces the debugging paragraph. Check its tooltip and eligible Expertise choice. Confirm the approved single Expertise section, dimming and section order are unchanged.
12. **Next/direct tabs:** Stage valid edits on each editable page; Next and a direct tab click save once and await acknowledgement. Save Draft stays put. During rejected/disconnected saves, values stay retained and no navigation occurs. After a lost class-change reply, refresh and verify the coherent saved class/reset is recovered without replay or stale score restoration.
13. **Conflict:** Change the same record through another authorized editing path while a class confirmation is open. Confirm must fail/require a fresh choice when the revision changed; it must not overwrite the intervening edit or loop dialogs. A real storage failure must leave the previously saved character coherent.
14. **Review & Finish:** Complete the new class's choices and inspect updated class, scores, saves, skills and Expertise. Missing-step guidance should describe the new choices only. Finish an underspent Point Buy character via its current-revision confirmation. Check the approved Character Sheet and grouped dashboard remain unchanged.
15. **Reload/completed/copies:** Reconnect/restart and reopen after changes. A completed source must not offer the destructive creation reset even after ordinary edits. Verify approved/bound campaign edit locks. Resetting a separately duplicated creation draft must not change its completed source or previously submitted campaign copies.

Capture Identity variants, class confirmation/Cancel, post-change Proficiencies/Ability Points, Saving Throws, Tools, Review and final Sheet screenshots. Report the logical GUI scale, reproduction steps and client/server logs for any discrepancy.

## Remaining/deferred

Visual acceptance remains open. Base-only standard guided finalization has no new approval override; use the existing separate manual/custom workflow where appropriate. Legacy missing lifecycle history has the boundary explained above. Existing incompatible F drafts are preserved until an explicit class change or manual reconciliation. Manual feature/progression records are not automatically reinterpreted as class mechanics. Independently scrollable class progression, equipment slots/stack splitting/hand restrictions, equipment/combat integration, automated racial/class effects, hub/library/manage-page redesign (H) and gameplay architecture/mechanics (I) remain deferred.

Stop after pushing G for Minecraft screenshot review and user approval. No merge or next-checkpoint work follows this delivery.
