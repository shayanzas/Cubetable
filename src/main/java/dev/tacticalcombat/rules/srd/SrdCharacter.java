package dev.tacticalcombat.rules.srd;

import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;

/** Explicit version-1 inputs. No derived AC/modifier or executable formula is accepted. */
public record SrdCharacter(Map<Ability,Integer> abilities, int level, int initiativeBonus,
        int hp, int maximumHp, int temporaryHp, int speedFeet, Size size, int reachFeet,
        Armor armor, boolean shield, Set<ArmorKind> armorProficiencies, boolean shieldProficient,
        int acBonus, Set<Weapon> weapons, Set<Weapon> weaponProficiencies, int attackBonus, int damageBonus,
        Set<DamageType> resistances, Set<DamageType> immunities, Set<DamageType> vulnerabilities,
        int arrows,int bolts,int daggers) {
    public enum Ability { STR, DEX, CON, INT, WIS, CHA }
    public enum Size { TINY, SMALL, MEDIUM, LARGE, HUGE, GARGANTUAN }
    public SrdCharacter {
        abilities=Map.copyOf(abilities);
        require(abilities.size()==6,"All six SRD abilities are required");
        for(Ability ability:Ability.values()) range(Objects.requireNonNull(abilities.get(ability)),1,30,"ability "+ability);
        range(level,1,20,"level"); range(initiativeBonus,-100,100,"initiative bonus");
        range(maximumHp,1,1024,"maximum HP"); range(hp,0,maximumHp,"HP"); range(temporaryHp,0,1024,"temporary HP");
        range(speedFeet,0,300,"speed feet"); range(reachFeet,5,30,"reach feet");
        require(reachFeet%5==0,"Reach must be a multiple of five feet");
        Objects.requireNonNull(size); Objects.requireNonNull(armor);
        range(acBonus,-20,20,"AC bonus"); range(attackBonus,-100,100,"attack bonus"); range(damageBonus,-100,100,"damage bonus");
        armorProficiencies=Set.copyOf(armorProficiencies); weapons=Set.copyOf(weapons);
        weaponProficiencies=Set.copyOf(weaponProficiencies);
        resistances=Set.copyOf(resistances); immunities=Set.copyOf(immunities); vulnerabilities=Set.copyOf(vulnerabilities);
        range(arrows,0,1024,"arrows"); range(bolts,0,1024,"bolts"); range(daggers,0,1024,"daggers");
        range(armor.ac(Math.floorDiv(abilities.get(Ability.DEX)-10,2))+(shield?2:0)+acBonus,0,100,"derived AC");
    }
    public int modifier(Ability ability) { return SrdRules.abilityModifier(abilities.get(ability)); }
    public int proficiency() { return SrdRules.proficiency(level); }
    public int armorClass() { return armor.ac(modifier(Ability.DEX))+(shield?2:0)+acBonus; }
    public boolean armorDisadvantage() {
        return (armor!=Armor.NONE && !armorProficiencies.contains(armor.kind)) || (shield && !shieldProficient);
    }
    /** No racial exception is inferred; e.g. dwarven heavy-armor movement awaits feature support. */
    public int walkingSpeedFeet() { return Math.max(0,speedFeet-(abilities.get(Ability.STR)<armor.strength?10:0)); }
    public int walkingSquares() { return walkingSpeedFeet()/5; }
    static void require(boolean condition,String message) { if(!condition) throw new IllegalArgumentException(message); }
    static int range(int value,int min,int max,String label) {
        require(value>=min && value<=max,"Invalid SRD "+label+": expected "+min+".."+max); return value;
    }
}
