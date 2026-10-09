# Phase 2 completion — authoritative characters and synchronization

Report date: October 9, 2026 (UTC). Repository: `shayanzas/Cubetable`.

## Checkpoint and readiness

Continued the existing clean branch `dev/phase2-character-foundation` at `ede22b3`, retaining implementation `b1b21eb` and Phase 1 baseline `d70b8cf`. No reset, reclone, dependency change, push or pull request. Final commit identifiers and verification evidence appear at the end of this report.

The requested remaining Phase 2 mechanisms are implemented: private discovery/readback, revision-aware updates to existing records, explicit reconciliation, a minimum character management screen, campaign approval policies, lifecycle capture and schema migration. **Automated acceptance is verified; graphical/two-client release acceptance is Not Verified.** The Phase 1 real-client gate remains open independently. No Phase 3 mechanics were implemented, and the SRD profile remains disabled for world combat.

The earlier [Phase 2 checkpoint](phase2-checkpoint.md) remains a historical record; this report supersedes its descriptions of revisions, approval policy, submission UI and storage schema.

## Requirement classification

| Requirement | Status | Result / boundary |
|---|---|---|
| Preserve workspace and verify baseline | Implemented | Clean starting branch; original 359 assertions and 31 GameTests passed before edits. Existing dependency versions retained. |
| Discover campaigns and authorized characters | Implemented | Private, permission-filtered pages; named campaign/character buttons. Ordinary members see their own characters; campaign managers may review that campaign's characters. |
| Request authoritative state | Implemented | Versioned request/reply API; explicit Refresh. For a loaded tactical actor, GET captures current health before returning its record. Offline actors retain last captured health. |
| Distinguish local/server data | Implemented | Existing sheet labelled Local; Server opens a separate workspace. Latest server status and staged changes are separate. No background write into local sheets. |
| Preserve edits on refresh and reconcile | Implemented | Refresh retains working draft and typed input. Use latest explicitly discards staged changes. Three-way reconciliation keeps disjoint changes; overlapping fields require explicit confirmation or manual resolution. |
| Revise existing drafts without duplicates | Implemented | Save changes patches the selected character ID at its expected revision. New from local is a separate confirmed create operation. Transport retries reuse sequence identity. |
| Acknowledgements | Implemented | Success, rejected/forbidden, conflict, storage failure, replay and rate-limit results. Timeout explicitly reports an unknown result and directs the user to refresh. |
| Selected/bound/approval status | Implemented | Named list entries show approval/binding; detail shows selected character UUID, revisions, profile and server HP/movement/initiative. Binding remains a DM command. |
| Unsupported-server behavior | Implemented / Not Verified visually | Server controls require the v2 request channel. Existing manual sheets remain available. Visual/capability acceptance on older servers remains a manual check. |
| Configurable approval policies | Implemented | New-character approval, renewed approval after edits, owner field allowlist and owner edit lock; manager-only changes with campaign revisions. Secure defaults preserved. |
| Management usability | Implemented minimum / Not Verified visually | Campaign and character browsing, selection, field browsing/editing, local import, refresh/reconcile and DM approval. Policy administration, invitation and binding retain commands. Full DM dashboard is Deferred. |
| Authority/lifecycle | Implemented for Minecraft Tactical | Bound edits remain forbidden; single max-health modifier; periodic, departure, respawn, join, unbind and explicit-refresh capture. Vanilla health/respawn semantics remain. |
| Active-encounter DM adjustment | Deferred | No direct bound-stat bypass. See the required future override contract below. |
| Ruleset isolation | Implemented | Tactical-only automation, explicit encounter override, campaign/profile admission and immutable active encounter policy. Manual data preserved. |
| Initial persistence/recovery | Implemented | Schema 1→2 migration, atomic writes, backup validation, bounded parsing, fault injection and write-before-publish. Full encounter recovery is Deferred to Phase 7. |
| Automated verification | Implemented | Final recorded build, pure assertions and real headless Minecraft GameTests below. |
| Two graphical clients / latency / visual regressions | Not Verified | No DISPLAY or Xvfb. Follow the linked acceptance checklist. Mock players are not remote clients. |
| SRD AC, attacks/damage, classes and spells | Deferred | Outside Phase 2; no substitute vanilla mechanics advertised as SRD implementation. |

