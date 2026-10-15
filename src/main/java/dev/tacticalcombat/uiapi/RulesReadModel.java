package dev.tacticalcombat.uiapi;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import java.util.*;

/** Serialization only. Callers must authorize the character before building this private read model. */
public final class RulesReadModel {
    private RulesReadModel() {}
    public static JsonObject of(DerivedRulesState rules,JsonObject sheet) {
        var out=new JsonObject();var c=rules.inputs();var hp=rules.resources();
        out.addProperty("version",CharacterRules.PROJECTION_VERSION);out.addProperty("rulesVersion",CharacterRules.RULES_VERSION);
        out.addProperty("mode",LegacySrdInputs.MODE);out.addProperty("valid",true);
        out.addProperty("effectiveLevel",c.level());int recorded=SheetSections.raw(sheet,"progression").get("recordedLevel").getAsInt();
        boolean explicit=sheet.has("collections")&&sheet.getAsJsonObject("collections").has("ui_progression");
        out.addProperty("recordedLevel",recorded);out.addProperty("recordedLevelExplicit",explicit);out.addProperty("levelMismatch",explicit&&recorded!=c.level());
        out.addProperty("levelNotice",explicit&&recorded!=c.level()?"Recorded level "+recorded+" differs from combat level "+c.level()+". Progression is informational; no advancement has been applied.":"");
        out.addProperty("hp",hp.hp());out.addProperty("maximumHp",hp.maximumHp());out.addProperty("temporaryHp",hp.temporaryHp());
        out.addProperty("ac",rules.armorClass());out.addProperty("initiativeModifier",rules.initiativeModifier());out.addProperty("speedFeet",rules.walkingSpeedFeet());out.addProperty("proficiency",rules.proficiency());
        out.addProperty("armorDisadvantage",c.armorDisadvantage());out.addProperty("arrows",hp.arrows());out.addProperty("bolts",hp.bolts());out.addProperty("daggers",hp.daggers());
        var abilities=new JsonObject();for(var a:SrdCharacter.Ability.values()){var row=new JsonObject();row.addProperty("score",c.abilities().get(a));row.addProperty("modifier",rules.modifiers().get(a));abilities.add(a.name().toLowerCase(Locale.ROOT),row);}out.add("abilities",abilities);
        // Inputs here remain explicit compatibility inputs; none imply inventory ownership or class grants.
        out.add("inputs",SheetSections.JSON.toJsonTree(c));out.add("provenance",SheetSections.JSON.toJsonTree(rules.provenance()));
        out.addProperty("checksExecutable",false);out.addProperty("inventoryIntegrated",false);out.addProperty("featureAutomation",false);
        return out;
    }
    public static JsonObject unavailable(RuntimeException error) {var out=new JsonObject();out.addProperty("version",CharacterRules.PROJECTION_VERSION);out.addProperty("valid",false);out.addProperty("reason","Saved SRD inputs could not be projected; review the existing validation errors.");return out;}
}
