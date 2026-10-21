# J2.5L-A — Reactions, opportunity attacks and Disengage

- Verified local and remote base: `dev/gameplay-srd-dm-workspace-redesign-j25k-d`, `1e407a45987932dd70f192d2748d23f73427e281`.
- Delivery branch: `dev/gameplay-srd-reactions-opportunity-attacks-j25l-a`.
- Tested implementation commit: `6945b0ef6fc493ed55f84fb56282a0b3d678740a`.
- This report is a documentation-only child of the implementation. The delivery response and local `build/j25la-delivery-receipt.txt` record the verified final remote HEAD.

## Implemented rules and lifecycle

The 2014 SRD interpretation is one reaction per creature, initially available, refreshed only at the beginning of that creature's own turn. `SrdReactionBudget` is independent of the current actor's `SrdActionBudget`: AVAILABLE → RESERVED → SPENT. Decline and invalid requests never spend it. The existing normal-action budget's unsupported-reaction slot is not reused as a participant budget.

Disengage spends the active creature's normal action, grants no distance and spends no reaction. Its protection covers subsequent split movement during that turn and expires at turn end. Admission checks the authenticated controller/manager, selected campaign at the gateway, encounter/turn/request identity, actor and reviewed revision. Executing routes, unresolved transactions, spent actions and zero HP block it.

Opportunity attacks are one supported melee attack, never a ranged attack or Multiattack. Existing encounters remain free-for-all: all other admitted participants are potential hostile reactors. A validated voluntary walking step provokes only when it crosses from inside a supported melee attack's reach to outside it. The authoritative attack plan supplies reach; it is not hard-coded to five feet. Geometry uses the existing logical-footprint, Chebyshev grid distance and elevation conventions. Entering reach and moving within it do not provoke. Dash and crawling provoke; Disengage suppresses movement opportunities. External teleports do not invoke this voluntary movement hook, and unauthorized displacement retains existing reconciliation behavior.

Sight uses server collision/cover sampling plus Minecraft combatant sight, invisibility, sleep, blindness and darkness checks. Camera fading is never consulted. Positive D&D HP, actual participant/binding, current snapshots, available reaction and an approved melee option are required. Any descriptive CONDITION or TEMPORARY feature conservatively makes reaction eligibility unavailable for adjudication; it is not parsed into a new condition engine. Zero walking speed alone does not remove the ability to react. Existing monster and special-sense limitations remain.

## Movement interruption and bounded decisions

`SrdMovementTurn` invokes the departure hook after validating the entire remaining route and its costs, but before changing position or charging the next step. Eligible candidates are captured at that departure in authoritative, tie-resolved initiative order. At most 15 other participants can be queued; one decision is exposed at a time. Each candidate's eligibility and melee options are checked again before its offer and at confirmation. The actor already considered for that departure is not offered it twice.

Each decision has a fixed deadline of 400 encounter server ticks, nominally 20 seconds. Previewing does not extend it. Expiration acts as decline, not an attack, with no reaction expenditure. Client countdowns are approximate presentation only. A participant disconnect retains the existing controlled-encounter termination policy; an absent/unresponsive nonparticipant manager cannot hold a decision beyond the deadline. Explicit DM termination remains available.

The mover remains at the pre-departure anchor while decisions resolve. Earlier steps remain charged; the interrupted step is uncharged. Decline, miss or a surviving hit allows the next server tick to revalidate terrain, occupancy, authorization, movement resources and the remaining route before progressing. A zero-HP hit halts the remaining route and uses the existing DM-adjudication pause. A persistence failure pauses with its transaction retained. No automatic mutation retry occurs.

## Attack transactions, protocol and replay

The new protocol operations are `disengage`, `reaction_preview`, `reaction_confirm` and `reaction_decline`. Their bounded payloads contain only intent and expected scope. Reaction admission authenticates control over the **reactor**, independently of active-turn control. Scope includes campaign, encounter, mover/turn, reactor, trigger ID, reviewed revision, the still-pending departure, deadline and the server-issued intent. Preview identity/revision, positions, participant snapshots, geometry, visibility, available reaction and choice are rechecked before RNG. Old, replaced, timed-out or duplicate confirmations cannot spend again.

Both ordinary and opportunity attacks use the same attack planner, RNG, damage calculation, `GameplayActionPlan`, atomic preparation/commit and result privacy pathways. Prone, Dodge, advantage/disadvantage cancellation, cover, resistance, immunity, vulnerability and supported resource handling are inherited from that shared path. Reaction reservations never reserve or spend the mover's normal action. The active mover's movement snapshot is reconciled after a durable reaction changes its HP; distance already spent is retained.

