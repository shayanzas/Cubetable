# Cubetable — Comprehensive Implementation Specification

## 1. Mission

Continue development of **Cubetable**, the existing Minecraft tactical combat and tabletop RPG mod.

Implement the approved design as a **modular tactical RPG framework**, initially supporting Minecraft Tactical combat, automated D&D 5e combat, and manual tabletop gameplay.

This is an **implementation task, not another read-only audit**. Inspect the current project, establish a verification baseline, and then implement the requirements in carefully tested, dependency-ordered phases.

Do not attempt to rewrite the entire mod at once.

### Repository

- Development repository: https://github.com/shayanzas/Cubetable
- Original upstream: https://github.com/jistingrid/Cubetable
- Previously audited revision: `d712cc0c23545ae54062d505debf415feb9fe490`
- Minecraft: **1.21.1**
- Mod loader: **Fabric**
- Java: **21**
- Use the existing checked-in Gradle wrapper and project dependencies.

Work only on the user's fork or its existing development workspace. **Do not modify the original upstream repository.**

If the repository is already checked out, inspect and continue from its current state. Do not unnecessarily reclone it, reset work, or overwrite existing changes.

Prefer a dedicated development branch with logically separated changes. Do not push to a remote branch or open a pull request without explicit authorization.

### Required references

Read and use:

1. The attached `SRD-5.1-audit.md`, containing the original technical audit and findings F01–F18.
2. The attached or linked `SRD_CC_v5.1.pdf`.
3. The existing source code, configuration, character packs, documentation, and gameplay behavior.

**Use only the 2014 D&D fifth-edition rules as presented in SRD 5.1 for automated D&D mechanics.**

Do not silently substitute the 2024 rules or SRD 5.2.1.

Maintain appropriate attribution for any copied SRD 5.1 content under CC BY 4.0, separately from the mod's existing code license.

---

## 2. Nonnegotiable Engineering Requirements

### Preserve existing functionality

The mod is already reported to work in Minecraft.

Preserve its existing:

- Tactical camera and camera fading.
- Grid display, navigation, and movement visualization.
- Combat HUD and initiative presentation.
- Dice animations and manual rolling.
- Character-sheet editor and JSON layouts.
- Existing generic D20 and percentile game packs.
- Shared character cards.
- Existing Minecraft Tactical gameplay wherever possible.

Do not remove an existing feature merely because the new D&D system requires a different implementation.

### Separate the game engine from the rules

Cubetable must have a reusable core responsible for:

- Encounter lifecycle and scheduling.
- Entity identification and ownership.
- Movement and world geometry.
- Server networking and permissions.
- DM tools.
- Persistence.
- User interface integration.

Individual rulesets supply their own:

- Character statistics and derived values.
- Initiative rules.
- Movement allowances.
- Attack and damage calculations.
- Action economy.
- Conditions and special abilities.
- Equipment interpretations.
- Character progression.

Avoid hardcoding D&D-specific mechanics throughout the core engine.

Use validated, versioned rules profiles and controlled effect primitives. **Do not turn character-sheet formulas or JSON content into an unrestricted server-side script engine.**

### Server authority

All consequential gameplay decisions must be validated on the server.

Clients may request actions and display previews, but cannot arbitrarily determine successful attacks, damage, HP, spell slots, initiative, or DM permissions.

Distinguish:

1. Freeform tabletop dice rolls.
2. Authoritative combat rolls.
3. DM overrides and manual adjudication.

These must not be interchangeable network operations.

### Incremental development

Implement small, independently verifiable changes.

After each phase:

- Run every feasible build and automated test.
- Verify affected networking and persistence paths.
- Identify regressions.
- Document implemented, incomplete, and deferred functionality.
- Preserve a working development checkpoint.

If a phase cannot be verified, do not build multiple additional dependent systems on unverified assumptions.

If the environment prevents compilation or runtime testing, report that limitation precisely, continue appropriate static checks, and provide reproducible local verification instructions.

