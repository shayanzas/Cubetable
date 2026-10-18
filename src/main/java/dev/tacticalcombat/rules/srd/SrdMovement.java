package dev.tacticalcombat.rules.srd;

import java.util.*;

/** Bounded weighted eight-neighbor search. Feet, never Euclidean route length, are the budget unit. */
public final class SrdMovement {
    private SrdMovement() {}
    public static final int MAX_NODES=4096;
    /** Maximum supported speed (300) plus one normal-action Dash. Search work stays node-bounded. */
    public static final int MAX_TURN_FEET=600;
    public static int stepCost(boolean difficult,boolean prone){return 5+(difficult?5:0)+(prone?5:0);}
    /** General SRD round-down rule, in whole feet; never round the charge to grid squares. */
    public static int standingCost(int speed){if(speed<0||speed>300)throw new IllegalArgumentException("Invalid speed");return speed/2;}
    public record Cell(int x,int z,double y) {
        public Cell {
            if(Math.abs((long)x)>30000000 || Math.abs((long)z)>30000000 || !Double.isFinite(y) || Math.abs(y)>4096)
                throw new IllegalArgumentException("Invalid SRD grid coordinate");
        }
    }
    public enum Reason { OK, BLOCKED, OCCUPIED, NO_SUPPORT, CLEARANCE, CORNER, UNSUPPORTED_ENVIRONMENT,
        UNLOADED, BORDER, OUT_OF_BUDGET, UNREACHABLE, SEARCH_LIMIT, INVALID_START }
    public record Step(Cell to,int feet,Reason reason) {
        public Step {
            Objects.requireNonNull(to); Objects.requireNonNull(reason);
            if(reason==Reason.OK && feet!=5 && feet!=10 && feet!=15) throw new IllegalArgumentException("Unsupported step cost");
        }
        public static Step denied(Cell to,Reason reason) { return new Step(to,0,reason); }
    }
    @FunctionalInterface public interface Terrain { Step step(Cell from,int dx,int dz); }
    public record Node(Cell cell,int costFeet,Cell parent) {}
    public record Boundary(Cell from,int dx,int dz,Reason reason) {}
    /** Pure preview data; no entity IDs or private character fields. Not a network authorization. */
    public record Preview(Cell start,int remainingFeet,Map<Cell,Node> reachable,List<Boundary> boundaries,boolean truncated) {
        public Preview {
            reachable=Map.copyOf(reachable); boundaries=List.copyOf(boundaries);
            if(remainingFeet<0 || remainingFeet>MAX_TURN_FEET || reachable.size()>MAX_NODES || boundaries.size()>MAX_NODES*8
                    || !reachable.containsKey(start)) throw new IllegalArgumentException("Invalid SRD preview bounds");
            for(var entry:reachable.entrySet()) {
                Node node=entry.getValue();
                if(!entry.getKey().equals(node.cell) || node.costFeet<0 || node.costFeet>remainingFeet)
                    throw new IllegalArgumentException("Invalid SRD preview node");
                if(node.cell.equals(start)) {
                    if(node.costFeet!=0 || node.parent!=null) throw new IllegalArgumentException("Invalid preview start");
                } else {
                    Node parent=reachable.get(node.parent);
                    if(parent==null || node.costFeet-parent.costFeet!=5 && node.costFeet-parent.costFeet!=10 && node.costFeet-parent.costFeet!=15)
                        throw new IllegalArgumentException("Invalid/cyclic preview parent chain");
                }
            }
        }
        public List<Cell> pathTo(Cell destination) {
            if(!reachable.containsKey(destination)) return List.of();
            var path=new ArrayList<Cell>();
            for(Cell p=destination;!p.equals(start);p=reachable.get(p).parent()) path.add(p);
            Collections.reverse(path); return List.copyOf(path);
        }
        public Reason status(Cell destination) {
            return reachable.containsKey(destination)?Reason.OK:truncated?Reason.SEARCH_LIMIT:Reason.UNREACHABLE;
        }
    }
    public static Preview search(Cell start,int remainingFeet,Terrain terrain) {
        Search search=new Search(start,remainingFeet);
        while(!search.advance(terrain,Integer.MAX_VALUE,Long.MAX_VALUE)) {}
        return search.result();
    }
    /** Resumable Dijkstra. Only pure search state is retained; callers own terrain validity and scheduling. */
    public static final class Search {
        private final Cell start;
        private final int remainingFeet;
        private final Map<Cell,Node> nodes=new LinkedHashMap<>();
        private final List<Boundary> boundaries=new ArrayList<>();
        private final PriorityQueue<Node> queue=new PriorityQueue<>(Comparator.comparingInt(Node::costFeet)
                .thenComparingInt(n->n.cell.x).thenComparingInt(n->n.cell.z).thenComparingDouble(n->n.cell.y));
        private Node current;
        private int neighbor;
        private boolean truncated,done;
        public Search(Cell start,int remainingFeet) {
        if(remainingFeet<0 || remainingFeet>MAX_TURN_FEET) throw new IllegalArgumentException("Invalid movement allowance");
        this.start=Objects.requireNonNull(start);this.remainingFeet=remainingFeet;
        Node first=new Node(start,0,null); nodes.put(start,first); queue.add(first);
        }
        /** Cooperative deadline: one terrain edge is indivisible. No live world access is implied by this class. */
        public boolean advance(Terrain terrain,int maxEdges,long deadlineNanos) {
            if(maxEdges<1) throw new IllegalArgumentException("Positive edge budget required");
            int edges=0;
            while(!done && edges<maxEdges && System.nanoTime()<deadlineNanos) {
                if(current==null) {
                    if(queue.isEmpty()) {done=true;break;}
                    current=queue.remove();neighbor=0;
                    if(nodes.get(current.cell)!=current) {current=null;continue;}
                }
                int dx=neighbor/3-1,dz=neighbor%3-1;
                neighbor++;
                if(neighbor==9) { // Keep the current node for this final edge, then resume with the next node.
                    Node processing=current;current=null;
                    process(terrain,processing,dx,dz);edges++;continue;
                }
                if(dx==0 && dz==0) continue;
                process(terrain,current,dx,dz);edges++;
            }
            return done;
        }
        private void process(Terrain terrain,Node current,int dx,int dz) {
                Step edge=terrain.step(current.cell,dx,dz);
                if(edge.reason!=Reason.OK) { boundaries.add(new Boundary(current.cell,dx,dz,edge.reason)); return; }
                if(edge.to.x-current.cell.x!=dx || edge.to.z-current.cell.z!=dz || Math.abs(edge.to.y-current.cell.y)>1.00001)
                    throw new IllegalArgumentException("Terrain returned a non-neighbor");
                int cost=current.costFeet+edge.feet;
                if(cost>remainingFeet) { boundaries.add(new Boundary(current.cell,dx,dz,Reason.OUT_OF_BUDGET)); return; }
                Node previous=nodes.get(edge.to);
                if(previous!=null && previous.costFeet<=cost) return;
                if(previous==null && nodes.size()>=MAX_NODES) { truncated=true; return; }
                Node next=new Node(edge.to,cost,current.cell); nodes.put(edge.to,next); queue.add(next);
        }
        public Preview result() {
            if(!done) throw new IllegalStateException("Search still pending");
            return new Preview(start,remainingFeet,nodes,boundaries,truncated);
        }
    }
}
