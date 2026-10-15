package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.client.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import java.util.*;

/** Honest navigation bridges to implemented interfaces; complete replacement screens arrive later. */
public final class DestinationScreen extends RpgScreen {
    public enum Destination { ENCOUNTERS, DM, TABLETOP, SETTINGS, HELP }
    private final Destination destination;
    public DestinationScreen(Screen parent,Destination destination) {super(switch(destination) {case ENCOUNTERS->"Encounters";case DM->"DM Tools";case TABLETOP->"Tabletop Tools";case SETTINGS->"Settings";case HELP->"Cubetable Help";},parent);this.destination=destination;}
    private void openRoster(){request(dev.tacticalcombat.uiapi.UiApiService.Operation.ACTIVE,ManagementState.args(),a->{String campaign=ManagementState.text(a.data(),"campaign");if(!a.code().equals("OK")||campaign.isEmpty())return;request(dev.tacticalcombat.uiapi.UiApiService.Operation.CAMPAIGN_GET,ManagementState.args("campaign",campaign),r->{if(r.code().equals("OK")&&ManagementState.flag(r.data(),"manager"))client.setScreen(new CampaignRosterScreen(this,campaign));});});}
    @Override protected void init() {
        super.init();clearChildren();int x=frame.x()+12,w=frame.width()-24,y=frame.y()+38;
        switch(destination) {
            case ENCOUNTERS -> {
                button("New Encounter","Select nearby approved, bound participants",x,y,w,34,()->client.setScreen(new EncounterLauncherScreen(this,true)));
                button("View Active Encounters","Initiative, combat controls and DM ending",x,y+40,w,34,()->client.setScreen(ClientSrdCombat.hudEncounter()!=null?new SrdCombatScreen(this):new EncounterLauncherScreen(this,false)));
                var tactical=button("Return to Tactical battlefield","Existing movement & combat controls",x,y+80,w,30,()->client.setScreen(new TacticalScreen()));tactical.active=ClientCombatState.active;
                addDrawableChild(new RpgTextBox(x,y+116,w,Math.max(24,frame.height()-190),"Your selected campaign DM can start controlled SRD combat here. Actors need approved bindings and supported centered footprints. No automatic monster statistics are generated."));
            }
            case DM -> {
                button("DM Quick Tools","Compact in-game panel",x,y+40,w,30,()->client.setScreen(new QuickPanelScreen(null,true)));
                button("Campaign character roster","Inspect, approve, request changes, assign and bind",x,y,w,34,this::openRoster);
                addDrawableChild(new RpgTextBox(x,y+76,w,Math.max(38,frame.height()-152),"Your campaign manager role was checked with the server. The roster and dashboard Manage tab provide ordinary character review. The server checks authority for every action. Live takeover is unavailable."));
            }
            case TABLETOP -> {
                button("Local character sheets","Generic D20, Percentile & manual dice",x,y,w,34,()->CharacterSheetScreen.open(this));
                button("Import local sheet into My Characters","Independent copy; existing local sheet is preserved",x,y+40,w,30,()->client.setScreen(new LocalLibraryImportScreen(this)));
                addDrawableChild(new RpgTextBox(x,y+76,w,Math.max(20,frame.height()-152),"The existing resizable manual sheet supports local packs, dice and shared cards. Local sheets are independent of approved server characters. K opens sheets; H dismisses a shown card. Shared-card recall remains available through /tcard."));
            }
            case SETTINGS -> {
                button("Minecraft options","GUI scale, accessibility & video settings",x,y,w,34,()->client.setScreen(new OptionsScreen(this,client.options)));
                button("Cubetable HUD customization","Local layout, font, visibility and dice settings",x,y+40,w,34,()->client.setScreen(new HudSettingsScreen(this)));
                addDrawableChild(new RpgTextBox(x,y+82,w,Math.max(24,frame.height()-158),"The shared UI uses native text size. Change Minecraft GUI scale for your display; no character or combat data is changed."));
            }
            case HELP -> {
                addDrawableChild(new RpgTextBox(x,y,w,Math.max(38,frame.height()-82),"Cubetable brings campaign, character and tabletop tools together.\n\nCampaigns: ordinary members navigate here; the hub quick-switch is restricted to DMs. Character selection changes the active indicator and does not bind an entity. Switching is locked during encounters.\n\nExisting commands: /sheet for local sheets, /srdcombat for controlled SRD combat, /tbc for server management. Tabletop Tools preserves manual packs, dice and shared cards.\n\nLater checkpoints add character creation, detailed dashboards and the new HUD. Unsupported servers still allow local tabletop tools. Server errors and timeouts never authorize a local change. Refresh before retrying an uncertain selection."));
            }
        }
        footer();
    }
}
