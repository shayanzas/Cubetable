# Cubetable UI overhaul — Checkpoint 1

Date: October 11, 2026 (America/New_York). Branch: **`dev/ui-overhaul`**.

Starting HEAD: `c82cff23db504d5213b3fc09b6dd00ac26882542`, the tested UI foundation and its handoff. Implementation commit: **`cb523de33958878d2211d43d6987dfd48c199430`**. The following documentation commit contains this report and the updated API handoff; its exact SHA is included in the final response. Work continued in the original workspace and branch. No reset, reclone, push, merge or published-branch modification occurred. The fork destination from the earlier conversation remains unresolved and was not used for this local checkpoint.

## Outcome and scope

The checkpoint adds an actual Minecraft pause-menu entry and a functional six-destination Cubetable hub. Campaign and character selection use the existing versioned UI API. Existing manual sheets, SRD controls, Tactical battlefield, server workspace and Minecraft options remain accessible. Later editors and HUD features have explicit pending-development destinations.

The approved mockup image was not attached or found among workspace assets. The implementation follows the supplied charcoal/stone-panel, gold-accent, compact RPG description with original procedural rendering and Minecraft's native font. No external artwork, copyrighted RPG assets, rasterized mockup, custom font or new texture dependency is used. Pixel-perfect mockup fidelity is not claimed.

## Implemented screens and navigation

| Entry/destination | Implemented behavior |
|---|---|
| Minecraft Escape menu | Fabric `ScreenEvents.AFTER_INIT` adds **Cubetable** to the existing visible `GameMenuScreen`. Placement uses actual widget bounds and reserves the title. It prefers space below the controls, then an adjacent/edge position. Existing controls are never moved/removed. A completely occupied third-party layout gets no overlapping button. |
| Main hub | Campaign/character selectors, six destinations, Refresh, Help and Back. Authoritative selected/bound labels and approval/binding status appear at the top. |
| Campaigns | Searchable paged campaign browser for authorized memberships; ordinary members can select permitted campaigns here. Ruleset names and DM/member status are human-readable. Full creation/invitation/settings UI is deferred. |
| My Characters | Library summaries and campaign-character tabs. Library entries open an explicit pending-editor information screen. Campaign entries can select eligible authorized characters without binding. Full library editor, wizard and dashboard are deferred. |
| Encounters | Existing SRD interface and active Tactical battlefield access. The new encounter browser/setup UI is explicitly pending. Existing start/watch commands remain the current setup path. |
| DM Tools | Disabled for an ordinary member/no selected campaign/unsupported or unverified state, with tooltip. On activation it rechecks `CAMPAIGN_GET` and opens only if the server still reports manager authority. Bridges to the existing server campaign/approval workspace; full DM dashboard is deferred. |
| Tabletop Tools | Opens the existing resizable local sheet, Generic D20/Percentile packs, manual dice/card workflows. |
| Settings | Opens actual Minecraft Options for GUI scale/accessibility/video. Future Cubetable HUD controls have a labelled pending-development page. No nonfunctional settings are represented as implemented. |
| Help | Wrapped scrollable instructions explaining commands, selection/binding, unsupported servers and later checkpoints. |

Every new screen holds its explicit parent; Back/Escape restores it. Existing manual and SRD screens now accept an optional parent when opened from the hub. Their no-argument constructors and command/key entry points still return to gameplay as before. Returning to the Tactical battlefield uses its existing Escape→vanilla pause menu behavior. New RPG screens do not pause the integrated server, allowing authoritative responses/encounter processing; returning to the vanilla pause menu preserves its normal pause behavior.

## Shared components

Source package: `src/main/java/dev/tacticalcombat/client/ui/`.

