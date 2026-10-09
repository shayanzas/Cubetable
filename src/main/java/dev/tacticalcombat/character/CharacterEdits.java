package dev.tacticalcombat.character;

import com.google.gson.*;
import java.util.*;

/** Explicit field changes, shared by server validation and the client reconciliation model. No expressions run here. */
public final class CharacterEdits {
    private CharacterEdits() {}
    public static final Set<String> GROUPS = Set.of("text", "values", "collections");
    public static Map<String, JsonElement> fields(JsonObject sheet) {
        var result = new TreeMap<String, JsonElement>();
        for (var entry : sheet.entrySet()) {
            if (GROUPS.contains(entry.getKey())) {
                for (var field : entry.getValue().getAsJsonObject().entrySet())
                    result.put(entry.getKey()+"."+field.getKey(), field.getValue());
            } else result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }
    public static JsonObject diff(JsonObject before, JsonObject after) {
        var a=fields(before); var b=fields(after); var keys=new TreeSet<>(a.keySet()); keys.addAll(b.keySet());
        var changes=new JsonObject();
        for(String key:keys) if(!Objects.equals(a.get(key),b.get(key)))
            changes.add(key,b.getOrDefault(key,JsonNull.INSTANCE).deepCopy());
        return changes;
    }
    public static JsonObject apply(JsonObject sheet, JsonObject changes) {
        JsonObject result=sheet.deepCopy();
        if(changes.size()>256) throw new IllegalArgumentException("Too many changed fields");
        for(var change:changes.entrySet()) {
            String key=change.getKey();
            if(!validField(key) || key.equals("format")) throw new IllegalArgumentException("Unsupported edit field: "+key);
            String[] parts=key.split("\\.",2); JsonObject target=result;
            if(parts.length==2) {
                if(!result.has(parts[0])) result.add(parts[0],new JsonObject());
                target=result.getAsJsonObject(parts[0]);
            }
            String leaf=parts[parts.length-1];
            if(change.getValue().isJsonNull()) target.remove(leaf); else target.add(leaf,change.getValue().deepCopy());
        }
        return result;
    }
    public static boolean validField(String field) {
        return Set.of("kind","theme","ui","format").contains(field)
            || field.matches("(text|values|collections)\\.[a-z0-9_:-]{1,64}");
    }
    public static boolean validRule(String rule) {
        return validField(rule) && !rule.equals("format") || Set.of("text.*","values.*","collections.*").contains(rule);
    }
}
