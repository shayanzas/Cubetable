package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.grid.SrdTerrain;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.SrdMovement;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.core.TurnRequests;
import dev.tacticalcombat.net.*;
import com.google.gson.*;
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
    private final TurnRequests requests=new TurnRequests(id);
    private final Map<UUID,SrdRules.D20> initiative=new LinkedHashMap<>();
    private final Map<UUID,List<UUID>> tieVotes=new HashMap<>();
    private List<List<UUID>> groups=List.of();
    private List<UUID> order=List.of();
    private int turnIndex,round=1;
    private long revision;
    private SrdMovementTurn movement;
    private boolean actionUsed;
    private Intent intent;
    private Transaction transaction;
    private final SrdRules.Dice dice;
    record Intent(UUID id,UUID requester,long turn,UUID actor,UUID target,SrdEquipment.Weapon weapon,
                  SrdCharacter.Ability ability,boolean twoHands,CharacterRepository.SrdSnapshot attacker,
                  CharacterRepository.SrdSnapshot defender,Vec3d from,Vec3d to,SrdRules.AttackPlan plan,SrdTargeting.Target geometry) {}
    enum TransactionState { PREPARED, SAVE_FAILED, COMMITTED }
    static final class Transaction {
        final Intent intent; final SrdRules.AttackResult roll; final SrdHealth health;
        TransactionState state=TransactionState.PREPARED;
        Transaction(Intent intent,SrdRules.AttackResult roll,SrdHealth health) { this.intent=intent; this.roll=roll; this.health=health; }
    }

    private SrdEncounter(ServerWorld world,UUID campaign,List<LivingEntity> actors,SrdRules.Dice dice) {
        this.world=world; this.campaign=campaign; this.actors=List.copyOf(actors);
        this.dice=dice;
    }
    /** Trusted admission API, deliberately package-private until the complete live gate is verified. */
    static SrdEncounter start(ServerWorld world,CharacterRepository.Principal requester,UUID campaign,List<LivingEntity> actors) {
        return start(world,requester,campaign,actors,sides->1+world.getRandom().nextInt(sides));
    }
    /** Deterministic server-only test seam, never selected from a client payload. */
    static SrdEncounter start(ServerWorld world,CharacterRepository.Principal requester,UUID campaign,List<LivingEntity> actors,SrdRules.Dice dice) {
        require(world.getServer().isOnThread(),"Encounter admission requires the server thread");
        require(CharacterService.repository().manages(requester,campaign),"Campaign manager required");
        require(actors.size()>=2 && actors.size()<=16,"Controlled SRD encounters require 2–16 explicit actors");
        require(actors.stream().map(Entity::getUuid).distinct().count()==actors.size(),"Duplicate actor");
        SrdEncounter encounter=new SrdEncounter(world,campaign,actors,dice);
        // Validate the entire admission set before taking ownership or changing any entity.
        for(LivingEntity actor:actors) {
            require(actor.getWorld()==world && actor.isAlive() && !actor.isRemoved() && !actor.isSpectator()
                    && world.getEntity(actor.getUuid())==actor,"Actor must be loaded and alive in this world");
            if(actor instanceof ServerPlayerEntity player)
                require(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,SrdStatePayload.ID),"Updated SRD v1 client required");
            else require(actor instanceof MobEntity,"Only explicitly bound living mobs are supported NPCs");
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
    public boolean isTurnOf(Entity actor) { return state==State.ACTIVE && !order.isEmpty() && order.get(turnIndex).equals(actor.getUuid()); }
    public List<LivingEntity> actors() { return actors; }
    public long turn() { return requests.turn(); }
    public long revision() { return revision; }
    public int round() { return round; }
    public boolean actionUsed() { return actionUsed; }
    public LivingEntity current() { return order.isEmpty()?null:actor(order.get(turnIndex)); }
    private LivingEntity actor(UUID id) { return actors.stream().filter(a->a.getUuid().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Actor is not in this encounter")); }
    private void thread() { require(world.getServer().isOnThread(),"Server thread required"); }
    private boolean manager(CharacterRepository.Principal p) { return CharacterService.repository().manages(p,campaign); }
    private boolean viewer(CharacterRepository.Principal p) {
        return CharacterService.repository().member(p,campaign) && (manager(p) || actors.stream().anyMatch(a->a instanceof ServerPlayerEntity && a.getUuid().equals(p.id())));
    }
    private boolean controls(CharacterRepository.Principal p,LivingEntity actor) {
        return viewer(p) && (manager(p) || actor instanceof ServerPlayerEntity && actor.getUuid().equals(p.id())
                && CharacterService.repository().controller(snapshots.get(actor.getUuid()).character()).equals(p.id()));
    }
    private CharacterRepository.SrdSnapshot fresh(LivingEntity actor) {
        require(CombatManager.encounter(actor)==this && actor.isAlive() && !actor.isRemoved() && actor.getWorld()==world
                && world.getEntity(actor.getUuid())==actor && !actor.isSpectator(),"Actor unavailable");
        require(CharacterService.mayJoin(actor,campaign,rules()),"Campaign selection changed");
        require(actor.getPos().squaredDistanceTo(anchors.get(actor.getUuid()))<1e-6,"Actor displaced; wait for server reconciliation");
        require(!actor.hasVehicle() && !actor.hasPassengers() && !actor.isSwimming() && !actor.isFallFlying(),"Unsupported movement mode");
        if(actor instanceof ServerPlayerEntity player) require(!player.getAbilities().flying
                && world.getServer().getPlayerManager().getPlayer(player.getUuid())==player
                && net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,SrdStatePayload.ID),"Connected SRD client and supported walking mode required");
        var snapshot=CharacterService.srdStats(actor,campaign);
        require(snapshot.equals(snapshots.get(actor.getUuid())),"Character changed; adjudication required");
        return snapshot;
    }
    /** Rolls once. PC ties require matching choices from each tied player; GM/mixed ties require a manager. */
    void begin() {
        thread(); require(state==State.PENDING && initiative.isEmpty(),"Initiative already rolled");
        var totals=new LinkedHashMap<UUID,Integer>();
        for(var actor:actors) {
            var roll=fresh(actor).rules().initiative(dice); initiative.put(actor.getUuid(),roll); totals.put(actor.getUuid(),roll.total());
        }
        groups=SrdRules.initiativeGroups(totals); requests.nextTurn(); revision++;
        reason="Initiative tie: players choose PC order; campaign DM chooses NPC/mixed order";
        if(groups.stream().noneMatch(g->g.size()>1)) establishOrder();
    }
    void tie(CharacterRepository.Principal p,List<UUID> choice) {
        thread(); require(state==State.PENDING && !initiative.isEmpty(),"No pending initiative decision");
        require(viewer(p),"Encounter permission denied");
        int index=-1;
        for(int i=0;i<groups.size();i++) if(groups.get(i).size()>1 && new HashSet<>(groups.get(i)).equals(new HashSet<>(choice)) && groups.get(i).size()==choice.size()) index=i;
        require(index>=0,"Choose exactly one tied group without duplicates");
        var group=groups.get(index);
        boolean onlyPlayers=group.stream().allMatch(id->actor(id) instanceof ServerPlayerEntity);
        if(onlyPlayers) {
            require(group.contains(p.id()),"Tied players decide their own order"); tieVotes.put(p.id(),List.copyOf(choice));
            if(!group.stream().allMatch(id->choice.equals(tieVotes.get(id)))) { revision++; return; }
        } else require(manager(p),"Campaign manager decides NPC or mixed ties");
        var next=new ArrayList<List<UUID>>(groups); next.remove(index);
        for(int i=0;i<choice.size();i++) next.add(index+i,List.of(choice.get(i)));
        groups=List.copyOf(next); revision++;
        if(groups.stream().noneMatch(g->g.size()>1)) establishOrder();
    }
    private void establishOrder() { order=groups.stream().flatMap(Collection::stream).toList(); turnIndex=0; beginTurn(); }
    private void beginTurn() {
        requests.nextTurn(); actionUsed=false; intent=null; transaction=null;
        state=State.ACTIVE; reason="Controlled public-position free-for-all; no reactions or death saves";
        try { movement=new SrdMovementTurn(current(),this::frame); }
        catch(IllegalArgumentException denied) { state=State.PAUSED; reason=denied.getMessage(); movement=null; }
        revision++;
        dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(UUID.randomUUID(),campaign,id,turn(),dev.tacticalcombat.uiapi.UiEvents.Kind.TURN,List.of(current().getUuid()),"Turn started",null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,actors.stream().filter(a->a instanceof ServerPlayerEntity).map(Entity::getUuid).collect(java.util.stream.Collectors.toSet()));
    }
    private SrdMovementTurn.Frame frame() { return new SrdMovementTurn.Frame(id,campaign,RulesProfiles.SRD,turn(),state==State.ACTIVE?current().getUuid():null); }
    void endTurn(CharacterRepository.Principal p) {
        thread(); require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        require(transaction==null || transaction.state==TransactionState.COMMITTED,"Resolve pending storage transaction first");
        if(movement!=null) movement.cancel(p);
        turnIndex++; if(turnIndex==order.size()) { turnIndex=0; round++; } beginTurn();
    }
    SrdMovement.Preview grid(CharacterRepository.Principal p) {
        validateGrid(p);
        return movement.preview(p);
    }
    void validateGrid(CharacterRepository.Principal p) {
        require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        if(!manager(p)) require(world.getEntitiesByClass(LivingEntity.class,current().getBoundingBox().expand(64),LivingEntity::isInvisible).isEmpty(),
                "Movement preview unavailable in this unsupported visibility situation");
        movement.validatePreview(p);
    }
    boolean budgetedGrid() {return movement!=null && movement.remainingFeet()>60;}
    dev.tacticalcombat.grid.SrdTerrain.Pending pendingGrid(CharacterRepository.Principal p) {validateGrid(p);return movement.pendingPreview(p);}
    void completedGrid(CharacterRepository.Principal p,SrdMovement.Preview preview) {validateGrid(p);movement.completePreview(p,preview);}
    SrdMovementTurn.Result move(CharacterRepository.Principal p,UUID request,SrdMovement.Cell destination) {
        thread(); require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        intent=null;
        var result=movement.request(p,new SrdMovementTurn.Request(id,campaign,RulesProfiles.SRD,turn(),current().getUuid(),request,destination));
        revision++; return result;
    }
    void cancelMove(CharacterRepository.Principal p) {
        thread(); require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required"); movement.cancel(p); revision++;
    }
    private SrdTargeting.Target legalTarget(LivingEntity attacker,LivingEntity target,SrdRules.AttackPlan plan) {
        require(attacker!=target && fresh(attacker).stats().hp()>0 && fresh(target).stats().hp()>0,"Actor or target cannot act at zero HP");
        var a=fresh(attacker).stats(); var b=fresh(target).stats();
        var geometry=SrdTargeting.inspect(attacker,a,target,b);
        boolean ranged=plan.mode()!=SrdRules.AttackMode.MELEE;
        require(geometry.feet()<=(ranged?plan.longRangeFeet():plan.reachFeet())+1e-6,"Target beyond supported reach/range");
        require(geometry.cover()!=SrdTargeting.Cover.TOTAL,"Target has total cover");
        boolean close=false;
        // This explicit test encounter is free-for-all: all other admitted actors are hostile.
        if(ranged && !attacker.isInvisible()) for(var other:actors) if(other!=attacker) {
            var stats=fresh(other).stats();
            if(stats.hp()>0 && SrdTargeting.feet(attacker,a,other,stats)<=5
                    && SrdTargeting.inspect(other,stats,attacker,a).cover()!=SrdTargeting.Cover.TOTAL) { close=true; break; }
        }
        if(plan.mode()==SrdRules.AttackMode.RANGED) require((plan.weapon()==SrdEquipment.Weapon.LIGHT_CROSSBOW?a.bolts():a.arrows())>0,"No approved virtual ammunition remains");
        return new SrdTargeting.Target(geometry.feet(),geometry.cover(),geometry.advantage(),geometry.disadvantage() || ranged && (close || geometry.feet()>plan.normalRangeFeet()));
    }
    Intent preview(CharacterRepository.Principal p,UUID targetId,SrdEquipment.Weapon weapon,SrdCharacter.Ability ability,boolean twoHands) {
        return preview(p,targetId,weapon,SrdRules.AttackMode.MELEE,ability,twoHands);
    }
    Intent preview(CharacterRepository.Principal p,UUID targetId,SrdEquipment.Weapon weapon,SrdRules.AttackMode mode,SrdCharacter.Ability ability,boolean twoHands) {
        thread(); require(state==State.ACTIVE && controls(p,current()) && !actionUsed && !movement.moving(),"No eligible active action");
        var attacker=current(); var target=actor(targetId); var a=fresh(attacker); var b=fresh(target);
        var plan=a.rules().plan(weapon,mode,ability,twoHands);
        var geometry=legalTarget(attacker,target,plan);
        intent=new Intent(UUID.randomUUID(),p.id(),turn(),attacker.getUuid(),targetId,weapon,ability,twoHands,a,b,attacker.getPos(),target.getPos(),plan,geometry);
        revision++; return intent;
    }
    Transaction confirm(CharacterRepository.Principal p,UUID intentId) {
        thread(); require(state==State.ACTIVE && controls(p,current()) && !actionUsed && !movement.moving(),"No eligible active action");
        Intent selected=intent;
        require(selected!=null && selected.id.equals(intentId) && selected.requester.equals(p.id()) && selected.turn==turn(),"Stale or unauthorized attack intent");
        var attacker=actor(selected.actor); var target=actor(selected.target);
        require(selected.attacker.equals(fresh(attacker)) && selected.defender.equals(fresh(target))
                && selected.from.equals(attacker.getPos()) && selected.to.equals(target.getPos()),"Attack preview changed; preview again");
        require(selected.geometry.equals(legalTarget(attacker,target,selected.plan)),"Target cover changed; preview again");
        // Acceptance happens once, before RNG. A storage failure retains these exact dice and expected revision.
        actionUsed=true; intent=null;
        var roll=selected.attacker.rules().attack(selected.weapon,selected.plan.mode(),selected.ability,selected.twoHands,
                selected.defender.rules().armorClass()+selected.geometry.cover().ac,selected.geometry.advantage(),selected.geometry.disadvantage(),dice);
        var health=SrdHealth.of(selected.defender.stats()).damage(List.of(roll.damage()),selected.defender.stats());
        transaction=new Transaction(selected,roll,health); persistTransaction(); return transaction;
    }
    private void persistTransaction() {
        try {
            var saved=CharacterService.repository().commitSrdAction(transaction.intent.attacker,transaction.intent.defender,transaction.health,transaction.intent.weapon,transaction.intent.plan.mode());
            for(var snapshot:saved) snapshots.put(snapshot.actor(),snapshot);
            movement.reconcileResources(saved.getFirst()); transaction.state=TransactionState.COMMITTED;
            if(transaction.health.zeroHp()) { state=State.PAUSED; reason="Zero D&D HP: encounter paused for DM healing or termination; no death saves"; }
            else { state=State.ACTIVE; reason=transaction.roll.hit()?"Attack committed":"Miss committed; action spent"; }
        } catch(java.io.IOException failure) {
            transaction.state=TransactionState.SAVE_FAILED; state=State.PAUSED;
            reason="Storage failed: action reserved, dice retained, no HP published. DM may retry the same transaction.";
        } catch(IllegalArgumentException conflict) {
            transaction.state=TransactionState.SAVE_FAILED; state=State.PAUSED;
            reason="Resource conflict: no automatic rebase or reroll. End encounter and adjudicate.";
        }
        revision++;
        if(transaction.state==TransactionState.COMMITTED) try {
            var t=transaction;var r=t.roll.roll();
            var mode=SrdRules.rollMode(t.intent.geometry.advantage(),t.intent.geometry.disadvantage()||t.intent.plan.disadvantage());
            var roll=dev.tacticalcombat.uiapi.UiEvents.Roll.from(20,r.faces(),mode==SrdRules.RollMode.NORMAL?0:mode==SrdRules.RollMode.ADVANTAGE?1:2,r.total()-r.chosen(),"SRD attack",t.roll.critical());
            dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(t.intent.id,campaign,id,t.intent.turn,dev.tacticalcombat.uiapi.UiEvents.Kind.ATTACK,List.of(t.intent.actor,t.intent.target),t.roll.hit()?"Hit committed":"Miss committed",roll,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,Set.copyOf(List.of(t.intent.requester,t.intent.actor)));
            var before=t.intent.defender.stats();
            int lost=before.hp()-t.health.current(),absorbed=before.temporaryHp()-t.health.temporary();
            var recipients=new java.util.HashSet<UUID>();recipients.add(t.intent.target);
            var targetRecord=CharacterService.repository().character(t.intent.defender.character());
            recipients.add(targetRecord.owner());recipients.add(CharacterService.repository().controller(targetRecord.id()));
            if(lost>0 || absorbed>0) dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(
                dev.tacticalcombat.uiapi.UiEvents.derived(t.intent.id,"damage"),campaign,id,t.intent.turn,dev.tacticalcombat.uiapi.UiEvents.Kind.DAMAGE,
                List.of(t.intent.target),"SRD committed HP loss: "+lost+"; temporary HP spent: "+absorbed,null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,recipients);
            if(before.hp()>0 && t.health.zeroHp()) dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(
                dev.tacticalcombat.uiapi.UiEvents.derived(t.intent.id,"zero"),campaign,id,t.intent.turn,dev.tacticalcombat.uiapi.UiEvents.Kind.ZERO_HP,
                List.of(t.intent.target),"Zero D&D HP; paused for DM adjudication, no automatic death saves",null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,recipients);
        } catch(RuntimeException presentation) {dev.tacticalcombat.TacticalCombatMod.LOGGER.warn("Could not record committed attack presentation",presentation);}
    }
    void retry(CharacterRepository.Principal p) {
        thread(); require(manager(p) && state==State.PAUSED && transaction!=null && transaction.state==TransactionState.SAVE_FAILED,"No failed transaction or permission denied");
        persistTransaction();
    }
    void heal(CharacterRepository.Principal p,UUID target,int amount) throws java.io.IOException {
        thread(); require(manager(p) && state==State.PAUSED,"Healing adjudication requires a paused encounter and campaign manager");
        require(transaction==null || transaction.state==TransactionState.COMMITTED,"Resolve failed attack before adjudication");
        var expected=fresh(actor(target)); var saved=CharacterService.repository().commitSrdHealth(expected,SrdHealth.of(expected.stats()).heal(amount));
        snapshots.put(target,saved); revision++; reason="DM healing committed; end this controlled encounter to resume exploration";
        dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(UUID.randomUUID(),campaign,id,turn(),dev.tacticalcombat.uiapi.UiEvents.Kind.HEALING,List.of(target),"DM healing committed: "+(saved.stats().hp()-expected.stats().hp()),null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,Set.of(target));
        dev.tacticalcombat.TacticalCombatMod.LOGGER.info("SRD DM {} healed actor {} by {} in {}",p.id(),target,amount,id);
    }
    void stop(CharacterRepository.Principal p) { thread(); require(manager(p),"Campaign manager required"); CombatManager.endCombat(this); }
    boolean admit(CharacterRepository.Principal p,dev.tacticalcombat.net.SrdRequestPayload request) {
        thread();
        if(!viewer(p) || state==State.ENDED) return false;
        if(state==State.ACTIVE && !controls(p,current())) return false;
        if(state==State.PAUSED && !manager(p)) return false;
        return requests.accept(request.encounter(),request.turn(),request.request());
    }
    public boolean tick() {
        require(world.getServer().isOnThread(),"Encounter tick requires server thread");
        if(state==State.ENDED) return true;
        for(var actor:actors) {
            if(!actor.isAlive() || actor.isRemoved() || actor.getWorld()!=world || world.getEntity(actor.getUuid())!=actor) {
                reason="Actor unavailable; controlled encounter ended"; return true;
            }
            if(actor instanceof ServerPlayerEntity player && (world.getServer().getPlayerManager().getPlayer(player.getUuid())!=player
                    || !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,SrdStatePayload.ID))) {
                reason="Client disconnected or lost SRD capability; controlled encounter ended"; return true;
            }
            try {
                if(!snapshots.get(actor.getUuid()).equals(CharacterService.srdStats(actor,campaign))) {
                    if(state!=State.PAUSED) revision++; state=State.PAUSED; reason="Character changed; adjudication required";
                }
            } catch(RuntimeException unavailable) { if(state!=State.PAUSED) revision++; state=State.PAUSED; reason="Character unavailable; adjudication required"; }
            if(state==State.ACTIVE && actor==current() && movement!=null) {
                var result=movement.tick(); anchors.put(actor.getUuid(),actor.getPos());
                if(!result.accepted()) { reason=result.message(); revision++; }
                else if(result.message().equals("Step completed")) { intent=null; revision++; }
                if(movement.closed()) state=State.PAUSED;
            }
            Vec3d anchor=anchors.get(actor.getUuid());
            if(actor.getPos().squaredDistanceTo(anchor)>1e-6) {
                if(actor instanceof ServerPlayerEntity player) player.networkHandler.requestTeleport(anchor.x,anchor.y,anchor.z,actor.getYaw(),actor.getPitch());
                else actor.refreshPositionAndAngles(anchor.x,anchor.y,anchor.z,actor.getYaw(),actor.getPitch());
            }
            actor.setVelocity(Vec3d.ZERO); actor.fallDistance=0;
            if(actor instanceof MobEntity mob) { mob.setAiDisabled(true); mob.getNavigation().stop(); }
        }
        return false;
    }
    public void end() {
        require(world.getServer().isOnThread(),"Encounter cleanup requires server thread");
        if(state==State.ENDED) return;
        state=State.ENDED;
        revision++;
        for(var actor:actors) {
            // A failed partial claim must not alter another encounter's participant.
            if(CombatManager.encounter(actor)!=this) continue;
            CombatInteractions.cancel(actor); actor.setVelocity(Vec3d.ZERO);
            if(actor instanceof MobEntity mob && previousAi.containsKey(actor.getUuid())) mob.setAiDisabled(previousAi.get(actor.getUuid()));
            CombatManager.release(actor,this);
        }
    }
    /** Private snapshot: public order/positions, own HP only; managers may see campaign actors. */
    void authorizeViewer(CharacterRepository.Principal p) {thread();require(viewer(p),"Encounter permission denied");}
    public JsonObject presentation(CharacterRepository.Principal p) {return snapshot(p);}
    JsonObject snapshot(CharacterRepository.Principal p) {
        authorizeViewer(p);
        var data=new JsonObject(); data.addProperty("state",state.name()); data.addProperty("turn",turn()); data.addProperty("round",round);
        data.addProperty("campaign",campaign.toString()); data.addProperty("manager",manager(p));
        data.addProperty("reason",reason); data.addProperty("actionUsed",actionUsed);
        data.addProperty("active",current()==null?"":current().getUuid().toString());
        data.addProperty("remainingFeet",movement==null?0:movement.remainingFeet());
        var entries=new JsonArray();
        for(var id:order.isEmpty()?actors.stream().map(Entity::getUuid).toList():order) {
            var actor=actor(id); var entry=new JsonObject(); var stats=snapshots.get(id).stats();
            entry.addProperty("id",id.toString()); entry.addProperty("entity",actor.getId());
            entry.addProperty("name",actor.getName().getString().substring(0,Math.min(64,actor.getName().getString().length())));
            entry.addProperty("x",actor.getX()); entry.addProperty("y",actor.getY()); entry.addProperty("z",actor.getZ());
            entry.addProperty("initiative",initiative.containsKey(id)?initiative.get(id).total():0);
            entry.addProperty("controlled",controls(p,actor));
            if(controls(p,actor)) {
                entry.addProperty("hp",stats.hp()); entry.addProperty("maximumHp",stats.maximumHp()); entry.addProperty("temporaryHp",stats.temporaryHp());
                entry.addProperty("arrows",stats.arrows());entry.addProperty("bolts",stats.bolts());entry.addProperty("daggers",stats.daggers());
                var weapons=new JsonArray(); weapons.add("UNARMED"); stats.weapons().forEach(w->weapons.add(w.name())); entry.add("weapons",weapons);
            }
            entries.add(entry);
        }
        data.add("actors",entries);
        var ties=new JsonArray(); for(var group:groups) if(group.size()>1) { var ids=new JsonArray(); group.forEach(id->ids.add(id.toString())); ties.add(ids); } data.add("ties",ties);
        return data;
    }
}
