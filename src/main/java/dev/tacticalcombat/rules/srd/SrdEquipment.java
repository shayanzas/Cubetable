package dev.tacticalcombat.rules.srd;

import java.util.*;

/** SRD 5.1 pp. 63–66. Catalog identities are rules data, never Minecraft item guesses. */
public final class SrdEquipment {
    private SrdEquipment() {}
    public enum DamageType { ACID, BLUDGEONING, COLD, FIRE, FORCE, LIGHTNING, NECROTIC,
        PIERCING, POISON, PSYCHIC, RADIANT, SLASHING, THUNDER }
    public enum Property { FINESSE, LIGHT, HEAVY, TWO_HANDED, VERSATILE, AMMUNITION, LOADING, THROWN }
    public enum ArmorKind { NONE, LIGHT, MEDIUM, HEAVY }
    public enum Armor {
        NONE(ArmorKind.NONE,10,0), LEATHER(ArmorKind.LIGHT,11,0),
        STUDDED_LEATHER(ArmorKind.LIGHT,12,0), CHAIN_SHIRT(ArmorKind.MEDIUM,13,0),
        SCALE_MAIL(ArmorKind.MEDIUM,14,0), CHAIN_MAIL(ArmorKind.HEAVY,16,13), PLATE(ArmorKind.HEAVY,18,15);
        public final ArmorKind kind;
        public final int baseAc, strength;
        Armor(ArmorKind kind,int baseAc,int strength) { this.kind=kind; this.baseAc=baseAc; this.strength=strength; }
        public int ac(int dexterityModifier) {
            return baseAc + switch(kind) {
                case NONE,LIGHT -> dexterityModifier;
                case MEDIUM -> Math.min(2,dexterityModifier);
                case HEAVY -> 0;
            };
        }
    }
    public enum Weapon {
        UNARMED(0,0,1,DamageType.BLUDGEONING,false,0,0,0),
        DAGGER(1,4,0,DamageType.PIERCING,false,20,60,0,Property.FINESSE,Property.LIGHT,Property.THROWN),
        LONGSWORD(1,8,0,DamageType.SLASHING,false,0,0,10,Property.VERSATILE),
        SHORTSWORD(1,6,0,DamageType.PIERCING,false,0,0,0,Property.FINESSE,Property.LIGHT),
        GREATSWORD(2,6,0,DamageType.SLASHING,false,0,0,0,Property.HEAVY,Property.TWO_HANDED),
        SHORTBOW(1,6,0,DamageType.PIERCING,true,80,320,0,Property.AMMUNITION,Property.TWO_HANDED),
        LONGBOW(1,8,0,DamageType.PIERCING,true,150,600,0,Property.AMMUNITION,Property.HEAVY,Property.TWO_HANDED),
        LIGHT_CROSSBOW(1,8,0,DamageType.PIERCING,true,80,320,0,Property.AMMUNITION,Property.LOADING,Property.TWO_HANDED),
        SLAM(1,6,0,DamageType.BLUDGEONING,false,0,0,0),
        SCIMITAR(1,6,0,DamageType.SLASHING,false,0,0,0,Property.FINESSE,Property.LIGHT);
        public final int dice, sides, flat, normalRangeFeet, longRangeFeet, versatileSides;
        public final DamageType damageType;
        public final boolean ranged;
        public final Set<Property> properties;
        Weapon(int dice,int sides,int flat,DamageType type,boolean ranged,int normal,int longer,int versatile,Property... properties) {
            this.dice=dice; this.sides=sides; this.flat=flat; damageType=type; this.ranged=ranged;
            normalRangeFeet=normal; longRangeFeet=longer; versatileSides=versatile; this.properties=Set.of(properties);
        }
        public boolean has(Property property) { return properties.contains(property); }
    }
}
