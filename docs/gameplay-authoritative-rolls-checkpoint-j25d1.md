# J2.5D1 — Authoritative character checks

Branch: `dev/gameplay-authoritative-rolls-checkpoint-j25d1` in `shayanzas/Cubetable`.
Base: `577c79dc26eefc85580425f73a228f9e8b18aecb` (J2.5 A–C).
Final delivery commit: the commit containing this report, resolved by `git log -1 --format=%H -- docs/gameplay-authoritative-rolls-checkpoint-j25d1.md` on this branch. The final delivery message supplies the exact hash; a commit cannot contain its own hash.

## Scope and audit

D1 adds ordinary server-calculated ability, skill and saving-throw checks, player request responses, DM group requests and DM-adjudicated rolls. Existing manual dice remain a distinct operation with their existing user-entered modifiers and nearby/encounter audience. No HP, resource, action budget, movement, class effect, spell, equipment or saved-stage execution is added. J2.5 graphical behavior is **not approved**; graphical testing was unavailable here.

Reviewed the I architecture/roadmap, J2 transaction report, J2.5 report and D handoff against the actual repository. `DiceService`/`DiceRequestPayload` accept bounded manual modifiers, generate server dice and use `DiceRollPayload`, `DiceAnimation` and authorized manual events. These paths are unchanged. `CharacterRules`/`DerivedRulesState`/`RulesReadModel` provide J1 effective abilities and proficiency from combat level. `DashboardModel.checks` merges informational grants/adjustments and therefore is **not** a mechanical input. `CreationRules` stores guided class choices separately from raw historical grants. Repository approval, membership, assignment and binding are rechecked by the new service. `SrdNetworking` and J2 resource transactions remain independent.

The baseline full Java 21 command passed before changes: 16,948 foundation assertions / 37 suites, 135 GameTests and 2,614 catalog checks. No repository setup changes or new runtime dependencies were needed. Existing Java 21 and Gradle configuration were reused.

## Verified modifier sources and limitations

`CharacterChecks` defines six ability keys (`str`, `dex`, `con`, `int`, `wis`, `cha`), the 18 named SRD skills and six saves. The server resolves the associated ability through J1. Ordinary ability checks apply only that effective modifier. Skills/saves require:

- Approved SRD v1 campaign character and current owner/controller membership.
- Existing `ui_creation` enrollment, a recognized SRD class, complete valid guided class skills/Expertise and equal recorded/effective levels.
- No ambiguous recorded multiclass `classes` entries.
- No raw historical skill/save/Expertise grant matching the requested check, and no raw check-adjustment entries. Historical/custom data are preserved, never converted or added to the calculation.

Eligible class skill selections and class saving throws add J1 proficiency once. Valid Rogue/Bard Expertise doubles proficiency once and requires a verified class skill, using existing validated level/count restrictions. Expertise in historical/background skills or tools is not promoted. Nonproficient checks on an otherwise eligible record use the ability modifier only. A recognized class name alone is insufficient. Incomplete higher-level choices, including a Bard needing a fourth verified Expertise selection beyond its three starting class skills, may require unsupported background/progression provenance and remain unavailable until that provenance is established.

Unsupported checks explain the reason and leave ordinary ability checks and separately labeled Manual Dice available. No guessed skill bonus is substituted. These are ordinary checks with explicit requested normal/advantage/disadvantage, not a complete effect evaluator: racial traits, Jack of All Trades, Reliable Talent, conditions/exhaustion, equipment penalties/bonuses, passive checks and custom formulas are not automatically applied. The DM must adjudicate those situations separately. Death saves and ability substitution for a skill are outside D1. A natural 20/1 does not automatically succeed/fail an ordinary check.

Rules were checked against the user's uploaded SRD 5.1, including Bard Expertise (p.13) and the ability/proficiency/check chapters (pp.76–83), with the existing class-choice tables retained. The catalog/resources and existing CC BY 4.0 attribution are unchanged. The previously documented independent official CC-edition comparison limitation remains; no new corpus or 2024 rules were imported.

## Execution architecture

