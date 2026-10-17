# J2.5D2 — SRD monster preparation and saved encounter stages

Base: `9da783fd05aca033f55593ce39bd5432c671a827`, from `dev/gameplay-cinematic-dice-checkpoint-j25e`.
Delivery branch: `dev/gameplay-monster-stages-checkpoint-j25d2` in `shayanzas/Cubetable`. No upstream changes, merge, force-push, or primary-world installation.

## Scope and support

This checkpoint adds DM preparation of independent, approved, bound campaign NPCs from three reviewed catalog entries, and reusable stage templates with explicit validated placement. It extends the existing SRD engine and J2 transactions. There is no monster AI, new spell/class system, equipment-instance system, or automatic encounter restoration.

| Source | Verified statistics | Executable basic actions | Explicit limitations |
| --- | --- | --- | --- |
| `srd51:monsters:zombie` | HP 22, AC 8, speed 20 ft, Medium; STR/DEX/CON/INT/WIS/CHA 13/6/16/3/6/5; Wisdom save +0; poison damage immunity | Slam, +3, reach 5 ft, 1d6+1 bludgeoning | Undead Fortitude, poisoned-condition immunity, senses and languages require adjudication. Reaching 0 HP does not automatically trigger survival. |
| `srd51:monsters:skeleton` | HP 13, AC 13, speed 30 ft, Medium; 10/14/15/6/8/5; poison damage immunity; bludgeoning vulnerability | Shortsword +4, reach 5 ft, 1d6+2 piercing; Shortbow +4, range 80/320 ft, 1d6+2 piercing | Condition immunities and senses remain manual. Preparation explicitly provisions 20 virtual arrows; that quantity is a preparation policy, not an inferred stat-block inventory. |
| `srd51:monsters:goblin` | HP 7, AC 15, speed 30 ft, Small; 8/14/10/10/8/8; Stealth +6 | Scimitar +4, reach 5 ft, 1d6+2 slashing | Nimble Escape is not executable. Shortbow is deferred because shield removal/loadout actions are not implemented. Senses remain manual. |

All three are **partially supported**, not complete SRD implementations. Support notes appear during preparation, in nearby actor discovery and in rules provenance. Ordinary ability and saving-throw modifiers use the supplied scores; reviewed extra proficiency applies only to Zombie Wisdom saves and Goblin Stealth. Use D1's DM-adjudicated checks for NPC checks.

`MonsterProfiles` contains reviewed typed adapters. Runtime validation checks structured catalog fields and verified-source status; it does not parse descriptive rules text into actions. Tests freeze the catalog action text and compare the typed attack calculations and defenses against it. The existing SRD catalog attribution and CC BY 4.0 licensing remain applicable; no external artwork or audio was added.

## NPC identity, ownership and persistence

The campaign character is an ordinary SRD-profile record with an explicit `collections.srd_monster` version-1 identity: `kind`, source ID, catalog version, preparation operation UUID and original actor UUID. The record has no player-class identity. Internal effective level 1 supplies the reviewed +2 proficiency through the existing calculator; provenance labels this as a monster adapter, not a player class. Fixed AC is a source-backed typed adjustment, not inferred equipment ownership.

The authenticated preparing DM owns and initially controls the record. Controller offers are restricted to campaign DMs and rechecked on acceptance. Monster records cannot bind to player entities. The DM chooses the D&D source separately from the Minecraft entity type; a Husk can intentionally use Zombie statistics. Compatible loaded living `MobEntity` instances are accepted by preparation; encounter admission still enforces supported movement, safe footprint, positive HP and all existing readiness checks.

Preparation creates the character, explicit approval and entity binding in **one existing atomic campaign-file replacement**. The UI confirmation authorizes all three. It does not change vanilla HP, spawn entities or move them. The operation UUID is also the persistent character UUID. Identical retries return the existing identity without resetting HP, ammunition, approval or binding. Altered source/actor/owner/campaign under the same ID is rejected. A new operation cannot bind an already-bound actor. On an ambiguous acknowledgement, refresh Nearby Actors to reconcile the existing binding; do not create another NPC blindly.

