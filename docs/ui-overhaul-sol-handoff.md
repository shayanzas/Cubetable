# Cubetable UI foundation — Sol integration handoff

Date: October 11, 2026. Branch: `dev/ui-overhaul`. Verified source checkpoint: `117348b4e28aba83cb065bcea50e0444c54f6597`. Read [foundation report](ui-overhaul-foundation.md) for scope, verification and outstanding gates. This is backend infrastructure, not a completed UI or new D&D rules engine.

## Client entry point

Use `dev.tacticalcombat.client.ClientUiApi` on the Minecraft client thread:

```java
if (ClientUiApi.supported()) {
    ClientUiApi.request(UiApiService.Operation.CAPABILITIES, new JsonObject(), reply -> {
        // Check reply.code() before using reply.data(). Keep unsaved edits separately.
    });
}
```

`request(Operation, JsonObject, Consumer<UiApiService.Reply>)` returns its connection-local sequence. It retains at most eight pending callbacks and times them out after ten seconds. `supported()` uses Fabric channel capability detection. Hide/disable server controls on unsupported servers. `TacticalCombatClient` already registers replies, timeout ticks and disconnect cleanup. No new screen exists.

Transport: `UiRequestPayload(sequence, operation, json)` on `tacticalcombat:ui_request_v1`; `UiReplyPayload(sequence, code, message, json)` on `tacticalcombat:ui_reply_v1`. Operation is the enum's uppercase name, at most 32 characters. Request JSON is limited to 49,152 characters; reply JSON to 65,536; Fabric's string codec additionally bounds encoded bytes. Nesting is checked before JSON parsing. The receiver runs on the server thread, derives identity/administrator authority from the authenticated player, and replies only to that connection. The server allows four requests per player per 20 ticks. Use section/bulk operations and debounce UI input rather than sending each keystroke. There is no unbounded work queue.

Errors: `CONFLICT`, `FORBIDDEN`, `REJECTED`, `UNAVAILABLE`, `STORAGE_ERROR`, `RATE_LIMIT`, `ALREADY_PROCESSED`; client transport can produce `TIMEOUT` or `INVALID_REPLY`. Codec-invalid packets may disconnect before an application reply. Malformed outer requests receive a generic `REJECTED`. A timeout or lost reply is not proof that a mutation failed: refresh before retry. Retain the same `submission`/rest `request` UUID for a retry of those operations. Other creation operations are connection-replay-protected, not globally idempotent across reconnects.

UUIDs/revisions are protocol fields obtained from read models and held by the UI, never text the player should type. Every operation below requires exactly the listed JSON keys; extra privilege flags or missing fields are rejected. `revision` normally means editing revision; assignment/rest operations use their own revision as noted. Nonnegative integer fields must be JSON integers. `page` starts at zero. Lists generally return eight entries; request subsequent pages until empty. Event pages have 16 entries.

## Operational request map

### Discovery and campaigns

| Operation | Exact args | Permission/result |
|---|---|---|
| `CAPABILITIES` | `{}` | Protocol/section version 1; `hubQuickSwitch:true` when the Checkpoint 1 server restriction is available; operations, section defaults and existing SRD authoring fields/options; automation flags are false. |
| `ACTIVE` | `{}` | Selected campaign/character when still authorized, bound character, in-combat flag. Selection is session-local. |
| `CAMPAIGN_LIST` | `page` | Visible campaigns only; existing summaries include name/profile/revision/manager/policy. Pages 0–8. |
| `CAMPAIGN_CREATE` | `name` | Server administrator only; creates through existing repository, returns campaign details. Does not implicitly select it. |
| `CAMPAIGN_GET` | `campaign` | Members/managers; roster member UUIDs and co-DM list only to managers. |
| `CAMPAIGN_SELECT` | `campaign` or `campaign, quick:true` | Ordinary browser: self-selection among authorized memberships. Checkpoint 1 hub quick-switch: server requires target campaign manager authority when `quick:true` is present. Existing combat lock applies. Clears UI character selection, does not rebind. |
| `CAMPAIGN_INVITE` | `campaign, revision, player, dm` | Campaign manager invites; only owner/admin appoints co-DMs. |
| `CAMPAIGN_REMOVE` | `campaign, revision, player` | Manager; cannot remove owner. Owner/admin required to remove co-DM; affected bindings must be released first. |
| `CAMPAIGN_REVOKE_DM` | `campaign, revision, player` | Owner/admin; delegates to existing role-revocation path, preserving membership and private-view invalidation. |
| `CAMPAIGN_POLICY` | `campaign, revision, policy` | Manager; `ApprovalPolicy`'s existing strict JSON shape. Secure default requires approval. |
| `CAMPAIGN_PROFILE` | `campaign, revision, profile` | Manager; changes default only, not existing sheets or encounters. |
| `ROSTER` | `campaign, page` | Members: names, identity, owner/controller, approval, binding, editing revision, private-view eligibility. No raw other-player sheets. |
| `ELIGIBLE_PLAYERS` | `campaign` | Managers: up to 256 online player names/UUIDs, membership and combat flags. These are discovery results, not authorization to mutate them. |

