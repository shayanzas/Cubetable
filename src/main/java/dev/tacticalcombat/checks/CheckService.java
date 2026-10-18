package dev.tacticalcombat.checks;

import dev.tacticalcombat.character.CharacterRepository;
import dev.tacticalcombat.character.CharacterRepository.Principal;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.uiapi.UiEvents;
import java.util.*;
import java.util.function.*;
import static dev.tacticalcombat.checks.CharacterChecks.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Server-thread, roll-only session ledger. Unknown IDs can never execute. No resource writes. */
public final class CheckService {
    public static final int CAPACITY=2048, MAX_TARGETS=16;
    public static final long LIFETIME=600_000;
    public enum Visibility { CAMPAIGN, DM_ONLY, RECIPIENTS }
    public enum Source { PLAYER_CHARACTER_CHECK, DM_ADJUDICATION, DM_REQUEST }
    public record Spec(Key check,Mode mode,String purpose,Visibility visibility,Set<UUID> recipients,Integer dc) {
        public Spec {Objects.requireNonNull(check);Objects.requireNonNull(mode);Objects.requireNonNull(visibility);require(purpose!=null&&purpose.length()<=80&&purpose.chars().noneMatch(Character::isISOControl),"Purpose must be short plain text");recipients=Set.copyOf(recipients);require(recipients.size()<=16,"At most 16 private recipients");require(visibility==Visibility.RECIPIENTS||recipients.isEmpty(),"Recipients only apply to specific visibility");require(dc==null||dc>=0&&dc<=100,"DC must be 0–100");}
    }
    public record Result(int version,UUID operation,UUID campaign,UUID character,UUID actor,UUID encounter,Key check,String rulesVersion,int projectionVersion,String purpose,Visibility visibility,Source source,int sides,List<Integer> faces,List<Integer> kept,Mode mode,Modifier modifier,int total,Integer dc,Boolean success) {
        public Result {faces=List.copyOf(faces);kept=List.copyOf(kept);}
        public Result withoutDc(){return new Result(version,operation,campaign,character,actor,encounter,check,rulesVersion,projectionVersion,purpose,visibility,source,sides,faces,kept,mode,modifier,total,null,null);}
    }
    public record Ticket(UUID id,UUID group,UUID campaign,UUID character,UUID creator,UUID executor,UUID controller,long revision,long runtimeRevision,long expires,Source source,Spec spec,UUID encounter) {}
    public record View(Ticket ticket,String status,Result result) {}
    /** Resolved only by a trusted server binding lookup, never a packet. Dodge is sampled at execution. */
    public record Context(UUID actor,UUID encounter,boolean dodge) {}
    private static final class Entry {final Ticket ticket;final Context context;Result result;boolean dismissed;Entry(Ticket t,Context c){ticket=t;context=c;}}
    private final CharacterRepository repo;private final LongSupplier clock;private final IntSupplier die;private final Function<UUID,Principal> principal;
    private final BiFunction<UUID,UUID,Context> contexts;
    private final LinkedHashMap<UUID,Entry> entries=new LinkedHashMap<>();
    private final Map<UUID,ArrayDeque<Long>> rates=new HashMap<>();
    public CheckService(CharacterRepository repo,LongSupplier clock,IntSupplier die,Function<UUID,Principal> principal){this(repo,clock,die,principal,null);}
    public CheckService(CharacterRepository repo,LongSupplier clock,IntSupplier die,Function<UUID,Principal> principal,BiFunction<UUID,UUID,Context> contexts){this.repo=repo;this.clock=clock;this.die=die;this.principal=principal;this.contexts=contexts;}
    private Principal who(UUID id){var p=principal.apply(id);return p==null?new Principal(id,false):p;}
    private void prune(){long now=clock.getAsLong();entries.values().removeIf(e->now>=e.ticket.expires());rates.values().forEach(q->q.removeIf(t->now-t>=60_000));rates.values().removeIf(ArrayDeque::isEmpty);}
    private void rate(Principal p){var q=rates.computeIfAbsent(p.id(),k->new ArrayDeque<>());require(q.size()<20,"Check request rate limit: 20 per minute");q.add(clock.getAsLong());}
    public CharacterRepository.Character eligible(UUID campaign,UUID id){var c=repo.character(id);require(c.campaign().equals(campaign)&&c.approved()&&c.profile().equals(RulesProfiles.SRD)&&c.profileVersion()==1,"Approved SRD campaign character required");require(repo.member(who(c.owner()),campaign)&&repo.member(who(repo.controller(id)),campaign),"Character owner/controller access revoked");return c;}
    private void permitted(Principal p,UUID campaign,UUID id,Source source){require(repo.member(p,campaign),"Campaign membership required");eligible(campaign,id);require(source==Source.PLAYER_CHARACTER_CHECK?repo.controller(id).equals(p.id())&&Objects.equals(repo.binding(id),p.id()):repo.manages(p,campaign),"Current bound controller or DM authority required");}
    private void validateSpec(Principal p,UUID campaign,Spec spec,Source source){if(source==Source.PLAYER_CHARACTER_CHECK)require(spec.dc()==null&&spec.visibility()==Visibility.RECIPIENTS&&spec.recipients().equals(Set.of(p.id())),"Player checks are private to self and current DMs");for(UUID r:spec.recipients())require(repo.member(who(r),campaign),"Specific recipient must be a campaign member");}
    public Ticket prepare(Principal p,UUID campaign,UUID id,long revision,Spec spec,Source source,UUID encounter){
        require(source!=Source.DM_REQUEST,"Use group request workflow");prune();permitted(p,campaign,id,source);validateSpec(p,campaign,spec,source);var c=eligible(campaign,id);require(c.revision()==revision,"Stale character revision");resolve(c.sheet(),spec.check());
        Context context=contexts==null?null:contexts.apply(campaign,id);if(context!=null)encounter=context.encounter();
        for(var e:entries.values())if(e.result==null&&!e.dismissed&&e.ticket.creator().equals(p.id())&&e.ticket.character().equals(id)&&e.ticket.source()==source&&e.ticket.revision()==revision&&e.ticket.runtimeRevision()==c.runtimeRevision()&&e.ticket.spec().equals(spec)&&Objects.equals(e.ticket.encounter(),encounter)&&(context==null||e.context!=null&&Objects.equals(e.context.actor(),context.actor())))return e.ticket;
        require(entries.size()<CAPACITY,"Roll session capacity reached; wait for expiry");rate(p);var t=new Ticket(UUID.randomUUID(),UUID.randomUUID(),campaign,id,p.id(),p.id(),repo.controller(id),revision,c.runtimeRevision(),clock.getAsLong()+LIFETIME,source,spec,encounter);entries.put(t.id(),new Entry(t,context));return t;
    }
    public List<Ticket> request(Principal dm,UUID campaign,List<UUID> targets,Spec spec,Set<UUID> online){
        prune();require(repo.member(dm,campaign)&&repo.manages(dm,campaign),"Current DM authority required");require(!targets.isEmpty()&&targets.size()<=MAX_TARGETS&&new HashSet<>(targets).size()==targets.size(),"Select 1–16 distinct characters");validateSpec(dm,campaign,spec,Source.DM_REQUEST);
        var resolvedContexts=new HashMap<UUID,Context>();
        for(UUID id:targets){var c=eligible(campaign,id);UUID controller=repo.controller(id);require(online.contains(controller)&&Objects.equals(repo.binding(id),controller),"Request target must be an online bound player character");resolve(c.sheet(),spec.check());if(contexts!=null)resolvedContexts.put(id,contexts.apply(campaign,id));}
        // Canonically identical groups retain their identity for the full lifetime, including completion/dismissal.
        for(var e:entries.values())if(e.ticket.source()==Source.DM_REQUEST&&e.ticket.creator().equals(dm.id())&&e.ticket.campaign().equals(campaign)&&e.ticket.spec().equals(spec)){
            var group=entries.values().stream().filter(x->x.ticket.group().equals(e.ticket.group())).map(x->x.ticket).toList();if(new HashSet<>(group.stream().map(Ticket::character).toList()).equals(new HashSet<>(targets)))return group;
        }
        require(entries.size()+targets.size()<=CAPACITY,"Roll session capacity reached; wait for expiry");rate(dm);UUID group=UUID.randomUUID();var out=new ArrayList<Ticket>();for(UUID id:targets){var c=repo.character(id);var context=resolvedContexts.get(id);var t=new Ticket(UUID.randomUUID(),group,campaign,id,dm.id(),repo.controller(id),repo.controller(id),c.revision(),c.runtimeRevision(),clock.getAsLong()+LIFETIME,Source.DM_REQUEST,spec,context==null?null:context.encounter());entries.put(t.id(),new Entry(t,context));out.add(t);}return List.copyOf(out);
    }
    private void recheck(Ticket t){var c=eligible(t.campaign(),t.character());require(repo.controller(t.character()).equals(t.controller()),"Controller changed");require(repo.member(who(t.creator()),t.campaign()),"Creator campaign access revoked");if(t.source()!=Source.PLAYER_CHARACTER_CHECK)require(repo.manages(who(t.creator()),t.campaign()),"Requesting DM authority revoked");if(t.source()!=Source.DM_ADJUDICATION)require(Objects.equals(repo.binding(t.character()),t.executor()),"Player binding changed");}
    public Result execute(Principal p,UUID campaign,UUID id,UUID encounter){prune();var e=entries.get(id);require(e!=null,"Unknown or expired roll ID; old outcomes cannot be recovered after restart");var t=e.ticket;require(t.campaign().equals(campaign)&&t.executor().equals(p.id())&&repo.member(p,campaign),"Roll executor permission denied");recheck(t);require(!e.dismissed,"Request dismissed");if(e.result!=null)return visibleResult(p,e);
        Context current=contexts==null?null:contexts.apply(campaign,t.character());
        if(contexts!=null)require(Objects.equals(e.context.actor(),current.actor())&&Objects.equals(e.context.encounter(),current.encounter()),"Check actor binding or encounter changed; prepare a new check");
        else require(Objects.equals(t.encounter(),encounter)||t.source()==Source.DM_REQUEST,"Encounter context changed");
        Mode mode=withDodge(t.spec().mode(),current!=null&&current.dodge()&&t.spec().check().category()==Category.SAVE&&t.spec().check().id().equals("dex"));var c=eligible(campaign,t.character());require(c.revision()==t.revision()&&c.runtimeRevision()==t.runtimeRevision(),"Stale character/runtime revision; request a new check");var mod=resolve(c.sheet(),t.spec().check());var faces=new ArrayList<Integer>();for(int i=0;i<(mode==Mode.NORMAL?1:2);i++){int n=die.getAsInt();require(n>=1&&n<=20,"Server RNG returned invalid face");faces.add(n);}int keep=0;if(faces.size()==2&&(mode==Mode.ADVANTAGE?faces.get(1)>faces.get(0):faces.get(1)<faces.get(0)))keep=1;int total=faces.get(keep)+mod.total();Integer dc=t.spec().dc();
        e.result=new Result(1,t.id(),campaign,t.character(),p.id(),t.encounter(),t.spec().check(),"srd51_checks_v1",1,t.spec().purpose(),t.spec().visibility(),t.source(),20,faces,List.of(keep),mode,mod,total,dc,dc==null?null:total>=dc);
        // Accepted immutable outcome precedes presentation. Event contains no DC or success information.
        var v=t.spec().visibility()==Visibility.CAMPAIGN?UiEvents.Visibility.CAMPAIGN:t.spec().visibility()==Visibility.DM_ONLY?UiEvents.Visibility.DM:UiEvents.Visibility.RECIPIENTS_AND_DM;
        UiEvents.SESSION.checked(t.id(),campaign,t.encounter(),List.of(t.character()),t.source()+" · "+t.spec().check().category()+" "+t.spec().check().id()+" · "+t.spec().purpose(),new UiEvents.Roll(20,faces,List.of(keep),mod.total(),total,t.spec().purpose(),false),v,t.spec().recipients(),viewer->{recheck(t);return clock.getAsLong()<t.expires()&&repo.member(viewer,campaign)&&resultAllowed(viewer,t);});
        return visibleResult(p,e);
    }
    private boolean resultAllowed(Principal p,Ticket t){return repo.manages(p,t.campaign())||t.spec().visibility()==Visibility.CAMPAIGN||t.spec().visibility()==Visibility.RECIPIENTS&&t.spec().recipients().contains(p.id())&&(p.id().equals(t.executor())?repo.controller(t.character()).equals(p.id()):true);}
    private Result visibleResult(Principal p,Entry e){if(!resultAllowed(p,e.ticket))return null;return repo.manages(p,e.ticket.campaign())?e.result:e.result.withoutDc();}
    public List<View> list(Principal p,UUID campaign){prune();require(repo.member(p,campaign),"Campaign membership required");var out=new ArrayList<View>();for(var e:entries.values()){var t=e.ticket;if(!t.campaign().equals(campaign))continue;try{recheck(t);}catch(IllegalArgumentException revoked){continue;}boolean manager=repo.manages(p,campaign);if(!manager&&!t.executor().equals(p.id())&&!(e.result!=null&&resultAllowed(p,t)))continue;
            // Non-DMs never receive hidden DC or recipient metadata, even before execution.
            var safe=manager?t:new Ticket(t.id(),t.group(),campaign,t.character(),t.creator(),t.executor(),t.controller(),t.revision(),t.runtimeRevision(),t.expires(),t.source(),new Spec(t.spec().check(),t.spec().mode(),t.spec().purpose(),t.spec().visibility(),Set.of(),null),t.encounter());
            out.add(new View(safe,e.dismissed?"DISMISSED":e.result==null?"PENDING":"ACCEPTED",e.result==null?null:visibleResult(p,e)));}return List.copyOf(out);}
    public void dismiss(Principal p,UUID campaign,UUID id){prune();var e=entries.get(id);require(e!=null&&e.ticket.campaign().equals(campaign)&&repo.member(p,campaign)&&(e.ticket.executor().equals(p.id())||repo.manages(p,campaign)),"Request permission denied");recheck(e.ticket);require(e.result==null,"Accepted outcomes cannot be dismissed or rerolled");e.dismissed=true;}
}