- `RpgTheme`: original charcoal/gold procedural panel palette and native-size text.
- `RpgScreen`: background-before-content ordering, explicit parent navigation, bounded request helper, loading/status/error messages and full-message hover tooltip.
- `RpgButton`: native keyboard/narration support, title/subtitle cards, disabled tooltips, focus/hover and selected-tab styling.
- `RpgList`: clipped summary-card lists with scroll bar, mouse-wheel, arrow/Page Up/Page Down and Enter/Space selection. Long names remain available through tooltips.
- `RpgTextBox`: wrapped, clipped and scrollable help/pending text; no microscopic font scaling.
- `RpgConfirmationScreen`: reusable Cancel/Confirm dialog with default Cancel focus and a one-shot accepted callback. It is provided for later edit workflows; quick selection adds no unnecessary confirmation step.
- `BrowseScreen`: shared searchable dropdown/selection screen and paged browser, local search and explicit empty/error states. Selector buttons open a separate searchable selection panel with parent Back/Escape; this is not a fully animated inline dropdown.
- `InfoScreen`, `DestinationScreen`: clear pending-development pages and bridges to working interfaces.
- `UiLayout`, `HubState`: pure geometry and server-derived presentation/choice policy used by implementation and headless tests.
- `UiRequestPump`/`UiRequestQueue`: bounded, shared client request pacing and lifecycle protection.

At approximately 427×240 logical pixels, all six hub cards fit in two columns and three rows with native-size text and a visible footer. Larger sizes retain the same layout up to a 720-pixel panel. Narrower/shorter screens use a vertical scrollable destination list instead of shrinking all controls/text. Selector pages show a bounded eight-record server page with local scrolling and search. Search applies to the current page only and sends no request per keystroke. Native text-field focus and widget keyboard behavior remain available.

## Campaign/character authority

Hub quick campaign selection is DM/co-DM/admin-manager only. Target campaigns in that dropdown are filtered using server-derived `manager`; the server independently verifies the role. Ordinary campaign browser selection still uses membership permissions. Both selection paths keep the existing encounter lock.

The existing `CAMPAIGN_SELECT` operation now accepts an additive optional **`quick:true`** key. When present, exact args are `{campaign,quick}` and the server requires that the authenticated requester manages the target campaign. `quick:false` does not bypass this check. Without `quick`, the original `{campaign}` membership selection operation is unchanged. `CAPABILITIES.hubQuickSwitch:true` advertises this behavior. An older foundation server without the capability disables the hub quick-switch and retains ordinary browser selection. No alternate network protocol or persistence mechanism was added.

Character choices use authorized roster visibility, accepted controller/manager status and current server encounter state. A viewable character assigned to another controller is disabled with an explanation for ordinary players. Managers retain their existing selection permission. Names, approval and bound/unbound status are displayed; UUIDs/revisions remain internal. Selection sends only `CHARACTER_SELECT`, never BIND/UNBIND, approval, HP or resource updates.

The hub distinguishes **Selected** from the fallback **Bound** character. A bound character from another selected campaign is labelled accordingly. It does not silently rebind or reinterpret that character. Full graphical assignments/bindings remain the later campaign/character checkpoint.

Role/combat state is fetched before loading selector choices. DM Tools revalidates role again before opening. A role or encounter can still change after readback; the server always checks mutation-time authority. On rejection/conflict/timeout, old selection choices are disabled until explicit refresh; there is no optimistic selection or automatic mutation retry.

## Request and screen lifecycle

All new server requests flow through `ClientUiApi.request()` and its documented operations. The shared UI pump keeps at most 16 queued requests and one in flight, with at least eight client ticks between sends. This normally emits at most three new sends in any 20-client-tick window. The existing server limit remains four per 20 server ticks. If server/client timing differs and RATE_LIMIT occurs, the pump backs off 25 client ticks and displays the error; it does not bypass the server limit.

Queued arguments are copied. Closed/resized/replaced screens invalidate their callbacks and queued work. A connection-session generation also prevents an old reply from releasing a new session's request. Unsupported capability yields a readable local-tools state. UI errors and lost replies do not overwrite unsaved legacy sheet edits. Leaving a screen cannot cancel a mutation already accepted by the server; returning to the hub reloads authoritative state. User-facing timeout recovery explicitly requires refresh before another selection.

## Foreground blur fix

Static inspection of the pinned Minecraft 1.21.1 mapped bytecode confirmed:

`Screen.render()` → `renderBackground()` → `applyBlur()` before rendering widgets.

