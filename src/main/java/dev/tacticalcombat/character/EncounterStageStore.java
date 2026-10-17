package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.MonsterProfiles;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Independent atomic template store. Templates never claim entities or resume encounters. */
public final class EncounterStageStore {
    public record Slot(UUID id,String role,int x,int z,double y,String monster,UUID character){
        public Slot {Objects.requireNonNull(id);text(role,64);coordinate(x,z,y);if(monster!=null)MonsterProfiles.get(monster);}
    }
    public record Stage(UUID id,long revision,UUID campaign,UUID creator,String name,String description,String dimension,
                        int x,int z,double y,List<Slot> slots,int catalogVersion,String rules,long created,long updated){
        public Stage {Objects.requireNonNull(id);Objects.requireNonNull(campaign);Objects.requireNonNull(creator);require(revision>0&&created>=0&&updated>=created,"Invalid stage metadata");text(name,64);require(description!=null&&description.length()<=512,"Invalid description");require(dimension!=null&&dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")&&dimension.length()<=128,"Invalid dimension");coordinate(x,z,y);require(catalogVersion==1&&"SRD5.1-2014".equals(rules),"Unsupported stage provenance");slots=List.copyOf(slots);require(slots.size()<=16,"Stage limit: 16 slots");var ids=new HashSet<UUID>();var positions=new HashSet<String>();for(var s:slots){require(ids.add(s.id),"Duplicate placement slot");require(positions.add(s.x+":"+s.z+":"+s.y),"Duplicate placement position");require(Math.abs((long)s.x-x)<=16&&Math.abs((long)s.z-z)<=16&&Math.abs(s.y-y)<=8,"Slot outside bounded stage area");}}
    }
    private record State(int schema,List<Stage> stages,Set<UUID> deleted){public State{require(schema==1,"Unsupported stage version");stages=List.copyOf(stages);deleted=Set.copyOf(deleted);require(stages.size()<=256&&deleted.size()<=2048,"Stage storage limit");var seen=new HashSet<UUID>();for(var stage:stages)require(seen.add(stage.id)&&!deleted.contains(stage.id),"Duplicate or deleted stage");}}
    private static final Gson JSON=new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private final AtomicJsonFile file;private State state;
    public EncounterStageStore(Path path)throws IOException{this(new AtomicJsonFile(path));}
    EncounterStageStore(AtomicJsonFile file)throws IOException{this.file=file;state=file.read(EncounterStageStore::decode,new State(1,List.of(),Set.of()));}
    public boolean recoveredFromBackup(){return file.recoveredFromBackup();}
    public List<Stage> list(CharacterRepository repo,CharacterRepository.Principal who,UUID campaign){authorize(repo,who,campaign);return state.stages.stream().filter(s->s.campaign.equals(campaign)).sorted(Comparator.comparing(Stage::name).thenComparing(s->s.id.toString())).toList();}
    public Stage get(CharacterRepository repo,CharacterRepository.Principal who,UUID id){var s=state.stages.stream().filter(v->v.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Stage unavailable"));authorize(repo,who,s.campaign);return s;}
    public Stage create(CharacterRepository repo,CharacterRepository.Principal who,Stage candidate)throws IOException{
        authorize(repo,who,candidate.campaign);require(candidate.creator.equals(who.id())&&candidate.revision==1&&candidate.slots.isEmpty(),"Invalid initial stage");
        for(var existing:state.stages)if(existing.id.equals(candidate.id)){require(existing.campaign.equals(candidate.campaign)&&existing.creator.equals(who.id())&&existing.name.equals(candidate.name)&&existing.description.equals(candidate.description)&&existing.dimension.equals(candidate.dimension)&&existing.x==candidate.x&&existing.z==candidate.z&&existing.y==candidate.y,"Stage ID reused with different parameters");return existing;}
        require(!state.deleted.contains(candidate.id),"Deleted stage ID cannot be reused");require(list(repo,who,candidate.campaign).size()<64,"Campaign stage limit");var all=new ArrayList<>(state.stages);all.add(candidate);commit(new State(1,all,state.deleted));return candidate;
    }
    public Stage replace(CharacterRepository repo,CharacterRepository.Principal who,UUID id,long expected,String name,String description,List<Slot> slots,long now)throws IOException{var old=get(repo,who,id);require(old.revision==expected,"Stale stage revision");var next=new Stage(id,expected+1,old.campaign,old.creator,name,description,old.dimension,old.x,old.z,old.y,slots,1,old.rules,old.created,Math.max(now,old.updated));var all=new ArrayList<>(state.stages);all.set(all.indexOf(old),next);commit(new State(1,all,state.deleted));return next;}
    public void delete(CharacterRepository repo,CharacterRepository.Principal who,UUID id,long revision)throws IOException{var old=get(repo,who,id);require(old.revision==revision,"Stale stage revision");var all=new ArrayList<>(state.stages);all.remove(old);var deleted=new HashSet<>(state.deleted);deleted.add(id);commit(new State(1,all,deleted));}
    private void commit(State next)throws IOException{file.write(JSON.toJson(next),EncounterStageStore::decode);state=next;}
    private static State decode(String json){CharacterRepository.checkDepth(json,16);var root=JsonParser.parseString(json).getAsJsonObject();if(root.get("schema").getAsBigDecimal().compareTo(java.math.BigDecimal.ONE)!=0)throw new UnsupportedOperationException("Unsupported future stage schema");if(!root.keySet().equals(Set.of("schema","stages","deleted")))throw new UnsupportedOperationException("Unknown stage storage fields");for(var value:root.getAsJsonArray("stages")){var row=value.getAsJsonObject();if(!row.keySet().equals(Set.of("id","revision","campaign","creator","name","description","dimension","x","z","y","slots","catalogVersion","rules","created","updated")))throw new UnsupportedOperationException("Unknown stage fields");for(var slot:row.getAsJsonArray("slots"))if(!slot.getAsJsonObject().keySet().equals(Set.of("id","role","x","z","y","monster","character")))throw new UnsupportedOperationException("Unknown slot fields");}return JSON.fromJson(root,State.class);}
    private static void authorize(CharacterRepository repo,CharacterRepository.Principal who,UUID campaign){require(repo.manages(who,campaign),"Only current campaign DMs manage stages");}
    private static void coordinate(int x,int z,double y){require(Math.abs((long)x)<=29999968&&Math.abs((long)z)<=29999968&&Double.isFinite(y)&&Math.abs(y)<=4096,"Invalid stage coordinate");}
    private static void text(String s,int max){require(s!=null&&!s.isBlank()&&s.length()<=max,"Invalid stage name/role");}
}
