# Cubetable J2.5E — cinematic dice presentation

Base: `7d9adb43f9175df803f47f4ac49042d76a24a826` on `dev/gameplay-authoritative-rolls-checkpoint-j25d1`. Delivery branch: `dev/gameplay-cinematic-dice-checkpoint-j25e`; the final revision is the HEAD of that branch (the delivery commit is reported separately after commit).

## Scope and architecture

This checkpoint changes client presentation only. DiceService, CheckService, J2 transactions, character records, combat calculations and campaign schema remain unchanged. The client constructs `PresentationRoll` from either the existing manual `DiceRollPayload`, an authorized D1 `CheckService.Result` version 1 JSON object, or a permission-filtered `CHARACTER_CHECK` event. No animation path calls a dice RNG, revises a modifier or invents a DC. A restricted acknowledgement with no `result` cannot create a presentation. The operation UUID links D1 replies and events for deduplication; a later full result can enrich an already queued event without starting a second roll. Manual packets carry no operation UUID, so their presentation identity is local to a received packet.

`PolyhedralMesh` caches convex tetrahedron, cube, octahedron, pentagonal trapezohedron, dodecahedron and icosahedron meshes. Percentile rolls use two D10 meshes for tens and ones. Mesh tests check face count, Euler characteristic, edge incidence, planar polygons and outward normals. `DieOrientation` rotates the selected server face normal toward the viewer; tests cover every face. A CPU projection gives perspective, shaded faces, gold edges and a contact shadow in the existing Minecraft GUI render pipeline. The final face label is taken from the authorized result. Intermediate visible labels follow fixed geometric face numbering; they do not represent another result.

`RollMotion` is a seconds-based INTRO → TUMBLE → LAND → REVEAL → DISMISS state machine. Entry momentum, three-axis decaying orientation, contact bounce, shadow response and final hold are independent of frame rate. Quick defaults to a 0.48-second tumble; Cinematic defaults to 1.15 seconds. Hold defaults to three seconds. The O key skips to the result without sending any gameplay request. Reduced motion reveals the result immediately. The overlay draws above Cubetable RPG screens as well as in normal gameplay; existing HUD event history and the manual toolbar remain available.

Visual animations queue at most 12 rolls. If the queue overflows, the omitted authorized result is printed in client chat so it remains readable; no server action is retried. The overlay animates up to six visible die meshes per roll, while the authorized full face list and total remain in the calculation/result view. Seen operation identities are bounded to 4096 per client session. The server's D1 operation semantics still provide the authoritative replay protection; client visual deduplication is for presentation only. Disconnect clears the queue and seen IDs; permission revocation and campaign/role changes clear pending private animations. Event refresh does not replay historical rolls.

The separate local `config/tacticalcombat/dice-presentation.json` stores Quick/Cinematic defaults and overrides, speed, reduced motion, sound mute/volume, result hold and die size. The V toolbar's Roll Window opens the settings. It does not modify world schema 5. The five short OGG cues (movement, clatter, impact, bounce, settle) are original deterministic synthesized assets produced by `tools/generate_dice_sounds.py` with no samples; these assets are released under CC0 1.0. Minecraft's master sound control still applies.

## Validation

Run with Java 21 and the repository Gradle setup:

```sh
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog
```

Final result: **BUILD SUCCESSFUL** (1m 5s). Foundation: 20,028 assertions across 39 suites, including 789 new cinematic dice checks; GameTests: all 139 required tests passed; catalog: 2,614 checks across 623 entries. The tests include deterministic geometry, face orientation, authoritative result adaptation, advantage/disadvantage, D100/manual dice, motion phases, reduced motion, local settings bounds, deduplication, overflow and privacy-filtered data. Existing foundation, J2/J2.5, D1, catalog and GameTests remain required. No graphical client rendering was available in this environment, so animation quality, sound balance, GUI scale behavior and visual acceptance remain unverified in Minecraft.

## Minecraft visual acceptance checklist

1. On a test world, press V and roll D4, D6, D8, D10, D12, D20 and D100. Check each silhouette, face, total, sound timing and percentiles.
2. Roll several dice and a D20 with advantage/disadvantage. Check that both D20 faces appear, the authoritative kept die is marked, and an equal-face tie remains understandable.
3. Open V → Roll Window → Dice animation & audio settings. Try Quick/Cinematic overrides, speed, die size, sound volume/mute and reduced motion. Press O during a roll; verify it reveals the existing result.
4. Open J → Abilities, Skills & Saves. Execute an official check; compare the face, kept index, modifier and total to the authorized result view. Repeat from a DM request in Rolls, including a DC known only to authorized viewers.
5. Compare DM, recipient and uninvolved player clients for public/restricted results. A restricted acknowledgement must reveal no face, total, modifier or DC.
6. Roll repeatedly, navigate/resize screens, disconnect/reconnect and inspect the event/history views. Check for duplicate animations, stuck overlays or lost authorized results.
7. Try GUI scales 2, 3 and 4 and a constrained screen size. Confirm readability and no serious frame drops.
8. Start the existing controlled encounter and confirm attack/damage behavior and quick panels remain intact.

Do not use the primary world for exploratory tests. Neither code tests nor GameTests constitute visual acceptance.

## Changed files and limitations

Changed files:

- `docs/gameplay-cinematic-dice-checkpoint-j25e.md`
- `src/main/java/dev/tacticalcombat/client/ClientRulesWatch.java`
- `src/main/java/dev/tacticalcombat/client/DiceAnimation.java`
- `src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java`
- `src/main/java/dev/tacticalcombat/client/TacticalScreen.java`
- `src/main/java/dev/tacticalcombat/client/dicevisual/DicePresentationSettings.java`
- `src/main/java/dev/tacticalcombat/client/dicevisual/DieOrientation.java`
- `src/main/java/dev/tacticalcombat/client/dicevisual/PolyhedralMesh.java`
- `src/main/java/dev/tacticalcombat/client/dicevisual/PresentationQueue.java`
- `src/main/java/dev/tacticalcombat/client/dicevisual/PresentationRoll.java`
- `src/main/java/dev/tacticalcombat/client/dicevisual/RollMotion.java`
- `src/main/java/dev/tacticalcombat/client/ui/CheckInboxScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CheckRollScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/CubetableHud.java`
- `src/main/java/dev/tacticalcombat/client/ui/DicePresentationScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/DiceToolbarScreen.java`
- `src/main/java/dev/tacticalcombat/client/ui/RpgScreen.java`
- `src/main/resources/assets/tacticalcombat/sounds.json`
- `src/main/resources/assets/tacticalcombat/sounds/dice_bounce.ogg`
- `src/main/resources/assets/tacticalcombat/sounds/dice_clatter.ogg`
- `src/main/resources/assets/tacticalcombat/sounds/dice_impact.ogg`
- `src/main/resources/assets/tacticalcombat/sounds/dice_move.ogg`
- `src/main/resources/assets/tacticalcombat/sounds/dice_settle.ogg`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/client/ui/CinematicDiceTests.java`
- `tools/generate_dice_sounds.py`

Rendering uses CPU projection into Minecraft GUI geometry, not 3D world physics or a GPU depth buffer. Physical feel and audio quality need player review. The six-mesh visual cap deliberately bounds per-frame work on large manual rolls. There is no client-facing replay control; retrying an accepted D1 operation does not replay an animation. Manual packets have no durable operation identity. These are presentation limits, not changes to authoritative dice rules or visibility.

Built JAR: `build/libs/tactical-combat-0.1.0.jar`. SHA-256: `b18860ea703d710eb56ef2b0c64899d6097be352021a2be659a3237454e7d674`.
