package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.EntityType;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

/** Real world/repository regression for the reported one-account, two-actor workflow. */
public class SrdWorldInteractionGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void managerMovesFiveFiveAttacksThenRemainingAndControlsHusk(TestContext ctx) throws Exception {
        var player=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)) {
            capability(player);var who=CharacterService.principal(player);
            f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);
            f.repo.invite(f.dm,f.campaign,2,player.getUuid(),true);CharacterService.select(player,f.campaign);
            UUID id=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());
            f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,player.getUuid(),player.getUuid());
            player.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            var husk=EntityType.HUSK.create(ctx.getWorld());husk.setAiDisabled(true);
            husk.refreshPositionAndAngles(f.origin.getX()+4.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            ctx.getWorld().spawnEntity(husk);f.entities.add(husk);
            f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",husk.getUuid());
            var dice=new Dice(20,1,15);var e=begin(f,dice,player,husk);long turn=e.turn();
            check(e.current()==player,"first active actor is player");
            for(int x=2;x<=3;x++) {
                var cell=new SrdMovement.Cell(f.origin.getX()+x,f.origin.getZ()+1,f.origin.getY()+1);
                check(e.grid(who).reachable().containsKey(cell),"fresh discovery contains next five-foot step");
                check(e.move(who,UUID.randomUUID(),cell).accepted(),"split movement accepted");e.tick();
                check(e.grid(who).remainingFeet()==30-(x-1)*5 && e.turn()==turn,"five plus five retains same turn and allowance");
            }
            var intent=e.preview(who,husk.getUuid(),SrdEquipment.Weapon.UNARMED,SrdCharacter.Ability.STR,false);
            var result=e.confirm(who,intent.id());
            check(result.state==SrdEncounter.TransactionState.COMMITTED && e.grid(who).remainingFeet()==20,"attack preserves twenty remaining feet");
            var feedback=SrdNetworking.result(result.plan,who);
            check(feedback.get("actor").getAsString().equals(player.getUuidAsString())&&feedback.get("target").getAsString().equals(husk.getUuidAsString()),"outcome carries exact authoritative actor identities");
            check(feedback.get("hpBefore").getAsInt()==22&&feedback.get("hpAfter").getAsInt()==21&&feedback.get("rolledDamage").getAsInt()==1,"DM receives exact committed damage and HP");
            var end=new SrdMovement.Cell(f.origin.getX()+3,f.origin.getZ()+5,f.origin.getY()+1);
            var after=e.move(who,UUID.randomUUID(),end);check(after.accepted(),"move after attack accepted: "+after.message());
            for(int i=0;i<4;i++)e.tick();
            check(e.grid(who).remainingFeet()==0 && e.turn()==turn && e.actionUsed(),"remaining distance spent without resetting action or turn");
            check(CharacterService.srdStats(husk,f.campaign).stats().hp()==21,"verified source NPC HP committed");
            e.endTurn(who);check(e.current()==husk && e.turn()!=turn,"same account switches to NPC turn");
            check(e.snapshot(who).getAsJsonArray("actors").asList().stream().allMatch(a->a.getAsJsonObject().get("controlled").getAsBoolean()),"DM controls both independently bound actors");
            check(e.grid(who).remainingFeet()==20,"Zombie stat block has its own twenty-foot speed");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),player.getUuid());
            rejects(()->e.grid(who),"revoked manager cannot control NPC even while still viewing own encounter");
            check(!SrdNetworking.result(result.plan,who).has("hpBefore")&&!SrdNetworking.result(result.plan,who).has("zeroHp"),"receipt presentation rechecks target privacy after DM revocation");
            check(!e.snapshot(who).getAsJsonArray("actors").get(1).getAsJsonObject().has("hp"),"revocation removes NPC private resources");
        } finally {CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void pacedStepsRevalidateDuringWaitWithoutRefundOrExtraSpending(TestContext ctx)throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,true);var b=f.actor(8,6,true);var e=begin(f,new Dice(20,1),a,b);
            var destination=new SrdMovement.Cell(f.origin.getX()+4,f.origin.getZ()+1,f.origin.getY()+1);
            check(!e.move(f.dm,UUID.randomUUID(),destination,0).accepted()&&!e.move(f.dm,UUID.randomUUID(),destination,7).accepted(),"forged cadence rejected");
            check(e.move(f.dm,UUID.randomUUID(),destination,4).accepted(),"bounded slow route accepted");e.tick();
            check(e.snapshot(f.dm).get("remainingFeet").getAsInt()==25,"first whole step charged once");
            var position=a.getPos();for(int i=0;i<3;i++)e.tick();
            check(a.getPos().equals(position)&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==25,"wait ticks neither move nor charge/refund");
            e.tick();check(e.snapshot(f.dm).get("remainingFeet").getAsInt()==20,"next whole step on requested cadence");
            ctx.getWorld().setBlockState(f.origin.add(4,1,1),net.minecraft.block.Blocks.STONE.getDefaultState(),3);
            ctx.getWorld().setBlockState(f.origin.add(4,2,1),net.minecraft.block.Blocks.STONE.getDefaultState(),3);
            e.tick();check(!e.snapshot(f.dm).get("moving").getAsBoolean()&&e.snapshot(f.dm).get("remainingFeet").getAsInt()==20,"late wall cancels even during wait with completed cost preserved");
        }ctx.complete();
    }

}
