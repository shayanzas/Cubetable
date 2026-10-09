package dev.tacticalcombat.character;

import dev.tacticalcombat.combat.*;
import dev.tacticalcombat.rules.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.*;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import java.util.*;
import java.io.IOException;

/** Minecraft adapter; repository records and rules profiles contain no world or client objects. */
public final class CharacterService {
    private static CharacterRepository repository;
    private static final Map<UUID,UUID> SELECTED=new HashMap<>();
    private static final Identifier HP=Identifier.of("tacticalcombat","character_hp");
    private static boolean failed;
    private CharacterService() {}
    public static void start(MinecraftServer server) {
        SELECTED.clear(); failed=false;
        try {
            repository=new CharacterRepository(server.getSavePath(WorldSavePath.ROOT).resolve("tacticalcombat/campaigns.json"));
            if(repository.recoveredFromBackup()) dev.tacticalcombat.TacticalCombatMod.LOGGER.warn("Recovered character storage from validated backup; latest save was unreadable");
            // Initial world entities may have loaded before SERVER_STARTED. Reconcile their saved modifiers too.
            for(var world:server.getWorlds()) for(var entity:world.iterateEntities())
                if(entity instanceof LivingEntity living) apply(living);
        }
        catch(Exception e) { repository=null; failed=true; dev.tacticalcombat.TacticalCombatMod.LOGGER.error("Character storage unavailable; refusing new tactical encounters",e); }
    }
    public static CharacterRepository repository() {
        if(repository==null) throw new IllegalStateException("Character service unavailable"); return repository;
    }
    public static CharacterRepository.Principal principal(ServerPlayerEntity p) { return new CharacterRepository.Principal(p.getUuid(),p.hasPermissionLevel(2)); }
    public static void select(ServerPlayerEntity p,UUID campaign) {
        if(!repository().member(principal(p),campaign)) throw new IllegalArgumentException("Campaign membership required");
        if(CombatManager.isInCombat(p)) throw new IllegalArgumentException("End the encounter before selecting a campaign");
        SELECTED.put(p.getUuid(),campaign);
    }
    public static UUID selected(ServerPlayerEntity p) {
        UUID campaign=SELECTED.get(p.getUuid());
        if(campaign==null) throw new IllegalArgumentException("Select a campaign with /tbc campaign select <id>"); return campaign;
    }
    public static CharacterRepository.Character bound(LivingEntity entity) {
        if(repository==null) return null;
        UUID id=repository.state().bindings().get(entity.getUuid()); return id==null?null:repository.character(id);
    }
    public record Context(UUID campaign,String profile) {}
    public static Context context(ServerPlayerEntity p) {
        var record=bound(p);
        UUID campaign=SELECTED.get(p.getUuid());
        if(campaign==null && record!=null) campaign=record.campaign();
        return new Context(campaign,campaign==null?RulesProfiles.TACTICAL:repository().campaign(campaign).profile());
    }
    public static boolean available() { return !failed; }
    public static boolean mayJoin(LivingEntity entity,UUID campaign,RulesProfile profile) {
        var record=bound(entity);
        if(record!=null && (!Objects.equals(record.campaign(),campaign) || !record.profile().equals(profile.id()))) return false;
        if(entity instanceof ServerPlayerEntity p) {
            var context=context(p);
            if(!Objects.equals(context.campaign(),campaign)) return false;
            // Explicit encounter overrides may run tactical combat for unbound members of a manual campaign.
        }
        return true;
    }
    public static Map<String,Double> stats(LivingEntity entity,RulesProfile profile) {
        var record=bound(entity);
        return record!=null && record.approved() && record.profile().equals(profile.id())?record.stats():Map.of();
    }
    public static void bind(ServerPlayerEntity requester,UUID character,long revision,LivingEntity target) throws IOException {
        if(CombatManager.isInCombat(target)) throw new IllegalArgumentException("Cannot change binding during combat");
        var record=repository().visible(principal(requester),character);
        repository().bind(principal(requester),character,revision,target.getUuid(),target instanceof ServerPlayerEntity?target.getUuid():null);
        apply(target);
        if(record.profile().equals(RulesProfiles.TACTICAL)) target.setHealth(record.stats().get("hp").floatValue());
    }
    public static void unbind(ServerPlayerEntity requester,LivingEntity target) throws IOException {
        if(CombatManager.isInCombat(target)) throw new IllegalArgumentException("Cannot unbind during combat");
        repository().unbind(principal(requester),target.getUuid()); apply(target);
    }
    /** Release by character ID also works when an NPC was removed or a player is offline. */
    public static void release(ServerPlayerEntity requester,UUID character,long revision) throws IOException {
        var record=repository().visible(principal(requester),character);
        if(record.revision()!=revision) throw new IllegalArgumentException("Stale character revision");
        UUID actor=repository().state().bindings().entrySet().stream().filter(e->e.getValue().equals(character))
                .map(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalArgumentException("Character is not bound"));
        if(CombatManager.isInCombat(actor)) throw new IllegalArgumentException("Cannot release binding during combat");
        for(var world:requester.getServer().getWorlds()) {
            var entity=world.getEntity(actor);
            if(entity instanceof LivingEntity living) { unbind(requester,living); return; }
        }
        repository().unbind(principal(requester),actor);
        // ENTITY_LOAD / JOIN removes the persisted modifier if the entity returns later.
    }
    public static void apply(LivingEntity entity) {
        if(repository==null || entity.getWorld().isClient) return;
        var attribute=entity.getAttributeInstance(EntityAttributes.GENERIC_MAX_HEALTH);
        if(attribute==null) return;
        var record=bound(entity);
        attribute.removeModifier(HP);
        if(record!=null && record.profile().equals(RulesProfiles.TACTICAL)) {
            double delta=record.stats().get("hp_max")-attribute.getBaseValue();
            attribute.addPersistentModifier(new EntityAttributeModifier(HP,delta,EntityAttributeModifier.Operation.ADD_VALUE));
        }
        entity.setHealth(Math.min(entity.getHealth(),entity.getMaxHealth()));
    }
    public static void capture(MinecraftServer server) {
        if(repository==null) return;
        Map<UUID,Double> values=new HashMap<>();
        for(var world:server.getWorlds()) for(UUID actor:repository.state().bindings().keySet()) {
            var entity=world.getEntity(actor);
            if(entity instanceof LivingEntity living && bound(living).profile().equals(RulesProfiles.TACTICAL))
                values.put(actor,(double)living.getHealth());
        }
        try { repository.captureHealth(values); }
        catch(Exception e) { dev.tacticalcombat.TacticalCombatMod.LOGGER.error("Could not persist character health",e); }
    }
    public static void forget(UUID player) { SELECTED.remove(player); }
    public static void stop(MinecraftServer server) { capture(server); repository=null; SELECTED.clear(); failed=false; }
}
