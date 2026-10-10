package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.SrdMovement;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import java.util.*;

/** Controlled SRD lifecycle. No auto-recruitment or vanilla-stat inference. */
public final class SrdEncounter implements Encounter {
    public enum State { PENDING, ACTIVE, PAUSED, ENDED }
    private final UUID id=UUID.randomUUID(),campaign;
    private final ServerWorld world;
    private final List<LivingEntity> actors;
    private final Map<UUID,Vec3d> anchors=new HashMap<>();
    private final Map<UUID,Boolean> previousAi=new HashMap<>();
    private final Map<UUID,CharacterRepository.SrdSnapshot> snapshots=new HashMap<>();
    private State state=State.PENDING;
    private String reason="Awaiting initiative and supported action gateway";

    private SrdEncounter(ServerWorld world,UUID campaign,List<LivingEntity> actors) {
        this.world=world; this.campaign=campaign; this.actors=List.copyOf(actors);
    }
    /** Trusted admission API, deliberately package-private until the complete live gate is verified. */
    static SrdEncounter start(ServerWorld world,CharacterRepository.Principal requester,UUID campaign,List<LivingEntity> actors) {
        require(world.getServer().isOnThread(),"Encounter admission requires the server thread");
        require(CharacterService.repository().manages(requester,campaign),"Campaign manager required");
        require(actors.size()>=2 && actors.size()<=16,"Controlled SRD encounters require 2–16 explicit actors");
        require(actors.stream().map(Entity::getUuid).distinct().count()==actors.size(),"Duplicate actor");
        SrdEncounter encounter=new SrdEncounter(world,campaign,actors);
        // Validate the entire admission set before taking ownership or changing any entity.
        for(LivingEntity actor:actors) {
            require(actor.getWorld()==world && actor.isAlive() && !actor.isRemoved() && !actor.isSpectator()
                    && world.getEntity(actor.getUuid())==actor,"Actor must be loaded and alive in this world");
            require(!(actor instanceof ServerPlayerEntity),"SRD player protocol is not yet available");
            require(actor instanceof MobEntity,"Only explicitly bound living mobs are supported NPCs");
            require(!actor.hasVehicle() && !actor.hasPassengers() && !actor.isSwimming() && !actor.isFallFlying(),"Unsupported movement mode");
            require(!CombatManager.isInCombat(actor),"Actor already belongs to an encounter");
            var snapshot=CharacterService.srdStats(actor,campaign);
            require(snapshot.stats().hp()>0,"Zero-HP actor needs adjudication before admission");
            require(CharacterService.mayJoin(actor,campaign,RulesProfiles.get(RulesProfiles.SRD)),"Incompatible campaign or ruleset");
            var terrain=new SrdTerrain(actor,snapshot.stats()); var cell=terrain.currentCell();
            require(actor.getPos().squaredDistanceTo(terrain.position(cell))<1e-6 && terrain.landing(cell)==SrdMovement.Reason.OK,
                    "Place actors on supported centered SRD footprints before admission");
            encounter.snapshots.put(actor.getUuid(),snapshot);
        }
        var claimed=new ArrayList<LivingEntity>();
        try {
            for(var actor:actors) {
                require(CombatManager.claim(actor,encounter),"Actor ownership changed"); claimed.add(actor);
                encounter.anchors.put(actor.getUuid(),actor.getPos());
                if(actor instanceof MobEntity mob) {
                    encounter.previousAi.put(actor.getUuid(),mob.isAiDisabled()); mob.setAiDisabled(true); mob.getNavigation().stop();
                }
                CombatInteractions.cancel(actor); actor.setVelocity(Vec3d.ZERO);
            }
            CombatManager.schedule(encounter); return encounter;
        } catch(RuntimeException failure) { encounter.end(); throw failure; }
    }
    private static void require(boolean value,String reason) { if(!value) throw new IllegalArgumentException(reason); }
    public UUID id() { return id; }
    public UUID campaign() { return campaign; }
    public RulesProfile rules() { return RulesProfiles.get(RulesProfiles.SRD); }
    public State state() { return state; }
    public String reason() { return reason; }
    public boolean isTurnOf(Entity actor) { return false; }
    public List<LivingEntity> actors() { return actors; }
    public boolean tick() {
        require(world.getServer().isOnThread(),"Encounter tick requires server thread");
        if(state==State.ENDED) return true;
        for(var actor:actors) {
            if(!actor.isAlive() || actor.isRemoved() || actor.getWorld()!=world || world.getEntity(actor.getUuid())!=actor) {
                reason="Actor unavailable; controlled encounter ended"; return true;
            }
            try {
                if(!snapshots.get(actor.getUuid()).equals(CharacterService.srdStats(actor,campaign))) {
                    state=State.PAUSED; reason="Character changed; adjudication required";
                }
            } catch(RuntimeException unavailable) { state=State.PAUSED; reason="Character unavailable; adjudication required"; }
            Vec3d anchor=anchors.get(actor.getUuid());
            if(actor.getPos().squaredDistanceTo(anchor)>1e-6) actor.refreshPositionAndAngles(anchor.x,anchor.y,anchor.z,actor.getYaw(),actor.getPitch());
            actor.setVelocity(Vec3d.ZERO); actor.fallDistance=0;
            if(actor instanceof MobEntity mob) { mob.setAiDisabled(true); mob.getNavigation().stop(); }
        }
        return false;
    }
    public void end() {
        require(world.getServer().isOnThread(),"Encounter cleanup requires server thread");
        if(state==State.ENDED) return;
        state=State.ENDED;
        for(var actor:actors) {
            // A failed partial claim must not alter another encounter's participant.
            if(CombatManager.encounter(actor)!=this) continue;
            CombatInteractions.cancel(actor); actor.setVelocity(Vec3d.ZERO);
            if(actor instanceof MobEntity mob && previousAi.containsKey(actor.getUuid())) mob.setAiDisabled(previousAi.get(actor.getUuid()));
            CombatManager.release(actor,this);
        }
    }
}
