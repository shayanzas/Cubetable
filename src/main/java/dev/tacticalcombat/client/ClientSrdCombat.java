package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.character.CharacterRepository;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdMovement;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import java.util.*;

/** Isolated client state. A preview is never an accepted action. */
public final class ClientSrdCombat {
    static UUID encounter;
    static long revision=-1,turn;
    static JsonObject state=new JsonObject(),preview;
    static String lastResult="",lastResultId="";
    static String message="No SRD encounter selected";
    static final SrdMovementPreview movement=new SrdMovementPreview();
    static SrdMovement.Preview grid;
    private static final SrdGridRequests gridRequests=new SrdGridRequests();
    private static final SrdGridRefresh refresh=new SrdGridRefresh();
    static long ticks;
    static final SrdRecentActivity activity=new SrdRecentActivity();
    static String pending="";
    private static boolean reconciling;
    static SrdMovement.Cell hover;
    static net.minecraft.util.math.Vec3d unavailableHover;
    static SrdMovement.Cell selected;
    static int focusEntity=-1;
    private static final LinkedHashSet<String> presented=new LinkedHashSet<>();
    private ClientSrdCombat() {}
    public static JsonObject workspaceState(){return state.deepCopy();}
    public static void battlefield(){MinecraftClient.getInstance().setScreen(new SrdBattlefieldScreen());}
    public static UUID hudEncounter() {return encounter;}
    public static void clear() { encounter=null; revision=-1; turn=0; state=new JsonObject(); preview=null; grid=null; focusEntity=-1;presented.clear();movement.clear();gridRequests.clear();refresh.clear();selected=null;lastResult="";lastResultId="";activity.clear();pending="";reconciling=false;hover=null;unavailableHover=null;SrdCamera.clear(); }
    public static void receive(SrdStatePayload payload) {
        if(payload.code().equals("RECEIPT")){message=payload.message();return;} // Closed encounter receipt never reconstructs world/combat state.
        if(payload.code().equals("ENDED")) {
            if(payload.encounter().equals(encounter)) clear(); message=payload.message(); return;
        }
        if(payload.code().equals("REJECTED") || payload.code().equals("STORAGE_ERROR")) { message=payload.message(); preview=null; pending="";activity.add("denied:"+ticks,message,ticks); return; }
        if(payload.encounter().equals(encounter) && payload.revision()<revision) return;
        try {
            CharacterRepository.checkDepth(payload.json(),12);
            var data=JsonParser.parseString(payload.json()).getAsJsonObject();
            boolean currentGrid=gridRequests.accepts(payload.encounter(),data.has("gridRequest")?data.get("gridRequest").getAsString():null);
            if(data.getAsJsonArray("actors").size()>16) throw new IllegalArgumentException("Too many SRD actors");
            boolean changed=!payload.encounter().equals(encounter) || payload.revision()!=revision || !data.get("actors").equals(state.get("actors"));
            if(!payload.encounter().equals(encounter)) {presented.clear();lastResult="";lastResultId="";activity.clear();pending="";}
            if(!SrdViewPolicy.permissions(data).equals(SrdViewPolicy.permissions(state))){lastResult="";lastResultId="";activity.clear();}
            if(!Objects.equals(data.get("active"),state.get("active"))){var a=SrdViewPolicy.current(data);if(a!=null)activity.add("turn:"+data.get("turn"),"Turn: "+a.get("name").getAsString(),ticks);}
            if(data.has("remainingFeet") && state.has("remainingFeet") && Objects.equals(data.get("active"),state.get("active")) && data.get("remainingFeet").getAsInt()<state.get("remainingFeet").getAsInt())activity.add("move:"+payload.revision(),"Movement committed · "+data.get("remainingFeet")+" ft remaining",ticks);
            if(changed) { preview=null; grid=null; selected=null;hover=null; movement.clear(); }
            encounter=payload.encounter(); revision=payload.revision(); state=data; turn=data.get("turn").getAsLong();
            if(currentGrid) message=payload.message();
            if((Set.of("OK","DASH","PREVIEW","RESULT","SAVE_FAILED","PAUSED").contains(payload.code()) || payload.code().equals("STATE")&&reconciling)){pending="";reconciling=false;}
            if(!canControl()) {grid=null;selected=null;preview=null;movement.clear();}
            if(currentGrid && (payload.code().equals("GRID_PENDING") || payload.code().equals("GRID_CANCELLED"))) {grid=null;movement.clear();}
            if(payload.code().equals("DASH")){message=payload.message();activity.add("dash:"+turn,message,ticks);}
            if(data.has("preview")) preview=data.getAsJsonObject("preview");
            if(data.has("result")) {
                var result=data.getAsJsonObject("result"); message=SrdAttackFeedback.describe(result,actors());lastResult=message;lastResultId=result.get("action").getAsString();
                var player=MinecraftClient.getInstance().player;
                if(presented.add(result.get("action").getAsString())) {
                    activity.add("attack:"+result.get("action").getAsString(),message,ticks);
                    if(presented.size()>32) presented.remove(presented.iterator().next());
                    if(player!=null && (!dev.tacticalcombat.client.ui.CubetableHud.campaignEvents() || dev.tacticalcombat.client.ui.CubetableHud.options().diceChat())) player.sendMessage(net.minecraft.text.Text.literal(message+"; d20 faces "+result.get("faces")+"; damage dice "+result.get("damageFaces")),false);
                    var faces=result.getAsJsonArray("faces");
                    if((!dev.tacticalcombat.client.ui.CubetableHud.campaignEvents() || dev.tacticalcombat.client.ui.CubetableHud.options().legacyDice()) && faces.size()>=1 && faces.size()<=2) dev.tacticalcombat.client.ui.CubetableHud.legacy(new DiceRollPayload("SRD server","Accepted attack",dev.tacticalcombat.dice.DiceType.D20,
                            result.get("total").getAsInt()-result.get("chosen").getAsInt(),result.get("keep").getAsInt(),faces.asList().stream().mapToInt(JsonElement::getAsInt).toArray()));
                }
            }
            if(data.has("grid") && currentGrid && canControl()) {
                var cells=data.getAsJsonArray("grid"); if(cells.size()>SrdMovement.MAX_NODES) throw new IllegalArgumentException("Grid too large");
                var nodes=new HashMap<SrdMovement.Cell,SrdMovement.Node>(); SrdMovement.Cell start=null;
                for(var value:cells) {
                    var n=value.getAsJsonArray(); if(n.size()!=4 && n.size()!=7) throw new IllegalArgumentException("Invalid node");
                    var cell=new SrdMovement.Cell(n.get(0).getAsInt(),n.get(1).getAsInt(),n.get(2).getAsDouble());
                    var parent=n.size()==4?null:new SrdMovement.Cell(n.get(4).getAsInt(),n.get(5).getAsInt(),n.get(6).getAsDouble());
                    nodes.put(cell,new SrdMovement.Node(cell,n.get(3).getAsInt(),parent)); if(parent==null) start=cell;
                }
                grid=new SrdMovement.Preview(Objects.requireNonNull(start),data.get("remainingFeet").getAsInt(),nodes,List.of(),data.get("truncated").getAsBoolean());
                movement.apply(RulesProfiles.SRD,grid);selected=SrdBattlefieldModel.retain(selected,grid);
            }
        } catch(RuntimeException invalid) { clear(); message="Invalid SRD server state; refresh required"; }
    }
    static void send(String operation,JsonObject args) {
        if(encounter==null || !ClientPlayNetworking.canSend(SrdRequestPayload.ID)) { message="Updated SRD server required"; return; }
        if(operation.equals("state"))reconciling=true;
        boolean mutation=!operation.equals("grid")&&!operation.equals("state");
        if(mutation&&!pending.isEmpty()){message="Waiting for server: "+pending+". Refresh state if needed.";return;}
        if(mutation)pending=operation;
        UUID request=UUID.randomUUID();
        if(operation.equals("grid")) {refresh.requested(encounter,revision,turn,ticks);gridRequests.expect(encounter,request);message="Requesting grid…";}
        ClientPlayNetworking.send(new SrdRequestPayload(encounter,turn,request,operation,args.toString()));
    }
    public static boolean localParticipant(){var p=MinecraftClient.getInstance().player;return p!=null && SrdViewPolicy.participant(state,p.getUuidAsString());}
    static boolean active() {return encounter!=null && state.has("state") && !state.get("state").getAsString().equals("ENDED");}
    static boolean canControl() {return active() && SrdViewPolicy.canControl(state); }
    static boolean moving() {return state.has("moving") && state.get("moving").getAsBoolean();}
    static void tick() {
        ticks++;
        boolean eligible=pending.isEmpty() && SrdCamera.activeEntity(MinecraftClient.getInstance())!=null && canControl() && !moving() && state.get("remainingFeet").getAsInt()>=5;
        if(refresh.due(encounter,revision,turn,eligible,ticks))send("grid");
    }
    static void send(String operation) { send(operation,new JsonObject()); }
    static JsonArray actors() { return state.has("actors")?state.getAsJsonArray("actors"):new JsonArray(); }
    static JsonObject current() {return SrdViewPolicy.current(state);}
    public static void open() { MinecraftClient.getInstance().setScreen(SrdCombatScreen.entry(null)); }
}
