package dev.tacticalcombat.uiapi;

import com.google.gson.*;
import dev.tacticalcombat.character.CharacterRepository;
import java.lang.reflect.*;
import java.util.*;

/** Version-1 inert RPG records. No field here is consumed by the combat engine. */
public final class SheetSections {
    public static final Gson JSON=new GsonBuilder().serializeNulls().create();
    public enum Mode { GUIDED, MANUAL }
    public enum Automation { AUTOMATED, MANUAL, INFORMATIONAL, UNAVAILABLE }
    public enum Advancement { XP, MILESTONE, DM_MANAGED }
    public enum FeatureKind { CLASS, SPECIES, BACKGROUND, FEAT, CONDITION, TEMPORARY, CUSTOM }
    public record Identity(String species,String characterClass,String subclass,String background,String alignment,
            String age,String height,String biography,String personality,String ideals,String bonds,String flaws,String appearance,String portrait) {
        public Identity { for(String s:List.of(species,characterClass,subclass,background,alignment,age,height,portrait)) text(s,128); for(String s:List.of(biography,personality,ideals,bonds,flaws,appearance)) text(s,2048); }
    }
    public record Proficiencies(List<String> skills,List<String> saves,List<String> tools,List<String> languages,List<String> expertise,List<Modifier> modifiers) {
        public Proficiencies {skills=strings(skills,32);saves=strings(saves,6);tools=strings(tools,32);languages=strings(languages,32);expertise=strings(expertise,32);modifiers=entries(modifiers,32);}
    }
    public record Modifier(String target,int value,String source,Automation status) {
        public Modifier {text(target,64);range(value,-100,100);text(source,128);inert(status);}
    }
    public record Progression(int recordedLevel,long xp,Advancement mode,List<String> history,List<String> pendingChoices,List<String> classes,List<String> feats,List<String> abilityImprovements) {
        public Progression {range(recordedLevel,1,20);require(xp>=0 && xp<=1_000_000_000,"XP outside bounds");Objects.requireNonNull(mode);history=strings(history,32);pendingChoices=strings(pendingChoices,32);classes=strings(classes,20);feats=strings(feats,32);abilityImprovements=strings(abilityImprovements,20);}
    }
    public record Item(String id,String definition,String name,int quantity,boolean equipped,String slot,List<String> properties,String notes,String physicalMapping) {
        public Item {key(id);text(definition,128);text(name,80);range(quantity,0,10000);text(slot,40);properties=strings(properties,24);text(notes,1024);text(physicalMapping,128);}
    }
    public record Inventory(List<Item> items,long copper,long silver,long electrum,long gold,long platinum) {
        public Inventory {items=entries(items,64);unique(items.stream().map(Item::id).toList());for(long n:new long[]{copper,silver,electrum,gold,platinum}) require(n>=0 && n<=1_000_000_000,"Currency outside bounds");}
    }
    public record SpellDefinition(String id,String name,int level,String school,String castingTime,String range,String duration,
            boolean verbal,boolean somatic,boolean material,String materials,boolean concentration,boolean ritual,String description,String upcasting,String sourceVersion,boolean custom) {
        public SpellDefinition {key(id);text(name,80);SheetSections.range(level,0,9);for(String s:List.of(school,castingTime,range,duration,sourceVersion)) text(s,128);text(materials,512);text(description,4096);text(upcasting,1024);require(custom && id.startsWith("custom:"),"Only namespaced custom definitions are accepted; standard catalog is read-only");}
    }
    public record SpellSelection(String spell,boolean known,boolean prepared,boolean alwaysPrepared,String source,String notes) {
        public SpellSelection {key(spell);text(source,128);text(notes,512);require(!prepared || known || alwaysPrepared,"Prepared spell must be known or always prepared");}
    }
    public record Pool(String id,String name,int current,int maximum,String source,String recharge,Automation status) {
        public Pool {key(id);text(name,80);range(maximum,0,10000);range(current,0,maximum);text(source,128);text(recharge,128);inert(status);}
    }
    public record Slot(int level,int current,int maximum,String source) {
        public Slot {range(level,1,9);range(maximum,0,99);range(current,0,maximum);text(source,128);}
    }
    public record Spellbook(List<SpellDefinition> customDefinitions,List<SpellSelection> selections,List<Slot> slots,String recordedAbility,int recordedSaveDc,int recordedAttackModifier) {
        public Spellbook {customDefinitions=entries(customDefinitions,32);selections=entries(selections,64);slots=entries(slots,18);text(recordedAbility,16);range(recordedSaveDc,0,100);range(recordedAttackModifier,-100,100);
            var ids=customDefinitions.stream().map(SpellDefinition::id).toList();unique(ids);unique(selections.stream().map(SpellSelection::spell).toList());
            unique(slots.stream().map(s->s.source()+":"+s.level()).toList());
            for(var s:selections) require(ids.contains(s.spell()),"Unknown spell reference; verified standard catalog is currently empty");}
    }
    public record Feature(String id,FeatureKind kind,String name,String description,String source,Automation status,
            Long durationRounds,UUID sourceActor,Pool uses,List<String> history) {
        public Feature {key(id);Objects.requireNonNull(kind);text(name,80);text(description,2048);text(source,128);inert(status);require(durationRounds==null || durationRounds>=0 && durationRounds<=100000,"Invalid duration");history=strings(history,16);}
    }
    public record Features(List<Feature> entries,List<Pool> resources) {
        public Features {entries=SheetSections.entries(entries,64);resources=SheetSections.entries(resources,32);unique(entries.stream().map(Feature::id).toList());unique(resources.stream().map(Pool::id).toList());}
    }
    public record HitDice(int sides,int current,int maximum,String source) {
        public HitDice {require(Set.of(6,8,10,12).contains(sides),"Invalid Hit Die");range(maximum,0,20);range(current,0,maximum);text(source,128);}
    }
    public record Trackers(boolean inspiration,int deathSuccesses,int deathFailures,int exhaustion,List<HitDice> hitDice,List<Pool> resources) {
        public Trackers {range(deathSuccesses,0,3);range(deathFailures,0,3);range(exhaustion,0,6);hitDice=entries(hitDice,20);resources=entries(resources,32);unique(resources.stream().map(Pool::id).toList());}
    }
    public static final Map<String,Class<?>> TYPES=Map.of("identity",Identity.class,"proficiencies",Proficiencies.class,"progression",Progression.class,
            "inventory",Inventory.class,"spellbook",Spellbook.class,"features",Features.class,"trackers",Trackers.class);
    private SheetSections() {}
    public static Map<String,Object> defaults() {
        return Map.of("identity",new Identity("","","","","","","","","","","","","",""),
            "proficiencies",new Proficiencies(List.of(),List.of(),List.of(),List.of(),List.of(),List.of()),
            "progression",new Progression(1,0,Advancement.DM_MANAGED,List.of(),List.of(),List.of(),List.of(),List.of()),
            "inventory",new Inventory(List.of(),0,0,0,0,0),"spellbook",new Spellbook(List.of(),List.of(),List.of(),"",0,0),
            "features",new Features(List.of(),List.of()),"trackers",new Trackers(false,0,0,0,List.of(),List.of()));
    }
    public static JsonObject read(JsonObject sheet,String section) {
        require(TYPES.containsKey(section),"Unknown sheet section");
        var collections=sheet.getAsJsonObject("collections");
        if(collections==null || !collections.has("ui_"+section)) return JSON.toJsonTree(defaults().get(section)).getAsJsonObject();
        var rows=collections.getAsJsonArray("ui_"+section);require(rows.size()==1,"Section requires one versioned record");
        var wrapper=rows.get(0).getAsJsonObject();require(wrapper.keySet().equals(Set.of("version","data")),"Invalid section wrapper");
        require(wrapper.get("version").getAsInt()==1,"Unsupported sheet section version");
        return JSON.toJsonTree(parse(wrapper.get("data"),TYPES.get(section))).getAsJsonObject();
    }
    public static JsonObject update(JsonObject sheet,String section,JsonElement data) {
        require(TYPES.containsKey(section),"Unknown sheet section");var value=parse(data,TYPES.get(section));var result=sheet.deepCopy();
        if(!result.has("collections")) result.add("collections",new JsonObject());
        var wrapper=new JsonObject();wrapper.addProperty("version",1);wrapper.add("data",JSON.toJsonTree(value));var rows=new JsonArray();rows.add(wrapper);
        result.getAsJsonObject("collections").add("ui_"+section,rows);validate(result);return result;
    }
    public static void validate(JsonObject sheet) {CharacterRepository.validateSheet(sheet.toString());validateExtensions(sheet);}
    public static void validateExtensions(JsonObject sheet) {for(String section:TYPES.keySet()) read(sheet,section);}
    public static <T> T parse(JsonElement data,Class<T> type) {strict(data,type);return JSON.fromJson(data,type);}
    private static void strict(JsonElement value,Type type) {
        if(value==null || value.isJsonNull()) {require(!(type instanceof Class<?> c) || !c.isPrimitive(),"Required primitive field");return;} // Nullable optional record members; required constructors reject null.
        if(type instanceof ParameterizedType p && p.getRawType()==List.class) {require(value.isJsonArray() && value.getAsJsonArray().size()<=128,"Invalid bounded list");for(var e:value.getAsJsonArray()) strict(e,p.getActualTypeArguments()[0]);return;}
        if(type instanceof Class<?> c && c.isRecord()) {
            require(value.isJsonObject(),"Expected structured section");var fields=new HashMap<String,Type>();for(var f:c.getRecordComponents()) fields.put(f.getName(),f.getGenericType());
            require(fields.keySet().containsAll(value.getAsJsonObject().keySet()),"Unknown section field");
            // Require explicit fields, including null for optional fields, rather than accepting forged/missing primitives.
            require(value.getAsJsonObject().keySet().containsAll(fields.keySet()),"Missing section field");
            fields.forEach((name,t)->strict(value.getAsJsonObject().get(name),t));
        } else if(type==int.class || type==long.class || type==Long.class) {
            require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && value.getAsString().matches("-?[0-9]{1,18}"),"Expected bounded integer");
        } else if(type==boolean.class) require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean(),"Expected boolean");
        else require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),"Expected text");
    }
    static void inert(Automation status) {require(status==Automation.MANUAL || status==Automation.INFORMATIONAL || status==Automation.UNAVAILABLE,"This record cannot grant automated effects");}
    public static void require(boolean ok,String message) {if(!ok) throw new IllegalArgumentException(message);}
    static void range(int n,int min,int max) {require(n>=min && n<=max,"Value outside bounds");}
    static void text(String s,int max) {require(s!=null && s.length()<=max,"Text missing or too long");}
    static void key(String s) {require(s!=null && s.matches("[a-z0-9_]+:[a-z0-9_./-]{1,80}"),"Expected namespaced identifier");}
    static <T> List<T> entries(List<T> a,int limit) {require(a!=null && a.size()<=limit,"Too many entries");return List.copyOf(a);}
    static List<String> strings(List<String> a,int limit) {var b=entries(a,limit);b.forEach(s->text(s,256));return b;}
    static void unique(List<String> ids) {require(new HashSet<>(ids).size()==ids.size(),"Duplicate entry identity");}
}