DM authority, actual campaign membership, selected SRD campaign, entity loading/liveness, distance (32 blocks), binding and combat claims are rechecked. Existing records and personal library entries are never overwritten. Source identity and static combat statistics cannot be edited into a different monster; HP, temporary HP and existing bounded virtual ammunition remain runtime resources. Generic submission cannot inject a source-owned monster marker.

## Combat and transaction guarantees

Slam and Scimitar extend the trusted weapon definitions; original weapon enum ordinals are retained. Monster attack requests must use an allowed reviewed action and its reviewed ability. The client receives that ability for presentation; it does not supply authoritative modifiers or damage. Monster unarmed fallbacks are excluded. Skeleton bows retain existing two-handed/ranged controls and ammunition semantics.

Monster attacks use the same `GameplayActionPlan`, durable PREPARED outcome, atomic participant updates and COMMITTED receipt as existing attacks. No second journal or RNG was introduced. Current DM authority is required for monster transaction execution/recovery. Binding and unbinding cannot bypass an unresolved PREPARED reservation; audited J2 resolution/cancellation is required first. Committed HP/ammunition remain after ending combat and restart. World/entity positions are not part of campaign JSON atomicity.

## Saved stages

`tacticalcombat/encounter-stages.json` is a separate atomic, version-1 store with validated backup recovery. It does not change campaign schema 5 or library schema 2. Each template stores ID/revision, campaign, creator, name/description, dimension, bounded origin, timestamps, catalog/rules provenance and placement slots. Each slot has its own ID, role label, saved grid position, optional source-monster identity and character reference.

Bounds: 256 templates globally, 64 per campaign, 16 slots per template, 64-character names/roles, 512-character descriptions; slots lie within 16 blocks on each horizontal axis and 8 vertically of the origin. Coordinates are finite and world-bounded. Up to 2,048 deletion tombstones prevent deleted template IDs from being recreated by delayed requests; reaching the bound fails closed. AtomicJsonFile also enforces its 16 MiB file limit. Unknown store versions/fields fail closed rather than silently rolling back unfamiliar data. Corrupt files can recover the last validated backup with a server warning; both invalid copies disable stages instead of resetting them.

Every operation rechecks current campaign DM authorization. Store authorization does not rely on the creator still being a DM. Updates and deletion use revision checks. Campaign membership and stages are separate storage boundaries: stale/deleted character references remain inert until activation validation; no cross-file atomicity is claimed.

### UI workflow

1. DM Workspace → Actors → Nearby Actors & Quick Binding → Bind Character → Prepare from SRD Monster.
2. Review Minecraft identity, D&D source, statistics and support limitations. Confirm preparation, approval and binding.
3. DM Workspace → Scene → Saved encounter stages → Save new stage here. Name it and optionally describe it. The server captures the DM's current location; the request contains no coordinates.
4. Open the stage and Capture actor placements. Select currently approved actors by name and confirm. This creates/replaces slots from their current safe grid anchors without moving anyone.
5. Click a slot to edit its role label. Edit the stage name/description separately. Recapture selected actors to change placement positions or the slot set.
6. Choose actors & validate. Select in the displayed slot order; numbered selections show assignments. Source-backed monster slots accept new independent instances of the same source, so a despawned entity UUID is not required. Non-monster character slots require their recorded campaign character.
7. Review the saved positions and confirm activation. Return to the existing encounter launcher to start combat separately.

### Placement and recovery

Preview/validation issues a private, one-use token valid for 400 server ticks; at most 128 viewer previews are retained, cleared on disconnect/server stop. Confirmation rechecks authority, campaign, template revision, actor snapshots, positions and velocities, current claims/reservations and every destination. The stage origin and actors must be within 32 blocks of the DM in the same dimension; each move is at most 16 blocks. No client teleport coordinates are accepted.

Existing `SrdTerrain` checks loaded chunks, world border, support, physical collision and logical D&D footprints. All destination pairs must be separated. All actors are validated before any movement. Placement conservatively rejects occupied destinations, including swaps. Actors must remain still between preview and confirmation; movement requires a new validation.

