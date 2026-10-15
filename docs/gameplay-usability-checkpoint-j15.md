# Gameplay usability checkpoint J1.5

## Delivery identity

- Approved J1 starting commit: `5c9f1186c75d9a6ec89b26c95d00af5218098614`.
- Final implementation commit: `344fd15f488ec48ecbf0e6e394f3f242211635d5`.
- Branch: `dev/gameplay-usability-checkpoint-j15` in `shayanzas/Cubetable`.
- The following report-only commit is the delivery tip. Resolve its exact hash with `git log -1 --format=%H -- docs/gameplay-usability-checkpoint-j15.md`; the report does not embed its own self-referential hash.
- No merge, force-push or upstream write is part of this checkpoint.

## Implemented behavior

### Graphical encounter launcher

Open **Cubetable → Encounters → New Encounter**. A server-authorized discovery lists up to 64 loaded living actors within 32 blocks in the DM's dimension. Rows show entity name, player/NPC classification, associated character name where authorized, bound/approval state, eligibility and rejection reason. Unbound Minecraft mobs receive no generated character or statistics. Refresh explicitly rebuilds discovery.

Select 2–16 eligible actors and press **Start Encounter**. The server receives actor identities and a short-lived discovery token, never calculated statistics or trusted eligibility flags. A successful start opens the existing SRD interface. It initially shows initiative/status for an active encounter or the existing tie controls for a pending encounter. Tie submission has an explanatory tooltip and an appended-actor count. Pending initiative is a successful encounter start, not a false failure.

The trusted start sequence is shared with the retained `test-public-ffa` diagnostic command: existing `SrdEncounter.start`, `begin`, participant/DM watches and existing cleanup on failure. Admission checking was extracted into a side-effect-free helper used by discovery and start; its conditions and combat calculations are unchanged. Other participating compatible clients receive authorized SRD state through the existing subscription system. They can open it from Encounters or Quick Sheet combat controls without commands.

**View Active Encounters** lists the selected campaign's encounters for managers. If the Encounters destination already has a watched encounter, it opens that combat interface directly, including for an authorized ordinary participant. DM Quick Tools provides the manager list. Watching an encounter uses the existing permission-filtered SRD pathway. **End Encounter** remains visible with confirmation in existing Movement / DM controls; DM Quick Tools also offers **End Watched Encounter…** when an encounter is watched. Ending uses the existing stop/cleanup behavior and retains persistent HP and ammunition.

### Character Quick Sheet

Default **J** opens a non-pausing, right-side dark-and-gold panel over the rendered world. It temporarily captures mouse input; Escape or the configured key closes it. From the SRD interface it returns to that interface; from gameplay it returns directly to gameplay. It scrolls within a bounded compact viewport and supports long wrapped content.

The server resolves the character actually bound to the player, independently of the locally selected character. The panel displays identity, effective combat level and informational recorded level, HP/max/temp HP, AC, speed, initiative, proficiency, six abilities/modifiers, typed supported weapon availability and virtual ammunition. J1's authorized projection supplies these supported values. Missing compatible bindings produce an explanatory empty state. No new attack, inventory, class, spell or progression actions are implied.

The panel links to the full Character Sheet, Cubetable, and watched combat controls where available. The layout remains a simple scrollable body suitable for later supported sections; no empty/fake mechanic tabs were added.

### DM Quick Tools

The configurable DM key starts **unbound** to avoid consuming another control. Assign it under **Options → Controls → Key Binds → Tactical Combat → DM Quick Tools**. The same panel is available from **Cubetable → DM Tools → DM Quick Tools**.

After current server manager authorization, it shows eight campaign characters per page, including bound NPCs, with approval/binding state, loaded player/NPC classification, available authorized HP and encounter participation. Unloaded bindings are labeled as such rather than guessed to be players or NPCs. Selecting Inspect opens a compact authorized character view with a full-sheet shortcut. Initiative presentation reuses the existing `CubetableHud.MODEL` for the matching campaign; detailed/current initiative remains available in the existing combat interface.

Shortcuts open New Encounter, active encounters/combat controls, roster/approvals/bindings, existing manual rest requests, the existing dice/event log and full Cubetable menus. NPC actions remain in the current DM-authorized combat controls. Ordinary players receive no DM roster, private inspection, launcher or ending authority by opening a panel.

### Small cleanup

