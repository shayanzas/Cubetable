package dev.tacticalcombat.rules.srd;

import java.util.*;
import static dev.tacticalcombat.rules.srd.RulesProvenance.Kind.*;

/** Shared read projection. Existing v1 algorithms are delegated to unchanged rules, not reimplemented. */
public final class CharacterRules {
    public static final int PROJECTION_VERSION=1;
    public static final String RULES_VERSION="srd_5_1_legacy_v1";
    private CharacterRules() {}
    public static DerivedRulesState read(String sheet) {
        var rules=derive(LegacySrdInputs.read(sheet));
        var identity=MonsterProfiles.identity(com.google.gson.JsonParser.parseString(sheet).getAsJsonObject());
        if(identity==null)return rules;
        var profile=MonsterProfiles.get(identity.get("source").getAsString());var sources=new LinkedHashMap<>(rules.provenance());
        sources.put("monster",List.of(source(PERSISTED_INPUT,"collections.srd_monster",profile.id()+" · catalog v1 · "+profile.limitations())));
        sources.put("proficiency",List.of(source(PERSISTED_INPUT,"collections.srd_monster","Reviewed monster proficiency +2; internal effective level is not a player class")));
        sources.put("ac",List.of(source(PERSISTED_INPUT,"collections.srd_monster","Reviewed fixed stat-block AC "+profile.ac()+"; equipment changes are not implemented")));
        return new DerivedRulesState(rules.inputs(),rules.modifiers(),rules.proficiency(),rules.initiativeModifier(),rules.armorClass(),rules.walkingSpeedFeet(),rules.resources(),sources);
    }
    public static DerivedRulesState derive(SrdCharacter c) {
        var modifiers=new EnumMap<SrdCharacter.Ability,Integer>(SrdCharacter.Ability.class);
        for(var ability:SrdCharacter.Ability.values()) modifiers.put(ability,c.modifier(ability));
        var sources=new LinkedHashMap<String,List<RulesProvenance>>();
        sources.put("ac",List.of(
            source(PERSISTED_INPUT,"collections.srd_combat.armor","Typed armor: "+c.armor().name().toLowerCase(Locale.ROOT)+"; inventory ownership is not inferred"),
            source(DERIVED,"SrdEquipment.Armor.ac","Existing "+c.armor().kind.name().toLowerCase(Locale.ROOT)+" armor Dexterity handling"),
            source(PERSISTED_INPUT,"collections.srd_combat.shield","Typed shield contribution: "+(c.shield()?2:0)),
            source(LEGACY_OVERRIDE,"collections.srd_combat.ac_bonus","Legacy typed AC adjustment: "+c.acBonus())));
        sources.put("initiativeModifier",List.of(source(DERIVED,"SrdRules.initiative","Dexterity modifier: "+c.modifier(SrdCharacter.Ability.DEX)),source(LEGACY_OVERRIDE,"collections.srd_combat.initiative_bonus","Legacy initiative adjustment: "+c.initiativeBonus())));
        sources.put("speedFeet",List.of(source(PERSISTED_INPUT,"collections.srd_combat.speed_feet","Typed speed: "+c.speedFeet()+" ft"),source(DERIVED,"SrdCharacter.walkingSpeedFeet","Existing armor Strength penalty; no inferred racial exception")));
        sources.put("proficiency",List.of(source(DERIVED,"collections.srd_combat.level","Proficiency from effective combat level "+c.level()+"; recorded progression does not advance combat")));
        sources.put("abilities",List.of(source(PERSISTED_INPUT,"collections.srd_combat.abilities","Six explicit scores; no automatic race or class bonuses"),source(DERIVED,"SrdRules.abilityModifier","Existing floor((score - 10) / 2) modifiers")));
        sources.put("hp",List.of(source(RUNTIME_RESOURCE,"collections.srd_combat.hp","Current and temporary HP remain separate from approved maximum HP"),source(PERSISTED_INPUT,"collections.srd_combat.hp_max","Typed maximum HP; class Hit Dice are not inferred")));
        sources.put("attacks",List.of(source(PERSISTED_INPUT,"collections.srd_combat.weapons","Typed available weapons and weapon proficiency; not inventory equipped flags"),source(DERIVED,"SrdRules.plan","Existing legal modes, ability choice, dice, hands and armor proficiency consequences"),source(LEGACY_OVERRIDE,"collections.srd_combat.attack_bonus","Legacy attack adjustment: "+c.attackBonus()),source(LEGACY_OVERRIDE,"collections.srd_combat.damage_bonus","Legacy damage adjustment: "+c.damageBonus())));
        sources.put("ammunition",List.of(source(RUNTIME_RESOURCE,"collections.srd_combat","Virtual arrows, bolts and daggers; v1 optional defaults preserved")));
        sources.put("defenses",List.of(source(PERSISTED_INPUT,"collections.srd_combat","Explicit resistances, immunities and vulnerabilities; no feature inference")));
        sources.put("recorded",List.of(source(RECORDED_ONLY,"collections.ui_*","Inventory flags, recorded checks, class/race features and spell records do not execute combat rules"),source(UNKNOWN,"inventory/loadout","Actual equipment ownership and class/race grant provenance are not established by v1 inputs")));
        return new DerivedRulesState(c,modifiers,c.proficiency(),c.modifier(SrdCharacter.Ability.DEX)+c.initiativeBonus(),c.armorClass(),c.walkingSpeedFeet(),
            new DerivedRulesState.Resources(c.hp(),c.maximumHp(),c.temporaryHp(),c.arrows(),c.bolts(),c.daggers()),sources);
    }
    private static RulesProvenance source(RulesProvenance.Kind kind,String path,String explanation) { return new RulesProvenance(kind,path,explanation); }
}