## Synchronization architecture

`CharacterRequestPayload` / `CharacterReplyPayload` use versioned `character_request_v2` / `character_reply_v2` channel IDs. Both bound their JSON string to 65,536 characters. Request operation names are bounded to 16 characters; response codes/messages to 24/256. Request JSON nesting is checked before parsing. Individual submitted sheets retain the 32,768-character/depth-20 limit. Catalog pages contain at most eight entries.

The Fabric receiver invokes `CharacterNetworking.handle` on the server thread. It derives the principal from the connected player and operator permission level, and obtains the selected campaign from server state. It never accepts ownership, DM role, approval or binding from a sheet or client flag. The pure `CharacterSyncService` provides campaign discovery, character listing/readback, creation, field updates, approval and policy updates. Private replies go only to the requesting connection; there is no sheet broadcast or private response cache.

One monotonic sequence per connection prevents old requests from executing again, even after newer requests. Replays return ALREADY_PROCESSED without private cached content. Four requests per 20 world ticks are admitted; excess requests receive RATE_LIMIT before JSON processing. The client keeps one request in flight, retries the same sequence after 100 ticks up to twice, and reports an unknown result after a final timeout. After replay/timeout, refresh the list/state before attempting another change. Disconnect and stop clear session state.

The v1 submission channel remains available for older Phase 2 clients and still uses its original bounded replay/rate checks; it now goes through the same policy-aware repository. The current UI replaces one-click Submit with Server management and explicit New from local versus Save changes.

### Edit and runtime revisions

`revision` now identifies character configuration/approval edits. `runtimeRevision` advances when captured HP changes. HP capture no longer makes a name, AC or other unrelated configuration edit stale.

Clients send an explicit field patch (`text.name`, `values.movement_blocks`, `collections.weapons`, etc.), the expected edit revision and the base health revision. The server merges only those fields into current state. An explicit HP edit also requires a current health revision. Unrelated patches retain newer captured HP. All edits still require the current edit revision and an unbound character. A command stat edit and a UI edit use the same permission/validation path.

Snapshots overlay authoritative numeric stats onto the preserved sheet JSON, so a command-updated stat or captured HP is not masked by old imported numbers. Only known core stats, existing explicit stats and `resource_` inputs become bounded numeric actor stats through the patch API. Other manual-pack numbers remain finite inert sheet data and retain their scale. The explicit administrator/troubleshooting `stat` command can create a bounded numeric stat. No formula, derived expression, class feature or script is executed by this adapter.

### Client reconciliation

`CharacterWorkspace` keeps three independent copies: the base snapshot, working draft, and latest server snapshot. Refresh changes only the latest snapshot once a draft is open. Use latest explicitly replaces the draft. Reconcile applies disjoint local changes over the latest server state; overlapping field changes are listed and rejected. Keep my edits requires confirmation before applying the user's changed fields over those conflicts, then Save changes still validates the latest revision on the server.

Collections are reconciled as whole named collections; there is no guessed row-level merge. Import local stages fields present in the local sheet and preserves server-only fields; formats must match. It does not save a local file or change server ownership/profile. Switching or closing a dirty workspace prompts before discard. Typed but unstaged input is preserved on refresh and must be staged before Save. Working drafts are in memory; this is not durable offline editing across disconnect/process exit.

## Campaign policy behavior

`ApprovalPolicy` contains four explicit fields:

| Field | Secure default | Meaning |
|---|---|---|
| `requireNewApproval` | true | New records remain drafts until an authorized manager approves. If false, the server approves a newly created permitted record. |
| `requireEditApproval` | true | An edit invalidates existing approval. If false, an already approved character keeps approval after a permitted edit. Previously unapproved drafts are not automatically approved by editing. |
| `ownerLocked` | false | When true, ordinary owners cannot create or edit characters. Managers can still review/edit unbound records. |
| `ownerFields` | text.*, values.*, collections.*, kind, theme, ui | Allowlist for ordinary-owner input. Supports exact field paths and the three group wildcards. No ownership/permission/approval/profile fields can be granted. |

