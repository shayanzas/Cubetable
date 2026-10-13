package dev.tacticalcombat.client.ui;

import java.util.*;

/** Original, limited SRD 5.1 summaries. These descriptions never grant mechanics. */
public final class CreatorLore {
    private CreatorLore() {}
    public static String classDetails(Srd2014Catalog.Choice c) {
        String description=switch(c.id()) {
            case "barbarian"->"A fierce warrior who channels primal fury.\nArmor: light, medium, shields. Weapons: simple and martial.\nLevel 1: Rage, Unarmored Defense. Level 2: Reckless Attack, Danger Sense. Level 3: Primal Path (Berserker in SRD).";
            case "bard"->"A versatile performer whose magic is carried by music and words.\nArmor: light. Weapons: simple, hand crossbows, longswords, rapiers, shortswords. Tools: three musical instruments.\nLevel 1: Spellcasting, Bardic Inspiration. Level 2: Jack of All Trades, Song of Rest. Level 3: College (Lore), Expertise.";
            case "cleric"->"A divine spellcaster whose faith shapes their calling.\nArmor: light, medium, shields. Weapons: simple.\nLevel 1: Spellcasting, Divine Domain (Life). Level 2: Channel Divinity. Domain benefits require manual review.";
            case "druid"->"A guardian of nature who draws on its magic and changing forms.\nArmor: light, medium, shields (druids will not wear metal armor or shields). Weapons: clubs, daggers, darts, javelins, maces, quarterstaffs, scimitars, sickles, slings, spears. Tools: herbalism kit.\nLevel 1: Druidic, Spellcasting. Level 2: Wild Shape, Circle (Land).";
            case "fighter"->"A disciplined master of weapons and battlefield technique.\nArmor: all armor, shields. Weapons: simple and martial.\nLevel 1: Fighting Style, Second Wind. Level 2: Action Surge. Level 3: Archetype (Champion). Style and features are recorded manually.";
            case "monk"->"A martial artist who cultivates discipline, speed and inner energy.\nArmor: none. Weapons: simple, shortswords. Tools: one artisan tool or musical instrument.\nLevel 1: Unarmored Defense, Martial Arts. Level 2: Ki, Unarmored Movement. Level 3: Tradition (Open Hand), Deflect Missiles.";
            case "paladin"->"A sworn champion combining martial skill with sacred power.\nArmor: all armor, shields. Weapons: simple and martial.\nLevel 1: Divine Sense, Lay on Hands. Level 2: Fighting Style, Spellcasting, Divine Smite. Level 3: Sacred Oath (Devotion), Divine Health.";
            case "ranger"->"A skilled tracker and wilderness defender.\nArmor: light, medium, shields. Weapons: simple and martial.\nLevel 1: Favored Enemy, Natural Explorer. Level 2: Fighting Style, Spellcasting. Level 3: Archetype (Hunter), Primeval Awareness.";
            case "rogue"->"An agile specialist who relies on skill, precision and opportunity.\nArmor: light. Weapons: simple, hand crossbows, longswords, rapiers, shortswords. Tools: thieves' tools.\nLevel 1: Expertise, Sneak Attack, Thieves' Cant. Level 2: Cunning Action. Level 3: Archetype (Thief).";
            case "sorcerer"->"A spellcaster whose magic comes from an innate gift or bloodline.\nArmor: none. Weapons: daggers, darts, slings, quarterstaffs, light crossbows.\nLevel 1: Spellcasting, Origin (Draconic Bloodline). Level 2: Font of Magic. Level 3: Metamagic.";
            case "warlock"->"A seeker of occult knowledge empowered by an otherworldly pact.\nArmor: light. Weapons: simple.\nLevel 1: Patron (Fiend), Pact Magic. Level 2: Eldritch Invocations. Level 3: Pact Boon.";
            case "wizard"->"A scholarly spellcaster who studies and records arcane magic.\nArmor: none. Weapons: daggers, darts, slings, quarterstaffs, light crossbows.\nLevel 1: Spellcasting, Arcane Recovery. Level 2: Tradition (Evocation). Spellbook and progression remain manual.";
            default->"Record your own class and discuss its rules with your DM.";
        };
        return c.name()+"\n\n"+description+"\n\n"+c.summary()+"\n"+c.guidance()+"\nChoose "+Srd2014Catalog.classSkillCount(c.name())+" class skills in Proficiencies.\n\nSRD 2014 reference. Benefits are not applied automatically. This is a level 1–3 introduction, not the complete progression table.";
    }
    public static String details(CreatorFlow.Step step,Srd2014Catalog.Choice c) {
        if(step==CreatorFlow.Step.CLASS)return classDetails(c);
        String traits=step==CreatorFlow.Step.IDENTITY?switch(c.id()) {
            case "human"->"Adaptable people with diverse cultures and ambitions. SRD traits: +1 to all six abilities; Medium size; Common and one extra language.";
            case "elf"->"Graceful, long-lived folk with keen senses. SRD traits: Dexterity +2; Perception proficiency; Darkvision; Fey Ancestry; Trance. High Elf: Intelligence +1, weapon training, one wizard cantrip and one extra language.";
            case "dwarf"->"Resilient folk known for tradition and skilled craft. SRD traits: Constitution +2; Darkvision; resistance to poison damage and advantage on poison saves; combat/tool training; Stonecunning. Hill Dwarf: Wisdom +1 and Dwarven Toughness.";
            case "halfling"->"Small, resourceful people who favor home, friendship and adventure. SRD traits: Dexterity +2; Small size; Lucky; Brave; Halfling Nimbleness. Lightfoot: Charisma +1 and Naturally Stealthy.";
            case "dragonborn"->"Proud draconic folk marked by their ancestry. SRD traits: Strength +2, Charisma +1; Medium size; choose draconic ancestry; breath weapon and matching resistance. Ancestry/effects require manual records.";
            case "gnome"->"Curious small folk with a gift for invention and discovery. SRD traits: Intelligence +2; Small size; Darkvision; Gnome Cunning. Rock Gnome: Constitution +1, Artificer's Lore and Tinker.";
            case "half_elf"->"People who bridge elven and human communities. SRD traits: Charisma +2 and +1 to two other abilities; Medium size; Darkvision; Fey Ancestry; two skill proficiencies; Common, Elvish and one extra language.";
            case "half_orc"->"Powerful folk whose stories span diverse traditions. SRD traits: Strength +2, Constitution +1; Medium size; Darkvision; Intimidation proficiency; Relentless Endurance; Savage Attacks.";
            case "tiefling"->"People whose infernal heritage is visible in their appearance. SRD traits: Intelligence +1, Charisma +2; Medium size; Darkvision; fire resistance; Infernal Legacy. Its spells remain manual.";
            default->"Custom race: agree on traits with your DM.";
        }:"";
        return c.name()+"\n\n"+c.summary()+"\n\n"+traits+"\n\n"+c.guidance()+"\n\nSRD 2014 reference. Record benefits with your DM. Choosing this entry does not change combat statistics or apply ability increases.";
    }
}
