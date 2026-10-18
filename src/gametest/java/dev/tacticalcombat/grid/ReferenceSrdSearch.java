package dev.tacticalcombat.grid;
import java.util.*;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import static dev.tacticalcombat.rules.srd.SrdMovement.MAX_NODES;
/** Frozen cba6b37 Dijkstra oracle; development tests only. */
public final class ReferenceSrdSearch {
    /** K-A oracle: keep the frozen search's costs/cells/boundaries, then independently
     * choose legal parents in increasing cost order. Production chooses during relaxation. */
    public static Preview preferred(Cell start,int feet,Terrain terrain){
        Preview frozen=search(start,feet,terrain);
        var chosen=new LinkedHashMap<Cell,Node>();chosen.put(start,frozen.reachable().get(start));
        record Column(int x,int z) {}
        var columns=new HashMap<Column,List<Cell>>();
        for(var c:frozen.reachable().keySet())columns.computeIfAbsent(new Column(c.x(),c.z()),k->new ArrayList<>()).add(c);
        var ordered=frozen.reachable().values().stream().sorted(Comparator.comparingInt(Node::costFeet)
            .thenComparingInt(n->n.cell().x()).thenComparingInt(n->n.cell().z()).thenComparingDouble(n->n.cell().y())).toList();
        for(var node:ordered){
            if(node.parent()==null)continue;
            var candidates=new ArrayList<Cell>();var to=node.cell();
            for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)if(dx!=0||dz!=0)
                candidates.addAll(columns.getOrDefault(new Column(to.x()+dx,to.z()+dz),List.of()));
            candidates.sort(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z).thenComparingDouble(Cell::y));
            Cell best=null;long bestScore=Long.MAX_VALUE;
            for(var from:candidates){
                var parent=chosen.get(from);if(parent==null||parent.costFeet()>=node.costFeet())continue;
                var edge=terrain.step(from,to.x()-from.x(),to.z()-from.z());
                if(edge.reason()!=Reason.OK||!edge.to().equals(to)||parent.costFeet()+edge.feet()!=node.costFeet())continue;
                // Score the full candidate path, without production's accumulated Shape metadata.
                var route=new ArrayList<Cell>();route.add(to);
                for(Cell c=from;c!=null;c=chosen.get(c).parent())route.add(c);
                Collections.reverse(route);long length=0,turns=0;int px=0,pz=0;
                for(int i=1;i<route.size();i++){
                    int dx=route.get(i).x()-route.get(i-1).x(),dz=route.get(i).z()-route.get(i-1).z();
                    length+=dx!=0&&dz!=0?1414:1000;
                    if(i>1&&(dx!=px||dz!=pz))turns++;px=dx;pz=dz;
                }
                long score=length*1000+turns;
                if(score<bestScore){bestScore=score;best=from;}
            }
            if(best==null)throw new AssertionError("Frozen reachable cell has no legal minimum-cost parent");
            chosen.put(to,new Node(to,node.costFeet(),best));
        }
        return new Preview(start,feet,chosen,frozen.boundaries(),frozen.truncated());
    }
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

