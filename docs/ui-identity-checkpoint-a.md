# Cubetable — Navigation and Identity, Checkpoint A

Implemented locally; waiting for Minecraft screenshots and visual approval. **Actual Minecraft GUI rendering was NOT TESTED.** This workspace has no configured graphical display or Xvfb. Headless layout/input assertions and server GameTests are not visual acceptance.

## Branch and commits

Starting branch: `dev/ui-correction-checkpoint-2`.

Base commit: `9ab7795d41a7a42751d0d9b0cf6a7f2d3039e76d` (completed Checkpoint 3).

New local branch, created before source changes: `dev/ui-identity-checkpoint-a`.

Implementation commits:

- `adcc596` — Nine creator tabs, deferred background compatibility, nine-section preview progress, and removal of duplicated Identity fields from Character Details.
- `3d9a4f3` — Measured 3×3 race cards, direct race descriptions, explicit base/subrace choices, inline alignment dropdown, popup/focus routing and focused regression coverage.

A documentation commit follows. No push, merge, reclone, reset or server/combat migration was performed.

## Requirement checklist

PASS below means code/static/input/service verification. It does not assert visual acceptance.

| Requirement | Result | Evidence / limit |
| --- | --- | --- |
| Exactly nine tabs in the requested order | PASS | Identity, Class, Ability Points, Proficiencies, Equipment, Features, Spells, Character Details, Review & Finish; exact-order test |
| No standalone Background, Overview or Method creator tab | PASS | Removed creator enum/routes; Continue uses actual last step rather than an eleven-step constant |
| Deferred background remains optional; old data preserved | PASS | No default assigned; blank-background guided draft validates/finalizes; personal background survives Identity edits and repository restart |
| Fixed, uniform 3-column × 3-row race grid | PASS | Nine measured card rectangles, equal width/height, spacing and non-overlapping hitboxes; all nine fit tested normal logical viewports |
| Substantially larger cards, centered readable labels | NOT VISUALLY VERIFIED | Cards use available Identity space. Active Minecraft font widths drive wrapping/centering; headless metric fixtures preserve every label character, including Dragonborn. Actual screenshots needed |
| Name first, Age/Height/Alignment second | PASS | Identity controls share those rows; duplicate Age/Height/Alignment editors removed from the optional story screen without deleting stored values |
| Nine-option alignment dropdown | PASS | Inline mouse/keyboard selection, Escape cancellation, workspace-bounded scrolling popup; all nine values persist; unknown legacy values remain until explicitly changed |
| Preview → description → contextual Subrace → Racial Traits | PASS | Direct original race introduction, race saved/unsaved status, explicit base/original and supported variants, individual trait inspection |
| Base/manual and incomplete SRD selections clearly distinguished | PASS | Base-only Elf/Dwarf/Halfling/Gnome labeled incomplete/manual; variants use supported 2014 SRD names. BG3-only variants are not introduced |
| Incompatible subrace transitions | PASS | Selecting a new race resets its staged species string; switching High Elf → Dwarf → Hill Dwarf → base → Tiefling tested; unsupported variants rejected locally |
| Preview and server acknowledgement remain separate | PASS | Real service conflict/ack tests retain old saved race until complete acknowledgement; newer story data survives retry; malformed replies cannot publish success |
| Reopening and ownership/revision protection | PASS | Restart restores race/alignment/background; unauthorized identity update denied; existing revision checks and server storage unchanged |
| Other creator screens and dashboard preserved | PASS | Only navigation references/names and removal of duplicated story Identity fields changed. Class/ability/proficiency/equipment/feature/spell/review/dashboard implementations remain from the prior checkpoint |
| No combat, mob, weapon, movement, damage, HUD or authority change | PASS | No changes to rules, character repositories, UI API, networking or combat implementations; existing GameTests passed |
| Actual GUI scales 2, 3, 4 and permitted 8; no visual clipping/overlap | NOT VISUALLY VERIFIED | No graphical client run or screenshots of this build. This is the outstanding acceptance gate |

## Files and implementation notes

- `CreatorFlow`, `CharacterWizardScreen`, `WizardModel`: nine visible steps and nine-step preview progress. Legacy `WizardModel.Stage.BACKGROUND` remains an internal compatibility category marked deferred, not a visible tab or requirement. Existing server validation already does not require a background, so no backend change was necessary.
- `CreatorWorkspace`: focused Identity replacement only. The existing Class implementation is unchanged. Preview descriptions are displayed directly and wrapped using the active Minecraft text renderer, rather than hidden behind an overview button. Trait entries retain the shared SRD hover/detail inspector; the heading is exactly **Racial Traits**.
- `IdentityLayout`, `IdentityRaceGrid`: three equal columns and rows, centered measured labels, clear selection, arrows/Enter, and card-sized hitboxes. Grid height uses available space rather than the old fixed 44-pixel class/race cell layout. Narrow workspaces reflow information below the full 3×3 grid; the sequence remains intact.
- `IdentityModel`: conventional alignment options, original approachable race introductions and compatible race/subrace transitions. Preview choices change local intent only. No automatic racial feature or ability-score grant.
- `AlignmentDropdown`: an in-page dropdown with all nine conventional alignments. Mouse and keyboard input work without another screen. Unknown saved strings are preserved and identified on hover. The popup stays within the workspace and scrolls when needed.
- `CharacterCanvas`: popup hit testing follows render order, focus is established before callbacks that rebuild the preview, and selecting the already-focused widget does not briefly blur it. A focused test caught and verified the popup-closing bug and confirms clicks do not pass through to race cards.
- `CharacterDetailsScreen`: retains six narrative fields and their editor behavior. Saves still merge those fields into the latest identity section, preserving age, height, alignment, background and race data.
- `IdentityCheckpointTests`, `CreatorCheckpointTests`, `FoundationTests`: focused tests registered in the existing full assertion runner. Obsolete Background creator-catalog geometry assertions were removed with the removed tab; unrelated previous coverage remains.

