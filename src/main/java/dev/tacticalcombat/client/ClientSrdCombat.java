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
    static JsonObject state=new JsonObject(),preview,reactionPreview;
    private static String announcedOpportunity="";
    static String lastResult="",lastResultId="",lastResultSummary="";
    static String message="No SRD encounter selected";
    static String lastFailure="";
    static long failureSerial;
    static final SrdMovementPreview movement=new SrdMovementPreview();
    static SrdMovement.Preview grid;
    static long gridRevision=-1;
    private static final SrdReplyGate replies=new SrdReplyGate();
    private static final SrdPreviewGate previewGate=new SrdPreviewGate();
    static void cancelPreview(){preview=null;previewGate.clear();}
    static boolean validPreview(String target){return SrdBattlefieldActions.matchesPreview(state,preview,target,revision);}
    private static final SrdGridRequests gridRequests=new SrdGridRequests();
    private static final SrdGridRefresh refresh=new SrdGridRefresh();
    static long ticks,opportunityReceivedTick;
    static final SrdRecentActivity activity=new SrdRecentActivity();
    static String pending="";
    private static boolean reconciling;
    static SrdMovement.Cell hover;
    static net.minecraft.util.math.Vec3d unavailableHover;
    static SrdMovement.Cell selected;
    static int focusEntity=-1,hoverEntity=-1;
    private static final LinkedHashSet<String> presented=new LinkedHashSet<>();
    private ClientSrdCombat() {}
    public static JsonObject workspaceState(){return state.deepCopy();}
    public static void battlefield(){MinecraftClient.getInstance().setScreen(new SrdBattlefieldScreen());}
    public static SrdMovementPreview.Details movementDetails(){return movement.details(selected!=null?selected:hover);}
    public static boolean hideVanillaHotbar(){var mc=MinecraftClient.getInstance();return SrdBattlefieldPresentation.hideHotbar(SrdCamera.engaged(),ClientCombatState.active,(mc.currentScreen instanceof SrdBattlefieldScreen||mc.currentScreen instanceof SrdReactionScreen),mc.player!=null&&mc.world!=null);}
    public static UUID hudEncounter() {return encounter;}
    public static void clear() { reactionPreview=null;announcedOpportunity=""; encounter=null; revision=-1; turn=0; state=new JsonObject(); preview=null; grid=null; focusEntity=hoverEntity=-1;presented.clear();movement.clear();gridRequests.clear();refresh.clear();selected=null;lastResult="";lastResultId="";lastResultSummary="";activity.clear();pending="";reconciling=false;hover=null;unavailableHover=null;lastFailure="";failureSerial=0;SrdCamera.clear();SrdMotion.clear();SrdCombatEffects.clear();previewGate.clear();replies.clear();gridRevision=-1;BlockFade.clear(); }
    public static void receive(SrdStatePayload payload) {
        if(encounter!=null&&!encounter.equals(payload.encounter())&&Set.of("REJECTED","STORAGE_ERROR","PREVIEW").contains(payload.code()))return;
        boolean matchingReply=true;
        try{if(!payload.json().equals("{}")){CharacterRepository.checkDepth(payload.json(),12);var header=JsonParser.parseString(payload.json()).getAsJsonObject();if(header.has("replyRequest"))matchingReply=replies.matches(header.get("replyRequest").getAsString());}}catch(RuntimeException invalid){return;}
        if(Set.of("REJECTED","STORAGE_ERROR").contains(payload.code())&&(!matchingReply||payload.encounter().equals(encounter)&&payload.revision()<revision))return;
        if(payload.code().equals("RECEIPT")){message=payload.message();return;} // Closed encounter receipt never reconstructs world/combat state.
        if(payload.code().equals("ENDED")) {
            if(payload.encounter().equals(encounter)){clear();var player=MinecraftClient.getInstance().player;if(player!=null)player.sendMessage(net.minecraft.text.Text.literal(payload.message()),true);} message=payload.message(); return;
        }
        if(payload.code().equals("REJECTED") || payload.code().equals("STORAGE_ERROR")) { message=payload.message();lastFailure=payload.message();failureSerial++;preview=null;reactionPreview=null;
            if(pending.equals("move")){grid=null;selected=null;hover=null;movement.clear();gridRequests.clear();refresh.clear();}
            movement.context(revision,message,grid==null);pending="";activity.add("denied:"+ticks,message,ticks); return; }
        if(payload.encounter().equals(encounter) && payload.revision()<revision) return;
        try {
            CharacterRepository.checkDepth(payload.json(),12);
            var data=JsonParser.parseString(payload.json()).getAsJsonObject();
            boolean currentGrid=gridRequests.accepts(payload.encounter(),data.has("gridRequest")?data.get("gridRequest").getAsString():null);
            if(data.getAsJsonArray("actors").size()>16) throw new IllegalArgumentException("Too many SRD actors");
            boolean changed=!payload.encounter().equals(encounter) || payload.revision()!=revision || !data.get("actors").equals(state.get("actors"));
            if(!payload.encounter().equals(encounter)) {presented.clear();lastResult="";lastResultId="";lastResultSummary="";activity.clear();pending="";}
            if(!SrdViewPolicy.permissions(data).equals(SrdViewPolicy.permissions(state))){lastResult="";lastResultId="";lastResultSummary="";lastFailure="";activity.clear();SrdCombatEffects.clear();}
            if(!Objects.equals(data.get("active"),state.get("active"))){var a=SrdViewPolicy.current(data);if(a!=null)activity.add("turn:"+data.get("turn"),"Turn: "+a.get("name").getAsString(),ticks);}
            if(data.has("remainingFeet") && state.has("remainingFeet") && Objects.equals(data.get("active"),state.get("active")) && data.get("remainingFeet").getAsInt()<state.get("remainingFeet").getAsInt())activity.add("move:"+payload.revision(),"Movement committed · "+data.get("remainingFeet")+" ft remaining",ticks);
            if(changed) { reactionPreview=null;preview=null; grid=null; selected=null;hover=null; movement.clear(); }
            if(!Objects.equals(data.get("active"),state.get("active"))||!Objects.equals(data.get("turn"),state.get("turn"))||!SrdViewPolicy.permissions(data).equals(SrdViewPolicy.permissions(state))){previewGate.clear();replies.clear();pending="";reconciling=false;}
            if(pending.startsWith("reaction_")&&(!data.has("opportunity")||state.has("opportunity")&&!data.getAsJsonObject("opportunity").get("id").equals(state.getAsJsonObject("opportunity").get("id")))){pending="";replies.clear();}
            encounter=payload.encounter(); revision=payload.revision(); state=data; turn=data.get("turn").getAsLong();
            SrdMotion.accept(encounter.toString(),data);
            if(currentGrid) message=payload.message();
            if((matchingReply&&Set.of("OK","DASH","DODGE","DISENGAGE","REACTION_PREVIEW","REACTION_DECLINED","POSTURE","PREVIEW","RESULT","SAVE_FAILED","PAUSED").contains(payload.code()) || payload.code().equals("STATE")&&reconciling)){pending="";reconciling=false;}
            if(!canControl()) {grid=null;selected=null;preview=null;movement.clear();}
            if(currentGrid && (payload.code().equals("GRID_PENDING") || payload.code().equals("GRID_CANCELLED"))) {grid=null;movement.clear();}
            if(payload.code().equals("POSTURE")){message=payload.message();activity.add("posture:"+revision,message,ticks);}
            if(payload.code().equals("DODGE")){message=payload.message();activity.add("dodge:"+turn,message,ticks);}
            if(payload.code().equals("DASH")){message=payload.message();activity.add("dash:"+turn,message,ticks);}
            if(data.has("state")&&data.get("state").getAsString().equals("PAUSED"))activity.add("pause:"+revision,"Encounter paused — DM adjudication required",ticks);
            if(data.has("preview")&&data.has("previewRequest")&&previewGate.accepts(encounter.toString(),turn,data.get("active").getAsString(),data.get("previewRequest").getAsString(),data.getAsJsonObject("preview"))){
                preview=data.getAsJsonObject("preview");activity.add("preview:"+preview.get("id"),"Preview ready · "+preview.get("attack").getAsString()+" · "+preview.get("range").getAsString(),ticks);
            }
            if(!data.has("opportunity"))reactionPreview=null;
            if(matchingReply&&data.has("reactionPreview")&&data.has("opportunity")){
                var candidate=data.getAsJsonObject("reactionPreview");var prompt=data.getAsJsonObject("opportunity");
                if(candidate.get("trigger").equals(prompt.get("id"))&&candidate.get("actor").equals(prompt.get("reactor"))&&candidate.get("target").equals(prompt.get("target"))&&candidate.get("revision").getAsLong()==revision)reactionPreview=candidate;
            }
            if(payload.code().equals("DISENGAGE")||payload.code().equals("REACTION_DECLINED")){message=payload.message();activity.add("reaction:"+revision,message,ticks);}
            if(data.has("opportunity")){
                opportunityReceivedTick=ticks;String offered=data.getAsJsonObject("opportunity").get("id").getAsString();
                if(!offered.equals(announcedOpportunity)){announcedOpportunity=offered;var mc=MinecraftClient.getInstance();mc.setScreen(new SrdReactionScreen(mc.currentScreen));}
            }
            if(data.has("result")) {
                var result=data.getAsJsonObject("result"); message=SrdAttackFeedback.describe(result,actors());lastResultSummary=SrdAttackFeedback.summary(result,actors());lastResult=message;lastResultId=result.get("action").getAsString();
                var player=MinecraftClient.getInstance().player;
                if(presented.size()<256&&presented.add(result.get("action").getAsString())) {
                    boolean fresh=(!data.has("replayed")||!data.get("replayed").getAsBoolean())&&result.has("turn")&&result.get("turn").getAsLong()==turn;
                    if(fresh)SrdCombatEffects.accept(result,ticks);
                    activity.add("attack:"+result.get("action").getAsString(),message,ticks);

                    if(player!=null && (!dev.tacticalcombat.client.ui.CubetableHud.campaignEvents() || dev.tacticalcombat.client.ui.CubetableHud.options().diceChat())) player.sendMessage(net.minecraft.text.Text.literal(message+"; d20 faces "+result.get("faces")+"; damage dice "+result.get("damageFaces")),false);
                    var faces=result.getAsJsonArray("faces");
                    if(fresh&&(!dev.tacticalcombat.client.ui.CubetableHud.campaignEvents() || dev.tacticalcombat.client.ui.CubetableHud.options().legacyDice()) && faces.size()>=1 && faces.size()<=2) dev.tacticalcombat.client.ui.CubetableHud.legacy(new DiceRollPayload("SRD server","Accepted attack",dev.tacticalcombat.dice.DiceType.D20,
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
                var boundaries=new ArrayList<SrdMovement.Boundary>();
                if(data.has("gridBoundaries")){
                    var list=data.getAsJsonArray("gridBoundaries");if(list.size()>256)throw new IllegalArgumentException("Too many boundaries");
                    for(var value:list){var b=value.getAsJsonArray();if(b.size()!=6)throw new IllegalArgumentException("Invalid boundary");
                        int dx=b.get(3).getAsInt(),dz=b.get(4).getAsInt();if(Math.abs(dx)>1||Math.abs(dz)>1||dx==0&&dz==0)throw new IllegalArgumentException("Invalid boundary direction");
                        boundaries.add(new SrdMovement.Boundary(new SrdMovement.Cell(b.get(0).getAsInt(),b.get(1).getAsInt(),b.get(2).getAsDouble()),dx,dz,SrdMovement.Reason.valueOf(b.get(5).getAsString())));
                    }
                }
                grid=new SrdMovement.Preview(Objects.requireNonNull(start),data.get("remainingFeet").getAsInt(),nodes,boundaries,data.get("truncated").getAsBoolean());
                gridRevision=revision;movement.apply(RulesProfiles.SRD,grid);selected=SrdBattlefieldModel.retain(selected,grid);
            }
        movement.context(revision,data.has("routeStatus")?data.get("routeStatus").getAsString():moving()?"Executing":"Idle",grid==null);
        } catch(RuntimeException invalid) { clear(); message="Invalid SRD server state; refresh required"; }
    }
    static void refreshReadOnly(){refresh.clear();gridRequests.clear();grid=null;movement.clear();send("state");}
    static void send(String operation,JsonObject args) {
        if(encounter==null || !ClientPlayNetworking.canSend(SrdRequestPayload.ID)) { message="Updated SRD server required"; return; }
        if(operation.equals("state"))reconciling=true;
        boolean mutation=!operation.equals("grid")&&!operation.equals("state");
        if(mutation&&!pending.isEmpty()){message="Waiting for server: "+pending+". Refresh state if needed.";return;}
        if(mutation)pending=operation;
        UUID request=UUID.randomUUID();
        if(mutation)replies.expect(request);
        if(operation.equals("preview"))previewGate.expect(request,encounter.toString(),turn,state.get("active").getAsString(),args.get("target").getAsString());
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