`SrdCharacterFormScreen`, `ServerCharactersScreen` and `SrdCombatScreen` drew their custom foreground labels/panels **before** calling `super.render()`, so that later inherited background pass blurred/dimmed already drawn foreground content. Each now overrides `renderBackground` with a no-op while retaining its own panel fill. The manual sheet already had that override. New RPG screens explicitly draw their own background once, then foreground/widgets, with no default post-foreground blur and no text matrix scaling.

This is a confirmed rendering-order correction. Actual visual sharpness, font appearance and GPU behavior still require graphical acceptance; they were not observed in a running client here. The full legacy SRD form navigation/layout redesign is not part of this checkpoint.

## Verification

Baseline command before editing:

`/workspace/cubetable-setup/gradle.sh build runGametest`

Result: **BUILD SUCCESSFUL**, **1,037 assertions**, **92 Minecraft GameTests**.

Final command after implementation and selector lifecycle review:

`/workspace/cubetable-setup/gradle.sh build runGametest`

Result: **BUILD SUCCESSFUL**, compilation succeeded, **1,155 assertions**, **95 required Minecraft GameTests**, **zero failures/errors/skipped tests**. The GameTest XML independently confirms 95 cases and no failed/skipped entries. All original tests remain enabled. Pinned Java 21/Minecraft 1.21.1/Fabric/Gradle dependencies are unchanged.

New pure UI group: **118 assertions** in `HubUiTests`, covering unsupported/loading/error state, server-derived manager permissions, stale unrelated readbacks, selected versus bound identity, cross-campaign binding disclosure, roster authorization/control, combat locks, local search, six-card layout/nonoverlap at 427×240/640×360/854×480/1280×720, small-layout scrolling fallback, pause placement, argument isolation, one-flight pacing, late/closed-screen callbacks, timeout nonretry, session invalidation, rate backoff and bounded backlog.

Three new embedded Minecraft GameTests in `HubGatewayGameTests` prove:

1. Ordinary members cannot submit a forged DM quick-switch; ordinary campaign navigation and co-DM quick-switch work; persistent character/binding state is unchanged.
2. A revoked co-DM cannot use stale hub permission and loses manager/private role fields on refresh.
3. Campaign and character selection reject during active SRD encounters, without changing turn, action use, persistent resources or binding.

These tests do not launch Minecraft graphical screens or simulate a real two-client session. Pause-menu hook construction, actual click/focus/narrator/Back interactions and render clipping are compiled and statically inspected, with pure placement/lifecycle tests; their graphical runtime acceptance remains **Not Verified**. No display is configured (`DISPLAY` unset), and neither Xvfb nor xvfb-run is installed. A graphical client was not launched. No remote multiplayer testing was performed. No automated-suite regression was observed.

Release artifact: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `1778f1d2db56f7f4e7eacfb5d310c51db1e3b426d7628bc11c68aa514ad9f824`.

## Real-client visual and multiplayer checklist

Use a backed-up disposable world and this exact JAR on Minecraft 1.21.1/Fabric Loader 0.16.9/Fabric API 0.116.1+1.21.1, with Java 21. Use two actual clients/accounts: A operator/campaign DM, B ordinary member. Record commit/JAR hash, resolution/GUI scale, client/server logs, expected/actual outcomes and Pass/Fail/Not Tested for each case. The earlier Phase 1–3 acceptance gates remain open.

