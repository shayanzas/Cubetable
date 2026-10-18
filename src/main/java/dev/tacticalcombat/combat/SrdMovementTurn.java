package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.core.TurnRequests;
import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdMovement;
import dev.tacticalcombat.rules.srd.SrdMovement.*;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.Vec3d;
import java.util.*;
import java.util.function.Supplier;

/** Isolated Stage B server turn controller. No packet/command can create it or enable an SRD encounter.
 * The future encounter owns exactly one instance per actor/turn and supplies its live trusted Frame.
 * One tick commits one complete grid step; there is no partly completed authoritative square.
 */
public final class SrdMovementTurn {
    public record Frame(UUID encounter,UUID campaign,String profile,long turn,UUID activeActor) {}
    public record Request(UUID encounter,UUID campaign,String profile,long turn,UUID actor,UUID request,Cell destination) {}
    public record Result(boolean accepted,String message,int spentFeet,int remainingFeet) {}
    private final LivingEntity actor;
    private final net.minecraft.world.World initialWorld;
    private final Supplier<Frame> context;
    private final Frame initial;
    private CharacterRepository.SrdSnapshot snapshot;
    private final SrdTerrain terrain;
    private final TurnRequests requests;
    private final int speed;
    private int budget;
    private int spent;
    private int stepTicks=1,waitTicks;
    private Cell anchor;
    private List<Cell> route=List.of();
    private CharacterRepository.Principal controller;
    private boolean closed;
    private Preview completedPreview;
    public void validatePreview(CharacterRepository.Principal principal) {
        requireServerThread();String error=invalid();
        if(error!=null || !permitted(principal) || moving()) throw new IllegalArgumentException(error==null?"Movement unavailable":error);
        if(actor.getPos().squaredDistanceTo(terrain.position(anchor))>1e-6 || terrain.landing(anchor)!=Reason.OK)
            throw new IllegalArgumentException("Invalid movement anchor");
    }
    public SrdTerrain.Pending pendingPreview(CharacterRepository.Principal principal) {
        validatePreview(principal);completedPreview=null;return terrain.pending(anchor,remainingFeet());
    }
    public void completePreview(CharacterRepository.Principal principal,Preview preview) {
        validatePreview(principal);
        if(!preview.start().equals(anchor) || preview.remainingFeet()!=remainingFeet()) throw new IllegalArgumentException("Stale grid");
        completedPreview=preview;
    }

