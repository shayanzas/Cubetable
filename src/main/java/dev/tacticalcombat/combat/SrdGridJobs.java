package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.CharacterService;
import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.net.SrdStatePayload;
import dev.tacticalcombat.rules.srd.SrdMovement;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import java.util.function.BiConsumer;

/** Bounded round-robin server-thread work. No worker reads Minecraft state. */
final class SrdGridJobs {
    static final int MAX_PENDING=8;
    static final long BUDGET_NANOS=8_000_000L;
    private record Job(ServerPlayerEntity player,SrdEncounter encounter,UUID request,long revision,long expires,SrdTerrain.Pending search) {}
    private static final LinkedHashMap<UUID,Job> JOBS=new LinkedHashMap<>();
    static int size() {return JOBS.size();}
    static void cancel(UUID player) {var old=JOBS.remove(player);if(old!=null) old.search.close();}
    static void clear() {for(var job:JOBS.values()) job.search.close();JOBS.clear();}
    static void submit(ServerPlayerEntity player,SrdEncounter encounter,UUID request) {
        cancel(player.getUuid());
        if(JOBS.size()>=MAX_PENDING) throw new IllegalArgumentException("Grid service busy; retry shortly");
        var search=encounter.pendingGrid(CharacterService.principal(player));
        JOBS.put(player.getUuid(),new Job(player,encounter,request,encounter.revision(),player.getServerWorld().getTime()+400,search));
    }
    static JsonObject grid(SrdMovement.Preview grid) {
        var extra=new JsonObject();var cells=new JsonArray();
        for(var n:grid.reachable().values()) {
            var cell=new JsonArray();cell.add(n.cell().x());cell.add(n.cell().z());cell.add(n.cell().y());cell.add(n.costFeet());
            if(n.parent()!=null) {cell.add(n.parent().x());cell.add(n.parent().z());cell.add(n.parent().y());}cells.add(cell);
        }
        extra.add("grid",cells);extra.addProperty("truncated",grid.truncated());return extra;
    }
    static void tick(BiConsumer<ServerPlayerEntity,SrdStatePayload> send) {
        long deadline=System.nanoTime()+BUDGET_NANOS;
        while(!JOBS.isEmpty() && System.nanoTime()<deadline) {
            var first=JOBS.entrySet().iterator();var job=first.next().getValue();first.remove();
            try {
                if(job.player.isDisconnected() || SrdNetworking.find(job.encounter.id())!=job.encounter
                        || job.encounter.revision()!=job.revision || job.player.getServerWorld().getTime()>job.expires)
                    throw new IllegalArgumentException("Grid cancelled: encounter changed or request expired; refresh");
                var principal=CharacterService.principal(job.player);job.encounter.validateGrid(principal);
                if(job.search.advance(Math.min(deadline,System.nanoTime()+1_000_000L))) {
                    var result=job.search.result();job.encounter.completedGrid(principal,result);
                    var extra=grid(result);extra.addProperty("gridRequest",job.request.toString());
                    send.accept(job.player,SrdNetworking.state(job.encounter,principal,"GRID","Grid complete"+(result.truncated()?" (search truncated)":""),extra));
                    job.search.close();
                } else JOBS.put(job.player.getUuid(),job);
            } catch(RuntimeException cancelled) {
                job.search.close();
                try {
                    send.accept(job.player,SrdNetworking.state(job.encounter,CharacterService.principal(job.player),"GRID_CANCELLED",
                            "Grid cancelled: world, authority or encounter changed; refresh",null));
                } catch(IllegalArgumentException denied) {
                    send.accept(job.player,new SrdStatePayload(job.encounter.id(),Long.MAX_VALUE,"ENDED","Encounter permission no longer available","{}"));
                }
            }
        }
    }
}
