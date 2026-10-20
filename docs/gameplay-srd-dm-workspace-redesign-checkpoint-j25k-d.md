# J2.5K-D — Compact, bound-first DM Workspace

- Resolved and verified remote K-C base: `27ac3935c1a63d555ce77d6fc67eb92ff1053a0f`.
- K-B ancestor verified: `9f20b5e27c7b9514fecb1e03c53735ce793ad56b`.
- D branch: `dev/gameplay-srd-dm-workspace-redesign-j25k-d`.
- D implementation commit: `2094c295445d9e73060f498ad2fcd4c3e88fc9cf`.
- This report is a documentation-only child of the tested implementation. The final delivery response and local `build/j25kd-delivery-receipt.txt` record the exact verified remote delivery HEAD.

K-C's report and successful 42,944-assertion / 181-GameTest validation were present before work began. The checkout was clean. D did not branch from main or K-B.

## Five-tab behavior

**Party — Campaign Characters.** Campaign records use compact two-line rows with character/type, authorized health/AC, approval/binding and controller/condition information in tooltips. Selection loads the authorized inline overview. Full Sheet, checks, approval and advanced roster/assignment/controller management remain available through existing screens and APIs. Party remains paged independently of nearby entity discovery.

**Actors — Nearby Actors / Prepare.** Actors, Encounters and Scene each default to server-backed `BOUND` discovery on first open or new campaign. Bound / Unbound / All and Player / NPC changes issue fresh `ENCOUNTER_DISCOVER` reads using those actual parameters. Search, readiness and sort operate on the resulting authorized snapshot. Filter settings, query and scroll are retained per tab within the campaign. The dense roster separates row inspection from the right-hand participant-selection marker. Selecting a row expands only that actor's contextual details, including server-provided blocked reason, world type/position, bound character and supported monster limitations. Bind Character and Prepare Creature open the established explicit workflows for supported unbound NPCs. Selecting a row never binds, prepares, moves or starts anything.

**Encounters — Combat Management.** Watched combat has a compact state/round/current-actor summary, authorized resources and HP, and initiative rows consumed in the server's actual tie-resolved order. Existing encounters appear as selectable compact rows; Watch and explicitly confirmed End apply to the selected encounter. A separate Start New Encounter section shows selected names/count, current readiness and a dense participant picker. Start requires 2–16 eligible selected actors in the current discovery, then explicit confirmation. Prepare Creature cross-navigation leads to Actors. Return to Battlefield preserves the existing camera/turn/ownership contract.

**Scene — Battlefield Setup.** A bounded north-up map renders positions only from the current filtered server discovery. It defaults to bound actors; deliberate Unbound/All reads remain available. Numbered markers have hover identity/reason, selected participants use gold, and clicking a marker selects the corresponding roster actor. Off-area/nonfinite markers are omitted. Map clicks never place actors. Stage templates are compact selectable rows; only the selected stage exposes slots and Capture / Validate / Edit / Delete actions. Capture saves current placements, Validate obtains the existing server preview token, and separately confirmed Activate moves actors under existing server validation. Starting combat remains separate. Linked participant order is preserved through search/sort/filter and used directly for stage-slot correspondence.

**Rolls — Checks & Dice.** Distinct Request Player Check, DM Character Roll and Manual Dice controls select workflow-specific forms. Category/check choices use `CharacterChecks.keys()`. Request targets include only the authorized online-bound controller choices; adjudication uses approved eligible characters. Targets have a bounded scrollable list, with search when the list exceeds eight entries. Audience/recipients and hidden DC live in a collapsible section whose current audience/DC-set status stays visible. A request requires deliberate confirmation. DM preparation and Roll / Retrieve remain separate; the retained operation has its original category/check/mode/audience summary even after form edits. Forget Local Reference affects only client navigation state, never cancellation, execution or reroll. Executing another pending request does not discard a separate prepared DM operation.

Manual dice retain their own modifier, quantity, die and D20 mode through the existing `DiceRequestPayload` route and nearby/encounter audience. They are explicitly distinct from authenticated character checks. Manual mode no longer changes the character-check mode. Accepted results show only supplied server mode/faces/kept indices mapped to their supplied faces, validated modifier total, final total and authorized DC/outcome. No client chooses a winning die or derives a hidden result. Inbox rows have a bounded independent scroll area, page navigation and selected-result detail. Pending execution and terminal dismissal are explicit; refresh/reopen/rebuild never prepares, executes or replays a roll.

## Density and layout

