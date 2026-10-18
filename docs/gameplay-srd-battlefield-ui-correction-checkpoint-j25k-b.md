# J2.5K-B — Tactical battlefield interaction correction

Base: `b980894876d4694f7c25654e51ad15d701562e5b`.
Branch: `dev/gameplay-srd-battlefield-ui-correction-j25k-b`.
The delivery response supplies the exact verified remote commit.

## Movement and contextual interaction

Ground hover uses the authorized K-A grid/path and displays cost/remainder beside the pointer. An explicit ground click selects a destination without entering Move mode. Its compact popup contains Move and Cancel, anchored to the click and flipped/clamped above the command bar. A second ground selection replaces the first. Selection never moves an actor.

`SrdBattlefieldInteraction` owns one movement or actor context. Movement confirmation binds encounter, actor, turn, revision and permissions; the fresh grid revision, current path, positive cost and pending guard are checked again immediately before dispatch. One local dispatch opportunity is consumed per selected confirmation. Turn, actor, permission or relevant revision changes invalidate it. Same-scope read-only refresh can retain the destination, but confirmation is disabled while the grid is unavailable; an unreachable refreshed destination is discarded. Pace changes preserve the selected destination and apply only to future confirmed routes.

Unavailable terrain never gets an enabled Move button. Pointer feedback uses supplied approach-edge reasons where available, otherwise says that no authorized route is available. Approach failures are not falsely described as proof that every possible route is blocked. Refresh explicitly requests read-only reconciliation/grid discovery, never retries a movement mutation. K-A pathfinding, costs, movement execution and walking metadata are unchanged.

## Attack interaction and request lifecycle

Clicking a visible combatant selects its world marker and opens an adjacent actor context. Inspection remains available to viewers who cannot act. One supported attack configuration immediately requests one server preview in response to that deliberate click. Multiple configurations appear as a compact scrollable list; selecting one requests its preview. No configurations produces an explanation. The server continues to decide actual equipment, ammunition, reach, geometry and action legality.

The accepted preview supplies weapon, attack modifier/expression, final roll mode, damage, reach/range, cover, stock and action cost. Confirm Attack sends only its server-issued intent ID. Cancel spends nothing. Short viewports retain confirmation controls and provide full authoritative cost/range information in the confirmation tooltip. Failed previews expose their actual rejection and a deliberate retry; there is no render/tick/hover/resize-generated preview.

Additive preview metadata identifies authenticated actor, target, turn, revision and originating request. `SrdPreviewGate` rejects cancelled/switched/late responses. `SrdReplyGate` prevents a late admitted acknowledgement from releasing a newer pending mutation. Non-admitted unauthorized/rate-limited rejections retain the existing empty-data contract; stale revisions are ignored client-side. Old receipt retrieval is explicitly marked replayed. No persistent schema or combat rules were changed.

Selecting another actor while a request is pending can update inspection, but cannot dispatch another operation. Once pending clears, a deliberate preview retry/choice is required. Movement/attack buttons recheck current authority at callback time. Orbit capture blocks confirmation clicks and activation keys. No automatic attack, End Turn or movement submission is added.

## Persistent controls, menus and pace

The small command bar contains Dash, Dodge, Drop Prone/Stand and End Turn, followed by History, Pace, DM and Help. Movement/attack confirmation buttons exist only in their contextual popup. The More panel, routine Details/DM routing and large pace submenu are removed.

Pace opens a small four-value list: Fast 1, Brisk 2, Walk 4, Slow 6 ticks per square. The selected name stays visible; tooltips distinguish timing from SRD speed/cost. Help explains direct selection, camera and shortcuts. Refresh and recenter are discreet controls. DM opens the established Workspace directly. A paused manager can still enter the established recovery/adjudication screen; its operations are not rewritten or expanded here.

Utility panels do not leave competing invisible contextual buttons underneath. History opening dismisses the executable selection; pace/help temporarily hide the popup without changing the selected destination. Disabled actions explain authority, pending state, spent action, movement, standing prerequisites or pause. Existing chat, inventory, character/Quick Sheet, DM and End Turn shortcuts remain available. Keyboard focus uses the existing Minecraft/RpgButton navigation.

## Initiative, resources and labels

Snapshots now explicitly publish the actual established initiative sequence, including server-resolved ties. The client uses that sequence rather than sorting names/values or guessing from arbitrary rows. The initiative strip uses separate compact combatant tiles with initiative results, round and an active highlight. It follows a new active actor automatically and supports wheel scrolling for larger rosters. The separate current-actor resource panel remains visible while the strip is manually scrolled.

