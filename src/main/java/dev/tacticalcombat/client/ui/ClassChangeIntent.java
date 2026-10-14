package dev.tacticalcombat.client.ui;
import com.google.gson.JsonObject;
import java.util.*;
import dev.tacticalcombat.uiapi.CreationRules;
import static dev.tacticalcombat.client.ui.ManagementState.*;
/** Confirmation is bound to saved revision and the staged allocation it will discard. */
public record ClassChangeIntent(long revision,String selected,String saved,String target,WizardModel.ScoreMethod method,Map<String,String> scores){
    public ClassChangeIntent{scores=Map.copyOf(scores);}
    public static Optional<ClassChangeIntent> prepare(CreatorDraftState state,String target){target=CreationRules.canonicalClass(target);if(target.isBlank()||target.length()>128)throw new IllegalArgumentException("Enter a class name of 1–128 characters.");String selected=CreationRules.canonicalClass(state.fields.get("characterClass"));if(selected.equals(target))return Optional.empty();return Optional.of(new ClassChangeIntent(ManagementState.revision(state.snapshot()),selected,CreationRules.clazz(state.snapshot().getAsJsonObject("sheet")),target,state.method,state.scores));}
    public boolean matches(CreatorDraftState state){return ManagementState.revision(state.snapshot())==revision&&CreationRules.canonicalClass(state.fields.get("characterClass")).equals(selected)&&state.method==method&&state.scores.equals(scores);}
    public boolean acknowledged(JsonObject next){try{var sheet=next.getAsJsonObject("sheet");var choices=dev.tacticalcombat.uiapi.CreationRules.choices(sheet);if(ManagementState.revision(next)!=revision+1||!CreationRules.clazz(sheet).equals(target)||!choices.skills().isEmpty()||!choices.expertise().isEmpty()||!choices.abilityMethod().equals(method.name()))return false;var abilities=dev.tacticalcombat.rules.srd.SrdAuthoring.data(sheet).getAsJsonObject("abilities");for(int i=0;i<6;i++){int expected=method==WizardModel.ScoreMethod.POINT_BUY?8:method==WizardModel.ScoreMethod.STANDARD_ARRAY?CreationRules.ARRAY.get(i):10;if(abilities.get(CreationRules.ABILITIES.get(i)).getAsInt()!=expected)return false;}return true;}catch(RuntimeException invalid){return false;}}
    public JsonObject data(){return args("fromClass",saved,"characterClass",target,"abilityMethod",method.name());}
}