Only campaign owners, co-DMs and server administrators can change this policy. Co-DMs still cannot appoint other co-DMs. Policy changes require the expected campaign revision and do not rewrite or approve existing characters. New submissions also obey owner field restrictions and locks; creating a fresh record cannot bypass a restricted field.

The repository rechecks campaign membership and character ownership/management for private reads and edits. Ownership alone does not grant access after campaign membership is removed. Schema-2 policy objects require all four fields with explicit boolean types; missing flags cannot silently default to automatic approval.

Commands for the selected campaign:

```text
/tbc campaign policy
/tbc campaign policy REVISION NEW_APPROVAL EDIT_APPROVAL LOCKED FIELD_LIST
```

Read the first command to obtain the current revision. The field list is comma-separated without spaces; `none` allows no owner-editable fields. Example secure policy:

```text
/tbc campaign policy REVISION true true false text.*,values.*,collections.*,kind,theme,ui
```

Example allowing already-approved owners to edit names without renewed approval, while new characters still require review:

```text
/tbc campaign policy REVISION true false false text.name
```

Restricted new submissions must contain only permitted owner fields (plus format). This deliberately rejects a full local import containing forbidden fields; it does not silently strip them. Managers can prepare such a character, or temporarily choose an appropriate creation policy.

## Character management workflow

1. Operator creates a campaign and invites the player using the existing commands. These remain name-based operations.
2. Open the existing character sheet and click **Server**. This is available even when no local character is selected, provided the server supports v2.
3. Select a campaign by name. Select an existing server character by name/status, or use **New from local** and confirm creation. To revise an existing record, select it instead of creating another.
4. Detail shows the selected record, approval/binding, edit/HP revisions and current server HP, movement and initiative bonus. The field/value inputs edit only the working draft. Browse available fields with `<` / `>` or type a field path, change its value, and click **Stage field**. Core numbers use `values.<key>`; text uses `text.<key>`. Collection/UI edits accept bounded JSON.
5. **Save changes** submits the patch for server validation. The reply reports success or the rejection/conflict. **Import local** stages fields from a same-format local character without changing the original file or creating a new server record.
6. **Refresh** fetches the latest server state while preserving the draft. Use **Reconcile** for disjoint edits, or resolve overlapping fields using **Use latest**, manual restaging, or the explicitly confirmed **Keep my edits** operation.
7. An authorized DM opens the same list, reviews the server record, and clicks **Approve**. Staged edits must be saved/discarded first. A player without DM authority is denied even with a forged approval request.
8. Binding remains `/tbc character bind CHARACTER_UUID REVISION PlayerName` or a living NPC selector. The UI shows the character UUID and revision. Show/list commands remain available. End combat and unbind before changing bound stats.

The local resizable sheet remains the manual pack interface. The separate management screen does not make local dice/card operations authoritative combat actions. Its list uses pagination and a height-dependent row count; controls use a bounded panel width, field browsing and wrapped status messages. Visual acceptance at small GUI sizes is still required.

## Health lifecycle and DM interventions

Minecraft Tactical still uses live vanilla HP. Approved binding applies initial HP and one additive maximum-health modifier. Apply removes/replaces that modifier rather than accumulating copies; other vanilla/mod modifiers are preserved. Current HP is captured periodically, at shutdown, on entity unload/disconnect, after join/respawn, before unbind and on authorized GET for a loaded actor. Entity NBT remains the source of live HP on reload; refresh does not heal the entity from an older sheet.

Tests exercise actual vanilla player respawn, entity NBT readback, load/unload callbacks, repeated modifier application, immediate unbind capture and character-service stop/start against the same saved file. These are headless lifecycle tests, not a separately launched dedicated-server process restart with two clients. A copied-world full process restart remains in the manual checklist.

