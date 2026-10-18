# J2.5F-B — Interactive tactical battlefield

Base: `dev/gameplay-srd-world-interaction-j25f-a`, `12e3ba00271b547bb6dacc58110a69e91a61f73d`.
Delivery branch: `dev/gameplay-srd-world-ui-j25f-b`.
Implementation commit: `ad9163e557a75324401e37db688410f0b968b6ac`.
Final commit: resolve the commit containing this report with `git log -1 --format=%H -- docs/gameplay-srd-world-ui-checkpoint-j25f-b.md`; the exact remote SHA is supplied in the delivery message. A commit cannot contain its own hash.

**GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.** This applies to Stage A and Stage B. No rendered screenshots, GUI-scale passes or manual input tests are claimed. The environment has no configured DISPLAY/Wayland display or installed Xvfb. Automated model/layout tests do not prove visual quality. No primary world was opened or modified.

## Implemented workflow

The ordinary SRD battlefield remains visible through movement, target selection, attack selection, server preview and explicit confirmation. These actions no longer open the opaque legacy attack screen. Optional **More → Details / DM** preserves advanced configuration, initiative adjudication, transaction recovery and encounter management.

- **Current turn card:** active actor, original pixel silhouette, public Minecraft entity type, initiative, authorized HP/temporary HP, remaining feet, action state, round and Up Next. Bonus actions are explicitly described as unsupported; no fictional mechanics fill the toolbar.
- **Persistent action bar:** Move, Attack, Confirm Move, End Turn, History and More. At narrow widths it uses two rows. Controls are custom Cubetable charcoal/stone/gold RpgButtons with keyboard focus, tooltips and disabled-state explanations. End Turn sends the SRD request and remains reachable while contextual panels are open.
- **Movement:** hover a server-provided destination to see the route; click to select, inspect cost/remaining feet, then Confirm Move. Outlined reachable cells, route pips and a gold destination replace solid filled squares. An unavailable surface cue is visual only and never becomes a movement destination. Stage A's automatic discovery and spent-foot accounting remain authoritative.
- **Attack:** choose Attack before clicking a target, or click a creature and then choose Attack. A compact paginated chooser exposes supported weapon/ability/mode/hand shapes, including the single legitimate unarmed shape. Preview requests carry only selection fields. The inline preview shows attacker/target, weapon, server roll expression, reach/range, damage expression/type, cover, action cost and stock cost. Confirm submits only the server intent UUID. Cancel/Escape/right-click spend nothing locally and do not undo an already submitted server operation.
- **World identity:** depth-tested ground outlines distinguish current turn, self, other controllable actors, neutral visible combatants, and selected target. No alliance or hostility is invented from mob type. Projected labels use the public roster and loaded entity identity and are occluded by solid terrain, including visually faded camera occluders. HP stays in authorized HUD/context data.
- **Results:** a dismissible ten-second toast identifies attacker/target, hit/miss/critical, server attack result, rolled damage, authorized applied HP/temp-HP changes and zero-HP adjudication. Rolled damage is never equated with HP loss. Recent History holds at most 32 authorized entries, including server-confirmed movement steps and turn changes, and scrolls for full result text. Operation IDs distinguish identical text from separate attacks. History/latest results clear on control-permission changes, encounter changes and disconnect.
- **Camera and pace:** Stage A's angled camera, actor follow, smooth transition, bounded pan/orbit/tilt and ordinary-camera restoration remain. Center and zoom controls plus tooltips expose wheel/middle-drag/WASD controls. More provides labeled Fast/Brisk/Walk/Slow choices, respectively 1/2/4/6 server ticks per square, with a visible selection. The default remains four ticks. This is discrete server movement, not a promise of continuous walking animation. Pace is session-local.

## State, safety and permissions

`ClientSrdCombat` remains the sole SRD client state and packet gateway. No parallel combat engine, client dice, private HP reconstruction, new server rules, persistence writes or resource calculations were added. New pure presentation helpers are `SrdBattlefieldModel`, `SrdBattlefieldActions` and `SrdRecentActivity`.

The model derives current/next actor and control from the authorized roster. Encounter/turn/control changes clear stale targeting; harmless snapshots and resizes preserve the selected target/tool. A same-revision grid refresh preserves a destination only if it remains in the server grid. New revisions still invalidate movement/attack previews. Automatic discovery never sends a mutating request.

The client allows one outstanding mutating UI request, disables repeated submissions and does not automatically retry. Target-changing world clicks are held while a request is pending so an old preview cannot be mislabeled as a newly selected target. Confirm consumes the local preview immediately. Read-only **More → Refresh state** offers reconciliation if a reply is lost; it never resends the previous mutation. Server rejection is visible in activity and the status strip. Server authorization, turn identity, J2 receipts and authoritative revalidation remain the actual security boundary.

The campaign manager can control the player actor and separately bound Husk in their respective turns; ordinary inspection does not grant control. The existing Stage A manager/player/Husk GameTest and permission-revocation tests remain required. No character ownership transfer was introduced.

Tactical and Generic D20 routing, J2 transaction/restart guarantees, D1 checks/privacy, D2 stages/monster support, E dice, library schema 2, campaign schema 5 and C2's 1,806 reference records are unchanged. No server or catalog source/resource files changed in this checkpoint. No external artwork, textures or audio were added.

