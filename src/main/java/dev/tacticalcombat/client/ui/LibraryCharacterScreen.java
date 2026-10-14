package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import dev.tacticalcombat.rules.srd.SrdCharacterCodec;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class LibraryCharacterScreen extends ManagementScreen {
    private final String id;
    private JsonObject draft;
    private boolean submitOnOpen;
    public LibraryCharacterScreen submitOnOpen(){submitOnOpen=true;return this;}
    private String validation="Finish Character checks your supported fields.";
    public LibraryCharacterScreen(Screen parent,String id) {super("Library character",parent);this.id=id;}
    String recordId() {return id;}
    @Override protected void refresh() {if(busy) return;fresh=false;clearRows();read(LIBRARY_GET,args("character",id),data->{draft=DraftReplies.snapshot(data,id);validation=data.has("errors")?ManagementState.validation(data):"Finish Character checks your supported fields.";display();if(submitOnOpen){submitOnOpen=false;client.setScreen(new CampaignBrowserScreen(this,this::submission));}});}
    private void display() {
        clearRows();info(text(draft,"name"),profile(text(draft,"profile"))+" · "+text(draft,"stage"));
        row("Open character dashboard","Post-creation overview, records, trackers, progression and management",text(draft,"stage").equals("COMPLETE"),"Finish Character before opening the dashboard",()->client.setScreen(CharacterDashboardScreen.library(this,id)));
        info("Independent personal source","Campaign copies have independent HP, XP, equipment and resources. Source edits never overwrite an approved copy.");
        row(text(draft,"stage").equals("DRAFT")?"Resume character creation":"Review creation record","Unified creator; campaign copies stay independent",text(draft,"stage").equals("DRAFT"),"Completed sources use this detail page; duplicate one to start a new draft",()->client.setScreen(new CharacterWizardScreen(this,draft)));
        info("Character status",validation);
        row("Edit name","Save supported basics; changes return this source to DRAFT",true,"",this::name);
        row("Edit SRD numeric combat inputs","Advanced numeric input editor; equipment and features remain separate",text(draft,"profile").equals("srd_5_1"),"SRD inputs require the SRD 5.1 profile. Manual pack fields remain under legacy local tools.",this::combat);
        row("Inspect saved source","Read-only server data; no campaign approval or live binding",true,"",()->client.setScreen(new InfoScreen(this,text(draft,"name"),"Independent library sheet",draft.getAsJsonObject("sheet").toString())));
        row("Duplicate","New independent DRAFT; original stays unchanged",!ambiguousCreation,"Return to My Characters and inspect a possibly committed duplicate before creating another",()->{long expected=revision(draft);confirm("Duplicate library character?","Create an independent draft from this saved source. A timeout may have created it; refresh the library before repeating.",()->mutate(LIBRARY_DUPLICATE,args("character",id,"revision",expected),data->client.setScreen(new LibraryCharacterScreen(parent,text(data,"id")))));});
        row("Submit to campaign","Creates a separate campaign copy, subject to membership and policy",text(draft,"stage").equals("COMPLETE"),"Finish your character before submitting",()->client.setScreen(new CampaignBrowserScreen(this,this::submission)));
        for(var e:array(draft,"submissions")) {var c=e.getAsJsonObject();row("Campaign copy: "+text(c,"name"),text(c,"campaignName")+" · "+(flag(c,"approved")?"Approved":"Awaiting approval"),true,"",()->{String copy=text(c,"id");client.setScreen(parent instanceof CampaignCharacterScreen target && target.recordId().equals(copy)?parent:new CampaignCharacterScreen(this,copy));});}
        if(flag(draft,"submissionsTruncated")) info("Additional campaign copies","Only the first 128 known submissions are shown. Use campaign rosters for more.");
        row("Delete Character","Delete this personal source. Independent campaign copies and bindings are preserved.",true,"",()->{long expected=revision(draft);confirm("Delete personal character?",text(draft,"name")+" will be removed from My Characters. This cannot be undone. Existing independent campaign copies, HP, resources and entity bindings remain intact.",()->mutate(LIBRARY_DELETE,args("character",id,"revision",expected),data->client.setScreen(parent)));});
        info("Authoring scope","The creation wizard edits supported library draft sections. Campaign copies are independent. Export and ownership transfer are not available.");
    }
    private void validate() {read(LIBRARY_VALIDATE,args("character",id),data->{validation=ManagementState.validation(data);display();client.setScreen(new InfoScreen(this,"Validation results","Supported fields only; not complete guided legality",validation));});}
    private void name() {
        var fields=List.of(new RecordFormScreen.Field("name","Character name",text(draft,"name"),80));
        client.setScreen(new RecordFormScreen(this,"Library basics",LIBRARY_GET,args("character",id),fields,(state,values)->args("character",id,"revision",revision(state),"section","basics","data",args("name",values.get("name"))),LIBRARY_UPDATE,data->client.setScreen(this),"Explicit name save. Other source and campaign fields stay independent.").expected(draft));
    }
    private void combat() {
        var sheet=draft.getAsJsonObject("sheet");var fields=new ArrayList<RecordFormScreen.Field>();
        for(var f:SrdAuthoring.fields()) if(!f.key().equals("name") && f.choices().isEmpty()) fields.add(new RecordFormScreen.Field(f.key(),f.label(),SrdAuthoring.value(sheet,f),12));
        client.setScreen(new RecordFormScreen(this,"SRD library inputs",LIBRARY_GET,args("character",id),fields,(state,values)->{
            var next=state.getAsJsonObject("sheet").deepCopy();for(var f:SrdAuthoring.fields()) if(values.containsKey(f.key())) next=SrdAuthoring.change(next,f,values.get(f.key()));SrdCharacterCodec.parseSheet(next.toString());
            return args("character",id,"revision",revision(state),"section","combat","data",SrdAuthoring.data(next));
        },LIBRARY_UPDATE,data->client.setScreen(this),"Canonical SRD numeric inputs. Equipment/catalog choices retain saved values; full wizard deferred.").expected(draft));
    }
    private void submission(JsonObject campaign) {
        String target=text(campaign,"id");long sourceRevision=revision(draft);SubmissionBook book;
        try {book=ClientSubmissionBook.get();}catch(RuntimeException unavailable) {message=unavailable.getMessage();return;}
        var existing=book.find(id,target);
        // LIBRARY_GET was refreshed on returning from campaign selection. Recover an acknowledged or lost copy first.
        if(existing!=null) {
            for(var e:array(draft,"submissions")) {var copy=e.getAsJsonObject();if(text(copy.getAsJsonObject("origin"),"submission").equals(existing.token().toString())) {client.setScreen(new CampaignCharacterScreen(this,text(copy,"id")));return;}}
            if(existing.revision()!=sourceRevision) {message="An older-source submission must be reconciled. Inspect campaign copies, or duplicate the source for an explicit new copy.";return;}
        }
        confirm("Submit independent campaign copy?",text(draft,"name")+" → "+text(campaign,"name")+". This creates a separate campaign character. Source HP, XP, equipment and resources will not synchronize with it. Campaign Inspiration begins at zero for player submissions and is awarded by the DM. Submission may require DM approval. If a reply is lost, refresh this source and retry with the same submission identity.",()->{
            if(revision(draft)!=sourceRevision) {message="Source changed while confirming. Reconcile before starting another copy.";return;}
            var intent=book.intent(id,target,sourceRevision); // Atomic write must succeed before the packet can be queued.
            mutate(LIBRARY_SUBMIT,args("character",id,"revision",intent.revision(),"campaign",target,"submission",intent.token()),data->client.setScreen(new CampaignCharacterScreen(this,text(data,"id"))));
        });
    }
}