Checkpoint 1 keeps the existing operation and adds only the explicit `quick:true` manager check, advertised by `CAPABILITIES.hubQuickSwitch`. Older foundation servers lack that flag and should disable the hub quick-switch while retaining ordinary campaign browsing.

Checkpoint 2 adds response-only discovery fields without changing request shapes or server schemas: network `CAPABILITIES.canCreateCampaign` (authenticated administrator); `CAMPAIGN_GET.canManageRoles` (owner/admin) and manager-only `owner`; bounded network `playerNames` for already authorized identities; `ROSTER.profile/readiness`; `ASSIGNMENT_LIST.name/profile/bound`; `ELIGIBLE_ENTITIES.position`; and library supported-field validation summaries. `LIBRARY_GET` includes at most 128 source-associated `submissions` with campaign names, approval and informational origin, filtered by source ownership and current campaign membership, plus `submissionsTruncated` and validation `errors`. Older servers missing authority fields disable those specific UI controls. Private sheets are not added to public roster/offer models. Unauthorized `CHARACTER_GET` returns `FORBIDDEN` before live HP capture. Malformed legacy informational provenance is skipped rather than rewriting records or failing unrelated reads. See [Checkpoint 2](ui-overhaul-sol-checkpoint-2.md).

The UI's fixed client-local `tacticalcombat/ui-submissions.json` journal persists a stable submission UUID after confirmation and before sending. Invalid/future/unwritable journals fail closed; a timeout or reconnect does not generate a replacement identity. Library/campaign/duplicate creation operations still lack durable idempotency keys and require browser reconciliation before repeating uncertain requests. Known offline members can receive durable control offers through the existing API; manual acceptance after reconnect is required. Unknown/offline names never require typed UUIDs and disable unidentified account choices.

The existing membership model grants a member self-selection among eligible campaigns. This task does not add a per-switch DM approval queue or forcibly change another player's active campaign. Do not depict such a queue as implemented.

### Library and character authoring

| Operation | Exact args | Result/constraints |
|---|---|---|
| `LIBRARY_LIST` | `page` | Only requester's private library summaries: id/name/profile/mode/stage/revision. Even server admin does not browse another library through this API. |
| `LIBRARY_CREATE` | `profile, mode, sheet` | `mode` is `GUIDED`/`MANUAL`. For SRD, `sheet:null` creates existing `SrdAuthoring.template()`. Other profiles require a supplied existing pack sheet serialized as a JSON string. |
| `LIBRARY_GET` | `character` | Authorized draft summary and parsed sheet. |
| `LIBRARY_UPDATE` | `character, revision, section, data` | Replace one section atomically; increments revision and returns stage to `DRAFT`. |
| `LIBRARY_VALIDATE` | `character` | Human-readable `errors[{section,code,message}]`; `guidedRulesComplete:false`. |
| `LIBRARY_FINALIZE` | `character, revision` | Validates name and implemented SRD combat codec where applicable; marks `COMPLETE`. This is not campaign approval or complete D&D character legality. |
| `LIBRARY_DUPLICATE` | `character, revision` | New identity/revision 1, independent `DRAFT`; original retained. |
| `LIBRARY_SUBMIT` | `character, revision, campaign, submission` | Complete library source + membership/policy required. `submission` is a stable UUID generated once for this intended copy. Independent campaign record returned. Same identity/source/campaign retry returns original campaign copy. |
| `CHARACTER_GET` | `character` | Existing permission-filtered character snapshot, effective sheet, stats, policy, editing/runtime revisions, readiness, owner/controller/assignment and source-of-authority labels. Tactical live HP is captured through existing service first. |
| `CHARACTER_SECTION` | `character, section` | One typed extension section plus editing/runtime revisions and manual/informational status. |
| `CHARACTER_EDIT` | `character, revision, runtimeRevision, section, data` | Owner/manager, unbound only; uses existing policy and edit CAS. Extension sections only. Approval policy may invalidate approval. |
| `CHARACTER_APPROVE` | `character, revision` | Campaign manager, unbound only. Re-approval cannot rewrite an active binding's editing revision. |
| `CHARACTER_REVOKE` | `character, revision` | Manager, unbound only. Revokes approval; emits private approval/change-request notification. No persistent textual review-thread API exists. |

