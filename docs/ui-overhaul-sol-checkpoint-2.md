# Cubetable UI overhaul — Checkpoint 2

Date: October 12, 2026 (UTC). Repository: `shayanzas/Cubetable`. Branch: **`dev/ui-overhaul`**.

Starting checkpoint: `b94d9ea844032640f1186204d324c51fb372a27e`, verified clean before edits. Implementation and regression tests: **`980c95160a4a3b815fdfe76bb3d0ae0433a28339`**. The following documentation commit contains this report and the additive API handoff; its SHA is reported in the final response. All work remained local. No reset, reclone, push, merge, dependency change or published-branch modification occurred.

The unchanged earlier branches were checked: `dev/phase3-grid-performance` remains `a5758b506212b01c7e5dc70409dd037e64e7a5e8`; `dev/phase3-srd-combat` remains `21e13a4eb78ae50bb94c4ef2215ce0fc7d46bec3`.

## Implemented workflows

| Screen | Behavior |
|---|---|
| Hub → Campaigns | Authorized campaigns, server profile, member/manager role, selected indicator, local search and eight-record pages. Open details without changing selection. Administrator-only Create uses authenticated capabilities. No public Join operation. |
| Campaign details | Actual campaign information, explicit membership selection, roster, assignments, approval policies, presentation/rest-request policy, manager membership controls and default profile selection. Selection remains locked during encounters. |
| Members and roles | Online invitation discovery, readable member/co-DM/owner names, member removal, co-DM grant/revocation. Owner/admin role authority is independently advertised and enforced. Unknown account names disable authority-changing choices until identifiable. |
| Approval policy | Manager-only toggles for new/edit approval, owner lock and existing/default owner field rules. Custom stored rules are retained and can be toggled; creation of arbitrary new field-rule expressions is deferred. Ordinary members can inspect policy. No retroactive approval. |
| Presentation policy | Existing PRIVATE/BARS/EXACT party sharing and 1–64-block rest-request radius, using the separate workflow revision. This adds no HUD, automatic healing, rest recovery or time advancement. |
| Campaign roster | Eight-record pages, search, Pending/All filter on the current page, names, profiles, owner/controller labels, approval, binding and readiness hints. Private inspection disabled for unrelated members. |
| Campaign character | Reusable lifecycle view: server sheet inspection, readiness, selection, approval/revocation, offers, clearing control, assignments, eligible bindings, unbinding and source navigation where authorized. This is not the complete character dashboard. |
| Assignments | Permission-filtered paged offers and current control. Recipient can explicitly accept/decline an unbound pending offer; names/status are visible before private-sheet access. Pending assignment does not itself grant private inspection. |
| Player control picker | Online members outside combat and known offline campaign members. Offline recipients must reconnect and manually discover/answer the durable offer. Unknown offline accounts are unavailable in the UI. Ownership/current control/binding do not change merely on offering. |
| Entity binding picker | Current loaded nearby living candidates, names, positions, eligibility/reasons and explicit 64-candidate truncation. Confirmation sends the selected internal identity; server revalidates approval, revision, entity, controller, profile, existing binding and encounter locks. |
| Hub → My Characters | Private server library, Guided/Manual label, profile, Draft/Complete status and supported-field readiness. Search, pagination and New draft. No other player's library is exposed, including to administrators. |
| Draft creation | Guided or Manual/Custom choice. Both can persist an SRD scaffold. Manual can copy a loaded local pack character into a Manual Tabletop or Minecraft Tactical library draft. Local data remains unchanged. Guided scaffold is explicitly incomplete wizard work. |
| Library character | Name editing, grouped SRD numeric combat input editing, inspection, duplicate, validate, finalize, submit and authorized known campaign-copy navigation. No deletion, transfer or export operation is invented. |
| Submission | Choose an authorized campaign, read an independent-copy confirmation, persist a stable submission identity, submit and open the resulting campaign record with actual approval/binding status. Membership, readiness and policy are checked by the server. |

Navigation paths are Hub → Campaigns → Details → Roster → Character and Hub → My Characters → Character → Campaign choice → Confirmation → Campaign copy. Explicit parent screens provide Back/Escape. Returning from a picker/confirmation reloads authoritative state before dispatching its chosen action. Source/copy navigation reuses its existing parent when appropriate instead of building a recurring parent chain. Browsing, opening and closing do not select, bind, approve or save automatically; selection is an explicit separate action.