`CHECK_OPTIONS` returns current authorized availability and validated modifier breakdowns. `CHECK_PREPARE` constructs an immutable typed server ticket after authentication, selected-campaign, approval, controller/DM, binding, build-revision and rules checks. Preparation generates **no dice**. `CHECK_EXECUTE` accepts only the server operation UUID; any extra client modifier, face, result, DC or check parameter is rejected by exact-field decoding.

Execution rechecks current permissions, original controller/binding and requesting DM authority, expected build/runtime revisions and relevant executor encounter context. It uses server `SecureRandom` D20 faces, chooses the retained index, and calculates the total from J1 plus the validated proficiency multiplier. Normal uses one die; advantage/disadvantage use two with one kept. A server-owned deterministic RNG seam supports tests. The result is stored before presentation and reused on retries. Accepted retries recheck authority but do not recompute old totals from a subsequently changed character.

No campaign/library schema changes or persistence writes are made by this service. J2's 1,024 permanently retained resource receipts are untouched. Tests verify identical campaign bytes/state/resources/receipt counts across checks. Any future check that applies HP, resources or effects must use a separately reviewed J2 transaction extension.

## DM requests and player UI

Quick Sheet → Abilities, Skills & Saves uses the existing screen, now showing server-validated availability. Selecting a check opens its calculation and normal/advantage/disadvantage choices. A prepared operation ID survives refresh/resize and supports **Retrieve / retry the same operation**. An accepted response offers an explicit new check. Ambiguous replies must be reconciled through the inbox; no automatic reroll occurs. Quick Sheet also links directly to **Roll Requests & Results**.

DM Workspace → Rolls → Character Checks & DM Requests supports:

- One or up to 16 explicitly selected online, approved, currently bound player characters; Select All rejects sets larger than 16 rather than silently truncating the group.
- Ability/skill/save choice, mode, optional plain-text purpose (80 characters), visibility and optional hidden DC (0–100).
- Individual per-character IDs and a shared group ID; partial completion and dismissal are visible in the inbox.
- Explicit DM-adjudicated preparation/execution for any eligible approved campaign character, including NPCs and unbound characters. This mode is visibly distinct from a player submission and requires current DM authority.

Creation validates the entire requested group before creating any tickets. Offline/unbound targets are rejected; no queued offline delivery is promised. A new request sends a generic notification directing the recipient to the inbox. The notification contains no dice/modifiers/DC. Inbox polling and stable IDs prevent repeated prompts. Execution rechecks current access, including controller or DM revocation. Disconnect does not erase tickets or rate limits; a reconnecting authorized player can answer within the lifetime. Dismissal generates no outcome and cannot reopen that operation; accepted results cannot be dismissed to reroll.

Identical DM requests (creator, campaign, target set, check, mode, purpose, visibility, recipient set and DC) reuse their group for the full ten-minute lifetime, **including completed/dismissed groups**. This conservatively protects a lost group acknowledgement. A deliberate new situation within that lifetime needs a distinct purpose; the UI explains this. A changed character/revoked authority can invalidate an outstanding group and require a newly described request.

## Privacy and audit

Player-initiated checks are private to that player and current campaign DMs. DM rolls/requests choose Public Campaign, DM-only, or Specific Recipients (up to 16 current members, plus current DMs). The source player is not implicitly a specific recipient. A player answering a DM-only request receives an accepted acknowledgement without faces, modifier or total.

DC and success/failure are always DM-only in D1, even when the numerical roll is public. Non-DM JSON omits both fields entirely and strips hidden DC/recipient metadata from pending tickets. There is no public-DC option yet. Never put a secret DC into the player-facing purpose text.

Results are delivered only through authenticated, filtered UI replies and the filtered `CHARACTER_CHECK` event history. They never enter the manual `DiceRollPayload` broadcast. Events contain the same server faces/kept indices/modifier/total, identified by operation ID and provenance, but no DC/success. Additional live event gates recheck current membership, controller/binding and requesting-DM authority; eviction of event history does not erase the operation ledger. Audit/history are bounded **session memory**, not durable records or external logs.