`LIBRARY_UPDATE` supports `basics` (`data:{"name":"..."}`), `combat` (the complete existing `SrdAuthoring.data(sheet)` object, SRD only), and the extension sections listed below. Use whole valid combat sections; the server rejects malformed combat input. Drafts can omit narrative/creation sections, but the combat section cannot contain arbitrary invalid partial numbers. Keep temporarily invalid form text in the screen until it can be saved as a valid section. All successfully saved sections persist through disconnect/restart.

Campaign basics/combat fields continue to use the existing **implemented** `ClientCharacters.send("edit", args, callback)` API on `CharacterRequestPayload` v2. Args: `character`, `revision`, `runtimeRevision`, `changes`, with changes produced by `CharacterEdits.diff(before, after)`. This delegates to the same repository permissions, binding lock and validation. SRD controls/options come from `SrdAuthoring.fields()`/`template()`/`change()` and `SrdCharacterCodec`; do not build a second combat-stat model. The old `ClientCharacters` manages its own sequence; do not share sequences with `ClientUiApi`.

### Assignment, selection and binding

| Operation | Exact args | Contract |
|---|---|---|
| `ASSIGNMENT_LIST` | `campaign, page` | Manager, source owner, current controller or offered recipient; no unrelated private assignments. |
| `ASSIGNMENT_OFFER` | `character, revision, assignmentRevision, player` | DM-controlled offer to current member. Character must be unbound. `assignmentRevision:0` means no prior assignment. |
| `ASSIGNMENT_ANSWER` | `character, revision, accept` | Offered recipient only; assignment revision CAS. Unbound only. Acceptance checks conflicting accepted control/binding. |
| `ASSIGNMENT_CLEAR` | `character, revision` | Manager, assignment revision, unbound only. Restores source owner as controller and cancels pending offer. |
| `CHARACTER_SELECT` | `character, revision` | Eligible controller/manager in selected campaign, outside combat. Changes selection only. |
| `ELIGIBLE_ENTITIES` | `character` | Managers: up to 64 living candidates in 32-block area, names/UUIDs, eligibility/reason and truncation flag. Does not reveal another binding's character sheet. |
| `BIND` | `character, revision, entity` | Manager; loaded live target; approved compatible assignment; all existing binding/encounter checks re-run. Calls `CharacterService.bind`, including Tactical modifiers/HP integration. |
| `UNBIND` | `character, revision` | Manager; `CharacterService.release` handles loaded/offline entities and combat locks. |

An assignment has `character, controller, offered, decision, revision, history`. Decisions: `PENDING`, `ACCEPTED`, `DECLINED`, `CANCELLED`. Declining preserves previous controller; clearing restores owner. History retains the last 16 changes. Offers do not expire automatically in this workflow; DM can clear them. Control is stored separately from ownership and binding. Accepted controller receives authorized private read access, not ownership-based editing authority.

Operational substitute control is **out-of-encounter player-entity rebinding**: unbind → DM offer → substitute accepts → DM binds to that player's entity. SRD action and movement checks both use accepted controller while still requiring that player's bound actor. NPCs remain DM-controlled; accepting a character then binding it to an NPC does not grant a non-DM remote NPC control. Live takeover, safe-boundary queued restoration and automatic reconnect reassignment are unavailable. Never bypass these restrictions for a graphical “Take over” button.

### RPG records