## Layout and interaction limits

Deterministic layout tests cover scaled viewports from 256×144 through 1920×1080, including representative dimensions for GUI scales 2/3/4. Panels remain within bounds; the primary toolbar never overlaps the context or toast. On short viewports, the optional context temporarily replaces the top status/camera controls; End Turn and the primary bar remain visible. Compact previews/history scroll their text while action buttons remain pinned. Long names/text are trimmed or wrapped. Actual GUI-scale readability, visual overlap with world actors, label placement and mouse projection still require a real client.

Context panels are anchored at the side rather than tracking the selected entity; pan/zoom remain available and panels can be cancelled. Closely clustered nameplates may overlap. Labels/picking perform bounded terrain checks for at most 16 participants; reachable geometry remains bounded by the existing server grid cap. There are no full-screen particles, shader effects or extra per-actor resource broadcasts.

Recent activity is transient, not a durable campaign log. Its result deduplication is bounded presentation state; authoritative replay safety remains J2's responsibility. Presentation settings are session-local. No new supported abilities, Dash, reactions, spells, class features, monster AI, or larger DM Workspace redesign was added.

## Validation

Final command:

```sh
JAVA_HOME=/workspace/.tools/jdk21 GRADLE_USER_HOME=/workspace/.gradle \
JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts' \
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

- **BUILD SUCCESSFUL**, Java 21, final full command completed in 1 minute 5 seconds.
- **37,809 foundation assertions across 43 suites**, including **902 new battlefield UI assertions**.
- **145/145 GameTests**, zero failures, errors or skipped cases.
- **2,748 catalog checks + 15,115 coverage/completion checks = 17,863 checks**. Repeated task executions are not double-counted.
- Graphical rendering, real mouse/keyboard input, actual GUI scales and end-to-end user acceptance: **not verified**.

The new UI suite checks current/next roster behavior; manager multi-actor transitions; inspection versus control; turn/permission invalidation; harmless-refresh preservation; server-cell destination retention; legal attack-intent fields; preview-required explicit confirmation; bounded/deduplicated/private history; and panel/button bounds across 63 viewport combinations. Existing foundation and GameTest suites continue to cover camera eligibility, Tactical priority, same-turn 5+5+attack+remaining movement, controlled Husk turns, denied/stale requests, pacing interruptions, HP/privacy, zero HP, transactions, catalog, creator and management regressions.

An intermediate compile caught ambiguous `Math` imports in the new projection helper; explicit JOML imports corrected it. No assertions were weakened or removed. Evidence: `build/j25fb-targeted.log`, `build/j25fb-release-validation.log`, `build/gametest-results.xml` and `build/reports/srd51-catalog.json`. Test worlds remain under ignored build output. Existing deprecation/authentication-public-key lookup warnings do not establish graphical acceptance.

## Minecraft acceptance checklist — pending

Use matching client/server JARs in a backed-up disposable test world.

1. Select the SRD campaign and use approved bound `ree_bunger` plus a separately prepared Zombie-stat-block Husk. Give the one controlling account current campaign DM authority. Start through the launcher and resolve any initiative tie.
2. Confirm angled camera entry, readable Current Turn/Up Next/initiative, distinct actor labels/markers and correct follow when the active actor is the Husk. Test wheel, middle-drag, WASD, Center, chat, inventory and Quick Sheet.
3. In Move mode, hover/click reachable terrain, inspect path/cost, and Confirm Move. Spend 5 feet, another 5 feet, attack, then spend remaining movement on the same turn. Check 25 then 20 feet remains. Test each labeled pace and solid/slab/elevated terrain.
4. Select Attack then click a visible target; repeat target-first. Choose a supported attack, Preview, inspect all authorized fields, Confirm and inspect the toast/history. Cancel another preview. No routine step should open Details or hide the battlefield behind a full-screen sheet.
5. Verify unarmed has only its supported configuration. Check private target HP stays private for an ordinary player. At authorized zero HP, verify explicit DM pause messaging rather than automatic death.
6. End Turn after movement, attack, cancellation and rejection, including with More/History open. Verify active actor, control, movement and camera update to the next participant without switching accounts or ownership.
7. Test right-click/Escape, keyboard focus, resize and GUI scales 2/3/4. Confirm tool/target persistence on harmless refresh, clearing on a new turn, safe pending feedback, and no automatic replay. Record actual resolutions/screenshots later.
8. Revoke DM permission and verify NPC control/private history disappear. End/disconnect and verify ordinary camera restoration. Smoke-test existing Tactical/Generic D20, D1 private checks, E dice, C2 browsing and D2 stages.

## Artifact and delivery

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `4956cfd28c8d660c3f63e391e718a7e5d04c68b454674bd30c58bde5707f1f1b`.
The dedicated branch is pushed after successful validation under the user's explicit authorization. No merge, force-push, other-branch changes, upstream changes or primary-world installation is performed. Stop after this checkpoint.

## Files changed

- `docs/gameplay-srd-world-ui-checkpoint-j25f-b.md`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldActions.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldModel.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdGridRenderer.java`
- `src/main/java/dev/tacticalcombat/client/SrdRecentActivity.java`
- `src/main/java/dev/tacticalcombat/client/SrdWorldPicker.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/SrdBattlefieldUiTests.java`
