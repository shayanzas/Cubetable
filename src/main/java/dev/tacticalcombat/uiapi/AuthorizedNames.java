package dev.tacticalcombat.uiapi;

import com.google.gson.*;
import java.util.*;
import java.util.function.Function;

/** Enrich only account identity fields in an already authorized read model. Never inspect sheet text. */
public final class AuthorizedNames {
    private AuthorizedNames() {}
    private static final Set<String> IDENTITIES=Set.of("owner","controller","offered","members","dms");
    private static final Set<String> PRIVATE=Set.of("sheet","history","origin","policy","playerNames");
    public static JsonObject collect(JsonElement authorized,Function<UUID,String> resolve) {
        var names=new JsonObject();visit(authorized,names,resolve,0,new int[]{4096});return names;
    }
    private static void visit(JsonElement value,JsonObject names,Function<UUID,String> resolve,int depth,int[] remaining){
        if(depth>4||names.size()>=256||remaining[0]--<=0||value==null||value.isJsonNull())return;
        if(value.isJsonArray()){for(var child:value.getAsJsonArray())visit(child,names,resolve,depth+1,remaining);return;}
        if(!value.isJsonObject())return;
        for(var field:value.getAsJsonObject().entrySet()){
            if(PRIVATE.contains(field.getKey()))continue;
            if(IDENTITIES.contains(field.getKey())){
                var ids=field.getValue();
                if(ids.isJsonArray())for(var id:ids.getAsJsonArray())identity(id,field.getKey().equals("members"),names,resolve,remaining);
                else identity(ids,false,names,resolve,remaining);
            }else visit(field.getValue(),names,resolve,depth+1,remaining);
        }
    }
    private static void identity(JsonElement value,boolean memberRow,JsonObject names,Function<UUID,String> resolve,int[] remaining){
        if(remaining[0]--<=0||names.size()>=256||value==null||value.isJsonNull())return;
        if(memberRow&&value.isJsonObject()){
            var row=value.getAsJsonObject();
            if(!row.keySet().equals(Set.of("id","name"))||!row.get("name").isJsonPrimitive()||!row.getAsJsonPrimitive("name").isString())return;
            value=row.get("id");
        }
        if(!value.isJsonPrimitive()||!value.getAsJsonPrimitive().isString())return;
        String raw=value.getAsString();if(raw.length()!=36)return;
        UUID id;try{id=UUID.fromString(raw);}catch(IllegalArgumentException malformed){return;}
        if(!id.toString().equalsIgnoreCase(raw)||names.has(id.toString()))return;
        String name=resolve.apply(id);if(name!=null)names.addProperty(id.toString(),name.substring(0,Math.min(80,name.length())));
    }
}