Never claim that a feature was compiled, tested, or run in Minecraft unless that verification actually occurred.

---

## 3. Game Modes and Ruleset Architecture

### Initial modes

Support three gameplay workflows:

**Minecraft Tactical**

Preserve the existing Minecraft-oriented tactical combat mode.

**D&D 5e — SRD 5.1**

Provide automated D&D combat with DM overrides and the ability to adjudicate unsupported features manually.

**Manual Tabletop**

Retain character sheets, dice, and resource tracking without requiring automated world combat.

### Extensibility

The architecture must support additional rulesets later.

Do not implement other tabletop systems now, but make the ruleset boundaries explicit enough to accommodate them without rewriting encounter scheduling, networking, or persistence.

### Campaign defaults and encounter overrides

- Each campaign has a default ruleset.
- Authorized DMs can select a different ruleset for an individual encounter.
- Each encounter runs exactly one ruleset.
- Switching rulesets must not corrupt character statistics or resources.
- Incompatible character data must be explicitly adapted, rejected, or preserved separately—not silently reinterpreted.

---

## 4. Character Ownership and DM Permissions

### Player characters

Players may create, select, import, and maintain their own characters.

Campaign settings determine whether character selection, editing, or progression requires DM approval.

### DM hierarchy

Implement server-enforced authorization:

- Server administrators authorize campaign creation and management.
- Authorized campaign owners manage their campaigns.
- Campaign owners may appoint eligible co-DMs.
- Co-DMs receive explicitly scoped campaign permissions.
- Ordinary players cannot grant themselves DM privileges.

DM permissions must not depend solely on client-side interface restrictions.

### DM capabilities

Authorized DMs must be able to:

- Create and edit NPCs and monsters.
- Assign character sheets and statistics.
- Control creature movement and actions.
- Change initiative and encounter membership.
- Apply or remove conditions.
- Adjust HP and resources.
- Adjudicate unsupported abilities.
- Override attack outcomes where permitted.
- Manage campaign and encounter configuration.
- Advance stalled turns.
- Spawn prepared encounters and reinforcements.

Provide a DM management interface that can select and command a creature without requiring the DM's Minecraft avatar to physically become that entity.

### Concurrency and accountability

- Prevent conflicting simultaneous DM operations.
- Enforce permissions on every privileged server request.
- Record important DM overrides and campaign changes in an activity log.
- Preserve clear ownership when a DM temporarily pilots a player character or NPC.

---

## 5. Monster AI, Custom Monsters, and Duplication

### AI behavior

Monsters use AI by default, including when no DM is present.

The DM may switch individual creatures or the entire encounter between AI and manual control.

When manual takeover occurs, stop the creature's automated decision-making immediately and preserve its already-consumed actions and movement.

AI-controlled creatures must follow the active ruleset's initiative, movement, attack, and resource restrictions.

### Custom monsters

DMs can define custom monsters based on existing Minecraft mobs or other configured creature profiles.

Support editable:

- HP and Armor Class.
- Ability scores and saving throws.
- Movement modes and speeds.
- Attacks, damage, and reach.
- Special abilities.
- Equipment.
- Conditions and resistances.
- AI behavior settings.
- Optional death-saving-throw behavior.

Do not assume that vanilla Minecraft mob statistics automatically equal corresponding D&D monster statistics.

### Duplication

Support two distinct operations:

**Duplicate Template**

Create a new editable monster template based on an existing template.

Example: copy a wolf and create an Elite Wolf with stronger statistics.

**Spawn Copies**

Create multiple independent encounter participants using the selected template.

Each spawned creature must have its own:

- Unique persistent identifier.
- HP and temporary HP.
- Initiative entry.
- Actions and reactions.
- Conditions.
- Equipment state.
- AI/manual control status.
- Position and encounter membership.

Editing one spawned wolf during combat must not unintentionally modify the other copies.

