# Gameplay transactions checkpoint J2 — work in progress

Base: `d5057c937cc7e1e258f49b6ce1cebf71f471e5c5`.
Branch: `dev/gameplay-transactions-checkpoint-j2`.

## Stage A: traced baseline and selected invariants

The existing path is client preview → authenticated `SrdNetworking` → current turn/controller admission → `SrdEncounter.preview` → server-owned intent and snapshots → confirm validation of snapshots/positions/cover → action reservation → server RNG → fixed damage/health candidate → `CharacterRepository.commitSrdAction` → one atomic campaign replacement → encounter snapshots/movement reconciliation → events and private result acknowledgement. Both participant runtime revisions advance, including misses/no-op HP. `commitSrdHealth` separately supports existing healing. `TurnRequests` only retains per-turn request UUIDs in memory. Ending or restarting loses pending rolls, requests, turn/action state and world claims; already-saved resources remain.

`AtomicJsonFile` validates candidate bytes, writes a last-good backup, forces a temporary file, and uses same-directory atomic rename before repository publication. Its existing fault seam fires before replacement. These are filesystem guarantees, not atomic transactions with Minecraft entity/world saves or guarantees across administrator backup rollback.

Before production changes, 36 new frozen checks pass against the unchanged repository: melee/ranged/thrown hit/miss resource effects, both runtime revisions, stale retry rejection and coherent reload. All prior 14,029 assertions also pass (14,065 total, 34 suites). Existing live tests additionally cover fixed-roll failure retries, action reservation and preservation of 25 feet after a failed ranged action.

Selected conservative design:

- Persist typed operation plans/receipts inside campaign schema 5; retain readers for schemas 1–4 and preserve library schema 2/SRD v1.
- The server-generated preview intent UUID is the operation ID. Its canonical identity includes authenticated requester, campaign/encounter/turn, participants, revisions, weapon/mode/ability/hands and validated geometry context. Network request UUIDs remain transport identities; they cannot supply outcomes or reset budgets.
- VALIDATED is a trusted in-memory candidate. PREPARED stores fixed RNG and before/after resource expectations without applying resource changes. COMMITTED applies both participants and the receipt in one candidate store replacement.
- Preserve an in-memory reservation if preparation itself fails; disclose no accepted roll. Same-process DM retry may persist those same retained rolls. No durable record exists if preparation never succeeded, so crash recovery cannot claim to recover that outcome.
- Durable PREPARED operations reserve both participant records across encounter end/restart. New encounters and conflicting resource writes cannot bypass the reservation.
- No automatic encounter, movement or damage resumption after restart. Unresolved durable preparations are inspectable by current campaign managers and have an explicit audited cancellation path after the old encounter is closed. Cancellation retains fixed outcomes and changes no gameplay quantities. Ordinary players cannot cancel.
- Retain at most 1,024 receipts with no eviction, including cancellation audit records. At capacity reject new preparations without spending resources. There is no pruning/reset endpoint. This deliberately favors replay safety over unlimited historical throughput; a later reviewed epoch/archive design is needed before expanding that bound.
- Recheck current permissions, controllers, bindings, approvals and expected state on live retry. Receipt inspection filters private results; unknown/future transaction formats fail closed.
- Keep the current one-action weapon semantics. Introduce an SRD-specific bounded budget with explicit unsupported bonus/reaction/interaction slots; movement remains owned by the existing movement turn and resource reconciliation never resets spent distance.

This is the pre-implementation design, not a completion claim. Final validation, fault evidence and delivery details will replace this work-in-progress report.
