# UI Overhaul — Sol Checkpoint 3

## Repository checkpoint

- Branch: `dev/ui-overhaul`
- Starting documentation commit: `ee96ece5d0dbb7b1b8980ae3fd639f8fc2b1ade0`
- Implementation commit: `b362f8b` (`Add guided and manual character creation wizard`)
- Documentation: this checkpoint document is committed separately after verification.
- Remote publication: **not performed**.

## Status summary

| Requirement | Status | Result |
|---|---|---|
| Eleven-stage creation workspace | Implemented | One reusable, responsive workspace provides Method, Basics, Species, Class, Background, Abilities, Proficiencies, Equipment, Features, Spells, and Review stages. |
| Guided creation | Partially implemented | Verified compact SRD 5.1 guidance and correct ability calculators are present. Full build-legality validation remains unavailable and is never advertised as complete. |
| Manual/custom creation | Implemented | Uses the same library drafts and section editors. Non-SRD profiles retain their existing sheet representation instead of being forced through SRD fields. |
| Save, close, and resume | Implemented | Draft creation opens the wizard; library drafts expose Resume. Section saves use `LIBRARY_GET` + revisioned `LIBRARY_UPDATE` and persist in library storage. |
| Conflict/error recovery | Implemented | Unsaved form text and ability values are retained after failure; stale revisions require explicit comparison/reconciliation; no per-keystroke network writes occur. |
| Finalization and submission | Implemented | Review routes to existing validation, finalization, and stable-identity campaign-copy submission. These states remain distinct from approval and binding. |
| Full post-creation dashboard | Deferred | Completed sources remain in the Checkpoint 2 detail flow. The wizard cannot reopen finalized/approved characters as a new creation draft. |
| Graphical and multiplayer acceptance | Not verified | No graphical Minecraft display or two-client session was available in this environment. |

## Creation screens and navigation

`CharacterWizardScreen` is the shared shell. A left stage list, central action panel, optional right preview, and bottom Previous/Next/Save controls are produced by `WizardModel.layout`. At widths below 560 pixels the stage list collapses into a short scrollable selector and the preview panel is removed, leaving the main editor readable. Stage status is recomputed from the latest acknowledged server draft.

The flow is:

`My Characters → Create → Guided/Manual → persistent draft → eleven-stage wizard → Review → library detail → Validate/Finalize/Submit`.

Direct stage navigation, keyboard-capable lists, mouse scrolling, Back, Escape, Previous, and Next use the Checkpoint 1 components. Editors warn before discarding staged text. The wizard itself owns no unsaved fields; its Save Draft action refreshes and reports the exact acknowledged server revision.

## Guided and Manual behavior

Guided mode provides compact, original informational metadata for the nine SRD races/species requested, twelve SRD classes, and the SRD Acolyte background example. It records choices in the existing identity section. It does not apply racial bonuses, class features, proficiencies, movement, equipment, subclasses, feats, or spells to authoritative combat data.

Manual mode uses the same typed records and grouped editors while prioritizing direct entry. Custom identity, narrative, proficiency, progression, inventory, feature, and spell records are supported. Manual mode cannot set approval, ownership, controller, binding, or executable automation fields. Generic D20, Percentile, Minecraft Tactical, and Manual Tabletop sheets continue using their established representation where SRD-only numeric fields do not apply.

`guidedRulesComplete:false` remains truthful. Completing every page is not finalization, structural validation is not full 2014 legality, finalization is not campaign submission, and submission is not campaign approval.

## Data and API integration

The implementation reuses:

- `LIBRARY_CREATE`, `LIBRARY_GET`, `LIBRARY_UPDATE`, `LIBRARY_VALIDATE`, `LIBRARY_FINALIZE`, and the Checkpoint 2 submission operation.
- Canonical `name`, `SrdAuthoring`, `SrdCharacterCodec`, `SheetSections`, and `RpgRecords` data; no parallel character schema was introduced.
- Full typed section replacement with server validation and expected revisions.
- Existing `RecordFormScreen`, `RpgScreen`, `RpgList`, `RpgButton`, confirmation dialogs, request queue, and submission journal.

Pronouns and weight do not have separate backend fields. The UI says to place those details in biography/appearance rather than silently dropping them or creating conflicting fields. Portrait remains an inert backend reference; this client does not download remote portraits.

## Ability-score methods

- **Standard Array — Implemented:** exact 2014 values `15, 14, 13, 12, 10, 8`; all six must be assigned once.
- **Point Buy — Implemented:** 27-point budget, bounds 8–15, and progressive costs 0/1/2/3/4/5/7/9.
- **Manual Entry — Implemented:** server-compatible values 1–30.
- **Rolled Scores — Partially implemented:** externally rolled values can be entered, clearly labelled as untrusted/manual. No client-generated result is represented as authoritative because no suitable server roll contract exists.

All six scores and modifiers are shown together. Scores update only the canonical existing SRD combat section. Species bonuses are not auto-applied, preventing double application.

## Structured records

- **Identity/narrative — Implemented:** canonical name, species, class, subclass, background, alignment, age, height/build, appearance, biography, personality, ideals, bonds, and flaws.
- **Progression — Implemented as informational:** recorded level/XP/mode, classes, feats, ASIs, and pending choices.
- **Proficiencies — Implemented as informational:** skills, saves, tools, languages, and expertise. Duplicate-source choice resolution is not automated.
- **Equipment — Implemented as manual records:** name, quantity, equipped state, slot, properties, notes, and optional physical mapping. No Minecraft item or combat statistic is granted.
- **Features/feats — Implemented as manual/informational records:** type, source, status, description, and progression summaries. Eligibility and effects are not automated.
- **Spellbook — Partially implemented:** typed custom definitions, known/prepared flags, details, casting summary, and manual slot records are editable. Search is inherited from the record list.