Also support authorized multi-selection and bulk operations.

Ensure persistence and restart recovery cannot accidentally duplicate previously spawned creatures.

---

## 6. Movement, Scale, and Geometry

### Grid scale

**One Minecraft block equals five D&D feet.**

A creature with 30 feet of movement receives six grid squares per turn.

Replace the existing fixed eight-square player movement allowance in SRD mode with movement derived from authoritative character statistics.

### Diagonals

Permit eight-direction movement.

Every adjacent move—including a diagonal move—costs five feet.

This is an explicitly approved Cubetable adaptation. Document it rather than attributing it to a mandatory SRD diagonal-counting rule.

Prevent movement through blocked diagonal corners and solid geometry.

### Vertical movement

Support:

- Ordinary one-block ascent as standard movement.
- One-block ascent without a special action or extra movement surcharge.
- Strength-based jumping.
- Climbing.
- Swimming.
- Flying.
- Falling and fall damage.
- Difficult terrain.
- Creature-size restrictions.
- Forced movement.
- Different movement speeds.

Higher climbs and jumps must use the appropriate rules rather than treating all vertical changes as ordinary walking.

Handle stairs and slabs naturally.

Movement paths must account for current terrain, entity dimensions, collision geometry, occupied spaces, and remaining movement.

Revalidate cached paths when terrain or occupancy changes.

### Logical creature spaces

Separate D&D controlled space from the literal Minecraft entity hitbox.

Support appropriately sized creature footprints and weapon reach without relying on approximate eye-to-hitbox distance alone.

---

## 7. Attacks, Dice, and Combat Resolution

### Required attack workflow

**Select target → Preview → Confirm → Server roll → Resolve**

The player must manually confirm an attack before it is committed.

The preview shows:

- Attack name and weapon.
- Attack formula, e.g. `1d20 + 5`.
- Applicable advantage/disadvantage.
- Damage formula, e.g. `1d8 + 3`.
- Minimum and maximum possible damage.
- Damage type.
- Applicable resource/action costs.
- Relevant publicly available targeting information.

Never reveal secret target statistics through a preview.

### On confirmation

The server must:

1. Validate actor and target identity.
2. Validate encounter membership and permissions.
3. Validate initiative and available actions.
4. Validate reach, range, cover, and target eligibility.
5. Accept the action exactly once.
6. Roll the attack using server-generated dice.
7. Compare the result with the target's AC.
8. Resolve any hit, critical hit, or miss.
9. Roll damage when applicable.
10. Apply damage types, resistances, vulnerabilities, immunities, and other applicable rules.
11. Update authoritative character and encounter state.
12. Send permitted results to the appropriate viewers.

An invalid or rejected request must not consume an action.

An accepted attack that misses still consumes the relevant attack.

Multi-target effects must not spend an action separately for every damage callback.

### SRD rules

Implement applicable:

- Natural 1 and 20 attack rules.
- Critical-hit damage dice.
- Advantage/disadvantage and cancellation.
- Ability and proficiency modifiers.
- Weapon properties.
- Melee and ranged reach.
- Range limitations and penalties.
- Half cover and three-quarters cover.
- Total cover.
- Visibility, unseen creatures, and hidden targets.
- Typed damage and resistance.
- Extra Attack and Multiattack for supported creatures.

Unseen targets must not automatically be treated as impossible to attack; apply the applicable SRD rules.

Camera fading must never authorize an attack through a wall.

### Dice presentation

Both players and DMs should see relevant attack and damage animations and results.

Differentiate a natural 20 on an attack from a natural 20 on an ability check, saving throw, or death save.

Do not incorrectly label every natural 20 a critical hit.

### DM roll visibility

Support:

**Public:** Authorized viewers see dice, modifiers, totals, and outcomes.

**Hidden:** Players see appropriate visible outcomes, but not the DM's private roll values.

