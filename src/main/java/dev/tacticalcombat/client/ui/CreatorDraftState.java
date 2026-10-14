package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Retained editing buffers; incoming acknowledgements never erase newer unsaved input. */
public final class CreatorDraftState {
    public static final List<String> FIELDS=List.of("name","species","age","height","alignment","characterClass");
    public static final List<String> ABILITIES=List.of("str","dex","con","int","wis","cha");
    public final Map<String,String> fields=new LinkedHashMap<>(),scores=new LinkedHashMap<>();
    public WizardModel.ScoreMethod method=WizardModel.ScoreMethod.MANUAL;
    public boolean classAllocationReset;
    private JsonObject snapshot,baseline;
    private WizardModel.ScoreMethod savedMethod;
    public CreatorDraftState(JsonObject initial){method=WizardModel.ScoreMethod.valueOf(SheetSections.read(initial.getAsJsonObject("sheet"),"creation").get("abilityMethod").getAsString());savedMethod=method;snapshot=initial.deepCopy();baseline=initial.deepCopy();for(String f:FIELDS)fields.put(f,savedField(initial,f));for(String k:ABILITIES)scores.put(k,savedScore(initial,k));}
    public boolean legacyArray(){return method==WizardModel.ScoreMethod.STANDARD_ARRAY||savedMethod==WizardModel.ScoreMethod.STANDARD_ARRAY;}
    public JsonObject snapshot(){return snapshot.deepCopy();}
    public void accept(JsonObject next,boolean ownAcknowledgement){
        for(String f:FIELDS)if(fields.get(f).equals(savedField(snapshot,f)))fields.put(f,savedField(next,f));
        for(String k:ABILITIES)if(scores.get(k).equals(savedScore(snapshot,k)))scores.put(k,savedScore(next,k));
        if(method==savedMethod)method=WizardModel.ScoreMethod.valueOf(SheetSections.read(next.getAsJsonObject("sheet"),"creation").get("abilityMethod").getAsString());savedMethod=WizardModel.ScoreMethod.valueOf(SheetSections.read(next.getAsJsonObject("sheet"),"creation").get("abilityMethod").getAsString());
        snapshot=next.deepCopy();if(ownAcknowledgement||!dirty())baseline=snapshot.deepCopy();
    }
    public void acceptClassChange(JsonObject next){accept(next,true);fields.put("characterClass",savedField(next,"characterClass"));method=WizardModel.ScoreMethod.valueOf(SheetSections.read(next.getAsJsonObject("sheet"),"creation").get("abilityMethod").getAsString());savedMethod=method;for(String key:ABILITIES)scores.put(key,savedScore(next,key));classAllocationReset=true;}
    public boolean reviewRequired(){return dirty()&&ManagementState.needsEditReview(baseline,snapshot);}
    public void reviewed(){baseline=snapshot.deepCopy();}
    public boolean dirty(){return method!=savedMethod||FIELDS.stream().anyMatch(f->!fields.get(f).equals(savedField(snapshot,f)))||ABILITIES.stream().anyMatch(k->!scores.get(k).equals(savedScore(snapshot,k)));}
    public boolean identityDirty(){return List.of("name","species","age","height","alignment").stream().anyMatch(f->!fields.get(f).equals(savedField(snapshot,f)));}
    public boolean classDirty(){return !fields.get("characterClass").equals(savedField(snapshot,"characterClass"));}
    public boolean scoresDirty(){return method!=savedMethod||ABILITIES.stream().anyMatch(k->!scores.get(k).equals(savedScore(snapshot,k)));}
    public JsonObject identityData(List<String> keys){var data=SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity");for(String f:keys)data.addProperty(f,fields.get(f));return data;}
    public Map<String,Integer> scoreValues(){var result=new LinkedHashMap<String,Integer>();try{for(String k:ABILITIES)result.put(k,Integer.parseInt(scores.get(k)));}catch(RuntimeException invalid){throw new IllegalArgumentException("Enter six whole scores.");}WizardModel.validateScores(method,result);return result;}
    public void standardArray(){method=WizardModel.ScoreMethod.STANDARD_ARRAY;for(int i=0;i<ABILITIES.size();i++)scores.put(ABILITIES.get(i),WizardModel.STANDARD_ARRAY.get(i).toString());}
    public static String savedField(JsonObject snapshot,String key){return key.equals("name")?text(snapshot,"name"):text(SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity"),key);}
    public static String savedScore(JsonObject snapshot,String key){try{return SrdAuthoring.data(snapshot.getAsJsonObject("sheet")).getAsJsonObject("abilities").get(key).getAsString();}catch(RuntimeException unsupported){return "";}}
}
