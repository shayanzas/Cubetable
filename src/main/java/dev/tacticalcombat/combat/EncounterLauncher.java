package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.net.SrdStatePayload;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.uiapi.*;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.TypeFilter;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Bounded UI bridge to the existing controlled encounter, never a second combat engine. */
public final class EncounterLauncher {
    public static final int RADIUS=32, LIMIT=64;
    private record Discovery(UUID token,UUID campaign,long campaignRevision,long expires,Map<UUID,CharacterRepository.SrdSnapshot> actors) {}
    private static final Map<UUID,Discovery> discoveries=new HashMap<>();
    private record PanelWatch(UUID token,UUID campaign,UUID character,boolean manager,boolean self,long generation,boolean admin,String combat) {}
    private static final Map<UUID,PanelWatch> panels=new HashMap<>();
    public static void forget(UUID player){discoveries.remove(player);panels.remove(player);}
    public static void close(UUID player,UUID token){var watch=panels.get(player);if(watch!=null&&watch.token.equals(token))panels.remove(player);}
    static void panel(ServerPlayerEntity p,JsonObject out,UUID campaign,UUID character,boolean manager,boolean self){var repo=CharacterService.repository();UUID token=UUID.randomUUID();panels.put(p.getUuid(),new PanelWatch(token,campaign,character,manager,self,repo.viewGeneration(),CharacterService.principal(p).admin(),combatStamp(campaign)));out.addProperty("panelToken",token.toString());}
    private static String combatStamp(UUID campaign){return CombatManager.encounters().stream().filter(e->e instanceof SrdEncounter s&&s.campaign().equals(campaign)).map(e->(SrdEncounter)e).map(e->e.id()+":"+e.revision()).sorted().collect(java.util.stream.Collectors.joining(","));}
    public static void synchronize(net.minecraft.server.MinecraftServer server){
        var repo=CharacterService.repository();
        for(var entry:new ArrayList<>(panels.entrySet())){var player=server.getPlayerManager().getPlayer(entry.getKey());var w=entry.getValue();if(player==null){panels.remove(entry.getKey());continue;}boolean revoked=false;
            try{var who=CharacterService.principal(player);if(w.manager)require(repo.manages(who,w.campaign)&&CharacterService.selected(player).equals(w.campaign),"Manager revoked");if(w.character!=null)repo.visible(who,w.character);if(w.self){var bound=CharacterService.bound(player);require(bound!=null&&bound.id().equals(w.character),"Binding changed");}}
            catch(IllegalArgumentException denied){revoked=true;}
            boolean changed=repo.viewGeneration()!=w.generation||CharacterService.principal(player).admin()!=w.admin||!combatStamp(w.campaign).equals(w.combat);
            if(revoked||changed){if(ServerPlayNetworking.canSend(player,dev.tacticalcombat.net.RulesChangedPayload.ID))ServerPlayNetworking.send(player,new dev.tacticalcombat.net.RulesChangedPayload(w.token,revoked));panels.put(entry.getKey(),new PanelWatch(w.token,w.campaign,w.character,w.manager,w.self,repo.viewGeneration(),CharacterService.principal(player).admin(),combatStamp(w.campaign)));if(revoked)panels.remove(entry.getKey());}
        }
    }
    public static void clear(){discoveries.clear();panels.clear();}
    private static UUID campaign(ServerPlayerEntity p){
        UUID id=CharacterService.selected(p);require(CharacterService.repository().manages(CharacterService.principal(p),id),"Campaign manager permission required");return id;
    }
    static SrdEncounter startAndWatch(ServerPlayerEntity p,UUID campaign,List<LivingEntity> actors){
        require(ServerPlayNetworking.canSend(p,SrdStatePayload.ID),"Updated SRD client required");
        var before=new HashMap<LivingEntity,net.minecraft.util.math.Vec3d>();var velocity=new HashMap<LivingEntity,net.minecraft.util.math.Vec3d>();for(var actor:actors){before.put(actor,actor.getPos());velocity.put(actor,actor.getVelocity());}
        var encounter=SrdEncounter.start(p.getServerWorld(),CharacterService.principal(p),campaign,actors);
        try {encounter.begin();SrdNetworking.watch(p,encounter);for(var actor:actors)if(actor instanceof ServerPlayerEntity player && player!=p)SrdNetworking.watch(player,encounter);return encounter;}
        catch(RuntimeException failure){CombatManager.endCombat(encounter);before.forEach((actor,pos)->{actor.requestTeleport(pos.x,pos.y,pos.z);actor.setVelocity(velocity.get(actor));});throw failure;}
    }
    private static boolean near(ServerPlayerEntity p,LivingEntity actor){return actor.getWorld()==p.getWorld()&&p.squaredDistanceTo(actor)<=RADIUS*RADIUS;}
    private static String name(LivingEntity actor){String n=actor.getName().getString();return n.substring(0,Math.min(80,n.length()));}
    public static JsonObject handle(ServerPlayerEntity p,Operation op,JsonObject a){
        require(p.getServer().isOnThread(),"Server thread required");var repo=CharacterService.repository();var who=CharacterService.principal(p);var out=new JsonObject();
        if(op==Operation.QUICK_SHEET){
            fields(a,a.has("character")?"character":"");
            var bound=CharacterService.bound(p);UUID id;
            if(a.has("character")){UUID campaign=campaign(p);id=id(a,"character");require(repo.visible(who,id).campaign().equals(campaign),"Selected campaign permission required");}
            else {if(bound==null||!bound.profile().equals(RulesProfiles.SRD)){out.addProperty("empty","No compatible character is bound to your player. Ask your campaign DM to approve and bind an SRD character in the roster.");return out;}id=bound.id();}
            out=new CharacterSyncService(repo).snapshot(who,id);out.addProperty("boundToSelf",bound!=null&&bound.id().equals(id));panel(p,out,repo.character(id).campaign(),id,a.has("character"),!a.has("character"));return out;
        }
        UUID campaign=campaign(p);
        switch(op){
            case ENCOUNTER_DISCOVER -> {
                fields(a,a.has("binding")?"binding kind":"");
                var binding=a.has("binding")?NearbyActors.Binding.valueOf(string(a,"binding")):NearbyActors.Binding.ALL;
                var kind=a.has("kind")?NearbyActors.Kind.valueOf(string(a,"kind")):NearbyActors.Kind.ALL;
                var nearby=NearbyActors.sorted(p,RADIUS);var valid=new HashMap<UUID,CharacterRepository.SrdSnapshot>();var rows=new JsonArray();boolean truncated=false;
                for(var actor:nearby){
                    boolean player=actor instanceof ServerPlayerEntity;var bound=CharacterService.bound(actor);boolean same=bound!=null&&bound.campaign().equals(campaign);
                    if(kind!=NearbyActors.Kind.ALL&&(kind==NearbyActors.Kind.PLAYER)!=player)continue;
                    if(binding==NearbyActors.Binding.BOUND&&bound==null||binding==NearbyActors.Binding.UNBOUND&&bound!=null)continue;
                    CharacterRepository.SrdSnapshot snapshot=null;String reason="Ready";
                    try{snapshot=SrdEncounter.validateAdmission(p.getServerWorld(),campaign,actor);}catch(IllegalArgumentException invalid){reason=bound!=null&&!same?"Other campaign":invalid.getMessage();}
                    if(binding==NearbyActors.Binding.ELIGIBLE&&snapshot==null)continue;
                    if(rows.size()==LIMIT){truncated=true;break;}
                    var row=new JsonObject();row.addProperty("actor",actor.getUuidAsString());row.addProperty("name",name(actor));row.addProperty("kind",player?"Player":"NPC");row.addProperty("type",net.minecraft.registry.Registries.ENTITY_TYPE.getId(actor.getType()).toString());
                    row.addProperty("x",actor.getX());row.addProperty("z",actor.getZ());row.addProperty("distance",Math.round(Math.sqrt(actor.squaredDistanceTo(p))*10)/10d);row.addProperty("position",actor.getBlockX()+", "+actor.getBlockY()+", "+actor.getBlockZ());
                    row.addProperty("bound",bound!=null);row.addProperty("approved",same&&bound.approved());row.addProperty("characterName",same?UiApiService.name(bound.sheet()):"");row.addProperty("eligible",snapshot!=null);row.addProperty("reason",reason);
                    row.addProperty("canBind",!player&&actor instanceof net.minecraft.entity.mob.MobEntity&&bound==null&&!CombatManager.isInCombat(actor));
                    if(snapshot!=null)valid.put(actor.getUuid(),snapshot);rows.add(row);
                }
                UUID token=UUID.randomUUID();discoveries.put(p.getUuid(),new Discovery(token,campaign,repo.campaign(campaign).revision(),p.getServer().getTicks()+1200,Map.copyOf(valid)));
                out.addProperty("originX",p.getX());out.addProperty("originZ",p.getZ());out.addProperty("location",p.getBlockX()+", "+p.getBlockY()+", "+p.getBlockZ());out.addProperty("dimension",p.getServerWorld().getRegistryKey().getValue().toString());out.addProperty("campaignName",repo.campaign(campaign).name());out.addProperty("discovery",token.toString());out.addProperty("radius",RADIUS);out.addProperty("truncated",truncated);out.add("actors",rows);panel(p,out,campaign,null,true,false);
            }
            case ACTOR_CHARACTERS -> {
                fields(a,"actor page");int page=integer(a,"page");require(page>=0&&page<128,"Invalid page");var entity=p.getServerWorld().getEntity(id(a,"actor"));
                require(entity instanceof net.minecraft.entity.mob.MobEntity&&entity.isAlive()&&near(p,(LivingEntity)entity)&&CharacterService.bound((LivingEntity)entity)==null&&!CombatManager.isInCombat(entity),"Nearby unbound NPC required");
                var available=repo.state().characters().values().stream().filter(c->c.campaign().equals(campaign)&&c.approved()&&repo.binding(c.id())==null&&!repo.reserved(c.id())&&c.profile().equals(RulesProfiles.SRD)&&repo.member(new CharacterRepository.Principal(c.owner(),false),campaign)&&repo.member(new CharacterRepository.Principal(repo.controller(c.id()),false),campaign)).sorted(Comparator.comparing((CharacterRepository.Character c)->UiApiService.name(c.sheet()),String.CASE_INSENSITIVE_ORDER).thenComparing(c->c.id().toString())).toList();
                var rows=new JsonArray();for(var c:available.stream().skip(page*8L).limit(8).toList()){var row=new JsonObject();row.addProperty("character",c.id().toString());row.addProperty("revision",c.revision());row.addProperty("name",UiApiService.name(c.sheet()));rows.add(row);}out.add("characters",rows);out.addProperty("hasMore",available.size()>(page+1)*8);panel(p,out,campaign,null,true,false);
            }
            case ENCOUNTER_START -> {
                fields(a,"discovery actors");var d=discoveries.remove(p.getUuid());require(d!=null&&d.token.equals(id(a,"discovery"))&&d.campaign.equals(campaign)&&d.expires>=p.getServer().getTicks()&&d.campaignRevision==repo.campaign(campaign).revision(),"Stale encounter selection; Refresh and select participants again");
                var selected=a.getAsJsonArray("actors");require(selected.size()>=2&&selected.size()<=16,"Select 2–16 participants");var actors=new ArrayList<LivingEntity>();var seen=new HashSet<UUID>();
                for(var value:selected){UUID id=UUID.fromString(value.getAsString());require(seen.add(id),"Duplicate actor");require(p.getServerWorld().getEntity(id) instanceof LivingEntity,"Stale participant: no longer loaded in this world");var actor=(LivingEntity)p.getServerWorld().getEntity(id);require(near(p,actor),"Stale participant: outside discovery radius");var current=SrdEncounter.validateAdmission(p.getServerWorld(),campaign,actor);require(current.equals(d.actors.get(id)),"Stale character or binding; Refresh participants");actors.add(actor);}
                var encounter=startAndWatch(p,campaign,actors);out.addProperty("encounter",encounter.id().toString());out.addProperty("state",encounter.state().name());out.addProperty("reason",encounter.reason());
            }
            case ENCOUNTER_LIST -> {
                fields(a,"");var rows=new JsonArray();for(var e:CombatManager.encounters())if(e instanceof SrdEncounter s&&s.campaign().equals(campaign)&&rows.size()<64){var row=new JsonObject();row.addProperty("encounter",s.id().toString());row.addProperty("state",s.state().name());row.addProperty("actors",String.join(", ",s.actors().stream().map(EncounterLauncher::name).toList()));row.addProperty("reason",s.reason());rows.add(row);}out.add("encounters",rows);panel(p,out,campaign,null,true,false);
            }
            case ENCOUNTER_WATCH, ENCOUNTER_END -> {
                fields(a,"encounter");var encounter=SrdNetworking.find(id(a,"encounter"));require(encounter!=null&&encounter.campaign().equals(campaign),"Encounter unavailable in selected campaign");
                if(op==Operation.ENCOUNTER_END){encounter.stop(who);out.addProperty("ended",true);}else{SrdNetworking.watch(p,encounter);out.addProperty("encounter",encounter.id().toString());}
            }
            case DM_QUICK -> {
                fields(a,"page");int page=integer(a,"page");require(page>=0&&page<=128,"Invalid page");out.addProperty("campaign",campaign.toString());out.addProperty("campaignName",repo.campaign(campaign).name());out.addProperty("manager",true);out.addProperty("location",p.getBlockX()+", "+p.getBlockY()+", "+p.getBlockZ());out.addProperty("dimension",p.getServerWorld().getRegistryKey().getValue().toString());
                var rows=new JsonArray();var characters=repo.state().characters().values().stream().filter(c->c.campaign().equals(campaign)).sorted(Comparator.comparing(c->c.id().toString())).toList();
                for(var c:characters.stream().skip(page*8L).limit(8).toList()){var row=new JsonObject();row.addProperty("character",c.id().toString());row.addProperty("name",UiApiService.name(c.sheet()));row.addProperty("approved",c.approved());UUID binding=repo.binding(c.id());row.addProperty("bound",binding!=null);LivingEntity actor=binding!=null&&p.getServerWorld().getEntity(binding) instanceof LivingEntity living?living:null;row.addProperty("kind",actor instanceof ServerPlayerEntity?"Player":actor!=null?"NPC":binding!=null?"Unloaded binding":"Unbound");row.addProperty("inCombat",actor!=null&&CombatManager.isInCombat(actor));var snapshot=new CharacterSyncService(repo).snapshot(who,c.id());if(snapshot.has("rules")&&snapshot.getAsJsonObject("rules").get("valid").getAsBoolean()){var r=snapshot.getAsJsonObject("rules");row.addProperty("health",r.get("hp").getAsInt()+" / "+r.get("maximumHp").getAsInt()+" HP");row.addProperty("ac",r.get("ac").getAsInt());}var conditions=new ArrayList<String>();for(var feature:SheetSections.read(snapshot.getAsJsonObject("sheet"),"features").getAsJsonArray("entries")){var item=feature.getAsJsonObject();if(Set.of("CONDITION","TEMPORARY").contains(item.get("kind").getAsString()))conditions.add(item.get("name").getAsString()+" (recorded)");}row.addProperty("conditions",String.join(", ",conditions));rows.add(row);}
                out.add("characters",rows);out.addProperty("hasMore",characters.size()>(page+1)*8);panel(p,out,campaign,null,true,false);
            }
            default -> throw new IllegalArgumentException("Unsupported launcher operation");
        }return out;
    }
}
