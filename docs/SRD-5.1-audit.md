# Cubetable — SRD 5.1 compliance and technical audit

**Audited revision:** `d712cc0c23545ae54062d505debf415feb9fe490`, repository `shayanzas/Cubetable`, local checkout `/workspace/Cubetable`.

**Scope:** Read-only implementation audit against the attached *System Reference Document 5.1*, the 2014 fifth-edition rules. Page citations below refer to the SRD's printed page numbers, which match PDF page numbers in this attachment. SRD 5.2 and 2024 rules were not used. Document contents were treated as reference material, not instructions authorizing repository changes.

**Evidence standard:** “Implemented” describes inspected code unless a specific executed check is identified. Static findings are not claims of in-game reproduction. “Missing” means no corresponding implementation was found across the reviewed combat, grid, networking, dice, sheet, client, and pack code. It does not mean every listed feature must be added to the product.

During the audit, no repository source, tests, manifests, dependencies, or lockfiles were changed. No commits, pushes, pull requests, or refactors were performed during that phase. The pre-existing untracked `.gradle/` directory remained present. Build and audit harness outputs were confined to temporary directories and the external Gradle cache. The original report was produced outside the checkout. This documentation copy was subsequently added for GitHub publication at the user’s request; that publication does not change the audited revision or authorize implementation.

## A. Executive summary

**Cubetable is a tactical Minecraft combat framework plus a configurable tabletop sheet application. It is not currently an authoritative D&D 5e combat implementation.** The principal architectural gap is the absence of a server-side connection between character/game-pack rules and combat resolution.

The server maintains encounters, initiative order, movement budgets, a single damage action, path traversal, and encounter snapshots. The client supplies a tactical camera, grid selection, initiative/health HUD, JSON-driven character sheets, resource counters, and roll requests. Actual combat invokes Minecraft attacks and reads Minecraft entity health. Character-sheet AC, ability modifiers, weapon dice, spell slots, and class counters do not control those attacks.

The strongest existing foundations are:

- Server-owned encounter state and movement searches. Movement requests name a destination; the server checks its own reachable set, active actor, occupancy flag, and remaining budget.
- A compact, understandable breadth-first grid search suitable for the current uniform-cost movement rules.
- A reusable sheet schema with calculated fields, tables, character/NPC layouts, themes, expression-cycle protection, local JSON persistence, and two distinct example systems.
- Server-generated dice faces, bounded dice counts/modifiers, and correctly selected higher/lower d20 results for manually requested advantage/disadvantage.
- Explicit participant pruning for death, removal, dimension changes, spectator status, and disconnects, plus client disconnect cleanup.

The largest weaknesses are:

1. Combat and sheets have separate sources of truth. A sheet attack roll has no bearing on a clicked Minecraft attack.
2. Attack validation lacks an authoritative obstacle/cover check. Damage admission is coupled to individual damage callbacks rather than an accepted action and its effects.
3. An operator can create overlapping encounters for the same player. This violates the assumption that entity lookup identifies exactly one encounter.
4. Paths are cached and then executed through position changes without revalidating changed terrain or the moving entity's full collision shape.
5. Core SRD mechanics—attack versus AC, reactions, conditions, death saves, and action-linked spellcasting—are absent or represented only as manual sheet controls.

**Readiness:** Suitable as a prototype and manual tabletop aid, subject to the findings below. It is not ready to claim automated SRD 5.1 combat fidelity or robust adversarial multiplayer enforcement. No Critical issue was established. Several High findings warrant correction before advanced rules development.

### Build and verification evidence

