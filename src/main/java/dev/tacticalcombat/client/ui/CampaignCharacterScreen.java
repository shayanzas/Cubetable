package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Permission-aware lifecycle management opened from the detailed dashboard. */
public final class CampaignCharacterScreen extends ManagementScreen {
    private final String id;
    private JsonObject character;
    private String selectedCampaign;
    private boolean inCombat;
    public CampaignCharacterScreen(Screen parent,String id) {super("Campaign character",parent);this.id=id;}
    String recordId() {return id;}
    @Override protected void refresh() {
        if(busy) return;fresh=false;clearRows();read(ACTIVE,args(),active->{selectedCampaign=text(active,"campaign");inCombat=flag(active,"inCombat");read(CHARACTER_GET,args("character",id),data->{character=data;display(text(active,"character"));});});
    }
    private void display(String selected) {
        clearRows();boolean manager=manager(character),bound=!text(character,"bound").isEmpty(),manage=canManageCharacter(character);
        info(text(character,"name"),profile(text(character,"profile"))+" · "+(flag(character,"approved")?"Approved":"Awaiting DM approval")+" · "+(bound?"Bound":"Unbound"));
        row("Open character dashboard","Authoritative combat overview and permission-aware detailed tabs",true,"",()->client.setScreen(CharacterDashboardScreen.campaign(this,id)));
        info("Ownership and control","Owner: "+player(character,text(character,"owner"))+" · controller: "+player(character,text(character,"controller"))+". Assignment never transfers ownership.");
        var srd=character.getAsJsonObject("srd");info("Readiness",srd==null?"Combat statistics follow the selected server profile. Approval and compatible entity binding are required.":text(srd,"reason"));
        row("Inspect supported sheet","Read-only canonical and manual data",true,"",()->client.setScreen(new InfoScreen(this,text(character,"name"),"Server-authoritative campaign copy",character.getAsJsonObject("sheet").toString())));
        boolean select=canSelect(character,self(),selectedCampaign,inCombat) && !id.equals(selected);
        row(id.equals(selected)?"Selected character":"Select character","Selection does not bind or change combat resources",select,"Select this campaign first; selection is locked during combat and requires controller/manager authority",()->mutate(CHARACTER_SELECT,args("character",id,"revision",revision(character)),d->refresh()));
        row("Approve","Server validates supported statistics; no client approval flag",canApprove(character,true),"Only managers approve unbound characters",()->approval(true));
        row("Revoke approval / request changes","Generic status only; no written review thread",canApprove(character,false),"Unbind first; only managers revoke approval",()->approval(false));
        row("Offer player control","Out-of-encounter substitute workflow",manage,"Manager required; bound characters cannot be reassigned",()->offer());
        var assignment=character.has("assignment") && character.get("assignment").isJsonObject()?character.getAsJsonObject("assignment"):null;
        row("Clear assignment","Restore original owner as controller",manage && assignment!=null,"Manager required; unbind first and an assignment must exist",()->{long expected=revision(assignment);confirm("Restore owner control?","Cancel the pending offer and restore the original owner. Ownership does not change.",()->mutate(ASSIGNMENT_CLEAR,args("character",id,"revision",expected),d->refresh()));});
        row("Assignments / offers","View, accept or decline authorized offers",true,"",()->client.setScreen(new AssignmentScreen(this,text(character,"campaign"))));
        row("Bind to eligible entity","Approval and compatible accepted controller required",manage && flag(character,"approved"),"Manager, approval and unbound character required",()->client.setScreen(new BindingPickerScreen(this,id,entity->{long expected=revision(character);confirm("Bind character?",text(character,"name")+" → "+text(entity,"name")+". The server rechecks live entity, control, campaign, profile and encounter state.",()->mutate(BIND,args("character",id,"revision",expected,"entity",text(entity,"id")),d->refresh()));})));
        row("Unbind","Server releases the binding; active encounter locks apply",manager && bound,"Only campaign managers can release bindings",()->{long expected=revision(character);confirm("Unbind character?","Release this character's entity binding. Combat may require ending the encounter first.",()->mutate(UNBIND,args("character",id,"revision",expected),d->refresh()));});
        var sheet=character.getAsJsonObject("sheet");var origin=dev.tacticalcombat.uiapi.UiApiService.origin(sheet);if(origin!=null) {
            row("Source library character","Independent source; opening requires source ownership",text(character,"owner").equals(self()),"Only source owner can open their private library",()->{String library=text(origin,"library");client.setScreen(parent instanceof LibraryCharacterScreen source && source.recordId().equals(library)?parent:new LibraryCharacterScreen(this,library));});
        }
        info("Editing / control limits","Character Details owns supported edits. Physical binding and player control require separate DM actions. Live takeover and remote NPC delegation remain unavailable.");
    }
    private void approval(boolean approve) {long expected=revision(character);confirm(approve?"Approve character?":"Revoke approval?",approve?"Approve the current server character revision. This does not bind it or establish complete guided D&D build legality.":"Revoke approval and mark generic changes requested. There is no persistent written review conversation.",()->mutate(approve?CHARACTER_APPROVE:CHARACTER_REVOKE,args("character",id,"revision",expected),d->refresh()));}
    private void offer() {client.setScreen(new PlayerPickerScreen(this,text(character,"campaign"),true,p->{long expected=revision(character);var a=character.has("assignment") && character.get("assignment").isJsonObject()?character.getAsJsonObject("assignment"):null;long ar=a==null?0:revision(a);confirm("Offer control?",text(character,"name")+" → "+text(p,"name")+". The recipient must accept. Ownership stays unchanged; assignment does not bind.",()->mutate(ASSIGNMENT_OFFER,args("character",id,"revision",expected,"assignmentRevision",ar,"player",text(p,"id")),d->refresh()));}));}
}
