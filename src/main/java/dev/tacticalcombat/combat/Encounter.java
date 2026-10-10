package dev.tacticalcombat.combat;

import dev.tacticalcombat.rules.RulesProfile;
import net.minecraft.entity.Entity;
import java.util.UUID;

/** Shared server-thread lifecycle/ownership contract. Action adapters remain ruleset-specific. */
public interface Encounter {
    UUID id();
    UUID campaign();
    RulesProfile rules();
    boolean isTurnOf(Entity actor);
    /** True asks the shared scheduler to end and remove this encounter. */
    boolean tick();
    void end();
}
