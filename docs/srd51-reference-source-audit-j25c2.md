# J2.5C2 source and compatibility audit

Base: `59b75ce2dc9842452db7eb2215a5b2fefbc758e2`.

The existing catalog has 623 stable IDs in 18 categories: 319 spells, 133 gear/tool price rows, 47 feature summaries/descriptions, 37 weapons, 15 conditions, 13 armor/shield records, 12 classes, 12 subclass openings, 9 races, 8 spell lists, 4 subraces, 3 monsters, 3 magic items, and 7 combat/movement/rest/advancement sections plus Grappler. The spell/class-list, condition, weapon and armor source inventories already have full table/heading comparisons. Other categories are bounded batches, not full coverage.

The newly supplied 403-page OGL-layout PDF matches the previous exact source SHA-256 `d3f94417d2532f42a5abaec07e71a59007bf6cc46992c6458be6667f7a9f1e34`. Pages 1–2 and running legal/footer matter remain excluded. The official CC URL still returns HTTP 403, including an escalated retry; the alternative Wizards URL also returns 403. Independent comparison against the CC edition is therefore unresolved. Distribution retains the existing elected CC BY 4.0 attribution without relabeling the uploaded PDF.

An independent geometric scan finds 317 Armor Class labels in monster chapters and appendices, and 240 italic item type/rarity headings in the magic-item list/artifact section. These provide candidate inventories, subject to boundary and field verification. Class progression needs all 20 rows for each class, including split columns, wrapped feature labels, spell slots and class-specific resources. Existing classes currently contain only identity/Hit Die metadata. Subclass features, invocation prerequisites and race/subrace trait boundaries require explicit reference metadata; unresolved relationships must remain flagged.

Equipment currently lacks full descriptions and mount/vehicle/service rows; magic items currently lack complete type/rarity/attunement records. General ability, exploration, spellcasting, equipment and adjudication sections are mostly absent. Existing unmatched legacy feature labels must not be renamed or treated as new executable grants.

Compatibility boundaries: preserve every existing ID; preserve all D2 Zombie/Skeleton/Goblin fields used by MonsterProfiles; preserve original spell/list metadata; keep all imports read-only; do not alter SrdAuthoring, CreationRules, MonsterProfiles, combat, stage placement, dice or persistence. Static additions use catalog schema 1 with additive reference metadata and a separately versioned source-coverage manifest. No world migration is required.
