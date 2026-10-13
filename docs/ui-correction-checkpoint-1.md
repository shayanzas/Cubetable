# UI correction checkpoint 1 — functional stabilization and usability

Continued the existing `dev/ui-overhaul` branch at `7129b965bf9206c43018e448d2a1c30a268eaa52`. Existing checkpoints 1–5 and their backend stabilization remain intact. No reset, reclone, push, merge, dependency change or upstream modification occurred. No combat movement, attacks, damage, encounter lifecycle, AI, water, ranged resolution or server dice trust was changed.

This is an improved functional checkpoint, **not complete approved-mockup fidelity or a complete guided rules implementation**. Graphical Minecraft rendering and real two-client acceptance are **Not verified**. All approved and observed images were inspected; the future HUD/dice image was inspected for context only and was not implemented.

## Local commits

- A: `404f776` — clipping, canonical draft replies, separate footer/status regions, retained list state and authorized selection recovery.
- B: `0a551ad` — creator choices, supported finish flow and authenticated source deletion.
- C: `3c66061` — dashboard/DM/navigation plus final creator/state regressions.
- This report is committed separately after final validation.

## P0 outcomes

- HUD clipping now acquires exactly one scope for each rendered panel and releases it with try-with-resources, including exceptional exits. No unconditional pop remains at the start/end of HUD rendering. Empty/no-visible-panel rendering performs no scissor operations. List clipping uses the same scope.
- The actual `LIBRARY_VALIDATE` reply is reproduced in tests: it contains validation results, not a sheet. Validation is retained separately; canonical drafts remain intact through validation and subsequent stage rendering/navigation. Partial/error mutation snapshots are rejected before publication, and malformed callback diagnostics are logged.
- Finalize's distinct confirmation/request/CAS/readback path was audited. Valid server replies contain complete snapshots. Unexpected successful replies are guarded and do not replace the previous draft. Valid finalize-then-project and incomplete-finalize regressions pass. The separately reported graphical Finalize crash was not reproduced in a graphical client; its original stack trace was unavailable.
- Footer controls and full-width status text occupy separate rows. Full error/details remain available on hover. Wizard navigation uses responsive button widths; actions scroll inside content instead of extending beyond the panel. All six abilities use three columns/two rows with +/- controls and visible full-width budget/error status.
- Lists retain selection identity and offset through refresh/ACK, clamp on shrink, and preserve their position through management-screen reconstruction. Management rows publish together rather than progressively resetting scroll. Record-form text, focused field and cursor survive reconstruction. Inline name edits retain their text and use explicit concurrent-change confirmation. Dashboard tab and overview card position remain stable across refresh/ACK.
- Empty event overlays are hidden. Ordinary successful history revalidation builds a replacement while retaining currently authorized content, then publishes it atomically. Role changes, scope denial, session changes and lease expiry clear private content. RATE_LIMIT keeps data/edits with a readable busy message and existing bounded backoff; it never authorizes a mutation or silently retries one.
- Last selections persist in a separate world-local `tacticalcombat/ui-selections.json`, schema 1, using validated atomic files/backups and a 4,096-player bound. Character/campaign schema 4 is unchanged. ACTIVE rechecks membership, visibility, same campaign and controller/manager eligibility before restoring; encounter locks suppress restoration, revocation clears remembered access, missing campaigns fail safely. Campaign selection clears remembered character selection. Restoring selections never binds/unbinds an entity.

## Creator outcomes and remaining scope

The eleven-stage shell uses a stable sidebar on wide layouts and visible compact tabs on narrow layouts. Basics embeds canonical name editing/saving. Species, class and background embed searchable supported choices, current selection summaries and detailed hover guidance. Narrative, progression and structured record editing still use grouped auxiliary editors; the entire creator is not one fully embedded form.

Create Character replaces New draft. Delete Character performs explicit confirmation and authenticated ownership/revision validation. Administrators cannot delete another player's private source. Deleting a source preserves independent campaign copies, approved/bound entities, HP, resources and campaign state.

Skills/saves/expertise use staged checkbox lists. Class skill pools/counts and Acolyte background choices are labeled guidance. Custom tools/languages remain available separately. Duplicate sources, multiclass choices, choice counts and prerequisites are **not server-enforced full build legality**. The picker does not apply combat bonuses.

Equipment uses the existing limited SRD weapon/armor catalog as a picker, with quantity editing and equipped-state toggles. Custom entries remain supported. Boolean record fields use controls instead of raw true/false typing. Features include a deliberately limited Grappler SRD feat reference and manual/custom records. Spells remain searchable custom records; a complete standard spell catalog is not supplied.

Finish Character validates supported fields, displays actionable stage links for errors, finalizes with the saved server revision, waits for ACK, then offers Keep in My Library or Submit Independent Copy to Campaign. Submission reuses the existing durable intent journal, independent-copy confirmation and server policy. Unsaved inline names must be saved before finishing. Finalization, DM approval, selection and physical binding stay distinct.

