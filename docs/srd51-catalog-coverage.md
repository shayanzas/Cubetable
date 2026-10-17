# SRD 5.1 reference catalog coverage — J2.5C2

The catalog contains **1806 entries across 19 categories**, up from 623. All are static references. D2's separate executable monster adapter still permits exactly Zombie, Skeleton and Goblin, with its existing partial support. No catalog presence grants equipment, class/race features, spells, levels or monster actions.

## Source and elected license

The user-supplied `SRD-OGL_V5.1.pdf` is the 403-page **2014 SRD 5.1 OGL-layout reference**, SHA-256 `d3f94417d2532f42a5abaec07e71a59007bf6cc46992c6458be6667f7a9f1e34`. The new upload matches the previous source exactly. It has not been relabeled as a CC PDF. Pages 1–2 and running license/footer material are excluded from extracted content. Catalog page numbers refer to this layout.

Distribution retains the elected **CC BY 4.0** license, consistent with the user's official dual-license confirmation:

> This work includes material taken from the System Reference Document 5.1 (SRD 5.1) by Wizards of the Coast LLC and available at https://www.dndbeyond.com/srd. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.

The notice also remains packaged in `SRD-5.1-NOTICE.txt` and displayed in the browser. Changes are extraction/typographic normalization, structured annotations and stable IDs. No endorsement is implied.

The official CC URL `https://media.dndbeyond.com/compendium-images/srd/5.1/SRD_CC_v5.1.pdf` returned HTTP 403 again, including the escalated retry. The alternative Wizards CC URL also returned 403. Independent CC-edition text comparison therefore remains **pending**; no differences between editions are claimed to have been checked. Source/license metadata explicitly preserves this limitation. No SRD 5.2 or outside source was substituted.

## Category population

“Eligible” below counts a defined source inventory, not implemented mechanics. A dash means no independent exhaustive category universe is claimed.

| Category | Before | After | Eligible source inventory | Structural extent and limitations |
| --- | ---: | ---: | ---: | --- |
| advancement | 1 | 1 | — | Beyond 1st Level preserved; class tables are now separately structured. No advancement transaction changes. |
| armor | 13 | 13 | 13 | 13/13 printed armor/shield rows preserved. No automatic AC/loadout behavior. |
| classes | 12 | 12 | 12 | 12 chapters; all 240 level/PB rows, printed resources/slots, ASI levels, source proficiencies/equipment and feature links. Some subsidiary choice/resource/dependency tables remain descriptive. |
| combat | 2 | 3 | — | Existing sections retained and enriched, plus one additional source section. More combat reference sections are under rules. |
| conditions | 15 | 15 | 15 | 15/15 printed conditions, including exhaustion. No condition engine. |
| equipment | 133 | 200 | — | Original 133 gear/tool rows plus 67 mount/vehicle/service/pack records. Detailed prose where matched, seven packs with source contents. Not an exhaustive inventory of every priced trade-good/variant or typed special use. |
| feats | 1 | 1 | 1 | Grappler, the one source feat; STR 13 prerequisite retained. |
| features | 47 | 305 | 305 | 305 source feature/subsection headings, including choices and invocations. 108 feature levels remain explicitly unresolved; no default level is invented. Original legacy IDs/levels are retained. |
| magic-items | 3 | 240 | 240 | 240 independent italic type/rarity anchors in pp.207–253, including Orb of Dragonkind. Rarity/attunement metadata; source charge index and restrictions. Variant rarity tables and effect semantics remain descriptive. |
| monsters | 3 | 317 | 317 | 317 independent Armor Class anchors in pp.261–357 and 366–403. Core statistics, defenses and full trait/action text retained; spell references are text mentions requiring review. Only the separate D2 trio can be prepared. |
| movement | 2 | 3 | — | Existing sections retained and enriched, plus one additional source section. More exploration rules are under rules. |
| races | 9 | 9 | 9 | 9 source families, reviewed base ability adjustments, languages/trait text and conditional context. Base traits separated from included subrace prose. |
| rests | 2 | 2 | — | Both Short/Long Rest source sections retained; no automatic recovery. |
| rules | 0 | 305 | — | 305 additional source headings across ability checks, skills, equipment, combat, spellcasting, adjudication, monster rules and appendices. Heading-bounded text, not exhaustive typed rule predicates. |
| spell-lists | 8 | 8 | 8 | 8 printed class lists with reciprocal membership. Domain/patron entitlement tables remain descriptive in class text. |
| spells | 319 | 319 | 319 | 319/319 printed descriptions and original metadata preserved. No spell execution. |
| subclasses | 12 | 12 | 12 | 12 SRD-only subclasses; full source section text and section-bounded child links. Conditional grants and some feature levels still require review. |
| subraces | 4 | 4 | 4 | 4 SRD subraces, parent links and reviewed adjustments. No automatic inherited grant system. |
| weapons | 37 | 37 | 37 | 37/37 printed rows preserved; properties/dice/ranges/cost/weight. No executable weapon changes. |

## Evidence, completeness and unresolved material

`source-inventory-v1.json` preserves the original spell/list, condition, weapon, armor and gear row inventories, extended for the additional equipment records. `source-coverage-v1.json` adds bounded source candidates with page/line spans and source-text hashes, imported record fingerprints, all 623 retained IDs, frozen baseline description/data checks and per-category status/counts. Its `unresolvedRelationships` array names all 108 feature-level ambiguities. Zero stat-block parsing failures does not mean all monster mechanics are structured or executable.

Monster/item eligible totals are based on independent source labels, then compared to imported records. Class progression requires exactly levels 1–20 in order and correct printed PB/slot/resource structures. General headings and gear prose are explicitly bounded scans: the catalog does not claim 100% structured coverage of every table cell, prerequisite, variant, damage formula, lair effect, charge condition or rule predicate in the entire SRD.

The original 14 unmatched legacy summary labels remain in the prior source inventory. New source headings such as Channel Divinity: Turn Undead are indexed by their printed names; no invented automatic alias or executable grant replaces an old character reference.

## Reproduction and validation

With PyMuPDF 1.26.6 and the exact reference PDF, from the repository root:

```sh
python3 tools/build_srd51_reference.py /path/to/SRD-OGL_V5.1.pdf
python3 tools/expand_srd51_reference.py /path/to/SRD-OGL_V5.1.pdf
python3 tools/verify_srd51_source.py /path/to/SRD-OGL_V5.1.pdf
bash ./gradlew --no-daemon --max-workers=4 build runGametest validateSrdCatalog validateSrdCoverage
```

The expansion deliberately requires the baseline 623-entry output first. The verifier runs both importers in an isolated temporary tree and compares all three artifacts byte-for-byte. Minecraft/normal Java builds need neither a PDF parser nor network access.

Validation covers unique IDs/names, required fields/types, source/edition/attribution, complete defined inventories, reciprocal spell lists, all class progression rows, typed references, monster stats, item metadata and source fingerprints. Negative fixtures alter descriptions, omit records, corrupt levels/slots, forge numeric types and remove required metadata; they must fail. Frozen old source data and the unchanged three-monster allowlist protect gameplay compatibility.

See `docs/srd51-reference-completion-checkpoint-j25c2.md` for actual final test counts, artifact checksum and delivery identity. The machine-readable build report is `build/reports/srd51-catalog.json`. Dedicated task reruns are not counted twice as foundation assertions.

Library schema 2, campaign schema 5, SRD combat v1 and D2 stage schema 1 are unchanged. No save migration or character reinterpretation is performed. Graphical Minecraft acceptance remains pending.
