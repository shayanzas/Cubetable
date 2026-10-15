package dev.tacticalcombat.rules.srd;

import java.util.Objects;

/** A description of an existing input/calculation, never a grant or an authorization decision. */
public record RulesProvenance(Kind kind, String source, String explanation) {
    public enum Kind { PERSISTED_INPUT, DERIVED, RUNTIME_RESOURCE, RECORDED_ONLY, LEGACY_OVERRIDE, UNKNOWN }
    public RulesProvenance { Objects.requireNonNull(kind); Objects.requireNonNull(source); Objects.requireNonNull(explanation); }
}
