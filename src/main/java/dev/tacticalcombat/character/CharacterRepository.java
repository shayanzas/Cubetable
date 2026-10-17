package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.rules.RulesProfiles;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;

/** Immutable snapshots, server-authenticated principals and revision-checked durable mutations. */
public final class CharacterRepository {
    public record Principal(UUID id, boolean admin) {}
    public record Campaign(UUID id,String name,UUID owner,Set<UUID> dms,Set<UUID> members,String profile,long revision,ApprovalPolicy policy) {
        public Campaign { dms=Set.copyOf(dms); members=Set.copyOf(members); Objects.requireNonNull(policy); }
        public Campaign(UUID id,String name,UUID owner,Set<UUID> dms,Set<UUID> members,String profile,long revision) {
            this(id,name,owner,dms,members,profile,revision,ApprovalPolicy.secureDefault());
        }
    }
    public record Character(UUID id,UUID campaign,UUID owner,String profile,int profileVersion,
                            String sheet,Map<String,Double> stats,boolean approved,long revision,long runtimeRevision) {
        public Character { stats=Map.copyOf(stats); }
        public Character(UUID id,UUID campaign,UUID owner,String profile,int profileVersion,String sheet,Map<String,Double> stats,boolean approved,long revision) {
            this(id,campaign,owner,profile,profileVersion,sheet,stats,approved,revision,0);
        }
    }
    public enum Decision { PENDING, ACCEPTED, DECLINED, CANCELLED }
    public record ControlChange(UUID manager,UUID target,Decision decision) {}
    public record Assignment(UUID character,UUID controller,UUID offered,Decision decision,long revision,List<ControlChange> history) {
        public Assignment {history=List.copyOf(history);}
    }
    public record State(Map<UUID,Campaign> campaigns,Map<UUID,Character> characters,Map<UUID,UUID> bindings,Map<UUID,Assignment> assignments,Map<UUID,Long> manualRevisions,Map<UUID,GameplayReceipt> receipts) {
        public State { campaigns=Map.copyOf(campaigns); characters=Map.copyOf(characters); bindings=Map.copyOf(bindings);assignments=Map.copyOf(assignments); manualRevisions=Map.copyOf(manualRevisions);receipts=Map.copyOf(receipts); }
        public State(Map<UUID,Campaign> campaigns,Map<UUID,Character> characters,Map<UUID,UUID> bindings,Map<UUID,Assignment> assignments,Map<UUID,Long> manualRevisions){this(campaigns,characters,bindings,assignments,manualRevisions,Map.of());}
        public State(Map<UUID,Campaign> campaigns,Map<UUID,Character> characters,Map<UUID,UUID> bindings,Map<UUID,Assignment> assignments) {this(campaigns,characters,bindings,assignments,Map.of());}
        public State(Map<UUID,Campaign> campaigns,Map<UUID,Character> characters,Map<UUID,UUID> bindings) {this(campaigns,characters,bindings,Map.of());}
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final AtomicJsonFile file;
    private State state;
    private long viewGeneration;
    public long viewGeneration(){return viewGeneration;}
    private record ParsedSrd(Character source,dev.tacticalcombat.rules.srd.DerivedRulesState rules) {}
    // One immutable projection per stored character. Object identity covers build/runtime/manual writes.
    // Rules/catalog version is fixed by this process; future executable effects require a new snapshot contract.
    private final Map<UUID,ParsedSrd> parsedSrd=new HashMap<>();
    dev.tacticalcombat.rules.srd.SrdCharacter srdInputs(Character c) { return srdRules(c).inputs(); }
    dev.tacticalcombat.rules.srd.DerivedRulesState srdRules(Character c) {
        require(c.profile.equals(RulesProfiles.SRD),"SRD projection requires SRD profile");
        var cached=parsedSrd.get(c.id);
        if(cached!=null && cached.source==c) return cached.rules;
        var rules=dev.tacticalcombat.rules.srd.CharacterRules.read(c.sheet);
        if(state.characters.get(c.id)==c) parsedSrd.put(c.id,new ParsedSrd(c,rules));
        return rules;
    }
    public CharacterRepository(Path path) throws IOException {
        this(new AtomicJsonFile(path));
    }
    CharacterRepository(AtomicJsonFile storage) throws IOException {
        file=storage; state=file.read(CharacterRepository::decode,new State(Map.of(),Map.of(),Map.of()));
    }
    public State state() { return state; }
    public boolean recoveredFromBackup() { return file.recoveredFromBackup(); }
    public Campaign campaign(UUID id) { Campaign c=state.campaigns.get(id); if(c==null) throw new IllegalArgumentException("Unknown campaign"); return c; }
    public Character character(UUID id) { Character c=state.characters.get(id); if(c==null) throw new IllegalArgumentException("Unknown character"); return c; }
    public boolean manages(Principal p,UUID campaign) {
        Campaign c=campaign(campaign); return p.admin || c.owner.equals(p.id) || c.dms.contains(p.id);
    }
    public boolean member(Principal p,UUID id) { return manages(p,id) || campaign(id).members.contains(p.id); }
    public UUID createCampaign(Principal p,String name) throws IOException {
        require(p.admin,"Only server administrators create campaigns");
        require(name!=null && !name.isBlank() && name.length()<=64,"Campaign name must be 1–64 characters");
        require(state.campaigns.size()<64,"Campaign limit reached");
        UUID id=UUID.randomUUID(); var campaigns=new HashMap<>(state.campaigns);
        campaigns.put(id,new Campaign(id,name,p.id,Set.of(),Set.of(p.id),RulesProfiles.TACTICAL,1));
        commit(new State(campaigns,state.characters,state.bindings,state.assignments,state.manualRevisions)); return id;
    }
    public void invite(Principal p,UUID id,long revision,UUID player,boolean dm) throws IOException {
        Campaign c=campaign(id); require(c.revision==revision,"Stale campaign revision");
        require(dm ? p.admin || c.owner.equals(p.id) : manages(p,id),"Campaign permission denied");
        var dms=new HashSet<>(c.dms); var members=new HashSet<>(c.members);
        require(members.size()<256 || members.contains(player),"Campaign member limit");
        members.add(player); if(dm) dms.add(player);
        updateCampaign(new Campaign(id,c.name,c.owner,dms,members,c.profile,c.revision+1,c.policy));
    }
    public void revokeDm(Principal p,UUID id,long revision,UUID player) throws IOException {
        Campaign c=campaign(id); require(c.revision==revision,"Stale campaign revision");
        require(p.admin || c.owner.equals(p.id),"Only the owner/admin appoints or removes co-DMs");
        var dms=new HashSet<>(c.dms); dms.remove(player);
        updateCampaign(new Campaign(id,c.name,c.owner,dms,c.members,c.profile,c.revision+1,c.policy));
    }
    public void profile(Principal p,UUID id,long revision,String profile) throws IOException {
        Campaign c=campaign(id); require(c.revision==revision,"Stale campaign revision");
        require(manages(p,id),"Campaign permission denied"); RulesProfiles.get(profile);
        updateCampaign(new Campaign(id,c.name,c.owner,c.dms,c.members,profile,c.revision+1,c.policy));
    }
    public void policy(Principal p,UUID id,long revision,ApprovalPolicy policy) throws IOException {
        Campaign c=campaign(id); require(manages(p,id),"Campaign permission denied");
        require(c.revision==revision,"Stale campaign revision");
        updateCampaign(new Campaign(id,c.name,c.owner,c.dms,c.members,c.profile,revision+1,policy));
    }
    private void updateCampaign(Campaign c) throws IOException {
        var campaigns=new HashMap<>(state.campaigns); campaigns.put(c.id,c);
        commit(new State(campaigns,state.characters,state.bindings,state.assignments,state.manualRevisions));
    }
    public UUID submit(Principal p,UUID campaign,String profile,String sheet) throws IOException {
        require(dev.tacticalcombat.rules.srd.MonsterProfiles.identity(JsonParser.parseString(sheet).getAsJsonObject())==null,"Use authenticated monster preparation for NPC identity");
        require(member(p,campaign),"Join this campaign before submitting a character");
        require(state.characters.size()<1024,"Character limit reached");
        require(state.characters.values().stream().filter(c->c.owner.equals(p.id)).count()<32,"Owner character limit reached");
        RulesProfiles.get(profile); validateSheet(sheet);
        Campaign campaignRecord=campaign(campaign);
        if(!manages(p,campaign)) {
            require(!campaignRecord.policy.ownerLocked(),"Owner editing is locked");
            for(String field:CharacterEdits.fields(JsonParser.parseString(sheet).getAsJsonObject()).keySet())
                if(!field.equals("format")) require(campaignRecord.policy.permits(field),"Owner cannot edit "+field);
        }
        UUID id=UUID.randomUUID();
        var records=new HashMap<>(state.characters);
        // Submission is an independent, unapproved copy; it never imports ownership or permissions.
        var stats=new HashMap<>(Map.of("hp",20d,"hp_max",20d,"ac",10d,"movement_blocks",8d));
        JsonObject raw=JsonParser.parseString(sheet).getAsJsonObject();
        if(!manages(p,campaign)){var trackers=dev.tacticalcombat.uiapi.SheetSections.read(raw,"trackers");if(trackers.get("inspiration").getAsInt()>0){trackers.addProperty("inspiration",0);raw=dev.tacticalcombat.uiapi.SheetSections.update(raw,"trackers",trackers);sheet=raw.toString();}}
        if(raw.has("values") && raw.get("values").isJsonObject()) {
            JsonObject values=raw.getAsJsonObject("values");
            for(String key:List.of("hp","hp_max","ac","movement_blocks","initiative_bonus","str","dex","con","int","wis","cha","prof","level"))
                if(values.has(key)) stats.put(key,values.get(key).getAsDouble());
        }
        validateStats(stats);
        records.put(id,new Character(id,campaign,p.id,profile,1,sheet,stats,!campaignRecord.policy.requireNewApproval(),1));
        commit(new State(state.campaigns,records,state.bindings,state.assignments,state.manualRevisions)); return id;
    }
    public Character visible(Principal p,UUID id) {
        Character c=state.characters.get(id);
        require(c!=null && member(p,c.campaign) && (c.owner.equals(p.id) || manages(p,c.campaign) || controller(id).equals(p.id)),"Character permission denied"); return c;
    }
    /** One campaign-file transaction creates the explicit NPC, approval and binding. Operation ID is the persistent character ID. */
    public UUID prepareMonster(Principal p,UUID campaign,UUID operation,String source,UUID actor) throws IOException {
        require(manages(p,campaign)&&member(new Principal(p.id,false),campaign),"Only current campaign DMs prepare monsters");
        require(campaign(campaign).profile.equals(RulesProfiles.SRD),"Selected campaign requires the SRD profile");
        var sheet=dev.tacticalcombat.rules.srd.MonsterProfiles.sheet(source,operation,actor);
        if(state.characters.containsKey(operation)){
            var old=character(operation);var identity=dev.tacticalcombat.rules.srd.MonsterProfiles.identity(JsonParser.parseString(old.sheet).getAsJsonObject());
            require(old.campaign.equals(campaign)&&old.owner.equals(p.id)&&identity!=null&&identity.equals(dev.tacticalcombat.rules.srd.MonsterProfiles.identity(sheet)),"Preparation ID reused with different parameters");
            requireUnreserved(old.id);return old.id;
        }
        require(state.characters.size()<1024,"Character limit reached");require(!state.bindings.containsKey(actor),"Entity already bound");
        dev.tacticalcombat.rules.srd.SrdCharacterCodec.parseSheet(sheet.toString());
        var profile=dev.tacticalcombat.rules.srd.MonsterProfiles.get(source);
        var records=new HashMap<>(state.characters);records.put(operation,new Character(operation,campaign,p.id,RulesProfiles.SRD,1,sheet.toString(),Map.of("hp",(double)profile.hp(),"hp_max",(double)profile.hp(),"ac",(double)profile.ac(),"movement_blocks",profile.speed()/5d),true,1));
        var bindings=new HashMap<>(state.bindings);bindings.put(actor,operation);
        commit(new State(state.campaigns,records,bindings,state.assignments,state.manualRevisions));return operation;
    }
    public JsonObject effectiveSheet(Character c) {
        JsonObject sheet=JsonParser.parseString(c.sheet).getAsJsonObject();
        if(!sheet.has("values")) sheet.add("values",new JsonObject());
        c.stats.forEach((key,value)->sheet.getAsJsonObject("values").addProperty(key,value));
        return sheet;
    }
    public UUID binding(UUID character) {
        return state.bindings.entrySet().stream().filter(e->e.getValue().equals(character)).map(Map.Entry::getKey).findFirst().orElse(null);
    }
    /** Trusted server-only combat input lookup; never send this private snapshot to arbitrary clients.
     * The future encounter gateway must additionally validate ownership, turn and action identity.
     */
    public record SrdSnapshot(UUID character,UUID actor,UUID campaign,long revision,long runtimeRevision,
                              dev.tacticalcombat.rules.srd.DerivedRulesState rules) {
        public SrdSnapshot(UUID character,UUID actor,UUID campaign,long revision,long runtimeRevision,dev.tacticalcombat.rules.srd.SrdCharacter stats) {
            this(character,actor,campaign,revision,runtimeRevision,dev.tacticalcombat.rules.srd.CharacterRules.derive(stats));
        }
        public dev.tacticalcombat.rules.srd.SrdCharacter stats() { return rules.inputs(); }
    }
    public SrdSnapshot srdSnapshot(UUID actor,UUID expectedCampaign) {
        UUID id=state.bindings.get(actor);
        require(id!=null,"SRD combat requires an approved bound character");
        Character c=character(id);
        require(c.approved && c.profile.equals(RulesProfiles.SRD) && c.profileVersion==1,"Approved SRD 5.1 profile required");
        require(c.campaign.equals(expectedCampaign),"SRD character belongs to another campaign");
        require(member(new Principal(c.owner,false),c.campaign),"SRD character owner is no longer a campaign member");
        return new SrdSnapshot(c.id,actor,c.campaign,c.revision,c.runtimeRevision,srdRules(c));
    }
    public void edit(Principal p,UUID id,long revision,long runtimeRevision,JsonObject changes) throws IOException {
        edit(p,id,revision,runtimeRevision,changes,false);
    }
    public void editSections(Principal p,UUID id,long revision,long runtimeRevision,long manualRevision,JsonObject changes) throws IOException {
        edit(p,id,revision,runtimeRevision,changes,false,manualRevision);
    }
    private void edit(Principal p,UUID id,long revision,long runtimeRevision,JsonObject changes,boolean explicitStatCommand) throws IOException {
        edit(p,id,revision,runtimeRevision,changes,explicitStatCommand,null);
    }
    private void edit(Principal p,UUID id,long revision,long runtimeRevision,JsonObject changes,boolean explicitStatCommand,Long manualExpected) throws IOException {
        Character c=visible(p,id); require(c.owner.equals(p.id) || manages(p,c.campaign),"Only owner or campaign DM edits character data"); require(c.revision==revision,"Stale character revision; refresh and reconcile");
        requireUnreserved(id);
        require(binding(id)==null,"Unbind the character before editing its approved stats");
        if(changes.size()==0) return;
        if(changes.keySet().stream().anyMatch(k->Set.of("collections.ui_trackers","collections.ui_features","collections.ui_spellbook").contains(k)))
            require(manualExpected==null?manualRevision(id)==0:manualExpected==manualRevision(id),"Stale manual revision; refresh using the section editor");
        if(!manages(p,c.campaign)) for(String field:changes.keySet())
            require(campaign(c.campaign).policy.permits(field),"Owner editing locked or field forbidden: "+field);
        if(changes.has("values.hp")) require(c.runtimeRevision==runtimeRevision,"Stale health revision; refresh and reconcile HP");
        if(c.profile.equals(RulesProfiles.SRD) && changes.has("collections.srd_combat"))
            require(c.runtimeRevision==runtimeRevision,"Stale SRD health revision; refresh and reconcile before replacing combat data");
        JsonObject sheet=CharacterEdits.apply(effectiveSheet(c),changes); validateSheet(sheet.toString());
        require(Objects.equals(dev.tacticalcombat.rules.srd.MonsterProfiles.identity(effectiveSheet(c)),dev.tacticalcombat.rules.srd.MonsterProfiles.identity(sheet)),"Monster source identity cannot be edited or imported");
        dev.tacticalcombat.rules.srd.MonsterProfiles.validate(sheet);
        dev.tacticalcombat.uiapi.CreationRules.transition(effectiveSheet(c),sheet);
        int oldInspiration=dev.tacticalcombat.uiapi.SheetSections.read(effectiveSheet(c),"trackers").get("inspiration").getAsInt(),newInspiration=dev.tacticalcombat.uiapi.SheetSections.read(sheet,"trackers").get("inspiration").getAsInt();
        require(newInspiration<=oldInspiration||manages(p,c.campaign),"Only campaign DM may award Inspiration");
        if(c.profile.equals(RulesProfiles.SRD) && changes.has("collections.srd_combat"))
            dev.tacticalcombat.rules.srd.SrdCharacterCodec.parseSheet(sheet.toString());
        var stats=new HashMap<>(c.stats);
        for(var entry:changes.entrySet()) if(entry.getKey().startsWith("values.")) {
            String key=entry.getKey().substring(7);
            // Only explicit core/resource inputs are combat stats. Other manual pack numbers remain inert sheet data.
            require(!entry.getValue().isJsonNull() || !stats.containsKey(key),"Cannot remove authoritative stat "+key);
            if(!entry.getValue().isJsonNull() && (explicitStatCommand || stats.containsKey(key) || CORE.contains(key) || key.startsWith("resource_"))) {
                stats.put(key,entry.getValue().getAsDouble());
            }
        }
        validateStats(stats);
        boolean approved=c.approved && !campaign(c.campaign).policy.requireEditApproval();
        replaceManual(new Character(id,c.campaign,c.owner,c.profile,c.profileVersion,sheet.toString(),stats,approved,revision+1,c.runtimeRevision));event(c,dev.tacticalcombat.uiapi.UiEvents.Kind.RESOURCE,"Character record updated; approval policy applied");
        if(c.approved!=approved)event(c,dev.tacticalcombat.uiapi.UiEvents.Kind.APPROVAL,"Editing invalidated approval under campaign policy");
        if(changes.has("collections.ui_features") && !recordedConditions(effectiveSheet(c)).equals(recordedConditions(sheet)))event(c,dev.tacticalcombat.uiapi.UiEvents.Kind.CONDITION,"Recorded conditions updated; no automatic effects");
    }
    /** Independent CAS for inert counters. Generic sheet edits invalidate this CAS too. */
    public long manualRevision(UUID id) {character(id);return state.manualRevisions.getOrDefault(id,0L);}
    public boolean mayTrack(Principal p,UUID id) {
        var c=character(id);
        // Accepted controller replaces owner for play bookkeeping; managers may adjudicate.
        return member(p,c.campaign) && (manages(p,c.campaign) || controller(id).equals(p.id) && !campaign(c.campaign).policy.ownerLocked());
    }
    public boolean mayTrack(Principal p,UUID id,String section) {
        var c=character(id);return Set.of("trackers","features","spellbook").contains(section) && mayTrack(p,id)
            && (manages(p,c.campaign) || campaign(c.campaign).policy.permits("collections.ui_"+section));
    }
    public void track(Principal p,UUID id,long expected,dev.tacticalcombat.uiapi.RpgRecords.Change change) throws IOException {
        var c=visible(p,id);require(mayTrack(p,id),"Only current controller or campaign DM may update manual counters");
        require(manualRevision(id)==expected,"Stale manual revision; refresh and reconcile counters");
        require(change!=null,"Manual change required");
        if(change.action()==dev.tacticalcombat.uiapi.RpgRecords.Action.INSPIRATION)require(change.value()<=dev.tacticalcombat.uiapi.SheetSections.read(effectiveSheet(c),"trackers").get("inspiration").getAsInt()||manages(p,c.campaign),"Only campaign DM may award Inspiration");
        if(!manages(p,c.campaign)) require(campaign(c.campaign).policy.permits("collections.ui_"+change.section()),"Manual field forbidden by campaign policy");
        var sheet=JsonParser.parseString(c.sheet).getAsJsonObject();
        var data=dev.tacticalcombat.uiapi.RpgRecords.apply(sheet,change);
        var next=dev.tacticalcombat.uiapi.SheetSections.update(sheet,change.section(),data);
        validateSheet(next.toString());
        // Whitelisted RpgRecords operations touch ui_* counter values only. No revision used
        // by SrdSnapshot, approved inputs, ownership, assignment or binding changes here.
        replaceManual(new Character(c.id,c.campaign,c.owner,c.profile,c.profileVersion,next.toString(),c.stats,c.approved,c.revision,c.runtimeRevision));
        event(c,dev.tacticalcombat.uiapi.UiEvents.Kind.RESOURCE,"Manual "+change.action()+" recorded; no gameplay effect");
        if(change.action()==dev.tacticalcombat.uiapi.RpgRecords.Action.EXHAUSTION && !dev.tacticalcombat.uiapi.SheetSections.read(sheet,"trackers").get("exhaustion").equals(data.get("exhaustion")))
            event(c,dev.tacticalcombat.uiapi.UiEvents.Kind.CONDITION,"Manual exhaustion marker changed; no automatic effects");
    }
    private void replaceManual(Character c) throws IOException {
        var revisions=new HashMap<>(state.manualRevisions);long revision=manualRevision(c.id);
        require(revision<Long.MAX_VALUE,"Manual revision exhausted");revisions.put(c.id,revision+1);
        var records=new HashMap<>(state.characters);records.put(c.id,c);
        commit(new State(state.campaigns,records,state.bindings,state.assignments,revisions));
    }
    private static JsonArray recordedConditions(JsonObject sheet) {
        var out=new JsonArray();for(var e:dev.tacticalcombat.uiapi.SheetSections.read(sheet,"features").getAsJsonArray("entries"))
            if(Set.of("CONDITION","TEMPORARY").contains(e.getAsJsonObject().get("kind").getAsString()))out.add(e);
        return out;
    }
    private static final Set<String> CORE=Set.of("hp","hp_max","ac","movement_blocks","initiative_bonus","str","dex","con","int","wis","cha","prof","level");
    public void stat(Principal p,UUID id,long revision,String key,double value) throws IOException {
        Character c=visible(p,id);
        var changes=new JsonObject(); changes.addProperty("values."+key,value);
        // Validate arbitrary command-created stats, including resource fields, before entering the shared edit path.
        var stats=new HashMap<>(c.stats); stats.put(key,value); validateStats(stats);
        edit(p,id,revision,c.runtimeRevision,changes,true);
    }
    public void approve(Principal p,UUID id,long revision) throws IOException {
        Character c=character(id); require(manages(p,c.campaign),"Only campaign DMs approve characters");
        require(c.revision==revision,"Stale character revision");require(binding(id)==null,"Unbind before approving; active approvals cannot be rewritten");
        replace(new Character(id,c.campaign,c.owner,c.profile,c.profileVersion,c.sheet,c.stats,true,revision+1,c.runtimeRevision));
        event(c,dev.tacticalcombat.uiapi.UiEvents.Kind.APPROVAL,"Character approved");
    }
    public void bind(Principal p,UUID id,long revision,UUID entity,UUID playerOwner) throws IOException {
        Character c=character(id); require(manages(p,c.campaign),"Only campaign DMs bind characters");
        requireUnreserved(id);
        require(playerOwner==null||dev.tacticalcombat.rules.srd.MonsterProfiles.identity(JsonParser.parseString(c.sheet).getAsJsonObject())==null,"Monster records bind only to NPCs");
        require(c.revision==revision,"Stale character revision"); require(c.approved,"Character needs DM approval");
        require(member(new Principal(c.owner,false),c.campaign),"Owner campaign membership required");
        require(member(new Principal(controller(id),false),c.campaign),"Controller campaign membership required");
        require(playerOwner==null || controller(id).equals(playerOwner),"Cannot bind another player's character to this player");
        require(!state.bindings.containsKey(entity) && !state.bindings.containsValue(id),"Entity or character already bound");
        var bindings=new HashMap<>(state.bindings); bindings.put(entity,id); commit(new State(state.campaigns,state.characters,bindings,state.assignments,state.manualRevisions));
    }
    public void unbind(Principal p,UUID entity) throws IOException {
        UUID id=state.bindings.get(entity); require(id!=null,"Entity is not bound");
        require(manages(p,character(id).campaign),"Campaign permission denied");
        requireUnreserved(id);
        var bindings=new HashMap<>(state.bindings); bindings.remove(entity); commit(new State(state.campaigns,state.characters,bindings,state.assignments,state.manualRevisions));
    }
    /** Controller never changes ownership. All control changes require an unbound character. */
    public UUID controller(UUID id) {var a=state.assignments.get(id);return a!=null && a.controller!=null?a.controller:character(id).owner;}
    public Assignment assignment(UUID id) {return state.assignments.get(id);}
    public void offerControl(Principal p,UUID id,long revision,long assignmentRevision,UUID target) throws IOException {
        var c=character(id);require(manages(p,c.campaign),"Only campaign DMs assign characters");
        require(c.revision==revision,"Stale character revision");require(binding(id)==null,"Unbind before changing control; live takeover unavailable");
        require(member(new Principal(target,false),c.campaign),"Target campaign membership required");
        if(dev.tacticalcombat.rules.srd.MonsterProfiles.identity(JsonParser.parseString(c.sheet).getAsJsonObject())!=null) require(manages(new Principal(target,false),c.campaign),"Monster controllers must be campaign DMs");
        var old=state.assignments.get(id);require((old==null?0:old.revision)==assignmentRevision,"Stale assignment revision");
        require(assignmentRevision<Long.MAX_VALUE,"Assignment revision exhausted");
        saveAssignment(new Assignment(id,old==null?c.owner:old.controller,target,Decision.PENDING,assignmentRevision+1,history(old,p.id,target,Decision.PENDING)));
    }
    public void answerControl(Principal p,UUID id,long revision,boolean accept) throws IOException {
        var c=character(id);var old=state.assignments.get(id);
        require(old!=null && old.decision==Decision.PENDING && p.id.equals(old.offered) && member(p,c.campaign),"Pending assignment recipient permission required");
        require(old.revision==revision,"Stale assignment revision");require(binding(id)==null,"Unbind before changing control; live takeover unavailable");
        require(revision<Long.MAX_VALUE,"Assignment revision exhausted");
        if(accept && dev.tacticalcombat.rules.srd.MonsterProfiles.identity(JsonParser.parseString(c.sheet).getAsJsonObject())!=null)require(manages(new Principal(p.id,false),c.campaign),"Monster controller DM authority revoked");
        if(accept) require(state.characters.values().stream().noneMatch(other->!other.id.equals(id) && controller(other.id).equals(p.id) && (state.assignments.containsKey(other.id) && state.assignments.get(other.id).decision==Decision.ACCEPTED || binding(other.id)!=null)),"Player is already controlling another character");
        var decision=accept?Decision.ACCEPTED:Decision.DECLINED;
        saveAssignment(new Assignment(id,accept?p.id:old.controller,null,decision,revision+1,history(old,p.id,p.id,decision)));
    }
    public void clearControl(Principal p,UUID id,long revision) throws IOException {
        var c=character(id);require(manages(p,c.campaign),"Only campaign DMs restore control");
        var old=state.assignments.get(id);require(old!=null && old.revision==revision,"Stale assignment revision");require(binding(id)==null,"Unbind before changing control; live takeover unavailable");
        require(revision<Long.MAX_VALUE,"Assignment revision exhausted");
        saveAssignment(new Assignment(id,c.owner,null,Decision.CANCELLED,revision+1,history(old,p.id,c.owner,Decision.CANCELLED)));
    }
    private List<ControlChange> history(Assignment old,UUID who,UUID target,Decision decision) {
        var history=new ArrayList<ControlChange>(old==null?List.of():old.history);if(history.size()>=16) history.removeFirst();history.add(new ControlChange(who,target,decision));return history;
    }
    private void saveAssignment(Assignment a) throws IOException {
        var assignments=new HashMap<>(state.assignments);assignments.put(a.character,a);commit(new State(state.campaigns,state.characters,state.bindings,assignments,state.manualRevisions));
        var c=character(a.character);dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(UUID.randomUUID(),c.campaign,null,0,dev.tacticalcombat.uiapi.UiEvents.Kind.ASSIGNMENT,List.of(c.id),"Character control: "+a.decision,null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,
            a.offered==null?Set.copyOf(List.of(c.owner,a.controller)):Set.copyOf(List.of(c.owner,a.controller,a.offered)));
    }
    public void revokeApproval(Principal p,UUID id,long revision) throws IOException {
        var c=character(id);require(manages(p,c.campaign),"Only campaign DMs revoke approval");require(c.revision==revision,"Stale character revision");require(binding(id)==null,"Unbind before revoking approval");
        replace(new Character(id,c.campaign,c.owner,c.profile,c.profileVersion,c.sheet,c.stats,false,revision+1,c.runtimeRevision));event(c,dev.tacticalcombat.uiapi.UiEvents.Kind.APPROVAL,"Approval revoked; changes requested");
    }
    public void removeMember(Principal p,UUID campaign,long revision,UUID player) throws IOException {
        var c=campaign(campaign);require(manages(p,campaign),"Campaign permission denied");require(c.revision==revision,"Stale campaign revision");
        require(!c.owner.equals(player),"Cannot remove campaign owner");require(!c.dms.contains(player) || p.admin || c.owner.equals(p.id),"Only owner/admin removes co-DMs");
        require(state.characters.values().stream().noneMatch(ch->ch.campaign.equals(campaign) && (ch.owner.equals(player) || controller(ch.id).equals(player)) && binding(ch.id)!=null),"Release member bindings before removal");
        var members=new HashSet<>(c.members);members.remove(player);var dms=new HashSet<>(c.dms);dms.remove(player);
        updateCampaign(new Campaign(c.id,c.name,c.owner,dms,members,c.profile,c.revision+1,c.policy));
    }
    /** Server-only capture of vanilla health. Client drafts cannot call this through the network. */
    public void captureHealth(Map<UUID,Double> health) throws IOException {
        var records=new HashMap<>(state.characters); boolean changed=false;
        for(var entry:health.entrySet()) {
            UUID id=state.bindings.get(entry.getKey()); if(id==null) continue;
            Character c=records.get(id); if(!c.profile.equals(RulesProfiles.TACTICAL)) continue;
            double hp=Math.clamp(entry.getValue(),0,c.stats.get("hp_max"));
            if(c.stats.get("hp")==hp) continue;
            var stats=new HashMap<>(c.stats); stats.put("hp",hp);
            records.put(id,new Character(id,c.campaign,c.owner,c.profile,c.profileVersion,c.sheet,stats,c.approved,c.revision,c.runtimeRevision+1)); changed=true;
        }
        if(changed) commit(new State(state.campaigns,records,state.bindings,state.assignments,state.manualRevisions));
    }
    /** Trusted accepted-effect persistence seam. No packet/command exposes this API.
     * The caller must retain its action identity and expected snapshot across retries: this is CAS,
     * not permission to reroll/retry a consumed action against a fresh revision.
     */
    public SrdSnapshot commitSrdHealth(SrdSnapshot expected,dev.tacticalcombat.rules.srd.SrdHealth health) throws IOException {
        requireUnreserved(expected.character);
        SrdSnapshot current=srdSnapshot(expected.actor,expected.campaign);
        require(current.equals(expected),"Stale SRD runtime snapshot; effect not committed");
        require(health.maximum()==current.stats().maximumHp(),"Runtime effects cannot change approved maximum HP");
        Character c=character(current.character);
        JsonObject sheet=JsonParser.parseString(c.sheet).getAsJsonObject();
        JsonObject data=dev.tacticalcombat.rules.srd.SrdAuthoring.data(sheet);
        data.addProperty("hp",health.current()); data.addProperty("temporary_hp",health.temporary());
        dev.tacticalcombat.rules.srd.SrdCharacterCodec.parseSheet(sheet.toString());
        require(c.runtimeRevision<Long.MAX_VALUE,"SRD runtime revision exhausted");
        // Even a no-op effect advances CAS revision; retrying the same expected state cannot apply twice.
        replace(new Character(c.id,c.campaign,c.owner,c.profile,c.profileVersion,sheet.toString(),c.stats,c.approved,c.revision,c.runtimeRevision+1));
        return srdSnapshot(current.actor,current.campaign);
    }
    private void event(Character c,dev.tacticalcombat.uiapi.UiEvents.Kind kind,String message) {
        dev.tacticalcombat.uiapi.UiEvents.SESSION.safePublish(UUID.randomUUID(),c.campaign,null,0,kind,List.of(c.id),message,null,dev.tacticalcombat.uiapi.UiEvents.Visibility.RECIPIENTS_AND_DM,Set.copyOf(List.of(c.owner,controller(c.id))));
    }
    private void replace(Character c) throws IOException {
        var records=new HashMap<>(state.characters); records.put(c.id,c); commit(new State(state.campaigns,records,state.bindings,state.assignments,state.manualRevisions));
    }
    /** Single atomic store replacement for one accepted weapon action: attacker stock AND target HP.
     * No gameplay endpoint may supply health/stock directly; the encounter retains both expected snapshots.
     */
    public List<SrdSnapshot> commitSrdAction(SrdSnapshot attacker,SrdSnapshot target,dev.tacticalcombat.rules.srd.SrdHealth health,
            dev.tacticalcombat.rules.srd.SrdEquipment.Weapon weapon,dev.tacticalcombat.rules.srd.SrdRules.AttackMode mode) throws IOException {
        requireUnreserved(attacker.character);requireUnreserved(target.character);
        require(!attacker.actor.equals(target.actor) && attacker.campaign.equals(target.campaign),"Invalid action participants");
        require(srdSnapshot(attacker.actor,attacker.campaign).equals(attacker) && srdSnapshot(target.actor,target.campaign).equals(target),"Stale SRD action snapshot");
        require(health.maximum()==target.stats().maximumHp(),"Runtime effects cannot change maximum HP");
        String stock=null; int remaining=0;
        if(mode==dev.tacticalcombat.rules.srd.SrdRules.AttackMode.THROWN) {
            require(weapon==dev.tacticalcombat.rules.srd.SrdEquipment.Weapon.DAGGER,"Unsupported thrown stock"); stock="daggers"; remaining=attacker.stats().daggers()-1;
        } else if(mode==dev.tacticalcombat.rules.srd.SrdRules.AttackMode.RANGED) {
            require(weapon.ranged,"Invalid ranged weapon");
            stock=weapon==dev.tacticalcombat.rules.srd.SrdEquipment.Weapon.LIGHT_CROSSBOW?"bolts":"arrows";
            remaining=(stock.equals("bolts")?attacker.stats().bolts():attacker.stats().arrows())-1;
        }
        require(stock==null || remaining>=0,"No approved ammunition remains");
        var records=new HashMap<>(state.characters);
        for(var expected:List.of(attacker,target)) {
            var c=character(expected.character); var sheet=JsonParser.parseString(c.sheet).getAsJsonObject();
            var data=dev.tacticalcombat.rules.srd.SrdAuthoring.data(sheet);
            if(expected==target) { data.addProperty("hp",health.current()); data.addProperty("temporary_hp",health.temporary()); }
            else if(stock!=null) data.addProperty(stock,remaining);
            dev.tacticalcombat.rules.srd.SrdCharacterCodec.parseSheet(sheet.toString());
            require(c.runtimeRevision<Long.MAX_VALUE,"Runtime revision exhausted");
            records.put(c.id,new Character(c.id,c.campaign,c.owner,c.profile,c.profileVersion,sheet.toString(),c.stats,c.approved,c.revision,c.runtimeRevision+1));
        }
        commit(new State(state.campaigns,records,state.bindings,state.assignments,state.manualRevisions));
        return List.of(srdSnapshot(attacker.actor,attacker.campaign),srdSnapshot(target.actor,target.campaign));
    }
    private void commit(State next) throws IOException {commit(next,state.receipts);}
    private void commit(State next,Map<UUID,GameplayReceipt> receipts) throws IOException {
        next=new State(next.campaigns,next.characters,next.bindings,next.assignments,next.manualRevisions,receipts);
        State published=next;
        String json=encode(next); file.write(json,CharacterRepository::decode); state=next; viewGeneration++;
        parsedSrd.entrySet().removeIf(e->published.characters.get(e.getKey())!=e.getValue().source);
    }
    public static final int MAX_GAMEPLAY_RECEIPTS=1024,MAX_PREPARED_ACTIONS=64;
    public GameplayReceipt gameplayReceipt(UUID operation){return state.receipts.get(operation);}
    public boolean reserved(UUID character){return state.receipts.values().stream().anyMatch(r->r.status()==GameplayReceipt.Status.PREPARED&&r.plan().participants().stream().anyMatch(p->p.character().equals(character)));}
    public void requireUnreserved(UUID character){require(!reserved(character),"Unresolved prepared gameplay operation; DM review required");}
    public GameplayReceipt visibleReceipt(Principal who,UUID operation){
        var receipt=state.receipts.get(operation);require(receipt!=null,"Gameplay receipt unavailable");var plan=receipt.plan();
        require(member(who,plan.campaign())&&(manages(who,plan.campaign())||who.id.equals(plan.requester())&&controller(plan.attacker().character()).equals(who.id)),"Gameplay result permission denied");
        return receipt;
    }
    private void authorizePlan(Principal who,GameplayActionPlan plan){
        if(dev.tacticalcombat.rules.srd.MonsterProfiles.identity(JsonParser.parseString(character(plan.attacker().character()).sheet).getAsJsonObject())!=null)require(manages(who,plan.campaign()),"Current DM authority required for monster actions");
        require(member(who,plan.campaign())&&(manages(who,plan.campaign())||who.id.equals(plan.requester())&&controller(plan.attacker().character()).equals(who.id)),"Gameplay action permission denied");
        for(var p:plan.participants()){
            var c=character(p.character());require(c.campaign.equals(plan.campaign())&&c.approved&&c.profile.equals(RulesProfiles.SRD)&&c.profileVersion==1&&member(new Principal(c.owner,false),plan.campaign()),"Approved SRD participant required");
            require(Objects.equals(state.bindings.get(p.actor()),p.character())&&controller(p.character()).equals(p.controller()),"Participant binding or controller changed");
            require(c.revision==p.revision()&&c.runtimeRevision==p.runtimeRevision()&&srdInputs(c).equals(p.before()),"Stale gameplay participant snapshot");
        }
    }
    public void requireGameplayCapacity(){require(state.receipts.size()<MAX_GAMEPLAY_RECEIPTS,"Gameplay receipt history full; no safe eviction is supported");require(state.receipts.values().stream().filter(r->r.status()==GameplayReceipt.Status.PREPARED).count()<MAX_PREPARED_ACTIONS,"Prepared action capacity reached");}
    public GameplayReceipt prepareGameplay(Principal who,GameplayActionPlan plan)throws IOException{
        var existing=state.receipts.get(plan.operation());if(existing!=null){visibleReceipt(who,plan.operation());require(existing.plan().equals(plan),"Operation identity reused with altered parameters");return existing;}
        authorizePlan(who,plan);require(state.receipts.size()<MAX_GAMEPLAY_RECEIPTS,"Gameplay receipt history full; no safe eviction is supported");
        require(state.receipts.values().stream().filter(r->r.status()==GameplayReceipt.Status.PREPARED).count()<MAX_PREPARED_ACTIONS,"Prepared action capacity reached");
        for(var p:plan.participants())requireUnreserved(p.character());
        var receipt=GameplayReceipt.prepared(plan);var receipts=new HashMap<>(state.receipts);receipts.put(plan.operation(),receipt);commit(state,receipts);return receipt;
    }
    public GameplayReceipt commitGameplay(Principal who,UUID operation,String fingerprint)throws IOException{
        var receipt=visibleReceipt(who,operation);require(receipt.plan().fingerprint().equals(fingerprint),"Operation fingerprint mismatch");
        if(receipt.status()==GameplayReceipt.Status.COMMITTED)return receipt;
        require(receipt.status()==GameplayReceipt.Status.PREPARED,"Cancelled operation cannot execute");authorizePlan(who,receipt.plan());
        var records=new HashMap<>(state.characters);
        for(var p:receipt.plan().participants()){
            var c=character(p.character());var sheet=JsonParser.parseString(c.sheet).getAsJsonObject();var data=dev.tacticalcombat.rules.srd.SrdAuthoring.data(sheet);var after=p.after();
            data.addProperty("hp",after.hp());data.addProperty("temporary_hp",after.temporaryHp());
            // Preserve optional legacy stock fields unless this operation changes that quantity.
            if(after.arrows()!=p.before().arrows())data.addProperty("arrows",after.arrows());
            if(after.bolts()!=p.before().bolts())data.addProperty("bolts",after.bolts());
            if(after.daggers()!=p.before().daggers())data.addProperty("daggers",after.daggers());
            dev.tacticalcombat.rules.srd.SrdCharacterCodec.parseSheet(sheet.toString());
            records.put(c.id,new Character(c.id,c.campaign,c.owner,c.profile,c.profileVersion,sheet.toString(),c.stats,c.approved,c.revision,c.runtimeRevision+1));
        }
        var committed=receipt.committed();var receipts=new HashMap<>(state.receipts);receipts.put(operation,committed);
        commit(new State(state.campaigns,records,state.bindings,state.assignments,state.manualRevisions),receipts);return committed;
    }
    public GameplayReceipt cancelGameplay(Principal who,UUID operation,String reason)throws IOException{
        var receipt=visibleReceipt(who,operation);require(manages(who,receipt.plan().campaign()),"Only campaign managers resolve prepared operations");
        require(receipt.status()==GameplayReceipt.Status.PREPARED,"Operation is already terminal");
        var cancelled=receipt.cancelled(who.id,reason);var receipts=new HashMap<>(state.receipts);receipts.put(operation,cancelled);commit(state,receipts);return cancelled;
    }
    /** Strict transaction metadata prevents a newer writer's fields from being silently discarded. */
    private static void strictTransactionFields(JsonElement value,Class<?> type){
        if(type.isEnum()&&!value.isJsonNull()&&java.util.Arrays.stream(type.getEnumConstants()).noneMatch(e->((Enum<?>)e).name().equals(value.getAsString())))throw new UnsupportedOperationException("Unknown gameplay enum value");
        if(type.isRecord()){
            if(type==GameplayReceipt.class||type==GameplayActionPlan.class){
                var object=value.getAsJsonObject();if(!object.has("version")||object.get("version").getAsBigDecimal().intValueExact()!=1)throw new UnsupportedOperationException("Unknown gameplay metadata version");
                if(type==GameplayReceipt.class&&(!object.has("status")||java.util.Arrays.stream(GameplayReceipt.Status.values()).noneMatch(s->s.name().equals(object.get("status").getAsString()))))throw new UnsupportedOperationException("Unknown gameplay receipt status");
                if(type==GameplayActionPlan.class)require(object.has("fingerprint")&&!object.get("fingerprint").isJsonNull()&&object.get("fingerprint").getAsString().matches("[0-9a-f]{64}"),"Missing gameplay fingerprint");
            }
            if(!value.isJsonObject())throw new IllegalArgumentException("Invalid transaction object");var expected=new HashSet<String>();for(var c:type.getRecordComponents())expected.add(c.getName());
            if(!expected.equals(value.getAsJsonObject().keySet()))throw new UnsupportedOperationException("Unknown or missing transaction fields");
            for(var c:type.getRecordComponents()){var child=value.getAsJsonObject().get(c.getName());if(!child.isJsonNull()&&(c.getType().isRecord()||c.getType().isEnum()))strictTransactionFields(child,c.getType());
                if(c.getGenericType() instanceof java.lang.reflect.ParameterizedType generic&&generic.getActualTypeArguments()[0] instanceof Class<?> element&&element.isEnum()){
                    if(java.util.Set.class.isAssignableFrom(c.getType()))for(var entry:child.getAsJsonArray())strictTransactionFields(entry,element);
                    else if(java.util.Map.class.isAssignableFrom(c.getType()))for(var key:child.getAsJsonObject().keySet())strictTransactionFields(new JsonPrimitive(key),element);
                }}
        }
    }
    public static void validateStats(Map<String,Double> stats) {
        require(stats.size()<=64,"Too many stats");
        for(var e:stats.entrySet()) {
            String key=e.getKey(); double value=e.getValue();
            require(key.matches("[a-z][a-z0-9_]{0,31}") && Double.isFinite(value),"Invalid stat");
            double max=switch(key) { case "hp","hp_max" -> 1024; case "movement_blocks" -> 32; case "ac" -> 100; default -> 1000; };
            double min=key.equals("initiative_bonus")?-100:0;
            require(value>=min && value<=max,"Stat outside supported range: "+key);
        }
        require(stats.getOrDefault("hp_max",0d)>=1 && stats.getOrDefault("hp",-1d)>=0
                && stats.get("hp")<=stats.get("hp_max"),"Require 0 <= hp <= hp_max and hp_max >= 1");
    }
    public static void validateSheet(String text) {
        require(text!=null && text.length()<=32768,"Sheet exceeds 32,768 characters");
        // Parse bounded JSON without invoking the sheet formula engine. Preserve manual pack data verbatim.
        checkDepth(text,20);
        JsonObject o=JsonParser.parseString(text).getAsJsonObject();
        require(o.has("format") && o.get("format").getAsString().matches("[a-z0-9_:-]{1,64}"),"Invalid sheet format");
        for(String group:List.of("text","values","collections")) if(o.has(group)) {
            require(o.get(group).isJsonObject(),"Invalid sheet group "+group);
            for(var e:o.getAsJsonObject(group).entrySet()) {
                require(e.getKey().matches("[a-z0-9_:-]{1,64}"),"Invalid sheet field name");
                if(group.equals("values")) require(e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber()
                    && Double.isFinite(e.getValue().getAsDouble()),"Raw values must be finite numbers");
                if(group.equals("text")) require(e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString(),"Text must be a string");
                if(group.equals("collections")) require(e.getValue().isJsonArray(),"Collection must be an array");
            }
        }
        dev.tacticalcombat.uiapi.SheetSections.validateExtensions(o);
        for(var e:o.entrySet()) require(Set.of("format","kind","theme","ui","text","values","collections").contains(e.getKey()),"Unknown sheet field: "+e.getKey());
    }
    public static void checkDepth(String text,int limit) {
        int depth=0; boolean quoted=false,escaped=false;
        for(char c:text.toCharArray()) {
            if(quoted) { if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c=='"') quoted=false; }
            else if(c=='"') quoted=true;
            else if(c=='{' || c=='[') require(++depth<=limit,"JSON nesting limit");
            else if(c=='}' || c==']') depth--;
        }
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalArgumentException(message); }
    private static String encode(State s) {
        JsonObject root=new JsonObject(); root.addProperty("schema",5);root.add("receipts",new GsonBuilder().serializeNulls().create().toJsonTree(new ArrayList<>(s.receipts.values())));
        root.add("campaigns",GSON.toJsonTree(new ArrayList<>(s.campaigns.values()))); root.add("characters",GSON.toJsonTree(new ArrayList<>(s.characters.values())));
        root.add("manualRevisions",GSON.toJsonTree(s.manualRevisions)); root.add("assignments",GSON.toJsonTree(new ArrayList<>(s.assignments.values()))); root.add("bindings",GSON.toJsonTree(s.bindings)); return new GsonBuilder().setPrettyPrinting().serializeNulls().create().toJson(root);
    }
    private static State decode(String json) {
        checkDepth(json,24);
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        int schema=root.get("schema").getAsBigDecimal().intValueExact();
        if(schema!=1 && schema!=2 && schema!=3 && schema!=4 && schema!=5) throw new UnsupportedOperationException("Unsupported save schema");
        Set<String> rootFields=new HashSet<>(Set.of("schema","campaigns","characters","bindings"));if(schema>=3)rootFields.add("assignments");if(schema>=4)rootFields.add("manualRevisions");if(schema>=5)rootFields.add("receipts");
        if(!rootFields.containsAll(root.keySet()))throw new UnsupportedOperationException("Unknown campaign store fields; refusing metadata loss");
        var campaigns=new HashMap<UUID,Campaign>(); var characters=new HashMap<UUID,Character>(); var bindings=new HashMap<UUID,UUID>();
        require(root.getAsJsonArray("campaigns").size()<=64 && root.getAsJsonArray("characters").size()<=1024,"Save limits exceeded");
        for(JsonElement e:root.getAsJsonArray("campaigns")) {
            if(schema==1) e.getAsJsonObject().add("policy",GSON.toJsonTree(ApprovalPolicy.secureDefault()));
            ApprovalPolicy.parse(e.getAsJsonObject().getAsJsonObject("policy"));
            Campaign c=GSON.fromJson(e,Campaign.class); RulesProfiles.get(c.profile);
            require(c.id!=null && c.owner!=null && c.revision>0 && c.name.length()<=64 && c.members.size()<=256 && c.members.contains(c.owner)
                    && c.members.containsAll(c.dms),"Invalid campaign");
            require(campaigns.put(c.id,c)==null,"Duplicate campaign");
        }
        for(JsonElement e:root.getAsJsonArray("characters")) {
            Character c=GSON.fromJson(e,Character.class); RulesProfiles.get(c.profile);
            require(c.id!=null && c.owner!=null && campaigns.containsKey(c.campaign) && c.profileVersion==1 && c.revision>0 && c.runtimeRevision>=0,"Invalid character");
            validateSheet(c.sheet); validateStats(c.stats); require(characters.put(c.id,c)==null,"Duplicate character");
        }
        var used=new HashSet<UUID>();
        for(var e:root.getAsJsonObject("bindings").entrySet()) {
            UUID actor=UUID.fromString(e.getKey()),id=UUID.fromString(e.getValue().getAsString());
            require(characters.containsKey(id) && characters.get(id).approved && used.add(id),"Invalid or duplicate binding"); bindings.put(actor,id);
        }
        var assignments=new HashMap<UUID,Assignment>();
        if(schema>=3) {
            require(root.getAsJsonArray("assignments").size()<=1024,"Assignment limit");
            for(var row:root.getAsJsonArray("assignments")) {
                var a=GSON.fromJson(row,Assignment.class);
                require(characters.containsKey(a.character) && a.controller!=null && a.decision!=null && a.revision>0 && a.history.size()<=16,"Invalid assignment");
                require((a.decision==Decision.PENDING)==(a.offered!=null),"Invalid pending assignment");
                for(var change:a.history) require(change.manager!=null && change.target!=null && change.decision!=null,"Invalid control history");
                require(assignments.put(a.character,a)==null,"Duplicate assignment");
            }
        }
        var manualRevisions=new HashMap<UUID,Long>();
        if(schema>=4) {
            require(root.getAsJsonObject("manualRevisions").size()<=1024,"Manual revision limit");
            for(var e:root.getAsJsonObject("manualRevisions").entrySet()) {
                UUID id=UUID.fromString(e.getKey());String value=e.getValue().getAsString();
                require(characters.containsKey(id) && e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isNumber() && value.matches("[0-9]{1,19}"),"Invalid manual revision");
                long revision=Long.parseLong(value);require(revision>=0,"Invalid manual revision");manualRevisions.put(id,revision);
            }
        }
        var receipts=new HashMap<UUID,GameplayReceipt>();
        if(schema>=5){
            require(root.has("receipts")&&root.getAsJsonArray("receipts").size()<=MAX_GAMEPLAY_RECEIPTS,"Invalid receipt collection");
            for(var row:root.getAsJsonArray("receipts")){
                strictTransactionFields(row,GameplayReceipt.class);var receipt=GSON.fromJson(row,GameplayReceipt.class);
                require(campaigns.containsKey(receipt.plan().campaign()),"Receipt campaign missing");
                require(receipts.put(receipt.plan().operation(),receipt)==null,"Duplicate gameplay receipt");
            }
            require(receipts.values().stream().filter(r->r.status()==GameplayReceipt.Status.PREPARED).count()<=MAX_PREPARED_ACTIONS,"Too many prepared operations");
            var reserved=new HashSet<UUID>();for(var receipt:receipts.values())if(receipt.status()==GameplayReceipt.Status.PREPARED)for(var participant:receipt.plan().participants())require(characters.containsKey(participant.character())&&reserved.add(participant.character()),"Conflicting prepared reservations");
        }
        return new State(campaigns,characters,bindings,assignments,manualRevisions,receipts);
    }
}
