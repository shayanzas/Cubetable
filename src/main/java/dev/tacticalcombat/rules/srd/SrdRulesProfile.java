package dev.tacticalcombat.rules.srd;

import dev.tacticalcombat.rules.RulesProfile;
import dev.tacticalcombat.rules.RulesProfiles;
import java.util.Map;

/** Typed calculation policy, deliberately gated until the complete server action pipeline exists. */
public final class SrdRulesProfile implements RulesProfile {
    @Override public String id() { return RulesProfiles.SRD; }
    @Override public int version() { return 1; }
    @Override public boolean worldCombat() { return false; }
    @Override public double movement(Map<String,Double> stats,boolean player) {
        throw new IllegalArgumentException("SRD movement requires an approved, validated SrdCharacter, not Tactical stats");
    }
    @Override public int initiative(int die,Map<String,Double> stats) {
        throw new IllegalArgumentException("SRD initiative requires an approved, validated SrdCharacter, not Tactical stats");
    }
    public int movementSquares(SrdCharacter character) { return character.walkingSquares(); }
    public SrdRules.D20 initiative(SrdCharacter character,SrdRules.Dice serverDice) {
        return SrdRules.initiative(character,serverDice);
    }
}
