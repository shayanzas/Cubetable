package dev.tacticalcombat.client.ui;

import java.util.*;

/** Original, limited SRD 5.1 summaries. These descriptions never grant mechanics. */
public final class CreatorLore {
    private CreatorLore() {}
    public static String classDetails(Srd2014Catalog.Choice c) {
        String features=FeatureReference.classFeatures(c.id()).stream().map(e->"Level "+e.level()+": "+e.name()+" — "+e.description()).reduce((a,b)->a+"\n"+b).orElse("Custom class; discuss rules with your DM.");
        return c.name()+"\n\n"+FeatureReference.classProficiencies(c.id())+"\n"+features+"\n\n"+c.summary()+"\n"+c.guidance()+"\nChoose "+Srd2014Catalog.classSkillCount(c.name())+" class skills in Proficiencies.\n\nSRD 2014 reference. Benefits are not applied automatically. Level 1–3 introduction only.";
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