| Operation | Exact args | Contract |
|---|---|---|
| `SPELL_CATALOG` | `query, page` | Version/source and explicitly empty standard list. Verified standard definitions have not been imported. |
| `MANUAL_UPDATE` | `scope, character, revision, runtimeRevision, change` | Scope `LIBRARY`/`CAMPAIGN`; library editing revision or campaign editing/runtime revisions. Same ownership/edit policy and unbound campaign lock. Returns full authorized draft/character. |

`change` is the complete `RpgRecords.Change` record: `action, section, key, index, value`. Actions:

- `INSPIRATION`, `DEATH_SUCCESSES`, `DEATH_FAILURES`, `EXHAUSTION`: section `trackers`; unused `key:"", index:0`; inspiration value 0/1, markers 0–3, exhaustion 0–6.
- `HIT_DICE`: section `trackers`, list index, desired current count.
- `SPELL_SLOT`: section `spellbook`, list index, desired current count.
- `RESOURCE`: section `trackers` or `features`, namespaced pool key, desired current count; pool must be `MANUAL`.

These are explicit current-value/CAS updates, not dice rolls, casting, healing, recovery triggers or feature activation. Bounds revalidate against maximum. They never mutate `srd_combat` or physical inventory. Do not label them “Cast”, “Take short rest” or “Apply condition effect”. Bound campaign trackers are intentionally unavailable until independent manual-record revision semantics are designed without invalidating active combat CAS/approval. Display this limitation; do not silently unbind/rebind.

## Data types and canonical sources

All new typed extension records are in `uiapi/SheetSections.java`. `CAPABILITIES.sectionDefaults` supplies complete defaults. Each extension is stored in the existing sheet's `collections.ui_<section>` as exactly one `{ "version":1, "data":{...} }` wrapper. Requests carry `data`, not the wrapper. Unknown fields are rejected; include every record field, using explicit `null` for optional fields. No executable formulas are accepted as mechanics.

| Section | Types and content |
|---|---|
| `identity` | `Identity`: species, characterClass, subclass, background, alignment, age, height, biography, personality, ideals, bonds, flaws, appearance, portrait. Canonical name remains `text.name`. Portrait is an inert reference; do not download arbitrary URLs or change Minecraft skins. |
| `proficiencies` | `Proficiencies`, `Modifier`: recorded skills, saves, tools, languages, expertise and source-labelled modifiers. Automated armor/weapon proficiency stays in `srd_combat`. |
| `progression` | `Progression`, `Advancement`: recordedLevel, xp, XP/MILESTONE/DM_MANAGED, bounded history, pending choices, class/feat/ASI records. `recordedLevel` is informational, not the combat level or an automatic level-up. |
| `inventory` | `Inventory`, `Item`: namespaced record/definition references, quantity, equipped/slot, recorded properties, notes, inert physicalMapping, five currencies. No Minecraft item grant or automatic equipment-stat mutation. |
| `spellbook` | `SpellDefinition`, `SpellSelection`, `Slot`, `Spellbook`: custom definitions; separate known/prepared/alwaysPrepared/source/notes references; manual slots and recordedAbility/recordedSaveDc/recordedAttackModifier. These spellcasting values are informational. |
| `features` | `FeatureKind`, `Feature`, `Features`, `Pool`: class/species/background/feat/condition/temporary/custom entries, optional duration/sourceActor/uses, description/source/history, generalized resource pools. |
| `trackers` | `Trackers`, `HitDice`, `Pool`: single inspiration flag, three success/failure markers, exhaustion, manual Hit Dice/resources. |

`Automation` values include `AUTOMATED`, `MANUAL`, `INFORMATIONAL`, `UNAVAILABLE`, but client-created extension entries **cannot request AUTOMATED**. Phase 4–6 engines must later introduce trusted transitions through their own authority, not flip a client flag. Slot/selection/book records are uniformly manual/informational; `CHARACTER_SECTION.automation` says so. Standard spell definitions have a future shared catalog endpoint; custom definitions are stored once within each character's book and referenced by selection IDs. Unknown references, forged standard entries and arbitrary formula fields reject. Future class eligibility/casting implementations must replace the current explicit unavailable contract; no resolver stub is advertised as working.

