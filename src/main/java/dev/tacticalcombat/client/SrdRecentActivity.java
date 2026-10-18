package dev.tacticalcombat.client;
import java.util.*;
/** Bounded transient authorized history. Permission changes/disconnect clear the entire buffer. */
public final class SrdRecentActivity {
    public record Event(String id,String text,long tick) {}
    private final ArrayDeque<Event> entries=new ArrayDeque<>();
    private final LinkedHashSet<String> seen=new LinkedHashSet<>();
    public void clear(){entries.clear();seen.clear();}
    public void add(String id,String text,long tick){
        if(text==null||text.isBlank()||!seen.add(id))return;
        if(seen.size()>256)seen.remove(seen.iterator().next());
        if(entries.size()==32)entries.removeFirst();entries.addLast(new Event(id,text,tick));
    }
    public List<Event> newest(){var result=new ArrayList<>(entries);Collections.reverse(result);return List.copyOf(result);}
    public Event toast(long tick){var event=entries.peekLast();return event!=null&&tick-event.tick()<200?event:null;}
}