1. Open Escape: check Cubetable placement beside/below existing controls at several resolutions and GUI scales. Verify Resume, Options, Disconnect and other vanilla controls behave normally. Reopen/resize repeatedly: no duplicate button. Check another menu-mod layout where available; a fully occupied layout must remain unobscured.
2. Open Cubetable: verify charcoal/gold panels, all six destinations, selectors, Help/Back, loading text and tooltips. Test 854×480 with approximately 427×240 logical size and a narrower/shorter GUI: scrolling must reach all controls. Check mouse wheel, Tab/Shift-Tab, arrows/Page Up/Page Down, Enter/Space, narration and search focus.
3. Back/Escape returns to the same pause menu; Resume returns to gameplay. Navigate Hub→Help→Back; Hub→Tabletop→local sheet→Back; Hub→Encounters→SRD→Back. Tactical battlefield uses its existing Escape→pause behavior. Disconnect with a request pending and reconnect: no stale callback or orphan screen.
4. In a server setup fixture, A creates campaigns with existing `/tbc campaign create <name>` and invites B using `/tbc campaign invite B`. A may grant a co-DM role with `/tbc campaign dm B` for the role test. Use the existing server character workspace to prepare approved/bound SRD or Tactical characters; full hub management is not implemented yet.
5. As ordinary B, DM Tools and hub campaign quick-switch are disabled with readable reasons. Campaigns still allows permitted membership selection. As A/co-DM, quick-switch shows managed campaign names/rulesets; acknowledge and return to hub. Revoke the co-DM role while B's hub is open: attempting DM Tools/quick-switch must recheck/deny rather than retain authority.
6. Character dropdown shows only authorized choices/status. Pick an unbound draft and an approved bound character; verify selected status and unchanged entity binding/HP/ammunition. Characters assigned to someone else must explain control requirements. A bound character from another campaign must be labelled rather than rebound.
7. Start an existing controlled encounter and open the hub. Selection locks must show; role/action/turn/resource state stays intact. Change encounter state between list readback and selection: server rejection must appear. End the encounter, Refresh, then select normally.
8. Exercise local search, eight-record pages, empty/no-match pages, long names/tooltips and role changes. Add latency or interrupt replies: loading should resolve to success/error/timeout; stale choices should require Refresh and never silently retry or rebind. Repeated Refresh/reopen must not create an unbounded backlog.
9. Connect to a server without UI v1: pause entry/local tools remain available, server selectors/DM controls stay disabled, and destinations explain missing support. Against an older foundation server without hubQuickSwitch, ordinary campaign selection works while the hub shortcut is unavailable.
10. Reopen the legacy SRD form/server workspace/SRD combat panel and inspect foreground text before/after this build. Confirm blur no longer passes over drawn labels. Test font clarity, GUI scales, clipping, field focus and unsaved staged edits across resize/errors.
11. Recheck `/sheet`, `/srdcombat`, `/tbc`, Generic D20/Percentile packs, local sheet resizing, dice/card display, Tactical camera/fading/grid/HUD, SRD movement/attack confirmation and existing inventory protections. Minecraft Options must work and return correctly.

## Next checkpoint

Proceed to graphical Campaigns/My Characters after visual acceptance: campaign creation/invitations/settings/roster management, independent library lifecycle and explicit submission/assignment/binding flows, with dirty-state reconciliation. Then add the grouped wizard and detailed dashboard. Keep the current event/dice/HUD and excluded gameplay work deferred to their planned checkpoints. Preserve the foundation's explicit bound-edit and live-takeover limitations.

## Changed files
- `src/gametest/java/dev/tacticalcombat/combat/HubGatewayGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java`
- `src/main/java/dev/tacticalcombat/client/ServerCharactersScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdCharacterFormScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdCombatScreen.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/client/ui/BrowseScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DestinationScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/HubScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/HubState.java`
- `src/main/java/dev/tacticalcombat/client/ui/InfoScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/PauseMenuEntry.java`
- `src/main/java/dev/tacticalcombat/client/ui/RpgButton.java`
- `src/main/java/dev/tacticalcombat/client/ui/RpgConfirmationScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/RpgList.java`
- `src/main/java/dev/tacticalcombat/client/ui/RpgScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/RpgTextBox.java`
- `src/main/java/dev/tacticalcombat/client/ui/RpgTheme.java`
- `src/main/java/dev/tacticalcombat/client/ui/UiLayout.java`
- `src/main/java/dev/tacticalcombat/client/ui/UiRequestPump.java`
- `src/main/java/dev/tacticalcombat/client/ui/UiRequestQueue.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/ui/HubUiTests.java`
- `docs/ui-overhaul-sol-checkpoint-1.md`
- `docs/ui-overhaul-sol-handoff.md` (additive quick-switch contract)