Automatic SRD abilities, level, HP/max/temp, AC calculation, equipment, ammunition, reach, speed and initiative remain in `collections.srd_combat`. Read actual derived values from the existing snapshot's `srd` projection. Tactical values stay in the repository/vanilla-health integration. New `ui_*` records must never be copied into combat values implicitly. Manual recorded values are deliberately distinct and labelled.

Limits: existing sheet 32,768 characters/depth 20, library 32 per owner/1,024 total; 64 inventory items, 32 custom definitions, 64 spell selections, 18 slot pools, 64 features, 32 general pools per section, 20 Hit Dice pools. ID namespace ≤32 and local part ≤80 characters. Descriptions/narrative have per-field 512–4,096-character limits; see record constructors for each. Total sheet and 16 MiB store limits can reject a combination below individual list limits. Show actual error and preserve unsaved edits. Treat all user strings as literal text, not clickable commands or executable markup.

## Presentation, notifications and rests

| Operation | Exact args | Contract |
|---|---|---|
| `PARTY` | `campaign, page` | Player-bound campaign characters only. Name, skin-player reference, recorded class, online/combat state, authorized HP and up to 16 private recorded conditions. NPC enemies are excluded. Returns presentation/rest policy with revision. |
| `PRESENTATION_POLICY` | `campaign, revision, hp, radius` | Manager; hp `PRIVATE` (default), `BARS`, `EXACT`; rest radius 1–64, default 16. Separate from character editing revisions. |
| `INITIATIVE` | `encounter` | Existing encounter permission checks. SRD returns existing full private projection; Tactical requires actual participation and returns bounded initiative order/active flags. No duplicated initiative state. |
| `EVENTS` | `campaign, cursor, kind` | Authorized history; `kind:null` means all. Returns session UUID, events, next cursor, oldest sequence, historyLost. Start cursor 0; reset on session change/disconnect. |
| `REST_REQUEST` | `campaign, request, kind` | Member; kind SHORT/LONG, stable request UUID. Server derives online player-bound party plus requester, checks live membership/dimension/radius/alive/non-spectator/not-in-combat. |
| `REST_REVIEW` | `request, revision, approve` | DM; revalidates stored participants before approval; writes APPROVED_FOR_ADJUDICATION or REJECTED. Expired requests write EXPIRED. |
| `REST_LIST` | `campaign, page` | Participants/DM; records plus serverTime and recoveryAutomated:false. |

Party bars expose a fraction, not exact current/max fields; this is not a cryptographic guarantee against inferring values from other information. Exact own/controller HP remains available; DM sharing applies only to player-bound party members, never NPC enemy records. Match initiative actor IDs and visible event actor IDs to the party member's skin-player/binding identity for recent-roll presentation. No additional stored recent-roll combat state exists.

Events: `UiEvents.Event(id, sequence, timestamp, campaign, encounter, turn, kind, actors, summary, roll, visibility, recipients)`. Types support ATTACK/DAMAGE/HEALING/TURN/CONDITION/APPROVAL/ASSIGNMENT/DM_ACTION/REST/RESOURCE/REJECTION/DICE/ZERO_HP. Actual producers currently connected: committed SRD attack rolls (including storage-retry success), SRD turn starts and DM healing, campaign approvals/revocation/record edits/assignments, rest requests/reviews, and manual dice rolls with an authorized campaign context. Other type names reserve contracts; they do **not** mean every Tactical damage/condition/rejection/zero-HP source is instrumented. No chat-string scraping is used.

`UiEvents.Roll` has sides, faces, kept **indices**, modifier, total, purpose and critical. Discarded indices are the complement. Example: faces `[18,7]`, kept `[0]`, modifier `5`, total `23`. Never animate 18 as the modified total. Stable event IDs deduplicate retries within the bounded history; SRD action replay prevention remains in the encounter engine. Use one presentation per event ID. Keep local animation skipping/speed independent of server action results. Existing DiceRollPayload/renderer still works; a future UI must avoid rendering both old and new animations for one action.

History holds at most 512 events globally, pages of 16; it is session-only, not durable audit storage. Visibility is rechecked at query time against current membership/DM role and explicit recipients. SRD attack rolls stay private to actor/requester and DMs; do not broaden them to everyone for spectacle. The UI polls history under the shared request rate limit; there is no automatic event push/notification subscription. Use event types for notices and explicit refresh for authoritative sheets. Do not advertise disconnected-player notifications or durable review conversations as implemented.

