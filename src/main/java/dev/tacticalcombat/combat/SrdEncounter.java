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
    private final Set<UUID> prone=new HashSet<>();
    private final Set<UUID> dodging=new HashSet<>();
    private SrdActionBudget budget=new SrdActionBudget();
    private final Map<UUID,SrdReactionBudget> reactions=new HashMap<>();
    private boolean disengaged;
    private Opportunity opportunity;
    private Vec3d departureFrom,departureTo;
    private List<UUID> departureCandidates=List.of();
    private long reactionIntentRevision=-1;
    private final Set<UUID> considered=new HashSet<>();
    private static final int REACTION_TICKS=400;
    private long clock;
    private static final class Opportunity {
        final UUID id=UUID.randomUUID(),reactor,mover;
        final long deadline;
        final List<ReactionChoice> choices;
        Opportunity(UUID reactor,UUID mover,long deadline,List<ReactionChoice> choices){this.reactor=reactor;this.mover=mover;this.deadline=deadline;this.choices=choices;}
    }
    record ReactionChoice(SrdEquipment.Weapon weapon,SrdCharacter.Ability ability,boolean twoHands) {}
    private Intent intent;
    private Transaction transaction;
    private final SrdRules.Dice dice;
    // Package-local fault seam, never supplied by a client. Runs strictly after the durable commit.
    Runnable afterDurableCommit=()->{};
    record Intent(UUID id,UUID requester,long turn,UUID actor,UUID target,SrdEquipment.Weapon weapon,
                  SrdCharacter.Ability ability,boolean twoHands,CharacterRepository.SrdSnapshot attacker,
                  CharacterRepository.SrdSnapshot defender,Vec3d from,Vec3d to,SrdRules.AttackPlan plan,SrdTargeting.Target geometry) {}
    enum TransactionState { PREPARED, SAVE_FAILED, COMMITTED }
    static final class Transaction {
        final Intent intent; final SrdRules.AttackResult roll; final SrdHealth health; final GameplayActionPlan plan;
        TransactionState state=TransactionState.PREPARED;
        Transaction(Intent intent,SrdRules.AttackResult roll,SrdHealth health,GameplayActionPlan plan) { this.intent=intent; this.roll=roll; this.health=health;this.plan=plan; }
    }

    private SrdEncounter(ServerWorld world,UUID campaign,List<LivingEntity> actors,SrdRules.Dice dice) {
        this.world=world; this.campaign=campaign; this.actors=List.copyOf(actors);
        this.dice=dice;
        actors.forEach(a->reactions.put(a.getUuid(),new SrdReactionBudget()));
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
            var snapshot=validateAdmission(world,campaign,actor);
            encounter.snapshots.put(actor.getUuid(),snapshot);
        }
        var original=new HashMap<LivingEntity,Vec3d>();var velocities=new HashMap<LivingEntity,Vec3d>();
        for(var actor:actors){original.put(actor,actor.getPos());velocities.put(actor,actor.getVelocity());}
        var claimed=new ArrayList<LivingEntity>();
        try {
            for(var actor:actors) {
                require(CombatManager.claim(actor,encounter),"Actor ownership changed"); claimed.add(actor);
                var terrain=new SrdTerrain(actor,encounter.snapshots.get(actor.getUuid()).stats());
                require(terrain.alignment(terrain.admissionCell())==SrdMovement.Reason.OK,"Admission geometry changed");
                Vec3d anchor=terrain.position(terrain.admissionCell());actor.requestTeleport(anchor.x,anchor.y,anchor.z);
                encounter.anchors.put(actor.getUuid(),actor.getPos());
                if(actor instanceof MobEntity mob) {
                    encounter.previousAi.put(actor.getUuid(),mob.isAiDisabled()); mob.setAiDisabled(true); mob.getNavigation().stop();
                }
                CombatInteractions.cancel(actor); actor.setVelocity(Vec3d.ZERO);
            }
            CombatManager.schedule(encounter); return encounter;
        } catch(RuntimeException failure) { encounter.end();for(var actor:actors){var old=original.get(actor);actor.requestTeleport(old.x,old.y,old.z);actor.setVelocity(velocities.get(actor));}throw failure; }
    }
    /** Shared side-effect-free admission check for discovery and the trusted start path. */
    static CharacterRepository.SrdSnapshot validateAdmission(ServerWorld world,UUID campaign,LivingEntity actor) {
            require(actor.getWorld()==world && actor.isAlive() && !actor.isRemoved() && !actor.isSpectator()
                    && world.getEntity(actor.getUuid())==actor,"Actor must be loaded and alive in this world");
            if(actor instanceof ServerPlayerEntity player)
                require(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,SrdStatePayload.ID),"Updated SRD v1 client required");
            else require(actor instanceof MobEntity,"Only explicitly bound living mobs are supported NPCs");
            require(!actor.hasVehicle() && !actor.hasPassengers() && !actor.isSwimming() && !actor.isFallFlying(),"Unsupported movement mode");
            require(!CombatManager.isInCombat(actor),"Actor already belongs to an encounter");
            var snapshot=CharacterService.srdStats(actor,campaign);
            CharacterService.repository().requireUnreserved(snapshot.character());
            require(snapshot.stats().hp()>0,"Zero-HP actor needs adjudication before admission");
            require(CharacterService.mayJoin(actor,campaign,RulesProfiles.get(RulesProfiles.SRD)),"Incompatible campaign or ruleset");
            var terrain=new SrdTerrain(actor,snapshot.stats()); var cell=terrain.admissionCell();
            require(terrain.alignment(cell)==SrdMovement.Reason.OK,
                    "No safe nearby grid anchor: check floor, clearance and occupied space");
        return snapshot;
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
    public boolean actionUsed() { return budget.used(); }
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
        dodging.remove(current().getUuid());
        reactions.get(current().getUuid()).refresh();disengaged=false;clearDeparture();
        requests.nextTurn(); budget=new SrdActionBudget(); intent=null; transaction=null;
        state=State.ACTIVE; reason="Controlled public-position free-for-all; opportunity reactions; no death saves";
        try { movement=new SrdMovementTurn(current(),this::frame,prone.contains(current().getUuid())); movement.departure(this::departure); }
        catch(IllegalArgumentException denied) { state=State.PAUSED; reason=denied.getMessage(); movement=null; }
        revision++;
        dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(UUID.randomUUID(),campaign,id,turn(),dev.tacticalcombat.uiapi.UiEvents.Kind.TURN,List.of(current().getUuid()),"Turn started",null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,actors.stream().filter(a->a instanceof ServerPlayerEntity).map(Entity::getUuid).collect(java.util.stream.Collectors.toSet()));
    }
    private SrdMovementTurn.Frame frame() { return new SrdMovementTurn.Frame(id,campaign,RulesProfiles.SRD,turn(),state==State.ACTIVE?current().getUuid():null); }
    void endTurn(CharacterRepository.Principal p) {
        thread(); require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        require(opportunity==null,"Resolve the pending reaction first");
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
    SrdMovementTurn.Result move(CharacterRepository.Principal p,UUID request,SrdMovement.Cell destination) {return move(p,request,destination,1);}
    SrdMovementTurn.Result move(CharacterRepository.Principal p,UUID request,SrdMovement.Cell destination,int stepTicks) {
        thread(); require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        intent=null;
        var result=movement.request(p,new SrdMovementTurn.Request(id,campaign,RulesProfiles.SRD,turn(),current().getUuid(),request,destination),stepTicks);
        revision++; return result;
    }
    /** Conservative eligibility: recorded condition/effect prose is not an executable condition system. */
    private boolean dodgeEligible(LivingEntity actor) {
        if(state!=State.ACTIVE && state!=State.PAUSED || actor.isSleeping())return false;
        try {
            var stats=fresh(actor);if(stats.stats().hp()<=0 || stats.rules().walkingSpeedFeet()<=0)return false;
            var sheet=JsonParser.parseString(CharacterService.repository().character(stats.character()).sheet()).getAsJsonObject();
            for(var entry:dev.tacticalcombat.uiapi.SheetSections.read(sheet,"features").getAsJsonArray("entries"))
                if(Set.of("CONDITION","TEMPORARY").contains(entry.getAsJsonObject().get("kind").getAsString()))return false;
            return true;
        }catch(IllegalArgumentException unavailable){return false;}
    }
    private void refreshDodge(){if(dodging.removeIf(id->!dodgeEligible(actor(id))))revision++;}
    boolean dodging(LivingEntity actor){thread();refreshDodge();return dodging.contains(actor.getUuid());}
    /** Trusted check gateway must associate the exact repository binding with this encounter. */
    public boolean dodgeSave(UUID character,UUID actorId){
        thread();var actor=actor(actorId);require(fresh(actor).character().equals(character),"Check actor binding changed");
        return dodging(actor);
    }
    void dodge(CharacterRepository.Principal p,UUID expectedCampaign,UUID expectedActor,long expectedRevision){
        thread();require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        require(campaign.equals(expectedCampaign) && current().getUuid().equals(expectedActor) && revision==expectedRevision,"Stale Dodge scope or revision");
        require(!budget.used() && movement!=null && !movement.moving() && transaction==null,"Dodge requires an available action, no route and no pending weapon transaction");
        require(dodgeEligible(current()),"Dodge unavailable: requires positive speed/HP, an awake actor and no unresolved recorded conditions or temporary effects");
        CharacterService.repository().requireUnreserved(fresh(current()).character());
        movement.validatePreview(p);budget.spend();dodging.add(current().getUuid());intent=null;revision++;
        reason="Controlled action accepted";
    }
    boolean prone(LivingEntity actor){return prone.contains(actor.getUuid());}
    void posture(CharacterRepository.Principal p,UUID expectedCampaign,UUID expectedActor,long expectedRevision,boolean nextProne){
        thread();require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        require(campaign.equals(expectedCampaign)&&current().getUuid().equals(expectedActor)&&revision==expectedRevision,"Stale posture scope or revision");
        require(movement!=null&&!movement.moving()&&(transaction==null||transaction.state==TransactionState.COMMITTED),"Finish movement or resolve pending weapon transaction first");
        var latest=fresh(current());require(latest.stats().hp()>0,"Posture changes unavailable at zero HP");CharacterService.repository().requireUnreserved(latest.character());
        require(prone(current())!=nextProne,nextProne?"Already Prone":"Already standing");
        movement.posture(p,nextProne);
        if(nextProne)prone.add(current().getUuid());else prone.remove(current().getUuid());
        intent=null;revision++;reason="Controlled posture updated";
    }
    int dash(CharacterRepository.Principal p,UUID expectedCampaign,UUID expectedActor,long expectedRevision) {
        thread();
        require(state==State.ACTIVE && controls(p,current()),"Active actor or campaign manager required");
        require(campaign.equals(expectedCampaign) && current().getUuid().equals(expectedActor) && revision==expectedRevision,"Stale Dash scope or revision");
        require(!budget.used() && movement!=null && !movement.moving(),"Dash requires an available normal action and no executing route");
        require(transaction==null,"Resolve pending gameplay transaction first");
        var latest=fresh(current());require(latest.stats().hp()>0,"Cannot Dash at zero HP");
        CharacterService.repository().requireUnreserved(latest.character());
        // Every check precedes mutation; neither grant nor action spending performs IO or callbacks.
        movement.dash(p);budget.spend();intent=null;revision++;
        reason="Dash accepted: +"+movement.speedFeet()+" ft; "+movement.remainingFeet()+" ft remaining; normal action spent";
        return movement.speedFeet();
    }
    void cancelMove(CharacterRepository.Principal p) {
        thread(); require(state==State.ACTIVE && controls(p,current()) && opportunity==null,"Resolve pending reaction first or permission denied"); movement.cancel(p);clearDeparture(); revision++;
    }
    void disengage(CharacterRepository.Principal p,UUID expectedCampaign,UUID expectedActor,long expectedRevision){
        thread();require(state==State.ACTIVE&&controls(p,current()),"Active actor or campaign manager required");
        require(campaign.equals(expectedCampaign)&&current().getUuid().equals(expectedActor)&&revision==expectedRevision,"Stale Disengage scope or revision");
        require(!budget.used()&&movement!=null&&!movement.moving()&&transaction==null&&opportunity==null,"Disengage requires an available action and no pending route/transaction");
        require(fresh(current()).stats().hp()>0,"Cannot Disengage at zero HP");CharacterService.repository().requireUnreserved(fresh(current()).character());movement.validatePreview(p);
        budget.spend();disengaged=true;intent=null;revision++;reason="Disengage accepted; normal action spent; movement protected this turn";
    }
    private void clearDeparture(){opportunity=null;departureFrom=departureTo=null;departureCandidates=List.of();reactionIntentRevision=-1;considered.clear();}
    private boolean reactionEligible(LivingEntity reactor){
        if(!reactions.get(reactor.getUuid()).available()||reactor.isSleeping())return false;
        try{
            var s=fresh(reactor);if(s.stats().hp()<=0)return false;
            var sheet=JsonParser.parseString(CharacterService.repository().character(s.character()).sheet()).getAsJsonObject();
            for(var e:dev.tacticalcombat.uiapi.SheetSections.read(sheet,"features").getAsJsonArray("entries"))
                if(Set.of("CONDITION","TEMPORARY").contains(e.getAsJsonObject().get("kind").getAsString()))return false;
            CharacterService.repository().requireUnreserved(s.character());return true;
        }catch(IllegalArgumentException unavailable){return false;}
    }
    private List<ReactionChoice> reactionChoices(LivingEntity reactor,Vec3d from,Vec3d to){
        if(!reactionEligible(reactor))return List.of();
        var a=fresh(reactor);var b=fresh(current());
        if(!SrdTargeting.canSeeAttacker(reactor,a.stats(),current(),b.stats()))return List.of();
        var monster=MonsterProfiles.identity(JsonParser.parseString(CharacterService.repository().character(a.character()).sheet()).getAsJsonObject());
        var weapons=new LinkedHashSet<SrdEquipment.Weapon>();if(monster==null)weapons.add(SrdEquipment.Weapon.UNARMED);weapons.addAll(a.stats().weapons());
        var choices=new ArrayList<ReactionChoice>();
        for(var w:weapons){if(w.ranged)continue;
            var abilities=monster!=null?List.of(monster.get("source").getAsString().endsWith(":zombie")?SrdCharacter.Ability.STR:SrdCharacter.Ability.DEX):w.has(SrdEquipment.Property.FINESSE)?List.of(SrdCharacter.Ability.STR,SrdCharacter.Ability.DEX):List.of(SrdCharacter.Ability.STR);
            for(var ability:abilities)for(boolean hands:w.has(SrdEquipment.Property.VERSATILE)?List.of(false,true):List.of(w.has(SrdEquipment.Property.TWO_HANDED))){
                try{var plan=a.rules().plan(w,SrdRules.AttackMode.MELEE,ability,hands);
                    if(SrdReactionBudget.leavesReach(SrdTargeting.feet(reactor.getPos(),a.stats(),from,b.stats()),SrdTargeting.feet(reactor.getPos(),a.stats(),to,b.stats()),plan.reachFeet())){legalTarget(reactor,current(),plan);choices.add(new ReactionChoice(w,ability,hands));}
                }catch(IllegalArgumentException unsupported){}
            }
        }return List.copyOf(choices);
    }
    private boolean departure(Vec3d from,Vec3d to){
        if(disengaged)return true;
        if(!from.equals(departureFrom)||!to.equals(departureTo)){clearDeparture();departureFrom=from;departureTo=to;departureCandidates=order.stream().filter(candidate->!candidate.equals(current().getUuid())&&!reactionChoices(actor(candidate),from,to).isEmpty()).toList();}
        if(opportunity!=null)return false;
        for(UUID candidate:departureCandidates){if(candidate.equals(current().getUuid())||considered.contains(candidate))continue;
            var options=reactionChoices(actor(candidate),from,to);considered.add(candidate);
            if(!options.isEmpty()){opportunity=new Opportunity(candidate,current().getUuid(),clock+REACTION_TICKS,options);intent=null;revision++;reason="Movement interrupted — awaiting reaction";return false;}
        }return true;
    }
    private void requireOpportunity(CharacterRepository.Principal p,UUID trigger,UUID reactor,UUID mover,UUID expectedCampaign,long expectedRevision){
        thread();require(state==State.ACTIVE&&opportunity!=null&&opportunity.id.equals(trigger)&&opportunity.reactor.equals(reactor)&&opportunity.mover.equals(mover)&&campaign.equals(expectedCampaign)&&revision==expectedRevision,"Stale reaction scope or revision");
        require(controls(p,actor(reactor))&&clock<opportunity.deadline&&reactionEligible(actor(reactor)),"Reaction expired or controller/eligibility changed");
        require(movement!=null&&movement.moving()&&current().getUuid().equals(mover)&&current().getPos().equals(departureFrom),"Departure no longer pending");
        require(transaction==null||transaction.state==TransactionState.COMMITTED,"Resolve pending transaction first");
    }
    Intent reactionPreview(CharacterRepository.Principal p,UUID trigger,UUID reactor,UUID mover,UUID expectedCampaign,long expectedRevision,ReactionChoice choice){
        requireOpportunity(p,trigger,reactor,mover,expectedCampaign,expectedRevision);
        require(opportunity.choices.contains(choice)&&reactionChoices(actor(reactor),departureFrom,departureTo).contains(choice),"Melee departure option no longer valid");
        var result=buildPreview(p,actor(reactor),actor(mover),choice.weapon,SrdRules.AttackMode.MELEE,choice.ability,choice.twoHands);reactionIntentRevision=revision;return result;
    }
    Transaction reactionConfirm(CharacterRepository.Principal p,UUID trigger,UUID reactor,UUID mover,UUID expectedCampaign,long expectedRevision,UUID intentId){
        requireOpportunity(p,trigger,reactor,mover,expectedCampaign,expectedRevision);var selected=intent;
        require(reactionIntentRevision==revision&&selected!=null&&selected.id.equals(intentId)&&selected.requester.equals(p.id())&&selected.turn==turn()&&selected.actor.equals(reactor)&&selected.target.equals(mover),"Stale reaction intent");
        require(selected.attacker.equals(fresh(actor(reactor)))&&selected.defender.equals(fresh(current()))&&selected.from.equals(actor(reactor).getPos())&&selected.to.equals(current().getPos()),"Reaction participants changed");
        require(reactionChoices(actor(reactor),departureFrom,departureTo).contains(new ReactionChoice(selected.weapon,selected.ability,selected.twoHands))&&selected.geometry.equals(legalTarget(actor(reactor),current(),selected.plan)),"Reaction geometry changed");
        var result=acceptAttack(p,selected,true);if(result.state==TransactionState.COMMITTED)finishOpportunity();return result;
    }
    void reactionDecline(CharacterRepository.Principal p,UUID trigger,UUID reactor,UUID mover,UUID expectedCampaign,long expectedRevision){requireOpportunity(p,trigger,reactor,mover,expectedCampaign,expectedRevision);finishOpportunity();reason="Reaction declined; no reaction spent";}
    private void finishOpportunity(){opportunity=null;intent=null;if(transaction!=null&&transaction.state==TransactionState.COMMITTED&&transaction.plan.reactionCost()==1)transaction=null;revision++;}
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
        return SrdTargeting.posture(new SrdTargeting.Target(geometry.feet(),geometry.cover(),geometry.advantage(),geometry.disadvantage() || ranged && (close || geometry.feet()>plan.normalRangeFeet()) || dodging(target) && SrdTargeting.canSeeAttacker(target,b,attacker,a)),prone(attacker),prone(target));
    }
    Intent preview(CharacterRepository.Principal p,UUID targetId,SrdEquipment.Weapon weapon,SrdCharacter.Ability ability,boolean twoHands) {
        return preview(p,targetId,weapon,SrdRules.AttackMode.MELEE,ability,twoHands);
    }
    Intent preview(CharacterRepository.Principal p,UUID targetId,SrdEquipment.Weapon weapon,SrdRules.AttackMode mode,SrdCharacter.Ability ability,boolean twoHands) {
        thread(); require(state==State.ACTIVE && controls(p,current()) && !budget.used() && !movement.moving(),"No eligible active action");
        return buildPreview(p,current(),actor(targetId),weapon,mode,ability,twoHands);
    }
    private Intent buildPreview(CharacterRepository.Principal p,LivingEntity attacker,LivingEntity target,SrdEquipment.Weapon weapon,SrdRules.AttackMode mode,SrdCharacter.Ability ability,boolean twoHands){
        UUID targetId=target.getUuid();var a=fresh(attacker); var b=fresh(target);
        var monster=dev.tacticalcombat.rules.srd.MonsterProfiles.identity(com.google.gson.JsonParser.parseString(CharacterService.repository().character(a.character()).sheet()).getAsJsonObject());
        if(monster!=null){require(a.stats().weapons().contains(weapon),"Monster action is not supported");require(ability==(monster.get("source").getAsString().endsWith(":zombie")?SrdCharacter.Ability.STR:SrdCharacter.Ability.DEX),"Use the reviewed monster attack ability");}
        var plan=a.rules().plan(weapon,mode,ability,twoHands);
        var geometry=legalTarget(attacker,target,plan);
        intent=new Intent(UUID.randomUUID(),p.id(),turn(),attacker.getUuid(),targetId,weapon,ability,twoHands,a,b,attacker.getPos(),target.getPos(),plan,geometry);
        revision++; return intent;
    }
    Transaction confirm(CharacterRepository.Principal p,UUID intentId) {
        thread(); require(state==State.ACTIVE && controls(p,current()) && !budget.used() && !movement.moving(),"No eligible active action");
        Intent selected=intent;
        require(selected!=null && selected.id.equals(intentId) && selected.requester.equals(p.id()) && selected.turn==turn(),"Stale or unauthorized attack intent");
        var attacker=actor(selected.actor); var target=actor(selected.target);
        require(selected.attacker.equals(fresh(attacker)) && selected.defender.equals(fresh(target))
                && selected.from.equals(attacker.getPos()) && selected.to.equals(target.getPos()),"Attack preview changed; preview again");
        require(selected.geometry.equals(legalTarget(attacker,target,selected.plan)),"Target geometry or defensive benefit changed; preview again");
        return acceptAttack(p,selected,false);
    }
    private Transaction acceptAttack(CharacterRepository.Principal p,Intent selected,boolean reaction) {
        // Acceptance happens once, before RNG. A storage failure retains these exact dice and expected revision.
        CharacterService.repository().requireGameplayCapacity();CharacterService.repository().requireUnreserved(selected.attacker.character());CharacterService.repository().requireUnreserved(selected.defender.character());
        if(reaction)reactions.get(selected.actor).reserve();else budget.reserve(); intent=null;
        var roll=selected.attacker.rules().attack(selected.weapon,selected.plan.mode(),selected.ability,selected.twoHands,
                selected.defender.rules().armorClass()+selected.geometry.cover().ac,selected.geometry.advantage(),selected.geometry.disadvantage(),dice);
        var health=SrdHealth.of(selected.defender.stats()).damage(List.of(roll.damage()),selected.defender.stats());
        var repo=CharacterService.repository();var attack=new GameplayActionPlan.Attack(selected.weapon,selected.plan.mode(),selected.ability,selected.twoHands,selected.geometry.cover().ac,selected.geometry.advantage(),selected.geometry.disadvantage());
        var plan=GameplayActionPlan.weapon(selected.id,p.id(),campaign,id,selected.turn,selected.attacker,repo.controller(selected.attacker.character()),selected.defender,repo.controller(selected.defender.character()),attack,roll);
        if(reaction)plan=plan.asReaction();
        transaction=new Transaction(selected,roll,health,plan); persistTransaction(p); return transaction;
    }
    private void persistTransaction(CharacterRepository.Principal requester) {
        boolean wasCommitted=transaction.state==TransactionState.COMMITTED;
        try {
            var repo=CharacterService.repository();
            repo.prepareGameplay(requester,transaction.plan);
            repo.commitGameplay(requester,transaction.plan.operation(),transaction.plan.fingerprint());
            // Persistence is authoritative even if a later movement/presentation update fails.
            transaction.state=TransactionState.COMMITTED;
            if(!wasCommitted){if(transaction.plan.reactionCost()==1)reactions.get(transaction.intent.actor).commit();else budget.committed();}
            afterDurableCommit.run();
            var saved=List.of(repo.srdSnapshot(transaction.intent.actor,campaign),repo.srdSnapshot(transaction.intent.target,campaign));
            for(var snapshot:saved)snapshots.put(snapshot.actor(),snapshot);
            movement.reconcileResources(saved.stream().filter(s->s.actor().equals(current().getUuid())).findFirst().orElseThrow());
            if(transaction.health.zeroHp()) { movement.halt();state=State.PAUSED; reason="Zero D&D HP: encounter paused for DM healing or termination; no death saves"; }
            else { state=State.ACTIVE; reason=transaction.plan.reactionCost()==1?(transaction.roll.hit()?"Opportunity attack committed; reaction spent":"Opportunity attack missed; reaction spent"):(transaction.roll.hit()?"Attack committed":"Miss committed; action spent"); }
        } catch(java.io.IOException failure) {
            transaction.state=TransactionState.SAVE_FAILED; state=State.PAUSED;
            boolean durable=CharacterService.repository().gameplayReceipt(transaction.plan.operation())!=null;
            reason=durable?"Prepared outcome saved; commit failed. Resources unchanged. DM may retry without reroll.":"Preparation failed; no durable accepted outcome. Resources unchanged. DM may retry retained dice while this encounter remains open.";
        } catch(RuntimeException conflict) {
            state=State.PAUSED;
            if(transaction.state==TransactionState.COMMITTED)reason="Resources committed; presentation reconciliation failed. Refresh or end; do not retry spending.";
            else {transaction.state=TransactionState.SAVE_FAILED;reason="Resource or permission conflict: no automatic rebase/reroll. End encounter and review prepared operations.";}
        }
        revision++;
        if(transaction.state==TransactionState.COMMITTED&&!wasCommitted) try {
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
        fresh(actor(transaction.intent.actor));fresh(actor(transaction.intent.target));
        persistTransaction(p);
        if(transaction.state==TransactionState.COMMITTED && transaction.plan.reactionCost()==1)finishOpportunity();
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
        boolean reaction=Set.of("reaction_preview","reaction_confirm","reaction_decline").contains(request.operation());
        if(reaction){if(state!=State.ACTIVE||opportunity==null||!controls(p,actor(opportunity.reactor)))return false;}
        else if(state==State.ACTIVE && (!controls(p,current()) || opportunity!=null&&!request.operation().equals("end"))) return false;
        if(state==State.PAUSED && !manager(p)) return false;
        return requests.accept(request.encounter(),request.turn(),request.request());
    }
    public boolean tick() {
        require(world.getServer().isOnThread(),"Encounter tick requires server thread");
        if(state==State.ENDED) return true;
        clock++;
        if(state==State.ACTIVE && opportunity!=null && (clock>=opportunity.deadline || !reactionEligible(actor(opportunity.reactor))))finishOpportunity();
        refreshDodge();
        for(var actor:actors) {
            if(!actor.isAlive() || actor.isRemoved() || actor.getWorld()!=world || world.getEntity(actor.getUuid())!=actor) {
                reason="Actor unavailable; controlled encounter ended"; dodging.clear();return true;
            }
            if(actor instanceof ServerPlayerEntity player && (world.getServer().getPlayerManager().getPlayer(player.getUuid())!=player
                    || !net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.canSend(player,SrdStatePayload.ID))) {
                reason="Client disconnected or lost SRD capability; controlled encounter ended"; dodging.clear();return true;
            }
            try {
                if(!snapshots.get(actor.getUuid()).equals(CharacterService.srdStats(actor,campaign))) {
                    if(state!=State.PAUSED) revision++; state=State.PAUSED; reason="Character changed; adjudication required";
                }
            } catch(RuntimeException unavailable) { if(state!=State.PAUSED) revision++; state=State.PAUSED; reason="Character unavailable; adjudication required"; }
            if(state==State.ACTIVE && actor==current() && movement!=null) {
                var result=movement.tick(); anchors.put(actor.getUuid(),actor.getPos());
                if(!result.accepted()) { if(opportunity!=null)finishOpportunity();reason=result.message(); revision++; }
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
        refreshDodge();return false;
    }
    public void end() {
        require(world.getServer().isOnThread(),"Encounter cleanup requires server thread");
        if(state==State.ENDED) return;
        state=State.ENDED;dodging.clear();prone.clear();clearDeparture();reactions.clear();
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
        authorizeViewer(p);refreshDodge();
        var data=new JsonObject(); data.addProperty("state",state.name()); data.addProperty("turn",turn()); data.addProperty("round",round);
        data.addProperty("campaign",campaign.toString()); data.addProperty("manager",manager(p));
        if(manager(p)) {
            var recovery=new JsonObject();boolean paused=state==State.PAUSED;
            boolean unresolved=transaction!=null&&transaction.state!=TransactionState.COMMITTED;
            recovery.addProperty("kind",!paused?"NONE":unresolved?"SAVE_FAILED":"ADJUDICATION");
            recovery.addProperty("canHeal",paused&&!unresolved);
            recovery.addProperty("canRetry",paused&&transaction!=null&&transaction.state==TransactionState.SAVE_FAILED);
            recovery.addProperty("maximumHealing",1024);data.add("recovery",recovery);
        }
        data.addProperty("reason",reason); data.addProperty("actionUsed",budget.used());
        data.addProperty("active",current()==null?"":current().getUuid().toString());
        data.addProperty("remainingFeet",movement==null?0:movement.remainingFeet());
        data.addProperty("moving",movement!=null && movement.moving());
        data.addProperty("reactionWaiting",opportunity!=null);
        if(opportunity!=null && controls(p,actor(opportunity.reactor))){
            var prompt=new JsonObject();prompt.addProperty("id",opportunity.id.toString());prompt.addProperty("reactor",opportunity.reactor.toString());prompt.addProperty("target",opportunity.mover.toString());
            prompt.addProperty("ticksRemaining",Math.max(0,opportunity.deadline-clock));prompt.addProperty("revision",revision);
            var options=new JsonArray();for(var choice:opportunity.choices){var o=new JsonObject();o.addProperty("weapon",choice.weapon.name());o.addProperty("ability",choice.ability.name());o.addProperty("twoHands",choice.twoHands);options.add(o);}prompt.add("choices",options);data.add("opportunity",prompt);
        }
        data.addProperty("canDisengage",state==State.ACTIVE && current()!=null && controls(p,current()) && !budget.used() && movement!=null && !movement.moving() && transaction==null);
        if(current()!=null && controls(p,current()))data.addProperty("disengaged",disengaged);
        if(movement!=null&&movement.motion()!=null)data.add("motion",movement.motion());
        if(current()!=null&&controls(p,current())){
            if(movement!=null)data.add("pendingRoute",movement.pendingRoute());
            data.addProperty("routeStatus",movement==null?"Unavailable":movement.routeStatus());
            data.addProperty("prone",prone(current()));data.addProperty("standCost",movement==null?0:SrdMovement.standingCost(movement.speedFeet()));
            data.addProperty("canPosture",state==State.ACTIVE&&movement!=null&&!movement.moving()&&(transaction==null||transaction.state==TransactionState.COMMITTED)&&(!prone(current())||movement.speedFeet()>0&&movement.remainingFeet()>=SrdMovement.standingCost(movement.speedFeet())));
        }
        data.addProperty("canDodge",state==State.ACTIVE && current()!=null && controls(p,current()) && !budget.used() && movement!=null && !movement.moving() && transaction==null && dodgeEligible(current()));
        if(current()!=null && controls(p,current())) data.addProperty("dashFeet",movement==null?0:movement.speedFeet());
        data.addProperty("dimension",world.getRegistryKey().getValue().toString());
        var entries=new JsonArray();
        for(var id:order.isEmpty()?actors.stream().map(Entity::getUuid).toList():order) {
            var actor=actor(id); var entry=new JsonObject(); var stats=snapshots.get(id).stats();
            entry.addProperty("id",id.toString()); entry.addProperty("entity",actor.getId());
            entry.addProperty("name",actor.getName().getString().substring(0,Math.min(64,actor.getName().getString().length())));
            entry.addProperty("x",actor.getX()); entry.addProperty("y",actor.getY()); entry.addProperty("z",actor.getZ());
            entry.addProperty("initiative",initiative.containsKey(id)?initiative.get(id).total():0);
            entry.addProperty("controlled",controls(p,actor));
            if(controls(p,actor)) {
                if(reactions.containsKey(id))entry.addProperty("reaction",reactions.get(id).state().name());entry.addProperty("dodging",dodging.contains(id));entry.addProperty("prone",prone.contains(id));
                entry.addProperty("hp",stats.hp()); entry.addProperty("maximumHp",stats.maximumHp()); entry.addProperty("temporaryHp",stats.temporaryHp());
                entry.addProperty("arrows",stats.arrows());entry.addProperty("bolts",stats.bolts());entry.addProperty("daggers",stats.daggers());
                var monsterIdentity=dev.tacticalcombat.rules.srd.MonsterProfiles.identity(com.google.gson.JsonParser.parseString(CharacterService.repository().character(snapshots.get(id).character()).sheet()).getAsJsonObject());
                var weapons=new JsonArray(); if(monsterIdentity==null)weapons.add("UNARMED");else {entry.addProperty("monsterAbility",monsterIdentity.get("source").getAsString().endsWith(":zombie")?"STR":"DEX");entry.addProperty("monsterSupport",dev.tacticalcombat.rules.srd.MonsterProfiles.get(monsterIdentity.get("source").getAsString()).limitations());} stats.weapons().forEach(w->weapons.add(w.name())); entry.add("weapons",weapons);
            }
            entries.add(entry);
        }
        data.add("actors",entries);
        var sequence=new JsonArray();order.forEach(id->sequence.add(id.toString()));data.add("initiativeOrder",sequence);
        var ties=new JsonArray(); for(var group:groups) if(group.size()>1) { var ids=new JsonArray(); group.forEach(id->ids.add(id.toString())); ties.add(ids); } data.add("ties",ties);
        return data;
    }
}
