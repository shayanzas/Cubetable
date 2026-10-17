package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.client.SrdCombatScreen;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Selection submits identities and a one-use discovery token; eligibility is never trusted locally. */
public final class EncounterLauncherScreen extends ManagementScreen {
    private final boolean creating;
    private final Set<String> selected=new LinkedHashSet<>();
    private JsonArray candidates=new JsonArray();
    private String discovery="";
    private RpgButton start;
    private dev.tacticalcombat.combat.NearbyActors.Binding binding=dev.tacticalcombat.combat.NearbyActors.Binding.ALL;
    private dev.tacticalcombat.combat.NearbyActors.Kind kind=dev.tacticalcombat.combat.NearbyActors.Kind.ALL;
    public EncounterLauncherScreen(Screen parent,boolean creating){super(creating?"New Encounter":"Controlled Encounters",parent);this.creating=creating;}
    @Override protected UiLayout.Rect listBounds(){return new UiLayout.Rect(frame.x()+12,frame.y()+88,frame.width()-24,Math.max(30,frame.height()-154));}
    @Override protected void controls(){if(creating){button("Filter: "+binding,"",frame.x()+12,frame.y()+62,(frame.width()-28)/2,20,()->{binding=dev.tacticalcombat.combat.NearbyActors.Binding.values()[(binding.ordinal()+1)%4];client.setScreen(this);});button("Type: "+kind,"",frame.x()+16+(frame.width()-28)/2,frame.y()+62,(frame.width()-28)/2,20,()->{kind=dev.tacticalcombat.combat.NearbyActors.Kind.values()[(kind.ordinal()+1)%3];client.setScreen(this);});}if(creating)start=button("Start Encounter","",frame.x()+112,frame.y()+frame.height()-38,Math.max(120,frame.width()-124),22,this::start).primary(true);else button("New Encounter","",frame.x()+112,frame.y()+frame.height()-38,Math.max(120,frame.width()-124),22,()->client.setScreen(new EncounterLauncherScreen(this,true)));}
    @Override protected void refresh(){if(busy)return;fresh=false;selected.clear();read(creating?ENCOUNTER_DISCOVER:ENCOUNTER_LIST,creating?args("binding",binding,"kind",kind):args(),data->{if(data.has("panelToken"))dev.tacticalcombat.client.ClientRulesWatch.panel(this,UUID.fromString(text(data,"panelToken")),revoked->{generation++;busy=false;fresh=false;selected.clear();candidates=new JsonArray();clearRows();filter();message=revoked?"Campaign manager access changed. Refresh to recheck.":"Encounter participants or campaign changed. Refresh before selecting again.";});clearRows();if(creating){discovery=text(data,"discovery");candidates=array(data,"actors");populate();message="Within "+text(data,"radius")+" blocks · select 2–16 · "+(flag(data,"truncated")?"list limited; move closer and Refresh":"Refresh if actors move or change")+". Safe nearby anchors checked at Start.";}else{for(var value:array(data,"encounters")){var e=value.getAsJsonObject();row(text(e,"state")+" · "+text(e,"actors"),text(e,"reason"),true,"View existing combat controls",()->read(ENCOUNTER_WATCH,args("encounter",text(e,"encounter")),r->client.setScreen(new SrdCombatScreen(this))));}if(rows.isEmpty())info("No active controlled encounters","Use New Encounter to select approved, bound participants.");filter();}});}
    private void populate(){clearRows();info("Select participants · "+selected.size()+" / 16","Controlled free-for-all. Start safely aligns supported actors by at most half a block per axis; no walls or occupied space crossed.");for(var value:candidates){var actor=value.getAsJsonObject();String id=text(actor,"actor");String status=flag(actor,"eligible")?"Ready":text(actor,"reason");row((selected.contains(id)?"✓ ":"□ ")+text(actor,"name")+" · "+text(actor,"distance")+"m",text(actor,"kind")+" · "+(flag(actor,"bound")?text(actor,"characterName"):"Unbound")+" · "+status,flag(actor,"eligible"),text(actor,"type")+" @ "+text(actor,"position")+" · "+status,()->{if(!selected.remove(id)&&selected.size()<16)selected.add(id);populate();});if(actor.has("monsterSupport"))info("Monster mechanics · "+text(actor,"name"),text(actor,"monsterSupport"));if(flag(actor,"canBind"))row("Bind Character → "+text(actor,"name"),"Select an approved campaign character",true,"No automatic monster actions",()->client.setScreen(new ActorBindingScreen(this,id)));}filter();}
    private void start(){if(busy||!fresh||selected.size()<2)return;var a=args("discovery",discovery);var ids=new JsonArray();selected.forEach(ids::add);a.add("actors",ids);fresh=false;request(ENCOUNTER_START,a,r->{selected.clear();discovery="";if(r.code().equals("OK"))client.setScreen(new SrdCombatScreen(parent));else{clearRows();filter();message=r.message()+" Refresh and select again. A pending initiative tie is resolved in combat controls.";}});}
    @Override public void removed(){dev.tacticalcombat.client.ClientRulesWatch.close(this);super.removed();}
    @Override public void tick(){super.tick();if(start!=null)start.active=fresh&&!busy&&selected.size()>=2&&selected.size()<=16;}
}
