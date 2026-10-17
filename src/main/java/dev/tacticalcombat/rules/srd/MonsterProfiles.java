package dev.tacticalcombat.rules.srd;

import com.google.gson.*;
import dev.tacticalcombat.reference.SrdReferenceCatalog;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Reviewed adapters, never executable description parsing. Immutable source identity, mutable runtime resources. */
public final class MonsterProfiles {
    public static final int VERSION=1;
    public record Profile(String id,String name,int hp,int ac,int speed,String size,List<Integer> abilities,
                          List<String> weapons,List<String> immunities,List<String> vulnerabilities,String limitations) {}
    public static final List<Profile> ALL=List.of(
        new Profile("srd51:monsters:zombie","Zombie",22,8,20,"medium",List.of(13,6,16,3,6,5),List.of("slam"),List.of("poison"),List.of(),"Partially supported: Slam and poison damage immunity supported. Undead Fortitude and poisoned condition immunity need DM adjudication; no automatic survival at 0 HP."),
        new Profile("srd51:monsters:skeleton","Skeleton",13,13,30,"medium",List.of(10,14,15,6,8,5),List.of("shortsword","shortbow"),List.of("poison"),List.of("bludgeoning"),"Partially supported: Shortsword, Shortbow, poison damage immunity and bludgeoning vulnerability supported. Exhaustion/poisoned condition immunity and senses need DM adjudication. Starts with 20 explicitly provisioned virtual arrows."),
        new Profile("srd51:monsters:goblin","Goblin",7,15,30,"small",List.of(8,14,10,10,8,8),List.of("scimitar"),List.of(),List.of(),"Partially supported: Scimitar supported. Nimble Escape needs DM adjudication. Shortbow deferred: shield removal/loadout actions are not implemented. Senses remain reference only."));
    public static Profile get(String id){return ALL.stream().filter(p->p.id.equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unsupported monster reference"));}
    public static void verify(Profile p){var entry=SrdReferenceCatalog.get().find(p.id);require(entry!=null&&entry.verification().equals("PDF_TEXT_VERIFIED"),"Verified monster source unavailable");var data=entry.data();require(data.get("hp").getAsInt()==p.hp&&data.get("ac").getAsInt()==p.ac&&data.get("size").getAsString().equalsIgnoreCase(p.size)&&data.get("speed").getAsString().equals(p.speed+" ft."),"Monster catalog/adapter mismatch");int i=0;for(String key:List.of("str","dex","con","int","wis","cha"))require(data.getAsJsonObject("abilities").get(key).getAsInt()==p.abilities.get(i++),"Monster ability mismatch");}
    public static JsonObject sheet(String source,UUID operation,UUID actor){var p=get(source);verify(p);var sheet=SrdAuthoring.template();sheet.getAsJsonObject("text").addProperty("name",p.name+" NPC");var data=SrdAuthoring.data(sheet);int i=0;for(String key:List.of("str","dex","con","int","wis","cha"))data.getAsJsonObject("abilities").addProperty(key,p.abilities.get(i++));
        data.addProperty("hp",p.hp);data.addProperty("hp_max",p.hp);data.addProperty("speed_feet",p.speed);data.addProperty("size",p.size);data.addProperty("ac_bonus",p.ac-(10+Math.floorDiv(p.abilities.get(1)-10,2)));
        for(String key:List.of("weapons","weapon_proficiencies"))data.add(key,new Gson().toJsonTree(p.weapons));data.add("immunities",new Gson().toJsonTree(p.immunities));data.add("vulnerabilities",new Gson().toJsonTree(p.vulnerabilities));data.addProperty("arrows",p.name.equals("Skeleton")?20:0);data.addProperty("bolts",0);data.addProperty("daggers",0);
        var identity=new JsonObject();identity.addProperty("version",VERSION);identity.addProperty("kind","SRD_MONSTER");identity.addProperty("source",p.id);identity.addProperty("catalog",1);identity.addProperty("operation",operation.toString());identity.addProperty("preparedActor",actor.toString());var rows=new JsonArray();rows.add(identity);sheet.getAsJsonObject("collections").add("srd_monster",rows);return sheet;
    }
    public static JsonObject identity(JsonObject sheet){var c=sheet.getAsJsonObject("collections");if(c==null||!c.has("srd_monster"))return null;var rows=c.getAsJsonArray("srd_monster");require(rows.size()==1,"Invalid monster identity");var id=rows.get(0).getAsJsonObject();require(id.keySet().equals(Set.of("version","kind","source","catalog","operation","preparedActor"))&&id.get("version").getAsInt()==1&&id.get("catalog").getAsInt()==1&&id.get("kind").getAsString().equals("SRD_MONSTER"),"Unsupported monster identity version");get(id.get("source").getAsString());UUID.fromString(id.get("operation").getAsString());UUID.fromString(id.get("preparedActor").getAsString());return id;}
    public static void validate(JsonObject sheet){var id=identity(sheet);if(id==null)return;var expected=SrdAuthoring.data(sheet(id.get("source").getAsString(),UUID.fromString(id.get("operation").getAsString()),UUID.fromString(id.get("preparedActor").getAsString())));var actual=SrdAuthoring.data(sheet);for(var field:expected.entrySet())if(!Set.of("hp","temporary_hp","arrows","bolts","daggers").contains(field.getKey()))require(field.getValue().equals(actual.get(field.getKey())),"Monster source statistics are immutable: "+field.getKey());}
    private MonsterProfiles(){}
}
