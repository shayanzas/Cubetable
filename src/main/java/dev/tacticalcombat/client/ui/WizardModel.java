package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import java.util.*;

/** Pure creation navigation, 2014 score calculators and truthful completion summaries. */
public final class WizardModel {
    public enum Stage { METHOD, BASICS, SPECIES, CLASS, BACKGROUND, ABILITIES, PROFICIENCIES, EQUIPMENT, FEATURES, SPELLS, REVIEW }
    public enum ScoreMethod { STANDARD_ARRAY, POINT_BUY, MANUAL, EXTERNAL_ROLLS }
    public record Status(boolean complete,String detail) {}
    public static final List<Integer> STANDARD_ARRAY=List.of(15,14,13,12,10,8);
    private static final Map<Integer,Integer> POINT_COST=Map.of(8,0,9,1,10,2,11,3,12,4,13,5,14,7,15,9);
    private WizardModel() {}
    public static String title(Stage s) {return switch(s) {
        case METHOD->"1 · Method";case BASICS->"2 · Basics";case SPECIES->"3 · Species";case CLASS->"4 · Class";case BACKGROUND->"5 · Background";
        case ABILITIES->"6 · Abilities";case PROFICIENCIES->"7 · Proficiencies";case EQUIPMENT->"8 · Equipment";case FEATURES->"9 · Features";case SPELLS->"10 · Spells";case REVIEW->"11 · Review";};}
    public static Stage validationStage(String section){return switch(section){case "basics","identity"->Stage.BASICS;case "combat","abilities"->Stage.ABILITIES;case "proficiencies"->Stage.PROFICIENCIES;case "inventory"->Stage.EQUIPMENT;case "features","progression"->Stage.FEATURES;case "spellbook"->Stage.SPELLS;default->Stage.REVIEW;};}
    public static int modifier(int score) {return Math.floorDiv(score-10,2);}
    public static int pointCost(int score) {var result=POINT_COST.get(score);if(result==null) throw new IllegalArgumentException("Point-buy scores must be 8–15");return result;}
    public static int pointRemaining(Map<String,Integer> scores) {return 27-scores.values().stream().mapToInt(WizardModel::pointCost).sum();}
    public static void validateScores(ScoreMethod method,Map<String,Integer> scores) {
        if(scores.size()!=6 || !scores.keySet().equals(Set.of("str","dex","con","int","wis","cha"))) throw new IllegalArgumentException("All six abilities are required");
        if(method==ScoreMethod.STANDARD_ARRAY) {
            var values=new ArrayList<>(scores.values());values.sort(Comparator.reverseOrder());if(!values.equals(STANDARD_ARRAY)) throw new IllegalArgumentException("Assign each standard-array value exactly once");
        } else if(method==ScoreMethod.POINT_BUY) {if(pointRemaining(scores)<0) throw new IllegalArgumentException("Point-buy exceeds 27 points");}
        else for(int score:scores.values()) if(score<1 || score>30) throw new IllegalArgumentException("Manual scores must be within server bounds 1–30");
    }
    public static EnumMap<Stage,Status> statuses(JsonObject draft) {
        var result=new EnumMap<Stage,Status>(Stage.class);if(draft==null||!draft.has("sheet")||!draft.get("sheet").isJsonObject()){for(var stage:Stage.values())result.put(stage,new Status(false,"Character snapshot unavailable; refresh the saved draft"));return result;}var sheet=draft.getAsJsonObject("sheet");var identity=SheetSections.read(sheet,"identity");
        String profile=ManagementState.text(draft,"profile"),mode=ManagementState.text(draft,"mode"),name=ManagementState.text(draft,"name");
        result.put(Stage.METHOD,new Status(!profile.isBlank(),mode+" · "+ManagementState.profile(profile)+(mode.equals("GUIDED")?" · guided rules incomplete":" · direct/manual records")));
        result.put(Stage.BASICS,new Status(!name.isBlank(),name.isBlank()?"Name required":"Canonical name saved"));
        result.put(Stage.SPECIES,present(identity,"species","Choose or record a species"));result.put(Stage.CLASS,present(identity,"characterClass","Choose or record a class"));result.put(Stage.BACKGROUND,present(identity,"background","Choose or record a background"));
        boolean srd=profile.equals("srd_5_1"),validCombat=false;try {dev.tacticalcombat.rules.srd.SrdCharacterCodec.parseSheet(sheet.toString());validCombat=true;}catch(RuntimeException ignored) {}
        result.put(Stage.ABILITIES,new Status(!srd || validCombat,srd?(validCombat?"Six authoritative SRD scores saved":"SRD combat inputs need correction"):"Profile uses its existing manual sheet representation"));
        var prof=SheetSections.read(sheet,"proficiencies");int pc=prof.getAsJsonArray("skills").size()+prof.getAsJsonArray("saves").size()+prof.getAsJsonArray("tools").size()+prof.getAsJsonArray("languages").size();result.put(Stage.PROFICIENCIES,new Status(true,pc+" recorded entries · informational unless existing combat schema says otherwise"));
        result.put(Stage.EQUIPMENT,new Status(true,SheetSections.read(sheet,"inventory").getAsJsonArray("items").size()+" manual inventory records"));
        result.put(Stage.FEATURES,new Status(true,SheetSections.read(sheet,"features").getAsJsonArray("entries").size()+" manual/informational features"));
        result.put(Stage.SPELLS,new Status(true,SheetSections.read(sheet,"spellbook").getAsJsonArray("selections").size()+" custom spell selections · standard catalog unavailable"));
        boolean structural=ManagementState.array(draft,"errors").isEmpty();result.put(Stage.REVIEW,new Status(structural && ManagementState.text(draft,"stage").equals("COMPLETE"),structural?(ManagementState.text(draft,"stage").equals("COMPLETE")?"Finalized library source":"Supported fields valid; not finalized"):ManagementState.validation(draft)));
        return result;
    }
    private static Status present(JsonObject o,String key,String missing) {String value=ManagementState.text(o,key);return new Status(!value.isBlank(),value.isBlank()?missing:value+" · recorded/informational");}
    public static UiLayout.Rect[] layout(UiLayout.Rect frame) {
        boolean compact=frame.width()<560;
        if(compact) return new UiLayout.Rect[]{new UiLayout.Rect(frame.x()+12,frame.y()+36,frame.width()-24,54),new UiLayout.Rect(frame.x()+12,frame.y()+94,frame.width()-24,Math.max(38,frame.height()-136)),new UiLayout.Rect(0,0,0,0)};
        int side=142,preview=frame.width()>=680?132:0,gap=6;int main=frame.width()-24-side-gap-(preview==0?0:preview+gap);
        return new UiLayout.Rect[]{new UiLayout.Rect(frame.x()+12,frame.y()+36,side,frame.height()-98),new UiLayout.Rect(frame.x()+12+side+gap,frame.y()+36,main,frame.height()-98),preview==0?new UiLayout.Rect(0,0,0,0):new UiLayout.Rect(frame.x()+frame.width()-12-preview,frame.y()+36,preview,frame.height()-98)};
    }
}
