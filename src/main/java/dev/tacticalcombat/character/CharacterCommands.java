package dev.tacticalcombat.character;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.*;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import dev.tacticalcombat.combat.CombatManager;
import dev.tacticalcombat.rules.RulesProfiles;
import java.util.UUID;

/** Commands are the first server management UI; permissions are rechecked in repository mutations. */
public final class CharacterCommands {
    @FunctionalInterface private interface Task { String run() throws Exception; }
    private static int run(CommandContext<ServerCommandSource> ctx,Task task) {
        try {
            String message=task.run();
            var words=ctx.getInput().split(" ",4);
            String group=words.length>1?words[1]:"", operation=words.length>2?words[2]:"";
            if(!java.util.Set.of("list","show","select").contains(operation))
                dev.tacticalcombat.TacticalCombatMod.LOGGER.info("Character/campaign command by {}: {} {}",
                        ctx.getSource().getName(),group,operation);
            ctx.getSource().sendFeedback(()->Text.literal(message),false); return 1;
        }
        catch(Exception e) { ctx.getSource().sendError(Text.literal(e.getMessage()==null?"Character operation failed":e.getMessage())); return 0; }
    }
    private static UUID id(CommandContext<ServerCommandSource> c,String name) { return UUID.fromString(StringArgumentType.getString(c,name)); }
    private static CharacterRepository repo() { return CharacterService.repository(); }
    private static ServerPlayerEntity player(CommandContext<ServerCommandSource> c) throws Exception { return c.getSource().getPlayerOrThrow(); }
    private static CharacterRepository.Principal who(CommandContext<ServerCommandSource> c) throws Exception { return CharacterService.principal(player(c)); }
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        var root=CommandManager.literal("tbc");
        root.then(CommandManager.literal("campaign")
            .then(CommandManager.literal("create").requires(s->s.hasPermissionLevel(2))
                .then(CommandManager.argument("name",StringArgumentType.greedyString()).executes(c->run(c,()->{
                    UUID campaign=repo().createCampaign(who(c),StringArgumentType.getString(c,"name"));
                    CharacterService.select(player(c),campaign); return "Created and selected campaign "+campaign;
                }))))
            .then(CommandManager.literal("list").executes(c->run(c,()->{
                var p=who(c); return repo().state().campaigns().values().stream().filter(x->repo().member(p,x.id()))
                    .map(x->x.id()+" "+x.name()+" ["+x.profile()+"] revision="+x.revision()).collect(java.util.stream.Collectors.joining("\n"));
            })))
            .then(CommandManager.literal("select").then(CommandManager.argument("campaign",StringArgumentType.word()).executes(c->run(c,()->{
                CharacterService.select(player(c),id(c,"campaign")); return "Campaign selected.";
            }))))
            .then(CommandManager.literal("invite").then(CommandManager.argument("target",EntityArgumentType.player()).executes(c->run(c,()->{
                UUID campaign=CharacterService.selected(player(c)); var current=repo().campaign(campaign);
                repo().invite(who(c),campaign,current.revision(),EntityArgumentType.getPlayer(c,"target").getUuid(),false); return "Member added.";
            }))))
            .then(CommandManager.literal("dm").then(CommandManager.argument("target",EntityArgumentType.player()).executes(c->run(c,()->{
                UUID campaign=CharacterService.selected(player(c)); var current=repo().campaign(campaign);
                repo().invite(who(c),campaign,current.revision(),EntityArgumentType.getPlayer(c,"target").getUuid(),true); return "Co-DM appointed.";
            }))))
            .then(CommandManager.literal("revoke-dm").then(CommandManager.argument("target",EntityArgumentType.player()).executes(c->run(c,()->{
                UUID campaign=CharacterService.selected(player(c)); var current=repo().campaign(campaign);
                repo().revokeDm(who(c),campaign,current.revision(),EntityArgumentType.getPlayer(c,"target").getUuid()); return "Co-DM removed.";
            }))))
            .then(CommandManager.literal("rules").then(CommandManager.argument("profile",StringArgumentType.word()).executes(c->run(c,()->{
                UUID campaign=CharacterService.selected(player(c)); var current=repo().campaign(campaign);
                String profile=StringArgumentType.getString(c,"profile"); repo().profile(who(c),campaign,current.revision(),profile);
                return "Campaign default: "+profile+(RulesProfiles.get(profile).worldCombat()?"":" (world combat disabled; SRD automation is not implemented)");
            })))));
        root.then(CommandManager.literal("character")
            .then(CommandManager.literal("list").executes(c->run(c,()->{
                var p=who(c); UUID campaign=CharacterService.selected(player(c));
                return repo().state().characters().values().stream().filter(x->x.campaign().equals(campaign) && (x.owner().equals(p.id()) || repo().manages(p,campaign)))
                    .map(x->x.id()+" revision="+x.revision()+" "+(x.approved()?"approved":"draft")+" "+x.profile()).collect(java.util.stream.Collectors.joining("\n"));
            })))
            .then(CommandManager.literal("show").then(CommandManager.argument("character",StringArgumentType.word()).executes(c->run(c,()->{
                var x=repo().visible(who(c),id(c,"character")); return x.id()+" revision="+x.revision()+" approved="+x.approved()+" stats="+x.stats();
            }))))
            .then(CommandManager.literal("stat").then(CommandManager.argument("character",StringArgumentType.word())
                .then(CommandManager.argument("revision",LongArgumentType.longArg(1)).then(CommandManager.argument("key",StringArgumentType.word())
                .then(CommandManager.argument("value",DoubleArgumentType.doubleArg()).executes(c->run(c,()->{
                    repo().stat(who(c),id(c,"character"),LongArgumentType.getLong(c,"revision"),StringArgumentType.getString(c,"key"),DoubleArgumentType.getDouble(c,"value"));
                    return "Draft updated; DM approval required.";
                })))))))
            .then(CommandManager.literal("approve").then(CommandManager.argument("character",StringArgumentType.word())
                .then(CommandManager.argument("revision",LongArgumentType.longArg(1)).executes(c->run(c,()->{
                    repo().approve(who(c),id(c,"character"),LongArgumentType.getLong(c,"revision")); return "Character approved.";
                })))))
            .then(CommandManager.literal("bind").then(CommandManager.argument("character",StringArgumentType.word())
                .then(CommandManager.argument("revision",LongArgumentType.longArg(1)).then(CommandManager.argument("target",EntityArgumentType.entity()).executes(c->run(c,()->{
                    var target=EntityArgumentType.getEntity(c,"target"); if(!(target instanceof LivingEntity living)) throw new IllegalArgumentException("Target must be living");
                    CharacterService.bind(player(c),id(c,"character"),LongArgumentType.getLong(c,"revision"),living); return "Character bound to "+target.getUuid();
                }))))))
            .then(CommandManager.literal("release").then(CommandManager.argument("character",StringArgumentType.word())
                .then(CommandManager.argument("revision",LongArgumentType.longArg(1)).executes(c->run(c,()->{
                    CharacterService.release(player(c),id(c,"character"),LongArgumentType.getLong(c,"revision"));
                    return "Binding released; returning entities will have their character modifier removed.";
                })))))
            .then(CommandManager.literal("unbind").then(CommandManager.argument("target",EntityArgumentType.entity()).executes(c->run(c,()->{
                var target=EntityArgumentType.getEntity(c,"target"); if(!(target instanceof LivingEntity living)) throw new IllegalArgumentException("Target must be living");
                CharacterService.unbind(player(c),living); return "Character unbound; vanilla maximum health restored.";
            })))));
        root.then(CommandManager.literal("encounter").then(CommandManager.argument("profile",StringArgumentType.word()).executes(c->run(c,()->{
            var p=player(c); UUID campaign=CharacterService.selected(p);
            if(!repo().manages(who(c),campaign)) throw new IllegalArgumentException("Campaign DM permission required");
            String profile=StringArgumentType.getString(c,"profile");
            if(!RulesProfiles.get(profile).worldCombat()) throw new IllegalArgumentException("This profile does not implement world combat yet");
            var combat=CombatManager.startAround(p.getServerWorld(),p,false,new CharacterService.Context(campaign,profile));
            return combat==null?"No eligible participants or incompatible character profile.":"Encounter uses "+profile;
        }))));
        dispatcher.register(root);
    }
}
