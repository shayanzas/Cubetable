package dev.tacticalcombat.rules;

import java.util.Map;

/** Trusted engine policy, independent of client sheet layouts and expression strings. */
public interface RulesProfile {
    String id();
    int version();
    boolean worldCombat();
    double movement(Map<String, Double> stats, boolean player);
    int initiative(int die, Map<String, Double> stats);
}
