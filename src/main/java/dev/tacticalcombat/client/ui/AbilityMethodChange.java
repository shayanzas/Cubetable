package dev.tacticalcombat.client.ui;

import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.revision;

/** Explicit conversion intent; historical scores never change just by opening their editor. */
public record AbilityMethodChange(long revision,WizardModel.ScoreMethod from,Map<String,String> scores,WizardModel.ScoreMethod to) {
    public AbilityMethodChange {scores=Map.copyOf(scores);}
    public static AbilityMethodChange prepare(CreatorDraftState state,WizardModel.ScoreMethod to){return new AbilityMethodChange(ManagementState.revision(state.snapshot()),state.method,state.scores,to);}
    public boolean apply(CreatorDraftState state){if(ManagementState.revision(state.snapshot())!=revision||state.method!=from||!state.scores.equals(scores))return false;if(to==WizardModel.ScoreMethod.STANDARD_ARRAY)state.standardArray();else{state.method=to;if(to==WizardModel.ScoreMethod.POINT_BUY){try{var values=new LinkedHashMap<String,Integer>();scores.forEach((k,v)->values.put(k,Integer.parseInt(v)));WizardModel.validateScores(to,values);}catch(RuntimeException invalid){for(String key:CreatorDraftState.ABILITIES)state.scores.put(key,"8");}}}return true;}
    public String explanation(){return to==WizardModel.ScoreMethod.POINT_BUY?"Switch to Point Buy? Scores already valid within its 27-point budget are preserved. Other allocations reset to 8 in each ability. Save Draft confirms the change on the server.":to==WizardModel.ScoreMethod.STANDARD_ARRAY?"Restore the legacy Standard Array? This replaces your staged scores with 15, 14, 13, 12, 10 and 8. Save Draft confirms the change.":"Switch to Manual / External and preserve all current scores? Save Draft confirms the change. External rolls and racial increases remain manual.";}
}
