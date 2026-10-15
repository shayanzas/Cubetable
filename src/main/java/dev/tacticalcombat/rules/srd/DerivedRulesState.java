package dev.tacticalcombat.rules.srd;

import java.util.*;
import static dev.tacticalcombat.rules.srd.SrdCharacter.Ability;
import static dev.tacticalcombat.rules.srd.SrdEquipment.*;

/** Immutable projection of explicit v1 inputs. No identity, approval or permission is cached here. */
public record DerivedRulesState(SrdCharacter inputs, Map<Ability,Integer> modifiers,
        int proficiency, int initiativeModifier, int armorClass, int walkingSpeedFeet,
        Resources resources, Map<String,List<RulesProvenance>> provenance) {
    public record Resources(int hp,int maximumHp,int temporaryHp,int arrows,int bolts,int daggers) {}
    public DerivedRulesState {
        Objects.requireNonNull(inputs); modifiers=Map.copyOf(modifiers); Objects.requireNonNull(resources);
        var copy=new LinkedHashMap<String,List<RulesProvenance>>();
        provenance.forEach((key,value)->copy.put(key,List.copyOf(value))); provenance=Map.copyOf(copy);
    }
    public SrdRules.AttackPlan plan(Weapon weapon,SrdRules.AttackMode mode,Ability ability,boolean twoHands) {
        return SrdRules.plan(inputs,weapon,mode,ability,twoHands);
    }
    public SrdRules.D20 initiative(SrdRules.Dice dice) { return SrdRules.initiative(inputs,dice); }
    public SrdRules.AttackResult attack(Weapon weapon,SrdRules.AttackMode mode,Ability ability,boolean twoHands,
            int targetAc,boolean advantage,boolean disadvantage,SrdRules.Dice dice) {
        return SrdRules.attack(inputs,weapon,mode,ability,twoHands,targetAc,advantage,disadvantage,dice);
    }
}
