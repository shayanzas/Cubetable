package dev.tacticalcombat.client;
import dev.tacticalcombat.rules.srd.*;
import java.util.*;
/** Supported selection shapes only. Server preview still validates stats, stock, geometry and permissions. */
public final class SrdAttackChoices {
    public record Choice(SrdEquipment.Weapon weapon,SrdRules.AttackMode mode,SrdCharacter.Ability ability,boolean twoHands) {}
    private SrdAttackChoices() {}
    public static List<Choice> forWeapon(String name,String monsterAbility){
        var w=SrdEquipment.Weapon.valueOf(name);var out=new ArrayList<Choice>();
        var modes=w.ranged?List.of(SrdRules.AttackMode.RANGED):w.has(SrdEquipment.Property.THROWN)?List.of(SrdRules.AttackMode.MELEE,SrdRules.AttackMode.THROWN):List.of(SrdRules.AttackMode.MELEE);
        var abilities=monsterAbility!=null?List.of(SrdCharacter.Ability.valueOf(monsterAbility)):w.ranged?List.of(SrdCharacter.Ability.DEX):w.has(SrdEquipment.Property.FINESSE)?List.of(SrdCharacter.Ability.STR,SrdCharacter.Ability.DEX):List.of(SrdCharacter.Ability.STR);
        for(var mode:modes)for(var ability:abilities){out.add(new Choice(w,mode,ability,w.has(SrdEquipment.Property.TWO_HANDED)));if(w.has(SrdEquipment.Property.VERSATILE))out.add(new Choice(w,mode,ability,true));}
        return List.copyOf(out);
    }
}