Library cards and character management headings display a supported redundant identity such as `Dwarf — Hill Dwarf` as `Hill Dwarf`. Stored identity and raw/search metadata remain unchanged. Explicit Base Traits, unusual/custom labels and mismatched parent/subrace names are preserved. Single-page My Characters lists hide the unnecessary Previous/Next controls; later pages retain Previous and real multi-page lists retain Next. Encounters now leads with graphical interaction instead of command instructions.

## Controls and HUD coexistence

| Control | Default | Behavior |
| --- | --- | --- |
| Character Quick Sheet | J | Opens in gameplay or from SRD controls; toggles closed |
| DM Quick Tools | Unbound | Assign a preferred key; opens authorized compact tools |
| Local manual sheets | K, unchanged | Existing independent local sheets |
| Event log | L, unchanged | Existing event display |
| Skip dice / dismiss card | O / H, unchanged | Existing HUD functions |
| End Tactical turn | Enter, unchanged | Existing Tactical control |

No existing key was silently reassigned. Other mods may use J; Minecraft Controls can rebind it. Quick panels can switch between each other using their configured keys. They do not intercept typing in unrelated full editors. Passive HUD, interactive compact panels and full management screens remain separate presentations; no second HUD polling subsystem was added.

## Authority, stale state and cleanup

- Every world operation runs through the existing authenticated, server-thread UI gateway and request pacing/replay checks. The core bridge also checks server thread and current selected-campaign manager authority.
- Discovery is capped and scoped to the current campaign/world. Foreign campaign character details are not disclosed.
- Each player retains at most one discovery token, expiring after 1,200 server ticks. Start consumes it, including failed attempts; Refresh is required afterward. Selected actor snapshots and campaign revision must still match. Repeated requests cannot recreate an encounter after it ends using the same token.
- Start rechecks distinctness, count, loaded/living state, radius/world, bindings, approval, SRD profile, controller/campaign relationships, client capability, existing encounter ownership and movement/footprint restrictions through the existing admission path.
- All admission validation precedes claiming actors. Failure after claims during begin/watch invokes existing end/cleanup. Tests verify claims are released and the mob's prior AI state is restored. There is no teleportation or positioning workaround.
- Compact reads and open launcher views register one opaque panel token per player. The server checks current authorization and notifies on repository or encounter revisions. Notices reuse J1's token/revocation payload and contain no character data. Disconnect, closure and shutdown clear subscriptions.
- Revoked access clears displayed private controls/data; late callbacks are invalidated. Role and binding changes are rechecked server-side. Refresh remains an explicit fallback. Private full sheets are never sent through discovery or roster summaries.
- Panel invalidation may refresh on an unrelated repository commit because the store generation is deliberately conservative. It does not continuously poll or reconstruct private sheets on the client. Existing HUD sampling remains unchanged.
- Schemas, character repositories, J1 projection formulas, approved records, G creation protections and H workflows are unchanged. No migration, transaction rewrite or new D&D mechanic is introduced.

## Automated verification

Java 21.0.12.1, existing Fabric/Minecraft 1.21.1 and Gradle configuration.

- `bash ./gradlew --no-daemon --max-workers=4 build runGametest`: successful, latest full run 1m 19s.
- Final `bash ./gradlew --no-daemon --max-workers=4 build`: successful, 36s, after the last client-only initiative/tie presentation adjustment. Server/GameTest code was unchanged by that final adjustment.
- **14,029 assertions across 33 foundation suites**: J1's 14,001 retained plus 28 usability checks.
- **128 GameTests, zero failures/errors** in the current XML: J1's 122 retained plus six launcher/panel tests.
- New coverage includes player+Zombie discovery/start, subscriptions, duplicate retry, unbound/unapproved actors, stale snapshots/unloaded entities, cross-campaign/world restrictions, footprint rejection without movement, existing encounter ownership, post-claim failure cleanup, DM-only ending, resource preservation, bound-versus-selected Quick Sheet, private inspection, revoked DM access and opaque panel revocation.
- Existing initiative-tie, movement/action, authorization, HUD, J1 parity, G/H creation and management tests remain enabled and pass. No prior test expectation was weakened or deleted.
- Foundation usability tests cover known subrace label simplification while preserving custom/Base Traits labels, pagination and compact bounds across six GUI sizes.
- `git diff --check` passes. Existing deprecated test/mock-player/Gradle warnings and the headless server's Minecraft public-key lookup warning do not prevent successful execution.

Graphical Minecraft was not run. Mouse/focus feel, rendered visual fit, screenshots and actual saved-world acceptance remain pending. Automated tests do not establish visual approval.

## Exact Minecraft acceptance scenario

