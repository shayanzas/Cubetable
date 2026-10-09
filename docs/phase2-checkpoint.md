# Phase 2 — Rules profiles and authoritative characters

Report date: October 9, 2026 (UTC).

## Checkpoint and scope

Repository: `shayanzas/Cubetable`. Branch: `dev/phase2-character-foundation`, based on the pushed Phase 1 checkpoint `d70b8cfee4b6e1b0e7d8e1ad553f5f6eca3e0a82`. Work continued in the existing checkout with unchanged dependencies. Verified implementation commit: `b1b21eb`. This checkpoint is local; no Phase 2 push or pull request was performed.

The user authorized starting Phase 2. The Phase 1 real-client acceptance gate remains open; headless verification does not close it. This checkpoint implements a usable server character foundation and passes the automated Phase 2 gate for the supported Minecraft Tactical calculations. **Phase 2 is Partially Implemented for full release acceptance**, primarily because graphical/two-client behavior is unverified and server character synchronization is command-based rather than a complete management screen. SRD combat remains Phase 3; its reserved profile cannot run Minecraft damage under a D&D label.

## Requirement status

| Requirement | Status | Result and limit |
|---|---|---|
| Modular rules interfaces | Implemented foundation | Trusted versioned policy supplies world-combat eligibility, initiative and movement. Attack/effect/action policies will be extended alongside their Phase 3 implementations; no arbitrary formula execution. |
| Campaign defaults | Implemented | Persisted campaign default, membership, owner and co-DMs. Selection is per connection and requires membership; changing selection during combat is rejected. |
| Encounter overrides | Implemented for supported engine | Campaign DM may start a `minecraft_tactical` override. Encounter keeps its original campaign/profile when the campaign default changes. Incompatible bound character profiles are rejected. Manual/SRD profiles cannot start world combat. |
| Character ownership/binding | Implemented | Independent server drafts, approval, one character per actor and one actor per character, UUID ownership checks, revision checks, combat-time binding restrictions and offline/removed-actor release. |
| HP/max HP | Implemented for Minecraft Tactical | Approved binding applies current/max HP. Vanilla health is the live server authority; capture updates the persistent character. No D&D environmental damage, unconsciousness, death saves or revival semantics. |
| Speed/initiative | Implemented for Minecraft Tactical | Approved `movement_blocks` and `initiative_bonus` affect real turn budget, mob planning and initiative. Unbound defaults remain player 8 / mob 6 blocks and plain d20. This is not Dexterity-derived SRD initiative. |
| Core attributes/AC/resources | Partially Implemented | Bounded numeric server stats persist, including abilities, AC and arbitrary named resources. AC does not replace vanilla armor/damage. Resource maxima, typed expenditure/recovery and derived/class values are not automated. |
| Server permissions | Implemented baseline | Owner/co-DM/member/admin checks below. Approval is always required; configurable approval policy, configurable co-DM capability scopes and owner-transfer UI remain incomplete. |
| Initial persistent storage | Implemented | Schema 1, atomic replacement, validated backup, bounded input, write-before-publish, fail-closed load. Future schema versions are refused rather than migrated or rolled back. Full encounter recovery remains Phase 7. |
| D20/percentile compatibility | Implemented data path / Not Verified visually | Existing pack files and local manual behavior retained. Submitted original JSON preserved verbatim; only explicit numeric fields feed draft stats. No sheet formula is executed on the server. |
| Server character UI/synchronization | Partially Implemented | New sheet Submit button; private command replies show authoritative revisions/stats. Local sheet is an independent copy and does not automatically reflect server changes. Existing combat snapshots/vanilla health carry world changes. |
| Multiplayer/visual release acceptance | Not Verified | Embedded mock server players and real Minecraft server execution passed. Two graphical clients, latency, reconnect/restart acceptance and narrow-screen button layout still require the checklist below. |

## Architecture and changed files

