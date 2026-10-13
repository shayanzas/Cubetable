package dev.tacticalcombat.character;
import com.google.gson.*;
import java.nio.file.Path;
import java.io.*;
import java.util.*;
/** Server-local last selections, separate from character schema and physical bindings. */
public final class SelectionStore {
    public record Selection(UUID campaign,UUID character) {}
    private final AtomicJsonFile file;
    private Map<UUID,Selection> selections;
    public SelectionStore(Path path)throws IOException {file=new AtomicJsonFile(path);selections=file.read(SelectionStore::decode,Map.of());}
    public Selection get(UUID player){return selections.get(player);}
    public void put(UUID player,UUID campaign,UUID character)throws IOException {
        var next=new LinkedHashMap<>(selections);if(campaign==null)next.remove(player);else next.put(player,new Selection(campaign,character));
        if(next.size()>4096)throw new IllegalArgumentException("Selection store capacity reached");
        var root=new JsonObject();root.addProperty("schema",1);var rows=new JsonArray();next.forEach((id,s)->{var row=new JsonObject();row.addProperty("player",id.toString());row.addProperty("campaign",s.campaign().toString());if(s.character()!=null)row.addProperty("character",s.character().toString());rows.add(row);});root.add("selections",rows);file.write(root.toString(),SelectionStore::decode);selections=Map.copyOf(next);
    }
    /** Rechecks access and control; an encounter blocks restoration without changing bindings. */
    public Selection eligible(CharacterRepository repo,CharacterRepository.Principal who,boolean encounter)throws IOException {
        var remembered=get(who.id());if(remembered==null)return null;
        boolean member;try{member=repo.member(who,remembered.campaign());}catch(IllegalArgumentException missing){member=false;}
        if(!member){put(who.id(),null,null);return null;}
        UUID character=remembered.character();if(character!=null)try{var c=repo.visible(who,character);if(!c.campaign().equals(remembered.campaign())||!(repo.controller(character).equals(who.id())||repo.manages(who,c.campaign())))throw new IllegalArgumentException("Control revoked");}catch(IllegalArgumentException revoked){put(who.id(),remembered.campaign(),null);remembered=get(who.id());}
        return encounter?null:remembered;
    }
    private static Map<UUID,Selection> decode(String json){CharacterRepository.checkDepth(json,8);var root=JsonParser.parseString(json).getAsJsonObject();int schema=root.get("schema").getAsInt();if(schema>1)throw new UnsupportedOperationException("Future selection schema");if(schema!=1)throw new IllegalArgumentException("Invalid selection schema");var rows=root.getAsJsonArray("selections");if(rows.size()>4096)throw new IllegalArgumentException("Selection capacity");var result=new LinkedHashMap<UUID,Selection>();for(var entry:rows){var row=entry.getAsJsonObject();UUID id=UUID.fromString(row.get("player").getAsString());if(result.put(id,new Selection(UUID.fromString(row.get("campaign").getAsString()),row.has("character")?UUID.fromString(row.get("character").getAsString()):null))!=null)throw new IllegalArgumentException("Duplicate selection");}return Map.copyOf(result);}
}
