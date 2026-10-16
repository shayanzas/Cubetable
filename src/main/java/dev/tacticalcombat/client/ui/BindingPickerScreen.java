package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.function.Consumer;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class BindingPickerScreen extends ManagementScreen {
    private final String character;
    private dev.tacticalcombat.combat.NearbyActors.Binding binding=dev.tacticalcombat.combat.NearbyActors.Binding.ALL;
    private dev.tacticalcombat.combat.NearbyActors.Kind kind=dev.tacticalcombat.combat.NearbyActors.Kind.ALL;
    @Override protected void controls(){int w=(frame.width()-124)/2;button(binding.name(),"",frame.x()+112,frame.y()+frame.height()-38,w-2,22,()->{binding=dev.tacticalcombat.combat.NearbyActors.Binding.values()[(binding.ordinal()+1)%4];client.setScreen(this);});button(kind.name(),"",frame.x()+114+w,frame.y()+frame.height()-38,w-2,22,()->{kind=dev.tacticalcombat.combat.NearbyActors.Kind.values()[(kind.ordinal()+1)%3];client.setScreen(this);});}
    private final Consumer<JsonObject> picked;
    public BindingPickerScreen(Screen parent,String character,Consumer<JsonObject> picked) {super("Eligible entity bindings",parent);this.character=character;this.picked=picked;}
    @Override protected void refresh() {if(busy) return;fresh=false;clearRows();read(ELIGIBLE_ENTITIES,args("character",character,"binding",binding,"kind",kind),data->{
        clearRows();int index=0;for(var e:array(data,"entities")) {var entity=e.getAsJsonObject();row(text(entity,"name")+" · "+text(entity,"distance")+"m",text(entity,"position")+" · "+text(entity,"reason"),flag(entity,"eligible"),text(entity,"reason"),()->returnChoice(parent,()->picked.accept(entity.deepCopy())));}
        message="Loaded candidates within "+data.get("radius").getAsInt()+" blocks"+(flag(data,"truncated")?"; list truncated to 64":"")+". Binding rechecks current state.";
    });}
}
