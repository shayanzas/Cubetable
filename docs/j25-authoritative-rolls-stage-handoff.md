# J2.5D authoritative rolls, stages and monster handoff

This is a handoff, not implementation of D or E. Base for the next checkpoint is the delivered `dev/gameplay-usability-catalog-checkpoint-j25` tip. Review the J2 transaction report before changing action or resource execution.

## Existing roll boundary

- `DiceToolbarScreen` sends the existing `DiceRequestPayload("Manual dice", type, count, modifier, mode)` through Fabric networking. `DiceService` generates faces on the server, applies its existing count/modifier bounds and d20 keep policy, and publishes `DiceRollPayload` plus existing authorized manual events.
- Quantity is 1–10, modifier -999…999, mode normal/advantage/disadvantage; advantage/disadvantage is meaningful only for one d20. These are **manual inputs**, never authenticated character modifiers. Existing commands/local manual sheets remain diagnostic/legacy consumers.
- Manual audience remains nearby compatible players outside combat, or compatible encounter peers during combat. No DM-only or targeted-private execution is enabled. The Rolls section explains this limitation rather than offering nonfunctional controls.
- The toolbar listens to its own player's `Manual dice` payload for presentation. It never generates final faces. Existing `DiceAnimation`, `CubetableHud`, roll events and skip controls remain independent of the toolbar's arithmetic-free presentation.

## Authoritative character checks: work for D

`CharacterRollMenuScreen` reads `QUICK_SHEET` and displays J1 ability modifiers plus existing `DashboardModel.checks` informational skill/save totals and breakdowns. It offers **no official check execution**. Recorded proficiencies/adjustments are still informational; do not turn these client totals into server authority by renaming a manual roll.

Add explicit typed intents identifying campaign, character, ability/skill/save key, context and expected revisions. Resolve the authenticated actor/controller and current approved projection on the server. Validate which recorded proficiencies/bonuses are actually eligible under the approved rules model. Never accept the UI's displayed modifier, success declaration, difficulty result or die faces as authority. Existing `QUICK_SHEET` is a private read, not authorization to mutate or execute later.

For forced/requested rolls, require current DM authority, selected campaign, authorized targets, bounded recipients, explicit requested purpose and visibility. Recheck roles/controllers on execution and delivery. Establish a distinct manual-versus-character result provenance contract. Public, DM-only and targeted-private visibility need server-side recipient filtering and invalidation; do not call the current broadcasting `DiceService.roll` and try to hide a private result afterward on the client.

Use server-generated identities and fixed outcomes for accepted operations. Where a check can trigger a durable resource/effect change, extend the existing J2 typed plan and same campaign commit boundary with a reviewed versioned action type. Do not copy `commitGameplay` into a second engine, accept arbitrary formulas, rebase stale snapshots or roll again after save failure. Pure informational rolls still need explicit request/replay and audit policy; do not imply all free rolls are J2 gameplay receipts.

Suggested result fields for E to consume: operation ID, provenance/manual-or-authoritative kind, rules version, authenticated source actor, validated purpose, visibility scope, die type, immutable faces, kept indices, server modifier breakdown and total. D owns the wire/schema contract; this checkpoint does not install a competing one.

## Stage boundary

The Scene tab is a **read-only preview** from existing `ENCOUNTER_DISCOVER`. It uses server origin, dimension, 32-block radius and actor coordinates to show an orientation map and eligibility tooltips. It neither saves a template nor moves an actor. Discovery is a bounded UI snapshot, not a persistent stage or movement authorization token. Terrain can change after preview.

Actual encounter creation already performs server admission and safe grid alignment. `SrdTerrain.admissionCell/alignment` chooses the closest same-height anchor, validates the full logical/physical swept footprint, support, collision, border, loaded chunks, hazards and occupancy. Confirmed starts move at most half a block on each horizontal axis; no vertical movement or obstacle bypass. Reuse this geometry where applicable, but do not generalize it into arbitrary stage teleportation.

