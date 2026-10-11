package dev.tacticalcombat.character;
import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Approval-to-adjudication only. No state claims that a rest began or recovered resources. */
public final class UiWorkflowStore {
    public enum RestKind { SHORT, LONG }
    public enum RestState { REQUESTED, APPROVED_FOR_ADJUDICATION, REJECTED, EXPIRED }
    public enum HpVisibility { PRIVATE, BARS, EXACT }
    public record Policy(UUID campaign,long revision,HpVisibility hp,int radius) {
        public Policy {Objects.requireNonNull(campaign);Objects.requireNonNull(hp);require(revision>=0 && radius>=1 && radius<=64,"Invalid presentation/rest policy");}
    }
    public record Rest(UUID id,UUID campaign,UUID requester,RestKind kind,List<UUID> participants,String dimension,int x,int y,int z,int radius,
            long expires,RestState state,long revision) {
        public Rest {Objects.requireNonNull(id);Objects.requireNonNull(campaign);Objects.requireNonNull(requester);Objects.requireNonNull(kind);Objects.requireNonNull(state);participants=List.copyOf(participants);
            require(!participants.isEmpty() && participants.size()<=16 && participants.contains(requester) && new HashSet<>(participants).size()==participants.size(),"Invalid rest party");
            require(dimension!=null && dimension.length()<=128 && radius>=1 && radius<=64 && expires>0 && revision>0,"Invalid rest request");}
    }
    private final AtomicJsonFile file;
    private record State(Map<UUID,Rest> rests,Map<UUID,Policy> policies) {State {rests=Map.copyOf(rests);policies=Map.copyOf(policies);}}
    private State state;
    public UiWorkflowStore(Path path) throws IOException {file=new AtomicJsonFile(path);state=file.read(UiWorkflowStore::decode,new State(Map.of(),Map.of()));}
    public Policy policy(UUID campaign) {return state.policies.getOrDefault(campaign,new Policy(campaign,0,HpVisibility.PRIVATE,16));}
    public void policy(CharacterRepository repo,CharacterRepository.Principal p,Policy next) throws IOException {
        require(repo.manages(p,next.campaign),"Only campaign DMs change presentation policy");require(policy(next.campaign).revision+1==next.revision,"Stale presentation policy revision");
        var policies=new HashMap<>(state.policies);policies.put(next.campaign,next);require(policies.size()<=64,"Policy capacity reached");save(new State(state.rests,policies));
    }
    public Rest request(CharacterRepository repo,CharacterRepository.Principal p,UUID id,UUID campaign,RestKind kind,List<UUID> participants,String dimension,int x,int y,int z,long now) throws IOException {
        require(repo.member(p,campaign),"Campaign membership required");require(participants.stream().allMatch(player->repo.member(new CharacterRepository.Principal(player,false),campaign)),"Rest participants must be members");
        var existing=state.rests.get(id);if(existing!=null) {require(existing.requester.equals(p.id()) && existing.campaign.equals(campaign) && existing.kind==kind,"Rest identity conflict");return existing;}
        require(state.rests.values().stream().noneMatch(r->r.requester.equals(p.id()) && r.state==RestState.REQUESTED && r.expires>now),"A rest request is already pending");
        var rests=new HashMap<>(state.rests);
        if(rests.size()>=128) rests.values().stream().filter(r->r.state!=RestState.REQUESTED || r.expires<=now).min(Comparator.comparingLong(Rest::expires)).ifPresent(r->rests.remove(r.id));
        require(rests.size()<128,"Rest request capacity reached");
        var r=new Rest(id,campaign,p.id(),kind,participants,dimension,x,y,z,policy(campaign).radius,now+300_000,RestState.REQUESTED,1);rests.put(id,r);save(new State(rests,state.policies));return r;
    }
    public Rest get(CharacterRepository repo,CharacterRepository.Principal p,UUID id) {
        var r=state.rests.get(id);require(r!=null && repo.member(p,r.campaign) && (repo.manages(p,r.campaign) || r.participants.contains(p.id())),"Rest permission denied");return r;
    }
    public List<Rest> list(CharacterRepository repo,CharacterRepository.Principal p,UUID campaign,int page) {
        require(repo.member(p,campaign),"Campaign permission denied");require(page>=0 && page<=16,"Invalid page");return state.rests.values().stream().filter(r->r.campaign.equals(campaign) && (repo.manages(p,campaign) || r.participants.contains(p.id())))
            .sorted(Comparator.comparing(r->r.id.toString())).skip(page*8L).limit(8).toList();
    }
    public Rest review(CharacterRepository repo,CharacterRepository.Principal p,UUID id,long revision,boolean approve,long now) throws IOException {
        var r=get(repo,p,id);require(repo.manages(p,r.campaign),"Only campaign DMs review rest requests");require(r.revision==revision,"Stale rest revision");require(r.state==RestState.REQUESTED,"Rest request already reviewed");
        var result=new Rest(r.id,r.campaign,r.requester,r.kind,r.participants,r.dimension,r.x,r.y,r.z,r.radius,r.expires,now>=r.expires?RestState.EXPIRED:approve?RestState.APPROVED_FOR_ADJUDICATION:RestState.REJECTED,revision+1);
        var rests=new HashMap<>(state.rests);rests.put(id,result);save(new State(rests,state.policies));return result;
    }
    private void save(State next) throws IOException {var o=new JsonObject();o.addProperty("schema",1);o.add("rests",SheetSections.JSON.toJsonTree(new ArrayList<>(next.rests.values())));o.add("policies",SheetSections.JSON.toJsonTree(new ArrayList<>(next.policies.values())));file.write(o.toString(),UiWorkflowStore::decode);state=next;}
    private static State decode(String json) {
        CharacterRepository.checkDepth(json,16);var o=JsonParser.parseString(json).getAsJsonObject();if(o.get("schema").getAsInt()!=1) throw new UnsupportedOperationException("Unsupported UI workflow schema");
        require(o.keySet().equals(Set.of("schema","rests","policies")) && o.getAsJsonArray("rests").size()<=128 && o.getAsJsonArray("policies").size()<=64,"Invalid workflow store");
        var rests=new HashMap<UUID,Rest>();var policies=new HashMap<UUID,Policy>();
        for(var row:o.getAsJsonArray("rests")) {var r=SheetSections.parse(row,Rest.class);require(rests.put(r.id,r)==null,"Duplicate rest request");}
        for(var row:o.getAsJsonArray("policies")) {var p=SheetSections.parse(row,Policy.class);require(policies.put(p.campaign,p)==null,"Duplicate policy");}
        return new State(rests,policies);
    }
}