- `rules/RulesProfile.java`, `rules/RulesProfiles.java`: engine boundary and three built-in version-1 profile identifiers. `minecraft_tactical` is enabled; `manual_tabletop` disables automated world combat; `srd_5_1` reserves separate character identity but automation is disabled.
- `character/CharacterRepository.java`: immutable campaigns/characters/bindings, permission checks, drafts, approvals, optimistic revisions, bounded stats and schema validation. Trusted server code creates principals; there is no client-supplied admin flag.
- `character/AtomicJsonFile.java`: bounded reads, last-good backup and durable temporary-file replacement.
- `character/CharacterService.java`: world storage lifecycle, server authentication, selected campaign, entity binding, HP adapter, persistence capture and safe release. Initial loaded entities, later entity loads, player joins and respawns reconcile the character max-health modifier.
- `character/CharacterCommands.java`: `/tbc campaign`, `/tbc character` and `/tbc encounter`. Successful management command actor names and operation names are logged without full sheet/command contents. This is basic server logging, not the complete persistent override activity log planned for DM tools.
- `net/SubmitCharacterPayload.java`, `TacticalCombatMod.java`: bounded C2S draft submission; ownership/campaign/profile come from server connection/selection, not the packet. Lifecycle registration and bounded replay/rate tracking.
- `client/CharacterSheetScreen.java`: Submit action on a loaded sheet when the server advertises the new channel. Existing editing, dice, cards and local saves remain.
- `combat/Combat.java`, `combat/CombatManager.java`: encounter policy snapshot, campaign/profile admission, approved initiative/movement, mob planning using remaining budget, UUID ownership lookup and periodic health capture.
- `CharacterRepositoryTests.java`, `FoundationTests.java`, `CharacterGameTests.java`, GameTest entrypoint manifest: storage/security/compatibility regressions plus world binding and codec integration.

All production paths above are relative to `src/main/java/dev/tacticalcombat`; pure tests are in `src/test/java/dev/tacticalcombat`, Minecraft tests in `src/gametest/java/dev/tacticalcombat/combat`.

## Permissions and conflicts

| Operation | Authorized actor |
|---|---|
| Create campaign | Server administrator (permission level 2); creator becomes owner |
| Select campaign / submit draft | Member, campaign manager or server administrator |
| Invite ordinary member / set default rules | Campaign owner, co-DM or administrator |
| Appoint/revoke co-DM | Owner or administrator; co-DM cannot elevate others |
| Read/edit character stats | Character owner or that campaign's manager; no private character broadcast |
| Approve / bind / unbind / release character | Campaign manager; player target must own that character |
| Start explicit encounter override | Campaign manager, using implemented world-combat profile |

Stat/approval/binding commands carry an expected character revision. Stale writes fail, and stat edits invalidate approval. Bound stats cannot be edited, even by a DM; unbind outside combat, edit and reapprove. Repository writes execute on the server thread, avoiding competing command writes. Normal vanilla health capture may advance the character revision, so read `show` before a revision-sensitive command. Each submitted sheet creates a distinct draft; submitting again does not overwrite a previous record.

A campaign default change does not convert an existing character. A tactical-bound character stays tactical and is rejected from incompatible profiles. Unbound members can participate in an explicitly started tactical override for their campaign. Automatic recruitment checks campaign identity and binding compatibility. Non-campaign players retain legacy Minecraft Tactical behavior.

## Persistence and limits

Storage: `<world>/tacticalcombat/campaigns.json`, backup `campaigns.json.bak`. Client pack/local sheet directories are separate and unchanged.

Schema 1 contains campaign records, character records (owner/campaign/profile/version/raw sheet/stats/approval/revision) and entity UUID-to-character UUID bindings. Writes validate the whole proposed state, force a same-directory temporary file to storage and atomically replace the target. A validated old primary is atomically backed up first. Memory publishes the new state only after the write succeeds. Filesystems without atomic rename support fail the operation; there is no unsafe fallback. Temporary files from interrupted saves are ignored.

If the primary is corrupt/missing and the backup validates, load it and log recovery. This can roll back the latest save, **including a membership or approval change**; server operators must review recovery warnings. Two bad copies disable the character service and new tactical encounters rather than resetting to empty storage. An unsupported primary schema refuses backup rollback. There is no schema migration beyond the initial schema. File data is forced; parent-directory fsync/power-loss guarantees on all filesystems are not claimed.

Limits: 64 campaigns, 256 members per campaign, 1,024 characters total, 32 per owner, 64 numeric stats per character, 16 MiB save, 32,768-character submitted sheet and nesting depth 20. Stat keys match `[a-z][a-z0-9_]{0,31}`; values must be finite. HP/max HP <= 1,024; movement <= 32 blocks; AC <= 100; other values <= 1,000. Only initiative bonus may be negative (minimum -100). Require `0 <= hp <= hp_max` and `hp_max >= 1`. Initiative uses the integer portion of the supplied bonus. Imported raw keys: hp, hp_max, ac, movement_blocks, initiative_bonus, str, dex, con, int, wis, cha, prof, level. Other raw sheet values remain in the preserved sheet, not authoritative stats unless explicitly entered with `stat`.

