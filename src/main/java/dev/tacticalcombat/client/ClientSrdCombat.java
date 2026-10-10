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
    static String message="No SRD encounter selected";
    static final SrdMovementPreview movement=new SrdMovementPreview();
    static SrdMovement.Preview grid;
    static int focusEntity=-1;
    private static final LinkedHashSet<String> presented=new LinkedHashSet<>();
    private ClientSrdCombat() {}
    public static void clear() { encounter=null; revision=-1; turn=0; state=new JsonObject(); preview=null; grid=null; focusEntity=-1;presented.clear();movement.clear(); }
    public static void receive(SrdStatePayload payload) {
        if(payload.code().equals("ENDED")) {
            if(payload.encounter().equals(encounter)) clear(); message=payload.message(); return;
        }
        if(payload.code().equals("REJECTED") || payload.code().equals("STORAGE_ERROR")) { message=payload.message(); preview=null; return; }
        if(payload.encounter().equals(encounter) && payload.revision()<revision) return;
        try {
            CharacterRepository.checkDepth(payload.json(),12);
            var data=JsonParser.parseString(payload.json()).getAsJsonObject();
            if(data.getAsJsonArray("actors").size()>16) throw new IllegalArgumentException("Too many SRD actors");
            boolean changed=!payload.encounter().equals(encounter) || payload.revision()!=revision;
            if(!payload.encounter().equals(encounter)) presented.clear();
            if(changed) { preview=null; grid=null; movement.clear(); }
            encounter=payload.encounter(); revision=payload.revision(); state=data; turn=data.get("turn").getAsLong(); message=payload.message();
            if(data.has("preview")) preview=data.getAsJsonObject("preview");
            if(data.has("result")) {
                var result=data.getAsJsonObject("result"); message="Authoritative attack: "+result.get("total")+(result.get("critical").getAsBoolean()?" critical hit":result.get("hit").getAsBoolean()?" hit":" miss");
                var player=MinecraftClient.getInstance().player;
                if(presented.add(result.get("action").getAsString())) {
                    if(presented.size()>32) presented.remove(presented.iterator().next());
                    if(player!=null) player.sendMessage(net.minecraft.text.Text.literal(message+"; d20 faces "+result.get("faces")+"; damage dice "+result.get("damageFaces")),false);
                    var faces=result.getAsJsonArray("faces");
                    if(faces.size()>=1 && faces.size()<=2) DiceAnimation.enqueue(new DiceRollPayload("SRD server","Accepted attack",dev.tacticalcombat.dice.DiceType.D20,
                            result.get("total").getAsInt()-result.get("chosen").getAsInt(),result.get("keep").getAsInt(),faces.asList().stream().mapToInt(JsonElement::getAsInt).toArray()));
                }
            }
            if(data.has("grid")) {
                var cells=data.getAsJsonArray("grid"); if(cells.size()>SrdMovement.MAX_NODES) throw new IllegalArgumentException("Grid too large");
                var nodes=new HashMap<SrdMovement.Cell,SrdMovement.Node>(); SrdMovement.Cell start=null;
                for(var value:cells) {
                    var n=value.getAsJsonArray(); if(n.size()!=4 && n.size()!=7) throw new IllegalArgumentException("Invalid node");
                    var cell=new SrdMovement.Cell(n.get(0).getAsInt(),n.get(1).getAsInt(),n.get(2).getAsDouble());
                    var parent=n.size()==4?null:new SrdMovement.Cell(n.get(4).getAsInt(),n.get(5).getAsInt(),n.get(6).getAsDouble());
                    nodes.put(cell,new SrdMovement.Node(cell,n.get(3).getAsInt(),parent)); if(parent==null) start=cell;
                }
                grid=new SrdMovement.Preview(Objects.requireNonNull(start),data.get("remainingFeet").getAsInt(),nodes,List.of(),data.get("truncated").getAsBoolean());
                movement.apply(RulesProfiles.SRD,grid);
            }
        } catch(RuntimeException invalid) { clear(); message="Invalid SRD server state; refresh required"; }
    }
    static void send(String operation,JsonObject args) {
        if(encounter==null || !ClientPlayNetworking.canSend(SrdRequestPayload.ID)) { message="Updated SRD server required"; return; }
        ClientPlayNetworking.send(new SrdRequestPayload(encounter,turn,UUID.randomUUID(),operation,args.toString()));
    }
    static void send(String operation) { send(operation,new JsonObject()); }
    static JsonArray actors() { return state.has("actors")?state.getAsJsonArray("actors"):new JsonArray(); }
    static JsonObject current() { for(var a:actors()) if(a.getAsJsonObject().get("id").getAsString().equals(state.get("active").getAsString())) return a.getAsJsonObject(); return null; }
    public static void open() { MinecraftClient.getInstance().setScreen(new SrdCombatScreen()); }
}
