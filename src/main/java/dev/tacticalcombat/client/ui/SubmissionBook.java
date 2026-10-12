package dev.tacticalcombat.client.ui;

import java.util.*;
import java.nio.file.Path;
import java.io.IOException;
import com.google.gson.*;
import dev.tacticalcombat.character.AtomicJsonFile;
import dev.tacticalcombat.character.CharacterRepository;

/** Bounded retry identities, optionally persisted before any campaign-copy request is sent. */
public final class SubmissionBook {
    public record Intent(String source,String campaign,long revision,UUID token) {}
    private Map<String,Intent> intents=new HashMap<>();
    private final AtomicJsonFile file;
    public SubmissionBook() {file=null;}
    public SubmissionBook(Path path) throws IOException {file=new AtomicJsonFile(path);intents=new HashMap<>(file.read(SubmissionBook::decode,Map.of()));}
    public Intent find(String source,String campaign) {return intents.get(source+":"+campaign);}
    public Intent intent(String source,String campaign,long revision) {
        String key=source+":"+campaign;var existing=intents.get(key);
        if(existing!=null) {
            if(existing.revision()!=revision) throw new IllegalArgumentException("A submission already exists for an older source. Open known campaign copies or duplicate the library character for a new copy.");
            return existing;
        }
        if(intents.size()>=256) throw new IllegalArgumentException("Submission intent limit reached. Reconcile existing copies before starting more submissions.");
        UUID.fromString(source);UUID.fromString(campaign);if(revision<1) throw new IllegalArgumentException("Invalid source revision");
        var created=new Intent(source,campaign,revision,UUID.randomUUID());var next=new HashMap<>(intents);next.put(key,created);
        if(file!=null) {
            var root=new JsonObject();root.addProperty("schema",1);root.add("intents",new Gson().toJsonTree(new ArrayList<>(next.values())));
            try {file.write(root.toString(),SubmissionBook::decode);}catch(IOException failure) {throw new IllegalStateException("Cannot save submission retry identity. No submission was sent; check local config storage.",failure);}
        }
        intents=next;return created;
    }
    public int size() {return intents.size();}
    private static Map<String,Intent> decode(String json) {
        if(json.length()>65536) throw new IllegalArgumentException("Submission journal exceeds limit");CharacterRepository.checkDepth(json,8);var root=JsonParser.parseString(json).getAsJsonObject();
        if(root.get("schema").getAsInt()!=1) throw new UnsupportedOperationException("Unsupported submission journal");
        if(!root.keySet().equals(Set.of("schema","intents")) || root.getAsJsonArray("intents").size()>256) throw new IllegalArgumentException("Invalid submission journal");
        var result=new HashMap<String,Intent>();for(var row:root.getAsJsonArray("intents")) {
            if(!row.getAsJsonObject().keySet().equals(Set.of("source","campaign","revision","token"))) throw new IllegalArgumentException("Invalid intent fields");
            var intent=new Gson().fromJson(row,Intent.class);UUID.fromString(intent.source());UUID.fromString(intent.campaign());Objects.requireNonNull(intent.token());if(intent.revision()<1 || result.put(intent.source()+":"+intent.campaign(),intent)!=null) throw new IllegalArgumentException("Invalid/duplicate intent");
        }return result;
    }
}