Direct editing of bound characters remains forbidden for players and DMs. No HP override was added during active combat: a future override must independently authorize the DM against the actor's campaign/encounter, use the appropriate rules-profile health adapter, validate limits and runtime/encounter versions, commit exactly once, synchronize affected viewers, and record an explicit adjudication event. It must not impersonate a normal attack or bypass accepted-action accounting. This is Deferred to the DM-tools work.

No D&D unconsciousness, stabilization, death saves, environmental damage conversion or class healing is implied. Other mods that increase effective maximum health beyond configured character hp_max can still create a difference between live modified health and the capped stored character HP; general health-mod interoperability is not verified.

## Persistence changes and recovery

Storage remains `<world>/tacticalcombat/campaigns.json` plus `.bak`. Schema 2 adds campaign policy and character runtime revision. Schema-1 records migrate in memory with secure default policies and runtime revision zero, preserving campaign/character/entity IDs, approval and values. The next successful mutation writes schema 2; a valid schema-1 primary is retained as the backup. There is no destructive eager rewrite at load.

Future schemas refuse fallback rollback. Corrupt/missing primary may recover a validated backup with a warning; two invalid copies fail closed. Save JSON nesting is bounded to 24 and sheet nesting to 20, in addition to existing byte/count/value limits. Writes still validate, flush a same-directory temporary file, atomically replace and publish memory only after success.

Fault injection now interrupts after temporary file flush and before primary rename. Tests verify the unchanged primary, valid backup, unchanged memory, temporary-file cleanup and successful reload of the prior policy. Existing tests for orphan temporary files, corrupt/missing primary, both invalid copies, unsupported schema, duplicate bindings and write failures remain.

Limitations: backups can roll back the last operation, including permissions/approval; operators must review recovery warnings. Parent-directory fsync and universal filesystem power-loss guarantees are not claimed. Saves are synchronous and full-store; large-scale profiling is Deferred. Crash-time HP capture can lag the most recent vanilla damage. Existing client-local sheet persistence is unchanged. Full encounter restoration remains Phase 7.

## Verification and regressions

Baseline command before edits passed **359 assertions and 31 required GameTests**. During development, the older repository test expecting HP capture to increment the edit revision failed; it was updated to the new two-revision contract, with additional tests proving both unrelated-edit success and stale explicit-HP rejection. No previous regression assertion was removed. Manual-pack edit review also led to retaining non-combat numbers as inert data rather than forcing them into actor-stat limits.

New pure tests cover private discovery/readback, injected authority flags, same-ID editing, disjoint/overlapping reconciliation, dirty-state preservation, format mismatch, approval transitions, field masks/locks, new-record bypass attempts, stale policy revisions, membership revocation, independent HP revisions, pagination, manual number scales, schema migration, missing-primary recovery, replay/rate cleanup and actual pre-rename interruption.

Four new Minecraft GameTests cover the production request handler with authenticated mock players, permissions and acknowledgements, replay rejection, stale patches, policy command parsing/authorization, codecs and malformed bounds, live health refresh, NBT lifecycle and vanilla respawn, plus ruleset overrides and legacy/campaign separation. The original 31 GameTests remain included.

Final build/test counts and artifact evidence are recorded below after final verification. Compilation includes the client screen, but neither that compilation nor mock-player tests verifies rendering or remote networking. No display or Xvfb is available, so no graphical client test was attempted or claimed passed. Existing Authlib public-key DNS and deprecated helper/Gradle warnings do not establish remote authentication or graphical readiness.

## Files changed

