package dev.tacticalcombat.grid;
import java.util.*;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import static dev.tacticalcombat.rules.srd.SrdMovement.MAX_NODES;
/** Frozen cba6b37 Dijkstra oracle; development tests only. */
public final class ReferenceSrdSearch {
    public static Preview search(Cell start,int remainingFeet,Terrain terrain) {
        if(remainingFeet<0 || remainingFeet>300) throw new IllegalArgumentException("Invalid movement allowance");
        var nodes=new LinkedHashMap<Cell,Node>(); var boundaries=new ArrayList<Boundary>();
        var queue=new PriorityQueue<Node>(Comparator.comparingInt(Node::costFeet)
                .thenComparingInt(n->n.cell().x()).thenComparingInt(n->n.cell().z()).thenComparingDouble(n->n.cell().y()));
        Node first=new Node(start,0,null); nodes.put(start,first); queue.add(first);
        boolean truncated=false;
        while(!queue.isEmpty()) {
            Node current=queue.remove(); if(nodes.get(current.cell())!=current) continue;
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                if(dx==0 && dz==0) continue;
                Step edge=terrain.step(current.cell(),dx,dz);
                if(edge.reason()!=Reason.OK) { boundaries.add(new Boundary(current.cell(),dx,dz,edge.reason())); continue; }
                if(edge.to().x()-current.cell().x()!=dx || edge.to().z()-current.cell().z()!=dz || Math.abs(edge.to().y()-current.cell().y())>1.00001)
                    throw new IllegalArgumentException("Terrain returned a non-neighbor");
                int cost=current.costFeet()+edge.feet();
                if(cost>remainingFeet) { boundaries.add(new Boundary(current.cell(),dx,dz,Reason.OUT_OF_BUDGET)); continue; }
                Node previous=nodes.get(edge.to());
                if(previous!=null && previous.costFeet()<=cost) continue;
                if(previous==null && nodes.size()>=MAX_NODES) { truncated=true; continue; }
                Node next=new Node(edge.to(),cost,current.cell()); nodes.put(edge.to(),next); queue.add(next);
            }
        }
        return new Preview(start,remainingFeet,nodes,boundaries,truncated);
    }
}