The resource panel presents the actor name, authorized HP, movement/action state and authorized Dodge/Prone. Short viewports prioritize movement/action availability. A paused encounter says DM adjudication is required, without calling zero HP automatic death. Unsupported bonus-action boilerplate is removed.

World labels use a name plus restrained active/control glyphs and authorized Dodge/Prone text. Active labels are prioritized, followed by the selected target. Width limits, collision suppression, translucent small backgrounds, head clearance and HUD exclusion reduce clutter. Gold marks the current actor; selected and hovered actors receive distinct restrained markers. Labels, ground indicators and picking retain K-A's rendered position and solid-geometry occlusion. No allegiance is inferred from entity type.

## Camera, fading and hotbar

Screen-relative camera right is `(-cos(yaw), -sin(yaw))`; the old SRD transform used the opposite sign for A/D. W/S retain the ground-projected camera-forward axis. Deterministic tests cover yaw 0/45/90/180/270. This only changes camera pan, not actor movement or legacy Tactical controls.

Short right-click cancels context. Right drag starts orbit after a five-pixel threshold and does not cancel on release. Middle drag remains supported. UI panels do not start camera gestures; pointer release/screen removal reset capture. Left clicks and activation keys cannot confirm gameplay during captured orbit. Wheel zoom and recenter remain.

SRD block fading uses nine narrow torso-silhouette rays, capped at 192 candidate blocks, instead of a widening cone. Only loaded opaque/leaf blocks on those rays are considered; ground below the actor is excluded. Sets update at bounded cadence, changed sections alone are rebuilt, stale appearance is restored on exit/world change, and the original Tactical cone policy is retained. Collision, targeting and server sight remain untouched. This is visual assistance, not fog of war.

A narrowly scoped `renderHotbar` hook suppresses the vanilla hotbar only during SRD battlefield presentation. Inventory and selected slot are untouched. Opening inventory/other screens, encounter exit, world change and disconnect restore ordinary rendering through the presentation predicate. Health/hunger/XP rendering is not globally cancelled. The Minecraft 1.21.1 hook descriptor and generated remapping were inspected; actual graphical rendering remains pending.

## Results, Dodge and native effects

Authorized results show Normal/Advantage/Disadvantage, D20 faces, kept face, final attack total, rolled damage and authorized HP/temporary-HP changes. The client never attributes disadvantage to Dodge unless a source is supplied; this contract supplies final mode, so the UI reports final mode only. Existing Dodge eligibility, expiration, sight and Dexterity-save rules are unchanged.

Compact prioritized result summaries show hit/miss/critical, mode and HP loss (or rolled damage with HP private). The full calculation stays in categorized, line-scrollable history. Attack results keep their readable hold instead of being immediately replaced by routine movement/turn status. Preview acceptance, rejection, movement, Dash, Dodge, posture, turn and pause events remain distinct. Encounter exit uses a local action-bar notification while transient private encounter data is cleared.

A new authorized committed result can trigger the attacker's native hand swing and a brief target ground-ring impact for a hit. The two-argument local swing overload avoids ClientPlayerEntity's packet-sending overload. No damage method, new dice, world particle broadcast or guessed target is used. Preview/rejection and replayed receipts do not trigger this effect. Existing result IDs deduplicate presentation; at the 256-ID session bound, new optional animations stop rather than evicting IDs and enabling replay. Authorized result text remains accessible. K-A model walking inputs and native hand swing operate through separate parameters.

## Validation

Final delivery run: **BUILD SUCCESSFUL in 1 minute 18 seconds**, Java 21.

- 42,889 foundation assertions across 46 suites (K-A: 39,959 / 45).
- 175/175 GameTests, zero failures, errors or skips (K-A: 172).
- 2,748 catalog checks + 15,115 coverage checks = 17,863.
- Existing transactions, recovery, movement/Dash/Dodge/Prone, ownership, Tactical/Generic D20, DM Workspace, character and catalog suites passed.

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `f9cfc2d887bb6beea026c3b665718281a7850e9342976c18d48506f7eac06b46`.
Evidence: `build/j25kb-delivery-validation.log`, `build/j25kb-targeted.log`, `build/gametest-results.xml`. Existing test API/Gradle deprecation warnings and the optional authentication public-key DNS warning are distinct from test failures.

Command (Java 21): `bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage`.