Campaign schema remains **5**, library schema **2**, receipt envelope version **1**. Gameplay **plan version 1** remains a normal-action attack; **plan version 2** records a melee reaction attack with action cost 0 and reaction cost 1. Version participates in the canonical fingerprint. Version 2 has no new arbitrary fields, formulas or effects. Strict decoding supports both versions and rejects unknown versions/fields. Existing normal receipts remain readable.

PREPARED records retain exact fixed outcomes and reserve both affected character records without applying resources. COMMITTED writes both participant runtime changes and the receipt in the same campaign JSON replacement. A preparation failure retains dice only in the live encounter; it does not claim durable crash recovery. A final-commit failure retains the durable prepared record. Existing explicit DM retry uses the same plan/dice and rechecks current authorization and records. Post-commit presentation failures never authorize a second spend.

Duplicate reaction confirmations are rejected. The existing authenticated `confirm` receipt lookup, supplied the original intent ID, can retrieve the accepted operation without another execution; the client never resubmits it automatically. Existing receipt limits remain 1,024 total and 64 prepared, with no unsafe eviction. Replayed result flags and existing event/effect deduplication prevent replayed native swings or dice presentations. There is no global exactly-once guarantee after administrator rollback to an older backup.

## Privacy, UI and presentation

Only authorized controllers/managers receive a reactor's budget, choices and prompt. Result dice go through the existing acting-player/manager audience; target HP remains separately permission-filtered. Permission changes invalidate private client previews and server mutations. An ordinary participant does not gain NPC control by being in the encounter.

The battlefield action bar places Disengage beside Dash/Dodge, retains Prone/Stand and End Turn, and uses two rows below 700 logical pixels. Disengage status is shown for authorized current actors. End Turn is disabled during an offered reaction. The compact, translucent reaction panel can appear over an existing screen and identifies both reactor and mover. Selecting a melee option deliberately requests a preview; Confirm explicitly spends the reaction; Decline preserves it. Back only dismisses presentation, and the battlefield React control reopens the pending decision. Rendering, resizing, refresh and reopening never submit an attack. Buttons capture reviewed revision/trigger to reject stale UI callbacks.

The tactical camera continues following the mover. Authorized remaining route squares stay visible while movement is interrupted. The status panel explains the wait. Reaction results use the established authorized roll/HP feedback and committed-attack swing/hit indicators, labeled as reaction use. The hotbar remains suppressed during this tactical reaction presentation and restores through the existing encounter lifecycle. No custom animation engine or additional art/audio assets were added.

## Validation and failure evidence

Final command (Java 21, configured network proxy and Gradle cache):