Point buy remains the correct 27-point calculator; standard array and manual/external entry remain supported. **Server-verified ability generation is deferred**: the existing trusted dice service has no ability-generation/assignment persistence contract. External rolls remain explicitly unverified. No client result is presented as server-authoritative. Full automatic class/species/feat/spell grants, whole-build legality, advancement and spellcasting remain outside this implementation.

## Dashboard and DM outcomes

Overview now uses a dense, scrollable card grid for authoritative/saved HP, temporary HP, AC, initiative, speed, proficiency, manual inspiration/death saves, progression, six abilities, skills, equipment and features. Cards navigate to dedicated tabs; hover details preserve authority/manual labels. A compact skin-face/identity/approval/XP header replaces repeated identity rows. Actual SRD 2014 XP thresholds remain unchanged (level 2 is 300–900), regardless of the illustration's inconsistent numbers.

All nine tabs are directly selectable, with compact multi-row layouts and full tooltips. Dedicated editing tabs retain existing supported controls and server restrictions. Manage directly exposes permitted Approve, Request Changes/Revoke, Assign and Bind workflows with confirmations and server checks. Existing advanced management remains accessible. Request Changes uses the existing revoke/status operation; it does not invent a persistent written review conversation.

Hub DM Tools opens the human-readable campaign roster, then the dashboard and its Manage tab, instead of the legacy UUID/revision/staging workspace. The hub's Open Character Sheet shortcut is available in compact layouts and opens the selected eligible SRD character (or the existing bound fallback when no selection exists). Manual sheets remain under Tabletop Tools.

**Partial visual scope:** the dashboard has dense cards and a header, but does not reproduce the reference's full portrait/identity left rail, separate right equipment column, original icon artwork or every illustrated panel. Dedicated tabs still use existing scrollable records. Current-player previews use Minecraft's skin face; full player-model previews and remote skin projection are not implemented. No Change Skin/Change Portrait, export, Level Up, takeover/delegation, future HUD or future dice animation was added.

## Reference-to-screen mapping

| Approved image | Screen mapping and achieved scope |
|---|---|
| 01 Main Hub Approved | HubScreen: preserved six destinations, campaign/character context; direct selected SRD sheet shortcut and roster DM route. Original art/icon rendering and exact stone treatment remain partial. |
| 02 Guided Creator 11 Stages | CharacterWizardScreen: eleven direct stages, embedded basics/catalog choices, compact sidebar/tabs, scrollable content; ProficiencyPickerScreen and structured record editors. Full class/background automation, comprehensive spell data and fully embedded stage editing remain partial. |
| 03 Character Creator Single Screen | Persistent central wizard with name/catalog controls, selected summaries and wide skin-face preview. Eleven-stage specification takes precedence over illustration's six stages; portrait-change controls are intentionally absent. |
| 04 Ability Scores Picker | AbilityScoreScreen: six scores together, +/- editing, standard array/27-point/manual/external methods and separate validation status. Full model portrait and authoritative ability-roll generation remain deferred. |
| 05 Character Dashboard Overview | CharacterDashboardScreen/RpgCardGrid: compact portrait/name/class/level/XP plus dense metrics, abilities/skills and record summaries. Multi-rail illustrated composition remains partial; native text and original procedural charcoal/gold panels are used. |
| 06 Character Dashboard Tabs | Nine direct tab controls, contextual Manage actions and preserved tab/card state. Dedicated record tabs remain existing functional lists/editors; exact illustrated tab icons/panel structure are not claimed. |
| 07 Creator Library DM Roster | Create Character, confirmed real Delete Character, Finish/optional independent submission, human-readable All/Pending/Approved roster and direct review/assignment/binding actions. Parchment artwork, submitted-date display and written change requests are not implemented. |

The ten observed screenshots map to the corrected footer/status separation, inline catalog choices, staged proficiency controls, equipment toggle/picker, readable DM routes, dense Overview and direct tabs, retained rate-limit context and hidden empty event overlay. Their graphical before/after comparison remains Not verified.

## Validation

Baseline before edits: full Java 21 Gradle build and Minecraft GameTests passed: **1,523 foundation assertions; 107 required GameTests**. Log: `/tmp/ui-correction-baseline.log`.

Final full command is run from `/workspace/Cubetable`:

```bash
export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

TLS verification uses the system truststore. Pinned dependencies/toolchain were preserved. Final log: `/tmp/ui-correction-final.log`; fresh GameTest XML: `build/gametest-results.xml`.

Final foundation suites: regression 320; repository 39; library 32; lifecycle 31; RPG records 20; presentation/workflow 27; hub 118; management 102; wizard 55; **UI correction 158**; dashboard 76; HUD 68; stabilization 67; synchronization 59; persistence interruptions 8; SRD core 319; movement 91; authoring/resources 80; resource faults 11. Total: **1,681 assertions**.

Correction coverage includes clip nesting/exception exits/double-close, actual Validate/Finalize replies followed by stage projection/navigation, unexpected snapshots, retained/clamped list/card state, authorization-history replacement/revocation, selection restart/encounter locks/control/membership/missing-campaign rejection, authenticated deletion/CAS/source-copy isolation, class/feat guidance and direct-tab/body/footer geometry at 320×180, 480×257, 640×360, 854×480 and 1280×720. The existing schema-1/2/3→4 migrations, schema-4 approved/bound character/manual/runtime preservation, atomic interruption, valid backup recovery and future-schema refusal suites passed before and after edits.

Two added authenticated Minecraft gateway tests cover reconnect restoration/revocation without binding and private-source deletion ownership/revision checks. Final build passed: **109 required GameTests**, with **109 fresh XML cases, 0 failures, 0 errors and 0 skipped cases**. The XML run timestamp is `2026-10-13T18:32:09.061901092Z`; Gradle completed successfully in 1m 5s. No tests were removed or weakened. Headless model/mock-player checks do not exercise GUI rendering, real mouse/focus behavior, skin loading, real GUI scales 2/3/4/8, FPS or two-client multiplayer.

Release output: `/workspace/Cubetable/build/libs/tactical-combat-0.1.0.jar`. Size: **966,811 bytes**. SHA-256: `e7825190fde058585e5987f950cba8da3247b2ae4e3413bb5577f9b1c44c4986`.

## Changed implementation paths

- Character/storage adapters: `character/CharacterService.java`, `character/SelectionStore.java`, `character/LibraryRepository.java`.
- Existing API/gateway: `uiapi/UiApiService.java`, `uiapi/UiNetworking.java`.
- Shared client UI: `client/ui/ClipScope.java`, `DraftReplies.java`, `RpgScreen.java`, `RpgList.java`, `RpgCardGrid.java`, `ManagementScreen.java`, `RecordFormScreen.java`, `DashboardLayout.java`.
- Creator/library: `CharacterWizardScreen.java`, `WizardModel.java`, `AbilityScoreScreen.java`, `ProficiencyPickerScreen.java`, `Srd2014Catalog.java`, `WizardRecordsScreen.java`, `LibraryScreen.java`, `LibraryCharacterScreen.java`.
- Dashboard/navigation/review: `CharacterDashboardScreen.java`, `DashboardModel.java`, `HubScreen.java`, `HubState.java`, `DestinationScreen.java`, `CampaignRosterScreen.java`, `CampaignCharacterScreen.java`.
- HUD stability only: `CubetableHud.java`, `HudSession.java`.
- Regression runners: `src/test/java/dev/tacticalcombat/FoundationTests.java`, `src/test/java/dev/tacticalcombat/client/ui/UiCorrectionTests.java`, `src/gametest/java/dev/tacticalcombat/combat/HubGatewayGameTests.java`.

Source files above are under `src/main/java/dev/tacticalcombat/` unless otherwise stated. No original tests or user campaign/library data were removed; existing generated build/run/cache data was preserved.

## Solo Minecraft acceptance checklist

Use a backed-up disposable world, Java 21, Minecraft 1.21.1, Fabric Loader 0.16.9 and Fabric API 0.116.1+1.21.1. Record the JAR hash, resolution/GUI scale, logs and screenshots.

1. Enter/re-enter a world with no campaign and with a selected campaign: no scissor crash; empty event overlay stays hidden. Generate authorized events and observe refresh for at least 30 seconds; content should not repeatedly blank.
2. At GUI scales 2, 3, 4 and 8 (including approximately 480×257 logical size), open Hub, creator, ability editor, all dashboard tabs and DM roster. Check sharp text, accessible scrolling, footer separation, complete hover errors, Tab/arrows/Enter and resizing.
3. Create Guided and Manual characters. Edit/save the embedded name, search/select species/class/background, choose skills/saves/expertise, add picked/custom equipment with quantity/equipped toggles, and inspect manual feat/custom spell labels.
4. Validate, then navigate/render every stage. Finish with invalid fields and follow error links; finish a valid saved draft, choose Keep or Submit, and verify success appears only after server confirmation.
5. Close/reconnect/restart and resume draft/selection. Selection must not bind. Test an encounter lock, then access revocation in a controlled fixture; stale remembered selections must not restore.
6. Open the selected SRD sheet directly; inspect Overview's HP/temp/AC/speed/initiative/proficiency/XP and manual trackers against server state. Change a tracker after scrolling; tab/position must remain stable. Manual tabletop sheet stays separate.
7. As DM use roster → inspect → Manage → Approve / Request Changes / Assign / Bind. Verify confirmations and unchanged original ownership; no UUID staging is required. Bound/encounter/policy restrictions remain enforced.
8. Delete a personal source with confirmation and verify any independent approved/bound campaign copy, HP/resources and entity remain intact. Test cancelled deletion and rejected/stale edits; text remains staged with explicit comparison.
9. Send screenshots and relevant crash logs for this checkpoint. Real two-client owner/controller/DM, latency/conflict/revocation and multiplayer acceptance remain a separate **Not verified** gate.

Stop here and wait for screenshots/approval. Do not implement the future combat HUD, dice animation or gameplay/combat fixes as continuation of this checkpoint.
