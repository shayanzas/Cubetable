# Gameplay rules checkpoint J1

## Delivery identity

- Approved I base: `6c012d9db9c12238bd905f90db66ec5efc1b9894`.
- Frozen pre-integration contract: `ee0d4ff` (277 checks run against unchanged production code).
- Final implementation commit: `0b95b2b8cfe1963ed435710071f947c4c07e828d`. The subsequent report-only commit is the delivery tip; use `git log -1 --format=%H -- docs/gameplay-rules-checkpoint-j1.md` to resolve it without a self-referential hash.
- Branch: `dev/gameplay-rules-checkpoint-j1`, destination `shayanzas/Cubetable`; no merge or force-push.
- This implements the bounded J1 portion of the I audit and implementation roadmap. No J2 equipment or feature integration is included.

## Architecture and authority

`LegacySrdInputs` adapts existing version-1 `collections.srd_combat` inputs through the unchanged codec. `CharacterRules` produces immutable `DerivedRulesState`: six ability modifiers, proficiency, AC, initiative modifier, walking speed, HP/temp HP and virtual ammunition, plus the immutable typed inputs and provenance. Attack planning, initiative rolls and attacks delegate to the existing `SrdRules` implementation. Static defenses remain in the same typed inputs and existing damage path.

`CharacterRepository` caches one projection per immutable character object. Build/runtime changes replace that object after successful persistence. Existing `srdInputs` and snapshot `stats()` access remain compatibility views. Encounter approval, binding, campaign membership, action validation and snapshot comparison remain enforced. Encounter initiation, attack preview/resolution and movement budgets consume the projection. Neither watch packets nor client read models provide calculated combat values to these paths.

`CharacterSyncService` and full personal-library responses serialize `RulesReadModel` after existing authorization. `DashboardModel`, ability tiles and sheet combat statistics consume those server values. Legacy-server responses without the new field retain the previous fallback; an explicitly invalid/unknown projection does not silently fall back. Informational skill/save totals retain recorded proficiency and adjustments, using the projected base modifier and proficiency. They remain explicitly non-executable.

Current sources remain separate: approved campaign typed inputs for encounters; owner-authorized personal drafts for library preview; informational `ui_*` records for inventory, progression, check annotations and biography. Library preview is not campaign approval. Recorded progression and effective combat level are separately exposed. Explicit disagreement adds an explanatory notice; it does not rewrite either value.

Provenance distinguishes persisted inputs, derived values, runtime resources, recorded-only information, legacy overrides and unknown ownership/grants. Combat tile tooltips expose available breakdowns. Typed armor and shield remain compatibility inputs, never inferred inventory ownership. No racial bonuses, class grants, equipment promotion, or custom-name interpretation are added.

## Security, caching and refresh

Authorization is checked before snapshots and at watch subscription/change processing. Projection caches contain no authorization decisions. Process-fixed rules/projection versions are exposed; future executable catalogs/effects require explicit version/revision integration rather than reusing this contract blindly. Manual-only metadata is not added to combat snapshot equality, preserving current transaction behavior.

Repositories increment an in-memory view generation only after a successful write. Failed persistence retains the previous state, cached projection and generation; stale writes publish nothing. A server watch holds one open-sheet subscription per player, bounded by connected-player count, and reevaluates affected view stamps when repository generation changes. View stamps include build/runtime/manual revisions, campaign revision, assignment, binding/controller and manager status. Operator changes are also checked. This avoids repeated client reads and re-parsing on every tick; a repository commit can still inspect all currently open watches.

Notifications carry only a random screen token and a revocation flag. They contain no character ID, sheet values or private details. New subscriptions catch changes between the initial read and subscription. Disconnect, screen closure and server shutdown clean up watches; late tokens cannot update another screen. Read-only subscriptions have a separate bounded rate limit. A lost/rate-limited subscription can fall back to manual Refresh.

Client refresh coalesces notifications, defers while currency edits are unsaved, retains edits on manual reconciliation, and preserves notifications received during an in-flight read. Failed reads stop automatic retry loops. Permission revocation clears the displayed character and invalidates pending callbacks. Manual Refresh remains available.

## Compatibility and parity evidence

Library schema 2, campaign schema 4, G `creationClosed`, independent campaign copies, approvals, bindings, custom data and Tactical behavior remain unchanged. There is no new migration, conversion mutation, persistent stat store or schema bump.

The first commit froze 277 legacy assertions before production changes. The later projection suite compares the unchanged decoder/formulas over 56 armor/DEX/shield combinations and the weapon/mode/ability/hand matrix, including rejected combinations and deterministic attack results. It also checks immutable provenance, invalid versions, negative modifiers, recorded level disagreement, sheet/encounter equality, dashboard output, approval/binding scope, unauthorized reads/watches, cache invalidation, stale writes, injected save failure, restart, independent records, Tactical isolation, library updates/deletion, revocation, disconnect and refresh coalescing.

All prior tests remain enabled and unchanged except adding suite entry points. Existing G/H workflow suites pass. Existing resource fault and transaction suites also pass. Two added GameTests exercise an authorized network projection read and committed runtime HP invalidation in the server environment. Automated tests do not substitute for visual Minecraft acceptance or testing the user's actual saved-world records.

## Validation

Java 21.0.12.1, Fabric Loom 1.8.13, Minecraft 1.21.1; existing Gradle configuration and dependencies.