The attached pasted request was treated as the user's instruction. The extracted `README_START_HERE.md`/manifest and five images were inspected as supporting specifications/reference material before implementation. They live under `/workspace/attachments/checkpoint-a-references/`. No image assets or BG3 rules/content were copied into the mod; existing approved assets remain unchanged.

## Build and tests

Final full Java 21 build: **PASS**, including **3,915 assertions across 21 suites**. Focused Identity suite: **1,106 assertions**.

GameTests: **109/109 required tests passed**, zero XML failures. The full `build runGametest` run passed; the final popup-focus correction and its new input regression received another full build/assertion run. No server/GameTest behavior changed in that final correction.

`git diff --check` passed. Release JAR contains all new Identity/dropdown classes. Final worktree is clean after the documentation commit. Generated root test logs were preserved outside the checkout at `/tmp/cubetable-identity-a-generated-logs/`; build logs are `/tmp/identity-a-validation.log` and `/tmp/identity-a-final-build2.log`.

The existing Java 21 toolchain, Fabric and Gradle configuration were reused with TLS validation enabled:

```bash
export JAVA_HOME=/workspace/.tools/jdk-21.0.12.1
export PATH="$JAVA_HOME/bin:$PATH"
export GRADLE_USER_HOME=/workspace/.gradle
export JAVA_TOOL_OPTIONS='-Dhttps.proxyHost=proxy -Dhttps.proxyPort=8080 -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 -Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'
bash ./gradlew --no-daemon --max-workers=4 build runGametest
```

Updated release JAR: `build/libs/tactical-combat-0.1.0.jar`.

SHA256: `61feaf3ef555e4363da66dbf430c3bad1a5c5a969a8ad5acc674a969bb168977`.

## Remaining acceptance issues

No known nonuniform-card or incompatible-subrace failure remains in the measured/input/service checks. Actual card composition, text sharpness, selected versus keyboard-focus styling, tooltip placement, alignment popup legibility and control alignment remain unverified in Minecraft.

Compact layouts put information below the race grid. Very small logical windows require internal scrolling; larger windows keep grid and information side by side. Test this behavior at actual supported GUI scales. Extremely narrow alignment fields can show a shortened label, with the full value available by hover/narration; whether this occurs at permitted Minecraft window/scale combinations requires screenshots.

Base-only races and unknown existing custom subraces are permitted manual records, not claims of complete SRD character legality. No unsupported trait automatically affects combat. Live two-client latency/reconnect/conflict behavior remains a manual acceptance check despite the real-service revision/ownership tests.

## Brief Minecraft procedure

1. Back up a disposable world. Use Java 21, Minecraft 1.21.1, Fabric Loader 0.16.9 and Fabric API 0.116.1+1.21.1. Replace the old mod with `build/libs/tactical-combat-0.1.0.jar`; install only the release JAR, not `-dev`, `-sources` or the test mod.
2. Open Cubetable → My Characters → Create Character, and reopen an older draft. Count the nine tabs in the exact sequence. Confirm no Background/Overview/Method page and no assigned/required default background.
3. Inspect the full 3×3 grid, especially Dragonborn, at GUI scales 2, 3, 4 and 8 where the window permits. Check equal cards, centered labels, selection borders, mouse hitboxes, and keyboard arrows/Enter.
4. Choose Elf/High Elf, Dwarf/Hill Dwarf, Halfling/Lightfoot and Gnome/Rock Gnome. Choose Base directly, switch races, and verify incompatible variants clear. Compare saved summary versus unsaved Preview before Save/Continue and after acknowledgement. Read introductions and inspect individual Racial Traits.
5. Use alignment dropdown mouse/keyboard/scroll/Escape; save/reopen each conventional alignment. Reopen a legacy custom value and verify it stays intact until changed. Check Age/Height/Alignment are absent from Character Details but their saved values survive story editing.
6. Refresh/resize with unsaved edits, save/reopen/reconnect, and test a conflicting save from another client. Verify no rejected save appears successful and background/story values survive. Briefly visit the other seven tabs to confirm existing editors/navigation still work; stop before redesigning them.
7. Send Identity screenshots with the grid and information panel, a selected subrace, and an open alignment dropdown at normal and compact sizes. Include GUI scale/window dimensions and any client/server crash logs. Suggested files: `identity_a_scale2.png`, `identity_a_scale3_subrace.png`, `identity_a_scale4_alignment.png`, and a supported scale8/compact variant.

Stop here for the user's Minecraft screenshots and approval. Class and Ability Points are subsequent checkpoints. This branch remains local; no push or merge is authorized.