**Secret:** The DM determines what information becomes visible; private rolls may occur without notifying ordinary players.

Hidden or secret roll information must not be transmitted to unauthorized clients.

DM overrides are separate from legitimate server-generated dice results.

---

## 8. Actions, Bonus Actions, and Reactions

Implement a proper server-authoritative action economy.

Support standard SRD actions as appropriate, including:

- Attack.
- Dash.
- Disengage.
- Dodge.
- Help.
- Hide.
- Ready.
- Search.
- Use an Object.

Bonus actions are available when a feature or ability specifically grants one; they are not universally available as another unrestricted action.

### Reactions

Use interactive reaction prompts.

For example, if an enemy voluntarily leaves a creature's melee reach:

1. Pause the relevant action before the enemy exits reach.
2. Determine whether an opportunity attack is permitted.
3. Display the reaction and its dice/damage preview.
4. Allow the player to accept or decline.
5. Resolve the reaction on the server.
6. Resume the interrupted action appropriately.

Account for Disengage, forced movement, teleportation, visibility, and other eligibility restrictions.

Refresh reaction availability at the appropriate turn boundary.

AI monsters resolve reactions using configured behavior.

Provide configurable reaction timeouts to prevent indefinite stalls.

Ensure Ready actions and reactions cannot duplicate resource expenditure or resolve more than once.

---

## 9. Persistent D&D HP, Death, and Recovery

### Single authoritative HP system

In a D&D campaign, character-sheet HP remains authoritative both inside and outside tactical encounters.

A character leaving combat at 18/45 HP must still have 18/45 HP while exploring.

The on-screen display should represent that same health state.

Do not independently apply vanilla Minecraft health loss after the D&D pipeline has already applied damage.

Explicitly handle interactions with vanilla Minecraft and other mods to the extent supported.

### Damage outside combat

Translate supported environmental damage, attacks, traps, and hazards into D&D-compatible damage.

Support appropriate falling, fire, lava, drowning, poison, and similar effects.

Avoid silently applying inconsistent vanilla and D&D values to the same damage event.

### Unconsciousness and death

Implement SRD 5.1 mechanics:

- Player characters generally become unconscious at zero HP.
- Death saving throws occur at the proper turn boundary.
- Three successes stabilize.
- Three failures cause death.
- Natural 20 restores one HP.
- Natural 1 counts as two failures.
- Damage while at zero HP has appropriate consequences.
- Massive damage can cause instant death.
- Healing above zero HP restores consciousness.
- Stabilization and recovery update all relevant state.

Ordinary monsters normally die at zero HP, but DMs may designate creatures to use death saving throws.

The Minecraft entity must not enter an unintended vanilla death/respawn cycle while D&D unconsciousness is being resolved.

### Rests

Implement selected SRD short-rest and long-rest mechanics with appropriate resources, Hit Dice, and recovery conditions.

Do not treat an existing local sheet reset button as a fully validated D&D rest.

Support DM adjudication for rest timing and unusual circumstances.

---

## 10. Equipment and Inventory

Support both physical Minecraft equipment and virtual character-sheet equipment.

### Physical items

Map supported Minecraft weapons, armor, shields, ammunition, and consumables to explicit ruleset equipment profiles.

Relevant D&D properties may include:

- Damage dice and type.
- Proficiency requirements.
- Finesse, light, heavy, versatile, and two-handed properties.
- Reach and range.
- Ammunition and loading.
- Armor category and AC calculations.
- Shield use.
- Equipped hands and item switching.

### Virtual items

Permit virtual weapons, armor, and equipment stored on character sheets.

Campaign settings determine whether virtual equipment can affect automated combat without a corresponding physical item.

DMs can create, edit, save, and duplicate equipment templates.

### Consistency

- Never apply physical and virtual versions of the same intended equipment bonus twice.
- Validate changes on the server.
- Keep the character sheet and equipped state synchronized.
- Preserve item identity across saves and reloads.
- Avoid creating unauthorized physical Minecraft items during imports or DM edits.