Submission rate: two attempts per 100 world ticks, at most 64 distinct request IDs per connection, with disconnect/stop cleanup. Duplicates and excess traffic are ignored. Permission fields are not in the packet; unknown root sheet fields are rejected. Nested sheet content remains inert data. The UI reports success/rejection, but rate-limit/replay drops currently have no acknowledgement.

For tactical bindings, one additive max-health modifier preserves the vanilla base and other modifiers. Other mods may therefore change effective max health. Stored HP is clamped to configured character hp_max. Live vanilla health is captured every 20 server ticks and on shutdown, with writes only when values change. A crash can lose the latest capture; this is not a D&D health pipeline. Vanilla respawn behavior remains, and entity NBT health remains the reload source rather than reapplying the binding's original HP on every join. Release by character ID supports unloaded/removed entities; returning entities have stale modifiers removed on load/join. No automatic permanent deletion of dead NPC records is performed.

Full-store JSON validation/fsync is synchronous. Large-scale tick cost and save-size behavior need measurement before increasing limits. Local sheet persistence still has the original implementation; this change does not close all of audit F13.

## Verification

Baseline before Phase 2: `build runGametest` passed 320 foundation assertions and 29 required Minecraft GameTests.

Final command in this environment:

```sh
/workspace/cubetable-setup/gradle.sh build runGametest
```

Equivalent on a machine with a complete Java 21 JDK:

```sh
./gradlew build runGametest
```

Toolchain unchanged: Java 21 (installed 21.0.12.1), Gradle wrapper 8.10.2, Loom 1.8.13, Minecraft 1.21.1, Fabric Loader 0.16.9, Fabric API 0.116.1+1.21.1.

The final verification record appears below. Pure assertions are not individual JUnit test methods. GameTests launch a real headless Minecraft server and use embedded mock players; they are not two remote clients.

New pure coverage includes role hierarchy, private reads, draft approval, forged/stale writes, finite/bounded stats, duplicate bindings, bound-edit rejection, revision changes, HP capture, reload, both existing pack samples, generic resources, orphan temporary files, corrupt-primary backup recovery, future-schema refusal, failed-write memory consistency and unrecoverable saves. New world coverage includes the production binding service, current/max HP, actual initiative/turn movement, campaign/profile isolation, unauthorized edits, combat-time release rejection, immutable encounter policy, manual-world-combat rejection, unbinding, offline-actor release and persisted health. Codec test checks complete round-trip decoding of the submission payload. Receiver rate/replay behavior is reviewed in code but not yet exercised through two real clients.

The first run exposed Gson serialization of the empty immutable-map values collection as null. Encoding concrete arrays fixed it; empty-store creation and subsequent reload are exercised by the tests. The final run passed after that correction. Build warnings about deprecated mock-player helpers/Gradle features remain. The headless environment may log the existing Authlib public-key DNS failure; remote authentication is not verified by these tests.

## First in-game workflow

Install the same newly built `build/libs/tactical-combat-0.1.0.jar` on a Fabric 1.21.1 server and both clients, with the required Fabric API. Use a disposable test world first. Existing packs still need to be installed as described in the sheet-format documentation.

1. Operator: `/tbc campaign create Test campaign`. Copy the returned campaign UUID. Creation selects it for the operator.
2. Operator: `/tbc campaign invite PlayerName`. Player: `/tbc campaign select CAMPAIGN_UUID`.
3. Player: open a Generic D20 sheet and click **Submit**. Copy its server character UUID. This is an unapproved server copy, not a replacement for the local file.
4. Player: `/tbc character show CHARACTER_UUID`, then `/tbc character stat CHARACTER_UUID 1 movement_blocks 4`. Read the new revision with `show`; subsequent edits must use it.
5. DM: `/tbc character approve CHARACTER_UUID CURRENT_REVISION`. Read `show` again (approval increments revision), then `/tbc character bind CHARACTER_UUID CURRENT_REVISION PlayerName`.
6. Start a normal tactical encounter near a supported hostile. Verify movement is four blocks and HP/max HP match the approved record; initiative only changes if an explicit initiative_bonus was approved.
7. End combat before editing bindings. DM: `/tbc character unbind PlayerName`. For an offline/removed actor, use `/tbc character release CHARACTER_UUID CURRENT_REVISION` instead.
8. DM: `/tbc campaign rules manual_tabletop`. Verify new automatic world encounters are disabled for selected campaign members. `/tbc encounter minecraft_tactical` explicitly overrides the default for an eligible tactical/unbound party. `/tbc campaign rules minecraft_tactical` restores the default.