    public SrdMovementTurn(LivingEntity actor,Supplier<Frame> context) {
        this.actor=Objects.requireNonNull(actor); this.context=Objects.requireNonNull(context);
        initialWorld=actor.getWorld();
        requireServerThread();
        initial=Objects.requireNonNull(context.get());
        if(!RulesProfiles.SRD.equals(initial.profile) || !actor.getUuid().equals(initial.activeActor) || initial.turn<1
                || initial.encounter==null || initial.campaign==null) throw new IllegalArgumentException("Invalid SRD movement turn");
        snapshot=CharacterService.srdStats(actor,initial.campaign);
        if(!CharacterService.mayJoin(actor,initial.campaign,RulesProfiles.get(RulesProfiles.SRD)))
            throw new IllegalArgumentException("Actor campaign selection conflicts with movement encounter");
        if(snapshot.stats().hp()==0) throw new IllegalArgumentException("Zero-HP movement is unsupported before recovery rules");
        terrain=new SrdTerrain(actor,snapshot.stats()); anchor=terrain.currentCell();
        if(actor.getPos().squaredDistanceTo(terrain.position(anchor))>1e-6 || terrain.landing(anchor)!=Reason.OK)
            throw new IllegalArgumentException("SRD movement requires a supported, centered starting footprint");
        speed=snapshot.rules().walkingSpeedFeet(); budget=speed; requests=new TurnRequests(initial.encounter); requests.nextTurn();
    }
    public int speedFeet() { return speed; }
    /** Encounter spends the normal action in the same server-thread operation. Never moves/refunds. */
    void dash(CharacterRepository.Principal principal) {
        validatePreview(principal);
        int expanded=Math.addExact(budget,speed);
        if(expanded>SrdMovement.MAX_TURN_FEET)throw new IllegalArgumentException("Movement turn allowance exceeds supported bounds");
        budget=expanded;completedPreview=null;
    }
    public int spentFeet() { return spent; }
    public int remainingFeet() { return budget-spent; }
    public boolean moving() { return !route.isEmpty(); }
    public boolean closed() { return closed; }
    /** Called only after the owning encounter atomically commits this actor's resource changes. Never resets spent feet. */
    void reconcileResources(CharacterRepository.SrdSnapshot latest) {
        requireServerThread();
        if(!latest.equals(CharacterService.srdStats(actor,initial.campaign)) || !latest.actor().equals(snapshot.actor())
                || !latest.character().equals(snapshot.character()) || latest.revision()!=snapshot.revision()
                || latest.stats().walkingSpeedFeet()!=snapshot.stats().walkingSpeedFeet() || latest.stats().size()!=snapshot.stats().size())
            throw new IllegalArgumentException("Cannot reconcile a changed movement build");
        snapshot=latest;
    }
    private void requireServerThread() {
        if(actor.getServer()==null || !actor.getServer().isOnThread())
            throw new IllegalStateException("SRD movement must run on the authoritative server thread");
    }
    private String invalid() {
        if(closed || !actor.isAlive() || actor.isRemoved() || actor.isSpectator() || actor.getWorld()!=initialWorld)
            return "Movement turn closed or actor unavailable";
        if(actor.isSwimming() || actor.isFallFlying() || actor.hasVehicle()
                || actor instanceof ServerPlayerEntity player && player.getAbilities().flying)
            return "Unsupported SRD movement mode; walking only";
        if(!initial.equals(context.get())) return "Stale encounter, campaign, ruleset or active turn";
        if(!CharacterService.mayJoin(actor,initial.campaign,RulesProfiles.get(RulesProfiles.SRD)))
            return "Actor campaign selection changed";
        try {
            var latest=CharacterService.srdStats(actor,initial.campaign);
            if(!snapshot.equals(latest)) return "Authoritative character changed; movement turn must be reconciled";
        } catch(IllegalArgumentException|IllegalStateException denied) { return "Authoritative SRD binding unavailable"; }
        return null;
    }
    private boolean permitted(CharacterRepository.Principal principal) {
        var repo=CharacterService.repository(); var record=repo.character(snapshot.character());
        return repo.member(principal,initial.campaign) &&
                (actor instanceof ServerPlayerEntity && actor.getUuid().equals(principal.id()) && CharacterService.repository().controller(record.id()).equals(principal.id())
                || repo.manages(principal,initial.campaign));
    }
    /** principal must come from the authenticated server connection, never payload flags. */
    public Preview preview(CharacterRepository.Principal principal) {
        requireServerThread();
        String error=invalid(); if(error!=null || !permitted(principal)) throw new IllegalArgumentException(error==null?"Movement permission denied":error);
        if(actor.getPos().squaredDistanceTo(terrain.position(anchor))>1e-6 || terrain.landing(anchor)!=Reason.OK)
            throw new IllegalArgumentException("Invalid movement anchor");
        return terrain.search(anchor,remainingFeet());
    }
    public Result request(CharacterRepository.Principal principal,Request request) {return request(principal,request,1);}
    /** Optional bounded cadence changes wall-clock pacing only. Revalidation still runs every server tick. */
    public Result request(CharacterRepository.Principal principal,Request request,int stepTicks) {
        requireServerThread();
        String error=invalid(); if(error!=null) return result(false,error);
        if(stepTicks<1 || stepTicks>6)return result(false,"Movement cadence must be 1–6 server ticks");
        if(request==null || !permitted(principal) || !actor.getUuid().equals(request.actor)
                || !initial.encounter.equals(request.encounter) || !initial.campaign.equals(request.campaign)
                || !initial.profile.equals(request.profile) || initial.turn!=request.turn || request.request==null || request.destination==null)
            return result(false,"Movement identity or permission denied");
        // Identity is consumed before expensive search, including rejected/stale geometry, without spending movement.
        if(!requests.accept(initial.encounter,requests.turn(),request.request)) return result(false,"Duplicate or excessive movement request");
        if(moving()) return result(false,"Cancel or complete the current route first");
        Preview preview;
        try {
            if(remainingFeet()>60) {
                validatePreview(principal);preview=completedPreview;
                if(preview==null || !preview.start().equals(anchor) || preview.remainingFeet()!=remainingFeet())
                    return result(false,"Request and await a current grid before high-speed movement");
            } else preview=preview(principal);
        } catch(IllegalArgumentException rejected) { return result(false,rejected.getMessage()); }
        List<Cell> path=preview.pathTo(request.destination);
        if(path.isEmpty()) return result(false,request.destination.equals(anchor)?"Already at destination":preview.status(request.destination).name());
        route=path; this.stepTicks=stepTicks;waitTicks=0; completedPreview=null; controller=principal; return result(true,"Route accepted; steps revalidate before execution");
    }
    private Result result(boolean accepted,String message) { return new Result(accepted,message,spent,remainingFeet()); }
    public Result cancel(CharacterRepository.Principal principal) {
        requireServerThread();
        if(!permitted(principal)) return result(false,"Movement permission denied");
        route=List.of(); return result(true,"Route cancelled; completed steps remain charged");
    }
    /** No tweening/free walking: unauthorized displacement cancels the route and restores the last safe anchor. */
    public Result tick() {
        requireServerThread();
        String error=invalid();
        if(error!=null) { route=List.of(); closed=true; return result(false,error); }
        if(actor.getPos().squaredDistanceTo(terrain.position(anchor))>1e-6) {
            route=List.of();
            if(terrain.landing(anchor)==Reason.OK) place(anchor); else closed=true;
            return result(false,"Unauthorized displacement; route cancelled");
        }
        if(terrain.landing(anchor)!=Reason.OK) { route=List.of(); closed=true; return result(false,"Anchor lost support/clearance; movement suspended"); }
        if(route.isEmpty()) { actor.setVelocity(Vec3d.ZERO); return result(true,"Idle"); }
        if(!permitted(controller)) { route=List.of(); return result(false,"Controller permission changed"); }
        // Revalidate the ENTIRE remaining route, including changed difficult-terrain costs, before committing a step.
        Cell previous=anchor; int total=0,firstCost=0;
        for(Cell next:route) {
            Step step=terrain.step(previous,next.x()-previous.x(),next.z()-previous.z());
            if(step.reason()!=Reason.OK || !step.to().equals(next)) { route=List.of(); return result(false,"Route invalidated: "+step.reason()); }
            total+=step.feet(); if(firstCost==0) firstCost=step.feet(); previous=next;
        }
        if(total>remainingFeet()) { route=List.of(); return result(false,"Remaining route exceeds movement allowance"); }
        if(waitTicks>0){waitTicks--;return result(true,"Paced movement; next step pending");}
        Cell next=route.getFirst(); place(next); anchor=next; spent+=firstCost;waitTicks=stepTicks-1;
        route=List.copyOf(route.subList(1,route.size()));
        return result(true,"Step completed");
    }
    private void place(Cell cell) {
        Vec3d p=terrain.position(cell);
        if(actor instanceof ServerPlayerEntity player) player.networkHandler.requestTeleport(p.x,p.y,p.z,player.getYaw(),player.getPitch());
        else actor.refreshPositionAndAngles(p.x,p.y,p.z,actor.getYaw(),actor.getPitch());
        if(actor instanceof MobEntity mob) mob.getNavigation().stop();
        actor.setVelocity(Vec3d.ZERO);
    }
}