Activation is an explicit bounded placement/teleport, not simulated walking. It does not start combat or spend movement/action budgets. If a move fails, prior positions and velocities are restored where entities are still available. Failure after the first actual move is tested. Restoration cannot be guaranteed if an entity/world disappears or restoration itself fails; the operation reports that limitation. No atomicity is claimed with Minecraft world saves, and a hard crash during placement may leave some world positions changed. Templates persist, but confirmation tokens and encounters do not automatically resume after restart.

## Compatibility and upgrade

Campaign schema 5, receipts, approval/binding state, independent campaign/library records and existing character creation remain intact. No historical inventory, class grants, AC or ammunition is converted. Existing Tactical behavior and D1/E result visibility remain covered by regression tests.

Back up the entire stopped **test world** before upgrading, including campaign JSON/backups, stage files, selections and entity/world saves. Install the matching client/server JAR on that copy. Test there first. Older binaries do not understand the new monster identity/action semantics even though the campaign schema remains 5: **rolling back only the JAR is not a supported rollback; restore the coherent pre-upgrade world backup too**. Backup rollback can restore old template IDs or older gameplay receipts; no global exactly-once guarantee across administrator rollback is claimed.

## Automated validation and evidence

Final validation passed on Java 21: **21,606 foundation assertions across 40 suites, all 143 GameTests, and 2,614 catalog validation checks**. The full required command completed successfully in 1m 1s. The 60 new monster/stage foundation checks and four new GameTests are included in those totals. No existing assertions were removed or weakened. The legacy fixed weapon expectation table retains all eight original rows and adds reviewed Slam/Scimitar rows; the shared-projection matrix now also covers those actions.

New foundation coverage uses real temporary files and AtomicJsonFile failure seams: unauthorized preparation; independent identity/binding; identical/altered retry; forged static data; catalog parity; exact monster attacks/checks; HP restart persistence; failed preparation with unchanged file/records/bindings; prepared monster attack commit failure with unchanged attacker ammunition and defender HP; committed retry/restart exactly once; stage create/reload/CAS/duplicate slots/bounds/dimensions/DM revocation; missing files, corrupt-primary backup recovery, future versions and failed deletion.

New GameTests exercise real loaded entities: preparation/retry/revocation and encounter admission; multi-actor capture/validate/activate; injected failure after the first move and rollback; consumed-token replay; stage reload; forged coordinates; stale revisions; J2 reservation exclusion; permission revocation after preview; despawned actors; unsafe terrain; and a newly prepared source-compatible replacement using an existing saved slot.

## Minecraft acceptance checklist — not yet performed

Use a backed-up test world, selected SRD campaign, a current DM, an approved bound player and nearby unbound Husk/Zombie, Skeleton and Goblin stand-ins. A–E graphical acceptance also remains pending.

- Prepare each source through Actors without the player-character creator. Check explicit Minecraft/D&D identity, independent campaign records, approval, binding, source statistics and visible limitations. Confirm no library changes and no duplicate record on refresh after success.
- Start an encounter through the existing launcher. Resolve initiative. Control the NPC through the DM interface. Zombie: Slam, STR, melee. Skeleton: Shortsword/DEX/melee, then Shortbow/DEX/ranged/two hands. Goblin: Scimitar/DEX/melee. Inspect preview modifiers/dice before confirming.
- Verify HP and skeleton arrow spending, misses, target defenses, and ordinary player attacks. Confirm unsupported traits do not execute. End the encounter and reopen records; restart the test server and verify committed resources remain without replay.
- Check Zombie Wisdom +0 and Goblin Stealth +6 through authorized DM checks. Verify Quick Sheet, DM Quick Tools, private roll audiences and E cinematic dice still behave correctly.
- Save a named stage at your current location. Capture several actor slots, rename roles and edit the template. Confirm capture changes neither positions nor combat state.
- Move actors a short distance using your normal test setup. Select them by name in slot order, validate, then confirm activation. Verify all saved positions and that combat has not started. Start combat separately via Encounters.
- Replace a despawned source-backed actor with another prepared instance of the same monster. Match it to the old slot without UUID entry. Test wrong source, missing actor, occupied/unsafe floor, distant actor, other dimension and actor already in combat; each should explain rejection and leave all actors unmoved.
- Confirm moving after validation requires a fresh preview. Restart and verify templates survive but activation confirmations do not. Update/delete with revision-safe confirmation.
- Inspect labels, wrapping, scrolling, confirmation flow, GUI scales 2/3/4 and constrained windows. No graphical client was available here; automated tests do not establish visual approval.
- Do not corrupt a real save or interrupt the primary server for fault tests; automated injection covers those paths.