| Area | K-C layout | D layout |
|---|---|---|
| Party record | 82px panel + gap + separate 26px inspect control | 28px two-line selectable row |
| Nearby actor | 26px action plus wrapped explanatory lines per actor | 28px roster row; details/actions only for selected actor |
| Saved stage | Heading, coordinates, slot list and all actions for every template | 28px selectable template rows; slots/actions only for selected template |
| Header reservation | 102px on every tab | Party 69px; nearby tabs 89px; Rolls 49px |
| Target selection | Potentially 128 separate full-width controls | At most three 34px visible list rows with internal scrolling |
| Inbox page | Full details for every result | At most four 34px visible rows with selected-result expansion |

Compact/expanded frames use up to 700×500 and 920×620 logical pixels, clamped to the viewport. Narrow rosters prioritize name/secondary status and hide the optional distance/status column; full details remain available in tooltips and contextual inspection. Command groups use two columns on narrow layouts and up to three on wider layouts. Long text is trimmed or wrapped, and scrollable content stays above the persistent Back/Battlefield footer. The translucent stone/charcoal panel and restrained gold reuse project components and original glyphs; no external assets were introduced.

Deterministic tests cover representative logical viewports from 320×180 to 1920×1080, all tabs, both frame modes, column separation, footer bounds, map bounds and selection limits. These are layout calculations, not graphical Minecraft GUI-scale acceptance.

## Discovery, request and permission safety

`DmWorkspaceModel.DiscoveryQuery` captures campaign, binding, kind and generation. Changing a server filter immediately invalidates the old executable discovery token. Late mismatched replies cannot replace current data. Start payloads use only the current token and stable actor UUIDs; Start is disabled unless every selected actor is eligible in that snapshot. Server token expiry, campaign revision, safe placement, binding and final admission checks remain unchanged.

Selected participant intentions can survive browsing a filtered-out domain (for example bound player/Husk → Unbound bats). They cannot execute from that unrelated snapshot. Returning to Bound/All obtains a new token and revalidates the selection. Ineligible or missing actors in a complete matching scope are removed with an explanation. Absence from a truncated snapshot is not falsely called removal; intent is retained but Start stays disabled until the actor is present and verified. Sorting never reorders the selected LinkedHashSet used by scene slots.

Two minimum response-adapter changes close actual display-scope gaps:

1. Manager encounter and stage replies now include their authenticated `campaign` ID, so a server-side campaign switch between the root context read and a subordinate read is detectable.
2. Same-campaign discovery rows include authorized `character` and `characterRevision` metadata. If a selected world entity is rebound or its approved record changes, D clears that selection for deliberate review even when the same entity UUID is still eligible. No IDs from another campaign or complete sheets were added.

No server permission, gameplay, transaction, persistence or admission rules changed. New metadata is additive for existing clients. D expects the matching D server's campaign-stamped discovery; a missing/mismatched stamp fails closed with Refresh rather than publishing an unscoped roster.

`WorkspaceMutationGate` allows one explicit operation at a time. Layout rebuild/resize does not destroy its acceptance identity. Campaign/access changes and screen removal invalidate it, and an old reply cannot release a newer operation. Mutation callbacks do not automatically retry. Read requests retain existing screen-generation/pump pacing and watch invalidation. Transient subordinate read failures preserve valid campaign context while disabling executable controls; permission revocation clears private data, selections, forms, stage/check references and tokens. World changes invalidate nearby snapshots. Read-only inbox polling pauses while form text is being edited or a confirmation/mutation is pending.

The title drag, dropdown dismissal and CharacterCanvas clipping retain the existing input model. Root popup clicks are consumed before underlying rows, collapsed controls are removed from the rebuilt tree, and no world click-through was added. Workspace remains a safe Minecraft Screen: world interaction resumes through Battlefield/Back rather than through the panel.

## K-C recovery and prior gameplay

Paused managers retain direct access to K-C's recovery screen. Saved-attack failures are labeled Saved Attack Review; other permitted paused adjudication uses Heal / Adjudicate. The server's reason remains visible. K-C continues to enforce positive bounded healing, encounter/revision/manager/selected-campaign checks, single explicit mutation dispatch, committed HP persistence and no automatic combat resumption. Transaction review and explicit End remain available. No healing logic, durable receipts or dice were rewritten.

All K-A/K-B movement/attack/camera/fading/hotbar, Dash/Dodge/Prone, D1 privacy, D2 stages/prepared monsters, cinematic dice, Tactical, Generic D20, character/library persistence and catalog tests remain enabled.

## Validation and evidence

Final command (existing Java 21 toolchain and verified proxy/trust configuration):

