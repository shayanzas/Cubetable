# UI Overhaul — Sol Checkpoint 4

## Repository checkpoint

- Branch: `dev/ui-overhaul`
- Starting commit: `d8c0b6846b13e81937116a3cbb3fd1c63f759c86`
- Implementation commit: `f14c46fba1f2a393652f4d31c96767078672d5d6` (`Add detailed character dashboard`)
- Documentation is committed separately after final verification.
- No push, merge, dependency update, schema migration, or protocol change was performed.

## Status summary

| Requirement | Status | Result |
|---|---|---|
| Unified post-creation dashboard | Implemented | One permission-aware dashboard displays either a private library source or an authorized campaign copy without merging identities. |
| Nine primary tabs | Implemented | Overview, Abilities & Skills, Combat & Actions, Equipment, Features & Feats, Spells, Character Details, Notes, and Manage. |
| Authoritative combat projection | Implemented | Campaign HP, temporary HP, AC, initiative, movement and readiness use the existing server snapshot; no duplicate combat model was added. |
| Manual/recorded sections | Implemented | Typed identity, progression, proficiency, inventory, features, conditions, resources, spellbook, slots and tracker records remain explicitly manual/informational. |
| Library and unbound campaign editing | Implemented | Existing revisioned operations, policy, validation and failure-preserving forms are reused. |
| Bound and encounter restrictions | Implemented | Bound campaign sections/manual trackers and all active-encounter dashboard edits display read-only state and remain server-refused. |
| Rest and recent activity | Partially implemented | Actual request/review and bounded session-event operations are exposed. Rest recovery and durable history remain unavailable. |
| Graphical/real multiplayer acceptance | Not verified | No graphical Minecraft client or real two-client session was available in this environment. |

## Screens and navigation

`CharacterDashboardScreen` is the shared sheet. It is reachable from:

- completed entries in My Characters;
- the library lifecycle/details screen;
- campaign roster entries;
- campaign character management;
- the main hub's selected/bound-character **Open Sheet** action; and
- known library-source/campaign-copy links under Manage.

Incomplete personal drafts still open the creation/lifecycle flow. Approved campaign characters never open the creation wizard.

The dashboard preserves the current tab during authoritative refreshes. Previous/Next controls and a searchable tab chooser support all nine tabs. Back/Escape returns to the actual source screen. At very small logical heights, the identity preview collapses and the list expands upward; it does not shrink text or overlap the footer. Lists retain the shared keyboard, narration, focus, mouse-wheel, clipping and search components.

## Header and Overview

The header identifies:

- canonical character name and profile;
- library source versus campaign copy;
- species, class and subclass;
- recorded level/progression;
- campaign name for campaign copies;
- draft/completion, approval, selected, bound and encounter state;
- ownership/controller information where authorized; and
- whether editing is available and why it is read-only.

The current player's skin face is shown for their own source/campaign character. A remote character uses an explicit `REMOTE` fallback rather than incorrectly displaying the viewer's skin. A full remote skin/model contract is unavailable.

The Overview contains server/saved HP, maximum HP, temporary HP, AC, initiative, speed, proficiency bonus, inspiration, death marks, exhaustion, Hit Dice, frequent manual resource pools, equipment count, condition count, feature count and spell/slot summaries. Summary rows navigate to the detailed tab.

The XP bar uses the verified 2014 cumulative thresholds from level 1 through 20. Progress is calculated between the character's current lower and next upper threshold—for example, level 2 uses 300–900 XP. Level 20 is capped. `MILESTONE` and `DM_MANAGED` modes display their mode and recorded level without a fabricated percentage. Recorded progression remains informational and there is no Level Up mutation.

## Implemented tabs

### Overview

Dense read-only summaries plus permitted manual tracker operations. Inspiration, death marks, Hit Dice and resource changes use `MANUAL_UPDATE` and are displayed as successful only after server acknowledgment. HP has no generic edit control; users are routed to existing encounter/server authority.

### Abilities & Skills

All six canonical SRD ability scores, modifiers and base saving throw modifiers are shown together. The full 18-skill list displays its governing ability and base modifier. Recorded save/skill/expertise markers are labelled informational and are not added to authoritative calculations. Armor and weapon proficiency inputs remain from the existing SRD combat section. Authorized score editing reuses the library score editor or established campaign server workspace.

### Combat & Actions

Shows authoritative combat values, existing SRD weapons/armor, active recorded conditions, exhaustion and manual resources. Automated Phase 3 actions are clearly separated from manually tracked features and unavailable future actions. The only executable route opens existing encounter controls; the dashboard generates no attacks, dice, damage, conditions or spell effects.

### Equipment

Equipped-first/name-sorted sheet inventory supports search, inspection, add, edit and confirmed removal when authorized. Currency editing is supported. Quantity, slot, properties, notes and physical mapping are visible. Sheet records are explicitly separate from Minecraft inventory and existing Phase 3 weapons/armor/ammunition.

### Features & Feats