| Area | Files |
|---|---|
| Policy, edits, synchronization model | `character/ApprovalPolicy.java`, `CharacterEdits.java`, `CharacterWorkspace.java`, `CharacterSyncService.java`, `CharacterSessions.java` |
| Server adapter and persistence | `character/CharacterNetworking.java`, `CharacterRepository.java`, `CharacterService.java`, `CharacterCommands.java`, `AtomicJsonFile.java`, `TacticalCombatMod.java` |
| Versioned protocol | `net/CharacterRequestPayload.java`, `CharacterReplyPayload.java` |
| Client management | `client/ClientCharacters.java`, `ServerCharactersScreen.java`, `CharacterSheetScreen.java`, `TacticalCombatClient.java` |
| Pure tests | `CharacterSyncTests.java`, `character/PersistenceFaultTests.java`, `CharacterRepositoryTests.java`, `FoundationTests.java` |
| Minecraft tests | `combat/CharacterSyncGameTests.java`, GameTest entrypoint manifest |
| Reports | This report, `phase2-multiplayer-checklist.md`, historical checkpoint pointer |

Production paths are relative to `src/main/java/dev/tacticalcombat`; test paths are relative to their respective `src/test/java/dev/tacticalcombat` and `src/gametest/java/dev/tacticalcombat` roots.

## Audit status and Phase 3 readiness

**F08: Partially Implemented overall.** The Phase 2 authority/synchronization gap is addressed for supported Minecraft Tactical inputs with private readback and policy-controlled edits. SRD AC/damage, derived rules, equipment and resource expenditure remain dependent on later rules engines; stored values do not imply automation.

**F13: Partially Implemented overall.** Server storage now has migration, bounded validation, atomic replacement, recovery and fault-injection coverage. Original local-sheet saving and full encounter recovery remain outside this change.

Other audit findings retain their qualified earlier statuses. No changes were made to the Phase 1 action authority engine, camera, grid, manual dice/card services or pack files. Automated regressions passed in the final record; graphical regressions remain unverified.

The implementation is a tested foundation for Phase 3 development, but not a claim that the full multiplayer/visual release gate has passed. Run [the Phase 2 two-client checklist](phase2-multiplayer-checklist.md) and the still-open [Phase 1 checklist](phase1-multiplayer-checklist.md) before declaring release acceptance. Keep `srd_5_1` automation disabled until Phase 3 independently verifies its own calculations.

## Final verification record and resume point

Verified implementation commit: **`91437ac`** on `dev/phase2-character-foundation`. Documentation follows in a separate local commit. No production or test source changes followed the final successful run.

Command:

```sh
/workspace/cubetable-setup/gradle.sh build runGametest
```

Result: **BUILD SUCCESSFUL in 29 seconds**. Compilation, checks and remapped packaging passed with unchanged dependencies.

| Executed suite | Result |
|---|---|
| Existing foundation assertions | 320 passed |
| Character repository assertions | 39 passed |
| Synchronization/policy assertions | 58 passed |
| Persistence interruption assertions | 6 passed |
| Total pure assertions | **423 passed** |
| Minecraft GameTests | **35 passed; 0 failures, 0 errors, 0 skipped** |
| Release packaging | No GameTest/test-mixin/fault-test entries in the release JAR |
| Diff validation | `git diff --check` passed |
| Two graphical clients | **Not Verified**, not counted as passed or skipped GameTests |

Runtime evidence includes actual headless Minecraft execution, mock-player command/handler paths, vanilla respawn, entity NBT reload and character-service restart. A full external process restart and two remote graphical clients remain manual acceptance checks. No automated regression was observed in the final run.

Artifact: `build/libs/tactical-combat-0.1.0.jar`, **367,488 bytes**.

SHA-256:

```text
99e6ad13b922f4314924f69bb3cb460cbe64e64a50981b7c7d772e2a0218a369
```

Logs in this environment: `/tmp/phase2-completion-baseline.log`, `/tmp/phase2-completion-final.log`; structured results: `build/gametest-results.xml`. Generated logs/build artifacts are not committed. Toolchain remains Java 21, Gradle 8.10.2, Loom 1.8.13, Minecraft 1.21.1, Fabric Loader 0.16.9 and Fabric API 0.116.1+1.21.1.

Resume by testing the built JAR with [the two-client checklist](phase2-multiplayer-checklist.md), recording results and resolving any observed failures. Phase 1 visual/remote acceptance remains open. No Phase 2 remote push was performed and no Phase 3 implementation was started.
