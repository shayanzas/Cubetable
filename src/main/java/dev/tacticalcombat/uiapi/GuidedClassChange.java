package dev.tacticalcombat.uiapi;
import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import java.util.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;
/** One inert creation transaction; independent raw grants and non-ability combat fields are untouched. */
public final class GuidedClassChange {
    private GuidedClassChange(){}
    public static JsonObject apply(JsonObject before,JsonObject data){
        require(data.keySet().equals(Set.of("fromClass","characterClass","abilityMethod")),"Class change accepts previous class, new class and retained allocation method only");
        for(String key:data.keySet())require(data.get(key).isJsonPrimitive()&&data.getAsJsonPrimitive(key).isString(),"Class change fields must be text");
        String from=data.get("fromClass").getAsString(),target=CreationRules.canonicalClass(data.get("characterClass").getAsString());
        require(CreationRules.clazz(before).equals(from),"Previous class changed; refresh and confirm again");require(!target.isBlank()&&target.length()<=128,"Choose a nonempty class of at most 128 characters");require(!target.equals(from),"Class is already selected; no reset was applied");
        String method=data.get("abilityMethod").getAsString();require(Set.of("POINT_BUY","MANUAL","EXTERNAL_ROLLS","STANDARD_ARRAY").contains(method),"Unknown ability method");require(!method.equals("STANDARD_ARRAY")||CreationRules.choices(before).abilityMethod().equals("STANDARD_ARRAY"),"Standard Array is available only for existing Array records");
        var identity=SheetSections.raw(before,"identity");identity.addProperty("characterClass",target);identity.addProperty("subclass","");var after=SheetSections.update(before,"identity",identity);
        after=SheetSections.update(after,"creation",SheetSections.JSON.toJsonTree(new CreationRules.Choices(List.of(),List.of(),method)).getAsJsonObject());
        var combat=SrdAuthoring.data(after);var defaults=SrdAuthoring.data(SrdAuthoring.template()).getAsJsonObject("abilities");for(int i=0;i<6;i++){String key=CreationRules.ABILITIES.get(i);int value=method.equals("POINT_BUY")?8:method.equals("STANDARD_ARRAY")?CreationRules.ARRAY.get(i):defaults.get(key).getAsInt();combat.getAsJsonObject("abilities").addProperty(key,value);}var rows=new JsonArray();rows.add(combat);after.getAsJsonObject("collections").add("srd_combat",rows);
        CreationRules.scores(after,false);var errors=CreationRules.choiceErrors(after,false);require(errors.isEmpty(),String.join(" ",errors));SrdCharacterCodec.parseSheet(after.toString());return after;
    }
}
