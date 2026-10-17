# J2.5C2 — 2014 SRD 5.1 reference completion

Base: `59b75ce2dc9842452db7eb2215a5b2fefbc758e2`, D2.
Branch: `dev/srd51-reference-completion-checkpoint-j25c2` in `shayanzas/Cubetable`.
Final validated implementation/test commit: `1ae0658d7121cc30695182fc66e0a77f4a997e41`.
The final delivery commit adds this report and the coverage document only; resolve its exact hash using `git log -1 --format=%H -- docs/srd51-reference-completion-checkpoint-j25c2.md` or the pushed branch HEAD. A commit cannot embed its own hash; the delivery response supplies it.

## Outcome and scope

The reference catalog expands from **623 to 1,806 entries across 19 categories**, adding **1,183 stable source-qualified IDs**. All original IDs remain present. The import substantially fills the source corpus while preserving explicit limits on structured feature dependencies, special rules and source-edition comparison. It is not a claim that all SRD mechanics are executable or every source table is fully typed.

Reviewable commits cover the source audit (`16a4327`), classes/features (`1871668`), monsters (`cbd920f`), equipment/items (`43762b8`), races/general rules (`6064b81`) and verification/navigation (`1ae0658`). No merge, force-push, upstream modification or primary-world installation is part of this delivery.

## Source and license

The newly uploaded 403-page `SRD-OGL_V5.1.pdf` exactly matches the prior 2014 reference: SHA-256 `d3f94417d2532f42a5abaec07e71a59007bf6cc46992c6458be6667f7a9f1e34`. Its OGL-layout identity is retained; it has not been called a CC PDF. License pages 1–2 and running legal/footer text are excluded from the data extraction. Catalog page numbers refer to that layout.

The official requested CC URL returned HTTP 403 again, including an escalated retry; the alternative Wizards CC URL also returned 403. Independent comparison with the CC PDF remains pending. No comparison differences were fabricated, and no 5.2 or non-SRD source was substituted.

Distribution elects the user's authorized **CC BY 4.0** license. Existing packaged notices, attribution and source metadata remain present:

> This work includes material taken from the System Reference Document 5.1 (SRD 5.1) by Wizards of the Coast LLC and available at https://www.dndbeyond.com/srd. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.

Changes: source extraction, typography/whitespace normalization, structured annotations, stable IDs and cross-references. No endorsement is implied. No new art/audio was added.

## Before/after population

| Category | Before | After |
| --- | ---: | ---: |
| advancement | 1 | 1 |
| armor | 13 | 13 |
| classes | 12 | 12 |
| combat | 2 | 3 |
| conditions | 15 | 15 |
| equipment | 133 | 200 |
| feats | 1 | 1 |
| features | 47 | 305 |
| magic-items | 3 | 240 |
| monsters | 3 | 317 |
| movement | 2 | 3 |
| races | 9 | 9 |
| rests | 2 | 2 |
| rules | 0 | 305 |
| spell-lists | 8 | 8 |
| spells | 319 | 319 |
| subclasses | 12 | 12 |
| subraces | 4 | 4 |
| weapons | 37 | 37 |

All new IDs, source spans, fingerprints and category counts are in `source-coverage-v1.json`. Its 623 retained IDs allow the added set to be derived exactly. Full coverage definitions, eligible-source counts and per-category limits are in `docs/srd51-catalog-coverage.md`.

## Structured reference content

