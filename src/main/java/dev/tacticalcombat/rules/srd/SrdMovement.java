package dev.tacticalcombat.rules.srd;

import java.util.*;

/** Bounded weighted eight-neighbor search. Feet, never Euclidean route length, are the budget unit. */
public final class SrdMovement {
    private SrdMovement() {}
    public static final int MAX_NODES=4096;
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
            if(reason==Reason.OK && feet!=5 && feet!=10) throw new IllegalArgumentException("Unsupported step cost");
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
            if(remainingFeet<0 || remainingFeet>300 || reachable.size()>MAX_NODES || boundaries.size()>MAX_NODES*8
                    || !reachable.containsKey(start)) throw new IllegalArgumentException("Invalid SRD preview bounds");
            for(var entry:reachable.entrySet()) {
                Node node=entry.getValue();
                if(!entry.getKey().equals(node.cell) || node.costFeet<0 || node.costFeet>remainingFeet)
                    throw new IllegalArgumentException("Invalid SRD preview node");
                if(node.cell.equals(start)) {
                    if(node.costFeet!=0 || node.parent!=null) throw new IllegalArgumentException("Invalid preview start");
                } else {
                    Node parent=reachable.get(node.parent);
                    if(parent==null || node.costFeet-parent.costFeet!=5 && node.costFeet-parent.costFeet!=10)
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
        if(remainingFeet<0 || remainingFeet>300) throw new IllegalArgumentException("Invalid movement allowance");
        var nodes=new LinkedHashMap<Cell,Node>(); var boundaries=new ArrayList<Boundary>();
        var queue=new PriorityQueue<Node>(Comparator.comparingInt(Node::costFeet)
                .thenComparingInt(n->n.cell.x).thenComparingInt(n->n.cell.z).thenComparingDouble(n->n.cell.y));
        Node first=new Node(start,0,null); nodes.put(start,first); queue.add(first);
        boolean truncated=false;
        while(!queue.isEmpty()) {
            Node current=queue.remove(); if(nodes.get(current.cell)!=current) continue;
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                if(dx==0 && dz==0) continue;
                Step edge=terrain.step(current.cell,dx,dz);
                if(edge.reason!=Reason.OK) { boundaries.add(new Boundary(current.cell,dx,dz,edge.reason)); continue; }
                if(edge.to.x-current.cell.x!=dx || edge.to.z-current.cell.z!=dz || Math.abs(edge.to.y-current.cell.y)>1.00001)
                    throw new IllegalArgumentException("Terrain returned a non-neighbor");
                int cost=current.costFeet+edge.feet;
                if(cost>remainingFeet) { boundaries.add(new Boundary(current.cell,dx,dz,Reason.OUT_OF_BUDGET)); continue; }
                Node previous=nodes.get(edge.to);
                if(previous!=null && previous.costFeet<=cost) continue;
                if(previous==null && nodes.size()>=MAX_NODES) { truncated=true; continue; }
                Node next=new Node(edge.to,cost,current.cell); nodes.put(edge.to,next); queue.add(next);
            }
        }
        return new Preview(start,remainingFeet,nodes,boundaries,truncated);
    }
}
