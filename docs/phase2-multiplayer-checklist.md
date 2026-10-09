# Phase 2 two-client acceptance checklist

**Status: Not Verified in the cloud environment.** There is no DISPLAY or Xvfb. Headless tests and mock players are separate evidence. The [Phase 1 real-client checklist](phase1-multiplayer-checklist.md) remains open.

Record date, tester, code commit/JAR SHA-256, server/client OS, Minecraft/Fabric/API versions, GUI scale, latency, extra mods, result and screenshots/log excerpts for each scenario. Mark Pass, Fail or Not Run; do not infer visual success from a build.

## Setup

1. Use Java 21 and the unchanged dependencies: Minecraft 1.21.1, Fabric Loader 0.16.9, Fabric API 0.116.1+1.21.1.
2. Build with `./gradlew build runGametest` (Windows: `gradlew.bat build runGametest`). Cloud helper: `/workspace/cubetable-setup/gradle.sh build runGametest`.
3. Install `build/libs/tactical-combat-0.1.0.jar` and Fabric API on the dedicated server and both clients. Install both existing manual packs according to `sheet-formats.md`.
4. Use a copied/disposable world. Client A is DM/operator; client B is Player, an ordinary non-operator. Avoid unrelated mods for the first run.
5. DM: `/tbc campaign create Phase 2 Test`, then `/tbc campaign invite Player`. Open the character sheet with K or `/sheet`, click **Server**, and select the campaign by name on both clients.

## 1. Campaign creation, invitation and selection

- Ordinary Player cannot create a campaign or appoint a co-DM.
- Player's campaign list contains only authorized campaigns. Create a separate DM-only campaign and verify it is absent.
- Selection succeeds for an invited campaign and is rejected during active combat. The UI must not report a failed selection as successful.

## 2. Submission and existing-character discovery

- Player opens the Generic D20 sample locally; confirm its header says Local.
- In Server, select the invited campaign and choose **New from local**. Confirm the explicit new-record operation.
- Expect an OK acknowledgement, named server character, draft approval status, unbound status and edit revision 1.
- Return to Characters and select that named record. Do not use New from local to update it.
- Repeated existing-record updates must keep the same UUID and list count. Repeat with the Percentile sample.

## 3. DM review and approval

- DM selects the campaign and sees Player's character in its list. Player cannot see a separate DM-owned character.
- DM opens the character, browses fields and clicks **Approve**. Expect approved status and a new edit revision.
- Player clicks Refresh and sees approval. A forged/direct player approval request must be rejected by the server, regardless of client button visibility.

## 4. Binding

- DM obtains the current ID/revision from detail or `/tbc character show UUID` and runs `/tbc character bind UUID REVISION Player`.
- Refresh shows BOUND. Attempt to bind the same character to another actor or another character to Player; both must fail.
- Binding someone else's character to Player must fail. Editing a bound character through UI or commands must fail, including for the DM.

## 5. Authorized refresh

- While bound, change Player's live HP through normal vanilla gameplay. Click Refresh immediately: server HP should reflect the current loaded actor without waiting for the periodic capture.
- Request another campaign's private character ID through a test client or troubleshooting request. Expect a permission error with no sheet data.
- Compare command `show`, UI server stats and live HP, allowing only the documented effects of external health modifiers (tested separately).

## 6. Local/server reconciliation

- End combat and unbind. Player stages a name change in the server workspace without saving.
- DM changes a different field (e.g. AC) and saves on the server.
- Player clicks Refresh: the latest server snapshot changes, but the staged name and typed input remain.
- Reconcile should preserve both the staged name and the DM's different-field update. Save changes must update the existing UUID.
- Import local stages same-format local fields, retains server-only fields, and never edits the local file. Reject an incompatible format.
- Use latest and closing/switching with unsaved changes must require an explicit discard decision. Confirm the local sheet still has its original data.

## 7. Conflicting edits

- Both clients fetch the same unbound character revision and stage different values for the same field.
- DM saves first. Player's stale Save changes must return CONFLICT without losing the draft.
- Refresh then Reconcile must name the overlapping field and refuse an automatic overwrite.
- Keep my edits must ask for confirmation. After confirmation and Save, the new value should apply against the fresh revision; another concurrent change must still cause a conflict.
- Repeat with Player temporarily appointed co-DM, then revoke that role and repeat denied approval/policy attempts.

## 8. Unauthorized inspection and authority flags

- Using two accounts/campaigns, test unrelated campaign listing, foreign character GET/edit/approve, attempted ownership/approval flag injection and reuse of an old privileged request after co-DM revocation.
- No new private snapshot or approval may be disclosed/granted. An old replay acknowledgement must contain no cached private sheet.
- An operator remains explicitly authorized globally; use a non-operator for denial checks.