Rest store retains at most 128 requests, pruning oldest resolved/expired entries when capacity is needed. A requester may have one unexpired pending request. Requests expire after five minutes; list includes expires/serverTime so the UI can label a pending record expired before review persists that state. New requests exclude offline members; members captured in a request must remain online and eligible at approval. Policy radius is fixed into each request. Approval means **manual adjudication only**, not resting, recovered resources, time passage, interruption handling or full SRD eligibility. Offline treatment, interruptions, override and time advancement are future concepts, not configurable working recovery rules.

## Persistence and revisions

- Existing `tacticalcombat/campaigns.json`: schema **3**, adding assignments to the same atomic state as characters/bindings. Schemas 1/2 load with empty assignment metadata and original IDs, policies and bindings; next mutation writes schema 3. Secure schema-1 policy migration remains. Older mod versions cannot read schema 3; preserve world backups for rollback.
- New `tacticalcombat/library.json`: schema **1**, private draft records. No forced migration or extraction of existing campaign characters. Submission copies sheet bytes into the existing campaign transaction; library is unchanged, so there is no cross-file two-phase commit. Optional `collections.ui_origin` records source ID/revision/submission UUID; provenance is informational, not authorization.
- New `tacticalcombat/ui-workflows.json`: schema **1**, bounded rest records and DM presentation/rest policies.
- HUD: `HudPreferences` version **1**, client-only file chosen by the client UI (recommended fixed config path `tacticalcombat/hud-layout.json`). Use `load(Path)`, instance `save(Path)`, `defaults()` for reset. No server packet or character revision is involved. It is not automatically loaded by a nonexistent HUD screen.

All server stores use existing AtomicJsonFile validation, fsync/atomic replacement, validated backup and write-before-publish behavior. Unsupported store/section versions fail closed instead of restoring an older backup. Both-invalid files fail rather than resetting characters. Library/workflow bootstrap failures disable their API facilities and log errors, preserving existing combat/character storage. Full-file writes retain the earlier large-store tick-cost limitation.

Conflict UX: retain the loaded editing revision and local working copy. On CONFLICT, fetch current state and show a field/section comparison. Do not silently retry using the new revision, replace dirty text on refresh, or treat a preview/selection as approval. Runtime HP/ammunition revisions remain independent and authoritative. Existing `CharacterWorkspace`/`CharacterEdits` can support reconciliation; a whole-section replacement still requires an explicit human choice when concurrent edits touched that section.

HP controls must use the existing safe operations: permitted unbound authoring through Character v2 edit, or existing paused-encounter DM SRD heal operation. Do not send client-derived damage, dice, AC or HP into accepted-action transactions. `commitSrdAction`, `commitSrdHealth`, repository maps, assignment internals and Minecraft health attributes are not UI mutation APIs.

## Implementation map and UI order

Main files:

- `uiapi/SheetSections.java`, `RpgRecords.java`: shared DTOs, defaults, limits and inert-record validation.
- `character/LibraryRepository.java`, `UiWorkflowStore.java`, `CharacterRepository.java`: persistence and permission/CAS operations.
- `uiapi/UiApiService.java`, `UiNetworking.java`: operation switch, world adapter and bounded protocol.
- `net/UiRequestPayload.java`, `UiReplyPayload.java`; `client/ClientUiApi.java`: transport contract.
- `uiapi/UiEvents.java`, `UiPresentation.java`, `HudPreferences.java`: event/read/layout models.
- Existing `character/CharacterService.java`, `CharacterSyncService.java`, `CharacterEdits.java`, `CharacterWorkspace.java`, `client/ClientCharacters.java`, `rules/srd/SrdAuthoring.java`, `SrdCharacterCodec.java`: reuse rather than replace.

Paths above are relative to `src/main/java/dev/tacticalcombat/`. New tests: `src/test/java/dev/tacticalcombat/character/Ui*Tests.java` and `src/gametest/java/dev/tacticalcombat/combat/UiFoundationGameTests.java`.

Recommended implementation order:

1. Shared theme/components and bounded error/loading/reconciliation states.
2. Escape-menu entry and hub using CAPABILITIES/ACTIVE.
3. Campaign browser/manager using the campaign API table.
4. Private character library with explicit local dirty-state handling.
5. Guided/manual wizard using shared section defaults and canonical SRD authoring data; label incomplete guided legality honestly.
6. Character dashboard showing authoritative vs recorded values, approval, controller and binding separately.
7. Spellbook/inventory/features/manual controls with bound-edit and unsupported-automation notices.
8. Party HUD and initiative; honor privacy and use existing actor IDs/skins.
9. Dice/event presentation; deduplicate stable IDs, retain privacy and old-protocol compatibility.
10. HUD layout controls/settings, reduced motion and polish; persist locally only.

Run the automated suite after integration. Then perform the real two-client scenarios in the foundation report and earlier Phase 1–3 checklists. Headless tests do not establish that graphical layouts, dragging, animation, skins, packet timing, or actual multiplayer UX work.

## Concrete request/reply examples

These JSON bodies are illustrative. IDs and revisions shown as angle-bracket placeholders must come from replies; generate `submission`/rest request UUIDs once per intended operation. Do not send the placeholders literally.

Create a library SRD scaffold (`LIBRARY_CREATE`):

```json
{"profile":"srd_5_1","mode":"GUIDED","sheet":null}
```

Successful reply envelope has `code:"OK"`, a human message, and data like:

```json
{
  "id":"<server library UUID>",
  "name":"New SRD character",
  "profile":"srd_5_1",
  "mode":"GUIDED",
  "stage":"DRAFT",
  "revision":1,
  "sheet":{"format":"generic_d20","text":{"name":"New SRD character"},"collections":{"srd_combat":["<actual typed combat object>"]}}
}
```

The actual `srd_combat` entry is an object, never the illustrative string above. Keep the complete returned sheet. Rename (`LIBRARY_UPDATE`):

```json
{"character":"<library UUID>","revision":1,"section":"basics","data":{"name":"Mira"}}
```

The reply has revision 2 and stage DRAFT. Finalize using revision 2, then use the returned revision for `LIBRARY_SUBMIT`:

```json
{"character":"<library UUID>","revision":3,"campaign":"<selected campaign UUID>","submission":"<stable generated UUID>"}
```

This returns the campaign character snapshot, with a **different** id, campaign, owner, controller, approval and revision fields. Do not replace the library identity with it. Secure-default submission has `approved:false`.

A manual library inspiration update (`MANUAL_UPDATE`):

```json
{
  "scope":"LIBRARY","character":"<library UUID>","revision":3,"runtimeRevision":0,
  "change":{"action":"INSPIRATION","section":"trackers","key":"","index":0,"value":1}
}
```

For campaign mode, use both current campaign revisions and expect policy/binding rejection if editing is unavailable. Example conflict envelope:

```json
{"code":"CONFLICT","message":"Stale library revision; reload and reconcile","data":{}}
```

Read events (`EVENTS`):

```json
{"campaign":"<campaign UUID>","cursor":0,"kind":null}
```

A private roll event, when authorized, includes structured data such as:

```json
{
  "kind":"ATTACK",
  "roll":{"sides":20,"faces":[18,7],"kept":[0],"modifier":5,"total":23,"purpose":"SRD attack","critical":false}
}
```

This is an excerpt of an event, not a client mutation schema. Neither this API nor the existing UI transport accepts client-supplied authoritative roll results.

## Backend stabilization contract (supersedes earlier bound-tracker limitation)

See [backend stabilization report](ui-overhaul-astra-stabilization.md) for verification, migration and acceptance gates. The earlier checkpoint descriptions above are historical; use this contract for the current branch.

### Independent campaign manual counters

`MANUAL_COUNTER` takes exactly `{character, manualRevision, change}`. `change` is the existing typed `RpgRecords.Change` (`action,section,key,index,value`). It returns the authorized `CHARACTER_GET` snapshot after a successful durable write. `CHARACTER_GET` now includes `manualRevision`, `canTrack` and `manualSections` (an authorized subset of `trackers`, `features`, `spellbook`). `CHARACTER_SECTION` also returns `manualRevision`. Missing capability fields disable the new dashboard controls on older servers.

The current controller may set supported current counters, including increasing them within recorded maxima for manual adjudication. The original owner does not retain this play-bookkeeping permission after assigning control. Campaign managers may adjudicate these counters. Membership, owner lock and field policy are enforced; managers retain their existing policy override. Counter edits do not approve an unapproved character. The owner still retains private reading and ordinary unbound structural editing under the existing policy.

