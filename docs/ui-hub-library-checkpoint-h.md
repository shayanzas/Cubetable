# Checkpoint H — Hub, library and character management

## Delivery

- Repository: `shayanzas/Cubetable`; branch: `dev/ui-hub-library-checkpoint-h`.
- Approved G base, fetched and verified before branching: `af38475cb9b7033e379d0dd979d3a79c7982ff29`.
- Final tested implementation commit: `613759b0a600993b2d09fc85aafc1fe1878a298a`. The subsequent documentation-only commit is the delivery tip; obtain its exact hash with `git log -1 --format=%H -- docs/ui-hub-library-checkpoint-h.md` on this branch. The delivery response also records that hash.
- Logical commits: `63e17df` bounded owner summaries; `43ab0fe` original hub artwork; `b56dcf6` screens/navigation; `613759b` regression coverage.
- Push only this branch to the user's repository. No merge or force push.
- No change to combat mechanics, G creator/reset code, G library repository, schema, approved creator artwork, build pins or campaign management implementation.

## Implemented screens

**Main Hub:** separate Your Table overview, campaign/character selectors, Refresh and Open Character Sheet; six illustrated destination tiles with native hover/focus, descriptions and disabled explanations. Labels distinguish selected, approved and bound characters and encounter locks. Selecting a character never binds it. Tiles use a two-column arrangement at normal widths and one column at narrow widths, in a clipped scrollable canvas. All six original destinations remain.

**My Characters:** top Create Character action, persistent search, real character metadata cards, textual Draft/Complete/Needs Attention status, ruleset and recorded level. Missing race/class have placeholders; default-named characters retain their individual metadata. Draft primary action continues the creator; completed supported characters open their sheet directly. Manage is secondary. Cards use two columns on wide canvases and one on smaller canvases. Pagination remains separate at the bottom. Search explicitly applies to the current server page. Empty, filtered-empty, loading and failed-read states provide guidance.

**Manage Character:** compact identity header and Sheet, Edit/Continue Creating, Duplicate and Submit actions; campaign copies have a separate section. Delete has a separate destructive panel and confirmation. Raw-input/source-inspection/authoring-scope entries no longer appear in normal player navigation. Existing backend capabilities and persisted data remain. Completed supported characters have a real creator route; manual profiles have a real name-only editor with its scope explained.

**Character Dashboard:** only the Manage route, availability and pending-currency explanation changed. Approved sheet presentation and creator presentation were not redesigned.

Navigation reuses matching parent Sheet/Manage screens by both scope and record ID, preserving the distinction between personal and campaign records. Delete returns to the library rather than a deleted record's dashboard. Existing creator pending-change protections, expected revisions and mutation acknowledgements remain.

## Artwork

Six original generated symbols: castle/banner, neutral heraldic character silhouette, crossed swords, crown, die and cog. No replacement race icons or third-party assets. No class artwork is used as a hub destination. Existing actual class icons identify characters on their cards.

`textures/gui/hub/navigation-h.png` retains the generated source unchanged. `tools/pack_hub_icons.py` copies six exact 512-square cells into 516-square PNGs with two-pixel transparent gutters. No redraw, filtering or resampling occurs during packaging. Nearest-neighbor texture metadata accompanies each sprite. The source includes translucent gold edge/halo pixels; these were preserved. Runtime display uses compact 24/32-pixel slots, with native Minecraft text.

## Visible action audit

| Action | Behavior and availability |
| --- | --- |
| Hub Refresh | Reloads authoritative capabilities, active state, role and character data; busy/unsupported reasons shown. |
| Campaign selector | Existing campaign browser and quick-switch permissions; no new player privilege. |
| Character selector | Existing controller checks and active-encounter locks; selection remains separate from binding. |
| Hub Open Sheet | Existing supported selected/bound SRD dashboard route. |
| Campaigns / My Characters | Existing server-backed destinations; unavailable/loading states disable with explanation. |
| Encounters | Existing encounter destination and tactical/SRD fallback behavior. |
| DM Tools | Requires authoritative DM role; rechecks campaign before opening roster. |
| Tabletop Tools / Settings | Existing local destinations, including server-unavailable fallback. |
| Create | Existing creation flow; owner limit 32, busy/unfresh reads disabled. Server still decides global capacity and concurrent eligibility. |
| Library search / Previous / Next | Page-local search; eight server records per page, authoritative has-more; first/last page explanations. |
| Card primary | Fresh record read before actual draft creator or supported completed dashboard route. Unsupported profiles explain unavailable sheet/creator support. |
| Manage / Refresh | Owner-backed record reads; failed, forbidden or missing records cannot leave stale actions enabled. |
| Open Character Sheet | Supported completed personal record only. |
| Edit / Continue Creating | Actual existing SRD wizard, or manual name-only editor; authoritative revision checks. |
| Duplicate | Existing confirmation and mutation; independent draft, ambiguity guard retained. |
| Submit to Campaign | Complete source required; existing campaign selection, durable idempotent submission and server permission validation. |
| Campaign copy | Opens campaign management with campaign permissions; never the personal editor. |
| Delete | Explicit confirmation and expected revision; removes only personal source, not independent copies or bindings. Cancel does not mutate. |
| Back | Relevant retained parent; matching ancestors prevent Sheet/Manage loops. Existing pending-change warnings remain. |

## Editing and data guarantees

Unfinished SRD drafts reopen their saved creator state. Completed personal characters can reopen the existing creator and perform supported edits. Saving an edit returns the source to Draft where the authoritative lifecycle requires it; finish again before submission. Persistent `creationClosed` survives edits and restart. First-time class reset remains prohibited for previously completed sources. Existing G transactional reset behavior is unchanged for eligible unfinished drafts.

