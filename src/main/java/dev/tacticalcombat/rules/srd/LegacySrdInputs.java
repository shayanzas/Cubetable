package dev.tacticalcombat.rules.srd;

/** Compatibility boundary: the strict v1 decoder remains the only interpretation of saved combat inputs. */
public final class LegacySrdInputs {
    private LegacySrdInputs() {}
    public static SrdCharacter read(String sheet) { return SrdCharacterCodec.parseSheet(sheet); }
    public static final String MODE = "legacy_srd_v1";
}
