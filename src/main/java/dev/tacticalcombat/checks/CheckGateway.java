package dev.tacticalcombat.checks;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.combat.CombatManager;
import dev.tacticalcombat.uiapi.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Authenticated UI gateway; no client arithmetic or arbitrary expressions enter the service. */
public final class CheckGateway {
    private static final Gson WIRE=new Gson();
    private static final Map<UUID,Long> notified=new HashMap<>();
    private static CheckService service;private static CharacterRepository repository;
    private static final java.security.SecureRandom RANDOM=new java.security.SecureRandom();
    private CheckGateway() {}
    public static void clear(){service=null;repository=null;notified.clear();}
    private static CheckService service(ServerPlayerEntity p){var repo=CharacterService.repository();if(service==null||repository!=repo){repository=repo;var server=p.getServer();service=new CheckService(repo,System::currentTimeMillis,()->RANDOM.nextInt(20)+1,id->{var player=server.getPlayerManager().getPlayer(id);return player==null?new CharacterRepository.Principal(id,false):CharacterService.principal(player);});}return service;}
    private static UUID encounter(ServerPlayerEntity p){var e=CombatManager.encounter(p);return e==null?null:e.id();}
    private static CheckService.Spec spec(JsonObject a){fields(a,"category key mode purpose visibility recipients dc");var recipients=new HashSet<UUID>();require(a.get("recipients").isJsonArray()&&a.getAsJsonArray("recipients").size()<=16,"Bounded recipient array required");for(var r:a.getAsJsonArray("recipients"))require(recipients.add(UUID.fromString(r.getAsString())),"Duplicate recipient");Integer dc=a.get("dc").isJsonNull()?null:integer(a,"dc");return new CheckService.Spec(new CharacterChecks.Key(CharacterChecks.Category.valueOf(string(a,"category")),string(a,"key")),CharacterChecks.Mode.valueOf(string(a,"mode")),string(a,"purpose"),CheckService.Visibility.valueOf(string(a,"visibility")),recipients,dc);}
    public static JsonObject handle(ServerPlayerEntity p,Operation op,JsonObject a){require(p.getServer().isOnThread(),"Character checks require server thread");var repo=CharacterService.repository();var who=CharacterService.principal(p);var campaign=CharacterService.selected(p);require(repo.member(who,campaign),"Selected campaign membership required");var svc=service(p);var out=new JsonObject();out.addProperty("campaign",campaign.toString());out.addProperty("manager",repo.manages(who,campaign));
        switch(op){
            case CHECK_TARGETS->{fields(a,"");require(repo.manages(who,campaign),"DM authority required");var rows=new JsonArray();for(var c:repo.state().characters().values().stream().filter(c->c.campaign().equals(campaign)).sorted(Comparator.comparing(c->c.id().toString())).toList()){try{svc.eligible(campaign,c.id());}catch(IllegalArgumentException unavailable){continue;}if(rows.size()>=128)break;var r=new JsonObject();r.addProperty("id",c.id().toString());r.addProperty("revision",c.revision());r.addProperty("name",UiApiService.name(c.sheet()));var controller=repo.controller(c.id());r.addProperty("controller",controller.toString());r.addProperty("requestEligible",Objects.equals(repo.binding(c.id()),controller)&&p.getServer().getPlayerManager().getPlayer(controller)!=null);rows.add(r);}out.add("characters",rows);var members=new JsonArray();for(var player:p.getServer().getPlayerManager().getPlayerList())if(repo.member(CharacterService.principal(player),campaign)&&members.size()<128){var r=new JsonObject();r.addProperty("id",player.getUuidAsString());r.addProperty("name",player.getName().getString());members.add(r);}out.add("members",members);}
            case CHECK_OPTIONS->{fields(a,a.has("character")?"character":"");var c=a.has("character")?repo.visible(who,id(a,"character")):CharacterService.bound(p);require(c!=null,"No bound character");svc.eligible(campaign,c.id());require(repo.manages(who,campaign)||Objects.equals(repo.binding(c.id()),p.getUuid())&&repo.controller(c.id()).equals(p.getUuid()),"Own bound character or DM authority required");out.addProperty("character",c.id().toString());out.addProperty("name",UiApiService.name(c.sheet()));out.addProperty("revision",c.revision());var rows=new JsonArray();for(var key:CharacterChecks.keys()){var r=WIRE.toJsonTree(key).getAsJsonObject();try{r.add("modifier",WIRE.toJsonTree(CharacterChecks.resolve(c.sheet(),key)));r.addProperty("available",true);}catch(IllegalArgumentException invalid){r.addProperty("available",false);r.addProperty("reason",invalid.getMessage());}rows.add(r);}out.add("checks",rows);}
            case CHECK_PREPARE->{fields(a,"character revision spec adjudicated");boolean dm=bool(a,"adjudicated");var t=svc.prepare(who,campaign,id(a,"character"),revision(a),spec(a.getAsJsonObject("spec")),dm?CheckService.Source.DM_ADJUDICATION:CheckService.Source.PLAYER_CHARACTER_CHECK,encounter(p));out.addProperty("operation",t.id().toString());out.addProperty("expires",t.expires());}
            case CHECK_EXECUTE->{fields(a,"operation");var result=svc.execute(who,campaign,id(a,"operation"),encounter(p));out.add("result",WIRE.toJsonTree(result));out.addProperty("accepted",true);}
            case CHECK_INBOX->{fields(a,"page");int page=integer(a,"page");require(page<=128,"Page outside bounds");var all=new ArrayList<>(svc.list(who,campaign));Collections.reverse(all);int start=Math.min(all.size(),page*8);out.add("rolls",WIRE.toJsonTree(all.subList(start,Math.min(start+8,all.size()))));out.addProperty("hasMore",start+8<all.size());}
            case CHECK_DISMISS->{fields(a,"operation");svc.dismiss(who,campaign,id(a,"operation"));out.addProperty("dismissed",true);}
            case CHECK_REQUEST->{fields(a,"characters spec");require(a.get("characters").isJsonArray()&&a.getAsJsonArray("characters").size()<=16,"At most 16 targets");var targets=new ArrayList<UUID>();for(var x:a.getAsJsonArray("characters"))targets.add(UUID.fromString(x.getAsString()));var online=new HashSet<UUID>();for(var player:p.getServer().getPlayerManager().getPlayerList())online.add(player.getUuid());var tickets=svc.request(who,campaign,targets,spec(a.getAsJsonObject("spec")),online);out.add("operations",WIRE.toJsonTree(tickets.stream().map(CheckService.Ticket::id).toList()));notified.values().removeIf(expiry->expiry<=System.currentTimeMillis());for(var t:tickets)if(notified.putIfAbsent(t.id(),t.expires())==null){var recipient=p.getServer().getPlayerManager().getPlayer(t.executor());if(recipient!=null)recipient.sendMessage(Text.literal("Cubetable: a DM check request is waiting. Open Quick Sheet → Roll Requests & Results (expires in 10 minutes)."),false);}}
            default->throw new IllegalArgumentException("Unknown character check operation");
        }
        if(Set.of(Operation.CHECK_TARGETS,Operation.CHECK_OPTIONS,Operation.CHECK_INBOX).contains(op)){
            boolean manager=repo.manages(who,campaign);UUID character=op==Operation.CHECK_OPTIONS?UUID.fromString(out.get("character").getAsString()):null;
            if(!manager&&character==null){var bound=CharacterService.bound(p);if(bound!=null)character=bound.id();}
            dev.tacticalcombat.combat.EncounterLauncher.panel(p,out,campaign,character,manager,!manager&&character!=null);
        }
        return out;
    }
}
