package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.*;
import java.util.*;
import java.io.IOException;
import static dev.tacticalcombat.uiapi.UiApiService.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Stage templates and explicit short-lived placement confirmations; no encounter resurrection. */
public final class EncounterStages {
    private static EncounterStageStore store;
    private record Move(UUID actor,CharacterRepository.SrdSnapshot snapshot,Vec3d from,Vec3d to,Vec3d velocity,Box destination){}
    private record Preview(UUID token,UUID campaign,UUID stage,long revision,long expires,List<Move> moves){}
    private static final Map<UUID,Preview> previews=new HashMap<>();
    public static void initialize(java.nio.file.Path path)throws IOException{store=null;previews.clear();store=new EncounterStageStore(path);if(store.recoveredFromBackup())dev.tacticalcombat.TacticalCombatMod.LOGGER.warn("Encounter stages recovered from validated backup; review templates before activation");}
    public static void clear(){store=null;previews.clear();}
    public static void forget(UUID player){previews.remove(player);}
    private static String dimension(ServerPlayerEntity p){return p.getServerWorld().getRegistryKey().getValue().toString();}
    public static JsonObject handle(ServerPlayerEntity p,Operation op,JsonObject a)throws IOException{
        require(store!=null,"Stage storage unavailable");var repo=CharacterService.repository();var who=CharacterService.principal(p);var campaign=CharacterService.selected(p);
        require(repo.manages(who,campaign),"Current campaign DM required");var out=new JsonObject();var gson=new GsonBuilder().serializeNulls().create();
        if(op==Operation.STAGE_LIST){fields(a,"");out.add("stages",gson.toJsonTree(store.list(repo,who,campaign)));return out;}
        if(op==Operation.STAGE_CREATE){fields(a,"stage name description");var pos=p.getBlockPos();var stage=new EncounterStageStore.Stage(id(a,"stage"),1,campaign,p.getUuid(),string(a,"name"),string(a,"description"),dimension(p),pos.getX(),pos.getZ(),p.getY(),List.of(),1,"SRD5.1-2014",System.currentTimeMillis(),System.currentTimeMillis());out.add("stage",gson.toJsonTree(store.create(repo,who,stage)));return out;}
        if(op==Operation.STAGE_ACTIVATE){fields(a,"token");var preview=previews.remove(p.getUuid());require(preview!=null&&preview.token.equals(id(a,"token"))&&preview.campaign.equals(campaign)&&preview.expires>=p.getServerWorld().getTime(),"Placement confirmation expired; validate again");var stage=store.get(repo,who,preview.stage);require(stage.revision()==preview.revision,"Stage changed; validate again");var moves=validate(p,stage,preview.moves.stream().map(Move::actor).toList());require(moves.equals(preview.moves),"Actor state changed; validate again");activate(p,moves);out.addProperty("placed",moves.size());return out;}
        var stage=store.get(repo,who,id(a,"stage"));require(stage.campaign().equals(campaign),"Selected campaign mismatch");
        if(op==Operation.STAGE_GET){fields(a,"stage");out.add("stage",gson.toJsonTree(stage));return out;}
        require(number(a,"revision")==stage.revision(),"Stale stage revision");
        switch(op){
            case STAGE_ROLE->{fields(a,"stage revision slot role");var slots=new ArrayList<>(stage.slots());var slotId=id(a,"slot");boolean found=false;for(int i=0;i<slots.size();i++){var slot=slots.get(i);if(slot.id().equals(slotId)){slots.set(i,new EncounterStageStore.Slot(slot.id(),string(a,"role"),slot.x(),slot.z(),slot.y(),slot.monster(),slot.character()));found=true;}}require(found,"Slot unavailable");out.add("stage",gson.toJsonTree(store.replace(repo,who,stage.id(),stage.revision(),stage.name(),stage.description(),slots,System.currentTimeMillis())));}
            case STAGE_EDIT->{fields(a,"stage revision name description");out.add("stage",gson.toJsonTree(store.replace(repo,who,stage.id(),stage.revision(),string(a,"name"),string(a,"description"),stage.slots(),System.currentTimeMillis())));}
            case STAGE_DELETE->{fields(a,"stage revision");store.delete(repo,who,stage.id(),stage.revision());}
            case STAGE_CAPTURE->{fields(a,"stage revision actors");require(dimension(p).equals(stage.dimension())&&p.squaredDistanceTo(stage.x(),stage.y(),stage.z())<=1024,"Stage must be within 32 blocks in this dimension");var actors=actors(a);var slots=new ArrayList<EncounterStageStore.Slot>();for(var id:actors){var actor=actor(p,id);var snapshot=SrdEncounter.validateAdmission(p.getServerWorld(),campaign,actor);var cell=new SrdTerrain(actor,snapshot.stats()).admissionCell();var record=repo.character(snapshot.character());var identity=MonsterProfiles.identity(JsonParser.parseString(record.sheet()).getAsJsonObject());slots.add(new EncounterStageStore.Slot(UUID.randomUUID(),actor.getName().getString(),cell.x(),cell.z(),cell.y(),identity==null?null:identity.get("source").getAsString(),record.id()));}out.add("stage",gson.toJsonTree(store.replace(repo,who,stage.id(),stage.revision(),stage.name(),stage.description(),slots,System.currentTimeMillis())));}
            case STAGE_VALIDATE->{fields(a,"stage revision actors");var moves=validate(p,stage,actors(a));require(previews.size()<128||previews.containsKey(p.getUuid()),"Stage preview service busy");var preview=new Preview(UUID.randomUUID(),campaign,stage.id(),stage.revision(),p.getServerWorld().getTime()+400,moves);previews.put(p.getUuid(),preview);out.addProperty("token",preview.token.toString());out.addProperty("count",moves.size());}
            default->throw new IllegalArgumentException("Unsupported stage operation");
        }return out;
    }
    private static List<UUID> actors(JsonObject a){var array=a.getAsJsonArray("actors");require(array.size()>0&&array.size()<=16,"Choose 1–16 actors");var result=new ArrayList<UUID>();for(var value:array){var id=UUID.fromString(value.getAsString());require(!result.contains(id),"Duplicate actor");result.add(id);}return List.copyOf(result);}
    private static LivingEntity actor(ServerPlayerEntity p,UUID id){var entity=p.getServerWorld().getEntity(id);require(entity instanceof LivingEntity&&entity.squaredDistanceTo(p)<=1024,"Loaded actor within 32 blocks required");return (LivingEntity)entity;}
    private static List<Move> validate(ServerPlayerEntity p,EncounterStageStore.Stage stage,List<UUID> actors){
        require(dimension(p).equals(stage.dimension())&&p.squaredDistanceTo(stage.x(),stage.y(),stage.z())<=1024,"Stage must be within 32 blocks in this dimension");require(!stage.slots().isEmpty()&&actors.size()==stage.slots().size(),"Choose one actor per slot, in slot order");var moves=new ArrayList<Move>();var repo=CharacterService.repository();
        for(int i=0;i<actors.size();i++){var slot=stage.slots().get(i);var actor=actor(p,actors.get(i));var snapshot=SrdEncounter.validateAdmission(p.getServerWorld(),stage.campaign(),actor);var identity=MonsterProfiles.identity(JsonParser.parseString(repo.character(snapshot.character()).sheet()).getAsJsonObject());
            if(slot.monster()!=null)require(identity!=null&&slot.monster().equals(identity.get("source").getAsString()),"Monster source incompatible with slot");else if(slot.character()!=null)require(slot.character().equals(snapshot.character()),"Slot requires its original campaign character");
            var terrain=new SrdTerrain(actor,snapshot.stats());var cell=new SrdMovement.Cell(slot.x(),slot.z(),slot.y());var to=terrain.position(cell);require(actor.getPos().squaredDistanceTo(to)<=256,"Placement exceeds 16-block limit");require(terrain.landing(cell)==SrdMovement.Reason.OK,"Unsafe/occupied/unloaded stage destination");
            double size=SrdTerrain.footprint(snapshot.stats().size());double width=Math.max(size,actor.getWidth());var box=new Box(to.x-width/2,to.y,to.z-width/2,to.x+width/2,to.y+actor.getHeight(),to.z+width/2).contract(1e-5);for(var other:moves)require(!box.intersects(other.destination),"Placement footprints overlap");moves.add(new Move(actor.getUuid(),snapshot,actor.getPos(),to,actor.getVelocity(),box));
        }return List.copyOf(moves);
    }
    static java.util.function.IntConsumer activationFault=index->{};
    private static void activate(ServerPlayerEntity p,List<Move> moves){
        var moved=new ArrayList<Move>();try{for(var move:moves){var actor=actor(p,move.actor);moved.add(move);actor.requestTeleport(move.to.x,move.to.y,move.to.z);actor.setVelocity(Vec3d.ZERO);require(actor.getPos().squaredDistanceTo(move.to)<1e-8,"Actor placement failed");activationFault.accept(moved.size());}}
        catch(RuntimeException failure){boolean restored=true;for(var move:moved){try{var actor=actor(p,move.actor);actor.requestTeleport(move.from.x,move.from.y,move.from.z);actor.setVelocity(move.velocity);}catch(RuntimeException lost){restored=false;}}throw new IllegalArgumentException(restored?"Activation failed; prior positions restored":"Activation failed; some actors could not be restored. Inspect the test world before retrying",failure);}
    }
    private EncounterStages(){}
}