The current library is server-local, distinct from legacy local character sheets. Creating a manual library draft copies saved local data explicitly; changes in either location do not propagate to the other. Pack numeric/equipment/catalog editing is not replaced in this checkpoint. Existing local pack tools remain accessible through Tabletop Tools; later library section editors will cover additional server-local fields.

## Reused components and layout

All screens use Checkpoint 1's `RpgScreen`, `RpgTheme`, `RpgButton`, `RpgList`, `RpgTextBox`, `RpgConfirmationScreen`, search controls and shared `UiRequestQueue`/`ClientUiApi`. No second renderer, protocol, combat-stat model, commercial asset, font scaling or custom texture framework was introduced.

`ManagementScreen` composes the existing widgets into a scrollable information/action workspace. `PagedManagementScreen` adds the common pagination row. `RecordFormScreen` retains staged text across field pages, refresh, resize and rejected saves. SRD input staging uses the existing `SrdAuthoring`/`SrdCharacterCodec`; equipment/catalog values remain unchanged. At 427×240 logical pixels the form has two visible numeric inputs per page; taller windows show more. This is a grouped section editor, not a substitute guided wizard. Read-only inspection currently uses wrapped serialized server data rather than a polished dashboard.

Geometry assertions cover 427×240, 640×360, 854×480 and 320×180. Lists stay above the footer/pagination and all actions remain reachable through scrolling. Real clipping, typography, button interaction, focus, narration and rendering are **Not Verified**. Long row descriptions use the existing full-text hover tooltips; the status line also has a full-message tooltip. The Checkpoint 1 no-late-blur behavior is preserved.

## Backend operations and additive read models

Integrated operations: `CAPABILITIES`, `ACTIVE`; `CAMPAIGN_LIST/CREATE/GET/SELECT/INVITE/REMOVE/REVOKE_DM/POLICY/PROFILE`; `ROSTER`; `LIBRARY_LIST/CREATE/GET/UPDATE/VALIDATE/FINALIZE/DUPLICATE/SUBMIT`; `CHARACTER_GET/SELECT/APPROVE/REVOKE`; `ASSIGNMENT_LIST/OFFER/ANSWER/CLEAR`; `ELIGIBLE_PLAYERS/ELIGIBLE_ENTITIES/BIND/UNBIND`; and `PARTY/PRESENTATION_POLICY` for existing visibility/radius settings.

Requests retain the documented exact argument shapes. UUIDs and revisions are read from replies and stored internally; no management workflow asks the player to type them. Approval, derived statistics, roles and executable formulas never come from client authority flags.

Narrow additive response fields were necessary for readable, permission-aware controls:

- Network `CAPABILITIES.canCreateCampaign` is authenticated server administrator authority, not campaign manager authority.
- `CAMPAIGN_GET.canManageRoles` reflects owner/admin authority. Manager-only details additionally include campaign `owner`; member and co-DM identities remain manager-only.
- Network `playerNames` resolves only player identities already present in an authorized response, bounded to 256 identities/depth four. Names use online players or the server's existing UUID cache, never external lookups or arbitrary account enumeration. Missing names get an explicit unknown/offline label. Private sheets/history are excluded from the resolver.
- `ROSTER` adds profile and a basic readiness hint; private SRD readiness/statistics still require `CHARACTER_GET`.
- `ASSIGNMENT_LIST` adds the character name/profile and binding flag without private sheet contents. Offered recipients keep the existing restricted visibility.
- `LIBRARY_LIST` adds supported-field validity/error count. `LIBRARY_GET` adds validation errors and at most 128 source-associated campaign-copy summaries, only for the source owner and currently authorized campaign membership, with a truncation flag. Relationships are informational and do not confer approval/control.
- `ELIGIBLE_ENTITIES` adds block-position labels to distinguish creatures with identical names.
- Unauthorized `CHARACTER_GET` is explicitly `FORBIDDEN` before live HP capture; it returns no private data. The existing authorized capture and snapshot paths remain intact.
- Informational `ui_origin` provenance is validated defensively. Missing/malformed legacy custom provenance is skipped without rewriting it or making unrelated library reads fail.

Protocol/version and all server character/library/workflow schemas are unchanged. Existing older clients may ignore the additional fields. Against an older foundation server, missing creation/role capability disables those controls; existing membership browsing remains available. No new mutation endpoint or alternate authorization path was added.