Personal edits, duplication and deletion do not overwrite submitted campaign copies. Campaign dashboard management continues through the campaign route and its existing permissions. Validation, owner checks, revisions, controller restrictions, binding state and encounter locks remain server authoritative.

G **schema 2 is preserved unchanged**; H introduces no storage migration. Listing adds bounded owner-only summaries and capability flags, not schema fields. It does not expose full sheets, private narratives or invented timestamps. Existing campaign-copy membership filtering remains.

## Automated validation

Java 21; supported Gradle 8.10.2 / Loom 1.8.13 / Fabric configuration unchanged. Final invocation:

```sh
JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
PATH="$JAVA_HOME/bin:$PATH"
GRADLE_USER_HOME=/workspace/.gradle
export JAVA_HOME PATH GRADLE_USER_HOME
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

- **BUILD SUCCESSFUL**, final run 56 seconds.
- **7,216 assertions across 30 suites**, including **514 new H assertions**. All previous 6,702 assertions, G's 458 checks and existing combat regressions passed.
- **120 GameTests passed**, zero XML failures/errors; two new H GameTests in addition to the previous 118.
- Covers owner-only summaries, actual lifecycle status, draft/completed editing, persisted closed protection, independent duplicates/submissions, actual personal/campaign navigation objects, deletion/Cancel, stale/unauthorized mutations, DM gates, selected versus bound state, combat locks, unavailable server, native disabled tile execution and packed asset pixels.
- Production geometry checks at logical sizes 240×135, 320×180, 427×240, 480×270, 640×360, 854×480, 960×540 and 1280×720, including long names, wrapped metadata, tile/card spacing and scroll viewport/footer boundaries.
- `git diff --check` passed. Protected G files, combat/mixins, creator assets and build pins match the approved base.
- All 14 hub resource files match the built JAR; all six new UI helper classes are packaged.

**Graphical Minecraft verification was not performed:** this environment has no DISPLAY, WAYLAND_DISPLAY or Xvfb. Headless geometry, native widget/navigation tests and server GameTests do not establish in-game visual approval. Sol 6.1 Medium was requested; an in-session model switch/verification facility was unavailable, so no verified model-switch claim is made.

## Build artifact

- `build/libs/tactical-combat-0.1.0.jar` (22,328,013 bytes).
- SHA-256: `34c91204a00298f826c3ac7995b0658add5d501c42065da15595957cb747e6bd`.
- Replace the prior Cubetable JAR in a Minecraft 1.21.1 / Java 21 Fabric installation; keep matching Fabric API. For multiplayer, install this build on both client and server, preserving existing world/config data. Remove the prior duplicate mod JAR, launch, and use the existing Cubetable opening control.

## Minecraft acceptance checklist

1. Open Hub with no selections: clear empty states, selectors/Refresh, separated overview, readable text.
2. Select an eligible campaign: correct name/role; Refresh retains authoritative state.
3. Compare selected unbound, bound-to-self and bound-to-other characters; verify approval and binding labels. Selection must not bind. Test encounter selection lock/controller denial.
4. Open each of Campaigns, My Characters, Encounters, DM Tools (authorized), Tabletop Tools and Settings; inspect icons, hover, keyboard focus and Back.
5. As non-DM, verify DM Tools is disabled with explanation; as DM, verify authorized roster opens. Test server-unavailable explanations and local fallback destinations.
6. Open an empty library, create a character, then test small and multiple full pages. Check top Create, bottom paging, Refresh and limit behavior.
7. Create several default-named records with different race/class choices: identify them by real metadata; search names/races/classes on the current page.
8. Compare Draft, Complete and invalid/Needs Attention cards; inspect actual recorded level, missing-choice placeholders and long-name wrapping/tooltips.
9. Open a completed supported character directly from its card; verify approved character sheet remains unchanged.
10. Continue an unfinished draft; verify saved state and G class-change confirmation/reset/cancel behavior remain correct.
11. Edit a completed personal character, save a supported detail, verify Draft/finish behavior and permanent prohibition on first-time destructive class reset; restart and recheck.
12. Duplicate: confirm independent draft and unchanged original/campaign copies; test Cancel.
13. Finish and Submit: select an eligible campaign, confirm one independent copy; inspect Awaiting Approval/Approved and its campaign management route.
14. Delete: read destructive confirmation, cancel once, then delete a disposable source. Return to the library; inspect stale revision/ownership rejection with a second session if available.
15. Verify approved campaign copies and bindings survive source edit/delete. Manage from a campaign sheet must use campaign permissions, never personal editing.
16. Exercise Hub → Library → Sheet → Manage → Edit and Back; Library → Continue and Back; Manage → Submit → Cancel and Back. Ensure no reopened dialog or Sheet/Manage loop and pending-change warnings work.
17. Repeat at GUI scales **2, 3 and 4**, wide/narrow windows and constrained logical sizes. Scroll all content; inspect full names/descriptions via wrapping/tooltips, readable icons, native font, focus/keyboard/mouse behavior and footer clearance. Capture Hub, populated library, Manage and any visual issue for approval.

## Remaining limits and review boundary

Search is page-local, not global. Manual-profile editing is name-only; unsupported profile sheets/creator controls remain disabled with explanations. Existing server limits and validation may reject concurrent actions even after a successful read. Small screens intentionally scroll; all six hub tiles need not fit simultaneously. Six high-resolution source cells are displayed compactly with nearest-neighbor sampling; actual GUI-scale icon legibility awaits Minecraft review.

The other destination contents remain unchanged. No equipment, AC integration, class mechanics, spell automation or new campaign/DM mechanics were added. Stop after this checkpoint for Minecraft screenshot review; do not start the next checkpoint or merge.
