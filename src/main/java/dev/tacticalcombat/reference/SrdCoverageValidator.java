package dev.tacticalcombat.reference;

import com.google.gson.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Independent source inventory/evidence contract. This is static validation, never gameplay activation. */
public final class SrdCoverageValidator {
    private static final Gson JSON=new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
    private static String hash(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception error){throw new IllegalStateException(error);}}
    private static JsonElement sorted(JsonElement value){if(value.isJsonObject()){var result=new JsonObject();new TreeMap<>(value.getAsJsonObject().asMap()).forEach((key,item)->result.add(key,sorted(item)));return result;}if(value.isJsonArray()){var result=new JsonArray();value.getAsJsonArray().forEach(item->result.add(sorted(item)));return result;}return value;}
    public static int validate(SrdReferenceCatalog catalog,JsonObject evidence){
        require(evidence.get("schema").getAsInt()==1,"Unsupported coverage version");
        require(evidence.get("sourceSha256").getAsString().equals("d3f94417d2532f42a5abaec07e71a59007bf6cc46992c6458be6667f7a9f1e34"),"Wrong source corpus");int checks=2;
        var records=new HashSet<String>();for(var value:evidence.getAsJsonArray("records")){var record=value.getAsJsonObject();String id=record.get("id").getAsString();require(records.add(id),"Duplicate coverage ID");var entry=catalog.find(id);require(entry!=null&&entry.name().equals(record.get("name").getAsString())&&entry.category().equals(record.get("category").getAsString()),"Missing/inconsistent inventoried record: "+id);
            require(hash(entry.description()).equals(record.get("descriptionSha256").getAsString()),"Description differs from source evidence: "+id);
            require(hash(JSON.toJson(sorted(entry.data()))).equals(record.get("dataSha256").getAsString()),"Metadata differs from reviewed evidence: "+id);checks+=4;
            var data=entry.data();if(data.has("reference"))data=data.getAsJsonObject("reference");
            for(String key:List.of("featureReferences","spellReferences","prerequisiteReferences"))if(data.has(key))for(var ref:data.getAsJsonArray(key)){require(catalog.find(ref.getAsString())!=null,"Missing typed reference");checks++;}
            if(entry.category().equals("classes"))for(var level:data.getAsJsonArray("levels"))for(var unlock:level.getAsJsonObject().getAsJsonArray("featureUnlocks"))for(var ref:unlock.getAsJsonObject().getAsJsonArray("references")){require(catalog.find(ref.getAsString())!=null,"Missing progression feature");checks++;}
        }
        for(var value:evidence.getAsJsonArray("baselineRecords")){var baseline=value.getAsJsonObject();var entry=catalog.find(baseline.get("id").getAsString());require(entry!=null,"Missing frozen gameplay reference");var data=entry.data();data.remove("reference");require(hash(entry.description()).equals(baseline.get("descriptionSha256").getAsString())&&hash(JSON.toJson(sorted(data))).equals(baseline.get("dataSha256").getAsString()),"Existing verified data changed: "+entry.id());checks+=2;}
        require(records.size()==catalog.entries().size(),"Uninventoried catalog records");checks++;
        var retained=new HashSet<String>();for(var id:evidence.getAsJsonArray("retainedIds")){require(retained.add(id.getAsString())&&catalog.find(id.getAsString())!=null,"Missing legacy reference identity");checks++;}require(retained.size()==623,"Invalid retained baseline");checks++;
        var candidateIds=new HashSet<String>();for(var row:evidence.getAsJsonArray("candidates")){var candidate=row.getAsJsonObject();require(candidateIds.add(candidate.get("id").getAsString())&&catalog.find(candidate.get("id").getAsString())!=null,"Missing/duplicate source candidate");require(candidate.get("startLine").getAsInt()<candidate.get("endLine").getAsInt()&&candidate.get("sourceTextSha256").getAsString().matches("[0-9a-f]{64}"),"Invalid source span");checks+=2;}
        for(var field:evidence.getAsJsonObject("categories").entrySet()){long count=catalog.entries().stream().filter(e->e.category().equals(field.getKey())).count();require(count==field.getValue().getAsJsonObject().get("imported").getAsLong(),"Wrong category coverage count");checks++;}
        return checks;
    }
    private SrdCoverageValidator(){}
}