The standard spell catalog remains explicitly empty. No SRD spell text was fabricated or imported, and no spellcasting/effect automation was added.

## Character preview

The wide layout shows the current player's Minecraft skin face, canonical character name, recorded species, class, level, and draft stage. Compact layouts omit this panel. A full animated player-model renderer was not introduced because it could not be visually or performance-tested in this headless environment; the face preview is the safe fallback.

## Validation, finalization, and submission

The Review stage displays current section status and supports structural validation, exact saved-sheet inspection, and navigation to finalization/submission. The existing library detail screen continues to own the confirmed finalize and campaign selection workflow. Submission uses the durable Checkpoint 2 submission UUID journal and creates an independent campaign character copy. It never automatically approves, assigns, controls, or binds that copy.

## Changed files

- `src/main/java/dev/tacticalcombat/client/ui/CharacterWizardScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/WizardModel.java`
- `src/main/java/dev/tacticalcombat/client/ui/WizardEditors.java`
- `src/main/java/dev/tacticalcombat/client/ui/WizardChoiceScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/WizardRecordsScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/AbilityScoreScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/Srd2014Catalog.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryCreateScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryCharacterScreen.java`
- `src/test/java/dev/tacticalcombat/client/ui/WizardUiTests.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `docs/ui-overhaul-sol-checkpoint-3.md`

No dependency, persistence schema, combat authority, or network protocol version changed.

## Automated verification

Command: `/workspace/cubetable-setup/gradle.sh build runGametest`

- Java compilation and Gradle build: **Passed**.
- Foundation assertions: **1,312 passed** (baseline 1,257; 55 new wizard assertions).
- Minecraft GameTests: **101 passed**.
- Failures: **0**.
- Skipped required tests: **0**.
- The logged Yggdrasil public-key fetch error is expected in the isolated headless test server and did not fail authentication-independent GameTests.

New deterministic coverage includes 2014 ability modifiers, standard-array uniqueness, every point-buy cost and bound, external-roll non-authority, slot bounds, compact/wide layout geometry, catalog truthfulness, guided draft creation, section persistence/restart, save/resume, revision conflicts, stale overwrite rejection, unauthorized access, structural finalization, independent campaign-copy submission, and inert inventory/spell records.

## Known limitations and Astra backend requirements

- **Partially implemented:** full guided 2014 legality. A future server contract needs versioned choice catalogs, source attribution, prerequisites, choice counts, duplicate-proficiency resolution, and whole-build validation.
- **Deferred:** server-authoritative rolled ability generation and auditable roll assignment.
- **Deferred:** verified/versioned standard spell catalog. The current correct response is empty.
- **Deferred:** automatic class/species/background features, multiclass rules, leveling, feat/ASI eligibility, starting-equipment choice resolution, and spellcasting.
- **Deferred:** separate structured pronoun and weight fields, should the backend choose to add them.
- **Deferred:** creation-time resource-pool authoring; existing manual runtime trackers remain available through established character tools.
- **Deferred:** full post-creation dashboard and approved-character editing workflow.
- **Not verified:** foreground rendering, focus order, tooltips, skin preview, clipping, scrolling, and framerate in a graphical client.

Any future Astra extension should add capabilities to the existing versioned sheet/library contract, preserve bounded messages and optimistic concurrency, and keep executable transformations server-side. It must not set `guidedRulesComplete` true until the server can prove the advertised legality scope.

## Real-client acceptance checklist

1. Open My Characters at large, medium, and minimum supported GUI scales.
2. Create Guided and Manual drafts; verify method/profile labels and that no duplicate appears after normal use.
3. Save Basics, close Minecraft, reconnect, and resume the same draft.
4. Navigate all eleven stages by sidebar, Previous/Next, keyboard, mouse, Back, and Escape.
5. Confirm unsaved text survives a rejected save and is protected by the discard dialog.
6. Open the same draft from two clients/sessions; create a revision conflict and verify explicit reconcile behavior.
7. Exercise standard array, point buy, manual values, and external-roll entry; verify modifiers and error text.
8. Select guided species/class/background; confirm no automatic combat bonus appears.
9. Add inventory, feature/feat, custom spell, and slot records; reconnect and verify persistence.
10. Verify the empty standard spell catalog is presented truthfully and custom spells are marked manual.
11. Validate, finalize, then submit; confirm the campaign copy is independent and not automatically approved/bound.
12. As another account, confirm the private library draft cannot be inspected or changed.
13. Check the current skin face preview and confirm compact layouts hide it without obscuring fields.
14. Regress Campaigns, assignments/binding, combat HUD/grid/camera, dice/cards, Generic D20, Percentile, Manual Tabletop, and Minecraft Tactical flows.
15. Repeat key workflows against a disconnected, unsupported, rate-limited, and slow server; confirm no false success or lost typed text.

## Next checkpoint recommendation

Run graphical single-client and real two-client acceptance first. Address any rendering/focus defects as a Checkpoint 3 stabilization follow-up. After acceptance, the next UI checkpoint should build the post-creation character dashboard on these same components. Full guided legality, catalog data, authoritative rolls, and advancement should remain an explicit Astra backend project rather than client-side rules invention.