```bash
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

- **46,246 foundation assertions across 49 suites**; baseline 45,775 / 48 retained.
- **197/197 required GameTests passed**; baseline 183 retained, 14 reaction integration tests added.
- **17,863 catalog/coverage checks**: 2,748 catalog checks plus 15,115 completion checks, 1,806 entries.
- Java 21 build: **SUCCESSFUL**.
- Final validation log: `build/j25la-validation.log`.
- No tests deleted, disabled or weakened. `validateAccessWidener` reports its existing `NO-SOURCE`; graphical client tests were unavailable. Existing deprecation warnings and the headless Minecraft public-key-service DNS warning did not fail validation.

New deterministic coverage includes independent budgets and transitions; four reach boundaries; bounded action-bar layouts and pending controls; version-1/version-2 cost/fingerprint separation; invalid ranged reaction plans; prepared/committed reload and replay; departure before cost; terrain changes; miss/hit/zero HP; spent-reaction repeat departure; own-turn refresh; timeout; deterministic multiple reactors; split Disengage movement/expiry; extended reach; diagonal/elevated movement; visibility and recorded-condition restrictions; Dash/crawling; Dodge/Prone cancellation; stale/replaced previews; revocation and private views; and real mock-server-player/prepared-Husk control in both attacking roles.

Two real atomic replacement seams are exercised: failure before primary preparation replacement and failure before final commit after durable preparation. Tests verify unchanged HP/position, RESERVED rather than phantom SPENT state, unchanged RNG count across failures, reloaded PREPARED version-2 receipts, reservation exclusion after restart, exact-once recovery commitment, and subsequent legal movement. A prepared Husk's reviewed Slam commits damage through the same repository transaction while leaving the moving player's normal action intact.

The first integration run exposed a late-grid-response access after encounter cleanup; the final implementation omits cleared reaction metadata safely. The existing K-A/F movement scenario now explicitly declines the newly legitimate Husk opportunity before continuing; all prior movement, turn, HP and controller assertions are retained, with an additional assertion identifying the reactor. Subsequent full runs passed.

## Restart, backup and supported limits

No complete encounter, movement route or reaction decision is reconstructed on restart. Committed HP/resources remain committed. Durable unresolved operations retain fixed outcomes for existing DM recovery/review; they do not automatically execute after restart. Reactions and Disengage are encounter-local.

Back up the **entire stopped test world**, including all campaign JSON and backup files, before upgrading. Install matching client/server binaries in a separate test instance first. Although campaign schema remains 5, older binaries reject plan version 2 once such a receipt exists. **Rolling back the JAR alone is insufficient: restore a coherent pre-upgrade world backup.** No user's primary world was opened or migrated. Atomicity covers the existing campaign JSON replacement, not Minecraft entity/world saves.

Special senses, tabletop lighting, executable incapacitation/status systems and undocumented reach modifiers are not invented. This retains conservative sight and descriptive-condition boundaries. No Sentinel, Polearm Master, War Caster, Ready, Counterspell, spell reactions, legendary actions, factions, AI, forced-movement mechanics, grappling/shoving or death saves were added. Declines do not reserve a future reaction; a later distinct qualifying departure may offer that still-available reaction again. A sequence of 15 unanswered offers can wait up to 300 seconds in server ticks; each individual offer is bounded and a DM can end the encounter.

The prompt is a compact modal presentation while open, not unrestricted world input through its buttons. Camera focus remains on the mover. Very small viewport text is trimmed with preview details also in the confirmation tooltip; actual readability, mouse behavior, native rendering and GUI scales require Minecraft review.

## Minecraft acceptance checklist

**J2.5L-A GRAPHICAL MINECRAFT ACCEPTANCE: NOT VERIFIED**

No graphical display or Xvfb client was available. Automated geometry, UI-layout and server tests do not establish visual acceptance.

1. In a backed-up test world, use the existing GUI to start an encounter with the approved player and a prepared Husk. Select the same campaign and use a manager account authorized for both.
2. Move away from an adjacent actor without Disengage. Verify movement pauses before leaving reach, identifies reactor/mover and shows the remaining route. Decline; verify the unexecuted step then proceeds and costs movement once.
3. On another qualifying departure, select the offered melee option, review and explicitly confirm. Verify hit/miss, roll mode, damage/authorized HP, native effect and reaction spending. A second departure before that reactor's next turn must not spend another reaction.
4. Test a Husk reacting with its supported Slam and a player reacting during the Husk's turn. Confirm the same manager can operate both while an ordinary account cannot control the NPC.
5. Use Disengage on a fresh turn. Verify normal action spent, movement unchanged, multiple split moves protected, and protection gone after End Turn.
6. Use two potential reactors. Verify initiative-ordered decisions, no departure between unresolved offers, and timeout declines without an automatic attack. Close/reopen the prompt and resize without submitting anything.
7. Check Dash, crawling/standing, Dodge, split movement and player/Husk walking. Confirm a zero-HP reaction hit stops the route and exposes existing DM recovery, without automatic death or movement.
8. Check GUI scales 2, 3 and 4, long names, narrow windows, interaction with DM Workspace, pending/rejection feedback, result history and hotbar/camera restoration after encounter end.
9. End combat and reopen campaign characters; restart the test server and verify committed HP persists with no automatic encounter/reaction resumption. Use automated fault tests rather than damaging a save manually.

## Artifact

- JAR: `/workspace/Cubetable/build/libs/tactical-combat-0.1.0.jar`
- SHA-256: `e534501458a4938334b22d60d4ae6a75fb60324499bf23d6573022939df30989`

## Files changed

- `src/gametest/java/dev/tacticalcombat/combat/SrdReactionGameTests.java`
- `src/gametest/java/dev/tacticalcombat/combat/SrdWorldInteractionGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java`
- `src/main/java/dev/tacticalcombat/character/GameplayActionPlan.java`
- `src/main/java/dev/tacticalcombat/client/ClientSrdCombat.java`
- `src/main/java/dev/tacticalcombat/client/SrdAttackFeedback.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldActions.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldPresentation.java`
- `src/main/java/dev/tacticalcombat/client/SrdBattlefieldScreen.java`
- `src/main/java/dev/tacticalcombat/client/SrdGridRenderer.java`
- `src/main/java/dev/tacticalcombat/client/SrdReactionScreen.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`
- `src/main/java/dev/tacticalcombat/combat/SrdNetworking.java`
- `src/main/java/dev/tacticalcombat/combat/SrdReactionBudget.java`
- `src/main/java/dev/tacticalcombat/combat/SrdTargeting.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/character/GameplayTransactionTests.java`
- `src/test/java/dev/tacticalcombat/combat/SrdReactionTests.java`
- `docs/gameplay-srd-reactions-opportunity-attacks-checkpoint-j25l-a.md`
