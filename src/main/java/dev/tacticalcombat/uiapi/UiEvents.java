package dev.tacticalcombat.uiapi;
import dev.tacticalcombat.character.CharacterRepository;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Server-only bounded session history. No C2S publish operation exists. */
public final class UiEvents {
    public enum Kind { ATTACK, DAMAGE, HEALING, TURN, CONDITION, APPROVAL, ASSIGNMENT, DM_ACTION, REST, RESOURCE, REJECTION, DICE, ZERO_HP }
    public enum Visibility { CAMPAIGN, DM, RECIPIENTS_AND_DM }
    public record Roll(int sides,List<Integer> faces,List<Integer> kept,int modifier,int total,String purpose,boolean critical) {
        public Roll {
            faces=List.copyOf(faces);kept=List.copyOf(kept);require(Set.of(4,6,8,10,12,20,100).contains(sides) && !faces.isEmpty() && faces.size()<=10,"Invalid dice");
            for(int n:faces) require(n>=1 && n<=sides,"Invalid die face");require(!kept.isEmpty() && new HashSet<>(kept).size()==kept.size(),"Invalid kept dice");
            int sum=modifier;for(int i:kept) {require(i>=0 && i<faces.size(),"Invalid kept index");sum+=faces.get(i);}require(sum==total && Math.abs(modifier)<=999,"Roll total mismatch");require(purpose!=null && purpose.length()<=80,"Invalid roll purpose");
        }
        public static Roll from(int sides,List<Integer> faces,int keep,int modifier,String purpose,boolean critical) {
            var indices=new ArrayList<Integer>();
            if(keep==0) for(int i=0;i<faces.size();i++) indices.add(i);
            else {require(keep==1 || keep==2,"Unknown keep mode");int best=0;for(int i=1;i<faces.size();i++) if(keep==1?faces.get(i)>faces.get(best):faces.get(i)<faces.get(best)) best=i;indices.add(best);}
            int total=modifier;for(int i:indices) total+=faces.get(i);return new Roll(sides,faces,indices,modifier,total,purpose,critical);
        }
    }
    public record Event(UUID id,long sequence,long timestamp,UUID campaign,UUID encounter,long turn,Kind kind,List<UUID> actors,
            String summary,Roll roll,Visibility visibility,Set<UUID> recipients) {
        public Event {actors=List.copyOf(actors);recipients=Set.copyOf(recipients);require(actors.size()<=16 && recipients.size()<=256 && summary.length()<=240,"Event limits exceeded");}
    }
    public record Page(UUID session,List<Event> events,long cursor,long oldest,boolean historyLost) {public Page {events=List.copyOf(events);}}
    public static final UiEvents SESSION=new UiEvents();
    private final LinkedHashMap<UUID,Event> events=new LinkedHashMap<>();
    private long sequence;
    private UUID session=UUID.randomUUID();
    public boolean publish(UUID id,UUID campaign,UUID encounter,long turn,Kind kind,List<UUID> actors,String summary,Roll roll,Visibility visibility,Set<UUID> recipients) {
        Objects.requireNonNull(id);Objects.requireNonNull(campaign);Objects.requireNonNull(kind);Objects.requireNonNull(visibility);
        if(events.containsKey(id)) return false;
        var e=new Event(id,++sequence,System.currentTimeMillis(),campaign,encounter,turn,kind,actors,summary,roll,visibility,recipients);
        if(events.size()>=512) events.remove(events.keySet().iterator().next());events.put(id,e);return true;
    }
    /** Presentation failures must never roll back or relabel an already committed gameplay action. */
    public void safePublish(UUID id,UUID campaign,UUID encounter,long turn,Kind kind,List<UUID> actors,String summary,Roll roll,Visibility visibility,Set<UUID> recipients) {
        try {publish(id,campaign,encounter,turn,kind,actors,summary,roll,visibility,recipients);}
        catch(RuntimeException failure) {System.getLogger("Cubetable events").log(System.Logger.Level.WARNING,"Could not record presentation event",failure);}
    }
    public Page query(CharacterRepository repo,CharacterRepository.Principal who,UUID campaign,long cursor,Kind filter) {
        require(repo.member(who,campaign),"Campaign event permission denied");require(cursor>=0,"Invalid event cursor");var result=new ArrayList<Event>();long next=cursor;
        for(var e:events.values()) if(e.sequence>cursor) {
            next=e.sequence;
            if(e.campaign.equals(campaign) && (filter==null || filter==e.kind) && (e.visibility==Visibility.CAMPAIGN || repo.manages(who,campaign) || e.visibility==Visibility.RECIPIENTS_AND_DM && e.recipients.contains(who.id()))) result.add(e);
            if(result.size()==16) break;
        }
        long oldest=events.isEmpty()?sequence+1:events.values().iterator().next().sequence;
        return new Page(session,result,next,oldest,cursor>0 && cursor<oldest-1);
    }
    public void clear() {events.clear();sequence=0;session=UUID.randomUUID();}
}
