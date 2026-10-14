package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;
import dev.tacticalcombat.rules.srd.SrdEquipment;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Stable identities drive edits; names/categories are display data, never record keys. */
public final class InventoryModel {
    public enum Category { ALL, WEAPONS, ARMOR, OTHER }
    public record Entry(String id,String label,String category,int quantity,boolean equipped,JsonObject item) {}
    private InventoryModel() {}
    public static Category category(JsonObject item){String name=text(item,"name").toUpperCase(Locale.ROOT).replace(' ','_'),definition=text(item,"definition");if(definition.startsWith("srd:weapon:")||Arrays.stream(SrdEquipment.Weapon.values()).anyMatch(v->v.name().equals(name)&&v!=SrdEquipment.Weapon.UNARMED))return Category.WEAPONS;if(definition.startsWith("srd:armor:")||Arrays.stream(SrdEquipment.Armor.values()).anyMatch(v->v.name().equals(name)&&v!=SrdEquipment.Armor.NONE))return Category.ARMOR;return Category.OTHER;}
    public static List<Entry> entries(JsonObject inventory,Category filter,String query){var counts=new HashMap<String,Integer>();for(var raw:array(inventory,"items")){String name=text(raw.getAsJsonObject(),"name");counts.merge(name,1,Integer::sum);}var ordinals=new HashMap<String,Integer>();var out=new ArrayList<Entry>();for(var raw:array(inventory,"items")){var item=raw.getAsJsonObject();String name=text(item,"name");int ordinal=ordinals.merge(name,1,Integer::sum);var category=category(item);String label=name+(counts.get(name)>1?" · item "+ordinal:"");if((filter==Category.ALL||filter==category)&&(name+" "+text(item,"notes")+" "+text(item,"slot")+" "+category.name()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))out.add(new Entry(text(item,"id"),label,category.name(),item.get("quantity").getAsInt(),flag(item,"equipped"),item.deepCopy()));}out.sort(Comparator.comparing((Entry e)->!e.equipped()).thenComparing(Entry::label).thenComparing(Entry::id));return List.copyOf(out);}
    public static JsonObject find(JsonObject inventory,String id){for(var raw:array(inventory,"items")){var item=raw.getAsJsonObject();if(text(item,"id").equals(id))return item.deepCopy();}return null;}
    public static JsonObject upsert(JsonObject inventory,JsonObject item){var out=inventory.deepCopy();var rows=out.getAsJsonArray("items");String id=text(item,"id");if(id.isBlank())throw new IllegalArgumentException("Item identity missing");for(int i=0;i<rows.size();i++)if(text(rows.get(i).getAsJsonObject(),"id").equals(id)){rows.set(i,item.deepCopy());return out;}rows.add(item.deepCopy());return out;}
    public static JsonObject quantity(JsonObject inventory,String id,int delta){var item=find(inventory,id);if(item==null)throw new IllegalArgumentException("This item was removed; refresh your inventory.");item.addProperty("quantity",Math.clamp(item.get("quantity").getAsInt()+delta,0,10000));return upsert(inventory,item);}
    public static JsonObject equipped(JsonObject inventory,String id){var item=find(inventory,id);if(item==null)throw new IllegalArgumentException("This item was removed; refresh your inventory.");item.addProperty("equipped",!flag(item,"equipped"));return upsert(inventory,item);}
    public static JsonObject catalogAddition(JsonObject inventory,JsonObject blueprint,boolean separate){
        if(find(inventory,text(blueprint,"id"))!=null)throw new IllegalArgumentException("This add intent already exists; inspect inventory before adding again.");
        if(!separate){var match=array(inventory,"items").asList().stream().map(JsonElement::getAsJsonObject).filter(item->!flag(item,"equipped")&&List.of("definition","name","slot","properties","notes","physicalMapping").stream().allMatch(key->Objects.equals(item.get(key),blueprint.get(key)))).sorted(Comparator.comparing(item->text(item,"id"))).findFirst();if(match.isPresent()){var item=match.get().deepCopy();long quantity=(long)item.get("quantity").getAsInt()+blueprint.get("quantity").getAsInt();if(quantity>10000)throw new IllegalArgumentException("Stack exceeds 10000; add a separate instance.");item.addProperty("quantity",quantity);return upsert(inventory,item);}}
        return upsert(inventory,blueprint);
    }
    public static JsonObject remove(JsonObject inventory,String id){var out=inventory.deepCopy();var items=out.getAsJsonArray("items");for(int i=items.size()-1;i>=0;i--)if(text(items.get(i).getAsJsonObject(),"id").equals(id))items.remove(i);return out;}
}
