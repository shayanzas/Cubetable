package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.rules.RulesProfiles;
import java.nio.file.Path;
import java.io.IOException;
import java.util.*;

/** Immutable snapshots, server-authenticated principals and revision-checked durable mutations. */
public final class CharacterRepository {
    public record Principal(UUID id, boolean admin) {}
    public record Campaign(UUID id,String name,UUID owner,Set<UUID> dms,Set<UUID> members,String profile,long revision) {
        public Campaign { dms=Set.copyOf(dms); members=Set.copyOf(members); }
    }
    public record Character(UUID id,UUID campaign,UUID owner,String profile,int profileVersion,
                            String sheet,Map<String,Double> stats,boolean approved,long revision) {
        public Character { stats=Map.copyOf(stats); }
    }
    public record State(Map<UUID,Campaign> campaigns,Map<UUID,Character> characters,Map<UUID,UUID> bindings) {
        public State { campaigns=Map.copyOf(campaigns); characters=Map.copyOf(characters); bindings=Map.copyOf(bindings); }
    }
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private final AtomicJsonFile file;
    private State state;
    public CharacterRepository(Path path) throws IOException {
        file=new AtomicJsonFile(path); state=file.read(CharacterRepository::decode,new State(Map.of(),Map.of(),Map.of()));
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
        commit(new State(campaigns,state.characters,state.bindings)); return id;
    }
    public void invite(Principal p,UUID id,long revision,UUID player,boolean dm) throws IOException {
        Campaign c=campaign(id); require(c.revision==revision,"Stale campaign revision");
        require(dm ? p.admin || c.owner.equals(p.id) : manages(p,id),"Campaign permission denied");
        var dms=new HashSet<>(c.dms); var members=new HashSet<>(c.members);
        require(members.size()<256 || members.contains(player),"Campaign member limit");
        members.add(player); if(dm) dms.add(player);
        updateCampaign(new Campaign(id,c.name,c.owner,dms,members,c.profile,c.revision+1));
    }
    public void revokeDm(Principal p,UUID id,long revision,UUID player) throws IOException {
        Campaign c=campaign(id); require(c.revision==revision,"Stale campaign revision");
        require(p.admin || c.owner.equals(p.id),"Only the owner/admin appoints or removes co-DMs");
        var dms=new HashSet<>(c.dms); dms.remove(player);
        updateCampaign(new Campaign(id,c.name,c.owner,dms,c.members,c.profile,c.revision+1));
    }
    public void profile(Principal p,UUID id,long revision,String profile) throws IOException {
        Campaign c=campaign(id); require(c.revision==revision,"Stale campaign revision");
        require(manages(p,id),"Campaign permission denied"); RulesProfiles.get(profile);
        updateCampaign(new Campaign(id,c.name,c.owner,c.dms,c.members,profile,c.revision+1));
    }
    private void updateCampaign(Campaign c) throws IOException {
        var campaigns=new HashMap<>(state.campaigns); campaigns.put(c.id,c);
        commit(new State(campaigns,state.characters,state.bindings));
    }
    public UUID submit(Principal p,UUID campaign,String profile,String sheet) throws IOException {
        require(member(p,campaign),"Join this campaign before submitting a character");
        require(state.characters.size()<1024,"Character limit reached");
        require(state.characters.values().stream().filter(c->c.owner.equals(p.id)).count()<32,"Owner character limit reached");
        RulesProfiles.get(profile); validateSheet(sheet);
        UUID id=UUID.randomUUID();
        var records=new HashMap<>(state.characters);
        // Submission is an independent, unapproved copy; it never imports ownership or permissions.
        var stats=new HashMap<>(Map.of("hp",20d,"hp_max",20d,"ac",10d,"movement_blocks",8d));
        JsonObject raw=JsonParser.parseString(sheet).getAsJsonObject();
        if(raw.has("values") && raw.get("values").isJsonObject()) {
            JsonObject values=raw.getAsJsonObject("values");
            for(String key:List.of("hp","hp_max","ac","movement_blocks","initiative_bonus","str","dex","con","int","wis","cha","prof","level"))
                if(values.has(key)) stats.put(key,values.get(key).getAsDouble());
        }
        validateStats(stats);
        records.put(id,new Character(id,campaign,p.id,profile,1,sheet,stats,false,1));
        commit(new State(state.campaigns,records,state.bindings)); return id;
    }
    public Character visible(Principal p,UUID id) {
        Character c=character(id); require(c.owner.equals(p.id) || manages(p,c.campaign),"Character permission denied"); return c;
    }
    public void stat(Principal p,UUID id,long revision,String key,double value) throws IOException {
        Character c=visible(p,id); require(c.revision==revision,"Stale character revision");
        require(!state.bindings.containsValue(id),"Unbind the character before editing its approved stats");
        var stats=new HashMap<>(c.stats); stats.put(key,value); validateStats(stats);
        replace(new Character(id,c.campaign,c.owner,c.profile,c.profileVersion,c.sheet,stats,false,revision+1));
    }
    public void approve(Principal p,UUID id,long revision) throws IOException {
        Character c=character(id); require(manages(p,c.campaign),"Only campaign DMs approve characters");
        require(c.revision==revision,"Stale character revision");
        replace(new Character(id,c.campaign,c.owner,c.profile,c.profileVersion,c.sheet,c.stats,true,revision+1));
    }
    public void bind(Principal p,UUID id,long revision,UUID entity,UUID playerOwner) throws IOException {
        Character c=character(id); require(manages(p,c.campaign),"Only campaign DMs bind characters");
        require(c.revision==revision,"Stale character revision"); require(c.approved,"Character needs DM approval");
        require(playerOwner==null || c.owner.equals(playerOwner),"Cannot bind another player's character to this player");
        require(!state.bindings.containsKey(entity) && !state.bindings.containsValue(id),"Entity or character already bound");
        var bindings=new HashMap<>(state.bindings); bindings.put(entity,id); commit(new State(state.campaigns,state.characters,bindings));
    }
    public void unbind(Principal p,UUID entity) throws IOException {
        UUID id=state.bindings.get(entity); require(id!=null,"Entity is not bound");
        require(manages(p,character(id).campaign),"Campaign permission denied");
        var bindings=new HashMap<>(state.bindings); bindings.remove(entity); commit(new State(state.campaigns,state.characters,bindings));
    }
    /** Server-only capture of vanilla health. Client drafts cannot call this through the network. */
    public void captureHealth(Map<UUID,Double> health) throws IOException {
        var records=new HashMap<>(state.characters); boolean changed=false;
        for(var entry:health.entrySet()) {
            UUID id=state.bindings.get(entry.getKey()); if(id==null) continue;
            Character c=records.get(id); double hp=Math.clamp(entry.getValue(),0,c.stats.get("hp_max"));
            if(c.stats.get("hp")==hp) continue;
            var stats=new HashMap<>(c.stats); stats.put("hp",hp);
            records.put(id,new Character(id,c.campaign,c.owner,c.profile,c.profileVersion,c.sheet,stats,c.approved,c.revision+1)); changed=true;
        }
        if(changed) commit(new State(state.campaigns,records,state.bindings));
    }
    private void replace(Character c) throws IOException {
        var records=new HashMap<>(state.characters); records.put(c.id,c); commit(new State(state.campaigns,records,state.bindings));
    }
    private void commit(State next) throws IOException { String json=encode(next); file.write(json,CharacterRepository::decode); state=next; }
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
        int depth=0; boolean quoted=false,escaped=false;
        for(char c:text.toCharArray()) {
            if(quoted) { if(escaped) escaped=false; else if(c=='\\') escaped=true; else if(c=='"') quoted=false; }
            else if(c=='"') quoted=true;
            else if(c=='{' || c=='[') require(++depth<=20,"Sheet nesting limit");
            else if(c=='}' || c==']') depth--;
        }
        JsonObject o=JsonParser.parseString(text).getAsJsonObject();
        require(o.has("format") && o.get("format").getAsString().matches("[a-z0-9_:-]{1,64}"),"Invalid sheet format");
        for(var e:o.entrySet()) require(Set.of("format","kind","theme","ui","text","values","collections").contains(e.getKey()),"Unknown sheet field: "+e.getKey());
    }
    private static void require(boolean value,String message) { if(!value) throw new IllegalArgumentException(message); }
    private static String encode(State s) {
        JsonObject root=new JsonObject(); root.addProperty("schema",1);
        root.add("campaigns",GSON.toJsonTree(new ArrayList<>(s.campaigns.values()))); root.add("characters",GSON.toJsonTree(new ArrayList<>(s.characters.values())));
        root.add("bindings",GSON.toJsonTree(s.bindings)); return GSON.toJson(root);
    }
    private static State decode(String json) {
        JsonObject root=JsonParser.parseString(json).getAsJsonObject();
        if(root.get("schema").getAsInt()!=1) throw new UnsupportedOperationException("Unsupported save schema");
        var campaigns=new HashMap<UUID,Campaign>(); var characters=new HashMap<UUID,Character>(); var bindings=new HashMap<UUID,UUID>();
        require(root.getAsJsonArray("campaigns").size()<=64 && root.getAsJsonArray("characters").size()<=1024,"Save limits exceeded");
        for(JsonElement e:root.getAsJsonArray("campaigns")) {
            Campaign c=GSON.fromJson(e,Campaign.class); RulesProfiles.get(c.profile);
            require(c.id!=null && c.owner!=null && c.revision>0 && c.name.length()<=64 && c.members.size()<=256 && c.members.contains(c.owner)
                    && c.members.containsAll(c.dms),"Invalid campaign");
            require(campaigns.put(c.id,c)==null,"Duplicate campaign");
        }
        for(JsonElement e:root.getAsJsonArray("characters")) {
            Character c=GSON.fromJson(e,Character.class); RulesProfiles.get(c.profile);
            require(c.id!=null && c.owner!=null && campaigns.containsKey(c.campaign) && c.profileVersion==1 && c.revision>0,"Invalid character");
            validateSheet(c.sheet); validateStats(c.stats); require(characters.put(c.id,c)==null,"Duplicate character");
        }
        var used=new HashSet<UUID>();
        for(var e:root.getAsJsonObject("bindings").entrySet()) {
            UUID actor=UUID.fromString(e.getKey()),id=UUID.fromString(e.getValue().getAsString());
            require(characters.containsKey(id) && characters.get(id).approved && used.add(id),"Invalid or duplicate binding"); bindings.put(actor,id);
        }
        return new State(campaigns,characters,bindings);
    }
}
