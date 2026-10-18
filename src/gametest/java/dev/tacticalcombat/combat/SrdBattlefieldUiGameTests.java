package dev.tacticalcombat.combat;
import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.client.*;
import dev.tacticalcombat.net.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

public class SrdBattlefieldUiGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void oneManagerPlayerAndHuskContextualPreviewCommit(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(player);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);f.repo.invite(f.dm,f.campaign,2,player.getUuid(),true);CharacterService.select(player,f.campaign);var who=CharacterService.principal(player);
            var id=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());player.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            var husk=EntityType.HUSK.create(ctx.getWorld());husk.setAiDisabled(true);husk.refreshPositionAndAngles(f.origin.getX()+2.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);ctx.getWorld().spawnEntity(husk);f.entities.add(husk);f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",husk.getUuid());
            var dice=new Dice(20,1,15);var e=begin(f,dice,player,husk);var before=CharacterService.srdStats(husk,f.campaign);var choice=SrdAttackChoices.forWeapon("UNARMED",null);check(choice.size()==1,"unarmed needs no choices page");
            var args=SrdBattlefieldActions.preview(e.snapshot(who),husk.getUuidAsString(),choice.getFirst());UUID request=UUID.randomUUID();
            var first=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),request,"preview",args.toString()));var data=JsonParser.parseString(first.json()).getAsJsonObject();var p=data.getAsJsonObject("preview");
            check(first.code().equals("PREVIEW")&&data.get("previewRequest").getAsString().equals(request.toString()),"server correlates deliberate preview request");
            check(p.get("actor").getAsString().equals(player.getUuidAsString())&&p.get("target").getAsString().equals(husk.getUuidAsString())&&p.get("revision").getAsLong()==e.revision(),"preview scope is authoritative");
            check(p.get("rollMode").getAsString().equals("NORMAL")&&dice.rolls==2&&!e.actionUsed()&&before.equals(CharacterService.srdStats(husk,f.campaign)),"preview has no dice, action or HP cost");
            var retry=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"preview",args.toString()));var fresh=JsonParser.parseString(retry.json()).getAsJsonObject().getAsJsonObject("preview");
            var old=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"confirm",SrdBattlefieldActions.confirmation(p).toString()));check(old.code().equals("REJECTED")&&!e.actionUsed()&&dice.rolls==2,"new selection invalidates old intent without spending");
            var confirmArgs=SrdBattlefieldActions.confirmation(fresh);var accepted=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"confirm",confirmArgs.toString()));check(accepted.code().equals("RESULT")&&e.actionUsed()&&dice.rolls==3,"one explicit confirmation commits one attack");
            var after=CharacterService.srdStats(husk,f.campaign);var replay=SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"confirm",confirmArgs.toString()));check(replay.code().equals("RESULT")&&dice.rolls==3&&after.equals(CharacterService.srdStats(husk,f.campaign)),"late confirmation retrieves receipt, never spends again");check(JsonParser.parseString(replay.json()).getAsJsonObject().get("replayed").getAsBoolean(),"retrieved result is explicitly marked, so native effects are not replayed");
            e.endTurn(who);var state=e.snapshot(who);check(state.get("active").getAsString().equals(husk.getUuidAsString())&&SrdViewPolicy.canControl(state),"same manager may operate next prepared Husk without ownership transfer");
            var npc=state.getAsJsonArray("actors").get(1).getAsJsonObject();var attack=SrdAttackChoices.forWeapon(npc.getAsJsonArray("weapons").get(0).getAsString(),npc.get("monsterAbility").getAsString()).getFirst();
            check(SrdNetworking.handle(player,new SrdRequestPayload(e.id(),e.turn(),UUID.randomUUID(),"preview",SrdBattlefieldActions.preview(state,player.getUuidAsString(),attack).toString())).code().equals("PREVIEW"),"next actor uses its own supported attack and authority");
        }finally{CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void actualTieSequencePublishedAndPreserved(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(4,1,true);var e=begin(f,new Dice(10,10),a,b);e.tie(f.dm,List.of(b.getUuid(),a.getUuid()));
            var state=e.snapshot(f.dm);var order=state.getAsJsonArray("initiativeOrder");check(order.get(0).getAsString().equals(b.getUuidAsString())&&order.get(1).getAsString().equals(a.getUuidAsString()),"explicit tie ordering is published");
            check(SrdBattlefieldPresentation.initiative(state).getFirst().get("id").getAsString().equals(b.getUuidAsString()),"UI consumes actual sequence rather than UUID/name sort");e.endTurn(f.dm);check(e.snapshot(f.dm).get("active").getAsString().equals(a.getUuidAsString()),"highlight follows same next-actor sequence");
        }ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void dodgeRollPresentationUsesAcceptedModeAndFaces(TestContext ctx)throws Exception{
        try(var f=new Fixture(ctx)){
            var a=f.actor(1,1,true);var b=f.actor(2,1,true);var dice=new Dice(20,1,18,2);var e=begin(f,dice,a,b);SrdDodgeGameTests.dodge(e,f.dm);e.endTurn(f.dm);
            var intent=e.preview(f.dm,a.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);var result=e.confirm(f.dm,intent.id());var data=SrdNetworking.result(result.plan,f.dm);
            check(data.get("keep").getAsInt()==2&&data.getAsJsonArray("faces").size()==2&&data.get("chosen").getAsInt()==2,"accepted Dodge disadvantage supplies immutable two dice and kept face");
            var text=SrdAttackFeedback.describe(data,e.snapshot(f.dm).getAsJsonArray("actors"));check(text.contains("Disadvantage")&&text.contains("kept 2")&&!text.contains("Dodge caused"),"presentation explains result without guessing source");
        }ctx.complete();
    }
}
