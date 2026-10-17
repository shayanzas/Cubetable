package dev.tacticalcombat.client.dicevisual;

import java.util.*;

/** Bounded visual queue with operation-ID deduplication. Overflow returns a result for accessible fallback. */
public final class PresentationQueue {
    private final int capacity,seenLimit;
    private final ArrayDeque<PresentationRoll> queued=new ArrayDeque<>();
    private final LinkedHashSet<String> seen=new LinkedHashSet<>();
    public PresentationQueue(int capacity,int seenLimit){if(capacity<1||seenLimit<capacity)throw new IllegalArgumentException("Invalid queue bounds");this.capacity=capacity;this.seenLimit=seenLimit;}
    public PresentationRoll offer(PresentationRoll roll){Objects.requireNonNull(roll);
        if(seen.contains(roll.identity())){if(roll.official()&&!roll.source().equals("CHARACTER_CHECK")){
            var pending=new ArrayList<>(queued);queued.clear();for(var item:pending)queued.addLast(item.identity().equals(roll.identity())?roll:item);
        }return null;}
        if(roll.official()){seen.add(roll.identity());if(seen.size()>seenLimit)seen.remove(seen.iterator().next());}
        PresentationRoll omitted=queued.size()==capacity?queued.removeFirst():null;queued.addLast(roll);return omitted;
    }
    public PresentationRoll poll(){return queued.pollFirst();}
    public boolean seen(String identity){return seen.contains(identity);}
    public int size(){return queued.size();}
    public void clear(){queued.clear();seen.clear();}
    public void invalidatePrivate(){queued.removeIf(PresentationRoll::official);seen.clear();}
}
