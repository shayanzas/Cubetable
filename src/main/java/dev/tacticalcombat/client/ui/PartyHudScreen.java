package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Scrollable complete player-party projection; private sheets still require a server read. */
public final class PartyHudScreen extends ManagementScreen {
    private long version;
    public PartyHudScreen(Screen parent){super("Campaign party · authorized player bindings",parent);}
    @Override protected void refresh(){
        clearRows();fresh=true;version=CubetableHud.MODEL.viewVersion();
        for(var member:CubetableHud.MODEL.members()) {
            var actor=CubetableHud.MODEL.actors().stream().filter(a->a.id().equals(member.actor())).findFirst().orElse(null);
            String detail=member.characterClass()+" · "+member.health()+(member.online()?"":" · offline")+(actor==null?"":" · initiative "+actor.initiative()+(actor.active()?" · current turn":""));
            row(member.name(),detail,true,"",()->read(dev.tacticalcombat.uiapi.UiApiService.Operation.CHARACTER_GET,args("character",member.character()),data->client.setScreen(CharacterDashboardScreen.campaign(this,member.character()))));
        }
        message=CubetableHud.MODEL.members().isEmpty()?"Loading or no eligible player bindings. Private sheets require separate permission.":"Complete last reconciled roster · health sampled when each page was read";
    }
    @Override public void tick(){super.tick();if(!busy&&version!=CubetableHud.MODEL.viewVersion())refresh();}
}
