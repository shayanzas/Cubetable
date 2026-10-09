package dev.tacticalcombat.rules.srd;

import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;
import static dev.tacticalcombat.rules.srd.SrdCharacter.*;

/** Pure SRD 5.1 calculations. Not an action gateway: the encounter must validate/accept before rolling. */
public final class SrdRules {
    private SrdRules() {}
    @FunctionalInterface public interface Dice { int roll(int sides); }
    public enum RollMode { NORMAL, ADVANTAGE, DISADVANTAGE }
    public enum AttackMode { MELEE, RANGED, THROWN }
    public record D20(List<Integer> faces,int chosen,int total) {
        public D20 { faces=List.copyOf(faces); }
    }
    public record Damage(DamageType type,int amount) {
        public Damage { Objects.requireNonNull(type); range(amount,0,100000,"damage"); }
    }
    public record AttackResult(D20 roll,boolean hit,boolean critical,List<Integer> damageFaces,Damage damage) {
        public AttackResult { damageFaces=List.copyOf(damageFaces); }
    }
    /** Target-independent preview: contains no target AC, defenses, HP or hidden statistics. */
    public record AttackPlan(Weapon weapon,AttackMode mode,Ability ability,int attackModifier,
            int dice,int sides,int flatDamage,boolean disadvantage,int reachFeet,int normalRangeFeet,int longRangeFeet) {
        public int minimumDamage() { return Math.max(0,dice+flatDamage); }
        public int maximumDamage() { return Math.max(0,dice*sides+flatDamage); }
    }
    public static int abilityModifier(int score) { return Math.floorDiv(range(score,1,30,"ability")-10,2); }
    public static int proficiency(int level) { return 2+(range(level,1,20,"level")-1)/4; }
    public static RollMode rollMode(boolean advantage,boolean disadvantage) {
        return advantage==disadvantage?RollMode.NORMAL:advantage?RollMode.ADVANTAGE:RollMode.DISADVANTAGE;
    }
    private static int roll(Dice dice,int sides) { return range(dice.roll(sides),1,sides,"die face"); }
    public static D20 d20(Dice dice,int modifier,RollMode mode) {
        range(modifier,-1000,1000,"roll modifier"); Objects.requireNonNull(mode);
        int first=roll(dice,20), chosen=first;
        var faces=new ArrayList<Integer>(); faces.add(first);
        if(mode!=RollMode.NORMAL) {
            int second=roll(dice,20); faces.add(second);
            chosen=mode==RollMode.ADVANTAGE?Math.max(first,second):Math.min(first,second);
        }
        return new D20(faces,chosen,chosen+modifier);
    }
    public static D20 initiative(SrdCharacter character,Dice dice) {
        return d20(dice,character.modifier(Ability.DEX)+character.initiativeBonus(),RollMode.NORMAL);
    }
    /** Equal totals remain equal; callers must collect player/DM adjudication (SRD p.90), not invent a winner. */
    public static List<List<UUID>> initiativeGroups(Map<UUID,Integer> totals) {
        require(totals.size()<=256,"Initiative participant limit");
        var groups=new TreeMap<Integer,List<UUID>>(Comparator.reverseOrder());
        totals.forEach((id,total)->groups.computeIfAbsent(range(total,-1000,1000,"initiative total"),ignored->new ArrayList<>()).add(Objects.requireNonNull(id)));
        return groups.values().stream().map(List::copyOf).toList();
    }
    public static AttackPlan plan(SrdCharacter c,Weapon weapon,AttackMode mode,Ability ability,boolean twoHands) {
        Objects.requireNonNull(weapon); Objects.requireNonNull(mode); Objects.requireNonNull(ability);
        require(c.hp()>0,"Zero-HP SRD actors cannot attack in the early test rules");
        require(weapon==Weapon.UNARMED || c.weapons().contains(weapon),"Weapon is not in approved SRD equipment");
        require(switch(mode) {
            case MELEE -> !weapon.ranged;
            case RANGED -> weapon.ranged;
            case THROWN -> weapon.has(Property.THROWN);
        },"Unsupported attack mode for weapon");
        require(ability==Ability.STR || ability==Ability.DEX,"Weapon ability must be Strength or Dexterity");
        Ability required=weapon.ranged?Ability.DEX:Ability.STR;
        require(weapon.has(Property.FINESSE) || ability==required,"Weapon does not permit that ability");
        require(!weapon.has(Property.TWO_HANDED) || twoHands,"Weapon requires two hands");
        require(!twoHands || !c.shield(),"A shield occupies the second hand");
        require(!twoHands || weapon.has(Property.TWO_HANDED) || weapon.has(Property.VERSATILE),"Unsupported two-handed use");
        int modifier=c.modifier(ability);
        int proficiency=weapon==Weapon.UNARMED || c.weaponProficiencies().contains(weapon)?c.proficiency():0;
        int sides=twoHands && mode==AttackMode.MELEE && weapon.has(Property.VERSATILE)?weapon.versatileSides:weapon.sides;
        // SRD 5.1 says Small; do not substitute the revised 2024 heavy-weapon rules.
        boolean disadvantage=c.armorDisadvantage() || (c.size()==Size.SMALL && weapon.has(Property.HEAVY));
        return new AttackPlan(weapon,mode,ability,modifier+proficiency+c.attackBonus(),weapon.dice,sides,
                weapon.flat+modifier+c.damageBonus(),disadvantage,mode==AttackMode.MELEE?c.reachFeet():0,
                mode==AttackMode.MELEE?0:weapon.normalRangeFeet,mode==AttackMode.MELEE?0:weapon.longRangeFeet);
    }
    /** Caller provides server-owned RNG and already-validated target AC. Misses do not roll damage. */
    public static AttackResult attack(SrdCharacter character,Weapon weapon,AttackMode mode,Ability ability,
            boolean twoHands,int armorClass,boolean advantage,boolean disadvantage,Dice dice) {
        range(armorClass,0,100,"target AC");
        AttackPlan p=plan(character,weapon,mode,ability,twoHands);
        D20 roll=d20(dice,p.attackModifier,rollMode(advantage,disadvantage || p.disadvantage));
        boolean critical=roll.chosen==20;
        boolean hit=critical || (roll.chosen!=1 && roll.total>=armorClass);
        var faces=new ArrayList<Integer>(); int damage=0;
        if(hit) {
            damage=p.flatDamage;
            for(int i=0;i<p.dice*(critical?2:1);i++) { int face=roll(dice,p.sides); faces.add(face); damage+=face; }
        }
        return new AttackResult(roll,hit,critical,faces,new Damage(weapon.damageType,Math.max(0,damage)));
    }
    /** One simultaneous effect; aggregate same-type components before rounding, never stack defenses. */
    public static List<Damage> defend(List<Damage> components,SrdCharacter target) {
        require(components.size()<=64,"Damage component limit");
        var totals=new EnumMap<DamageType,Integer>(DamageType.class);
        for(Damage d:components) totals.merge(d.type,d.amount,Math::addExact);
        var result=new ArrayList<Damage>();
        totals.forEach((type,amount)-> {
            int value=target.immunities().contains(type)?0:amount;
            if(target.resistances().contains(type)) value/=2;
            if(target.vulnerabilities().contains(type)) value*=2;
            result.add(new Damage(type,value));
        });
        return List.copyOf(result);
    }
}
