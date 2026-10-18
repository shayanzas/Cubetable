# J2.5G — Interactive DM Workspace

Base: `dev/gameplay-srd-world-ui-j25f-b` at `db171f6cad753363952fb1f2908fc1c0edfa66f4`.
Branch: `dev/gameplay-dm-workspace-redesign-j25g`.
Implementation commit: `2d0b356732e396786a9e7c80643ad17bb2554632`.
Final delivery commit: the commit containing this report; resolve with `git log -1 --format=%H -- docs/gameplay-dm-workspace-checkpoint-j25g.md`. The exact verified remote SHA is provided in the delivery message. A commit cannot contain its own hash.

**GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.** This includes the earlier F-A/B battlefield. No actual mouse-input, rendering, GUI-scale passes or screenshots are claimed. DISPLAY/Wayland are unconfigured and Xvfb is unavailable. No primary world was opened or modified.

## Five-tab implementation

| Tab | Inline information and common operations | Retained advanced screens |
| --- | --- | --- |
| Party | Authorized cards with name, class/effective level or monster type, HP/temp HP, AC, approval/binding, recorded conditions and current controller name. Player-owner skin when available; neutral NPC marker. Selecting a card shows its compact authorized overview in the workspace; unbound unapproved characters can be explicitly approved. Server pagination remains eight records per page. | Full sheet, ability/skill/save options, roster assignments/controller/binding administration. |
| Actors | Current 32-block discovery, entity type, distance, character association, bound/readiness state and explicit rejection reasons. Search, independent binding/type/readiness dropdowns, name/distance sorting, actor selection and a shared bounded participant set. Start selected participants with confirmation. | Existing approved-character binding and verified SRD-monster preparation/review. The discovery list itself no longer requires the launcher. |
| Encounters | Selected-campaign encounter list, ready-participant count, read-only Watch, inline watched round/current actor/initiative/authorized HP/movement/action status, explicit End confirmation and Start participant workflow. Battlefield return preserves the original parent screen when available. | J2 transaction recovery and saved-stage editor. Encounter identity is described through participants/status; the existing API provides no separate encounter name. |
| Scene | Proportional map using actual discovery origin and actor X/Z, north/center, named/numbered selectable actors, readiness cues/tooltips, selected participants in slot order, saved stages and explicit slot locations. Create empty stage, capture selected positions, validate assignments, separately confirm activation, delete with confirmation. Browsing/validation never starts combat or moves actors. | Revision-safe name/description/role editor and more detailed assignment workflow. |
| Rolls | Ability/skill/save selection, normal/advantage/disadvantage, explicit audience/recipient selection, purpose and optional hidden DC. Inline player requests, explicit DM preparation followed by explicit execution/retrieval of the same operation, authorized pending/accepted inbox with execute/dismiss, manual die/quantity/modifier and eligible D20 modes, live manual result. | Advanced grouped request/adjudication screen and dice animation/audio settings. |

## Workspace shell and tactical integration

The shell labels the server-supplied campaign name as **Campaign: …**, retains five tabs, tab-specific search/selection/scroll state, and uses Cubetable RpgButtons, ordinary dropdowns and the existing clipped CharacterCanvas. Dropdowns support mouse, wheel, arrows, Enter and Escape; there are no option-cycling filters. The workspace uses a translucent outer panel with opaque readable content cards. Compact/expanded sizes and title-bar dragging stay within the viewport.

Search filters the currently authorized page/snapshot. Party search/sort does not claim to search beyond the current eight-record server page. Nearby discovery is bounded by the existing 32-block/64-actor contract; check targets/members retain their existing server bounds. Empty/loading/rejected/permission-revoked states are shown honestly.

During combat the world remains visible and the existing SRD camera keeps following. **Battlefield** returns to the original parent battlefield screen when it was used to open DM tools; otherwise it opens the existing SRD battlefield. No turn/action request is emitted by navigation. This remains a focused Minecraft Screen: world clicks and camera-pan keys are not passed through the workspace. Persistent simultaneous battlefield interaction would require a separately reviewed input architecture. This limitation preserves safe input behavior.

