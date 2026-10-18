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
    private static int priority(String id){return id.startsWith("attack:")?3:id.startsWith("denied:")||id.startsWith("pause:")?2:id.startsWith("turn:")?1:0;}
    public static String category(String id){return id.startsWith("attack:")?"ATTACK":id.startsWith("denied:")?"REJECTED":id.startsWith("turn:")?"TURN":id.startsWith("move:")?"MOVEMENT":id.startsWith("posture:")?"POSTURE":id.startsWith("dodge:")?"DODGE":id.startsWith("dash:")?"DASH":id.startsWith("preview:")?"PREVIEW":"STATUS";}
    public Event toast(long tick){Event best=null;for(var event:entries)if(tick-event.tick()<200&&(best==null||priority(event.id())>=priority(best.id())))best=event;return best;}
}
