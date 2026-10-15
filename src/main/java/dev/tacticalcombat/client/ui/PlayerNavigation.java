package dev.tacticalcombat.client.ui;
import net.minecraft.client.gui.screen.Screen;
import com.google.gson.JsonObject;
import java.util.List;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;
/** Return to matching ancestors instead of building Sheet/Manage/Sheet loops; scopes never cross. */
public final class PlayerNavigation {
 private PlayerNavigation(){}
 public static Screen manage(Screen parent,DashboardSource source){for(Screen p=parent;p instanceof RpgScreen r;p=r.parent){if(source.library()&&p instanceof LibraryCharacterScreen l&&l.recordId().equals(source.character()))return p;if(!source.library()&&p instanceof CampaignCharacterScreen c&&c.recordId().equals(source.character()))return p;}return source.library()?new LibraryCharacterScreen(parent,source.character()):new CampaignCharacterScreen(parent,source.character());}
 public static Screen sheet(Screen parent,DashboardSource source){for(Screen p=parent;p instanceof RpgScreen r;p=r.parent)if(p instanceof CharacterDashboardScreen d&&d.matches(source))return p;return new CharacterDashboardScreen(parent,source);}
 public static Screen edit(Screen parent,JsonObject d){if(PlayerUiModel.creator(d))return new CharacterWizardScreen(parent,d);String id=text(d,"id");return new RecordFormScreen(parent,"Edit Character Name",LIBRARY_GET,args("character",id),List.of(new RecordFormScreen.Field("name","Character name",text(d,"name"),80)),(state,values)->args("character",id,"revision",revision(state),"section","basics","data",args("name",values.get("name"))),LIBRARY_UPDATE,next->net.minecraft.client.MinecraftClient.getInstance().setScreen(parent),"Save this personal character's name. Manual pack details stay in Tabletop Tools; campaign copies remain unchanged.").expected(d);}
 public static Screen afterDelete(Screen current){for(Screen p=current;p instanceof RpgScreen r;p=r.parent)if(p instanceof LibraryScreen)return p;for(Screen p=current;p instanceof RpgScreen r;p=r.parent)if(p instanceof HubScreen||p instanceof CampaignCharacterScreen)return new LibraryScreen(p);return new LibraryScreen(null);}
}
