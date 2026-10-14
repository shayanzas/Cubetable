package dev.tacticalcombat.combat;
import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
public class UiFoundationGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void guidedClassChoicesAreValidatedAtAuthenticatedGateway(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            var create=new JsonObject();create.addProperty("profile",RulesProfiles.SRD);create.addProperty("mode","GUIDED");create.add("sheet",JsonNull.INSTANCE);
            var response=UiNetworking.handle(player,new UiRequestPayload(1,"LIBRARY_CREATE",create.toString()));check(response.code().equals("OK"),"guided creation accepted");var draft=JsonParser.parseString(response.json()).getAsJsonObject();var sheet=draft.getAsJsonObject("sheet");check(CreationRules.enrolled(sheet),"server enrolls guided validation");
            var identity=SheetSections.read(sheet,"identity");identity.addProperty("species","Human");identity.addProperty("characterClass","Rogue");var update=new JsonObject();update.add("character",draft.get("id"));update.add("revision",draft.get("revision"));update.addProperty("section","identity");update.add("data",identity);response=UiNetworking.handle(player,new UiRequestPayload(2,"LIBRARY_UPDATE",update.toString()));check(response.code().equals("OK"),"class selection acknowledged");draft=JsonParser.parseString(response.json()).getAsJsonObject();
            update.add("revision",draft.get("revision"));update.addProperty("section","creation");var choices=SheetSections.JSON.toJsonTree(new CreationRules.Choices(List.of("Acrobatics","Athletics","Deception","Insight","Stealth"),List.of("Acrobatics","Athletics"),"MANUAL")).getAsJsonObject();update.add("data",choices);check(!UiNetworking.handle(player,new UiRequestPayload(3,"LIBRARY_UPDATE",update.toString())).code().equals("OK"),"forged fifth Rogue choice rejected by server");choices.getAsJsonArray("skills").remove(4);response=UiNetworking.handle(player,new UiRequestPayload(4,"LIBRARY_UPDATE",update.toString()));check(response.code().equals("OK"),"valid class choices acknowledged");draft=JsonParser.parseString(response.json()).getAsJsonObject();var p=SheetSections.read(draft.getAsJsonObject("sheet"),"proficiencies");check(p.getAsJsonArray("skills").size()==4&&p.getAsJsonArray("saves").asList().stream().map(JsonElement::getAsString).collect(java.util.stream.Collectors.toSet()).equals(Set.of("dex","int")),"ack contains four choices and mandatory Rogue saves");
        } finally {UiNetworking.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void campaignInspirationAwardsRequireDmAtAuthenticatedGateway(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            var p=CharacterService.principal(player);f.repo.invite(f.dm,f.campaign,1,p.id(),false);var id=f.repo.submit(p,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,p.id(),p.id());var combat=f.repo.srdSnapshot(p.id(),f.campaign);
            var request=new JsonObject();request.addProperty("character",id.toString());request.addProperty("manualRevision",0);request.add("change",SheetSections.JSON.toJsonTree(new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"trackers","",0,3)));check(!UiNetworking.handle(player,new UiRequestPayload(1,"MANUAL_COUNTER",request.toString())).code().equals("OK"),"player cannot award campaign Inspiration");check(f.repo.manualRevision(id)==0,"rejected award leaves revision untouched");f.repo.track(f.dm,id,0,new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"trackers","",0,3));request.addProperty("manualRevision",1);request.add("change",SheetSections.JSON.toJsonTree(new RpgRecords.Change(RpgRecords.Action.INSPIRATION,"trackers","",0,2)));var reply=UiNetworking.handle(player,new UiRequestPayload(2,"MANUAL_COUNTER",request.toString()));check(reply.code().equals("OK"),"current controller may decrease awarded manual count");check(SheetSections.read(JsonParser.parseString(reply.json()).getAsJsonObject().getAsJsonObject("sheet"),"trackers").get("inspiration").getAsInt()==2,"count acknowledged accurately");check(f.repo.srdSnapshot(p.id(),f.campaign).equals(combat),"inspiration bookkeeping preserves complete combat snapshot");check(UiNetworking.handle(player,new UiRequestPayload(2,"MANUAL_COUNTER",request.toString())).code().equals("ALREADY_PROCESSED"),"manual decrease cannot replay");
        } finally {UiNetworking.forget(player.getUuid());CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void uiGatewayChecksAuthenticatedPermissionReplayAndBounds(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            var args=new JsonObject();args.addProperty("campaign",f.campaign.toString());
            var request=new UiRequestPayload(1,"CAMPAIGN_GET",args.toString());
            check(UiNetworking.handle(player,request).code().equals("FORBIDDEN"),"authenticated outsider cannot inspect campaign");
            check(UiNetworking.handle(player,request).code().equals("ALREADY_PROCESSED"),"same request cannot replay");
            check(!UiNetworking.handle(player,new UiRequestPayload(2,"CAPABILITIES","x".repeat(UiRequestPayload.LIMIT+1))).code().equals("OK"),"oversized direct request denied");
            args.addProperty("admin",true);check(!UiNetworking.handle(player,new UiRequestPayload(3,"CAMPAIGN_GET",args.toString())).code().equals("OK"),"forged administrator field rejected");
        } finally {ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void assignedPlayerControlsBoundActorWithoutOwningOrEditingIt(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            SrdLiveGameTests.capability(player);var principal=CharacterService.principal(player);f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);CharacterService.select(player,f.campaign);
            player.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var character=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,character,1);
            f.repo.offerControl(f.dm,character,2,0,player.getUuid());f.repo.answerControl(principal,character,1,true);f.repo.bind(f.dm,character,2,player.getUuid(),player.getUuid());
            var npc=f.actor(5,2,true);var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),player,npc);
            check(e.snapshot(principal).getAsJsonArray("actors").get(0).getAsJsonObject().get("controlled").getAsBoolean(),"substitute has authoritative actor control");
            e.grid(principal);check(e.current()==player,"assigned player can request grid for active actor");
            rejects(()->{try {f.repo.clearControl(f.dm,character,2);} catch(java.io.IOException storage) {throw new AssertionError(storage);}},"active binding blocks control replacement");
            rejects(()->CharacterService.select(player,f.campaign),"ordinary campaign switch remains combat locked");
            check(f.repo.character(character).owner().equals(f.dm.id()),"ownership remains original");
            e.end();
        } finally {CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void partyPrivacyAndRestEligibilityUseLiveWorldState(TestContext ctx) throws Exception {
        var a=ctx.createMockCreativeServerPlayerInWorld();var b=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,a.getUuid(),false);f.repo.invite(f.dm,f.campaign,2,b.getUuid(),false);
            var id=f.repo.submit(CharacterService.principal(a),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,a.getUuid(),a.getUuid());
            var npc=f.actor(4,2,true);var second=f.actor(6,2,true);
            var privateView=UiPresentation.party(b,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,0,UiWorkflowStore.HpVisibility.PRIVATE,16)).getAsJsonArray("members");
            check(privateView.size()==1 && !privateView.get(0).getAsJsonObject().has("hp"),"party excludes NPC enemies and private peer HP");
            var bars=UiPresentation.party(b,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,1,UiWorkflowStore.HpVisibility.BARS,16)).getAsJsonArray("members").get(0).getAsJsonObject();
            check(bars.has("healthFraction") && !bars.has("maximumHp"),"bar policy does not expose exact HP fields");
            check(UiPresentation.party(a,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,0,UiWorkflowStore.HpVisibility.PRIVATE,16)).getAsJsonArray("members").get(0).getAsJsonObject().has("hp"),"owner retains exact private HP");
            String dimension=ctx.getWorld().getRegistryKey().getValue().toString();a.setPosition(0.5,64,0.5);b.setPosition(1.5,64,0.5);
            UiPresentation.eligible(a,f.campaign,List.of(a.getUuid(),b.getUuid()),dimension,0,64,0,16);
            b.setPosition(100.5,64,0.5);rejects(()->UiPresentation.eligible(a,f.campaign,List.of(a.getUuid(),b.getUuid()),dimension,0,64,0,16),"distant party cannot approve rest");
            var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1),npc,second);
            rejects(()->UiPresentation.initiative(b,e.id()),"campaign membership alone does not expose a separate encounter");
        } finally {CharacterService.forget(a.getUuid());CharacterService.forget(b.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(a);ctx.getWorld().getServer().getPlayerManager().remove(b);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void committedAttackFeedsOnePrivateStructuredRoll(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var actor=f.actor(2,2,true);var target=f.actor(3,2,true);var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1,18),actor,target);
            var intent=e.preview(f.dm,target.getUuid(),SrdEquipment.Weapon.UNARMED,SrdRules.AttackMode.MELEE,SrdCharacter.Ability.STR,false);var result=e.confirm(f.dm,intent.id());
            var events=UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.ATTACK).events();
            check(events.size()==1 && events.getFirst().id().equals(intent.id()),"committed action emits one stable event");
            check(events.getFirst().roll().total()==result.roll.roll().total() && events.getFirst().roll().faces().equals(result.roll.roll().faces()),"presentation matches authoritative RNG");
            rejects(()->e.confirm(f.dm,intent.id()),"duplicate accepted intent cannot reroll");
            check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.ATTACK).events().size()==1,"retry cannot duplicate event");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void restGatewayApprovesAdjudicationWithoutRecoveringResources(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();var dm=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,player.getUuid(),false);f.repo.invite(f.dm,f.campaign,2,dm.getUuid(),true);
            var args=new JsonObject();args.addProperty("campaign",f.campaign.toString());args.addProperty("request",UUID.randomUUID().toString());args.addProperty("kind","SHORT");
            var request=UiNetworking.handle(player,new UiRequestPayload(1,"REST_REQUEST",args.toString()));check(request.code().equals("OK"),"rest request committed: "+request.message());
            var review=new JsonObject();review.add("request",args.get("request"));review.addProperty("revision",1);review.addProperty("approve",true);
            check(!UiNetworking.handle(player,new UiRequestPayload(2,"REST_REVIEW",review.toString())).code().equals("OK"),"requester cannot approve rest");
            var before=f.repo.state();var response=UiNetworking.handle(dm,new UiRequestPayload(1,"REST_REVIEW",review.toString()));
            check(response.code().equals("OK") && response.json().contains("APPROVED_FOR_ADJUDICATION"),"DM approval is explicit manual adjudication");
            check(f.repo.state().equals(before),"rest review cannot heal, recover slots or mutate characters");
            var buf=new net.minecraft.network.RegistryByteBuf(io.netty.buffer.Unpooled.buffer(),ctx.getWorld().getRegistryManager());
            try {rejects(()->{try {UiRequestPayload.CODEC.encode(buf,new UiRequestPayload(3,"CAPABILITIES","x".repeat(UiRequestPayload.LIMIT+1)));} catch(io.netty.handler.codec.EncoderException expected) {throw new IllegalArgumentException(expected);}},"actual packet codec bounds");} finally {buf.release();}
        } finally {ctx.getWorld().getServer().getPlayerManager().remove(player);ctx.getWorld().getServer().getPlayerManager().remove(dm);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void manualCountersPreserveActiveSrdIntentAndActionAuthority(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            SrdLiveGameTests.capability(player);var p=CharacterService.principal(player);f.repo.invite(f.dm,f.campaign,1,p.id(),false);
            player.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+2.5);
            var id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.offerControl(f.dm,id,2,0,p.id());f.repo.answerControl(p,id,1,true);f.repo.bind(f.dm,id,2,p.id(),p.id());
            var target=f.actor(3,2,true);var e=SrdLiveGameTests.begin(f,new SrdLiveGameTests.Dice(20,1,18),player,target);
            var intent=e.preview(p,target.getUuid(),SrdEquipment.Weapon.UNARMED,SrdRules.AttackMode.MELEE,SrdCharacter.Ability.STR,false);
            long revision=e.revision(),turn=e.turn();var snapshot=f.repo.srdSnapshot(p.id(),f.campaign);var original=e.snapshot(p);
            var change=new RpgRecords.Change(RpgRecords.Action.DEATH_SUCCESSES,"trackers","",0,1);
            var request=new JsonObject();request.addProperty("character",id.toString());request.addProperty("manualRevision",0);request.add("change",SheetSections.JSON.toJsonTree(change));
            check(UiNetworking.handle(player,new UiRequestPayload(1,"MANUAL_COUNTER",request.toString())).code().equals("OK"),"bound accepted controller uses authenticated counter API");
            check(e.revision()==revision&&e.turn()==turn&&f.repo.srdSnapshot(p.id(),f.campaign).equals(snapshot),"no encounter or combat snapshot change");
            e.tick();check(e.state()==SrdEncounter.State.ACTIVE&&e.snapshot(p).get("actionUsed").equals(original.get("actionUsed")),"no pause or extra action");
            e.grid(p);var result=e.confirm(p,intent.id());check(result.state==SrdEncounter.TransactionState.COMMITTED,"pre-counter preview still commits once");
            rejects(()->e.confirm(p,intent.id()),"counter did not make intent replayable");
            check(UiNetworking.handle(player,new UiRequestPayload(2,"MANUAL_COUNTER",request.toString())).code().equals("CONFLICT"),"stale counter remains conflict during encounter");
            check(f.repo.character(id).approved()&&f.repo.character(id).revision()==snapshot.revision(),"counter preserved approval/editing revision");
            var events=UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.DAMAGE).events();check(events.size()==1&&events.getFirst().id().equals(UiEvents.derived(intent.id(),"damage")),"committed damage has stable derived event identity");
            check(UiEvents.SESSION.query(f.repo,p,f.campaign,0,UiEvents.Kind.DAMAGE).events().isEmpty(),"attacker cannot inspect NPC private HP change");
            var rejected=new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"unsupported","{}");
            check(SrdNetworking.handle(player,rejected).code().equals("REJECTED"),"admitted invalid operation rejected");
            check(UiEvents.SESSION.query(f.repo,p,f.campaign,0,UiEvents.Kind.REJECTION).events().size()==1,"authoritative rejection privately recorded");
            SrdNetworking.handle(player,rejected);check(UiEvents.SESSION.query(f.repo,p,f.campaign,0,UiEvents.Kind.REJECTION).events().size()==1,"replayed rejection does not duplicate log");
            e.end();
        } finally {CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void partyPaginationTracksRosterAndPrivacyGenerations(TestContext ctx) throws Exception {
        var viewer=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            f.repo.invite(f.dm,f.campaign,1,viewer.getUuid(),false);var owners=new ArrayList<UUID>();
            for(int i=0;i<17;i++){
                UUID owner=UUID.randomUUID();owners.add(owner);f.repo.invite(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),owner,false);
                var id=f.repo.submit(new CharacterRepository.Principal(owner,false),f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,owner,owner);
            }
            f.actor(4,4,true);var policy=new UiWorkflowStore.Policy(f.campaign,0,UiWorkflowStore.HpVisibility.PRIVATE,16);
            var pages=new dev.tacticalcombat.client.ui.PartyPages();JsonObject combined=null;
            for(int i=0;i<3;i++){var page=UiPresentation.party(viewer,f.campaign,i,policy);check(page.get("total").getAsInt()==17,"NPC excluded from paginated total");combined=pages.accept(page);}
            check(combined.getAsJsonArray("members").size()==17,"all offline bound player identities aggregate");
            for(var row:combined.getAsJsonArray("members"))check(!row.getAsJsonObject().has("hp")&&!row.getAsJsonObject().get("online").getAsBoolean(),"private HP omitted and offline reported");
            var first=UiPresentation.party(viewer,f.campaign,0,policy);pages.accept(first);f.repo.unbind(f.dm,owners.getFirst());
            check(pages.accept(UiPresentation.party(viewer,f.campaign,1,policy))==null&&pages.restarted(),"unbind invalidates in-progress roster");
            var bars=UiPresentation.party(viewer,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,1,UiWorkflowStore.HpVisibility.BARS,16));
            check(!bars.get("rosterToken").equals(first.get("rosterToken"))&&bars.getAsJsonArray("members").get(0).getAsJsonObject().has("healthFraction"),"sharing policy has new token and filtered fields");
            var exact=UiPresentation.party(viewer,f.campaign,0,new UiWorkflowStore.Policy(f.campaign,2,UiWorkflowStore.HpVisibility.EXACT,16));check(exact.getAsJsonArray("members").get(0).getAsJsonObject().has("hp"),"exact policy supplies HP");
        } finally {ctx.getWorld().getServer().getPlayerManager().remove(viewer);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void tacticalEventsObserveAppliedHealthNotRejectedDamage(TestContext ctx) throws Exception {
        var observer=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            var a=f.actor(2,2,false);var b=f.actor(3,2,false);
            f.repo.invite(f.dm,f.campaign,1,observer.getUuid(),false);CharacterService.select(observer,f.campaign);
            observer.setPosition(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+3.5);
            var combat=Combat.start(ctx.getWorld(),List.of(observer),List.of(a,b),new CharacterService.Context(f.campaign,RulesProfiles.TACTICAL));f.encounters.add(combat);
            while(combat.current().entity==observer)combat.endTurn();
            int turns=UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.TURN).events().size();
            var active=combat.current().entity;var target=active==a?b:a;float before=target.getHealth();
            var damage=ctx.getWorld().getDamageSources().mobAttack(active);
            check(!target.damage(damage,2)&&target.getHealth()==before,"unpermitted damage rejected");
            check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.DAMAGE).events().isEmpty(),"rejected vanilla damage emits no success");
            check(CombatActions.melee(active,target,()->target.damage(damage,2)),"accepted damage applies");
            check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.DAMAGE).events().size()==1,"actual vanilla health change emits once");
            check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.ATTACK).events().size()==1,"accepted Tactical action recorded");
            check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.TURN).events().size()==turns,"Tactical first turn recorded");
            target.heal(1);check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.HEALING).events().size()==1,"actual vanilla healing emits once");
            combat.endTurn();check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.TURN).events().size()==turns+1,"next Tactical turn recorded");
            target.timeUntilRegen=0;target.damage(ctx.getWorld().getDamageSources().generic(),1000);
            check(UiEvents.SESSION.query(f.repo,f.dm,f.campaign,0,UiEvents.Kind.ZERO_HP).events().size()==1,"applied vanilla lethal transition recorded");
        } finally {CharacterService.forget(observer.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(observer);}
        ctx.complete();
    }
}
