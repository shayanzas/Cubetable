package dev.tacticalcombat.reference;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Static reference only. Never participates in character writes, grants or action execution. */
public final class SrdReferenceCatalog {
    public static final Set<String> CATEGORIES=Set.of("races","subraces","classes","subclasses","features","weapons","armor","equipment","spells","spell-lists","monsters","conditions","combat","movement","rests","advancement","feats","magic-items");
    public record Entry(String id,String category,String name,String source,List<Integer> pages,String verification,String description,JsonObject data,List<String> related){
        public Entry{pages=List.copyOf(pages);related=List.copyOf(related);data=data.deepCopy();}
        @Override public JsonObject data(){return data.deepCopy();}
        public String detail(){var out=new StringBuilder("REFERENCE ONLY · SRD 5.1 (2014)\nSource pages: "+pages+" · "+verification+"\n\n");for(var field:data.entrySet())out.append(field.getKey()).append(": ").append(field.getValue().isJsonPrimitive()?field.getValue().getAsString():field.getValue().toString()).append('\n');return out.append("\n").append(description).append("\n\nCatalog presence does not activate spells, monster actions, grants or conditions.").toString();}
    }
    private static SrdReferenceCatalog loaded;
    private final List<Entry> entries;private final Map<String,Entry> index;private final JsonObject root;
    private SrdReferenceCatalog(JsonObject root){this.root=root.deepCopy();require(root.get("schema").isJsonPrimitive()&&root.get("schema").getAsJsonPrimitive().isNumber()&&root.get("schema").getAsBigDecimal().intValueExact()==1,"Unsupported catalog schema");var sources=new HashSet<String>();for(var value:root.getAsJsonArray("sources")){var s=value.getAsJsonObject();require(sources.add(text(s,"id"))&&text(s,"license").equals("CC-BY-4.0")&&text(s,"title").equals("System Reference Document 5.1 (2014)")&&!text(s,"attribution").isBlank()&&text(s,"referenceSha256").matches("[a-f0-9]{64}"),"Invalid catalog provenance");}
        var rows=new ArrayList<Entry>();var map=new LinkedHashMap<String,Entry>();for(var value:root.getAsJsonArray("entries")){var object=value.getAsJsonObject();for(String key:List.of("id","category","name","source","verification","description"))require(object.has(key)&&object.get(key).isJsonPrimitive()&&object.get(key).getAsJsonPrimitive().isString(),"Invalid catalog string field: "+key);for(var page:object.getAsJsonArray("pages"))require(page.isJsonPrimitive()&&page.getAsJsonPrimitive().isNumber()&&page.getAsBigDecimal().intValueExact()>0,"Invalid page type");var entry=new Gson().fromJson(object,Entry.class);require(entry.id().matches("srd51:[a-z-]+:[a-z0-9-]+")&&CATEGORIES.contains(entry.category())&&entry.id().startsWith("srd51:"+entry.category()+":")&&!entry.name().isBlank()&&!entry.description().isBlank()&&sources.contains(entry.source())&&!entry.pages().isEmpty()&&entry.pages().stream().allMatch(p->p>2&&p<=403)&&entry.verification().equals("PDF_TEXT_VERIFIED"),"Invalid catalog entry");require(map.put(entry.id(),entry)==null,"Duplicate catalog ID");require(rows.stream().noneMatch(e->e.category().equals(entry.category())&&e.name().equalsIgnoreCase(entry.name())),"Duplicate catalog name");validateData(entry);rows.add(entry);}
        for(var entry:rows)for(String related:entry.related())require(map.containsKey(related),"Missing reference: "+related);entries=List.copyOf(rows);index=Map.copyOf(map);
    }
    private static void validateData(Entry e){var d=e.data();switch(e.category()){
        case "spells"->{require(d.get("level").isJsonPrimitive()&&d.get("level").getAsJsonPrimitive().isNumber(),"Invalid level type");int level=d.get("level").getAsBigDecimal().intValueExact();require(level>=0&&level<=9,"Invalid spell level");require(Set.of("abjuration","conjuration","divination","enchantment","evocation","illusion","necromancy","transmutation").contains(text(d,"school")),"Invalid school");for(String key:List.of("castingTime","range","components","duration"))require(d.get(key).isJsonPrimitive()&&d.get(key).getAsJsonPrimitive().isString()&&!text(d,key).isBlank(),"Missing/invalid spell field: "+key);for(String key:List.of("ritual","concentration"))require(d.get(key).getAsJsonPrimitive().isBoolean(),"Invalid boolean");require(d.getAsJsonArray("classes").size()>0,"Missing spell list");var names=new HashSet<String>();for(var c:d.getAsJsonArray("classes"))require(names.add(c.getAsString())&&e.related().contains("srd51:classes:"+c.getAsString()),"Inconsistent spell classes");require(e.related().size()==names.size(),"Unmatched spell class references");}
        case "weapons"->{require(Set.of("bludgeoning","piercing","slashing","none").contains(text(d,"damageType")),"Invalid weapon damage type");require(text(d,"damageDice").matches("[1-9][0-9]*d[1-9][0-9]*|1|none"),"Invalid damage dice");for(var property:d.getAsJsonArray("properties"))require(Set.of("ammunition","finesse","heavy","light","loading","reach","special","thrown","two-handed","versatile").contains(property.getAsString()),"Invalid weapon property");require(d.get("rangeNormal").getAsInt()>=0&&d.get("rangeLong").getAsInt()>=d.get("rangeNormal").getAsInt(),"Invalid weapon range");}
        case "armor"->require(Set.of("light","medium","heavy","shield").contains(text(d,"category"))&&d.get("ac").getAsInt()>0,"Invalid armor metadata");
        case "spell-lists"->{require(e.related().contains(text(d,"characterClass")),"Missing spell-list class");for(var spell:d.getAsJsonArray("spells"))require(e.related().contains(spell.getAsString()),"Invalid spell-list reference");}
        case "features","subclasses"->require(d.get("minimumLevel").isJsonPrimitive()&&d.get("minimumLevel").getAsJsonPrimitive().isNumber()&&d.get("minimumLevel").getAsBigDecimal().intValueExact()>=1&&d.get("minimumLevel").getAsInt()<=20,"Invalid level prerequisite");
        case "feats"->require(Set.of("str","dex","con","int","wis","cha").contains(text(d,"prerequisiteAbility"))&&d.get("minimumScore").getAsInt()>=1&&d.get("minimumScore").getAsInt()<=30,"Invalid feat prerequisite");
        case "races"->require(Set.of("Small","Medium").contains(text(d,"size"))&&d.get("speedFeet").getAsInt()>0,"Invalid race reference metadata");
        case "conditions"->require(d.has("automated")&&!d.get("automated").getAsBoolean(),"Reference condition cannot execute");
        case "classes"->require(Set.of(6,8,10,12).contains(d.get("hitDie").getAsInt()),"Invalid class Hit Die");
        case "monsters"->{require(d.get("hp").getAsInt()>0&&d.get("ac").getAsInt()>0,"Invalid monster health/AC");for(String k:List.of("str","dex","con","int","wis","cha"))require(d.getAsJsonObject("abilities").get(k).getAsInt()>=1&&d.getAsJsonObject("abilities").get(k).getAsInt()<=30,"Invalid monster ability");for(String key:List.of("size","type","speed","saves","skills","senses","languages","resistances","immunities","challenge","traits","actions","spellcasting"))require(d.has(key),"Missing monster field "+key);}
        default->{require(d.size()>0,"Missing structured reference data");}
    }}
    public static SrdReferenceCatalog parse(String json){return new SrdReferenceCatalog(JsonParser.parseString(json).getAsJsonObject());}
    public static synchronized SrdReferenceCatalog get(){if(loaded==null)loaded=parse(resource("catalog-v1.json"));return loaded;}
    public static String resource(String name){try(var stream=SrdReferenceCatalog.class.getResourceAsStream("/data/tacticalcombat/reference/"+name)){if(stream==null)throw new IllegalStateException("Reference catalog missing");return new String(stream.readAllBytes(),StandardCharsets.UTF_8);}catch(IOException failure){throw new IllegalStateException("Reference catalog unreadable",failure);}}
    public List<Entry> entries(){return entries;}
    public Entry find(String id){return index.get(id);}
    public JsonObject document(){return root.deepCopy();}
    private static String text(JsonObject o,String key){return o.get(key).getAsString();}
    private static void require(boolean condition,String why){if(!condition)throw new IllegalArgumentException(why);}
}
