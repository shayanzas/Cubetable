package dev.tacticalcombat.client.ui;
import com.google.gson.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
/** Inspection of existing custom spell records, without creating an executable spell action. */
public final class SpellReference {
    private SpellReference() {}
    public static FeatureReference.Entry entry(JsonObject definition,JsonObject selection){if(definition==null)return new FeatureReference.Entry(text(selection,"spell"),"Spell definition unavailable","Character spellbook",0,"","Unavailable","","","","","INFORMATIONAL","This selection's definition is missing. Edit the spellbook to review it; casting is unavailable.");String components=(flag(definition,"verbal")?"V ":"")+(flag(definition,"somatic")?"S ":"")+(flag(definition,"material")?"M "+text(definition,"materials"):"");String description=text(definition,"description")+"\n\nRange: "+text(definition,"range")+"\nDuration: "+text(definition,"duration")+"\nComponents: "+components+"\n"+(flag(definition,"concentration")?"Concentration. ":"")+(flag(definition,"ritual")?"Ritual. ":"")+"\n"+(flag(selection,"alwaysPrepared")?"Always prepared":flag(selection,"prepared")?"Prepared":flag(selection,"known")?"Known":"Not selected")+"\nAt higher levels: "+text(definition,"upcasting");return new FeatureReference.Entry(text(definition,"id"),text(definition,"name"),text(definition,"sourceVersion"),0,"Spell level "+text(definition,"level"),text(definition,"castingTime"),"","","",text(definition,"description"),"INFORMATIONAL",description);}
}
