package dev.tacticalcombat.combat;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.context.CommandContext;
import dev.tacticalcombat.character.CharacterService;
import dev.tacticalcombat.net.SrdStatePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import java.util.*;

/** Explicit opt-in controlled encounter, never automatic survival-world activation. */
public final class SrdCommands {
    @FunctionalInterface private interface Task { String run(ServerPlayerEntity player) throws Exception; }
    private static int run(CommandContext<ServerCommandSource> c,Task task) {
        try { String message=task.run(c.getSource().getPlayerOrThrow()); c.getSource().sendFeedback(()->Text.literal(message),false); return 1; }
        catch(Exception e) { c.getSource().sendError(Text.literal(e.getMessage()==null?"SRD operation failed":e.getMessage())); return 0; }
    }
    private static SrdEncounter encounter(CommandContext<ServerCommandSource> c) {
        var encounter=SrdNetworking.find(UUID.fromString(StringArgumentType.getString(c,"encounter")));
        if(encounter==null) throw new IllegalArgumentException("SRD encounter unavailable"); return encounter;
    }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root=CommandManager.literal("srd");
        root.then(CommandManager.literal("test-public-ffa").then(CommandManager.argument("actors",StringArgumentType.greedyString()).executes(c->run(c,p->{
            if(!ServerPlayNetworking.canSend(p,SrdStatePayload.ID)) throw new IllegalArgumentException("Updated SRD v1 client required");
            UUID campaign=CharacterService.selected(p);
            if(!CharacterService.repository().manages(CharacterService.principal(p),campaign)) throw new IllegalArgumentException("Campaign manager required");
            String[] ids=StringArgumentType.getString(c,"actors").split(","); if(ids.length<2 || ids.length>16) throw new IllegalArgumentException("Supply 2–16 comma-separated actor UUIDs or online player names");
            var actors=new ArrayList<LivingEntity>();
            for(String value:ids) {
                var player=p.getServer().getPlayerManager().getPlayer(value.trim());
                var entity=player==null?p.getServerWorld().getEntity(UUID.fromString(value.trim())):player;
                if(!(entity instanceof LivingEntity living)) throw new IllegalArgumentException("Loaded actor not found: "+value);
                actors.add(living);
            }
            var encounter=SrdEncounter.start(p.getServerWorld(),CharacterService.principal(p),campaign,actors);
            try { encounter.begin(); SrdNetworking.watch(p,encounter); for(var actor:actors) if(actor instanceof ServerPlayerEntity player) SrdNetworking.watch(player,encounter); }
            catch(RuntimeException failure) { CombatManager.endCombat(encounter); throw failure; }
            dev.tacticalcombat.TacticalCombatMod.LOGGER.info("DM {} started controlled SRD encounter {} in campaign {}",p.getUuid(),encounter.id(),campaign);
            return "Controlled public-position free-for-all "+encounter.id()+". Open /srdcombat. All participants are hostile; locations public even if invisible. No reactions/environment/death saves. DM controls NPCs.";
        }))));
        root.then(CommandManager.literal("watch").then(CommandManager.argument("encounter",StringArgumentType.word()).executes(c->run(c,p->{SrdNetworking.watch(p,encounter(c));return "Open /srdcombat";}))));
        root.then(CommandManager.literal("end").then(CommandManager.argument("encounter",StringArgumentType.word()).executes(c->run(c,p->{encounter(c).stop(CharacterService.principal(p));return "Encounter ended; persistent D&D resources retained";}))));
        root.then(CommandManager.literal("retry").then(CommandManager.argument("encounter",StringArgumentType.word()).executes(c->run(c,p->{var e=encounter(c);e.retry(CharacterService.principal(p));return e.reason();}))));
        root.then(CommandManager.literal("heal").then(CommandManager.argument("encounter",StringArgumentType.word())
            .then(CommandManager.argument("actor",StringArgumentType.word()).then(CommandManager.argument("amount",IntegerArgumentType.integer(0,1024)).executes(c->run(c,p->{
                encounter(c).heal(CharacterService.principal(p),UUID.fromString(StringArgumentType.getString(c,"actor")),IntegerArgumentType.getInteger(c,"amount"));return "DM healing committed; encounter stays paused until ended";
            }))))));
        dispatcher.register(CommandManager.literal("tbc").then(root));
    }
}
