# Phase 1 real-client acceptance checklist

Status: **Not Verified** in the cloud run. The available machine has no `DISPLAY` or `Xvfb`; no graphical Minecraft client, production dedicated server with clients, or authenticated client connection was launched. Embedded GameTest players are not substitutes for these checks.

## Reproduce locally

1. Use a complete Java 21 JDK and this branch, then run `./gradlew build` and `./gradlew runGametest`. Dependencies remain pinned. Save the generated GameTest XML and client/server logs with the exact `git rev-parse HEAD`.
2. Create a fresh **disposable** Fabric 1.21.1 server using Loader 0.16.9, Fabric API 0.116.1+1.21.1 and `build/libs/tactical-combat-0.1.0.jar`. Start normally, read Minecraft's EULA, and accept it locally only if you agree. Use two separate client profiles with those same mod versions and this exact JAR. The unchanged 0.1.0 mod label alone does not establish protocol compatibility.
3. Copy the repository's `packs/generic_d20` and `packs/percentile` folders into each client's `config/tacticalcombat/packs`. Use ordinary survival players A and B; initially grant operator level 2 only to A. Keep authenticated server configuration for external connections. Use a backup/disposable world; creepers retain normal terrain destruction.
4. Prepare flat terrain, a two-high solid wall, one-block steps, stairs, slabs, a narrow passage, water/lava, a removable bridge and two groups of zombies. Add skeleton/pillager, guardian, slime, blaze and creeper cases separately. Avoid overlapping experimental scenarios when recording outcomes.

Record **Pass / Fail / Not Run**, observed behavior, logs and commit for every row. An unrun row remains open.

| Scenario | Procedure and expected result |
|---|---|
| Enrollment/protocol | Both updated clients see the same encounter/order. Join with the old audit-era JAR or no mod: the server explains the v2 requirement and never recruits that client, either on initial start or later recruitment. Confirm the old client remains usable outside tactical encounters. |
| Two encounters | Start separated fights with `/tbc start`, then approach their edges. No entity gains two owners; turns and resources advance independently; no cross-encounter attack lands. |
| Join boundary | A new supported hostile targeting an enrolled player joins at the documented range. A hostile targeting the other encounter does not migrate between owners. Verify nearby player recruitment, including incompatible clients. |
| Turn requests | Rapidly press End Turn and click moves/targets during state updates. One intent cannot advance multiple turns. For exact packet replay, use a development client harness that resends the same `RequestStamp`; then resend it after the player's next turn. The server must reject both. Plain rapid clicking is not proof of replay protection. |
| Melee legality | Try a normal click attack, tactical click, too-distant target and target behind the solid wall, on and off turn. Only a legal active-turn tactical attack resolves; rejected requests do not spend the action. |
| Ranged actions | Draw/release a bow; fire a charged multishot crossbow; let arrows land after End Turn and after shooter death/removal. One volley spends one action; distinct bolts can affect the same target subject to vanilla hurt resistance. Repeated release cannot generate another shot. |
| Specialized mobs | Test real AI windup and targeting for guardian/elder guardian beams, slime/magma-cube contact, blaze three-shot bursts, witch splash potions and creeper multi-target explosions. Observe actions and correct target boundaries. Direct GameTest invocation does not prove AI cadence. |
| Explicit limits | Evokers and wardens are not recruited; bosses remain excluded. Persistent clouds do not affect participants, and an enrolled creeper does not spawn its lingering cloud. Verify this is explained in the checkpoint rather than presented as full vanilla parity. |
| Interactions | On turn, equip armor, switch hotbar, move inventory stacks, consume food/potions/milk and use supported controls. Equipment is free; accepted consumable/control use costs the action. Try the same inventory/drop/offhand operations outside the turn: state must be resynchronized without duplication or loss. |
| UI/free operations | With empty hand, open chest/barrel/shulker UI outside turn. Viewing is allowed; slot mutations are denied. Read books and use sheets/dice/card UI. On turn, use armor stands and empty-hand merchant UI. |
| Rejected use | Try bow without ammunition, already-spent action, ender pearl/chorus fruit, unsupported item and terrain mining. No unauthorized effect or item loss occurs. Start drinking/drawing, end turn, then attempt delayed completion. |
| Movement | Traverse slabs, stairs and up/down one block; monitor the per-turn allowance. Insert a wall, remove an intermediate floor or move a combatant into the destination after path acceptance. Movement stops safely and only completed progress remains charged. Retry repeatedly; previously charged progress must not be refunded. |
| Dimensions/boundaries | Try large/tall bodies, narrow corners, fluids/hazards, border edges and chunk boundaries. Server positions stay legal even when the preview cannot find an alternative path. |
| Out-of-turn displacement | Jump, fall, swim, fly in creative (separate case), receive knockback, and send ordinary movement while waiting. Record rubber-banding/camera behavior and whether an actor remains pinned or consumes the permitted movement. Include server latency. |
| Lifecycle | Disconnect A on/off turn, remove a mob and change dimensions. Ownership and current-turn state clean up without affecting B's encounter. Reconnect does not restore an encounter in this phase: persistent recovery is explicitly deferred. |
| Operator advance | B cannot use `/tbc advance`. A can advance A's encounter, with a log entry. `/tbc endturn` only ends its caller's active turn. Test commands while animations or item use are active. |
| HUD/sync | Compare both clients after each action, rejected request, operator advance, death and disconnect. Order, action status, movement allowance and active actor converge. Watch for temporary selected-slot mismatch after rejected equipment packets. |
| Camera/fading | Enter/leave combat, rotate/zoom, move under ceilings and behind walls, switch dimension and reconnect. Camera angle does not accumulate during movement teleports; faded terrain returns normally. |
| Dice/cards | Burst rolls/cards from both clients. Pending queues remain bounded; evicted dice results still reach chat, card history remains usable. Verify scoped audience and readable animations. |
| Manual packs | Open and resize each sheet, edit/save/reopen characters, evaluate derived fields and roll in both Generic D20 and Percentile. Do not treat these manual rolls as authoritative combat results. |

## Acceptance and failure handling

Do not promote this to a Phase 1 release until the relevant rows pass on two real clients. Preserve any failed case with exact reproduction and logs. Confirm failures before attributing them to the new adapters. Server restart recovery, campaign persistence, SRD combat, classes and spells remain later-phase work; do not use this checklist to imply they exist.

Before Phase 2, resolve any authority or state-synchronization failure, then introduce rules interfaces and authoritative character binding in a separate change. Keep this test suite and both manual packs as regression gates.
