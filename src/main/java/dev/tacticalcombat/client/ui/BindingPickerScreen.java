package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.function.Consumer;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class BindingPickerScreen extends ManagementScreen {
    private final String character;
    private final Consumer<JsonObject> picked;
    public BindingPickerScreen(Screen parent,String character,Consumer<JsonObject> picked) {super("Eligible entity bindings",parent);this.character=character;this.picked=picked;}
    @Override protected void refresh() {if(busy) return;fresh=false;clearRows();read(ELIGIBLE_ENTITIES,args("character",character),data->{
        clearRows();int index=0;for(var e:array(data,"entities")) {var entity=e.getAsJsonObject();row(text(entity,"name")+" · nearby #"+(++index),text(entity,"position")+" · "+text(entity,"reason"),flag(entity,"eligible"),text(entity,"reason"),()->returnChoice(parent,()->picked.accept(entity.deepCopy())));}
        message="Loaded candidates within "+data.get("radius").getAsInt()+" blocks"+(flag(data,"truncated")?"; list truncated to 64":"")+". Binding rechecks current state.";
    });}
}
