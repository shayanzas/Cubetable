# SRD 5.1 reference catalog coverage — J2.5

This is a **source-verified batched catalog**, not a claim that the entire SRD has been fully structured or implemented. There are **623 entries across 18 categories**. Every entry is read-only reference data; presence does not grant traits, cast spells, equip items, execute monster actions or change saved characters.

## Source and elected license

Rules reference: user-uploaded `SRD-OGL_V5.1.pdf`, 403 pages, System Reference Document 5.1 (2014 rules). SHA-256: `d3f94417d2532f42a5abaec07e71a59007bf6cc46992c6458be6667f7a9f1e34`. PDF page numbers in entries refer to that supplied edition, not shifted page numbers in another layout. OGL legal pages 1–2 and running legal/footer text are excluded from distributed catalog content. The uploaded PDF itself has not been relabeled.

Distribution elects **Creative Commons Attribution 4.0 International (CC BY 4.0)** following the user's supplied confirmation that Wizards makes the full SRD 5.1 available under either license. Attribution:

> This work includes material taken from the System Reference Document 5.1 (SRD 5.1) by Wizards of the Coast LLC and available at https://www.dndbeyond.com/srd. The SRD 5.1 is licensed under the Creative Commons Attribution 4.0 International License available at https://creativecommons.org/licenses/by/4.0/legalcode.

Changes: typography/hyphens and whitespace normalized, structured metadata and stable IDs added. No endorsement is implied. Attribution is also packaged in `SRD-5.1-NOTICE.txt`, catalog source metadata and the reference interface.

Verification attempts: supplied D&D Beyond PDF URL, Wizards media PDF, `https://www.dndbeyond.com/srd`, and the supplied Wikimedia archive page returned HTTP 403 in this environment; the official PDF was also retried through approved escalation. Independent CC-edition text comparison remains pending. The source/license record explicitly preserves that limitation. No 5.2 content, OGL license text or external non-SRD text was imported.

- Official dual-license page: https://www.dndbeyond.com/srd
- Official CC edition: https://media.dndbeyond.com/compendium-images/srd/5.1/SRD_CC_v5.1.pdf
- Wizards edition URL: https://media.wizards.com/2023/downloads/dnd/SRD_CC_v5.1.pdf
- User-supplied archive: https://commons.wikimedia.org/wiki/File:SRD5.1-CCBY4.0License.pdf

## Category coverage

| Category | Entries | Verified extent and remaining work |
| --- | ---: | --- |
| advancement | 1 | Beyond 1st Level source section, including the printed advancement table as text. Structured per-class progression/HP/choice transactions remain deferred. |
| armor | 13 | 12 armor rows plus shield (13/13), including AC category/formula, Strength, stealth, price/weight and don/doff metadata. No auto-equipping or AC changes. |
| classes | 12 | All 12 class identities and Hit Dice. Full progression, choice/slot/grant tables and executable class entitlements are NOT complete. |
| combat | 2 | Two source sections: Actions in Combat and Attack Rolls. Not a complete structured combat rules index. |
| conditions | 15 | 15/15 condition headings across source pages 358–359, including exhaustion. Descriptive; no new condition engine. |
| equipment | 133 | 133 price/weight rows extracted from the printed adventuring-gear and tools tables on pages 69–70. Special uses, pack contents, mounts, services and vehicles are not a complete structured catalog. |
| feats | 1 | Grappler, the SRD feat, with STR 13 prerequisite and source text. No feat execution. |
| features | 47 | 47 legacy feature names matched to exact headings inside their source class chapter; source text and existing minimum levels/legacy IDs retained. 14 unmatched summary labels listed below. Full level-1–20 progression and prerequisites remain incomplete. |
| magic-items | 3 | Three source sections: Bag of Holding, Potion of Healing, Wand of Magic Missiles. Other magic items and fully structured item effects remain uncovered. |
| monsters | 3 | Three complete basic stat blocks: Goblin, Skeleton, Zombie, including ability scores, defenses, senses/languages, traits/actions and spellcasting absence. Remaining SRD monsters are not populated; no monster adapter. |
| movement | 2 | Two source sections: Movement and combat Difficult Terrain. Not a complete movement/reference rules index or new movement mechanics. |
| races | 9 | Nine source race-family descriptions with size/speed and trait text. No automatic ASIs, traits or grants; individual trait predicates are not fully structured. |
| rests | 2 | Short Rest and Long Rest source sections. No automatic resource recovery. |
| spell-lists | 8 | All eight printed class lists cross-checked against imported spell identities. Domain/patron expanded lists and class preparation rules are not entitlements. |
| spells | 319 | 319/319 printed description headings, required metadata and reciprocal membership in the printed spell lists. Full text/scaling retained; not castable. |
| subclasses | 12 | 12 authorized SRD subclass opening sections, class links and minimum levels. Subsequent feature details are separately indexed where matched, not a complete subclass entitlement model. |
| subraces | 4 | Four source subrace descriptions (Hill Dwarf, High Elf, Lightfoot, Rock Gnome), linked to parent races. No automatic grants. |
| weapons | 37 | 37/37 rows in the printed weapon table, with damage types/dice, properties, ranges, versatility, price and weight. Existing executable weapon subset is unchanged. |

