# J2.5I — Controlled SRD Dodge

Base: `bdd17b0dc7f74b59402f4ac8dc403e75481c6a8e` (J2.5H).
Branch: `dev/gameplay-srd-dodge-action-j25i`. The verified remote commit is supplied in the delivery response and branch history.

## Action and lifecycle

Dodge spends the same normal-action budget used by weapon attacks and Dash. The authenticated gateway accepts only expected campaign, actor and revision, under the existing encounter/turn/request-ID admission checks. It rejects stale/duplicate requests, unauthorized controllers, an executing route, a spent/reserved action, unresolved weapon storage transactions and invalid eligibility before spending. Movement is neither granted nor spent by Dodge; ordinary remaining movement and End Turn remain available.

The encounter owns one bounded set of Dodging actor IDs (at most its existing 16 participants). Ending an actor's turn retains its Dodge. Beginning that actor's next turn removes only that actor's state. Multiple actors can independently Dodge. Removal is permanent for that activation: waking or otherwise restoring eligibility does not restore a lost benefit. Invalid actors lose their state, and ending the encounter clears all state. A recoverable weapon persistence pause retains otherwise valid Dodge until its proper expiry; retry still uses the original prepared dice.

Like Dash, Dodge is an immediate encounter-local action, not a durable character resource update. Existing bounded turn request IDs fail closed at capacity and cannot replay in a different turn or encounter. Restart does not reconstruct an encounter or restore Dodge. J2 persistent resources, receipts, replay handling and schema remain unchanged. There is no global exactly-once guarantee across administrator backup restoration.

## Incoming attacks

The server combines eligible defender Dodge with the attack pipeline's existing boolean disadvantage source. Existing SRD cancellation applies: advantage plus disadvantage is normal; multiple disadvantages do not stack dice. Preview and confirmation both inspect current Dodge/sight; changed geometry or defensive applicability requires a fresh preview before any cost or RNG. Accepted attack flags and fixed outcomes enter the existing J2 prepared/committed plan and receipt without a new transaction type.

Sight uses server-loaded geometry, existing cover inspection and Minecraft's eye-to-eye visibility ray. An invisible attacker, sleeping defender, or defender with Minecraft Blindness/Darkness does not receive the Dodge sight-based attack penalty. Camera angles, client flags and camera occluder fading are irrelevant. These are conservative Minecraft sight mappings, not a complete implementation of tabletop lighting, darkvision, blindsight, truesight or magical-obscurement rules. Blindness/Darkness checks here only gate this Dodge benefit; they do not introduce all SRD blinded-condition mechanics.

## Eligibility and condition limitations

Supported eligibility requires a valid live approved binding, positive authoritative D&D HP and walking speed, and an awake actor. Invalid or changed authoritative character data removes the old defensive benefit. The current supported speed projection includes the existing armor-strength adjustment; new effect-based speed modifiers are not introduced.

Cubetable still has no general executable SRD incapacitation/condition system. Recorded `CONDITION` and `TEMPORARY` feature entries therefore conservatively block Dodge and its benefits instead of being interpreted as executable prose or silently ignored. This can suppress Dodge for a recorded condition that would allow it under full SRD rules. The UI explains that those records need DM review. Unrecorded SRD conditions cannot be inferred; special senses and full condition automation remain deferred. This checkpoint claims the documented controlled subset, not unrestricted SRD condition/visibility compliance. No new condition schema or arbitrary effect formulas were added.

## Authoritative Dexterity saves

The check gateway now resolves the selected character's actual repository binding and server encounter, rather than assuming that a DM's own Minecraft actor is the check subject. Preparation/group requests capture that trusted actor/encounter association. Execution repeats authorization, binding/encounter association, character/runtime revision and current Dodge checks before RNG. A changed association rejects the pending operation; there is no guessed actor or silent context transfer.

For supported `SAVE dex` checks only, current Dodge supplies advantage. Requested disadvantage cancels it; requested advantage does not add dice. Ability checks, skills and other saves retain their existing mode. DM-requested player rolls, player character checks and DM adjudication share this service path. Existing unsupported proficiency/provenance checks remain unsupported.

Accepted result retrieval occurs before recalculating current effects and returns the same immutable dice/mode/total after expiry or encounter closure, subject to existing current authorization. Secret results remain absent from unauthorized acknowledgements and history. No extra manual-dice broadcast is introduced. D1 result version 1 and its executor provenance remain unchanged; its encounter context now describes the selected bound subject. No campaign/library save schema changed.

