package dev.tacticalcombat.rules.srd;

import java.util.List;

/** SRD 5.1 pp.97–99 resource arithmetic; zero HP is an adjudication boundary, not vanilla death. */
public record SrdHealth(int current,int maximum,int temporary) {
    public SrdHealth {
        if(maximum<1 || maximum>1024 || current<0 || current>maximum || temporary<0 || temporary>1024)
            throw new IllegalArgumentException("Invalid SRD health state");
    }
    public static SrdHealth of(SrdCharacter character) { return new SrdHealth(character.hp(),character.maximumHp(),character.temporaryHp()); }
    public boolean zeroHp() { return current==0; }
    public SrdHealth damage(List<SrdRules.Damage> components,SrdCharacter defenses) {
        int total=SrdRules.defend(components,defenses).stream().mapToInt(SrdRules.Damage::amount).sum();
        int absorbed=Math.min(temporary,total);
        return new SrdHealth(Math.max(0,current-(total-absorbed)),maximum,temporary-absorbed);
    }
    public SrdHealth heal(int amount) {
        bounded(amount); return new SrdHealth(Math.min(maximum,current+amount),maximum,temporary);
    }
    /** Explicit owner/DM choice by the caller: keep existing or replace; never add the amounts. */
    public SrdHealth temporary(int amount,boolean replace) {
        bounded(amount); return replace?new SrdHealth(current,maximum,amount):this;
    }
    private static void bounded(int amount) { if(amount<0 || amount>1024) throw new IllegalArgumentException("SRD resource amount outside 0..1024"); }
}