Focused tests cover movement selection/cancel/one-submit/stale scopes, attack choice counts, intent/reply correlation, revocation and actor changes, popup bounds across representative GUI-scale-equivalent sizes, initiative ties/order, camera axes and drag gestures, hotbar lifecycle policy, narrow fade bounds, prioritized history and roll-mode wording. New GameTests exercise one manager controlling an actual player and prepared Husk through production preview/confirmation, old-intent rejection, receipt replay without another cost, actual tie ordering and accepted Dodge-disadvantage faces/kept die.

All earlier suites remain enabled. Development corrected a new fixture that expected the closed-encounter receipt code while its encounter was still open. Existing privacy/flood tests caught request metadata on pre-admission rejection; the implementation was corrected to preserve their exact empty-payload contract. Those assertions were retained. The scoped review also corrected utility-panel click-through, activation during orbit, stale acknowledgement handling and historical native-effect replay.

## Graphical limits and acceptance

**J2.5K-B GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.** No graphical display/client acceptance environment is available. Unit/GameTests do not establish actual mouse feel, native rendering, label readability, GUI scales, walking smoothness or camera/fading quality. K-A walking and earlier unverified graphical items remain pending. J user-passed costs, crawling/standing, rejected-attack action preservation and HP persistence are preserved as prior observations, not claimed retested graphically.

Popups clamp in tiny windows and prioritize buttons; some extended preview text uses tooltips. Labels can be suppressed rather than overlap. Utility panels suspend the contextual popup while open. Initiative scroll can intentionally show other combatants while the current actor remains in the resource panel. Native effects are simple hand swings/rings, not custom creature attack animations. Fade rendering still depends on the project's existing FRAPI renderer integration. Matching client/server builds are required for strict preview/reply correlation and new metadata. No true fog of war, rules expansion, DM Rolls fix or new recovery mechanics are included.

Use matching client/server JARs in a separate test world:

1. Hover three squares away: inspect 15-ft route/cost near the cursor. Click, cancel, verify no spending; reselect and confirm once. Repeat split movement and test invalid/occupied ground and read-only refresh.
2. Click adjacent Husk: single unarmed choice should request one preview with no choice page. Cancel/reselect/confirm. Test multiple weapons, out-of-range rejection and target switching during a pending preview.
3. Check popup locations at each screen edge, short/narrow windows, and GUI scales 2/3/4. Confirm/cancel must stay accessible; disabled controls must not click through.
4. Watch K-A player/Husk walking and pathing at all four paces. Change pace before confirmation; verify costs/destination stay correct and accepted routes keep their captured pace.
5. Check actual initiative/tie order, active highlight, many-actor scrolling, resources, compact names and authorized status markers. End Turn through the button and keyboard; verify old commands clear when the same manager changes actors.
6. At several yaw angles, test screen-relative WASD, middle/right drag, short right cancellation, zoom and recenter. Drag while a popup is open; no gameplay may confirm at release or from a second button.
7. Rotate within the enclosed arena: only narrow material occluders should fade. Verify ground/nearby walls remain readable, faded solids still block picking and all blocks restore after combat.
8. Verify the vanilla hotbar is hidden in the battlefield and returns in inventory, after combat, world change and reconnect. Check chat, Quick Sheet, character, DM Workspace and return navigation.
9. Activate Dodge, change turns and attack it. Inspect final roll mode, both dice/kept die where applicable, hit/miss and authorized HP loss. Compare manager and ordinary-player visibility. Do not infer a Dodge rules failure solely from visuals.
10. Inspect native swing/hit ring only after a committed result, and ensure preview/rejection/refresh do not replay it. Open history and scroll the entire result. End/paused encounters should explain DM adjudication and retain existing recovery access.

## Changed files

- `docs/gameplay-srd-battlefield-ui-correction-checkpoint-j25k-b.md`
- `src/gametest/java/dev/tacticalcombat/combat/SrdBattlefieldUiGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/BlockFade.java`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/SrdAttackFeedback.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldActions.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldInteraction.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldPresentation.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdCombatEffects.java`
- `src/main/java/dev/tacticalcombat/client/SrdGridRenderer.java`
- `src/main/java/dev/tacticalcombat/client/SrdOcclusionMask.java`
- `src/main/java/dev/tacticalcombat/client/SrdPreviewGate.java`
- `src/main/java/dev/tacticalcombat/client/SrdRecentActivity.java`
- `src/main/java/dev/tacticalcombat/client/SrdReplyGate.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/mixin/client/InGameHudMixin.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/SrdBattlefieldCorrectionTests.java`
