# UI Overhaul — Checkpoint 5: Combat presentation

## Repository and verification

- Branch: `dev/ui-overhaul`.
- Verified starting HEAD: `8d34a2798ad037b3bae2f2d1764de2a8785921b0`; starting worktree was clean.
- Implementation: `eb3ee9d0973346db3fd1c96443c541f37333a443` — customizable combat HUD and authorized event presentation.
- This report is committed separately; its identifier is included in the final handoff.
- No push or merge performed. Dependencies and character/campaign schemas are unchanged.

Baseline command: `/workspace/cubetable-setup/gradle.sh build runGametest`.
Baseline result: successful compilation/build, **1,388 assertions**, **103 required GameTests**, no failures or required skips.

Final command: the same full build/GameTest command.
Final result: successful compilation/build, **1,456 assertions**, **104 required GameTests**, no failures or required skips. The new HUD suite contains 68 assertions and the new authenticated gateway GameTest checks shared/private roll readback. Every previous test remains enabled.

Graphical Minecraft, actual two-client multiplayer, FPS, measured network response latency and visual-reference matching are **Not verified**. Embedded mock players are not graphical clients. No approved mockup images were supplied in this workspace; layout follows the textual top-left/top-center/center/bottom-left directions.

## Feature status

| Feature | Status | Scope |
|---|---|---|
| Compact party panel | Implemented | Authorized PARTY projection, privacy-filtered HP, class, online state, recorded conditions, initiative/current-turn highlight and recent visible-roll bubble. |
| Complete larger-party browsing | Partially implemented | HUD polls page 0 and presents up to eight members. Further party pages are not combined yet. |
| Initiative strip | Implemented | Existing server ordering and active flags, supplied round only, compact overflow, hidden outside an encounter. Default compact placement avoids the party panel. |
| Original animated dice | Implemented | Lightweight procedural tumbling/settling, authoritative faces and kept indices, modifier/total, server critical flag and natural-face indicator; duration, reduced motion and skip. |
| Shared authorized dice | Implemented | Same server event identity/session/result is read independently by authorized recipients using polling. |
| Immediate shared synchronization | Partially implemented | Polling is scheduled; there is no new push channel or synchronized animation clock. |
| Dedicated log | Implemented | Compact/expanded overlay plus searchable/filterable history screen and live-cache event calculation details. |
| Notifications | Implemented | Actual TURN/REST/APPROVAL/ASSIGNMENT/REJECTION/ZERO_HP events when emitted and authorized; bounded toasts, duration, visibility and optional existing Minecraft sound. |
| HUD customization | Implemented | Deliberate settings-screen dragging, independent scale/visibility, separate fonts, animation speed, reset and local persistence. |
| Direct dragging while playing | Deferred | Input capture is confined to Customize HUD. Ordinary world input is unaffected. |
| Graphical and real-client acceptance | Not verified | Headless checks cover logic/contracts, not appearance or real player interaction. |

## Party and initiative

The party panel uses PARTY's player-bound campaign characters. It does not derive a party from arbitrary nearby entities or add NPC enemies. Exact HP is displayed only if the server returned `hp` and `maximumHp`. A fraction-only response produces a bar; a hidden response displays HP private. Missing/offline skin data uses names and online state rather than another player's portrait. Portrait loading is not implemented here.

Recorded conditions remain informational. No condition, exhaustion, rest or resource rule is executed by the HUD. The active-turn marker and initiative numbers match actor IDs against the existing authorized initiative projection. The renderer retains server tie order. It uses only a supplied round number.

SRD and Tactical encounter IDs come from their existing client state. INITIATIVE still runs the existing server permission checks. The existing Tactical initiative bar is suppressed only while the replacement strip has usable data and is enabled; its turn/movement/action panel remains intact. Unsupported servers keep the legacy Tactical interface.

The first visible party-page limit and polling latency should be checked with eight or more bound players before production use.

## Dice delivery, identity and timing

Campaign rolls are presented from EVENTS. Each event retains its server identity, session, raw faces, kept indices, modifier, total, purpose and critical flag. Advantage/disadvantage highlights the kept die. Natural 20/1 indicators use the raw kept d20, never the modified total. Transient face transitions are decorative arithmetic; no client RNG decides a result.

Initial history and a changed server session are loaded without replaying old animations or notifications. Incremental pages deduplicate by event identity within the session. Campaign switch, disconnect, failed scope reads and expired authorization leases clear private cached projections. Periodic history reconstruction rechecks permissions and suppresses historical animation replay.

Structured roll animations have an eight-entry queue; the fallback packet path has a separate eight-entry queue. Bursts can condense older pending animations while the bounded log retains received event records. History is capped at 512 entries; dedup identities are capped at 1,024; recent-roll bubbles are capped at 64 actor entries and expire. Skipping only advances local presentation time.

