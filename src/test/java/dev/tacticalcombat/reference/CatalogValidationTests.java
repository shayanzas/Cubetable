package dev.tacticalcombat.reference;

import com.google.gson.*;
import java.util.*;
import java.nio.file.*;

public final class CatalogValidationTests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    private static void rejects(Runnable task,String why){try{task.run();}catch(RuntimeException expected){count++;return;}throw new AssertionError(why);}
    private static String normalized(String text){return text.replace('’','\'').toLowerCase(Locale.ROOT);}
    public static void run(){
        var catalog=SrdReferenceCatalog.get();var root=catalog.document();var inventory=JsonParser.parseString(SrdReferenceCatalog.resource("source-inventory-v1.json")).getAsJsonObject();
        for(String category:List.of("spells","conditions","weapons","armor","equipment")){var expected=new HashSet<String>();for(var e:inventory.getAsJsonArray(category))check(expected.add(normalized(e.getAsString())),"Source inventory unique");var actual=new HashSet<String>();for(var e:catalog.entries())if(e.category().equals(category))check(actual.add(normalized(e.name())),"Catalog names unique");check(actual.equals(expected),category+" covers every inventoried source heading/table row");}
        check(inventory.getAsJsonArray("spells").size()==319,"319 printed spell headings");check(inventory.getAsJsonArray("conditions").size()==15,"15 source conditions including exhaustion");check(inventory.getAsJsonArray("weapons").size()==37,"37 printed weapons");check(inventory.getAsJsonArray("armor").size()==13,"12 armors plus shield");
        for(var entry:catalog.entries())if(entry.category().equals("spells")){for(var c:entry.data().getAsJsonArray("classes")){var printed=inventory.getAsJsonObject("classLists").getAsJsonArray(c.getAsString());check(printed.asList().stream().anyMatch(n->normalized(n.getAsString()).equals(normalized(entry.name()))),"Spell class membership occurs in printed class list");}}
        for(var list:inventory.getAsJsonObject("classLists").entrySet())for(var name:list.getValue().getAsJsonArray()){var spell=catalog.entries().stream().filter(e->e.category().equals("spells")&&normalized(e.name()).equals(normalized(name.getAsString()))).findFirst();check(spell.isPresent()&&spell.get().data().getAsJsonArray("classes").asList().contains(new JsonPrimitive(list.getKey())),"Every printed class-list entry has reciprocal spell membership");}
        var zombie=catalog.find("srd51:monsters:zombie");check(zombie.data().get("hp").getAsInt()==22&&zombie.data().get("ac").getAsInt()==8&&zombie.data().get("traits").getAsString().contains("Undead Fortitude"),"Zombie verified stat block");check(catalog.find("srd51:spells:fireball").data().get("level").getAsInt()==3,"2014 Fireball metadata");
        var changed=zombie.data();changed.addProperty("hp",999);check(zombie.data().get("hp").getAsInt()==22,"Reference data immutable to callers");
        var bad=root.deepCopy();bad.getAsJsonArray("entries").add(bad.getAsJsonArray("entries").get(0).deepCopy());rejects(()->SrdReferenceCatalog.parse(bad.toString()),"Duplicate entries rejected");
        for(String field:List.of("source","level","school","classes","range","ritual","components")){var invalid=root.deepCopy();var e=invalid.getAsJsonArray("entries").get(0).getAsJsonObject();var d=e.getAsJsonObject("data");switch(field){case "source"->e.addProperty("source","srd52");case "level"->d.addProperty("level",10);case "school"->d.addProperty("school","invented");case "classes"->d.add("classes",new JsonArray());case "range"->d.remove("range");case "ritual"->d.addProperty("ritual","yes");case "components"->d.addProperty("components",12);}rejects(()->SrdReferenceCatalog.parse(invalid.toString()),"Malformed catalog field: "+field);}
        for(String field:List.of("damageType","properties")){var invalid=root.deepCopy();var entry=invalid.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject).filter(e->e.get("category").getAsString().equals("weapons")).findFirst().orElseThrow();if(field.equals("damageType"))entry.getAsJsonObject("data").addProperty("damageType","invented");else entry.getAsJsonObject("data").getAsJsonArray("properties").add("cast-spell");rejects(()->SrdReferenceCatalog.parse(invalid.toString()),"Incorrect damage/property type rejected");}
        var future=root.deepCopy();future.addProperty("schema",2);rejects(()->SrdReferenceCatalog.parse(future.toString()),"Future catalog schema rejected");
        var missing=root.deepCopy();missing.getAsJsonArray("entries").remove(320);rejects(()->SrdReferenceCatalog.parse(missing.toString()),"Missing class reference rejected");
        System.out.println("J2.5 catalog validation checks passed: "+count);
    }
    public static void main(String[] args)throws Exception{run();var counts=new TreeMap<String,Long>();for(String category:SrdReferenceCatalog.CATEGORIES)counts.put(category,SrdReferenceCatalog.get().entries().stream().filter(e->e.category().equals(category)).count());var report=new JsonObject();report.addProperty("schema",1);report.addProperty("validatedEntries",SrdReferenceCatalog.get().entries().size());report.addProperty("checks",count);report.add("categories",new Gson().toJsonTree(counts));report.addProperty("ccEditionComparison","Pending independent retrieval; all text verified against user-provided SRD 5.1");if(args.length>0){Path path=Path.of(args[0]);Files.createDirectories(path.getParent());Files.writeString(path,new GsonBuilder().setPrettyPrinting().create().toJson(report));}System.out.println(report);}
}
