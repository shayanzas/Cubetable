package dev.tacticalcombat.client;

import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import java.util.*;

/** Isolated presentation model. No packets, rendering hooks, local world search or movement authority.
 * A future privacy-filtered SRD grid receiver can install server data here without changing ClientGrid.
 */
public final class SrdMovementPreview {
    public record Selection(Reason status,List<Cell> path,OptionalInt costFeet,int remainingFeet,
                            List<Reason> boundaryReasons) {
        public Selection { path=List.copyOf(path); boundaryReasons=List.copyOf(boundaryReasons); }
    }
    private Preview preview;
    public void apply(String profile,Preview serverPreview) {
        if(!RulesProfiles.SRD.equals(profile)) throw new IllegalArgumentException("SRD preview cannot consume another ruleset");
        preview=Objects.requireNonNull(serverPreview);
    }
    public void clear() { preview=null; }
    public Selection select(Cell destination) {
        if(preview==null || destination==null)
            return new Selection(Reason.INVALID_START,List.of(),OptionalInt.empty(),0,List.of());
        Node node=preview.reachable().get(destination);
        if(node!=null) return new Selection(Reason.OK,preview.pathTo(destination),OptionalInt.of(node.costFeet()),
                preview.remainingFeet()-node.costFeet(),List.of());
        // Reasons describe failed approach edges, not proof of a globally impassable destination.
        List<Reason> reasons=preview.boundaries().stream()
                .filter(b->b.from().x()+b.dx()==destination.x() && b.from().z()+b.dz()==destination.z())
                .map(Boundary::reason).distinct().toList();
        return new Selection(preview.status(destination),List.of(),OptionalInt.empty(),preview.remainingFeet(),reasons);
    }
}
