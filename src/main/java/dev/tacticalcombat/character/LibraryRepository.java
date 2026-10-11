package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Private, independent templates. Campaign submission copies bytes into the existing campaign transaction. */
public final class LibraryRepository {
    public enum Stage { DRAFT, COMPLETE }
    public record Draft(UUID id,UUID owner,String profile,SheetSections.Mode mode,Stage stage,long revision,String sheet) {}
    public record Validation(String section,String code,String message) {}
    private final AtomicJsonFile file;
    private Map<UUID,Draft> drafts;
    public LibraryRepository(Path path) throws IOException {this(new AtomicJsonFile(path));}
    LibraryRepository(AtomicJsonFile file) throws IOException {this.file=file;drafts=file.read(LibraryRepository::decode,Map.of());}
    public boolean recoveredFromBackup() {return file.recoveredFromBackup();}
    public Draft get(CharacterRepository.Principal p,UUID id) {
        var d=drafts.get(id);require(d!=null && d.owner.equals(p.id()),"Library ownership permission denied");return d;
    }
    public List<Draft> list(CharacterRepository.Principal p,int page) {
        require(page>=0 && page<=128,"Invalid page");return drafts.values().stream().filter(d->d.owner.equals(p.id()))
            .sorted(Comparator.comparing(d->d.id.toString())).skip(page*8L).limit(8).toList();
    }
    public Draft create(CharacterRepository.Principal p,String profile,SheetSections.Mode mode,String sheet) throws IOException {
        require(drafts.size()<1024 && drafts.values().stream().filter(d->d.owner.equals(p.id())).count()<32,"Library capacity reached");
        RulesProfiles.get(profile);Objects.requireNonNull(mode);SheetSections.validate(JsonParser.parseString(sheet).getAsJsonObject());
        var d=new Draft(UUID.randomUUID(),p.id(),profile,mode,Stage.DRAFT,1,sheet);save(d);return d;
    }
    public Draft duplicate(CharacterRepository.Principal p,UUID id,long revision) throws IOException {
        var d=expected(p,id,revision);return create(p,d.profile,d.mode,d.sheet);
    }
    public Draft update(CharacterRepository.Principal p,UUID id,long revision,String section,JsonObject data) throws IOException {
        var d=expected(p,id,revision);var sheet=JsonParser.parseString(d.sheet).getAsJsonObject();
        if(section.equals("basics")) {
            require(data.keySet().equals(Set.of("name")),"Basics accepts name only");String name=data.get("name").getAsString();require(name.length()<=80,"Name too long");
            if(!sheet.has("text")) sheet.add("text",new JsonObject());sheet.getAsJsonObject("text").addProperty("name",name);
        } else if(section.equals("combat")) {
            require(d.profile.equals(RulesProfiles.SRD),"Combat section requires SRD profile");
            var rows=new JsonArray();rows.add(data.deepCopy());sheet.getAsJsonObject("collections").add("srd_combat",rows);
            SrdCharacterCodec.parseSheet(sheet.toString());
        } else sheet=SheetSections.update(sheet,section,data);
        SheetSections.validate(sheet);
        var next=new Draft(id,d.owner,d.profile,d.mode,Stage.DRAFT,revision+1,sheet.toString());save(next);return next;
    }
    public List<Validation> validate(CharacterRepository.Principal p,UUID id) {return validate(get(p,id));}
    private static List<Validation> validate(Draft d) {
        var errors=new ArrayList<Validation>();var sheet=JsonParser.parseString(d.sheet).getAsJsonObject();
        if(!sheet.has("text") || !sheet.getAsJsonObject("text").has("name") || sheet.getAsJsonObject("text").get("name").getAsString().isBlank())
            errors.add(new Validation("basics","NAME_REQUIRED","Enter a character name"));
        if(d.profile.equals(RulesProfiles.SRD)) try {SrdCharacterCodec.parseSheet(d.sheet);} catch(RuntimeException e) {errors.add(new Validation("combat","INVALID_COMBAT",e.getMessage()));}
        return List.copyOf(errors);
    }
    public Draft finalizeDraft(CharacterRepository.Principal p,UUID id,long revision) throws IOException {
        var d=expected(p,id,revision);require(validate(d).isEmpty(),"Character has validation errors; review before finalizing");
        var next=new Draft(id,d.owner,d.profile,d.mode,Stage.COMPLETE,revision+1,d.sheet);save(next);return next;
    }
    public Draft expected(CharacterRepository.Principal p,UUID id,long revision) {
        var d=get(p,id);require(d.revision==revision,"Stale library revision; reload and reconcile");require(revision<Long.MAX_VALUE,"Revision exhausted");return d;
    }
    private void save(Draft d) throws IOException {
        var next=new HashMap<>(drafts);next.put(d.id,d);var root=new JsonObject();root.addProperty("schema",1);root.add("drafts",SheetSections.JSON.toJsonTree(next.values()));
        file.write(root.toString(),LibraryRepository::decode);drafts=Map.copyOf(next);
    }
    private static Map<UUID,Draft> decode(String json) {
        CharacterRepository.checkDepth(json,24);var root=JsonParser.parseString(json).getAsJsonObject();
        if(root.get("schema").getAsInt()!=1) throw new UnsupportedOperationException("Unsupported library schema; refusing rollback");
        require(root.keySet().equals(Set.of("schema","drafts")),"Unknown library field");var rows=root.getAsJsonArray("drafts");require(rows.size()<=1024,"Library capacity exceeded");
        var result=new HashMap<UUID,Draft>();var owners=new HashMap<UUID,Integer>();
        for(var row:rows) {
            var d=SheetSections.parse(row,Draft.class);Objects.requireNonNull(d.id);Objects.requireNonNull(d.owner);Objects.requireNonNull(d.mode);Objects.requireNonNull(d.stage);
            require(d.revision>0,"Invalid revision");RulesProfiles.get(d.profile);SheetSections.validate(JsonParser.parseString(d.sheet).getAsJsonObject());
            require(d.stage!=Stage.COMPLETE || validate(d).isEmpty(),"Invalid finalized draft");require(result.put(d.id,d)==null,"Duplicate library identity");require(owners.merge(d.owner,1,Integer::sum)<=32,"Owner library capacity exceeded");
        }
        return Map.copyOf(result);
    }
}
