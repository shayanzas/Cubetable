package dev.tacticalcombat.uiapi;
import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.combat.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdCharacterCodec;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Read models project existing authority. No duplicated HP/initiative runtime state. */
public final class UiPresentation {
    private UiPresentation() {}
    public static JsonObject party(ServerPlayerEntity player,UUID campaign,int page,UiWorkflowStore.Policy policy) {
        var repo=CharacterService.repository();var p=CharacterService.principal(player);require(repo.member(p,campaign),"Campaign permission denied");require(page>=0 && page<=128,"Invalid page");
        var out=new JsonObject();var rows=new JsonArray();
        var bindings=new HashMap<UUID,UUID>();repo.state().bindings().forEach((actor,character)->bindings.put(character,actor));
        // Only player-bound characters, never NPC enemies, are shareable as party members.
        var roster=repo.state().characters().values().stream().filter(c->c.campaign().equals(campaign) && repo.controller(c.id()).equals(bindings.get(c.id())))
            .sorted(Comparator.comparing(c->c.id().toString())).toList();
        StringBuilder identity=new StringBuilder(campaign+":"+repo.campaign(campaign).revision()+":"+policy.revision()+":"+p.id()+":"+p.admin());
        for(var c:roster) identity.append(":").append(c.id()).append(":").append(repo.controller(c.id())).append(":").append(c.revision()).append(":").append(repo.manualRevision(c.id())).append(":").append(player.getServer().getPlayerManager().getPlayer(repo.controller(c.id()))!=null);
        out.addProperty("rosterToken",UUID.nameUUIDFromBytes(identity.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
        out.addProperty("total",roster.size());out.addProperty("page",page);out.addProperty("pageSize",8);out.addProperty("hasMore",(page+1)*8<roster.size());
        roster.stream().skip(page*8L).limit(8).forEach(c->{
                var row=new JsonObject();row.addProperty("character",c.id().toString());row.addProperty("name",UiApiService.name(c.sheet()));row.addProperty("skinPlayer",repo.controller(c.id()).toString());
                boolean privateView=repo.manages(p,campaign) || c.owner().equals(p.id()) || repo.controller(c.id()).equals(p.id());
                row.addProperty("online",player.getServer().getPlayerManager().getPlayer(repo.controller(c.id()))!=null);
                row.addProperty("characterClass",SheetSections.read(repo.effectiveSheet(c),"identity").get("characterClass").getAsString());
                if(c.profile().equals(RulesProfiles.SRD)) try {
                    var stats=SrdCharacterCodec.parseSheet(c.sheet());
                    if(privateView || policy.hp()==UiWorkflowStore.HpVisibility.EXACT) {row.addProperty("hp",stats.hp());row.addProperty("maximumHp",stats.maximumHp());row.addProperty("temporaryHp",stats.temporaryHp());}
                    else if(policy.hp()==UiWorkflowStore.HpVisibility.BARS) row.addProperty("healthFraction",stats.hp()/(double)stats.maximumHp());
                } catch(IllegalArgumentException invalid) {row.addProperty("readiness","Invalid SRD inputs");}
                else if(c.profile().equals(RulesProfiles.TACTICAL)) {
                    var online=player.getServer().getPlayerManager().getPlayer(repo.controller(c.id()));double hp=online==null?c.stats().get("hp"):online.getHealth(),max=c.stats().get("hp_max");
                    if(privateView || policy.hp()==UiWorkflowStore.HpVisibility.EXACT) {row.addProperty("hp",hp);row.addProperty("maximumHp",max);}
                    else if(policy.hp()==UiWorkflowStore.HpVisibility.BARS) row.addProperty("healthFraction",Math.clamp(hp/max,0,1));
                }
                if(privateView) {
                    var conditions=new JsonArray();for(var entry:SheetSections.read(repo.effectiveSheet(c),"features").getAsJsonArray("entries")) {
                        var item=entry.getAsJsonObject();if(conditions.size()<16 && Set.of("CONDITION","TEMPORARY").contains(item.get("kind").getAsString())) {
                            var condition=new JsonObject();for(String key:List.of("id","name","status","durationRounds")) condition.add(key,item.get(key));conditions.add(condition);
                        }
                    }row.add("recordedConditions",conditions);
                }
                var entity=player.getServer().getPlayerManager().getPlayer(bindings.get(c.id()));var encounter=entity==null?null:CombatManager.encounter(entity);row.addProperty("inCombat",encounter!=null);if(encounter!=null) row.addProperty("encounter",encounter.id().toString());rows.add(row);
            });out.add("members",rows);out.addProperty("hpVisibility",policy.hp().name());out.add("policy",SheetSections.JSON.toJsonTree(policy));return out;
    }
    public static JsonObject initiative(ServerPlayerEntity player,UUID encounter) {
        return CombatManager.presentation(player,encounter);
    }
    public static List<UUID> restParty(ServerPlayerEntity player,UUID campaign) {
        var repo=CharacterService.repository();var ids=new ArrayList<UUID>();
        for(var c:repo.state().characters().values()) if(c.campaign().equals(campaign) && repo.controller(c.id()).equals(repo.binding(c.id())) && player.getServer().getPlayerManager().getPlayer(repo.controller(c.id()))!=null) ids.add(repo.controller(c.id()));
        if(!ids.contains(player.getUuid())) ids.add(player.getUuid());require(ids.size()<=16,"Rest party exceeds 16");return List.copyOf(ids);
    }
    public static void eligible(ServerPlayerEntity manager,UUID campaign,List<UUID> players,String dimension,int x,int y,int z,int radius) {
        var repo=CharacterService.repository();
        for(UUID id:players) {
            var p=manager.getServer().getPlayerManager().getPlayer(id);
            require(p!=null && repo.member(new CharacterRepository.Principal(id,false),campaign),"Rest party member offline or no longer eligible");
            require(p.getServerWorld().getRegistryKey().getValue().toString().equals(dimension) && p.squaredDistanceTo(x+.5,y,z+.5)<=radius*(double)radius,"Rest party must be nearby in the same dimension");
            require(p.isAlive() && !p.isSpectator() && !CombatManager.isInCombat(p),"Rest unavailable during combat or while incapacitated");
        }
    }
}
