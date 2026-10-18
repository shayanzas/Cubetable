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
    public record Details(Cell destination,Selection selection,long revision,boolean stale,String routeStatus) {}
    private long revision=-1;
    private boolean stale=true;
    private String routeStatus="Unavailable";
    public void context(long revision,String status,boolean stale){this.revision=revision;routeStatus=status;this.stale=stale;}
    public Details details(Cell destination){return new Details(destination,select(destination),revision,stale,routeStatus);}
    private Preview preview;
    private List<Cell> window=List.of();
    private record CachedSelection(Selection selection,Set<Cell> pathCells) {}
    private static final CachedSelection EMPTY=new CachedSelection(new Selection(Reason.INVALID_START,List.of(),OptionalInt.empty(),0,List.of()),Set.of());
    private final LinkedHashMap<Cell,CachedSelection> selections=new LinkedHashMap<>(64,.75f,true);
    public void apply(String profile,Preview serverPreview) {
        if(!RulesProfiles.SRD.equals(profile)) throw new IllegalArgumentException("SRD preview cannot consume another ruleset");
        preview=Objects.requireNonNull(serverPreview);stale=false;
        selections.clear();var start=preview.start();
        window=preview.reachable().keySet().stream().filter(c->Math.abs(c.x()-start.x())<=10 && Math.abs(c.z()-start.z())<=6)
                .sorted(Comparator.comparingDouble(Cell::y)).toList();
    }
    public void clear() { preview=null;stale=true;revision=-1;routeStatus="Unavailable";window=List.of();selections.clear(); }
    public List<Cell> localWindow() {return window;}
    public int retainedSelections() {return selections.size();}
    public Selection select(Cell destination) {
        return choice(destination).selection;
    }
    public Set<Cell> pathCells(Cell destination) {return choice(destination).pathCells;}
    private CachedSelection choice(Cell destination) {
        if(preview==null || destination==null) return EMPTY;
        var cached=selections.get(destination);if(cached!=null) return cached;
        var selected=describe(destination);var result=new CachedSelection(selected,Set.copyOf(selected.path()));
        if(selections.size()>=64) selections.remove(selections.keySet().iterator().next());
        selections.put(destination,result);return result;
    }
    private Selection describe(Cell destination) {
        Node node=preview.reachable().get(destination);
        if(node!=null) return new Selection(Reason.OK,preview.pathTo(destination),OptionalInt.of(node.costFeet()),
                preview.remainingFeet()-node.costFeet(),List.of());
        // Reasons describe failed approach edges, not proof of a globally impassable destination.
        List<Reason> reasons=preview.approachReasons(destination);
        return new Selection(preview.status(destination),List.of(),OptionalInt.empty(),preview.remainingFeet(),reasons);
    }
}
