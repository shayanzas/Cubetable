package dev.tacticalcombat.rules;

import java.util.Map;

/** Explicit, versioned built-ins. A future pack loader must validate policies, never run arbitrary formulas. */
public final class RulesProfiles {
    private RulesProfiles() {}
    public static final String TACTICAL="minecraft_tactical", MANUAL="manual_tabletop", SRD="srd_5_1";
    private record Profile(String id, boolean worldCombat) implements RulesProfile {
        public int version() { return 1; }
        public double movement(Map<String,Double> stats, boolean player) {
            return worldCombat ? stats.getOrDefault("movement_blocks",player?8d:6d) : 0;
        }
        public int initiative(int die, Map<String,Double> stats) {
            return die + (worldCombat ? stats.getOrDefault("initiative_bonus",0d).intValue() : 0);
        }
    }
    private static final Map<String,RulesProfile> PROFILES=Map.of(
            TACTICAL,new Profile(TACTICAL,true),MANUAL,new Profile(MANUAL,false),SRD,new dev.tacticalcombat.rules.srd.SrdRulesProfile());
    public static RulesProfile get(String id) {
        RulesProfile p=PROFILES.get(id);
        if(p==null) throw new IllegalArgumentException("Unknown rules profile: "+id);
        return p;
    }
}
