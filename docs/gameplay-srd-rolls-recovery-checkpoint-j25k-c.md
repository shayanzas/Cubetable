# J2.5K-C — DM Rolls and paused-encounter recovery

Base: `9f20b5e27c7b9514fecb1e03c53735ce793ad56b`, verified against the remote K-B branch before editing.
Branch: `dev/gameplay-srd-rolls-recovery-j25k-c`.
The delivery response records the exact verified remote commit containing this report.

## Reproduced Rolls failure and repair

`CharacterCheckGameTests.structuredMemberTargetsThroughNetworking` creates a selected campaign manager and an online bound player. The direct production `CheckGateway.CHECK_TARGETS` returned valid object-shaped `members`. The same request through `UiNetworking.handle` failed on the untouched K-B implementation with:

`REJECTED Invalid UI request or unavailable service`

The reproduction run passed the previous 175 GameTests and failed this new regression. Name enrichment called `getAsString()` on a member JSON object, which is not a UUID primitive. Its outer runtime catch hid the cause. The existing workspace compounded the problem by clearing valid campaign context after a subordinate check read failed, returning its heading to “verifying”.

`AuthorizedNames` now extracts only known account identity fields from an already-authorized read model. It accepts canonical UUID strings and the specific `{id, name}` member-row schema, ignores malformed/other objects, preserves typed rows, and never scans arbitrary string values for UUIDs. Sheet/history/origin/policy/playerNames subtrees remain excluded. Traversal depth is bounded at four, work at 4,096 visited items, unique lookups at 256, and displayed names at 80 characters. It resolves only identities already in the permitted response, without enumerating accounts. Primitive campaign owner/member/DM and character controller enrichment remains supported.

The production UI-boundary integration test covers empty targets/inbox, eligible bound online controllers, CHECK_OPTIONS, CHECK_REQUEST, player execution, DM-only acknowledgements, CHECK_PREPARE DM adjudication, CHECK_EXECUTE retrieval, hidden DC/outcome redaction, pending/completed paging and `hasMore`, CHECK_DISMISS, malformed fields, stale revisions, permission revocation, campaign reads and capabilities. Identical groups keep their operation IDs; accepted retrieval and refresh keep their original results. Existing deterministic check suites continue to cover 10-minute expiry, offline controllers, recipient visibility, campaign/encounter association, rate/capacity limits and no rerolls. Existing manual-dice GameTests exercise its separate payload/service/audience route; manual dice were not converted into private character checks.

## Safe errors and client state

Unexpected UI faults now produce `SERVER_ERROR` with an opaque eight-character reference. The server log contains a recognized operation, exception class and at most eight stack frames, plus the reference. It intentionally excludes exception messages/causes, request bodies, identity lists, dice, private sheets, hidden DCs and credentials. No stack trace is sent to the player. Known input errors, permission failures, unavailable services, stale revisions and storage failures retain separate structured responses. `DM authority required` now maps to `FORBIDDEN`, with empty rejection data. Malformed check specifications are rejected as inputs rather than reported as server faults. Gson-wrapped record validation remains an input error without exposing constructor data.

Workspace read failures identify the operation/code, provide Refresh, disable stale executable controls, and preserve a successfully authorized campaign header for subordinate failures. Actual permission failures clear private data, prepared operation references and watch state. Existing screen-generation/request-pump guards discard old replies; successful subordinate campaign identity is also checked. Read-only refresh never requests/prepares/executes a roll. Mutation errors require deliberate reconciliation and do not retry automatically. Inbox refresh still does not enqueue cinematic dice.

## Scoped recovery workflow

The battlefield's paused-manager **Recover** control opens `SrdRecoveryScreen` directly. DM Workspace → Encounters exposes **Paused encounter: heal / review / end…** for the watched encounter. The encounter entry route also sends paused views to this screen. No routine More menu or Workspace redesign was restored.

The small scrollable recovery panel leaves the world background visible and provides Back, Battlefield and Refresh. It distinguishes:

- **Committed zero-HP/adjudication pause:** select a roster actor by stable UUID (displayed as name and authorized HP), enter a whole amount, review target/amount, and explicitly confirm healing. Healing persists HP but **does not resume combat**. The DM explicitly ends the encounter to resume exploration.
- **Uncommitted/failed saved attack:** healing is disabled. Review/retry uses the existing retained dice and transaction, with explicit confirmation. Gameplay Transaction Review remains reachable, including existing audited cancellation after encounter closure. Permission/revision/storage conflicts may still reject a retry; there is no automatic rebase or reroll.
- **End encounter:** explicit confirmation explains that committed HP/resources remain saved and unresolved prepared records still require review. Ending does not implicitly heal, cancel a durable preparation or run death saves.

The manager-only additive `recovery` read model contains `kind`, `canHeal`, `canRetry` and `maximumHealing`. It exposes no receipt internals to other viewers. Nonmanagers have read-only paused status; server authorization remains decisive. A heal offered by the read model remains subject to target freshness/binding checks at execution.

`SrdHealth.heal` supports amounts 0–1024 and caps HP at maximum while preserving temporary HP. The new deliberate UI/packet workflow requires **1–1024**. The existing command/core arithmetic, including its historical zero amount behavior, is unchanged. No resurrection or death-save reset is implied.

`SrdRecoveryReview` binds confirmation to encounter, revision, manager permission and PAUSED state. Cancel sends nothing; pending operations block dispatch; a consumed review cannot submit twice. Context changes invalidate review. HP is read only from the server response.

