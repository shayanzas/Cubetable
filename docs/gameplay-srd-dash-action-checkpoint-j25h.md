# J2.5H — SRD Dash action and movement integration

Base: `11507b1879ad3874943886821769b64e81c3bea8` (J2.5G). Delivery branch: `dev/gameplay-srd-dash-action-j25h`. The final verified remote SHA is supplied in the delivery response and Git branch history.

## Implemented behavior

Dash spends the active creature's normal action and adds its authoritative supported walking speed to that turn's movement allowance. It does not move the creature or refund any completed movement. A 30-foot actor with 5 feet spent has 55 feet remaining after Dash. Ordinary movement and End Turn remain available. Weapon attacks and Dash share the same action budget, including reserved weapon transactions. No bonus-action Dash or class exception is exposed.

`SrdEncounter.dash` validates current campaign/controller authority, expected campaign/actor/encounter turn/revision, a fresh live character/binding, positive HP, no executing route, available action, and no unresolved J2 reservation. `SrdMovementTurn` expands its budget on the server thread, clears its cached preview, and retains the spent-feet ledger. Every potentially rejecting check occurs before the grant/action mutation; the immediate transition performs no file IO, RNG, entity movement or event callbacks. A new server turn creates fresh movement/action budgets for the next actor.

The authenticated `dash` request accepts only expected campaign, actor and revision. Client-provided costs, speed and other fields are rejected. Existing bounded `TurnRequests` admission consumes request IDs before execution and rejects duplicates/stale turns; a new ID cannot bypass the shared spent action. No ID eviction within a turn makes an old request eligible: the 128-request cap fails closed, and history clears only when the turn identity changes.

Dash is an encounter/turn-local resource transition, not a persistent character mutation. It does not introduce a second durable journal or alter J2 receipts, campaign schema 5, library schema 2 or character revisions. Encounters do not resume on restart; unused movement and action state therefore expire, while committed HP/ammunition retain existing J2 guarantees. No global exactly-once claim is made across restoring an older world backup.

## Client integration

The existing compact battlefield bar adds Dash, displaying the server-provided grant and a tooltip explaining its normal-action cost. It is disabled for unauthorized/inactive actors, spent actions, executing routes and pending mutations. Explicit clicks send one request; no automatic mutation retry is added. An accepted server response supplies movement/action state and a bounded history/toast message. A lost acknowledgement can be reconciled through existing read-only Refresh state. Dash invalidates stale movement previews; existing revision-driven automatic grid discovery fetches a fresh preview. Move and End Turn stay available afterward. Camera, world picking and DM Workspace behavior are retained.

Movement previews now accept at most 600 feet, covering the existing maximum supported 300-foot speed plus one Dash. Character speed bounds remain 0–300. Search remains bounded to 4,096 nodes and the existing incremental high-speed server job/deadline; large open grids may be truncated. The old search-bound test now rejects 601 rather than 301, with added 600-foot bounded-search coverage. No assertions were deleted or disabled.

## Validation

Java 21 targeted foundation/compile checks passed. Final command:

```bash
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

Final result: BUILD SUCCESSFUL (1 minute 10 seconds).

- 38,435 foundation assertions across 44 suites (G: 38,354).
- 151/151 GameTests (G: 146), no failures, errors or skips.
- 2,748 catalog checks plus 15,115 coverage checks = 17,863.
- Existing Tactical/Generic D20, SRD weapon/transaction, character, DM Workspace and dice suites remain enabled and passing.

Five new real server GameTests exercise Dash before movement, the exact 5-foot-then-Dash example, movement beyond original speed, attack/Dash exclusion, in-flight-route rejection, duplicate/new-ID/stale revision/turn and forged-grant requests, refreshed grids, independent turns, a manager-controlled prepared Zombie-stat-block Husk with 20-foot speed, authority revocation, disconnected actor rejection, and the maximum supported speed with an incremental terrain preview. An existing real-storage transaction fault test now also verifies Dash cannot bypass a failed prepared weapon reservation. Foundation checks cover pending UI operations, unavailable capabilities/disconnect state, action reservations and layout bounds at representative scaled dimensions.

Evidence: `build/j25h-targeted.log`, `build/j25h-validation.log`, `build/gametest-results.xml`. Existing deprecated test-API/Gradle warnings and an optional Minecraft authentication-public-key DNS warning did not prevent the successful headless suite.

Built JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `a8d6b1a31a014def4579a4eaf74cbb1b7971a6eb7396c04e59592e3b6d994a22`.

## Limits and Minecraft acceptance

GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.

No graphical display/Xvfb is available. F-A/B and G graphical acceptance also remains pending. Mathematical layout checks do not establish text legibility, actual mouse behavior or graphical approval.

Dash uses existing `DerivedRulesState.walkingSpeedFeet`, including the existing supported armor-strength speed adjustment. It does not implement new effect-based speed changes, alternate movement modes, bonus-action/class exceptions, reactions or other actions. Movement remains discrete authoritative server steps at the existing selected pace. Install matching updated client/server binaries on a separate test setup, not a primary world.

Manual checklist:

1. Start a controlled encounter with a 30-foot player and an independently prepared/bound Husk using the Zombie profile. Verify identification, angled camera and controller permissions.
2. Move 5 feet: 25 remain. Hover Dash: +30 feet and normal-action cost. Click once: no immediate movement, 55 feet remain, action spent, clear server-confirmed notification.
3. Continue moving, including beyond the original 30-foot allowance. Verify path/cost/grid refresh, ordinary Move and End Turn remain accessible. Confirm Attack and a second Dash are unavailable.
4. On a fresh turn, attack first and confirm Dash is unavailable. On another fresh turn, Dash before moving and verify 60 feet. End the turn and confirm unused grant expires.
5. Use the same manager account for the Husk: Dash grants 20 feet for a total of 40. Verify an ordinary player cannot control it, and revoking manager permission removes control.
6. Check pending-request disabled states, read-only Refresh recovery, return through DM Workspace, GUI scales 2/3/4 and small windows. Confirm no automatic resubmission on refresh, screen navigation or reconnect.

## Files changed

- `src/main/java/dev/tacticalcombat/combat/SrdActionBudget.java`
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/rules/srd/SrdMovement.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldActions.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/test/java/dev/tacticalcombat/SrdMovementTests.java`
- `src/test/java/dev/tacticalcombat/client/SrdBattlefieldUiTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdDashGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/GameplayTransactionGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `docs/gameplay-srd-dash-action-checkpoint-j25h.md`
