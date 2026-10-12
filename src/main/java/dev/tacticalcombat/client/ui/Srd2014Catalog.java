package dev.tacticalcombat.client.ui;

import java.util.*;

/** Compact verified choice labels from SRD 5.1 (2014). Text is original summary; all effects remain informational. */
public final class Srd2014Catalog {
    public record Choice(String id,String name,String summary,String guidance) {}
    public static final List<Choice> SPECIES=List.of(
        c("human","Human","Versatile folk; base walking speed 30 feet.","SRD 2014 ability implications are guidance only in this wizard."),
        c("elf","Elf","Darkvision, keen senses, Fey Ancestry and trance; speed 30 feet.","Choose High Elf subrace in the identity editor if desired; no bonus is auto-applied."),
        c("dwarf","Dwarf","Darkvision, resilience and stonecunning; speed 25 feet.","Hill and Mountain dwarf records are informational; armor/stat grants are not automated."),
        c("halfling","Halfling","Lucky, brave and nimble; speed 25 feet.","Lightfoot subrace can be recorded; traits are not automated."),
        c("dragonborn","Dragonborn","Draconic ancestry, breath weapon and damage resistance; speed 30 feet.","Breath and resistance automation is unavailable."),
        c("gnome","Gnome","Darkvision and Gnome Cunning; speed 25 feet.","Rock Gnome subrace can be recorded; traits are informational."),
        c("half_elf","Half-Elf","Darkvision, Fey Ancestry and skill versatility; speed 30 feet.","Choices/bonuses require manual recording without double application."),
        c("half_orc","Half-Orc","Darkvision, menacing, Relentless Endurance and Savage Attacks; speed 30 feet.","Feature automation is unavailable."),
        c("tiefling","Tiefling","Darkvision, fire resistance and infernal legacy; speed 30 feet.","Spells and resistance are not automatically granted."));
    public static final List<Choice> CLASSES=List.of(
        cls("barbarian","Barbarian","d12 · Strength","Strength, Constitution saves · Path at level 3"),cls("bard","Bard","d8 · Charisma","Dexterity, Charisma saves · College at level 3"),
        cls("cleric","Cleric","d8 · Wisdom","Wisdom, Charisma saves · Domain at level 1"),cls("druid","Druid","d8 · Wisdom","Intelligence, Wisdom saves · Circle at level 2"),
        cls("fighter","Fighter","d10 · Strength or Dexterity","Strength, Constitution saves · Archetype at level 3"),cls("monk","Monk","d8 · Dexterity and Wisdom","Strength, Dexterity saves · Tradition at level 3"),
        cls("paladin","Paladin","d10 · Strength and Charisma","Wisdom, Charisma saves · Oath at level 3"),cls("ranger","Ranger","d10 · Dexterity and Wisdom","Strength, Dexterity saves · Archetype at level 3"),
        cls("rogue","Rogue","d8 · Dexterity","Dexterity, Intelligence saves · Archetype at level 3"),cls("sorcerer","Sorcerer","d6 · Charisma","Constitution, Charisma saves · Origin at level 1"),
        cls("warlock","Warlock","d8 · Charisma","Wisdom, Charisma saves · Patron at level 1"),cls("wizard","Wizard","d6 · Intelligence","Intelligence, Wisdom saves · Tradition at level 2"));
    public static final List<Choice> BACKGROUNDS=List.of(c("acolyte","Acolyte","Insight and Religion; two languages; shelter feature.","The SRD's worked background. Equipment/personality suggestions remain manual records."));
    private Srd2014Catalog() {}
    private static Choice c(String id,String name,String summary,String guidance) {return new Choice(id,name,summary,guidance);}
    private static Choice cls(String id,String name,String summary,String guidance) {return c(id,capitalize(name),summary,guidance);}
    private static String capitalize(String s) {return Character.toUpperCase(s.charAt(0))+s.substring(1);}
    public static List<Choice> forStage(WizardModel.Stage stage) {return switch(stage) {case SPECIES->SPECIES;case CLASS->CLASSES;case BACKGROUND->BACKGROUNDS;default->List.of();};}
}