Manual dice's legacy packet lacks a stable event identity. In a selected campaign, the default structured-event path suppresses the packet animation and waits for the authorized event. Outside a campaign or on an unsupported UI server, the server's existing packet result can use the new compact renderer. Those fallback results do not gain a fabricated server event identity or permanent history. Explicit Legacy Dice mode retains the old renderer and disables the new campaign dice queue to avoid duplicate visuals.

## Event log and notifications

Default controls:

- **L:** open the dedicated Cubetable event log.
- **Shift+L:** toggle compact/expanded overlay height.
- **O:** skip the current new dice animation locally.
- **Hub → Settings → Cubetable HUD customization:** edit layout/preferences.

These keys are registered Minecraft keybindings and can be rebound. The log supports All, Combat, Dice, Campaign and Rest filters derived from actual event kinds. Follow mode shows newest first and updates when events arrive, including when the retention limit is already full. Read mode leaves the current list in place and indicates new entries. Shared list controls provide scrolling, search, keyboard focus and narration.

Event calculation screens resolve the current authorized cache rather than keeping a permanent private copy. Cache invalidation makes details unavailable. Party-character navigation requests CHARACTER_GET before opening a dashboard; a public party row does not authorize private inspection. Rest and assignment routes reuse the existing screens.

Notifications come only from actual authorized records; they are not a durable inbox. Your-turn wording requires an emitted TURN event whose actors include the local player. The server does not emit every reserved notification type. Rest notices describe request/review and manual adjudication; no HP, time, slots, Hit Dice or resources recover.

Normal chat is untouched. Only mod-owned dice echo and the existing SRD result echo are configurable. The HUD never scrapes chat, intercepts unrelated messages, changes command output or changes server logging.

## Local settings

The existing validated version-1 `HudPreferences` format remains supported at `config/tacticalcombat/hud-layout.json`. New default anchors follow the approved compact layout. Existing saved positions remain intact.

Additional presentation options use `config/tacticalcombat/hud-options.json`: notification position/scale, log/party/dice fonts, outside-combat party visibility, sounds, legacy dice, chat echo and notification/bubble durations. Each file uses AtomicJsonFile validation/backups. The two files are separate atomic writes, not a cross-file transaction; an interrupted options save can leave one file newer than the other. A failed save keeps the staged settings and reports the error.

Customize HUD exposes panel handles and scroll-to-scale resizing for party, initiative, dice, events and notifications. Coordinates are fractions of available space and render bounds are clamped after resolution/GUI-scale changes. Scissoring prevents content painting outside panels. No movement, camera or gameplay input is captured by the overlay renderer. The settings screen itself is non-pausing.

## Networking, privacy and synchronization

Operations used: ACTIVE, CAMPAIGN_GET, PARTY, EVENTS, INITIATIVE, and CHARACTER_GET for deliberate private-sheet navigation. Existing assignment/rest screens retain their documented operations.

The HUD shares UiRequestQueue with all screens, has at most one HUD request outstanding, waits 12 client ticks between background submissions and pauses its background poll while ordinary RPG forms are open. The pump keeps its existing pacing, timeout, queue bound and rate-limit backoff. No world state is accessed off-thread. JSON is parsed on receipt/tick paths rather than during rendering.

Successful authorization reads renew a 100-client-tick cache lease. Campaign role/revision changes trigger reauthorization; event history is periodically rebuilt after catch-up. Denials, malformed replies, disconnect and campaign changes clear caches. This provides bounded stale presentation, not instantaneous revocation: existing APIs have no push invalidation. Polling pauses for forms, so private HUD data can expire while editing and reload afterward.

A normal five-operation cycle takes at least 60 client ticks plus queue/server latency. Event catch-up drains 16-record pages with pacing. Slow clients, menus, heavy UI traffic and rate limits increase latency. Reauthorization/bootstrap may omit animations for rolls that arrive during history reconstruction; received authorized history remains reviewable. No exact real-client latency was measured.

## Producer limitations and precise Astra follow-ups

Existing connected producers include committed SRD attacks, SRD turn starts/DM healing, approvals/revocations/record edits/assignments, rest request/review and manual dice with campaign context. Event kind definitions alone do not establish a producer.

Required future backend work:

1. Add missing Tactical attack/damage/turn and other condition/zero-HP/rejection producers where desired, with actual committed transaction identities and explicit visibility. Do not synthesize them from client HP or chat.
2. Provide bounded push delivery or permission-invalidation notifications if lower latency/instant cache revocation is required. Keep current privacy filtering and replay identity semantics.
3. Add stable authoritative identity to the legacy dice packet before deduplicating that packet and EVENTS universally. The present client deliberately selects one campaign animation path.
4. Supply approved actor/target/character-name references and private-view eligibility for richer clickable event/initiative entries. The current event model mostly supplies actor UUIDs and bounded summary text.
5. Add a safe remote skin/model projection if portraits are required; current fallback does not render the viewer's skin for someone else.
6. Expose explicit party total/truncation information or combine authorized pages for complete larger-party presentation.
7. Keep permanent event archive, bound-manual bookkeeping, spellcasting, class automation, rest recovery, condition enforcement and takeover as separately authorized future gameplay/storage work.