## Quick-switch audit and security

The main hub still filters quick campaigns using server `manager` and sends `{campaign,quick:true}`. The server independently requires manager authority on the **target** campaign and the normal encounter lock. The ordinary Campaigns details Select action sends `{campaign}` and preserves legitimate self-selection among authorized memberships. The current hub's decision to show/disable its quick button is presentation; it does not narrow normal member selection permissions.

Creation requires server administrator authority. Invitation and policy/profile mutation require manager authority. Co-DM changes require campaign owner/admin. A member cannot approve their own character; controllers do not become owners. Approval/revocation and assignment mutation stay locked for bound characters. Selection does not bind. Offers do not bind. Binding requires approval and all existing service validation. Default-profile changes affect future defaults, never existing character sheets or active encounters. Confirmations precede destructive/authority-changing operations; successful UI state follows server acknowledgment/readback only.

Phase 3 combat/action/movement/resource transactions, dice authority, HP/ammunition updates, encounter revisions, Generic D20/Percentile packs, camera/grid/HUD and legacy sheets/services were not edited. The existing commands and server workspace remain available. Live takeover, non-DM remote NPC delegation and automatic reconnect restoration stay unavailable.

## Synchronization, conflicts and safe retries

The shared request pump remains bounded to 16 queued operations and one in flight, with eight-client-tick send spacing and RATE_LIMIT backoff. Management choices ignore repeated activation while a request is pending. Closed/resized screens invalidate stale callbacks/queued reads using the existing generation rules. No mutation is automatically retried. Every error—including permissions, conflicts, missing entities, unsupported transport, timeouts, rate limits and storage errors—is visible; failed changes require refresh/reconciliation.

Forms retain their original editing revision and text. If a reload reveals a different editing revision, Save opens a comparison of the latest server sheet and staged fields. Explicit confirmation is required to apply the staged section; another change during comparison cancels that attempt. CAS is still enforced by the server. Invalid numbers/formulas fail staging validation, without losing text. Cancel/discard is explicit; an in-flight save cannot be abandoned with the form's ordinary Back action. Resize invalidates its callback and requires fresh readback rather than optimistic success.

`SubmissionBook` stores one stable identity per source/campaign/revision intent, in fixed client config `tacticalcombat/ui-submissions.json`, schema 1, at most 256 records, 65,536 JSON characters/depth eight. It contains IDs/revisions only, no sheets or authority. Existing `AtomicJsonFile` provides validated write-before-publish, fsync/atomic replacement and backup recovery. The token is persisted **after confirmation and before sending**. A failed local write sends no submission; invalid/future journal data fails closed rather than generating a replacement identity.

Tokens survive navigation, disconnect, process restart and normal server restart. After campaign choice, the source is reloaded; a known copy with the same submission identity is reopened. Otherwise the same identity is reused. Changing a source revision while an unresolved intent exists cannot silently renew that identity. To intentionally make another copy after a resolved source/campaign submission, duplicate the library source and explicitly submit that independent draft. This UI intentionally avoids automatically creating multiple copies on repeated Submit presses.

Campaign/draft creation and duplication lack durable backend idempotency keys. An uncertain acknowledgment disables repetition within that workspace and directs the user back to the browser to reconcile before another explicit creation. Their shared request lifecycle never auto-retries. This is distinct from durable `LIBRARY_SUBMIT` retry safety. Journal pruning/archive UX is deferred; reaching its bounded capacity rejects new intents instead of discarding existing retry identities. Deleting the journal manually would discard this client's remembered retry history and is not an implemented recovery workflow.

## Verification

Baseline, before code changes:

`/workspace/cubetable-setup/gradle.sh build runGametest`

**BUILD SUCCESSFUL; 1,155 assertions, 95 required Minecraft GameTests, zero failures/skips.** Log: `/tmp/ui-checkpoint2-baseline.log`.

Final command after all implementation/test changes:

`/workspace/cubetable-setup/gradle.sh build runGametest`

**BUILD SUCCESSFUL in 36s. Compilation passed; 1,257 assertions; 101 required Minecraft GameTests; zero failures, errors or skips.** Log: `/tmp/ui-checkpoint2-acceptance.log`. `build/gametest-results.xml` independently contains 101 cases and zero failure/error/skipped nodes. Original assertions/tests remain enabled; dependencies were not changed.