The `heal` packet now requires exactly `target`, `amount`, and reviewed encounter `revision`. The server rechecks selected campaign, revision, positive bounded integer amount, current manager permission, PAUSED state, participant identity, loaded living actor, binding and saved character snapshot before the existing atomic `commitSrdHealth`. Request UUID/turn admission remains intact. Old hand-crafted heal packets lacking `revision` are rejected; the previously existing command is unaffected. This tightens a packet that had no healing GUI in K-B. No world/storage schema changes occur.

## Transaction evidence and preserved behavior

New recovery GameTests exercise:

- ACTIVE rejection, committed zero-HP pause, positive heal, exact duplicate UUID rejection and new-UUID/stale-revision rejection.
- Nonmanager forging, unknown encounter/nonparticipant, invalid amounts, changed selected campaign, revoked manager, changed binding and removed actor.
- Maximum-HP cap, real campaign-file reload after healing, persistent HP after explicit end and rejection after ending.
- Real `AtomicJsonFile` fault injection through the existing `SrdSaveFault` seam: failed healing leaves HP/revision unchanged; failed attack retry keeps HP unchanged and retains dice; repaired persistence commits the same fixed result once.
- Committed attack receipt and RNG count unchanged by healing; no automatic combat resume.

No new transaction engine, condition mechanics, spell mechanics, combat calculations, persistence migration or monster behavior was added. K-A movement, K-B request correlation/camera/input/fading/hotbar cleanup, Dash/Dodge/Prone, existing checks/dice, Tactical and Generic D20 remain under the full regression suite.

## Validation

Executed with Java 21 and the existing toolchain/proxy/trust configuration:

```bash
JAVA_HOME=/workspace/.tools/jdk21 GRADLE_USER_HOME=/workspace/.gradle \
JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

Final delivery validation: **BUILD SUCCESSFUL in 1m 25s**.

- **42,944 foundation assertions across 47 suites** (K-B baseline plus 55 new deterministic assertions).
- **181/181 GameTests** (six new integration tests); zero failures/errors/skips.
- **2,748 catalog checks + 15,115 coverage checks = 17,863**.
- Built JAR: `build/libs/tactical-combat-0.1.0.jar`.
- SHA-256: `6a5efde38e451702da1a67c0caa984a3809eb01e049cb614e4cc91da5b769702`.
- Evidence retained locally in `build/j25kc-delivery-validation.log`, `build/j25kc-reproduction.log`, `build/j25kc-targeted.log` and `build/gametest-results.xml`.
 The intentional before-fix reproduction and test-harness compile corrections are not final test failures. Existing assertions were not weakened, deleted or skipped. Mock-player/Gradle deprecation warnings and the optional Minecraft public-key service DNS warning are separate from executable test results. The new diagnostics suite deliberately injects one opaque server fault to test redaction.

## Minecraft acceptance checklist

Use a backed-up disposable test world with the same mod on client and server:

1. Select the campaign as manager; open DM Workspace → Rolls. Verify the campaign label, targets and empty/results inbox load without the old generic error.
2. Request a check for an online bound player. Execute from that player's inbox; verify a single result, the chosen audience and hidden DC privacy. Refresh/reopen repeatedly without a new roll or animation.
3. Prepare a DM-adjudicated check, then explicitly roll/retrieve it. Test manual dice separately; its audience remains the existing nearby/encounter audience.
4. Test an ordinary member and a revoked manager: no DM target enumeration, secret results or healing controls. Try a campaign switch during loading.
5. Reduce an actor to zero D&D HP in controlled combat. Open Recover, select the actor, enter 4, cancel once, then review and confirm. Verify one HP increase and that combat remains paused.
6. End the encounter explicitly; reopen the campaign character and restart the test server to inspect retained HP. Check camera, hotbar, fading and world controls restore as in K-B.
7. Check Workspace → Encounters reaches the same recovery screen and Back/Battlefield navigation works at GUI scales 2, 3 and 4. Exercise overflow roster scrolling and pending/disabled controls.
8. Run ordinary split movement, Dash, Dodge, Prone/Stand and a contextual weapon attack with the manager controlling player and Husk. No K-B interaction should change.

Do not corrupt a save to test recovery failures; automated fault injection covers those paths.

**J2.5K-C GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.** No display or Xvfb is available. Real mouse input, panel readability, GUI scales, camera restoration and walking appearance remain manual acceptance items. K-A/K-B visual acceptance is not implied by these tests.

Check tickets remain session-scoped and expire after 10 minutes; restart does not resume/recreate them. Recovery is limited to existing paused encounter rules. There is no automatic zero-HP resolution, healing spell, death save, resurrection or combat resumption. No primary world was modified.

## Changed files

New:
- `src/main/java/dev/tacticalcombat/uiapi/AuthorizedNames.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiDiagnostics.java`
- `src/main/java/dev/tacticalcombat/client/SrdRecoveryReview.java`
- `src/main/java/dev/tacticalcombat/client/SrdRecoveryScreen.java`
- `src/test/java/dev/tacticalcombat/client/SrdRollsRecoveryTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdRecoveryGameTests.java`
- This report.

Updated:
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/checks/CheckGateway.java`
- `src/main/java/dev/tacticalcombat/client/ui/DmWorkspaceScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdCombatScreen.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/CharacterCheckGameTests.java`
- `src/gametest/resources/fabric.mod.json`
