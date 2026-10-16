package dev.tacticalcombat.combat;

import dev.tacticalcombat.dice.*;
import dev.tacticalcombat.net.DiceRollPayload;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;

public final class ManualDiceGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void toolbarUsesServerDiceBoundsAndD20Keep(TestContext ctx)throws Exception{
        var player=ctx.createMockCreativeServerPlayerInWorld();try{
            var addon=net.fabricmc.fabric.impl.networking.server.ServerNetworkingImpl.getAddon(player.networkHandler);var field=net.fabricmc.fabric.impl.networking.AbstractChanneledNetworkAddon.class.getDeclaredField("sendableChannels");field.setAccessible(true);@SuppressWarnings("unchecked") var channels=(Set<net.minecraft.util.Identifier>)field.get(addon);channels.add(DiceRollPayload.ID.id());
            for(var type:DiceType.values()){
                DiceService.forget(player.getUuid());PacketProbe.clear();PacketProbe.enabled=true;DiceService.roll(player,"Manual dice",type,1,3,1);
                var result=PacketProbe.packets.stream().filter(p->p instanceof DiceRollPayload).map(p->(DiceRollPayload)p).findFirst().orElseThrow();check(result.type()==type&&result.results().length==(type==DiceType.D20?2:1),"Server supports every toolbar die with d20-only advantage");check(Arrays.stream(result.results()).allMatch(face->face>=1&&face<=type.sides)&&result.keep()==(type==DiceType.D20?1:0),"Server owns bounded faces and keep policy");
            }
            DiceService.forget(player.getUuid());PacketProbe.clear();PacketProbe.enabled=true;DiceService.roll(player,"Manual dice",DiceType.D20,999,Integer.MAX_VALUE,999);var result=PacketProbe.packets.stream().filter(p->p instanceof DiceRollPayload).map(p->(DiceRollPayload)p).findFirst().orElseThrow();check(result.results().length==10&&result.modifier()==999&&result.keep()==0,"Malformed count/modifier/mode cannot escape existing engine limits");check(result.total()==Arrays.stream(result.results()).sum()+999,"Result uses server faces and bounded manual modifier");
        }finally{PacketProbe.clear();DiceService.forget(player.getUuid());player.getServer().getPlayerManager().remove(player);}ctx.complete();
    }
}