## Battlefield presentation

The compact action bar adds Dodge, with its normal-action cost and disabled-state explanation. One explicit click sends one operation through the existing pending-request protection. The authorized direct acknowledgement produces a result toast/history entry. The shared encounter status remains generic so it does not disclose private Dodge state.

Authorized controller/DM actor fields include a restrained DODGING label in the existing actor header, world label and selected-actor heading. Other viewers do not receive that field. Expiry updates the snapshot/revision and indicator. Move, End Turn, Dash, attack previews, camera and DM Workspace paths remain intact. No new full-screen menu or automatic mutation retry was added.

## Validation

Java 21 compile/foundation checks and final full regression completed successfully:

```bash
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

- BUILD SUCCESSFUL in 1 minute 16 seconds.
- 38,520 foundation assertions across 44 suites (H: 38,435).
- 160/160 GameTests (H: 151), zero failures, errors or skips.
- 2,748 catalog checks + 15,115 coverage checks = 17,863.
- Existing J2/H combat, Tactical/Generic D20, character, DM Workspace and dice regressions remain enabled and passing.

Evidence: `build/j25i-targeted.log`, `build/j25i-delivery-validation.log`, `build/gametest-results.xml`. Development caught an incorrect test accessor and an invalid feature identifier; both fixtures were corrected, with their assertions retained. All existing required suites remain enabled. Existing deprecated test-API/Gradle warnings and the optional authentication public-key DNS warning are distinct from test outcomes.

Nine new GameTests cover lifecycle/independence, action exclusion and movement, committed disadvantaged attacks, confirmation-time sight changes, zero-speed/unavailable actors, conservative recorded-condition and sleep handling, private indicators, replay/stale requests/revocation, bound-NPC Dexterity saves and privacy, and real J2 final-commit failure/recovery retaining Dodge and dice. The existing prepared-transaction fault test also rejects Dodge. Added foundation coverage checks mode cancellation, deterministic DM-requested private saves, accepted-result reuse, actor-association changes before RNG, pending UI gating, and eight-button layout bounds.

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `fda9ae67cb27dcd65830cc3ddfd1ad6b4d79d49537d9e48accdd94a95a65ca3e`.

## Minecraft acceptance checklist

GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED.

No graphical display/Xvfb is available. F-A/B, G and H graphical acceptance remains pending as well. Automated layout tests do not establish mouse interaction, label readability or visual approval.

Use matching client/server builds and a separate test world:

1. Start a controlled encounter with two approved bound actors; verify Dash, camera, picking and ordinary movement still work.
2. Dodge on the first actor: confirm action spent, movement unchanged, clear acknowledgement and authorized DODGING indicator. Attack and Dash should be unavailable; movement and End Turn should work.
3. End the turn. Verify the first actor still shows Dodge while the second actor acts. Preview/confirm an attack against a visible Dodging defender; expect disadvantage unless an existing advantage cancels it.
4. Start the defender's next turn and verify expiry/action reset. Test multiple independent Dodging actors and the same manager account controlling a prepared NPC.
5. Through existing Rolls/character-check controls, prepare and execute a Dexterity save for the Dodging bound subject. Check advantage, requested-disadvantage cancellation, unaffected other checks, and fixed retrieval after expiry.
6. Compare DM-only results and actor indicators with an ordinary player's account. Verify private outcomes/conditions are absent and revoked control cannot act.
7. Verify conservative recorded-condition rejection, pending-request handling, GUI scales 2/3/4, return through DM Workspace, and existing split movement/weapon feedback. Storage failures are covered by automated temporary-world tests; do not corrupt a real save.

## Changed files

- `docs/gameplay-srd-dodge-action-checkpoint-j25i.md`
- `src/gametest/java/dev/tacticalcombat/combat/GameplayTransactionGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdDodgeGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/checks/CharacterChecks.java`
- `src/main/java/dev/tacticalcombat/checks/CheckGateway.java`
- `src/main/java/dev/tacticalcombat/checks/CheckService.java`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldActions.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/combat/SrdTargeting.java`
- `src/test/java/dev/tacticalcombat/checks/CharacterCheckTests.java`
- `src/test/java/dev/tacticalcombat/client/SrdBattlefieldUiTests.java`
