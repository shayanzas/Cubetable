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
                addDrawableChild(new RpgTextBox(x,y+116,w,Math.max(24,frame.height()-190),"Your selected campaign DM can start controlled SRD combat here. Actors need approved bindings and safe standing positions and supported footprints. No automatic monster statistics are generated."));
            }
            case DM -> {
                button("DM Workspace","Party, Actors, Encounters, Scene and Rolls",x,y+40,w,30,()->client.setScreen(new DmWorkspaceScreen(this)));
                button("Campaign character roster","Inspect, approve, request changes, assign and bind",x,y,w,34,this::openRoster);
                addDrawableChild(new RpgTextBox(x,y+76,w,Math.max(38,frame.height()-152),"Your campaign manager role was checked with the server. The roster and dashboard Manage tab provide ordinary character review. The server checks authority for every action. Live takeover is unavailable."));
            }
            case TABLETOP -> {
                var canvas=addDrawableChild(new CharacterCanvas(new UiLayout.Rect(x,y,w,Math.max(30,frame.height()-90))));int cw=w-16;
                canvas.control("local",new RpgButton(8,4,cw,32,"Local character sheets","Generic D20 and percentile packs",()->CharacterSheetScreen.open(this)));
                canvas.control("import",new RpgButton(8,42,cw,32,"Import local sheet","Independent library copy",()->client.setScreen(new LocalLibraryImportScreen(this))));
                canvas.control("dice",new RpgButton(8,80,cw,32,"Manual Dice","Server RNG · manual modifier",()->client.setScreen(new DiceToolbarScreen(this,true))));
                canvas.control("reference",new RpgButton(8,118,cw,32,"SRD 5.1 Reference","Read-only spells, equipment and monster stat blocks",()->client.setScreen(new ReferenceCatalogScreen(this))));
                canvas.wrapped("Local sheets remain separate from approved campaign characters. Manual rolls do not execute character checks.",8,158,cw,RpgTheme.MUTED);
            }
            case SETTINGS -> {
                button("Minecraft options","GUI scale, accessibility & video settings",x,y,w,34,()->client.setScreen(new OptionsScreen(this,client.options)));
                button("Cubetable HUD customization","Local layout, font, visibility and dice settings",x,y+40,w,34,()->client.setScreen(new HudSettingsScreen(this)));
                addDrawableChild(new RpgTextBox(x,y+82,w,Math.max(24,frame.height()-158),"The shared UI uses native text size. Change Minecraft GUI scale for your display; no character or combat data is changed."));
            }
            case HELP -> {
                addDrawableChild(new RpgTextBox(x,y,w,Math.max(38,frame.height()-82),"Cubetable brings campaign, character and tabletop tools together.\n\nCampaigns: ordinary members navigate here; the hub quick-switch is restricted to DMs. Character selection changes the active indicator and does not bind an entity. Switching is locked during encounters.\n\nExisting commands: /sheet for local sheets, /srdcombat for controlled SRD combat, /tbc for server management. Tabletop Tools preserves manual packs, dice and shared cards.\n\nCharacter creation, dashboards, the gameplay panels and HUD are available through their corresponding menus. Unsupported servers still allow local tabletop tools. Server errors and timeouts never authorize a local change. Refresh before retrying an uncertain selection."));
            }
        }
        footer();
    }
}