- `bash ./gradlew --no-daemon --max-workers=4 build runGametest`: successful, 58 seconds.
- Final `foundationTest` after adding six display/library assertions: successful, 16 seconds; **14,001 assertions across 32 suites** (7,216 baseline + 277 frozen parity + 6,508 projection/refresh).
- Current XML: **122 GameTests, zero failures/errors**; all required tests passed. Baseline was 120.
- `git diff --check`: clean.
- Existing deprecated mock-player/Gradle warnings remain. The GameTest server logged a Minecraft public-key host lookup warning, but all required tests completed successfully.
- Graphical Minecraft was not run; visual verification remains pending.

Build environment used `JAVA_HOME=/workspace/.tools/jdk-21.0.12.1`, `GRADLE_USER_HOME=/workspace/.gradle`, and the existing proxy/system trust-store JVM options. No build configuration change was required.

JAR: `build/libs/tactical-combat-0.1.0.jar` (install the main JAR, not sources).

SHA-256: `d74fdb3785d4b24dc9c0d0f4faa557a45b3af40cb8527f8cb0532d8406df5122`.

## SRD provenance and limits

Uploaded `SRD-OGL_V5.1.pdf` identifies SRD 5.1 under OGL 1.0a, 403 pages. SHA-256: `d3f94417d2532f42a5abaec07e71a59007bf6cc46992c6458be6667f7a9f1e34`. Relevant armor/weapon, ability and combat sections were inspected as reference. It is not represented as the CC edition. Additional D&D Beyond/Wikimedia requests returned proxy 403. No external rules text or new executable content was imported, and existing Creative Commons attribution is unchanged.

Legacy gaps deliberately remain: typed equipment flags do not prove inventory ownership; recorded saves/checks have no new execution engine; class/race names grant nothing; speed keeps existing heavy-armor handling without inferred racial exceptions; virtual ammunition stays separate from inventory. No rule correction, new action/resource, spell effect, level-up, transaction rewrite or persistent receipt is introduced. Future corrections need separate verified tests and approval.

## Minecraft acceptance checklist

Use a backup/test copy of an existing world with Minecraft 1.21.1, Java 21 and its existing compatible Fabric dependencies. Install this JAR on both client and server, replacing the previous Cubetable JAR, then restart.

1. Open an older approved SRD character. Record HP/temp HP, AC, speed, initiative and proficiency; compare with the previous checkpoint and bound encounter values. Inspect source tooltips.
2. Use an existing legal weapon attack. Confirm attack modifiers, damage, ammunition and movement are unchanged, including armor nonproficiency and negative ability modifiers where available.
3. Keep the sheet open while an authorized runtime HP/temp-HP change occurs. Confirm it refreshes. Also use manual Refresh and verify the same results.
4. Stage inline currency edits, then make an authorized concurrent change. Confirm automatic refresh waits and manual reconciliation retains the draft. Complete/save edits and confirm fresh values appear.
5. Verify recorded level versus typed combat level disagreement is clearly informational and neither value changes automatically. Check recorded skills/saves retain their totals and informational wording.
6. Select a different character without rebinding. Confirm selected-versus-bound behavior and encounter approval restrictions remain unchanged.
7. Toggle a recorded inventory equipped flag on an editable character. Confirm it does not change typed combat AC or imply equipment integration.
8. Test owner, DM, unrelated player and revoked access. Private sheets must not become visible; an open revoked sheet must clear. Delete a watched personal draft and confirm it clears.
9. Open personal-library and independent campaign copies; change one and verify the other is unaffected. Confirm older custom fields survive normal editing/reload.
10. Smoke-test main hub, My Characters, management, creator, completed-record protections and Tactical profile. Test resize/reopen, leaving the sheet and disconnect/reconnect.

Stop after these checks for review; do not begin J2 automatically.

## Changed files

- `src/gametest/java/dev/tacticalcombat/combat/CharacterSyncGameTests.java`
- `src/main/java/dev/tacticalcombat/character/CharacterRepository.java`
- `src/main/java/dev/tacticalcombat/character/CharacterSyncService.java`
- `src/main/java/dev/tacticalcombat/character/LibraryRepository.java`
- `src/main/java/dev/tacticalcombat/client/ClientRulesWatch.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/client/ui/CharacterDashboardScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CharacterSheetUi.java`
- `src/main/java/dev/tacticalcombat/client/ui/DashboardModel.java`
- `src/main/java/dev/tacticalcombat/client/ui/RulesRefreshState.java`
- `src/main/java/dev/tacticalcombat/client/ui/SheetHeader.java`
- `src/main/java/dev/tacticalcombat/combat/SrdEncounter.java`
- `src/main/java/dev/tacticalcombat/combat/SrdMovementTurn.java`
- `src/main/java/dev/tacticalcombat/net/RulesChangedPayload.java`
- `src/main/java/dev/tacticalcombat/net/RulesWatchPayload.java`
- `src/main/java/dev/tacticalcombat/rules/srd/CharacterRules.java`
- `src/main/java/dev/tacticalcombat/rules/srd/DerivedRulesState.java`
- `src/main/java/dev/tacticalcombat/rules/srd/LegacySrdInputs.java`
- `src/main/java/dev/tacticalcombat/rules/srd/RulesProvenance.java`
- `src/main/java/dev/tacticalcombat/uiapi/CharacterWatchService.java`
- `src/main/java/dev/tacticalcombat/uiapi/RulesReadModel.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiApiService.java`
- `src/main/java/dev/tacticalcombat/uiapi/UiNetworking.java`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/LegacyRulesParityTests.java`
- `src/test/java/dev/tacticalcombat/character/RulesProjectionTests.java`
- `docs/gameplay-rules-checkpoint-j1.md` (this report).
