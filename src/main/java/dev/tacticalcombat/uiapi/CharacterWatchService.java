package dev.tacticalcombat.uiapi;

import dev.tacticalcombat.character.*;
import java.util.*;
import java.util.function.Function;

/** One authorized open-sheet watch per connected player; notices contain no character values. */
public final class CharacterWatchService {
    public record Notice(UUID viewer,UUID token,boolean revoked) {}
    private record Watch(UUID token,boolean library,UUID character,String stamp,boolean admin) {}
    private final CharacterRepository repo;
    private final LibraryRepository library;
    private final Map<UUID,Watch> watches=new HashMap<>();
    private final Set<UUID> dirty=new HashSet<>();
    private long campaignGeneration=-1,libraryGeneration=-1;
    public CharacterWatchService(CharacterRepository repo,LibraryRepository library){this.repo=repo;this.library=library;}
    public boolean matches(CharacterRepository r,LibraryRepository l){return repo==r&&library==l;}
    public static String campaignStamp(CharacterRepository repo,CharacterRepository.Principal who,UUID id){
        var c=repo.visible(who,id);var assignment=repo.assignment(id);
        return c.revision()+":"+c.runtimeRevision()+":"+repo.manualRevision(id)+":"+repo.campaign(c.campaign()).revision()+":"+(assignment==null?0:assignment.revision())+":"+repo.binding(id)+":"+repo.controller(id)+":"+repo.manages(who,c.campaign());
    }
    public static String libraryStamp(LibraryRepository library,CharacterRepository.Principal who,UUID id){return Long.toString(library.get(who,id).revision());}
    private String stamp(CharacterRepository.Principal who,boolean personal,UUID id){return personal?libraryStamp(library,who,id):campaignStamp(repo,who,id);}
    public void watch(CharacterRepository.Principal who,UUID token,boolean personal,UUID character,String observed){
        Objects.requireNonNull(token);Objects.requireNonNull(character);Objects.requireNonNull(observed);
        if(observed.length()>256)throw new IllegalArgumentException("Invalid watch stamp");
        stamp(who,personal,character); // Authorize now, even if the supplied stamp matches a previous view.
        watches.put(who.id(),new Watch(token,personal,character,observed,who.admin()));dirty.add(who.id());
    }
    public void close(UUID viewer,UUID token){var w=watches.get(viewer);if(w!=null&&w.token.equals(token))forget(viewer);}
    public void forget(UUID viewer){watches.remove(viewer);dirty.remove(viewer);}
    public int size(){return watches.size();}
    public List<Notice> drain(Function<UUID,CharacterRepository.Principal> connected){
        boolean changed=campaignGeneration!=repo.viewGeneration()||libraryGeneration!=library.viewGeneration();
        campaignGeneration=repo.viewGeneration();libraryGeneration=library.viewGeneration();var notices=new ArrayList<Notice>();
        for(var entry:new ArrayList<>(watches.entrySet())){
            UUID viewer=entry.getKey();Watch w=entry.getValue();var who=connected.apply(viewer);
            if(who==null){forget(viewer);continue;}
            if(!changed&&!dirty.remove(viewer)&&who.admin()==w.admin)continue;
            dirty.remove(viewer);
            try{
                String current=stamp(who,w.library,w.character); // Never cache authorization.
                if(!current.equals(w.stamp))notices.add(new Notice(viewer,w.token,false));
                watches.put(viewer,new Watch(w.token,w.library,w.character,current,who.admin()));
            }catch(IllegalArgumentException denied){forget(viewer);notices.add(new Notice(viewer,w.token,true));}
        }
        return List.copyOf(notices);
    }
}
