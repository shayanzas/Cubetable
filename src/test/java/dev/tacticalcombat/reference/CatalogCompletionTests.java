package dev.tacticalcombat.reference;

import com.google.gson.*;
import java.util.*;

/** Source-evidence, progression and compatibility gates; no executable gameplay data is generated. */
public final class CatalogCompletionTests {
    private static int checks;
    private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
    private static void reject(Runnable task,String why){try{task.run();}catch(RuntimeException expected){checks++;return;}throw new AssertionError(why);}
    private static JsonObject row(JsonObject root,String id){return root.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject).filter(e->e.get("id").getAsString().equals(id)).findFirst().orElseThrow();}
    public static void main(String[] args){run();}
    public static int run(){
        checks=0;var catalog=SrdReferenceCatalog.get();var evidence=JsonParser.parseString(SrdReferenceCatalog.resource("source-coverage-v1.json")).getAsJsonObject();
        checks+=SrdCoverageValidator.validate(catalog,evidence);
        var counts=new HashMap<String,Integer>();catalog.entries().forEach(e->counts.merge(e.category(),1,Integer::sum));
        check(counts.get("monsters")==317,"All independent source stat-block anchors imported");check(counts.get("magic-items")==240,"All independent item type/rarity anchors imported");
        check(dev.tacticalcombat.rules.srd.MonsterProfiles.ALL.size()==3,"Reference expansion cannot expand combat allowlist");
        for(var monster:dev.tacticalcombat.rules.srd.MonsterProfiles.ALL)dev.tacticalcombat.rules.srd.MonsterProfiles.verify(monster);
        for(var entry:catalog.entries()){
            if(entry.category().equals("classes")){
                var data=entry.data().getAsJsonObject("reference");var levels=data.getAsJsonArray("levels");check(levels.size()==20,"20 class levels");
                for(int i=0;i<20;i++){var level=levels.get(i).getAsJsonObject();check(level.get("level").getAsInt()==i+1,"Ordered levels");check(level.get("proficiencyBonus").getAsInt()==2+i/4,"Printed proficiency progression");}
                var asi=data.getAsJsonArray("abilityScoreImprovementLevels").asList();check(asi.contains(new JsonPrimitive(4))&&asi.contains(new JsonPrimitive(19)),"ASI progression");
            }
            if(entry.category().equals("magic-items")){var data=entry.data();if(data.has("reference"))data=data.getAsJsonObject("reference");check(data.has("rarities")&&data.has("attunementRequired"),"Explicit magic metadata");}
            if(entry.category().equals("monsters"))check(entry.data().has("reference")||entry.data().get("referenceOnly").getAsBoolean(),"Imported monsters remain static references");
        }
        for(var e:catalog.entries())check(e.detail().contains("REFERENCE ONLY"),"Every reference detail renders without activating gameplay");
        check(catalog.find("srd51:subclasses:champion").data().getAsJsonArray("featureReferences").size()==5,"Champion links only its five subclass feature headings, not an earlier table word");
        var barb=catalog.find("srd51:classes:barbarian").data().getAsJsonObject("reference");check(barb.getAsJsonArray("levels").get(19).getAsJsonObject().getAsJsonObject("resources").get("rages").getAsString().equals("unlimited"),"Wide Unlimited cell retained");
        check(barb.getAsJsonArray("levels").get(10).getAsJsonObject().get("featuresText").getAsString().equals("Relentless Rage"),"Split-column feature continuation");
        var rogue=catalog.find("srd51:classes:rogue").data().getAsJsonObject("reference");check(rogue.getAsJsonArray("abilityScoreImprovementLevels").asList().contains(new JsonPrimitive(10)),"Split Rogue 10 ASI");
        check(catalog.find("srd51:classes:wizard").data().getAsJsonObject("reference").getAsJsonArray("levels").get(19).getAsJsonObject().getAsJsonArray("spellSlots").toString().equals("[4,3,3,3,3,2,2,1,1]"),"Full caster 20 slots");
        for(String id:List.of("srd51:monsters:aboleth","srd51:magic-items:amulet-of-health","srd51:classes:wizard","srd51:equipment:camel")){
            var altered=catalog.document();var entry=row(altered,id);entry.addProperty("description",entry.get("description").getAsString()+" invented");var valid=SrdReferenceCatalog.parse(altered.toString());reject(()->SrdCoverageValidator.validate(valid,evidence),"Changed source descriptions fail evidence");
        }
        for(String mutation:List.of("monster-stat","class-level","slots","rarity","attunement","equipment-cost","unresolved-level")){
            var altered=catalog.document();switch(mutation){
                case "monster-stat"->row(altered,"srd51:monsters:aboleth").getAsJsonObject("data").getAsJsonObject("abilities").addProperty("str","21");
                case "class-level"->row(altered,"srd51:classes:wizard").getAsJsonObject("data").getAsJsonObject("reference").getAsJsonArray("levels").remove(19);
                case "slots"->row(altered,"srd51:classes:wizard").getAsJsonObject("data").getAsJsonObject("reference").getAsJsonArray("levels").get(0).getAsJsonObject().getAsJsonArray("spellSlots").set(0,new JsonPrimitive(-1));
                case "rarity"->row(altered,"srd51:magic-items:amulet-of-health").getAsJsonObject("data").remove("rarities");
                case "attunement"->row(altered,"srd51:magic-items:amulet-of-health").getAsJsonObject("data").remove("attunementText");
                case "equipment-cost"->row(altered,"srd51:equipment:camel").getAsJsonObject("data").addProperty("cost",50);
                case "unresolved-level"->{var e=altered.getAsJsonArray("entries").asList().stream().map(JsonElement::getAsJsonObject).filter(v->v.get("category").getAsString().equals("features")&&v.getAsJsonObject("data").has("levelStatus")&&v.getAsJsonObject("data").get("minimumLevel").isJsonNull()).findFirst().orElseThrow();e.getAsJsonObject("data").remove("levelStatus");}
            }reject(()->SrdReferenceCatalog.parse(altered.toString()),"Incomplete or inconsistent "+mutation);
        }
        var missing=evidence.deepCopy();missing.getAsJsonArray("records").remove(0);reject(()->SrdCoverageValidator.validate(catalog,missing),"Incomplete coverage manifest rejected");
        System.out.println("SRD C2 completion checks passed: "+checks);return checks;
    }
}
