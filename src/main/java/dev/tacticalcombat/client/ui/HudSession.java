package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Bounded presentation state from authorized read models; no gameplay mutations. */
public final class HudSession {
    public enum Filter { ALL, COMBAT, DICE, CAMPAIGN, REST }
    public record Member(String character,String actor,String name,String characterClass,String health,double fraction,boolean online,List<String> conditions) {}
    public record Actor(String id,String name,int initiative,boolean active) {}
    public record Line(UUID id,long sequence,String kind,String summary,List<String> actors,UiEvents.Roll roll) {
        public String calculation(){if(roll==null)return summary;return "d"+roll.sides()+" "+roll.faces()+" kept "+roll.kept()+" "+(roll.modifier()>=0?"+":"")+roll.modifier()+" = "+roll.total();}
    }
    public record Bubble(String text,long until) {}
    public static final int HISTORY_LIMIT=512,QUEUE_LIMIT=8;
    private final LinkedHashMap<UUID,Line> history=new LinkedHashMap<>();
    private final LinkedHashSet<UUID> seen=new LinkedHashSet<>();
    private final ArrayDeque<Line> dice=new ArrayDeque<>(),notices=new ArrayDeque<>();
    private final Map<String,Bubble> recent=new HashMap<>();
    private List<Member> members=List.of();
    private List<Actor> actors=List.of();
    private String session="",campaign="",encounter="";
    private long cursor;
    private boolean bootstrap=true;
    private int round;
    private long viewVersion;
    public long viewVersion(){return viewVersion;}
    public int historySize(){return history.size();}
    public Line event(UUID id){return history.get(id);}
    public void clear(){viewVersion++;history.clear();seen.clear();dice.clear();notices.clear();recent.clear();members=List.of();actors=List.of();session="";campaign="";encounter="";cursor=0;bootstrap=true;round=0;}
    public void campaign(String id){if(!campaign.equals(id)){clear();campaign=id;}}
    public String campaign(){return campaign;}
    public String encounter(){return encounter;}
    public long cursor(){return cursor;}
    public boolean catchingUp(){return bootstrap;}
    public int round(){return round;}
    public List<Member> members(){return members;}
    public List<Actor> actors(){return actors;}
    public List<Line> history(Filter filter){return history.values().stream().filter(e->matches(e,filter)).sorted(Comparator.comparingLong(Line::sequence)).toList();}
    public static boolean matches(Line e,Filter filter){return switch(filter){case ALL->true;case DICE->e.roll()!=null;case COMBAT->Set.of("ATTACK","DAMAGE","HEALING","TURN","CONDITION","ZERO_HP","REJECTION").contains(e.kind());case CAMPAIGN->Set.of("APPROVAL","ASSIGNMENT","DM_ACTION","RESOURCE").contains(e.kind());case REST->e.kind().equals("REST");};}
    public void party(JsonObject reply){var result=new ArrayList<Member>();for(var value:array(reply,"members")){if(result.size()>=1024)break;var o=value.getAsJsonObject();String health="HP private";double fraction=-1;if(o.has("hp")&&o.has("maximumHp")){double hp=o.get("hp").getAsDouble(),max=o.get("maximumHp").getAsDouble();health=format(hp)+"/"+format(max)+" HP";fraction=max>0?Math.clamp(hp/max,0,1):0;}else if(o.has("healthFraction")){fraction=Math.clamp(o.get("healthFraction").getAsDouble(),0,1);health="HP bar";}var conditions=new ArrayList<String>();for(var e:array(o,"recordedConditions"))if(conditions.size()<16)conditions.add(text(e.getAsJsonObject(),"name"));result.add(new Member(text(o,"character"),text(o,"skinPlayer"),text(o,"name"),text(o,"characterClass"),health,fraction,flag(o,"online"),List.copyOf(conditions)));}var updated=List.copyOf(result);if(!members.equals(updated))viewVersion++;members=updated;}
    public void initiative(String id,JsonObject reply){if(!encounter.equals(id)){actors=List.of();round=0;}encounter=id;String active=text(reply,"active");var result=new ArrayList<Actor>();for(var value:array(reply,"actors")){if(result.size()>=32)break;var o=value.getAsJsonObject();result.add(new Actor(text(o,"id"),text(o,"name"),o.has("initiative")?o.get("initiative").getAsInt():0,flag(o,"active")||text(o,"id").equals(active)));}actors=List.copyOf(result);round=reply.has("round")?reply.get("round").getAsInt():0;}
    public void endEncounter(){encounter="";actors=List.of();round=0;}
    public int events(JsonObject page,long now,int bubbleTicks){String incoming=text(page,"session");boolean restarted=!session.isEmpty()&&!incoming.equals(session);if(!incoming.equals(session)){history.clear();seen.clear();dice.clear();notices.clear();recent.clear();session=incoming;cursor=0;bootstrap=true;}var rows=array(page,"events");if(rows.size()>16)throw new IllegalArgumentException("Event page exceeds server limit");var ordered=new ArrayList<JsonObject>();for(var e:rows)ordered.add(e.getAsJsonObject());ordered.sort(Comparator.comparingLong(e->e.get("sequence").getAsLong()));int added=0;for(var e:ordered){UUID id=UUID.fromString(text(e,"id"));if(seen.contains(id))continue;var actorIds=new ArrayList<String>();for(var a:array(e,"actors"))actorIds.add(a.getAsString());UiEvents.Roll roll=e.has("roll")&&!e.get("roll").isJsonNull()?SheetSections.JSON.fromJson(e.get("roll"),UiEvents.Roll.class):null;Line line=new Line(id,e.get("sequence").getAsLong(),text(e,"kind"),text(e,"summary"),List.copyOf(actorIds),roll);seen.add(id);if(seen.size()>1024)seen.remove(seen.iterator().next());history.put(id,line);if(history.size()>HISTORY_LIMIT)history.remove(history.keySet().iterator().next());added++;if(!bootstrap){if(roll!=null){offer(dice,line);for(String actor:actorIds)recentBubble(actor,line.calculation(),now,bubbleTicks);}if(Set.of("TURN","REST","APPROVAL","ASSIGNMENT","REJECTION","ZERO_HP").contains(line.kind()))offer(notices,line);}}cursor=page.has("cursor")?Math.max(cursor,page.get("cursor").getAsLong()):cursor;if(rows.size()<16)bootstrap=false;if(restarted){cursor=0;bootstrap=true;}if(added>0||restarted)viewVersion++;return added;}
    /** Re-authorize retained history without replaying animations, retaining dedup identities. */
    public void revalidateHistory(){viewVersion++;history.clear();dice.clear();notices.clear();recent.clear();cursor=0;bootstrap=true;seen.clear();}
    public void visibleHistoryOnly(JsonObject page,long now,int duration){events(page,now,duration);}
    public Line nextDice(){return dice.pollFirst();}
    public Line nextNotice(){return notices.pollFirst();}
    public int queuedDice(){return dice.size();}
    public int queuedNotices(){return notices.size();}
    public Bubble bubble(String actor,long now){var value=recent.get(actor);if(value!=null&&now>=value.until()){recent.remove(actor);return null;}return value;}
    public static int natural(UiEvents.Roll roll){return roll.sides()==20&&roll.kept().size()==1?roll.faces().get(roll.kept().getFirst()):0;}
    public static UiLayout.Rect bounds(HudPreferences.Panel panel,int width,int height,int baseWidth,int baseHeight){int w=Math.min(Math.max(24,width-8),Math.max(24,(int)(baseWidth*panel.scale()))),h=Math.min(Math.max(18,height-8),Math.max(18,(int)(baseHeight*panel.scale())));return new UiLayout.Rect(4+(int)((width-w-8)*panel.x()),4+(int)((height-h-8)*panel.y()),w,h);}
    public static UiLayout.Rect initiativeBounds(HudPreferences p,int width,int height,UiLayout.Rect party){var r=bounds(p.initiative(),width,height,280,36);if(p.party().visible()&&p.party().x()==0&&p.party().y()==0&&p.initiative().x()==.5&&p.initiative().y()==0&&r.overlaps(party)){int x=party.x()+party.width()+6;if(width-x-4<70)return new UiLayout.Rect(4,Math.min(height-r.height()-4,party.y()+party.height()+6),width-8,r.height());return new UiLayout.Rect(x,4,width-x-4,r.height());}return r;}
    private static String format(double n){return n==(int)n?Integer.toString((int)n):String.format(Locale.ROOT,"%.1f",n);}
    private void recentBubble(String actor,String text,long now,int duration){recent.entrySet().removeIf(e->now>=e.getValue().until());if(recent.size()>=64&&!recent.containsKey(actor))recent.remove(recent.keySet().iterator().next());recent.put(actor,new Bubble(text,now+duration));}
    private static void offer(ArrayDeque<Line> queue,Line e){if(queue.size()>=QUEUE_LIMIT)queue.removeFirst();queue.addLast(e);}
}