---

## 11. Character Progression and Classes

Support both:

**Guided Progression**

The system assists with levels, supported features, proficiency, HP changes, and other SRD-based progression.

**Manual Progression**

Players and authorized DMs may edit levels, stats, and features directly within campaign permissions.

Manual changes must not cause automatic features to apply twice.

Support XP-based, milestone-based, and directly awarded levels as configurable campaign approaches.

### Initial automation order

**Stage 1: Fighter and Rogue**

Prioritize a tested selection of features involving:

- Weapon and armor proficiency.
- Second Wind.
- Action Surge.
- Extra Attack.
- Sneak Attack.
- Cunning Action.
- Hiding, positioning, and advantage.

**Stage 2: Cleric and Wizard**

Add supported spellcasting features, healing, spell preparation, resource tracking, and selected class abilities.

**Stage 3: Evaluation**

Review gameplay, correctness, multiplayer behavior, and class architecture.

**Stage 4: Remaining SRD classes**

Only after the initial four have been tested and refined should full automation expand across the remaining SRD classes.

All other classes should remain usable for manual tabletop character management in the meantime.

Do not claim unsupported levels, subclasses, or class features are fully automated.

---

## 12. Spellcasting — Hybrid and Extensible

The existing sheet casting system primarily tracks local resources and requests dice rolls.

Build an authoritative spellcasting system while retaining the manual workflow.

### Spellcasting flow

**Select spell → Select target or area → Preview → Confirm → Server validates and resolves**

The preview should display relevant:

- Spell name and level.
- Available slot levels.
- Casting time and resource cost.
- Attack roll or saving throw.
- Save DC when appropriate and permitted to display.
- Damage/healing formula and potential range.
- Damage type.
- Range and area.
- Concentration.
- Duration.
- Upcasting effects.

### Automated effects

Build reusable primitives for:

- Spell attacks.
- Saving throws.
- Single-target and area damage.
- Healing.
- Conditions.
- Temporary effects.
- Concentration.
- Durations.
- Resource expenditure.
- Effect cleanup.

Do not create a hardcoded combat rewrite for every individual spell.

### Suggested initial spell set

Start with representative SRD 5.1 spells such as:

- Fire Bolt.
- Magic Missile.
- Cure Wounds.
- Shield.
- Fireball.

These exercise attack rolls, automatic hits, healing, reactions, saving throws, and area effects.

Implement only effects whose mechanics can be validated correctly. Document coverage and dependencies.

### Manual fallback

Unsupported spells remain usable through a clear manual/adjudicated workflow.

They must not be falsely labeled as automated.

### Correctness

- Validate spell eligibility, preparation, slots, and targets server-side.
- Never spend a spell slot before the action is accepted.
- Do not spend slots for rejected or invalid requests.
- Correctly support cantrips and eligible upcasting.
- Apply appropriate casting-time action costs.
- Enforce concentration rules for supported spells.
- Respect secret information and DM visibility permissions.

---

## 13. Encounters, Reinforcements, and PvP

### Automatic recruitment

Allow nearby hostile creatures to join an active encounter when appropriate.

Players may also join automatically, subject to campaign rules and optional DM approval.

DMs can manually add, remove, approve, or reject participants.

Prevent overlapping or conflicting encounter ownership.

Nearby encounters remain independent unless deliberately merged by an authorized DM.

### Outside interference

Entities outside an encounter must not bypass its action and damage rules.

Define controlled handling for:

- New attackers.
- Neutral mobs.
- Reinforcements.
- Pets and summons.
- Projectiles in flight.
- Environmental damage.
- Players arriving late.
- Spectators and creative-mode players.

### PvP and friendly fire

In D&D mode, area effects affect eligible allies and enemies according to the spell's actual rules.

Campaign settings separately control intentional direct PvP.