New `ManagementUiTests`: **102 assertions**, exercising actual presentation predicates and backend operations: authorized campaign filtering; member/co-DM/admin capabilities; policy/security restrictions; library privacy, creation, duplication, validation/finalization; editing conflicts; stable submit identity, source/copy isolation and relationship readback; approval/revocation; offers/answers/control separation; bound locks; grouping/layout; readable name fallback; bounded journal; restart/backup recovery, future-schema refusal and failed-write nonpublication; malformed legacy provenance.

Six new `ManagementGatewayGameTests` use actual authenticated embedded connections:

1. Administrator versus co-DM creation/role authority, authorized name discovery and ordinary-member private-field exclusion.
2. Live candidate binding/release through `CharacterService` and bound reassignment rejection.
3. Unapproved candidate rejection and entity removal between discovery/binding, with unchanged repository state on rejection.
4. Pending offer visibility, private-sheet denial, wrong-recipient rejection and acceptance without ownership transfer or binding.
5. Manager-only presentation policy, separate revision conflicts and unchanged character/combat state.
6. Offline offer persistence, missing-cache fallback and no automatic control/ownership/binding transition.

Intermediate failures were resolved: a layout assertion incorrectly reserved pagination space on an unpaged screen; unauthorized pre-read HP capture reported generic rejection rather than explicit permission failure; and an added offline-name fixture assumed the headless TestServer had a `UserCache` (it does not). The final fixture now tests that actual missing-cache behavior and durable offline offers, while online name resolution is positively tested separately. Production cached-offline-name behavior is compiled/static-inspected and remains a real-client acceptance item. No existing test was removed, disabled or weakened.

All tests are dependency-free foundation assertions or embedded/mock-player Minecraft tests. **No graphical Minecraft clients, real two-client sessions or remote multiplayer tests were run.** No display/Xvfb is available. Earlier Phase 1, 2 and 3 real-client gates remain open. No final automated regression was observed. This checkpoint did not rerun dedicated production performance benchmarks; existing optimized movement/combat regression tests still run.

Release artifact: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `bb224b546d90085648e468ec0ef188e54072cbc86152e568f86413d5a2f894fb`.

## Real-client acceptance checklist

Use a backed-up disposable world and this exact JAR with pinned Java 21/Minecraft 1.21.1/Fabric Loader 0.16.9/Fabric API 0.116.1+1.21.1. A is server administrator/campaign owner, B ordinary player; temporarily grant B co-DM for role tests. Record commit/JAR hash, GUI scale/logical dimensions, client/server logs and Pass/Fail/Not Tested per step.