D should define versioned stage IDs/revisions, campaign scope, dimension/area, actor placement slots, source references, permissions and explicit activation. Validate loaded entities, participant footprints and simultaneous occupancy for the entire plan before mutation. A saved stage is an encounter area and initial placement, not environmental scripting. Keep preview, saved template and authorized activation distinct. No fake Save Stage endpoint exists to replace.

Campaign schema 5 commits cannot atomically include Minecraft entity/chunk files. Define failure/restart recovery for positioning separately and honestly. J2 never resumes a whole encounter on restart. Stage activation must not release a PREPARED resource reservation, reset spent movement, reroll an outcome or overwrite an approved character automatically.

## Monster adapter boundary

Reference IDs `srd51:monsters:zombie`, `srd51:monsters:goblin` and `srd51:monsters:skeleton` contain verified source stat blocks. They are **not executable characters**. `ActorBindingScreen` binds an already approved available SRD campaign character via the existing `BIND` service; it does not derive a character from a Minecraft mob or the catalog.

D can introduce an explicit prepare-NPC operation selecting a verified monster reference and creating a reviewable independent character. Define ownership/controller, approval, binding, revisions and unsupported mechanics before activation. Preserve existing characters and J2 reservations. Zombie Undead Fortitude, damage/condition immunities, special actions and spellcasting must be implemented and tested individually or visibly unavailable. A source stat block alone must never advertise those features as executable. No hidden Minecraft-to-D&D inference or silently replaced legacy AC/HP is acceptable.

## Catalog contract and verification limits

`SrdReferenceCatalog` reads version-1 static resources in `data/tacticalcombat/reference/`. Stable IDs are `srd51:<category>:<slug>`; each entry carries source ID, PDF pages, verification state, description, typed category metadata and related IDs. Data returned to callers is defensive-copied. This never writes into library schema 2 or campaign schema 5.

All entries are reference material. In particular, a feature's `minimumLevel` and class reference are **not a complete entitlement predicate**: subclass choices, prerequisite chains, armor/loadout, resource policies and choices still require D's reviewed executable adapters. The 12 class entries index identity/Hit Dice, not complete progression tables. Do not infer auto-grants from descriptive text or introduce a shadow rules engine. Existing authoring catalogs and legacy feature IDs remain intact; imported features retain `legacyReferenceId` where source headings matched.

See `srd51-catalog-coverage.md` for full per-category coverage and the 14 unimported legacy summary labels. Text is verified against the user-provided 2014 SRD 5.1 PDF. Distribution elects CC BY 4.0 based on the user's official dual-license confirmation. Official/archive retrieval returned HTTP 403; independent CC-edition comparison remains an explicit provenance limitation, not a claim that the uploaded license pages themselves are CC.

## UI/navigation integration

- J: own bound-character Quick Sheet. V: interactive manual dice toolbar; optional locally persisted pin leaves a passive result strip above the vanilla control area.
- DM Workspace has its own configurable, initially unbound key. Party retains up to six inspection-tab identities, fetching current authorized content on selection. Actors opens sorted/filterable discovery and quick binding. Encounters reuses existing initiative/tie/combat/end/recovery controls. Scene is read-only. Rolls exposes existing manual/history operations.
- Keep one active permission watch per private panel. Manager/selected-campaign changes clear privileged content and stale callbacks. Tabs retain identities rather than cached private sheets. Full management remains available from Cubetable.
- For E, consume authoritative final results without changing them. Geometry, cinematic motion/sounds, quick/reduced-motion modes and skip behavior belong to a separate Minecraft-reviewed presentation checkpoint. This delivery makes no cinematic visual-acceptance claim.

## Regression gate

Retain J2's real filesystem fault cases, child-JVM abrupt-exit tests, receipt retention/replay checks and movement preservation. Add D-specific authorization, recipient privacy, stale revision, replay, save failure and stage activation/rollback tests. Run Java 21 `build runGametest validateSrdCatalog`. Graphical Minecraft and multi-client privacy acceptance remain separate from automated evidence.