Use a test copy of the existing world with the player and Zombie already approved and bound to separate SRD characters in the same campaign. Install the new main mod JAR on both client and server, replacing the previous JAR, and restart. Retain Minecraft 1.21.1, Java 21 and the existing compatible Fabric dependencies.

1. Select the correct campaign and ensure the testing player has current campaign-manager authority.
2. Open **Cubetable → Encounters → New Encounter**.
3. Find the player and Zombie within the displayed 32-block radius. Both should show approved, bound and eligible. Refresh if they moved or their character changed.
4. If rejected for a supported centered footprint, position the actors appropriately using the existing supported world setup. For ordinary one-block footprints this requires centered X/Z placement and valid standing terrain. The launcher deliberately does not move actors, expand movement support or infer monster stats. Flying, mounted, swimming and other unsupported states remain subject to existing checks.
5. Select both and click **Start Encounter** once. No entity UUID entry or command is needed. Verify both appear in initiative and the active actor is identified.
6. If initiative ties, use the displayed Movement / DM tie controls: cycle to a tied actor, append each actor from that group in the desired order, then Submit. Mixed player/NPC ties require the DM. Player-only ties require matching choices from all tied players. The Status / initiative page shows the totals.
7. Use the existing page switch to reach Attack controls or Movement / DM. Test an existing supported attack or move; confirm the authoritative result and resources.
8. Press **J** for the player's Quick Sheet. Verify it shows the bound player character even if another character was locally selected. Compare HP/temp HP, AC, speed and initiative with the full sheet/encounter. Escape or J closes it.
9. Assign a **DM Quick Tools** key in Minecraft Controls and open it, or use the DM Tools menu shortcut. Inspect the player and Zombie, open a compact inspection, then its full sheet. Check the existing rest/event shortcuts.
10. View/watch the active encounter, then use **End Watched Encounter…** in DM Tools or **End encounter** under Movement / DM. Confirm the dialog.
11. Verify actor claims and mob AI recover, while persistent D&D HP and virtual ammunition remain at their post-combat values.
12. Test an ordinary player's own Quick Sheet and denial of DM tools/private NPC inspection/ending. Revoke a manager while their panel is open and confirm it clears.
13. Repeat with multiple online players where possible: participating clients receive state and can open their existing combat interface; player-only initiative ties still require agreement.
14. Check long names and smaller/larger GUI scales, scrolling, Escape/toggle closure, return to gameplay, disconnect/reconnect and failed/unsupported server states. Confirm passive HUD and K local sheets still work.
15. Check My Characters with one page and multiple pages, the Hill Dwarf label, Base Traits/custom labels, creator completion guards and campaign management.

## Known limits

- Controlled combat remains the existing public-position free-for-all: all selected participants are hostile. No team/faction editor or automatic monster generation was added.
- Strict pre-existing placement/admission restrictions can prevent a moving or off-center Zombie from joining. This checkpoint reports the restriction and does not silently reposition it.
- Discovery lists only nearby loaded actors; larger groups may require moving closer and refreshing. The DM roster includes unloaded records but cannot make unloaded entities combat participants.
- Only the initiating DM's combat screen opens automatically. Other participants receive state and can open controls via Encounters/Quick Sheet.
- Richer DM action editors are deferred. Complex approval/binding/rest operations intentionally open existing full screens.
- The DM key has no default assignment. Quick-panel visuals and actual multiplayer interaction await user testing/screenshots.

## Build artifact

Main JAR: `build/libs/tactical-combat-0.1.0.jar`.

SHA-256: `6171c6bb0478eef945b5cde380c8bb4897a8dc4aebeac99596ac2bdb4061d312`.

## Exact changed files

- `src/gametest/java/dev/tacticalcombat/combat/EncounterLauncherGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/ClientRulesWatch.java`
- `src/main/java/dev/tacticalcombat/client/SrdCombatScreen.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/client/ui/DestinationScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/EncounterLauncherScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryCharacterScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/LibraryScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/PlayerUiModel.java`
- `src/main/java/dev/tacticalcombat/client/ui/QuickPanelLayout.java`
- `src/main/java/dev/tacticalcombat/client/ui/QuickPanelScreen.java`
- `src/main/java/dev/tacticalcombat/combat/EncounterLauncher.java`
- `src/main/java/dev/tacticalcombat/combat/SrdCommands.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/main/resources/assets/tacticalcombat/lang/en_us.json`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/ui/CheckpointJ15Tests.java`
- `docs/gameplay-usability-checkpoint-j15.md` (this delivery report).