## Tests and performance boundaries

New deterministic coverage includes PRIVATE/BARS/EXACT mapping, offline members, existing tied initiative order, actual round and encounter end, raw-versus-total d20 values, advantage/disadvantage indices, historical bootstrap, shared session/event identities, duplicate suppression, queue limits, recent-roll expiry, event filters, reauthorization/session/campaign resets, retention overflow/new-entry indication, compact non-overlapping default geometry, scale/resolution recovery and settings save/reload. The service test proves hidden modifiers never enter an unauthorized peer's event result.

The new GameTest exercises the actual authenticated UiNetworking gateway with embedded recipient/DM/peer connections: the recipient and DM read the same event ID/session and raw face/total, the peer receives an empty private result, and a cursor read does not retransmit the event.

Rendering uses procedural panels/text/matrix transforms, bounded actor lists and cached event-history projection. No image downloading, new graphics dependency, custom shader or synchronous texture rebuild was introduced. Actual frame allocations, FPS, drag responsiveness, font readability, animation aesthetics, sound and graphical input behavior remain **Not verified**.

## Changed files

- New client UI: `CubetableHud`, `HudSession`, `HudOptions`, `HudSettingsScreen`, `HudLogScreen`, `HudEventScreen`.
- Integration: `TacticalCombatClient`, `ClientCombatState`, `ClientSrdCombat`, `CombatHud`, `DiceAnimation`, `DestinationScreen`, `HudPreferences`, and `assets/tacticalcombat/lang/en_us.json`.
- Tests: `HudUiTests`, `FoundationTests`, `ManagementGatewayGameTests`.
- Documentation: `docs/ui-overhaul-sol-checkpoint-5.md`.

No combat resolution, movement optimization, sheet authority, HP/ammunition transaction, approval/assignment/binding permission or server event database was changed.

## Real two-client acceptance

Use a disposable backed-up world and the same JAR on two actual Java 21 clients and server. Record build SHA, GUI scale, permissions and Pass/Fail/Not Tested per scenario. Earlier Phase 1–3/UI real-client gates remain open.

1. Select an authorized campaign. Bind player characters and inspect compact party names/classes/online state. NPCs must not appear as party members.
2. As DM set PRIVATE, BARS and EXACT. Compare both clients' visible fields; secret exact HP must be absent from the unauthorized result, not merely visually hidden.
3. Start SRD and Tactical encounters. Verify initiative ties/order/current actor and supplied round against server state. End the encounter and verify the tracker hides while the existing combat controls still work.
4. Generate an authorized public/shared manual roll and a private SRD attack. Compare event identity, faces, kept indices, modifier, total and server critical flag on eligible recipients; the ordinary unauthorized peer must see no private animation or log record.
5. Reconnect and restart the server: history should load without replaying old dice. Switch campaigns or revoke membership/DM role and verify cached private data disappears at the next revalidation/lease boundary.
6. Burst rolls: check bounded animation, log retention, latest party bubble and O skip. Enable reduced motion and different speeds/scales on each client; totals must match.
7. Compare default and legacy dice modes. Verify one animation per supported event path and optional dice chat echo; normal conversation/server messages/commands must remain functional.
8. Open L, search/filter, inspect calculations, disable Follow, and verify new-entry indication. Use Shift+L for panel height. Open a party sheet/rest/assignment route and verify server permissions still apply.
9. Trigger emitted turn/rest/approval/assignment notices. Check duration, disabled notifications, optional sound and private recipient filtering. No rest recovery should occur.
10. Open Customize HUD; drag and resize each panel, change fonts/scales/visibility/duration and reset. Save, restart and restore. Check at 320×180, 427×240, 854×480 and varying aspect ratios/GUI scales.
11. Test window resize, overlay clipping, hotbar/chat/scoreboard/menu coexistence, camera/movement input, key rebinds and settings exit. Exact screenshot-to-mockup matching is a later polish pass.
12. Exercise latency, denied requests, lost replies, storage/settings errors and unsupported UI servers. No unbounded backlog, false outcomes or hidden-value reuse should occur.
13. Regress creation/dashboard, independent submissions, approval/binding/control, SRD attacks/ammunition/grid, Tactical movement/inventory/camera, dice/cards and Generic D20/Percentile sheets.

## Next checkpoint

Perform real single-client and two-client visual acceptance before broader UI work. The recommended follow-up is HUD stabilization and approved-reference matching: typography, spacing, die silhouettes/landing feedback, compact panel balance and stronger event-reference navigation. Include larger-party pagination and low-latency/privacy infrastructure follow-ups only with explicit backend scope. This checkpoint implements presentation, not new gameplay rules.
