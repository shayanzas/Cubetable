package dev.tacticalcombat.character;

import com.google.gson.*;
import java.util.*;

/** Client-side three-way reconciliation, independent of Minecraft so dirty-state behavior can be tested. */
public final class CharacterWorkspace {
    private JsonObject base, draft, latest;
    public void receive(JsonObject snapshot) {
        latest=snapshot.deepCopy();
        if(base==null || !base.get("id").equals(snapshot.get("id"))) useLatest();
        // Refresh deliberately never overwrites an existing draft, including unsaved edits.
    }
    public boolean loaded() { return latest!=null; }
    public JsonObject latest() { return latest.deepCopy(); }
    public JsonObject draft() { return draft.deepCopy(); }
    public JsonObject changes() { return CharacterEdits.diff(base.getAsJsonObject("sheet"),draft); }
    public boolean dirty() { return loaded() && changes().size()>0; }
    public void stage(String field,JsonElement value) {
        var changes=new JsonObject(); changes.add(field,value);
        draft=CharacterEdits.apply(draft,changes);
    }
    public void importLocal(JsonObject local) {
        if(!local.get("format").equals(base.getAsJsonObject("sheet").get("format"))) throw new IllegalArgumentException("Local and server formats differ");
        var patch=new JsonObject();
        CharacterEdits.fields(local).forEach((key,value)->{if(!key.equals("format")) patch.add(key,value.deepCopy());});
        draft=CharacterEdits.apply(draft,patch);
    }
    public void useLatest() { base=latest.deepCopy(); draft=base.getAsJsonObject("sheet").deepCopy(); }
    public Set<String> conflicts() {
        var mine=changes(); var theirs=CharacterEdits.diff(base.getAsJsonObject("sheet"),latest.getAsJsonObject("sheet"));
        var conflicts=new TreeSet<String>();
        for(String key:mine.keySet()) if(theirs.has(key) && !mine.get(key).equals(theirs.get(key))) conflicts.add(key);
        return conflicts;
    }
    /** Explicit user reconciliation only. The normal path refuses overlapping changes. */
    public void rebase(boolean explicitlyKeepMine) {
        if(!explicitlyKeepMine && !conflicts().isEmpty()) throw new IllegalArgumentException("Conflicting fields: "+String.join(", ",conflicts()));
        var merged=CharacterEdits.apply(latest.getAsJsonObject("sheet"),changes());
        base=latest.deepCopy(); draft=merged;
    }
    public JsonObject request() {
        var args=new JsonObject(); args.add("character",base.get("id")); args.add("revision",base.get("revision"));
        args.add("runtimeRevision",base.get("runtimeRevision")); args.add("changes",changes()); return args;
    }
}