Entries are grouped/sorted by kind, source and name and expose description, automation status and usage-record presence. CLASS, SPECIES, BACKGROUND, FEAT, CONDITION, TEMPORARY and CUSTOM records remain manual/informational. Manual feature resource pools can be created and edited, including current/maximum, source and recharge note. Resource consumption/recharge never executes automatically.

### Spells

Custom spell selections show cantrip/level, school, known/prepared/always-prepared state, casting time and range. Search and a level 0–9 filter are available. The full structured editor retains components, duration, concentration, ritual, description, upcasting and source fields. Manual slot pools use `MANUAL_UPDATE` when permitted. Custom definitions/selections can be added, edited and confirmed-removed.

The verified standard catalog remains explicitly empty. There is no Cast Spell button or automated effect, healing, damage, concentration or slot consumption.

### Character Details

Displays and edits the canonical identity section: species, class, subclass, background, alignment, age, height/build, appearance, biography, personality, ideals, bonds and flaws. Name remains the canonical sheet name managed by the existing lifecycle editor. Progression editing is recorded/informational. Campaign forms warn that a confirmed edit may invalidate approval under policy.

### Notes

Uses the documented bounded biography, appearance, personality, ideals, bonds and flaws fields. No unlimited or duplicate notes field/schema was invented. The shared form retains staged text through rejected, timed-out, disconnected or stale saves and warns before discarding it.

### Manage

Library sources route to validation, finalization, duplication, durable submission and known independent campaign copies. Campaign copies route to existing approval/revocation, assignment, controller, binding/unbinding and source navigation. The tab also exposes rest requests and recent authorized activity without duplicating those services.

## Canonical versus recorded values

| Data | Dashboard source | Authority label |
|---|---|---|
| Campaign HP/max/temp, AC, initiative and movement | `CHARACTER_GET.srd` | Server-authoritative snapshot |
| Library SRD summary | Existing `SrdCharacterCodec` over the saved server draft | Saved source/established SRD derivation; not a live combat state |
| Abilities, armor, weapons and proficiency bonus | Existing `srd_combat` plus `SrdCharacter` rules | Canonical existing SRD implementation |
| Identity, progression and extra proficiencies | Versioned `ui_*` sections | Recorded/manual/informational |
| Inventory, features, conditions and custom spells | Versioned `ui_*` sections | Recorded/manual/informational; no mechanics granted |
| Inspiration, death marks, Hit Dice, slots and pools | `RpgRecords` through `MANUAL_UPDATE` | Manual bookkeeping; server bounded and revisioned |

No UI section copies a recorded value into combat values implicitly.

## Editing, authority and failure handling

Integrated operations:

- `LIBRARY_GET`, `LIBRARY_UPDATE`, `LIBRARY_VALIDATE`, `LIBRARY_FINALIZE`, `LIBRARY_SUBMIT`;
- `CHARACTER_GET`, `CHARACTER_EDIT`, and existing character management operations;
- `MANUAL_UPDATE`;
- `ACTIVE`, `CAMPAIGN_GET`, `EVENTS`, `REST_LIST`, `REST_REQUEST`, and `REST_REVIEW`.

Library saves use library editing revisions. Campaign saves use both editing and runtime revisions. Every mutation waits for server acknowledgment. Existing forms retain staged text after validation errors, permission rejection, conflicts, rate limits, timeout, storage failure or disconnect. Stale server state requires reload/comparison; no silent last-write-wins path was added.

Unbound campaign owner/manager edits remain subject to owner fields, manager authority and approval policy. A confirmed edit can revoke approval under `requireEditApproval`; this is stated before saving. Accepted controllers can read/control where authorized but do not acquire owner editing rights.

Bound characters and active-encounter dashboards are read-only. The server also rejects bound `MANUAL_UPDATE`, so a stale/untrusted client cannot bypass the UI. HP, binding, approval, selection and combat outcomes are not modified through generic section edits.

## Conditions, resources, rests and activity

CONDITION records and exhaustion are visible in Overview and Combat & Actions. Condition source, recorded duration/status and description are retained, but enforcement is not claimed.

Manual resource pools support current/maximum values and recharge notes. Hit Dice and spell slots use their actual bounded record actions. Bound updates remain unavailable pending a separately revisioned bookkeeping design.

The Rest screen creates stable request identities for retries while the screen remains open, lists authorized requests, and permits campaign managers to approve for manual adjudication or reject. It repeatedly states that HP, slots, Hit Dice, resources, time and encounter state are not automatically changed.

Recent Activity reads the existing permission-filtered event session and provides read-only event details. It is bounded and session-limited, not a permanent audit archive, and no second event pipeline was introduced.

## Changed files

- `src/main/java/dev/tacticalcombat/client/ui/CharacterDashboardScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DashboardModel.java`
- `src/main/java/dev/tacticalcombat/client/ui/DashboardSource.java`
- `src/main/java/dev/tacticalcombat/client/ui/DashboardEditors.java`
- `src/main/java/dev/tacticalcombat/client/ui/RestScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/RecentActivityScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/WizardRecordsScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ManagementScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/HubScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/BrowseScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryCharacterScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignRosterScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignCharacterScreen.java`
- `src/test/java/dev/tacticalcombat/client/ui/DashboardUiTests.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/ManagementGatewayGameTests.java`
- `docs/ui-overhaul-sol-checkpoint-4.md`