Friendly-fire immunity must not be invented for an SRD spell.

Minecraft Tactical may use independent PvP settings.

---

## 14. World Time, AFK, and Disconnects

### Localized tactical time

Do not pause the entire Minecraft world.

Only encounter participants use turn-based tactical timing.

The surrounding world continues operating normally.

Supported status durations, environmental hazards, ongoing damage, and resource effects on combatants must use a coherent encounter-time policy rather than unintentionally advancing according to real-world thinking time.

Multiple encounters can operate independently.

### Turn timers

No forced player turn timer by default.

Allow configurable campaign- or encounter-specific timers.

DMs can advance or skip a stalled turn.

### Disconnects

A disconnected player's character remains in a recoverable encounter state.

Authorized DMs may temporarily control that character.

On reconnection, restore player ownership and the correct initiative, HP, and resource state.

Ensure a reconnect or delayed network request cannot execute an action twice.

---

## 15. Campaign Persistence and Character Portability

### Server persistence

Save:

- Campaign identity and default ruleset.
- DM and player permissions.
- Character ownership and approved character data.
- Character levels, abilities, equipment, spells, and resources.
- Active encounter identity and ruleset.
- Initiative, round, and turn.
- Action/reaction state.
- HP and conditions.
- Monster and NPC identity.
- Creature positions.
- AI/manual control state.
- Custom creature/equipment templates.
- Prepared encounter templates.

### Restart handling

Automatically persist relevant changes.

After a restart, restored encounters remain paused until the DM authorizes resumption.

Prevent:

- Duplicate creature spawning.
- Repeated actions.
- Lost resources.
- Conflicting entity ownership.
- Stale encounter references.
- Accidental execution of old requests.

### Safe storage

Use versioned save schemas, atomic replacement where supported, backups, validation, and migrations.

Test interrupted writes and recovery from a previous valid state.

### Character imports and exports

Allow characters to be exported and imported between campaigns and supported servers.

An imported character:

- Must pass schema validation.
- Must be compatible with or explicitly adapted to the destination ruleset.
- Can require DM approval.
- Becomes an independent campaign-owned copy.
- Must not import DM permissions or unauthorized physical items.
- Must not overwrite an existing character without explicit action.

Preserve compatibility with existing manual character formats.

---

## 16. Mandatory Audit Remediation — F01 to F18

Use the attached audit as the detailed technical evidence.

Track each finding explicitly.

| ID | Required remediation |
|---|---|
| **F01 — High** | Enforce a single authoritative encounter per entity. Prevent overlapping encounter creation and conflicting lookup. |
| **F02 — High** | Validate melee and other attack geometry, cover, and obstacles on the server. |
| **F03 — High** | Move action accounting from damage callbacks to accepted action identities. Handle misses, delayed damage, and multi-target effects correctly. |
| **F04 — High** | Enforce encounter damage boundaries and define outside-attacker behavior. |
| **F05 — High** | Prevent unauthorized vertical movement, jumping, knockback, or other movement outside active-turn policy. |
| **F06 — High** | Revalidate movement paths against terrain changes, collision geometry, and full creature dimensions. |
| **F07 — Medium** | Integrate relevant item, block, and entity interactions into the action economy. |
| **F08 — High** | Connect character sheets to authoritative combat actor state. |
| **F09 — Medium** | Correct critical hit/failure presentation based on roll purpose. |
| **F10 — Medium** | Reject unsupported dice expressions rather than silently changing their meaning. |
| **F11 — High** | Rate-limit dice traffic and bound dice/card presentation queues and broadcast audiences. |
| **F12 — Medium** | Validate spell operations before resource expenditure; support explicit upcast selection and correct scaling. |
| **F13 — Medium** | Implement safe character persistence, atomic writes, and recovery. |
| **F14 — Medium** | Correct or clearly label threat overlays that are approximate or truncated. |
| **F15 — Medium** | Profile and improve excessive full combat snapshots and repeated calculations. |
| **F16 — Medium** | Implement DM advance-turn controls and configurable AFK policies. |
| **F17 — Low** | Harden packet bounds, decoding, resource cleanup, and lifecycle bookkeeping. |
| **F18 — Medium** | Reconcile encounter rounds with continuing Minecraft world effects. |