## 9. Approval policies

Use `/tbc campaign policy` to read the policy and current campaign revision. Replace REV below each time; policy writes increment the campaign revision.

```text
/tbc campaign policy REV true true false text.*,values.*,collections.*,kind,theme,ui
```

- Secure default: new characters are drafts; editing an approved unbound character invalidates approval.
- Set new/edit approval to false with the same field list. A newly created permitted character is approved by the server; permitted edits to an already-approved character retain approval. Existing unapproved drafts remain unapproved.
- Set owner fields to `text.name`. Owner name edits work, while HP/movement/AC changes and full imports containing forbidden fields fail.
- Set locked to true. Ordinary owners cannot create/edit; authorized managers can edit unbound records. Nobody can directly edit bound stats.
- Player policy commands must fail. Two managers writing the same policy revision: only the first succeeds.
- Restore the secure default afterward.

## 10. HP, movement and initiative

- On an unbound tactical character, stage `values.hp_max = 40`, `values.hp = 30`, `values.movement_blocks = 4`, and `values.initiative_bonus = 5`; save, approve and bind.
- Verify initial live HP/max HP, four-block turn allowance and d20+5 initiative. Changing only the local sheet must not change these authoritative values.
- HP changes advance the HP revision, not the edit revision. An unrelated configuration patch after unbinding must preserve the latest HP; a stale explicit HP change must conflict.
- End/unbind restores the character modifier's contribution to vanilla max health. Other modifiers must not be accidentally removed.

## 11. Nearby campaigns

- Put players assigned to different campaigns near the same area. Verify automatic recruitment does not put them in the other campaign's fight.
- Run independent fights nearby and repeat the Phase 1 no-overlap/no-outside-action checks.
- An unbound player without campaign selection retains legacy Minecraft Tactical behavior.

## 12. Disconnect, entity reload, death and restart

- Damage a bound player, disconnect promptly, reconnect and refresh. Do not heal from an old sheet or accumulate max-health modifiers.
- Bind a DM-owned character to a supported living NPC. Damage it, unload/reload its chunk and compare current/max HP and binding identity.
- Kill/respawn a bound player. Expect vanilla death/respawn behavior and one re-applied maximum-health modifier, not D&D death saves.
- Release an offline/removed binding with `/tbc character release UUID REVISION`; a returning entity must lose the stale character modifier.
- Stop and restart the dedicated-server process. Verify policies, identity, approval, stats and bindings survive. Encounters are not resumable in this phase.

## 13. Ruleset isolation

- Set the campaign default to `manual_tabletop`; verify new automatic tactical encounters are disabled for selected members.
- `/tbc encounter minecraft_tactical` may explicitly start the supported override for an eligible unbound/tactical party.
- Change default during the encounter: its current policy stays tactical.
- `srd_5_1` cannot start automated combat. Incompatible bound profiles must be rejected without converting or corrupting characters.
- Both packs' original data, formulas, local rolls and cards must still work manually.

## 14. Phase 1 and network regressions

- Complete the existing camera/grid/HUD, movement, attack legality, inventory, projectile, disconnect and adjacent-fight checklist.
- Under latency, do not lose typed/staged edits on reply. Repeated requests with the same sequence cannot create a second record or apply an edit twice.
- Excess requests report RATE_LIMIT. Lost acknowledgements/retries may report ALREADY_PROCESSED; refresh the list/state before another mutation.
- Malformed/oversized/truncated payloads must be rejected without authorization changes. These hostile protocol cases require a test client/harness, not ordinary UI clicking.
- Against a server without the v2 channel, Server controls should be absent while local sheets/manual features remain usable.

## 15. Small-screen UI

- Test the smallest supported Minecraft GUI area (including 320×240), larger GUI scales, and resizing the existing local sheet.
- Check Local/Server distinction, title buttons, list pagination, field/value controls, field browsing, wrapped errors and confirmation dialogs for overlap/clipping.
- Test a long character name, long policy/error text, more than eight characters/campaigns, and a large collection. No record should become unreachable through pagination.
- Verify keyboard focus and Escape, confirmation cancellation, and returning to the local sheet preserve the expected state. Staged server edits are in memory and are not promised to survive a disconnect/process exit.

## Persistence fault acceptance on a copied world

With the server stopped, separately test missing/corrupt primary with a valid backup, two invalid files, an unsupported future schema and unwritable storage. Expect warned recovery or a closed/unavailable character service; never silent empty data. Keep original copies for restoration. A backup can roll back the latest policy/approval, so inspect the recovered permissions.

Record every result. Any failed real-client authority or data-loss case keeps release acceptance open, even when automated tests pass.