`/tbc campaign list`, `/tbc character list` and `/tbc character show UUID` expose the IDs/revisions needed by the workflow. Co-DMs are appointed/revoked with `/tbc campaign dm PlayerName` and `/tbc campaign revoke-dm PlayerName`; these target online players. Binding a DM-owned sheet to a selected living NPC uses an entity selector instead of PlayerName. There is no destructive character-delete command in this checkpoint.

## Manual acceptance checklist — not yet executed

- Two clients: player submits, owner/co-DM reviews, approves and binds; third unrelated player cannot list/read/edit/approve the record or grant roles. Verify the ordinary owner of a character cannot self-approve unless they independently have DM authority.
- Conflicting DMs: both use one revision; first edit succeeds, second fails. Bound edits and active-turn unbind/release fail without changing movement/HP.
- Campaigns: nearby players in different campaigns do not autojoin one another's encounters. Change default while an encounter runs and verify its policy stays fixed. Reject incompatible bound profiles without rewriting data.
- Both supplied packs: compare local rendering, formula/manual rolls, cards, editing and saving before/after Submit. Confirm local edits do not affect the bound server record. Check the Submit button at small UI sizes; verify no button is advertised against an older server.
- HP: take damage outside/inside combat, read the server record after 20 ticks, disconnect/reconnect, respawn, unload/reload a bound mob, release an offline binding and restart. Check max-health modifiers do not accumulate and original vanilla max health returns after release. Use additional health-modifier mods only in a separate compatibility test.
- Networking: repeated/delayed submissions, identical request IDs, oversized/malformed JSON and denied/stale commands. Confirm no duplicate approved actor or privilege change; reconnect clears submission request history.
- Storage on a copied world: stop server, corrupt only the primary and verify warned backup recovery; corrupt both and verify closed service; restore known-good data. Test unsupported schema rejection and unwritable-storage failure without losing the last valid state.
- Repeat [Phase 1 multiplayer/visual checklist](phase1-multiplayer-checklist.md), including camera, grid, HUD, dice/cards, movement and inventory prediction under latency.

## Audit status and next work

F08 is **Partially Implemented**: approved HP/movement/initiative are connected to actors, while AC/damage, abilities, equipment and resource expenditure await their rules engines. F13 is **Partially Implemented**: new server storage is protected; existing local sheets and complete campaign/encounter recovery remain. F01–F07, F11 and F17 keep the qualified Phase 1 status; their regression suite still runs. F09, F10, F12, F14–F16 and F18 are not claimed closed by this checkpoint. In particular, the threat overlay remains approximate and does not automatically become an authoritative character movement preview for every enemy.

Next: finish the graphical/two-client acceptance checks and improve server character readback/editing synchronization and approval policy before declaring Phase 2 release-ready. Then extend the trusted rules boundary for Phase 3 SRD initiative, attacks, AC/damage and movement; do not enable the reserved SRD profile until those calculations are independently tested. Keep manual packs and Minecraft Tactical adapters separate from SRD resolution. Encounter persistence, class/spell automation and generalized DM controls remain their specified later phases.

## Final verification record and resume point

Verified production/test code: `b1b21eb`. Final command `build runGametest`: **BUILD SUCCESSFUL**, 30 seconds. **320 existing foundation assertions + 39 character/storage assertions = 359 passed; all 31 required Minecraft GameTests passed**, including the 29 Phase 1 tests. No source changes followed that successful run. Documentation is committed separately.

Release JAR: `build/libs/tactical-combat-0.1.0.jar`, 326,117 bytes. SHA-256: `74ebb525253d40e76c18732642908510f0166b37ccbe7101c0a0c8ab51845aaa`. Packaging inspection found no GameTest/test-accessor entries. `git diff --check` passed.

Environment logs: `/tmp/phase2-baseline.log`, `/tmp/phase2-final-verification.log`; structured Minecraft results: `build/gametest-results.xml`. These generated files are not committed. Reproduce them with the commands above. The Authlib public-key DNS warning did not prevent the headless suite completing; real remote authentication remains unverified.

Resume on `dev/phase2-character-foundation`; read this report and the Phase 1 multiplayer checklist, then run the manual acceptance workflow against the built JAR. Do not interpret the reserved SRD profile or stored AC/resources as automated D&D rules. No Phase 3 implementation was started.
