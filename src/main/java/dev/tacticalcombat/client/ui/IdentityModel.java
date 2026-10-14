package dev.tacticalcombat.client.ui;

import java.util.*;

/** Identity choices change only local intent; canonical data advances on server acknowledgement. */
public final class IdentityModel {
    public static final List<String> ALIGNMENTS=List.of("Lawful Good","Neutral Good","Chaotic Good","Lawful Neutral","True Neutral","Chaotic Neutral","Lawful Evil","Neutral Evil","Chaotic Evil");
    private static final Map<String,String> INTRO=Map.of(
        "Human","Humans build varied communities and follow many ambitions. Their adaptability lets them pursue almost any calling.",
        "Elf","Long-lived and keen-eyed, elves value craft, learning and their ties to the natural world.",
        "Dwarf","Dwarves are resilient folk with traditions of skilled craft, strong communities and enduring determination.",
        "Halfling","Small and resourceful, halflings meet a large world with courage, good fortune and a love of home.",
        "Dragonborn","Dragonborn carry a visible draconic heritage. Their ancestry shapes their breath weapon and resistance.",
        "Gnome","Curious and inventive, gnomes delight in discovery, clever ideas and the details others overlook.",
        "Half-Elf","Half-elves share human and elven ancestry, bringing varied experiences and talents to their communities.",
        "Half-Orc","Half-orcs carry human and orc ancestry. Strength and determination help them shape their own paths.",
        "Tiefling","Tieflings bear an infernal heritage that grants distinctive traits. Their choices and ambitions remain their own.");
    private IdentityModel() {}
    public static String intro(String race){return INTRO.getOrDefault(race,"A personal race choice. Keep its saved description and discuss its traits with your DM.");}
    public static String subraceIntro(String subrace){return switch(subrace){case "High Elf"->"High elves study lore and magic. Extra weapon training, a wizard cantrip and another language are recorded traits.";case "Hill Dwarf"->"Hill dwarves pair keen senses with enduring resilience. Their Wisdom increase and Dwarven Toughness remain explicit manual adjustments.";case "Lightfoot Halfling"->"Lightfoots move easily among larger folk. Their Charisma increase and Naturally Stealthy trait are recorded guidance.";case "Rock Gnome"->"Rock gnomes excel in invention and craft. Their Constitution increase, Artificer's Lore and Tinker traits remain manual.";default->"";};}
    public static String subrace(String recorded){String base=CreatorFlow.baseRace(recorded);if(recorded.startsWith(base+" — "))return recorded.substring(base.length()+3);return CreatorFlow.subraces(base).contains(recorded)?recorded:"";}
    public static void race(CreatorDraftState state,String race){if(Srd2014Catalog.SPECIES.stream().noneMatch(c->c.name().equals(race)))throw new IllegalArgumentException("Choose a supported race");state.fields.put("species",race);}
    public static void subrace(CreatorDraftState state,String subrace){state.fields.put("species",CreatorFlow.recordedRace(CreatorFlow.baseRace(state.fields.get("species")),subrace));}
    public static void alignment(CreatorDraftState state,String value){if(!ALIGNMENTS.contains(value))throw new IllegalArgumentException("Choose a conventional alignment");state.fields.put("alignment",value);}
    public static String status(CreatorDraftState state){return state.fields.get("species").equals(CreatorDraftState.savedField(state.snapshot(),"species"))?"Saved selection":"Unsaved preview · Save or Continue to keep";}
    public static String completeness(String recorded){String race=CreatorFlow.baseRace(recorded);if(!CreatorFlow.subraces(race).isEmpty()&&subrace(recorded).isBlank())return "Base traits only · select a supported subrace for new guided creation; existing records remain preserved";if(!CreatorFlow.subraces(race).isEmpty()&&!CreatorFlow.subraces(race).contains(subrace(recorded)))return "Existing custom subrace · keep as a manual choice";return "Traits are guidance; no combat bonuses are added automatically";}
}