New private panels reuse opaque permission-invalidating watches. Campaign switching now invalidates watches consistently, including ordinary-player check panels. Invalidation clears rows and stale callbacks. The existing HUD has its existing bounded permission/history refresh lifecycle; previously authorized information cannot be erased from an adversarial client's memory. No future unauthorized delivery is authorized by a historical permission decision.

## Identity, bounds and restart policy

- Server-issued random operation IDs; clients cannot provide IDs to mint or resurrect a ticket. Spec/participants are immutable records with copied collections.
- At most **2,048 tickets** across the session, **16 targets/group**, **16 specific recipients**, **20 new preparations/groups per authenticated actor per rolling minute**. Existing transport limits also apply. Duplicate pending preparation/group retrieval does not consume another slot. Each target consumes one ticket.
- Tickets/results/dismissals live **10 minutes from creation**. Full capacity rejects new work without evicting a live outcome. Expiry removes records, but execution of the old ID remains an error because unknown IDs can never execute. New deliberate checks receive new server IDs.
- Inbox responses contain at most eight entries/page; the DM target picker lists at most 128 approved candidates, and specific-recipient selection lists at most 128 online members. No full character sheet appears in a result.
- Server restart discards requests/results; it does not reconstruct an encounter or replay a roll. Old execution IDs return unavailable/expired and do not draw dice. This is tested with a new service/repository instance and gateway session reset. D1 does **not** claim durable roll-only recovery or global exactly-once behavior across restart/backup rollback. J2's separate durable resource guarantees remain unchanged and its real-file/abrupt-process tests still run.

## Versioned result contract for cinematic presentation

`CheckService.Result` version **1** contains operation/campaign/character/authenticated actor IDs; optional executor encounter ID; typed category/key; rules adapter `srd51_checks_v1` and projection version; purpose/visibility/provenance; sides=20; immutable faces and kept zero-based indices; mode; validated ability/proficiency/multiplier/source/total breakdown; final total; and permission-filtered optional DC/success.

Provenance is `PLAYER_CHARACTER_CHECK`, `DM_REQUEST` (answered by its authenticated player) or `DM_ADJUDICATION`. Existing manual rolls retain their separate manual contract. Future animation must consume these final faces/indices/total and must not calculate a new outcome. Encounter context is informational executor context; a request is not a combat action or a target-turn entitlement. Results do not change HP or spend actions even on success.

## Automated evidence

Final command (Java 21, existing proxy/trust configuration):

```bash
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog
```

Final result: **BUILD SUCCESSFUL in 1m 15s**, 2026-10-17 UTC.

- **19,239 foundation assertions across 38 suites**: the complete 16,948 / 37 baseline plus 2,291 new checks.
- **139/139 required GameTests passed**, zero failures/errors: all 135 baseline tests plus four new integration tests.
- **2,614 catalog validation checks / 623 entries**, unchanged. The separate repeated catalog task is not double-counted in the foundation total.
- Full J2 filesystem failure, child-JVM abrupt-exit, replay, receipt/resource and movement tests remain enabled and passed.
- Evidence: `/tmp/d1-baseline.log`, `/tmp/d1-final-validation.log`, `build/gametest-results.xml`, `build/reports/srd51-catalog.json`. `git diff --check` is clean. The release JAR includes the check service and excludes GameTest classes.
- Expected existing mock-player deprecation and Minecraft public-key-host warnings did not prevent the test server completing. No graphical client or real-world installation was performed.

New coverage includes all six abilities, all skill/save mappings, twelve class proficiency/save selections, eligible Rogue/Bard Expertise and level thresholds, negative modifiers, historical/ambiguous grants, deterministic advantage/disadvantage, server-only RNG, forged fields, wrong actors, stale revisions/context, individual/group requests, partial completion, offline targets, dismissal/expiry, requesting-DM and controller revocation, private/public/targeted visibility, hidden DCs, fixed retries, group-ack loss, event deduplication, reconnect identity, actual 2,048-ticket capacity and post-expiry replay rejection. Live tests exercise the authenticated UI dispatch, bounded server faces, absence of manual broadcast packets, private serialized responses/events, runtime revision changes and panel invalidation.