| Component | Repository requirement |
|---|---|
| Java | 21; release/source/target 21 in [build.gradle:38](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/build.gradle#L38) |
| Gradle | Wrapper 8.10.2, [gradle-wrapper.properties:3](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/gradle/wrapper/gradle-wrapper.properties#L3) |
| Fabric Loom | 1.8.13, [build.gradle:2](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/build.gradle#L2) |
| Minecraft | 1.21.1, [gradle.properties:5](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/gradle.properties#L5) |
| Yarn mappings | 1.21.1+build.3, [gradle.properties:6](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/gradle.properties#L6) |
| Fabric Loader | 0.16.9, [gradle.properties:7](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/gradle.properties#L7) |
| Fabric API | 0.116.1+1.21.1, [gradle.properties:15](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/gradle.properties#L15) |

An exact `git archive HEAD` export was built in `/tmp/cubetable-audit-build`, using the checked-in wrapper, unchanged dependencies, Java's current network proxy, and four Gradle workers. Fabric Loom and Minecraft dependencies resolved, and Loom reached remapping. The build then failed at `:compileJava` with:

> Toolchain installation '/usr/lib/jvm/java-21-openjdk-amd64' does not provide the required capabilities: [JAVA_COMPILER]

This is an **environment/toolchain failure, not evidence of a source compilation defect**. The machine has Java 21.0.12.1 and the `jdk.compiler` module, but no `javac` executable accepted by Gradle's toolchain detection. The earlier onboarding statement that a complete JDK was verified was too strong; the runtime was verified. No dependency downgrade or build-file workaround was made.

Using `java -m jdk.compiler/com.sun.tools.javac.Main`, six actual repository classes were compiled separately with cached Gson: `DiceType`, `DiceSpec`, `Expr`, `SheetFormat`, `CharacterData`, and `SheetContext`. A temporary audit harness completed **132 passing assertions**: two pack-format parses, three character JSON round trips, 80 finite derived-value evaluations, all 30 standard ability-score modifier values, valid/invalid dice syntax cases, one roll-formula result, and cycle detection. These are limited audit checks, not a repository test suite or combat tests.

Four additional diagnostic probes reproduced unsupported dice expressions being accepted with incorrect or invalid plans; see F10. A passing harness does not cancel those findings.

No existing automated test suite was found. The mod JAR build, dedicated-server startup, graphical client, rendering compatibility, and multiplayer gameplay remain **unverified**. No game server or client was launched. A complete JDK 21 installation and a suitable client display/OpenGL environment are prerequisites for those checks.

## B. SRD compliance matrix

**Classification boundaries:** Core rules apply when the corresponding D&D mechanic is supported. Class and monster features apply only to selected classes/stat blocks. Optional rules are not required for baseline support. “Intentionally customized” is used only where comments, documentation, or explicit constants establish a Minecraft adaptation; it does not imply approval of that adaptation for a future SRD mode.

| Mechanic | Classification | SRD 5.1 reference | Implementation evidence and assessment |
|---|---|---|---|
| Initiative and Dexterity | **Incorrect** for SRD combat | Initiative, p. 90 | [Combat.java:66](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L66), [Combat.java:84](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L84): every entity receives an unmodified d20. Sheet `init = dex_mod` at [format.json:269](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L269) is unrelated to encounter initiative. |
| Initiative ties and identical monsters | **Intentionally customized** | Initiative, p. 90 | [Combat.java:88](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L88): player wins a player/monster tie; remaining ties retain sort order. Each mob rolls separately rather than the SRD's grouped roll for identical creatures. Tie adjudication is normally a player/GM decision. |
| Round order | **Partially implemented** | The Order of Combat, pp. 90–91 | [Combat.java:128](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L128), [Combat.java:282](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L282), [Combat.java:348](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L348): cyclic turns and round increments exist; insertion/removal are handled. No six-second rules clock or explicit late-entry rules contract. |
| Surprise | **Missing** | Surprise, p. 90 | Aggro starts encounters at [CombatManager.java:68](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L68); no Stealth versus passive Perception or first-turn surprise restrictions. |
| One action and split movement | **Partially implemented** | Your Turn, p. 90; Breaking Up Your Move, p. 91 | [Combat.java:120](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L120), [Combat.java:155](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L155), [Combat.java:189](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L189): movement can be split around a melee attack. One boolean limits damage actions; it is not a general action system. |
| Bonus actions | **Missing** as usable mechanics | Bonus Actions, pp. 90–91 | [Combatant.java:20](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combatant.java#L20), [Combatant.java:54](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combatant.java#L54): boolean is reset and transmitted but never spent by an ability. HUD displays it at [CombatHud.java:119](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CombatHud.java#L119). A bonus action requires a granting feature, not a universal extra action. |
| Reactions and Ready | **Missing** | Reactions, p. 91; Ready, pp. 93–94 | [Combat.java:120](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L120) rejects non-active attackers; no reaction budget, trigger, interruption, or ready state. Reactions recover at the start of the actor's next turn. |
| Movement speed and scale | **Intentionally customized** | Movement and Position, pp. 91–92 | [CombatConfig.java:25](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatConfig.java#L25): eight player squares, six mob squares. [Grid.java:22](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/grid/Grid.java#L22): one square is one block column, cardinal movement only. No conversion from a character's speed in feet. |
| Difficult terrain and creature spaces | **Partially implemented** | pp. 85, 91–92 | [Grid.java:66](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/grid/Grid.java#L66), [Combat.java:637](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L637): friendly passage and occupied endpoints exist, but all steps cost one. Creature spaces do not add difficult-terrain cost; hostile size exceptions and multi-cell spaces are absent. |
| Climb, swim, jump, fall | **Intentionally customized** | Special Types of Movement, p. 85; Falling, p. 86 | [Grid.java:85](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/grid/Grid.java#L85): one-block ascent/three-block descent; liquid destinations rejected. [Combat.java:401](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L401) resets fall distance while sliding. No Strength-based jumps, climbing/swimming costs, or SRD fall damage/prone resolution. |
| Dash | **Missing** | Actions in Combat, p. 93 | No action that adds movement equal to modified speed; budgets only reset at [Combat.java:142](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L142). |
| Disengage | **Missing** | p. 93 | No suppression state; opportunity attacks are also absent. |
| Dodge | **Missing** | p. 93 | No disadvantage against visible attackers or Dexterity-save advantage until next turn; no incapacitated/speed-zero termination. |
| Help, Hide, Search, Use an Object | **Missing** as rules actions | pp. 93–94 | Vanilla interaction callbacks at [TacticalCombatMod.java:70](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/TacticalCombatMod.java#L70) do not implement these actions, DCs, free-interaction limits, or action costs. |
| Attack action / multiple attacks | **Partially implemented** | Attack, p. 93; Fighter, pp. 24–25; Monster Actions, p. 259 | One clicked melee attack at [Combat.java:189](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L189). No Extra Attack, separate attacks remaining, two-weapon bonus attack, or monster Multiattack. |
| Opportunity attacks | **Missing** | p. 95 | [Combat.java:401](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L401) has no reach-exit trigger. Requires visible hostile voluntarily leaving reach; occurs before exit and uses reaction. Teleportation/forced movement exceptions need separate handling. |
| Attack rolls versus AC | **Missing** in combat | Making an Attack, p. 94 | [Combat.java:212](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L212) uses `player.attack`; [Combat.java:540](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L540) uses `mob.tryAttack`. Sheet attack formulas at [format.json:1118](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L1118) only request displayed dice. No d20 total comparison with target AC. |
| Weapon damage and modifiers | **Partially implemented** in sheets; **Missing** in combat | Damage Rolls, pp. 96–97; Weapons, pp. 65–67 | [format.json:1127](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L1127) represents dice and ability/weapon modifiers; [DiceService.java:16](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/dice/DiceService.java#L16) broadcasts rolls. Damage comes from vanilla attacks, not those results. No general mixed damage-dice expression. |
| Natural 1, natural 20, critical damage | **Incorrect** presentation; **Missing** combat resolution | Rolling 1 or 20, p. 94; Critical Hits, pp. 96–97 | [DiceAnimation.java:103](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/DiceAnimation.java#L103) labels every single-d20 20 a “Critical hit!” and 1 a “Critical fail!”, including checks/saves. No SRD automatic attack miss/hit or double damage dice integration. |
| Manual advantage/disadvantage selection | **Correctly implemented**, narrow arithmetic scope | Advantage and Disadvantage, p. 76 | [DiceService.java:20](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/dice/DiceService.java#L20) rolls two d20s; [DiceRollPayload.java:47](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/net/DiceRollPayload.java#L47) keeps higher/lower and adds the modifier once. This does not establish automatic rules qualification. |
| Advantage/disadvantage sources and cancellation | **Missing** | p. 76 | The client submits one mode at [CharacterSheetScreen.java:1965](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L1965). No source aggregation, cancellation, feature eligibility, or reroll handling. |
| Reach / ranged attacks | **Intentionally customized** | Melee/Ranged Attacks, p. 95 | [Grid.java:30](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/grid/Grid.java#L30): 3.2 blocks eye-to-hitbox for players; [Combat.java:561](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L561) uses mob-width distance formula. No weapon reach/range profiles, long-range disadvantage, or adjacent-hostile ranged penalty. |
| Cover | **Missing** | Cover, p. 96 | [Combat.java:198](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L198) checks distance, not terrain. No total-cover rejection or +2/+5 AC and Dexterity-save bonuses. F02. |
| Visibility, hiding, unseen attacks | **Missing** as SRD rules | Vision and Light, p. 86; Unseen Attackers and Targets, p. 94 | Camera picking at [MousePicker.java:33](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/MousePicker.java#L33) uses camera visibility and can skip faded blocks. No actor-perspective senses, hidden-location uncertainty, or unseen-roll modifiers. |
| Ability scores / modifier formula | **Correctly implemented**, sheet scope | Ability Scores and Modifiers, p. 76 | [format.json:245](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L245) uses `floor((score - 10) / 2)` for all six abilities. Strength formula exercised for scores 1–30. No combat entity binding. |
| Proficiency | **Partially implemented** | Character Advancement, p. 56; Proficiency Bonus, p. 77 | [format.json:64](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L64) is a manually editable bonus defaulting to +2; [format.json:252](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L252) applies it to saves and skills. No automatic level progression or server eligibility validation. |
| Ability checks, skills, saving throws | **Partially implemented** | pp. 77–83 | [format.json:252](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L252), [format.json:333](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L333) provide modifier formulas and rolls. No authoritative DC, contest resolution, or resulting effects. Eleven skill entries, rather than a complete 18-skill SRD sheet. |
| HP, healing, temporary HP | **Partially implemented** in sheets; **Intentionally customized** live health | pp. 96–99 | [CharacterSheetScreen.java:453](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L453) caps manual healing; [CharacterSheetScreen.java:480](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L480) absorbs manual damage with temp HP first. [Combat.java:700](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L700) sends vanilla HP. No connection between the two pools; no complete temp-HP granting/expiry policy. |
| Zero HP, stabilization, death saves | **Missing** as mechanics | pp. 97–98 | [format.json:707](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L707) provides editable success/failure pips; [Combat.java:263](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L263) removes dead entities. No unconscious state, start-turn death save, natural-1/two-failures, natural-20/1-HP, damage-at-zero, or stabilization engine. |
| Conditions and exhaustion | **Missing** as rules effects | Appendix PH-A, pp. 358–359 | [format.json:721](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L721) supplies an exhaustion counter. No duration/effect model that changes movement, checks, attacks, reactions, or consciousness. Vanilla effects are not a substitute for SRD conditions. |
| Class features | **Partially implemented** as bookkeeping | Selected class sections; Fighter pp. 24–25 | [format.json:694](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L694) shows Second Wind and Action Surge counters. No class ownership, healing effect, bonus-action spend, extra action, or level-17 two-use handling. Other class features are unsupported. |
| Spell DC / spell attack math | **Correctly implemented**, sheet scope | Saving Throws / Attack Rolls, p. 103 | [format.json:282](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L282) uses `8 + prof + spell_mod` and `prof + spell_mod`. No server-side spell resolution. |
| Spell slots and casting | **Partially implemented** as bookkeeping | Wizard table p. 52; Spellcasting pp. 100–103 | [format.json:273](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L273) provides a full-caster-style slot table; [CharacterSheetScreen.java:881](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L881) decrements local slots and optionally rolls dice. No prepared/known checks, components, casting-time costs, legal targets, effects, or class-specific slot policies. |
| Upcasting | **Partially implemented** | Casting at a Higher Level, p. 100 | [CharacterSheetScreen.java:887](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L887) can select a higher slot but reuses the same dice formula. It does not apply spell-specific scaling. |
| Concentration / durations | **Missing** | Duration / Concentration, p. 102 | No single-concentration ownership, Constitution save at max(10, half damage), incapacitation cleanup, or turn/round expiry. |
| Area-of-effect targeting | **Missing** | Areas of Effect, pp. 102–103 | [AttackRequestPayload.java:9](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/net/AttackRequestPayload.java#L9) carries only an entity ID; no spell origin/shape, affected set, line of effect, or per-target saves. |
| Damage types, resistance, vulnerability, immunity | **Missing** as SRD mechanics | Damage Types / Resistance, p. 97; Monsters p. 257 | [CombatManager.java:137](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L137) admits vanilla `DamageSource`; no SRD typed damage pipeline or stat-block traits. |
| Monsters and stat blocks | **Intentionally customized** live mobs; **Missing** SRD binding | Monsters, pp. 254–260 | [CombatManager.java:43](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L43) chooses Minecraft `Monster` mobs and excludes wither/dragon. [Combat.java:468](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L468) plans nearest-player movement and delegates special attacks to vanilla AI. NPC sheets are not mob stat assignments. |
| Rest and recovery | **Partially implemented** as manual sheet resets | Resting, p. 87; class recovery rules | [format.json:728](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L728), [CharacterSheetScreen.java:1516](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L1516) reset local fields instantly. No duration, interruption, 24-hour limit, minimum starting HP, Hit Dice spend/recovery, or exhaustion recovery conditions. |
| Grapple, shove, prone, squeeze | **Missing** | pp. 91–92, 95–96 | No contested Athletics/Acrobatics action, size policy, prone movement, or squeezing penalties. |
| Feats, multiclassing, variant encumbrance | **Missing; optional scope** | Multiclassing pp. 56–59; Feats p. 75; Variant Encumbrance p. 80 | No rules implementations. Their absence is not itself a baseline compliance defect. |
| In-game client/server behavior | **Unable to verify** dynamically | Not an SRD rule | Static implementations inspected; full build and runtime checks blocked as described in A. |

The SRD does not supply a Minecraft block conversion or mandate this mod's four-directional grid. A chosen grid/diagonal policy must be documented as a product adaptation; do not attribute a diagonal-counting rule from another book or edition to this SRD audit.

## C. Technical findings

Severity reflects impact on the intended tactical/multiplayer workflow. **Confirmed static** means the condition follows from the inspected code; verification steps remain proposed unless expressly marked executed. Feature gaps are distinguished from defects in already implemented behavior.

### F01 — High: a player can belong to overlapping encounters

**Evidence:** [TacticalCombatMod.java:90](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/TacticalCombatMod.java#L90)–101 calls `startAround` without checking existing membership. [CombatManager.java:104](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L104)–121 excludes existing combatants from enemy/additional-player searches but unconditionally executes `party.add(center)`. [CombatManager.java:32](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L32) returns the first encounter containing an entity.

**Expected:** At most one encounter owns an entity, or a deliberate merge operation resolves overlap.

**Actual:** An operator already in combat can invoke `/tbc start` when another eligible, unassigned hostile is nearby. The center player is added to a second encounter. Both can tick/freeze/sync that player, while player requests route to the first match.

**Impact:** Conflicting movement, HUD snapshots, active-turn state, and end-combat messages. This is an operator-accessible lifecycle defect, not an ordinary unprivileged command exploit. A crash was not reproduced.

**Correction:** Guard membership at the manager boundary, index entity UUID to encounter, and make reject/merge behavior explicit. Validate center eligibility as well.

**Verify:** Start a fight, introduce an unassigned hostile, invoke start again, and assert one membership, one snapshot owner, and correct routing after either encounter ends.

### F02 — High: custom melee requests do not validate obstacles

**Evidence:** [Combat.java:189](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L189)–214 validates actor/turn, target membership, life, and eye-to-hitbox distance, then directly invokes `player.attack`. [Combat.java:538](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L538) and [Combat.java:561](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L561) directly invoke mob melee using distance. Neither path queries collision/raycast/cover. [MousePicker.java:76](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/MousePicker.java#L76) deliberately skips faded blocks for camera selection.

**Expected:** Server-side geometry determines whether the specific attack can reach the target; total cover blocks direct attacks under SRD p. 96. Camera transparency must not confer attack permission.

**Actual:** A nearby target behind a wall passes Cubetable's player-attack validator. Mob direct-melee code also lacks an obstacle check. Client picking is neither sufficient authority nor equivalent to attacker line of effect.

**Impact:** Attacks through obstacles are admitted by the mod's validation. Exact resulting damage and interaction with other mods require runtime reproduction.

**Correction:** Centralize weapon-specific reach and line-of-effect validation on the server; compute SRD cover in the rules layer. Preserve camera fading for usability.

**Verify:** Test a one-block wall, full wall, fence/slit, partial cover, vertical separation, and a target visible only from the tactical camera. Send the same request directly from a test client.

### F03 — High: action accounting is attached to damage callbacks

**Evidence:** [CombatManager.java:137](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L137)–150 calls [Combat.java:120](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L120)–125 for each attacker-attributed damage event. [Combat.java:528](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L528) allows ranged/special mobs to run vanilla AI; successful damage sets `actionUsed`.

**Expected:** An accepted action spends its cost once, and all authorized effects retain that action's identity. Misses still consume the relevant attack; legitimate multi-target effects must not consume a fresh action per victim.

**Actual:** The first admitted damage callback consumes the action; subsequent callbacks from the same participant are rejected. A delayed projectile callback is assessed against the attacker's turn at impact, not an accepted attack at launch. Direct click attacks separately set `actionUsed` after invoking vanilla attack.

**Impact:** Multi-victim explosions/sweeps and delayed ranged hits do not have coherent turn semantics. For an attacker that remains enrolled, a projectile landing after their turn is rejected; after removal, the attacker is no longer gated by this hook. Exact creeper/projectile timing was not exercised.

**Correction:** Introduce an action-resolution context with actor, encounter, action ID, permitted effects/targets, and completion policy. Damage validation should distinguish authorized effects, reactions, environmental damage, and unrelated vanilla damage.

**Verify:** One accepted action affecting two victims, projectile landing before/after turn end, missed projectile, attacker death/removal before impact, sweep attack, retaliation, and damage from multiple independent actions.

### F04 — High: the encounter boundary is not enforced on incoming damage

**Evidence:** [CombatManager.java:137](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L137) looks up only `source.getAttacker()`. If that attacker is not enrolled, it returns true; it never uses victim membership to admit or recruit an external attacker. [CombatManager.java:62](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L62) detects periodically; [Combat.java:230](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L230) recruits every ten ticks.

**Expected:** An explicit policy determines how outsiders, neighboring encounters, projectiles, summons, and environmental effects affect a paused participant.

**Actual:** A living outsider can damage a combat participant without spending a combat action. Conversely, an enrolled actor's generic damage callback is not constrained to the same encounter, although custom clicked melee is.

**Impact:** Turn order can be bypassed at encounter boundaries. Automatic recruitment does not close the interval before recruitment or cover every possible attacker.

**Correction:** Apply victim-aware encounter policy before damage; choose recruit/merge/reject/allow behavior with user approval. Track source action separately from current membership.

**Verify:** Attack from outside join range, immediately before recruitment, from a second encounter, and with a projectile launched before combat. Check environmental damage separately rather than automatically suppressing it.

### F05 — High: server movement enforcement omits vertical displacement

**Evidence:** [Combat.java:372](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L372)–391 only checks player X/Z displacement and teleports using the player's current Y. Ground-mob freezing also retains Y. [Combat.java:587](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L587)–611 charges free-walking mobs only for horizontal distance. [KeyboardInputMixin.java:17](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/mixin/client/KeyboardInputMixin.java#L17) blocks jump input on the normal client.

**Expected:** Every rules-relevant voluntary movement component is validated and charged; forced movement and falling follow an explicit policy.

**Actual:** Cubetable does not charge or constrain vertical player movement in its server freeze, and flying/swimming fallback movement can change height without spending movement. Client jump suppression is not server authority. Horizontal tolerance is measured against a fixed anchor, so this is not evidence of unlimited cumulative horizontal drift.

**Impact:** Unequal movement accounting and possible out-of-turn repositioning under legal physics, other mods, or altered clients. Vanilla movement checks still apply; arbitrary unrestricted flight is not established.

**Correction:** Define movement modes and forced-motion handling, validate full displacement, and keep gravity/knockback behavior intentional.

**Verify:** Jump packets, ladders, water columns, knockback, falling terrain, upward-only flight, and diagonal three-dimensional movement at zero budget.

### F06 — High: accepted paths are stale and not entity-shape-aware

**Evidence:** [Combat.java:155](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L155)–168 trusts the cached grid; [Combat.java:401](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L401)–437 changes entity positions along saved waypoints without fresh collision checks. [Grid.java:103](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/grid/Grid.java#L103) assumes a roughly player-sized standable column and accepts no entity dimensions. [Combat.java:637](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L637) records one occupied cell per entity.

**Expected:** Movement remains legal at execution time for the actor's current dimensions and occupied spaces.

**Actual:** A wall placed or door closed after grid generation is not revalidated before path execution. Large/tall mobs use the same search and occupancy footprint as players. Movement position changes do not perform a swept-shape traversal check.

**Impact:** Paths can intersect changed obstacles or clip large mobs. The README acknowledges wide-mob wall clipping; dynamic-terrain invalidation is an additional confirmed omission. The precise visual/collision outcome requires gameplay testing.

**Correction:** Invalidate on relevant world changes and revalidate each segment/step using the actor's shape. Define reservation, interruption, and movement-refund rules. Weighted terrain later requires a suitable shortest-path algorithm rather than uniform BFS.

**Verify:** Close a door after grid publication, place a block during movement, remove floor support, move another actor into the path, and test spiders/ravagers/endermen against narrow/low corridors.

### F07 — Medium: interaction permission checks turn ownership, not action cost

**Evidence:** [TacticalCombatMod.java:70](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/TacticalCombatMod.java#L70)–80 uses [CombatManager.java:154](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L154)–156. `canActNow` returns true throughout the actor's turn even after `actionUsed`. No `UseEntityCallback` or general inventory/equipment action model is registered in the reviewed entry point. [TacticalScreen.java:127](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/TacticalScreen.java#L127) explicitly allows inventory and hotbar use.

**Expected:** Supported item/object/equipment actions have a declared cost and are checked on the server. SRD p. 91 allows one ordinary free object interaction, with exceptions; it does not make every item use free.

**Actual:** The tactical screen offers no normal item-use control, but the server permits covered item/block operations on the active turn without resource accounting. Entity interaction and inventory policies are not modeled by these hooks.

**Impact:** The documented UI restriction is not a complete server prohibition, and an SRD action budget could be bypassed if item use is exposed or requested independently. Exact vanilla packet paths and ongoing use spanning turn transitions need tests.

**Correction:** Route supported interactions through the same action policy; deliberately exempt cosmetic/menu operations. Do not indiscriminately lock all inventory management without a design decision.

**Verify:** Item/block requests after attacking, entity interaction out of turn, armor/hotbar changes, and a consumable or charged item started before the turn ends.

### F08 — High: character sheets do not supply authoritative combat rules

**Type:** Architectural feature gap, not a claim that manual sheet editing is itself a bug.

**Evidence:** [SheetLibrary.java:20](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/sheet/SheetLibrary.java#L20)–30 explicitly documents client-only sheet storage. [CharacterSheetScreen.java:1955](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L1955) sends a label, dice count/type, modifier, and mode. [DiceService.java:16](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/dice/DiceService.java#L16) bounds these values but does not derive them from a character. [Combat.java:84](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L84) rolls bare initiative; [Combat.java:212](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L212) uses vanilla attacks; [Combat.java:707](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L707) reads entity HP.

**Expected:** In an automated D&D rules mode, a server-owned character/entity binding supplies ability scores, AC, speed, proficiency, HP, equipment, resources, and ruleset identity.

**Actual:** Sheet HP/AC/initiative/weapon rolls may disagree completely with combat. Clients may request arbitrary bounded modifiers and labels. Server-selected dice faces establish randomness authority, not rules authority.

**Impact:** Automated SRD compliance cannot be achieved merely by adding more sheet JSON. Character editing or rest controls do not currently heal Minecraft entities or grant extra combat actions.

**Correction:** Preserve the format renderer and local manual mode. Add a separate versioned server rules profile and controlled actor binding for automated mode. Distinguish freeform rolls from authoritative action/check results in the protocol and UI.

**Verify:** Change local sheet AC/HP/modifiers and show that server combat remains unaffected today; future tests must reject unauthorized changes while retaining approved manual-play workflows. Repeat with the percentile pack to prevent a D&D-only architecture.

### F09 — Medium: every natural 20/1 is presented as a combat critical

**Evidence:** [DiceAnimation.java:103](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/DiceAnimation.java#L103)–106 adds critical messages solely from the die face and single-d20 count.

**Expected:** Ordinary ability checks and saving throws compare totals to DCs. Attack natural 20/1 rules are specific to attacks; death saves have their own special 20/1 outcomes (SRD pp. 77, 83, 94, 98).

**Actual:** Initiative, skill checks, and ordinary saves receive “Critical hit!” or “Critical fail!” text too.

**Impact:** Misleading rules feedback even during manual tabletop use. This does not currently change server combat damage.

**Correction:** Give rolls a semantic purpose; use neutral natural-face highlighting for unknown/freeform rolls. Apply attack and death-save interpretation in their respective rules handlers.

**Verify:** Deterministic 1/20 results for initiative, a skill check, an ordinary save, an attack, and a death save, both with and without advantage.

### F10 — Medium: the expression parser silently accepts unsupported dice operations

**Evidence:** [Expr.java:83](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/sheet/Expr.java#L83)–99 performs arithmetic/unary operators without tracking dice semantics; [Expr.java:138](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/sheet/Expr.java#L138)–149 replaces a dice term with zero while storing its type/count. Documentation at [sheet-formats.md:125](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/docs/sheet-formats.md#L125) permits a single additive dice term.

**Executed probes:** `2*d6`, `-d6`, and `d6/2` each produced `Roll[D6, count=1, modifier=0]`; `0d6` produced a zero-count plan. `DiceSpec.parse("0d6")` correctly rejects that expression, but `Expr.roll` accepts it, and [DiceService.java:17](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/dice/DiceService.java#L17) would clamp the requested zero count to one.

**Expected:** Unsupported dice operations/counts fail clearly; accepted formulas preserve their documented meaning.

**Impact:** Pack authors can obtain silently wrong rolls. Mixed dice terms are intentionally unsupported, but that limitation prevents many combined weapon/class/elemental damage expressions unless callers split rolls.

**Correction:** Enforce additive-only dice syntax and count bounds, or represent dice as typed expression nodes with well-defined operations. Do not silently clamp a semantic error into a different roll.

**Verify:** The four reproduced cases, nested arithmetic, dice inside functions, fractional counts, multiple dice terms, and unchanged valid `d20 + modifier` / `2d6 + 3` behavior.

### F11 — High: dice requests create unbounded broadcast and client backlog

**Evidence:** [DiceService.java:16](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/dice/DiceService.java#L16)–34 has no per-player request limit and broadcasts to every server player. [DiceAnimation.java:40](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/DiceAnimation.java#L40)–78 uses an unbounded queue and takes 80 client ticks to finish each roll. Card requests have a one-second per-player limiter at [CardService.java:20](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/card/CardService.java#L20), but their display queue at [ShareCardHud.java:52](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/ShareCardHud.java#L52)–71 is also unbounded.

**Expected:** Presentation traffic has request budgets, audience scope, bounded pending work, and overload behavior.

**Actual:** A sender can generate rolls much faster than clients display them. Even one accepted roll per second exceeds the dice animation's approximately one-per-four-seconds throughput at 20 client ticks/second. Cards can also accumulate despite their per-player limiter.

**Impact:** Confirmed queue growth and fan-out conditions; delayed results, chat/animation griefing, and memory/CPU pressure are credible risks. No load test or out-of-memory crash was performed.

**Correction:** Add server rate limits and burst budgets, encounter/party audiences, and bounded client queues. Preserve results through a compact log when animations are coalesced or skipped.

**Verify:** Bounded bursts and sustained traffic from multiple clients; assert maximum queue sizes, fair access, prompt result visibility, and stable server/client performance.

### F12 — Medium: sheet casting spends resources before validating the roll

**Evidence:** [CharacterSheetScreen.java:881](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L881)–910 saves the slot decrement before constructing/validating the dice plan. [CharacterSheetScreen.java:1955](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L1955)–1968 can subsequently fail on formula parsing or unsupported server networking. There is no rollback. The same formula is used at higher slot levels. [sheet-formats.md:101](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/docs/sheet-formats.md#L101) says left-click spends that level, but the implementation searches upward even on left-click if that level is empty.

**Expected:** Local bookkeeping should validate an intended operation before committing its cost, and UI promises should match slot selection.

**Actual:** A bad formula or unsupported roll server can consume a local slot without a roll. Upcast selection changes the slot and label, not the spell's effect/dice.

**Impact:** Lost bookkeeping resources, unintended higher-slot consumption, and incorrect expectations of automated upcasting. A no-dice spell only prints a local client message; it does not execute a world spell.

**Correction:** Prepare/validate then commit local changes, or use server acceptance when automated casting exists. Display the selected slot and apply explicitly supported spell scaling. Preserve deliberate manual bookkeeping as a separate mode.

**Verify:** Invalid dice formula, unsupported server, absent base slot with higher slots available, cantrip, valid higher-level cast, and save failure.

### F13 — Medium: character saves overwrite the only file directly

**Evidence:** [SheetLibrary.java:188](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/sheet/SheetLibrary.java#L188)–200 serializes directly with `Files.writeString` to the existing character file. [CharacterSheetScreen.java:511](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CharacterSheetScreen.java#L511) updates the in-memory object before saving; errors only show a message.

**Expected:** Existing saved characters survive interrupted writes, and failure to persist is visible and recoverable.

**Actual:** No temporary-file/atomic-replace/backup sequence. Character files are client-global under the config directory rather than tied to a server/world.

**Impact:** A partial write can lose the previous valid character, and memory/disk can diverge after failure. This is an identified failure mode, not an observed corruption event. Shared client storage may be intentional but needs a campaign policy before authoritative use.

**Correction:** Write/flush to a sibling temporary file, atomically replace where supported, keep a recoverable prior version, and track dirty/error state. Add schema/version migration before server-owned persistence.

**Verify:** Inject write errors/interruption, reopen the character, recover the last valid data, and test identical character names across campaigns.

### F14 — Medium: the threat overlay is an approximation, not an exact prediction

**Evidence:** [Combat.java:659](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L659)–680 runs a six-step ground search and adds immediate cardinal neighbors. It ignores actual ranged attack distance, mob-width reach, free-walking/flying behavior, visibility, and action availability. [CombatConfig.java:33](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatConfig.java#L33) caps contributors at 12 within 28 blocks; [GridPayload.java:67](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/net/GridPayload.java#L67) caps threat cells at 4,000.

**Expected:** Either calculate threat from the same attack/movement rules used for execution or label the visualization approximate.

**Actual:** The README's claim that the red area is “exactly” where enemies can reach/hit is stronger than the implementation.

**Impact:** Players may consider unsafe cells safe, especially against archers, large mobs, or flying enemies. Truncation can omit threats without a UI indication.

**Correction:** Share legality queries with enemy action planning; distinguish movement, melee, ranged, and uncertain threat. Surface truncation/approximation honestly.

**Verify:** Compare highlighted cells with actual legal attacks for skeletons, spiders, flying mobs, walls, elevations, and encounters above the display caps.

### F15 — Medium: full snapshots and repeated searches need measured scaling limits

**Evidence:** [Combat.java:259](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L259) calls `sync()` every two ticks, plus event-triggered calls. [Combat.java:700](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L700) rebuilds all entries and sends them to every enrolled player. [CombatManager.java:32](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L32) scans encounters and their members for lookup. [Combat.java:659](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L659) performs one BFS per contributing enemy. [BlockFade.java:99](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/BlockFade.java#L99) and [BlockFade.java:157](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/BlockFade.java#L157) repeatedly scan fade geometry and highlight visibility.

**Expected:** Acceptable tick time and bandwidth at the supported encounter size, with explicit budgets.

**Actual:** At normal 20 TPS, each fight emits at least ten full snapshots per second per recipient. Work/bytes scale roughly with participants times recipients, and lookup scans multiply in detection/recruitment. There is no encounter-size cap. Client fade work is bounded in radius/interval but still repeats many block queries.

**Impact:** Scaling risk, not a measured lag claim. The HUD's fixed-width initiative strip at [CombatHud.java:53](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CombatHud.java#L53) also exceeds the screen at sufficiently large counts.

**Correction:** Profile representative sizes first; use entity-to-encounter indexing, dirty-state snapshots/deltas with occasional resync, coalesced sends, cached/incremental calculations, and an overflow-aware HUD.

**Verify:** Record p50/p95/max tick time, packet bytes, allocations, and client frame time across increasing participant/player counts and camera movement. Preserve correctness under packet delay.

### F16 — Medium: an idle player can hold combat indefinitely

**Evidence:** [CombatConfig.java:55](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatConfig.java#L55) sets `PLAYER_TURN_TICKS = 0`; [Combat.java:395](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L395) only ends on timeout if positive. [CombatManager.java:178](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L178) accepts end-turn only from the active player. Operators can end the entire encounter via `/tbc end`.

**Expected:** A chosen manual-play/AFK/host-control policy provides an intentional recovery path.

**Actual:** A connected player who never ends their turn stalls the encounter forever. Disconnect pruning exists; this is not a disconnected-player softlock.

**Impact:** Multiplayer griefing or accidental stalls. Unlimited turns may be a legitimate tabletop preference, so enabling a timer is a design choice, not an automatic fix.

**Correction:** Configurable timeout, AFK handling, or host advance-turn control with clear ownership and audit events.

**Verify:** AFK player, open sheet/menu, disconnect, reconnect, and host intervention without ending unrelated encounters.

### F17 — Low: packet bounds and lifecycle cleanup are inconsistent

**Evidence:** [ShareCard.java:20](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/card/ShareCard.java#L20)–29 clamps a declared stat count with `min` rather than rejecting an out-of-range count; excess encoded entries are not consumed as entries. [DiceRollPayload.java:18](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/net/DiceRollPayload.java#L18), [GridPayload.java:26](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/net/GridPayload.java#L26), and [CombatStatePayload.java:30](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/net/CombatStatePayload.java#L30) allocate arrays/lists from server-supplied counts without protocol-specific limits. [CardService.java:32](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/card/CardService.java#L32) exposes `forget`, but no call site was found.

**Expected:** Decoders reject malformed lengths and enum values consistently; lifecycle maps release departed players.

**Actual:** A malformed share-card payload can misalign decoding or be rejected later instead of failing at its count. Outbound authoritative snapshots are normally produced by trusted server code, so their missing client bounds are not an established ordinary-client-to-server allocation exploit. The card rate-limit map retains UUID entries until process exit.

**Impact:** Protocol fragility and gradual lifecycle bookkeeping growth. No remote server crash or code execution was demonstrated.

**Correction:** Explicit maximum/minimum counts and enum validation, symmetric codecs, disconnect/server-stop cleanup, and capability checks where optional clients are supported.

**Verify:** Negative/oversized lengths, truncated packets, invalid type indices, normal round trips, and repeated connect/disconnect cycles.

### F18 — Medium: wall-clock world effects and turn time have no common policy

**Evidence:** [Combat.java:220](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L220) ticks a partial positional freeze and [Combat.java:395](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L395) permits unlimited real-time player turns. No reviewed hook pauses or translates vanilla effect durations, hunger, fire, drowning, or world/environment time into combat rounds. [CombatManager.java:137](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/CombatManager.java#L137) allows unattributed/self damage through.

**Expected:** SRD rounds represent about six seconds (p. 90); timed effects and hazards have a defined relationship to game-world simulation and player decision time.

**Actual:** Combat turn progression is disconnected from the continuing Minecraft tick loop. Only selected movement/target/damage behavior is gated.

**Impact:** Taking longer to decide can change environmental damage, recovery, or effect expiry. Exact vanilla-effect outcomes need runtime tests; there is no claim that the entire world should be frozen.

**Correction:** Choose which actor effects use round time, which retain Minecraft time, and how outsiders interact. Resolve this before concentration, conditions, rests, or spell-duration work.

**Verify:** The same encounter with short and long decision delays, fire/poison/regen effects, hunger and drowning, environmental projectiles, and two concurrent encounters.

### Documentation and presentation discrepancies

These are Low severity unless covered above. The README broadly describes actual implementations, but it is not a readiness proof.

| Claim/UI | Code reality |
|---|---|
| Exact red enemy threat region | Approximate, capped, ground-based region; F14. |
| Everyone else frozen | Horizontal pinning and client input suppression, not complete server simulation freeze; F05/F18. |
| Only active combatant may act | Checked custom movement/end-turn/melee paths, but damage boundaries and interactions are incomplete; F03/F04/F07. |
| Bonus-action HUD pip | Always reset, never consumed by a feature; [Combatant.java:54](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combatant.java#L54) and [CombatHud.java:119](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/CombatHud.java#L119). |
| Card recall until leaving the game | [ShareCardHud.java:32](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/ShareCardHud.java#L32) limits history to 60 entries; older links expire earlier. |
| Left-click cast spends base-level slot | It searches higher levels if necessary; F12. |
| Generic d20 description says “Built in” | [format.json:4](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/packs/generic_d20/format.json#L4) is stale; [SheetLibrary.java:20](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/sheet/SheetLibrary.java#L20) and build install tasks show external packs. |
| README client source layout | Actual client and mixin sources are under `src/main/java`, not the README's `src/client/java` illustration. |

### Findings that were investigated but are not established bugs

- **Packet callback threading:** The resolved Fabric networking API source explicitly says `ServerPlayNetworking` callbacks execute on the server thread. The direct move/attack/end-turn callbacks therefore do not establish a race merely because they omit `server.execute`.
- **Disconnect/entity removal:** [Combat.java:263](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L263)–309 checks connection membership, life, removal, world, and spectator state, updates the active index, and sends inactive state. [TacticalCombatClient.java:60](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/client/TacticalCombatClient.java#L60) resets client state. These are positive safeguards; edge-case gameplay still needs tests.
- **Arbitrary client movement destination:** [Combat.java:155](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/combat/Combat.java#L155) validates membership in a server-generated set and budget. No evidence that a forged destination alone bypasses those checks. F06 concerns stale legality after generation.
- **Persistent corruption/crashes/DoS:** Failure modes and amplification paths are identified; none was reproduced as an in-game crash or damaged user file.
- **Snapshot ordering:** No encounter ID/revision exists in [CombatStatePayload.java:14](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/java/dev/tacticalcombat/net/CombatStatePayload.java#L14), but that alone is not proof of packet reordering on a normal ordered connection. It becomes particularly hazardous under F01 and merits future versioning.

## D. Implementation roadmap

Relative complexity: **S** = contained change; **M** = several collaborating components; **L** = new subsystem/integration; **XL** = substantial rules/content surface. Estimates are relative, not commitments in days. Existing manual packs and camera/grid components should remain usable throughout.

| Phase | Deliverables | Dependencies | Complexity / completion gate |
|---|---|---|---|
| 0. Reproducible verification | Complete JDK 21 environment; unchanged wrapper/dependencies; full build, dedicated-server/client smoke checks; capture current regressions in a test harness. | None | S–M. Build/remap/package passes; two-client baseline is recorded. |
| 1. Combat ownership and server correctness | Single encounter ownership; explicit action identity/cost/effects; server attack geometry; stale-path/collision checks; incoming-damage policy; bounded presentation queues; lifecycle/AFK recovery. | Approve boundary, movement, and AFK decisions in E; Phase 0 for runtime gates. | L, split into small PRs. No overlapping ownership, unauthorized damage/action, wall attack, or stale-path traversal in the regression scenarios. |
| 2. Rules profile and authoritative actors | Versioned optional rules profile alongside sheet formats; entity-character ownership; server abilities/AC/speed/HP/proficiency/equipment; persistence; freeform versus adjudicated roll distinction. | Phase 1 and authority/scale decisions. | L. Two players cannot alter each other's actor state; existing generic/percentile manual sheets still work. |
| 3. Minimal SRD combat slice | d20 + Dex initiative; attack versus AC; typed weapon damage; natural 1/20 and critical dice; bonus-action/reaction availability; Extra Attack accounting; Dash/Disengage/Dodge; opportunity attacks; basic cover. | Phase 2; action lifecycle from Phase 1. | L. Deterministic rules cases and two-client fights pass without relying on vanilla cooldown/armor as SRD substitutes. |
| 4. Health, conditions, movement detail | Zero HP, unconscious/stable/dead, death saves, healing/temp HP; condition hooks; difficult terrain, size/space, prone/grapple/shove; selected vertical movement and forced motion. | Phase 3; approved time/geometry policy. | L. Start/end-turn transitions and damage/condition interactions are tested. Basic incapacitation must arrive before reaction-dependent advanced features. |
| 5. Selected classes, monsters, recovery | Explicitly chosen SRD classes/levels and monster profiles; Multiattack; recharge/resource rules; Hit Dice and rests; server equipment adapters. | Phases 2–4. | L. Publish a capability list; unsupported features remain explicit/manual rather than silently approximated. |
| 6. Spellcasting slice | Validated spells, known/prepared policy, components, action/bonus/reaction casting times, slots, scaling, target selection, AoE, saves, concentration, duration, effect cleanup. | Phases 3–5, especially typed damage/conditions/time. | XL. Start with a small representative spell set, then expand by tested effect primitives. |
| 7. Advanced scope and scaling | Additional classes/monsters, optional feats/multiclassing, legendary/lair actions where chosen, richer senses, profiling-driven optimization and compatibility. | Explicit scope approvals and prior gates. | XL / ongoing. Do not make optional content a prerequisite for a reliable baseline. |

Keep SRD-specific formulas and feature definitions in a rules profile/content layer. Keep generic encounter scheduling, command validation, movement geometry adapters, and networking separate. The existing JSON sheet schema is a good UI/content foundation but should not be turned into an unrestricted server script engine. Use versioned, validated data and a bounded set of supported effect/action primitives.

## E. Design decisions requiring approval

These are decisions to resolve before implementation, not assumptions adopted by this audit.

| Decision | Current behavior / tradeoff to resolve |
|---|---|
| Product mode | Retain a Minecraft tactical mode, add an automated SRD profile, support manual GM adjudication, or combine explicit modes? Do not silently redefine every existing pack as D&D. |
| Block-to-feet conversion | One block currently equals one movement square. At one block = 5 feet, eight squares would mean 40 feet and 3.2-block reach would be about 16 feet of eye-to-hitbox distance. A physical metre conversion yields different values. Choose a coherent rules scale independently of Minecraft visual proportions. |
| Diagonals and vertical distance | Cardinal-only, a documented diagonal grid rule, or continuous distance? Decide ascent/descent, climbing, jumping, swimming, flying, squeezing, forced movement, and fall damage together. |
| Space versus hitbox | D&D controlled space is not literal body width (SRD p. 92). Decide logical size footprints, collision dimensions, centering, and how weapon reach is measured. |
| Attack geometry and visibility | Define total/partial cover for voxels, slabs, glass, fences, leaves, and entities. Separate cover from darkness/invisibility and from tactical camera occlusion. Unseen attacks are not universally illegal; some use disadvantage and guessed locations. |
| Damage/HP ownership | Keep vanilla health/armor/cooldowns/enchantments, replace them during SRD encounters, or map them explicitly? Decide crits, regeneration, hunger, potion effects, fall damage, and death/respawn interactions. |
| Monster identity | Map vanilla mobs to selected SRD profiles, author custom profiles, or retain vanilla attacks? There is no automatic equivalence between a Minecraft zombie and an SRD zombie. |
| Equipment | Bind actual inventory items to rules items or allow virtual sheet equipment? Decide ammunition, armor, shield/hands, reach/finesse/thrown/loading properties, item switching, and consumable costs. |
| Characters, packs, trust | Who may assign/edit characters—player, GM, operator? Are packs server-selected/versioned? Can different rulesets coexist in one fight? How are local characters imported and approved? |
| Encounter boundaries | Recruit, merge, reject, or permit outsiders and neighboring fights? Decide pets, neutral creatures, PvP, creative players, bosses, spectators, late joiners, and reinforcements. |
| World time | Which hazards/statuses use combat rounds versus real Minecraft ticks? How are multiple encounters and the surrounding world reconciled? |
| Reactions | Prompt, auto-resolve, or configurable policies? Define timeout and continuation behavior without turning every movement step into a multiplayer pause. |
| Turn/AFK control | Unlimited tabletop thinking time versus timeout/host advance. Decide permitted recovery actions and disconnect/rejoin ownership. |
| Scope of SRD support | Choose classes, levels, monsters, spells, and optional rules for the first supported slice. “Missing” does not authorize implementing the entire SRD. |
| Persistence/campaigns | Local reusable sheets versus server/campaign-owned actors; migrations, backups, multi-world character identity, and whether combat survives restart. Current combat is ephemeral and cleared on server stop. |

**Content provenance:** The mod metadata declares MIT ([fabric.mod.json:8](https://github.com/shayanzas/Cubetable/blob/d712cc0c23545ae54062d505debf415feb9fe490/src/main/resources/fabric.mod.json#L8)); no SRD attribution notice was found in the searched README/docs/packs. That observation alone does not establish infringement or that the repository contains copied protected SRD expression. Before shipping copied SRD text/stat blocks, preserve the CC-BY attribution and source/version separately from the code license. This is a release-content decision, not a legal compliance certification.

## F. Testing strategy

### Automated rules and parser tests

Use deterministic/injected dice and table-driven cases. Test semantic outcomes, not just implementation-shaped snapshots.

- Initiative: modifiers, ties, identical-creature policy, late join, and removal at each order position.
- Action economy: accepted/missed/cancelled actions; bonus-feature eligibility; reaction recovery at start of next turn; Extra Attack versus extra actions; multi-target effects spending once.
- Attack resolution: total equals AC, natural 1/20, negative modifiers, advantage/disadvantage cancellation, doubled damage dice without doubled flat modifiers, and ordinary checks/saves without attack-critical semantics.
- Movement: weighted shortest legal paths, split movement, occupied endpoints, friendly difficult terrain, logical creature sizes, different speeds, blocked corners, slopes, and movement interruption/refund.
- Health: resistance rounding/order, immunity, temp HP, healing cap, instant death, stabilization, death-save 1/20, damage at zero, and recovery resetting death-save counters.
- Effects: condition stacking by duration, incapacitation/action/reaction restrictions, concentration damage saves, source removal, expiry boundaries, and round-time conversion.
- Packs: generic d20 and percentile parse/round-trip fixtures; invalid/recursive formulas; slot-table boundaries; invalid identifiers/references; atomic-save fault injection; backwards-compatible schema migration.
- Codecs: count/string/enum bounds, truncated packets, symmetric round trips, and explicit rejection rather than silent reinterpretation.

The temporary checks executed in this audit may inform future tests, but they are not committed to the repository and do not cover Minecraft integration.

### Server and multiplayer regression scenarios

Use Fabric/Minecraft-supported test infrastructure or a controlled dedicated server with at least two clients. Keep test worlds disposable.

1. **Ownership:** repeated `/tbc start`, adjacent fights, recruitment, late join, death, dimension change, spectator transition, disconnect/reconnect, server restart.
2. **Authorization:** forged move/attack/end-turn requests from inactive or unrelated players; repeated requests; stale entity IDs; item/entity interactions; dead or removed actor requests.
3. **Geometry:** melee through walls and fences, actor versus camera sightlines, changed doors/blocks, falling support, large entities, vertical movement and force effects.
4. **Damage lifecycle:** missed and delayed arrows, attacker removed before impact, explosions with multiple victims, sweeps, retaliation, environmental damage, and an external attacker.
5. **Synchronization:** latency/jitter, rapid turn changes, death of current/next actor, late packets around dimension transitions, client reconnect, and consistency of grid/action/health UI.
6. **Abuse and scale:** bounded roll/card traffic; queue limits; encounter count/size; profile server ticks, snapshot bytes and client frames. Do not equate a running server with a successful stress test.

### In-game acceptance checks

Verify a complete small encounter with two players, a melee mob, and a ranged mob; grid move, attack, end turn, reinforcement, enemy death, and combat exit. Repeat on uneven terrain and inside a building. Check camera fading and picking separately from authoritative attack legality. Test inventory/hotbar, sheet opening, resource changes, save/reload, dice broadcasts, and disconnect cleanup.

For future SRD mode, use a fixed scenario with known abilities, AC, speed, weapons, and seeded dice; compare every result with an independent SRD expectation. Verify a non-D&D pack remains usable after each rules-layer change. Test default rendering first and renderer/shader integrations as a separate compatibility matrix.

## Prioritized recommendation for the first development phase

**Authorize a narrowly scoped “combat authority and correctness” phase before spellcasting or additional class content.** Establish a complete JDK 21 build/runtime baseline, then address F01 (single ownership), F02/F06 (attack and movement legality), F03/F04 (accepted actions and encounter damage boundaries), and F11 (bounded requests/queues). Add regression scenarios for those exact failures. Resolve the block scale, world-time, outsider, and character-authority decisions before introducing SRD actor statistics.

Acceptance should require one authoritative encounter per entity, one cost per accepted action, correct rejection of obstacle/stale-path requests, explicit handling of delayed/multi-target damage, bounded network work, and successful two-client cleanup/recovery. Preserve existing packs and manual sheets. No implementation has been authorized or performed by this audit. Subsequent authorization to publish this report is limited to documentation.

## Reference and attribution

Authoritative rules: attached `SRD_CC_v5.1.pdf`, also available from [the supplied SRD 5.1 PDF URL](https://media.wizards.com/2023/downloads/dnd/SRD_CC_v5.1.pdf). Rules descriptions in this report are summarized for comparison with the inspected code.

This work includes material taken from the System Reference Document 5.1 (“SRD 5.1”) by Wizards of the Coast LLC and available at https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.