1. Pause → Hub → Campaigns: A creates a named campaign, opens details, selects it and changes the default profile. B cannot create even when made co-DM. Verify ordinary authorized membership selection still works and hub quick-switch stays DM-only.
2. A invites B using online names; B refreshes Campaigns and discovers membership. Confirm no public Join button exists. Grant/revoke B's co-DM role with confirmation, including revocation while B's management screen is open. Refresh/attempt mutation must remove stale authority.
3. Inspect names, owner/co-DM/member labels, long names/tooltips, search, eight-entry pages, empty/no-match lists and Back/Escape. Reconnect a previously known account: cached offline name should appear. Unknown offline identity controls must stay unavailable, without typed UUIDs.
4. B opens My Characters, creates Guided and Manual SRD drafts, saves names and grouped numeric SRD fields. Validate/finalize a valid draft; blank name or invalid HP/number must explain errors and preserve text. Verify all saved changes across reconnect/server restart.
5. Load Generic D20 and Percentile local characters in Tabletop Tools, create independent manual library copies and compare source/library changes. Original local files, rolls, dice/cards, resize behavior and themes must stay intact. Tactical copies must pass actual profile requirements; validation/submission errors must remain readable.
6. Duplicate a source and confirm independent identity/DRAFT stage. Finalize and submit to a named eligible campaign, read the independent-copy explanation and inspect actual approval. Change source HP/name later; campaign copy must stay unchanged. Follow source/copy links and Back without building recurring navigation chains.
7. Delay/drop a submission acknowledgment. Refresh the source, restart/reconnect the client and retry the same intended submission. Verify one campaign copy and a retained local journal token. Inject local journal write failure/corruption in a disposable client config: no replacement identity or packet should be silently sent.
8. A reviews Pending/All roster, inspects readiness, approves and revokes unbound characters. B cannot self-approve or inspect another player's private sheet. A generic revoked/change-request state must not imply a written review conversation.
9. A offers control using a player name; B opens Assignments and declines, then accepts a new offer. Check original ownership, previous controller after decline and new controller after acceptance. A clears an unbound assignment; owner control returns. Pending recipients must not see the private sheet before acceptance.
10. Offer to a known offline member, reconnect that account and manually refresh/accept. There must be no automatic notification/takeover/rebinding promise. If the name is unknown, wait for reconnect instead of manipulating an unidentified account.
11. A chooses an approved character's binding candidate by name/position, confirms and binds. Check actual HP/movement/initiative authority, correct controller compatibility and original ownership. Selection alone must not bind. Unbind normally and test offline/removed-entity release. Remove/change a candidate after discovery: server rejects stale/occupied/unapproved/incompatible targets.
12. Change approval owner locks/fields and automatic future-approval policy; verify existing approvals are unchanged and players cannot bypass locks. Change PRIVATE/BARS/EXACT sharing and radius; verify permission/revision conflicts and no HP/rest recovery/encounter mutation.
13. Start an actual encounter: ordinary campaign/character switching, bound reassignment and approval revocation remain blocked. Try stale choices/revisions across two clients. Rejections must preserve turns, HP, ammunition, resources and bindings.
14. Edit a library section in two clients for the same account where feasible, or use a controlled second request source. Stale Save must conflict, retain text, display the latest/staged comparison and require explicit reconciliation. Change it again during confirmation: retry must be cancelled/rejected rather than overwrite silently.
15. Repeat refresh/open/close/resize with latency, TIMEOUT, RATE_LIMIT, server storage failure and disconnect. Confirm bounded work, ignored stale replies, no duplicate automatic submissions, preserved form text and reasonable return navigation. Against unsupported/older foundation servers, unavailable server-specific authority controls must disable safely.
16. Test 427×240 logical size and 320×180 fallback plus larger windows, mouse wheel, Tab/Shift-Tab, arrows/Page keys/Enter, focus, narration, clipping and crisp text. Inspect both legacy and new screens for blur. Re-run earlier Phase 1–3 camera/fading/grid/HUD/inventory/Tactical/SRD/dice/card acceptance gates with two real clients.

## Status and next checkpoint

| Requirement | Status |
|---|---|
| Campaign browser/details and supported management operations | Implemented; graphical acceptance Not Verified |
| Approval, assignment and supported binding lifecycle | Implemented; graphical/two-client acceptance Not Verified |
| Independent library lifecycle and submission/reconciliation | Implemented; graphical/two-client acceptance Not Verified |
| Supported name and grouped SRD numeric editing | Implemented |
| Full library section editors, equipment/catalog authoring and detailed dashboard | Deferred to later UI checkpoints |
| Full 11-step guided wizard and comprehensive build legality | Deferred, explicitly excluded |
| Public joining, deletion, cross-server transfer/import/export, written review conversations | Unsupported backend operations; not invented |
| Live takeover, remote NPC delegation, automatic reconnect restoration | Deferred; existing restrictions preserved |
| Build and all automated regressions | Verified |
| Actual rendering, cached offline-name behavior and real multiplayer UX | Not Verified |

Next: execute the real-client checklist, stabilize any visual/request-lifecycle issues it reveals, then implement the dedicated guided-creator checkpoint with supported catalogs/section editing and dirty-state reconciliation. Preserve the separate combat authority and deferred bound-edit/live-takeover design gates. No Phase 4 gameplay is implemented or authorized by this checkpoint.

## Changed files

- `src/gametest/java/dev/tacticalcombat/combat/ManagementGatewayGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/ui/AssignmentScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/BindingPickerScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignBrowserScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignCharacterScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignDetailScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignMembersScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignPolicyScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignPresentationScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CampaignRosterScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ChoiceScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ClientSubmissionBook.java`
- `src/main/java/dev/tacticalcombat/client/ui/HubScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryCharacterScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryCreateScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ManagementScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ManagementState.java`
- `src/main/java/dev/tacticalcombat/client/ui/PagedManagementScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/PlayerPickerScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/RecordFormScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/SubmissionBook.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/ui/ManagementUiTests.java`
- `docs/ui-overhaul-sol-checkpoint-2.md`
- `docs/ui-overhaul-sol-handoff.md`