```bash
JAVA_HOME=/workspace/.tools/jdk21 GRADLE_USER_HOME=/workspace/.gradle \
JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

- **BUILD SUCCESSFUL in 1m 22s**.
- **45,775 foundation assertions across 48 suites**; D adds 2,831 deterministic assertions.
- **183/183 GameTests**, zero failures/errors/skips; D adds two integration tests.
- **2,748 catalog checks + 15,115 coverage checks = 17,863**.
- JAR: `build/libs/tactical-combat-0.1.0.jar`.
- SHA-256: `74cbac361f010662cc0541ae58daab0e610651ced9d89cfaffa97c4f3c13d3b8`.
- Local evidence: `build/j25kd-delivery-validation.log`, `build/j25kd-full-validation.log`, `build/j25kd-targeted.log`, `build/gametest-results.xml`.

The production-boundary integration test prepares a real Minecraft Husk from the supported Zombie source, binds a real mock player and creates incidental unbound bats. It verifies Bound excludes bats, Unbound/All use new server snapshots, old tokens cannot start, returning Bound revalidates retained selections, one manager controls both actors, watch/end works, rebind requires new selection and revoked discovery is denied. The second test switches actual server campaign between the captured client query and read, proving the reply stamp prevents cross-scope publication. All K-C check/recovery/storage-fault tests pass unchanged. One initial new test fixture used `entity` instead of the production monster-preparation field `actor`; the fixture was corrected, without weakening service validation or existing assertions.

Existing mock-player/API and Gradle deprecation warnings remain. Optional public-key service DNS failure does not invalidate the completed GameTests. K-C's diagnostics test deliberately emits one redacted test fault; it is not a runtime checkpoint failure.

## Remaining limitations and Minecraft checklist

**J2.5K-D GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.** No graphical display or Xvfb is available. Actual Minecraft mouse feel, visual density, GUI scales, renderer appearance and K-A/K-B walking/camera acceptance remain pending.

Discovery/map rows are bounded point-in-time server snapshots, not a new live radar. Refresh after actors move or bindings change; final server admission always rejects stale radius, record, reservation, token or placement state. Counts describe returned rows, not the entire world. A filtered/truncated-away selected participant remains an intention, never an executable admission. Expired check records follow the existing server inbox semantics and may disappear; D does not invent expiry statuses or reroll them. Advanced binding, monster review, complete sheets, roster/controller administration, stage editing, transaction recovery and grouped check tools intentionally retain their established screens. No new rules, persistent schemas, world editor, AI or background world interaction were added. The primary world was not installed into or modified.

Later, in a backed-up disposable test world:

1. Actors opens Bound-first: prepared player/Husk appear before incidental bats. Deliberately switch Unbound/All, inspect and prepare a creature, then return Bound.
2. Select player/Husk with the right-hand roster markers. Check count/readiness, then confirm Start in Encounters. Cancel once to verify no mutation.
3. Inspect current actor, actual initiative order and authorized HP. Return to Battlefield without changing turn or camera; verify one manager can still control both actors.
4. At a zero-HP pause, open Heal / Adjudicate, cancel then confirm healing. Combat stays paused; explicit End retains HP. Never corrupt a save to test storage failures.
5. Scene: select markers/rows, verify slot order, save/capture a template, Validate, then explicitly Activate. No combat starts until separately requested.
6. Rolls: request a player check, execute once through the existing player inbox, inspect privacy and result, refresh without rerolling. Prepare/retrieve a DM Character Roll. Test Manual Dice separately.
7. Resize while preparing a DM roll; verify one retained operation and no automatic execution. Test form edits, dropdowns, scroll, title dragging and tab navigation.
8. Test long names/many actors and compact/expanded windows at GUI scales 2, 3 and 4. Confirm no clipped mandatory controls or world click-through.
9. Switch campaign during discovery/Rolls loading and revoke manager permission. No old private data or executable selection should revive; ordinary members cannot use DM operations.
10. Re-run ordinary split movement, Dash, Dodge, Prone/Stand and contextual attacks; verify ending restores camera/hotbar/fading as before.

## Changed files

New: `WorkspaceRosterRow.java`, `WorkspaceSceneMap.java`, `WorkspaceMutationGate.java` under `src/main/java/dev/tacticalcombat/client/ui/`; `DmWorkspaceRedesignTests.java` under the matching foundation-test package; `DmWorkspaceRedesignGameTests.java` under `src/gametest/java/dev/tacticalcombat/combat/`; and this report.

Updated: `DmWorkspaceScreen.java`, `DmWorkspaceModel.java`, `EncounterLauncher.java`, `EncounterStages.java`, `FoundationTests.java`, and `src/gametest/resources/fabric.mod.json`.