No baseline suite/assertion was removed or weakened. Early validation identified an incomplete high-level Bard test fixture (the test now asserts its unsupported provenance) and serialization of redacted null fields (the wire format now omits them). Full automated evidence does not establish graphical acceptance.

## Minecraft manual checklist

Use matching client/server JARs on a disposable test world/copy, with a coherent backup. Do not install into the primary world before review.

1. Select the existing campaign. Keep an approved bound player character and separately approved bound Zombie/NPC. Verify the J2.5 launcher, safe alignment, initiative, attacks, movement, ammunition/HP, end encounter, Quick Sheet and DM tabs still behave as before.
2. Press J → Abilities, Skills & Saves. Compare an ability modifier with the full J1 projection. Roll Normal, Advantage and Disadvantage; inspect actual faces, kept index, modifier and final total.
3. On a valid guided class character, check a class skill, untrained skill and class save. On a valid Rogue/Bard, verify selected class-skill Expertise is doubled once. On a historical/custom/level-mismatched character, confirm explanatory unavailable skill/save rows and working ordinary ability/manual alternatives.
4. Refresh/retrieve an accepted ID from the check screen or inbox. Verify the same faces/total and one event. Start a new deliberate check only after reviewing the previous result. No HP, stock, receipt or movement usage should change.
5. DM Workspace → Rolls → Character Checks & DM Requests. Select one player, then a group. Choose check/mode/purpose and send. Recipients see one generic notification and can answer through Roll Requests & Results. Confirm partial completion and safe dismissal.
6. Test all three visibility modes with a DM, source player and another player. DM-only must show the source player only an accepted acknowledgement; Specific Recipients must exclude unselected players, even the source. Public rolls show the same total to members. Only DMs see DC/success, including in pending request/result JSON; event history contains no DC/success.
7. Repeat an identical group request after completion: it must reuse the old group within ten minutes. Change the purpose explicitly for a new situation. Let a request expire and confirm its old operation cannot roll.
8. Reconnect within ten minutes and inspect existing requests/results. Revoke the requesting DM or change character control/binding and confirm old requests/retries are denied and private panels clear/revalidate. Switch selected campaign with a panel open.
9. Restart the disposable server. Old roll-only requests/results are unavailable, with no automatic reroll. Previously committed J2 HP/ammunition remain saved. Do not corrupt saves; automated J2 fault tests cover storage failures.
10. Smoke-test V/manual dice, manual event animation, J2 transaction review, G/H creator/library management, Tactical characters, reference browsing, narrow GUI scales, Escape/return paths and text focus. Report graphical/multiplayer results before the next checkpoint.

## Artifact and delivery constraints

JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `fab5ef76f0ade5c65ca5b2ab9eaa01e2c96fa7cd078b176c68ff26375ccbfd28`.

No merge, force-push, upstream changes, primary-world installation, character conversion or unrelated migration. Library schema 2, campaign schema 5, SRD v1 and all catalog resources remain intact. Before any real upgrade, retain a coherent world backup; downgrading to a pre-schema-5 binary still requires the matching pre-upgrade backup, not merely replacing the JAR. Cinematic dice, saved stages, monster activation, resource-changing checks and unsupported grant/effect mechanics remain deferred.

## Complete files changed

- `docs/gameplay-authoritative-rolls-checkpoint-j25d1.md`
- `src/gametest/java/dev/tacticalcombat/combat/CharacterCheckGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/checks/CharacterChecks.java`
- `src/main/java/dev/tacticalcombat/checks/CheckGateway.java`
- `src/main/java/dev/tacticalcombat/checks/CheckService.java`
- `src/main/java/dev/tacticalcombat/client/ui/CharacterRollMenuScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CheckInboxScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CheckRollScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CheckScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DmCheckScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DmWorkspaceScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/QuickPanelScreen.java`
- `src/main/java/dev/tacticalcombat/combat/EncounterLauncher.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiEvents.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/checks/CharacterCheckTests.java`