## Automated verification

Command: `/workspace/cubetable-setup/gradle.sh build runGametest`

- Compilation and Gradle build: **Passed**.
- Foundation assertions: **1,388 passed** (baseline 1,312; 76 new dashboard assertions).
- Minecraft GameTests: **103 passed** (baseline 101; two new authenticated gateway tests).
- Failures: **0**.
- Required skips: **0**.
- The isolated server's Yggdrasil key-fetch error did not fail authentication-independent tests.

Dashboard assertions cover all 2014 XP thresholds, XP/milestone/DM-managed projection, nine tabs, small-screen bounds, canonical abilities and 18 skills, authoritative combat summaries, identities, private library access, structured equipment/features/conditions/spells, tracker mutations, independent submissions, campaign policy approval invalidation, stale revisions, owner/controller separation, bound refusal and empty standard spell catalog.

New GameTests exercise the authenticated network gateway: an unbound owner section edit succeeds through CAS and invalidates approval under policy; replaying the stale revision conflicts; unrelated inspection is forbidden; a bound player's manual tracker write is forbidden and leaves characters/bindings unchanged.

## Remaining backend blockers and limitations

- **Not verified:** graphical rendering, focus order, clipping, scroll behavior, tooltips, skin loading and framerate in real clients.
- **Not verified:** actual two-client ownership/controller/DM flows and reconnect behavior.
- **Deferred:** a server projection for another player's skin/model; remote dashboards use a truthful fallback.
- **Deferred:** separately revisioned manual bookkeeping that can safely operate while a campaign character is bound.
- **Deferred:** durable rest-request retry intent across full client restarts, automatic recovery and complete rest eligibility/interruption rules.
- **Deferred:** complete condition enforcement and automatic duration changes.
- **Deferred:** verified standard spell catalog and spellcasting.
- **Deferred:** complete leveling/advancement, class-feature automation and automatic resource recharge.
- **Deferred:** dedicated general notes field; documented narrative fields are used instead.
- **Deferred:** durable lifetime event log and full event/HUD interface.
- **Partially implemented:** remote character preview, because the authorized character snapshot does not expose a safe skin-texture/model reference.

## Real-client acceptance checklist

1. Open a completed library source from My Characters and a campaign copy from Roster, management, and the hub Open Sheet action. Verify the same identity and correct Back path.
2. Test all nine tabs with mouse, keyboard, chooser and Previous/Next controls. Refresh and confirm the current tab remains selected.
3. Test 320×180, 427×240, 854×480 and common GUI scales. Verify the compact header collapses, lists clear the footer, text stays crisp and no foreground blur returns.
4. Verify the current player's skin face; inspect another player's character and confirm the `REMOTE` fallback never shows the wrong skin.
5. Compare Overview HP/max/temp, AC, initiative, speed, proficiency and weapons with the authoritative server/encounter state.
6. Check XP values below, at and above level thresholds; verify level 2 uses 300–900. Check milestone and DM-managed sheets show no fake percentage.
7. Search/sort/add/edit/remove inventory records and edit currency. Confirm Minecraft inventory, combat AC, attacks and ammunition do not change.
8. Create/edit feature, feat, condition and manual resource records. Confirm labels remain manual/informational and no class effect or condition is enforced.
9. Search/filter custom spells, inspect full details, toggle known/prepared data and change slots while unbound. Confirm no Cast action/effect exists and the standard catalog is empty.
10. Change identity/notes/progression on a library source. Simulate validation, timeout, disconnect, storage failure and stale revision; typed text must remain until acknowledged or explicitly discarded.
11. Edit an approved unbound campaign character and confirm the warning plus actual policy-driven approval result. Repeat with two clients to verify conflict reconciliation.
12. Bind the campaign character and enter combat. All section/manual controls must be read-only; stale screens must receive server rejection without visual success or state mutation.
13. Compare owner, accepted controller, manager and unrelated-member views. Controllers must not gain owner edits; unrelated campaigns/sheets/events must remain private.
14. Request Short and Long Rest, approve and reject as DM, test expired/ineligible party state, and confirm no automatic recovery or time advance.
15. Generate authorized approval, assignment, resource, dice and combat events. Verify bounded session activity visibility and no lifetime-history wording.
16. Regress the creation wizard, submission retry identity, approvals, assignments/binding, Phase 1 Tactical systems, Phase 3 SRD combat/grid, Generic D20/Percentile packs, dice/cards, camera and HUD.

## Recommended next step

Perform the graphical single-client and real two-client checklist before expanding the UI. Fix any focus, clipping or request-lifecycle defects as a Checkpoint 4 stabilization commit. After acceptance, proceed to the planned event-log and compact party/initiative HUD checkpoint using the existing event, party, initiative and preference contracts. Do not begin spellcasting, class automation, leveling or condition enforcement as UI work.