## Authority and lifecycle

No parallel DM authority, new combat mechanic, client dice calculation or persistence schema was introduced. All actions use the existing UI operations, CharacterRepository approval/binding rules, EncounterLauncher discovery/admission tokens, D2 stage revision/placement validation, CheckGateway/CheckService and DiceService.

The only server response change is additive DM_QUICK card metadata: owner identity for existing skin rendering, record revision, resolved online/offline controller label, class/effective-level or reviewed monster label, and temporary HP. This response still requires current selected-campaign manager authorization. Unknown/invalid projections do not receive invented statistics. Recorded conditions remain labeled recorded, with no new mechanical automation.

Each tab begins with current manager/campaign authorization. Existing generation-scoped UiRequestQueue callbacks ignore responses after navigation or invalidation. Panel-watch changes disable mutations and request a paced read-only refresh; revocation clears all displayed private data and selected private identities. Failed reads/mutations also hide displayed data, close the old watch and require explicit read-only reconciliation. No ambiguous mutation is automatically retried. A prepared check operation is retained for deliberate same-ID retrieval, but cleared when campaign/manager access changes.

Read-only refresh retains valid selected actors and per-tab state. Missing/ineligible participants are removed from the Start set. Start consumes the real discovery token and still revalidates every actor. Approval, stage capture/delete/activation and encounter ending require explicit confirmation. Stage capture saves a template; validation returns only a short-lived token; activation moves only after confirmation; combat starts separately. No world/campaign-file atomicity claim is added.

Roll results are rendered only when the authorized reply contains result data. Missing DC/success fields are not inferred. Inbox refresh never rolls or replays dice animation. Only explicit execution enqueues a returned authorized result through existing cinematic presentation. Manual dice retain the existing nearby/encounter audience and are explicitly described as non-secret, unauthenticated manual modifiers. Character rolls use the separate DM-only/campaign/specific-recipient contract. Repeated execution retrieves the same accepted operation. No HP, action budget or campaign resource changes are caused by ordinary checks.

J2 transactions, D1 privacy, D2 stage store, E dice, F-A/B camera/picking/movement/inline combat, C2's 1,806 reference records, creator/library, Tactical and Generic D20 remain intact. Library schema 2, campaign schema 5 and stage schema 1 are unchanged. No new artwork/audio or external dependency was added.

## Validation

Command:

```sh
JAVA_HOME=/workspace/.tools/jdk21 GRADLE_USER_HOME=/workspace/.gradle \
JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

- **BUILD SUCCESSFUL**, Java 21; final full command completed in **1 minute 5 seconds**.
- **38,354 foundation assertions across 44 suites**, including **545 new workspace checks**.
- **146/146 GameTests**, zero failures, errors or skipped cases.
- **2,748 catalog checks + 15,115 coverage/completion checks = 17,863 checks**; repeated Gradle executions are not double-counted.
- Graphical Minecraft rendering, actual input and GUI-scale acceptance: **not verified**.

New foundation coverage exercises all five tabs, independent actor filters, actual distance sorting, read-only selection retention, missing/ineligible selection removal, campaign/revocation clearing, per-tab scroll/search state, proportional scene coordinates, human-readable recipient labels, private versus authorized roll outcomes, stale-generation response rejection, successful read-only recovery and no automatic mutation retry. Layout tests cover 112 compact/expanded viewport combinations from 320×180 through 1920×1080. These are logical layout tests, not graphical GUI-scale acceptance.

The new GameTest uses a real temporary campaign/repository, mock manager/player connection, bound player and separately prepared source-owned Zombie. It verifies authorized enriched party cards, discovery origin/radius/token, no combat from browsing, roll-target/inbox authorization, explicit private request/execution, same-ID accepted-result retrieval, unchanged combat resources, denial to ordinary members and denial after manager revocation. Existing F player/Husk split movement, inline combat model, D1 dice/privacy, J2 fault/restart/replay, D2 stages, catalog and character regressions remain required.

Intermediate compilation caught a compound `var` declaration and was corrected before validation. No tests or assertions were weakened or removed. Evidence: `build/j25g-targeted.log`, `build/j25g-delivery-validation.log`, `build/gametest-results.xml`, `build/reports/srd51-catalog.json`. Existing deprecation and optional external authentication public-key lookup warnings do not imply graphical acceptance. Tests use generated/temporary worlds only.

## Remaining limitations

- Graphical acceptance remains pending: dragging, dropdown rendering/focus, scene-label overlap, actual GUI scales and combat→workspace→combat input must be tested in Minecraft.
- On short scaled windows the content scrolls heavily; primary tabs, refresh and Back/Battlefield remain outside the scroll area. Very close scene actors can overlap labels; the selectable list/tooltips retain their identities.
- Cards use available online owner skins; offline skins may be unavailable. NPCs use a neutral marker, not invented monster art. Conditions are informational records.
- The existing encounter API supplies detailed state for the explicitly watched encounter. Watch another entry to inspect its live initiative; it does not provide a named aggregate snapshot of every encounter.
- Binding, monster preparation and some advanced character/stage edits intentionally retain their existing validated screens. No unsupported inline mutation was fabricated.
- Activity/inbox records remain session-limited by D1. A manual roll uses its existing audience rather than a new secret-dice permission. No automatic reactions, spells, monster AI or other new rules exist.

## Minecraft acceptance checklist — pending

Use a backed-up disposable world and matching client/server JARs.

1. Open DM Workspace as a current campaign manager. Verify the labeled campaign name, five tabs, compact/expanded sizes, title dragging, readable dropdowns, search/sort, keyboard focus and retained scroll at GUI scales 2/3/4.
2. Party: inspect a player and source-prepared NPC inline; verify authorized HP/temp HP/AC/type/controller/conditions. Test an eligible approval and advanced sheet/roster return. Revoke DM permission and confirm private contents clear.
3. Actors: verify the 32-block origin/radius, all independent filters, rejection reasons, stable selection, binding and source preparation. Choose two ready participants; verify explicit Start and server rejection of stale discovery.
4. Encounters: inspect Current Turn/round/initiative/HP, switch the watched encounter deliberately, return to battlefield, and test explicit End. Confirm resource persistence and J2 recovery access.
5. Scene: match actor labels/positions to actual world locations. Select actors in slot order, save an empty stage, capture positions, validate without movement, cancel once, then explicitly activate. Confirm no combat begins until Start.
6. Rolls: request an ability check, skill check and saving throw; test DM-only and specific recipients using separate players. Execute/retrieve the same prepared operation. Check pending/accepted/dismissed states and absence of animation on refresh. Test manual quantities/modes and the clearly stated manual audience.
7. During the player/Husk SRD encounter, open/close/return from DM tools. Verify camera follow, repeated 5+5 movement, inline attack confirmation, End Turn and authorized combat history remain functional. Smoke-test Tactical/Generic D20, C2 browsing, creator/library, D1 checks, D2 stages and E dice.

## Artifact and delivery

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `59f5144a5ce6e630a939ceba6bd33e395da17712b7e1770bd3994853da32f52a`.
The dedicated branch is pushed after successful validation under the user's explicit authorization. No force-push, merge, other-branch changes, upstream changes or primary-world installation occurs. Stop after delivery.

## Files changed

- `docs/gameplay-dm-workspace-checkpoint-j25g.md`
- `src/gametest/java/dev/tacticalcombat/combat/DmWorkspaceGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/ui/CharacterCanvas.java`
- `src/main/java/dev/tacticalcombat/client/ui/DmWorkspaceModel.java`
- `src/main/java/dev/tacticalcombat/client/ui/DmWorkspaceScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/EncounterStagesScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/WorkspaceSelect.java`
- `src/main/java/dev/tacticalcombat/combat/EncounterLauncher.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/ui/DmWorkspaceTests.java`