Do not automatically classify every missing feature as a defect.

Do not claim that the audit reproduced a crash, exploit, corruption, or multiplayer failure when it established only static evidence.

Prioritize the High findings before implementing dependent gameplay systems.

---

## 17. Implementation Phases

Use the following dependency order.

### Phase 0 — Verification baseline

- Inspect the repository and current changes.
- Verify Java 21 and a complete usable JDK.
- Attempt the existing Gradle build without downgrading dependencies.
- Establish reproducible automated tests.
- Capture existing functionality and limitations.
- Attempt dedicated-server/client smoke checks where supported.

The previous audit could not complete `compileJava` because the environment did not provide an acceptable Java compiler. This is not proof of a repository source defect.

### Phase 1 — Combat authority and correctness

Address the foundational encounter and networking defects, especially F01–F07, F11, and relevant F17 issues.

Prioritize:

- Encounter identity and ownership.
- Correct server validation.
- Action identities and costs.
- Attack legality.
- Movement revalidation.
- Damage boundaries.
- Network bounds.
- Resource cleanup.

**Completion gate:** No overlapping entity ownership, unauthorized action, wall attack, stale-path movement, or unbounded presentation workload in the supported regression tests.

### Phase 2 — Ruleset architecture and characters

Implement:

- Modular ruleset interfaces.
- Campaign and encounter ruleset selection.
- Authoritative character/entity binding.
- Core attributes, AC, HP, speed, and resources.
- Server-controlled character permissions.
- Initial persistent character storage.
- Safe compatibility with manual D20 and percentile packs.

**Completion gate:** Changes to authorized character statistics affect the correct ruleset's combat calculations, while unauthorized clients cannot forge those statistics.

### Phase 3 — Core D&D combat

Implement:

- Dexterity-based initiative.
- Attack preview and confirmation.
- Server dice resolution.
- Damage and AC calculations.
- Weapon rules.
- Cover and visibility.
- Eight-direction movement.
- Movement costs and terrain validation.
- Standard supported actions.

**Completion gate:** A basic multiplayer SRD encounter runs without depending on vanilla damage calculations as substitutes for D&D mechanics.

### Phase 4 — Reactions, conditions, and survival

Implement:

- Interactive reactions.
- Opportunity attacks.
- Relevant conditions.
- Death saves.
- Unconsciousness and stabilization.
- Persistent HP inside and outside combat.
- Climbing, jumping, falling, and supported movement modes.
- Coherent encounter-time effects.
- AFK and DM intervention mechanisms.

**Completion gate:** Verified turn transitions, reaction interruptions, conditions, and zero-HP behavior.

### Phase 5 — DM tools, monsters, and equipment

Implement:

- DM control and authorization.
- Monster AI/manual takeover.
- Custom monster templates.
- Template duplication and independent creature copies.
- Encounter recruitment and controls.
- Physical/virtual equipment mappings.
- Initial supported Fighter and Rogue features.

**Completion gate:** A DM can prepare and run a custom multiplayer encounter with independent duplicated creatures and correctly enforced permissions.

### Phase 6 — Spellcasting and further classes

Implement:

- Authoritative spellcasting framework.
- Initial tested spell set.
- Spell targeting and previews.
- Slots and concentration.
- Damage, healing, saves, and area effects.
- Supported Cleric and Wizard features.
- Manual fallback for unsupported spells.

**Completion gate:** Representative spells resolve correctly and safely in multiplayer.

### Phase 7 — Persistence and campaign recovery

Complete:

- Resumable encounter state.
- Restart-safe actor recovery.
- Persistent campaigns.
- Character imports and exports.
- Template persistence.
- Version migrations and backups.
- Permission recovery and logging.

Basic safe persistence must begin earlier; this phase completes full recovery behavior.

**Completion gate:** A saved encounter can be restored and resumed without duplication, lost resources, or repeated actions.

### Phase 8 — Regression testing and optimization

- Review all F01–F18 findings.
- Profile server tick costs, network traffic, and client performance.
- Verify large encounters and multiple simultaneous encounters.
- Test failure recovery and corrupted/stale requests.
- Verify camera, HUD, grid, dice, and card regressions.
- Test existing non-D&D game packs.
- Document supported versus manual features.

Do not begin automating the remaining SRD classes until the initial four-class foundation has been evaluated.

---

## 18. Required Testing

### Deterministic unit and integration tests

Use controllable dice results to test:

- Initiative and tie handling.
- Natural 1 and 20 outcomes.
- AC equality and attack success.
- Critical damage.
- Advantage/disadvantage.
- Resistance, vulnerability, and immunity.
- Action and reaction expenditure.
- Extra Attack and Multiattack.
- Invalid actions and resource refunds.
- Death saving throws.
- Temporary HP and healing.
- Spell-slot expenditure and upcasting.
- Concentration and duration cleanup.
- Movement costs and blocked geometry.
- Character imports, migrations, and persistence.

### Multiplayer tests

Where an executable environment is available, test at least two clients and a dedicated server.

Include:

- Unauthorized actions.
- Multiple simultaneous encounters.
- DM takeover during an AI turn.
- Delayed projectiles.
- Area effects affecting multiple targets.
- Hidden DM rolls.
- Friendly fire and PvP restrictions.
- Mid-encounter disconnect and reconnect.
- Server restart and encounter recovery.
- Multiple DMs issuing commands.
- Custom monster duplication.
- Out-of-turn movement attempts.
- Player and monster reinforcements.
- Existing Minecraft Tactical mode.

### Performance checks

Measure rather than guess.

Inspect encounter snapshot frequency, server tick behavior, client rendering, network payloads, dice/card queues, and encounter-size limits.

Avoid performance changes that break action correctness or synchronization.

---

## 19. Delivery and Reporting

At the start:

1. Inspect the current workspace and git state.
2. Identify any changes since the audited revision.
3. Summarize the immediate implementation sequence.
4. Begin implementation rather than stopping after a new audit.

**For the initial Astra Medium run, prioritize completing Phase 0 and Phase 1 to a verifiable checkpoint.**

This master specification defines the remaining roadmap for subsequent continuation runs. Do not rush into later phases if the foundation has not passed verification.

For each completed or attempted phase, provide:

- Files changed.
- Components added or refactored.
- Bugs corrected.
- Tests performed.
- Exact results and failures.
- Build and Minecraft runtime verification status.
- Any intentionally deferred functionality.
- Remaining audit findings.
- Relevant technical risks.
- The next recommended phase.
- Instructions for resuming from the current workspace.

Mark requirements as **Implemented**, **Partially Implemented**, **Deferred**, **Blocked**, or **Not Verified**.

Never silently skip a requirement.

Do not claim that a partially implemented subsystem is complete because its interface exists.

Preserve existing project work and avoid unapproved remote changes.

### Final objective

Cubetable should evolve into a robust, modular Minecraft tabletop RPG framework that combines:

- The flexibility of Minecraft.
- SRD 5.1 tactical combat.
- Player-controlled characters.
- Interactive dice and tabletop presentation.
- Powerful but permission-controlled DM tools.
- Custom NPCs and monsters.
- Extensible class and spell automation.
- Multiple future rulesets.
- Reliable multiplayer behavior.
- Persistent campaigns and resumable encounters.

Implement the foundation correctly first. Expand functionality through independently tested phases, preserving compatibility and documenting incomplete work throughout.