Supported: inspiration, manual death-save marks, exhaustion marker, current Hit Dice, current manual spell slots, and MANUAL pools in `trackers.resources` or `features.resources`. Counter definitions, maxima, nested feature descriptions/uses, preparation, equipment and combat fields remain ordinary structural edits. This operation cannot heal, spend/refund combat actions, edit ammunition, cast, recover resources automatically, enforce conditions, change approval or bind.

Counter updates advance only `manualRevision`; they preserve editing/runtime revisions and active SRD snapshots. Stale counter writes return `CONFLICT`; failed writes return `STORAGE_ERROR` without publication. A replay with the old revision does not apply again. After timeout, reload and explicitly reconcile; do not infer failure or automatically send a rebased write. Generic sheet edits also advance the counter revision so stale indexed slot/Hit Dice updates cannot target a replaced definition.

`CHARACTER_EDIT` accepts additive `manualRevision` alongside its existing fields. Supply it when editing sections. Full replacements of `ui_trackers`, `ui_features` or `ui_spellbook` require this CAS once independent bookkeeping has begun. Older generic edits without it reject rather than overwrite counters; unrelated legacy edits still work. `DashboardSource.sectionArgs` supplies it, and `RecordFormScreen` comparison detects both editing and manual revision changes while preserving staged input. The legacy `MANUAL_UPDATE` retains its bound lock; library workflows still use that operation. Never remove the bound lock or send `MANUAL_COUNTER` as a generic section replacement.

### Storage

Campaign storage is now schema **4**, adding only a `manualRevisions` map. Counter values remain in the existing canonical `ui_*` sheet sections. No new HP/resource database or duplicate sheets exist. Schemas 1–3 load with counter revision zero; the next successful mutation atomically writes schema 4. Characters, approval, bindings, assignments and independent copies are retained. Older binaries cannot read schema 4: rollback requires a complete pre-upgrade world backup, not editing the schema number or relying on a rolling `.bak` file. Library/workflow/section schemas are unchanged.

### Party traversal and events

`PARTY {campaign,page}` keeps its request shape and adds `rosterToken,total,page,pageSize:8,hasMore`. The token covers ordered binding/character identities, editing/manual revisions, online status, campaign/role and sharing-policy generation. It is not an authority credential. Each page independently rechecks membership and filters private data. HP is live when each page is read, not a frozen whole-party health snapshot.

The client aggregates at most 128 pages/1,024 members and publishes only a complete matching-token traversal. Changed tokens discard partial rows and restart. Normal ACTIVE/campaign/events/initiative polling continues between pages; there is no unbounded job queue or render-time request loop. The compact panel caps its visible rows and exposes overflow through L → Inspect party character → searchable full party view. Private sheet access still requires CHARACTER_GET. Continually changing rosters may delay completion; loading/empty state is preferable to mixing permission generations.

EVENTS remains bounded polling, not server push. A cursor beyond the new session sequence resets at the server. A changed session in the HUD forces cursor-zero reconstruction, suppressing historical animations and sorting event history by server sequence. Current-character private RESOURCE/CONDITION/APPROVAL/ASSIGNMENT events recheck ownership/controller/offer access as well as stored recipients and current membership/DM authority. Historical personal combat/dice events retain their original recipient audience while campaign membership is valid; ending an encounter does not erase that history.

New producers: committed SRD damage/temporary-HP loss and zero-HP transitions, actual applied healing amounts, accepted Tactical actions/turns, applied vanilla Tactical health/healing/zero-HP changes, committed manual counters/condition markers, approval invalidation from edits, and privately scoped rejections of admitted SRD operations. No event claims a Tactical action acceptance is a hit. SRD child events derive stable identities from the committed intent; failed resource transactions emit no success. Full inventory/condition/spell gameplay automation, all vanilla status-effect changes, pre-admission packet rejection logging, and permanent history remain unavailable.

Visual polishing should reuse these controls and read models. Keep structure editing locked while bound, show manual counters separately, preserve exact HP filtering, and distinguish pending roster traversal, polling delay and genuine empty state. Do not infer missing events or silently enable counter definitions, rest recovery, spells or class mechanics.