- **Classes:** all twelve classes and 240 ordered level rows, PB, printed features, spell slots, Bard known spells/cantrips, Sorcery Points, Warlock pact/invocation counts, Monk Ki/movement/Martial Arts, Rogue Sneak Attack and Barbarian Rage columns. ASI and subclass selection levels are indexed. Exact proficiency and starting-equipment text is retained. Source paragraphs/tables remain available alongside annotations.
- **Subclasses/features:** only the twelve SRD subclasses. Full subclass source sections and section-bounded child links; 305 feature/subsection references including conditional choices and invocations. Prerequisite text, exact text-mention links and progression links are descriptive. **108 feature levels remain explicitly unresolved**; absent levels are not silently assigned level 1. Original legacy feature IDs/metadata remain frozen. The original 14 unmatched summary aliases are not invented as mechanical mappings.
- **Monsters:** all 317 stat blocks found by independent Armor Class anchors in pp.261–357 and 366–403. Names, classifications, AC/HP/Hit Dice, speed text/modes, six abilities, saves, skills, defenses, senses, languages, CR/XP and full trait/action descriptions are retained. Reactions/legendary/lair/regional sections are preserved where supplied. Spell references are explicitly text mentions requiring review, not inferred spellcasting entitlements. Multiattack text is retained without execution.
- **Magic items:** all 240 type/rarity anchors in pp.207–253, including Orb of Dragonkind. Item type, rarity/variable-rarity status, attunement requirements, charge-text index, explicit maximum charges where unambiguous, usage restrictions and full source descriptions. Five variable-rarity records retain their source tables/prose; variant/effect/charge semantics are not claimed fully structured.
- **Equipment:** original 133 rows preserved, plus 67 mount/vehicle/service/pack records. Seven packs include source contents; 44 gear records gain matched detailed prose. Existing weapon/armor metadata is retained. Not every trade-good/variant or item special use has its own typed record; broader source sections preserve their text.
- **Races/rules:** all nine families/four subraces retain source text with reviewed ability adjustments, choice context, languages and trait annotations. Base adjustment metadata is separated from included subrace prose. 305 additional general-rule headings cover checks, skills, equipment, exploration, combat, spellcasting, adjudication, monster guidance and appendices; source tables may remain descriptive.

## Verification architecture

Catalog schema 1 remains additive: existing data fields are retained, with enriched reference metadata under `data.reference` where needed. The reader supports explicitly unresolved feature levels and validates the new structures. It is a static resource format; campaign/library/stage formats are untouched.

`source-coverage-v1.json` schema 1 stores the exact PDF hash, bounded source candidates/page-line spans/source-text hashes, category population, all retained IDs, frozen baseline data/description hashes and per-record hashes. The static validator checks both inventories and content identity. Defined source totals are not equated with complete rule implementation. General heading/gear scans explicitly retain partial structural status.

`tools/verify_srd51_source.py` runs the original baseline importer and additive expansion in an isolated temporary tree, then compares catalog, inventory and evidence byte-for-byte. Final result: **all three artifacts reconstruct identically from the exact PDF**. Maintainer dependency: PyMuPDF 1.26.6. No PDF parser or network access is needed by Minecraft or the normal Java build.

Negative fixtures reject changed source text, missing records/evidence, invalid monster numeric types, missing class levels, negative slots, missing rarity/attunement restrictions, forged equipment costs and unresolved levels without an explicit status. Regression tests cover split-column continuation, Barbarian Unlimited, Rogue level-10 ASI, Wizard spell slots, all reference detail rendering and Champion's five correct child features. Original assertions were not deleted or weakened.

## Navigation

The existing reference browser gains search across all names and readable class-level/stat-block details. Category browsing/search, source attribution and read-only labels remain. There are no new gameplay buttons or grants. All 1,806 detail render models are exercised by tests; layout, scrolling, GUI scales and graphical usability remain unverified in an actual client.

## Compatibility

No changes to SrdAuthoring, CreationRules, MonsterProfiles, encounters, placement, attacks, transactions, checks, cinematic dice, character repositories or world schemas. Library schema 2, campaign schema 5, D2 stage schema 1 and J2 receipts are retained. No real save was loaded, converted or migrated. Catalog data is consumed only by the static reference UI and the existing reviewed MonsterProfiles lookup.

The executable monster allowlist remains exactly Zombie/Skeleton/Goblin. Their original reviewed data and descriptions are fingerprinted unchanged after removing additive reference annotations. The same protection applies to original spell metadata/descriptions, legacy feature records, conditions and existing rule batches. Source expansion never reinterprets a saved character.

## Automated results

Java 21 command completed successfully:

```sh
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

**BUILD SUCCESSFUL**, 1m 3s. **36,855 foundation assertions across 41 suites**. **143/143 GameTests**, zero failures. Dedicated catalog validation: **2,748 existing-suite checks plus 15,115 completion/coverage checks = 17,863 checks**. The coverage task reruns the same completion suite; these repeated invocations are not added again to the foundation total.

Source reconstruction verification also passed. Local evidence: `build/j25c2-validation.log`, `build/j25c2-source-verification.log`, `build/gametest-results.xml` and `build/reports/srd51-catalog.json`. Generated reports/test worlds are not committed. Existing J2/D1/D2/E, creator/library, permission and Tactical regression suites all remain required and passed. The offline GameTest server's external public-key lookup warning and existing deprecated API warnings did not prevent completion.

This environment used the previously established complete Java 21 JDK at `/workspace/.tools/jdk21`, Gradle cache at `/workspace/.gradle`, and its configured HTTPS proxy/trust store. No build target or test was weakened.

Built JAR: `build/libs/tactical-combat-0.1.0.jar`.
SHA-256: `89c78ad87d2419457e7041fcf8d17314044206b4a96a0e18fac26b715cd7e533`.

## Later Minecraft review

No graphical Minecraft testing was available. A–E/D2 graphical acceptance remains pending.

1. On a backed-up test setup, open SRD Reference and inspect category counts and attribution/verification notice.
2. Use Search all entries for a spell, monster, magic item and class feature; return through category/detail screens and resize at GUI scales 2/3/4.
3. Inspect each class's level table, especially Barbarian 11/20, Rogue 10 and Wizard 20. Confirm the interface calls them references and preserves source descriptions.
4. Inspect Champion and other SRD subclass sections; compare feature prerequisites and unresolved-level labels. Do not treat conditional choices as automatic grants.
5. Inspect Aboleth, dragons, beasts and NPCs; confirm no new Prepare/Attack button appears. D2 preparation should still offer only its existing three monsters.
6. Inspect magic-item rarity/attunement, variable-rarity source text, equipment packs, mount/service rows and gear descriptions. Confirm nothing equips, consumes, attunes or changes AC.
7. Open existing personal/campaign characters. Check creator, approval, binding, HP/ammunition, ordinary attacks, saved stages, D1 private checks and E dice presentation in the existing test world. No saved values should change from browsing.
8. Compare typography, long text wrapping, scrollbar performance and category/all-name search in constrained windows. Report graphical issues separately from source/metadata issues.

## Known limitations and next review needs

Independent CC-edition comparison is pending due to HTTP 403. Feature-level/dependency/conditional metadata is partial, with all 108 unresolved levels listed by ID. Monster actions, spell mention links, item charges/variants and many subsidiary tables require further semantic review before any future executable mechanic. Equipment coverage is broader but does not claim a universal typed row for every trade good/variant. General-rule heading coverage is not proof that every rule predicate is structured. Original source descriptions are retained so future reviews can resolve specific gaps rather than guessing.

## Files changed

- `build.gradle`
- `docs/srd51-catalog-coverage.md`
- `docs/srd51-reference-completion-checkpoint-j25c2.md`
- `docs/srd51-reference-source-audit-j25c2.md`
- `src/main/java/dev/tacticalcombat/client/ui/ReferenceCatalogScreen.java`
- `src/main/java/dev/tacticalcombat/reference/SrdCoverageValidator.java`
- `src/main/java/dev/tacticalcombat/reference/SrdReferenceCatalog.java`
- `src/main/resources/data/tacticalcombat/reference/catalog-v1.json`
- `src/main/resources/data/tacticalcombat/reference/source-coverage-v1.json`
- `src/main/resources/data/tacticalcombat/reference/source-inventory-v1.json`
- `src/test/java/dev/tacticalcombat/FoundationTests.java`
- `src/test/java/dev/tacticalcombat/reference/CatalogCompletionTests.java`
- `src/test/java/dev/tacticalcombat/reference/CatalogValidationTests.java`
- `tools/expand_srd51_reference.py`
- `tools/verify_srd51_source.py`