Only the spell headings/class lists, condition headings, weapon table and armor table have a full independent expected-count gate in this delivery. Do not extrapolate those complete table checks to all magic items, monsters, class progression or the whole SRD. Population of the remaining material should continue in source-checked batches; no unknown entries are invented.

## Specific unmatched legacy feature summaries

These existing summary labels did not exactly match a source heading in their class chapter, so they were not imported as newly verified feature entries. Existing character data/reference summaries are retained. Several correspond to separately imported subclass entries; that does not establish an executable grant or alias.

- barbarian: Primal Path · Berserker
- fighter: Martial Archetype · Champion
- bard: Bard College · Lore
- cleric: Divine Domain · Life
- cleric: Turn Undead
- cleric: Preserve Life
- druid: Druid Circle · Land
- monk: Monastic Tradition · Open Hand
- paladin: Sacred Oath · Devotion
- ranger: Ranger Archetype · Hunter
- rogue: Roguish Archetype · Thief
- sorcerer: Sorcerous Origin · Draconic Bloodline
- warlock: Otherworldly Patron · Fiend
- wizard: Arcane Tradition · Evocation

All newly imported entries have `PDF_TEXT_VERIFIED` provenance against the supplied PDF; independent CC-edition comparison is pending at the source level. The unmatched summaries above and incomplete categories are explicitly distinguished from imported verified text.

## Format, generation and validation

Versioned mod resources: `data/tacticalcombat/reference/catalog-v1.json` and `source-inventory-v1.json`. IDs are stable source-qualified category/name slugs. Category metadata and related IDs are validated by `SrdReferenceCatalog`; no reference corpus is copied wholesale into characters. Library schema 2, campaign schema 5 and SRD combat v1 remain unchanged.

The maintainer script `tools/build_srd51_reference.py <uploaded-SRD-5.1.pdf>` requires PyMuPDF and reproduces these batches from the exact source hash. Minecraft and normal Gradle builds require neither a PDF parser nor network access. Review any generated changes against the source; do not rerun against an arbitrary or 5.2 document as if it were this manifest.

Run `bash ./gradlew --no-daemon --max-workers=4 validateSrdCatalog`. It checks packaged JSON, version/source/attribution, unique IDs/names, fields/types, references, levels/prerequisites, damage/property types, full inventoried spell/condition/weapon/armor names, the extracted equipment inventory and both directions of printed spell-list membership. Negative fixtures verify malformed data is rejected.

Actual result: **2,614 catalog checks passed**, **623 entries validated**. Machine-readable report: `build/reports/srd51-catalog.json`. The same catalog suite is retained in foundation tests; the dedicated Gradle check runs it separately and must not be double-counted as additional foundation assertions.