## Remaining limitations

Partial monster support is deliberate as listed above. There is no equipment loadout editor, automatic undead survival, condition-immunity engine, Nimble Escape, spawning/despawning, monster AI or cross-dimension placement. Template placement requires stationary actors during confirmation and rejects swaps. Role positions are edited by recapturing actors, not dragging arbitrary coordinates. Revision-safe stage edits require refreshing and reviewing conflicts. Graphical usability and actual rendering remain unverified.

## Delivery identity and reproduction

Final validated code/test commit: `8eb233bfd1ae0723f0e0eeae701ce26ab8f359e5`. The final delivery commit adds this report only; its exact hash is supplied in the delivery response and is the pushed branch HEAD (`git rev-parse dev/gameplay-monster-stages-checkpoint-j25d2`).

Built JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `6bc613d8f3b8b0491e210d47e9dd3c7c701013c3b3c8a89ccf6335cb00117d26`.
Local validation evidence: `build/j25d2-validation.log` and `build/gametest-results.xml` (143 cases, zero failures). Build artifacts and generated test worlds are not committed to Git.

Required command, run successfully:

```sh
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog
```

This cloud image initially supplied a Java 21 runtime without JDK launchers/release symbols. The matching Debian OpenJDK 21.0.12.1 headless JDK was downloaded from the official Debian HTTPS mirror, checked against package SHA-256 `f3abafb6c644b03df042824e707cd211ea33254761d7f7b75be3f8dc0df97c7a`, and assembled with the existing matching runtime under `/workspace/.tools/jdk21`. The build used `JAVA_HOME=/workspace/.tools/jdk21`, `GRADLE_USER_HOME=/workspace/.gradle` and this environment's HTTPS proxy/trust store. No project compiler target or test requirement was weakened. A standard complete Java 21 JDK needs no launcher workaround.

The offline headless test server logged an external Minecraft public-key lookup failure; all required tests still completed successfully. Existing deprecated mock-player/Gradle API warnings remain. There was no graphical Minecraft session.

## Files changed from the verified base

- `docs/gameplay-monster-stages-checkpoint-j25d2.md`
- `src/gametest/java/dev/tacticalcombat/combat/MonsterStageGameTests.java`
- `src/gametest/resources/fabric.mod.json`
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java`
- `src/main/java/dev/tacticalcombat/character/EncounterStageStore.java`
- `src/main/java/dev/tacticalcombat/checks/CharacterChecks.java`
- `src/main/java/dev/tacticalcombat/client/SrdCombatScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/ActorBindingScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DmWorkspaceScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/EncounterLauncherScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/EncounterStagesScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/MonsterPreparationScreen.java`
- `src/main/java/dev/tacticalcombat/combat/EncounterLauncher.java`
- `src/main/java/dev/tacticalcombat/combat/EncounterStages.java`
- `src/main/java/dev/tacticalcombat/combat/MonsterPreparation.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/rules/srd/CharacterRules.java`
- `src/main/java/dev/tacticalcombat/rules/srd/MonsterProfiles.java`
- `src/main/java/dev/tacticalcombat/rules/srd/SrdAuthoring.java`
- `src/main/java/dev/tacticalcombat/rules/srd/SrdCharacterCodec.java`
- `src/main/java/dev/tacticalcombat/rules/srd/SrdEquipment.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/LegacyRulesParityTests.java`
- `src/test/java/dev/tacticalcombat/character/MonsterStageTests.java`
