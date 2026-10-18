package dev.tacticalcombat.combat;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.checks.CheckGateway;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.SrdAuthoring;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import java.util.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public class DmWorkspaceGameTests implements FabricGameTest {
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void workspaceReadsExposeOnlyAuthorizedPartyActorAndRollData(TestContext ctx)throws Exception {
        var p=ctx.createMockCreativeServerPlayerInWorld();var ordinary=ctx.createMockCreativeServerPlayerInWorld();
        try(var f=new Fixture(ctx)){
            capability(p);f.repo.profile(f.dm,f.campaign,1,RulesProfiles.SRD);f.repo.invite(f.dm,f.campaign,2,p.getUuid(),true);var who=CharacterService.principal(p);CharacterService.select(p,f.campaign);
            p.refreshPositionAndAngles(f.origin.getX()+1.5,f.origin.getY()+1,f.origin.getZ()+1.5,0,0);
            var pc=f.repo.submit(who,f.campaign,RulesProfiles.SRD,SrdAuthoring.template().toString());f.repo.approve(f.dm,pc,1);f.repo.bind(f.dm,pc,2,p.getUuid(),p.getUuid());
            var actor=f.actor(4,1,false);f.repo.prepareMonster(who,f.campaign,UUID.randomUUID(),"srd51:monsters:zombie",actor.getUuid());
            var party=EncounterLauncher.handle(p,DM_QUICK,args("page",0));check(party.get("campaignName").getAsString().equals("Controlled SRD"),"campaign name is server supplied");check(party.getAsJsonArray("characters").size()==2,"party includes player and independently prepared NPC");
            for(var v:party.getAsJsonArray("characters")){var c=v.getAsJsonObject();check(c.has("owner")&&c.has("revision")&&c.has("controllerName")&&c.has("description")&&c.has("temporaryHp"),"DM party card has authorized portrait/type/controller/temp HP metadata");}
            var discovery=EncounterLauncher.handle(p,ENCOUNTER_DISCOVER,args("binding","ALL","kind","ALL"));check(discovery.get("radius").getAsInt()==32&&discovery.has("originX")&&discovery.has("discovery"),"inline scene/discovery retains origin, radius and server token");
            check(EncounterLauncher.handle(p,ENCOUNTER_LIST,args()).getAsJsonArray("encounters").isEmpty(),"browsing does not start combat");
            var targets=CheckGateway.handle(p,CHECK_TARGETS,args());check(targets.getAsJsonArray("characters").size()==2&&targets.get("manager").getAsBoolean(),"inline roll targets are current authorized campaign characters");
            var receipts=CheckGateway.handle(p,CHECK_INBOX,args("page",0));check(receipts.getAsJsonArray("rolls").isEmpty(),"browsing inbox never executes dice");
            var before=f.repo.srdSnapshot(p.getUuid(),f.campaign);
            var spec=args("category","ABILITY","key","str","mode","NORMAL","purpose","Workspace private request","visibility","DM_ONLY","recipients",new com.google.gson.JsonArray(),"dc",15);
            var ids=new com.google.gson.JsonArray();ids.add(pc.toString());var requested=CheckGateway.handle(p,CHECK_REQUEST,args("characters",ids,"spec",spec));String roll=requested.getAsJsonArray("operations").get(0).getAsString();
            var accepted=CheckGateway.handle(p,CHECK_EXECUTE,args("operation",roll));check(accepted.has("result")&&!accepted.get("result").isJsonNull(),"explicit inline execution returns authorized result");
            check(CheckGateway.handle(p,CHECK_EXECUTE,args("operation",roll)).get("result").equals(accepted.get("result")),"same operation retrieves accepted outcome without another roll");
            check(f.repo.srdSnapshot(p.getUuid(),f.campaign).equals(before),"workspace character checks do not mutate combat resources");
            f.repo.invite(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),ordinary.getUuid(),false);CharacterService.select(ordinary,f.campaign);
            rejects(()->EncounterLauncher.handle(ordinary,DM_QUICK,args("page",0)),"ordinary member cannot obtain private party summaries");rejects(()->EncounterLauncher.handle(ordinary,ENCOUNTER_DISCOVER,args("binding","ALL","kind","ALL")),"ordinary member cannot discover DM binding data");
            rejects(()->CheckGateway.handle(ordinary,CHECK_TARGETS,args()),"ordinary member cannot enumerate DM roll targets");
            check(CheckGateway.handle(ordinary,CHECK_INBOX,args("page",0)).getAsJsonArray("rolls").isEmpty(),"DM-only accepted result is not sent to unrelated campaign members");
            f.repo.revokeDm(f.dm,f.campaign,f.repo.campaign(f.campaign).revision(),p.getUuid());
            rejects(()->EncounterLauncher.handle(p,DM_QUICK,args("page",0)),"revoked manager cannot refresh cached cards");rejects(()->EncounterLauncher.handle(p,QUICK_SHEET,args("character",pc)),"revocation blocks DM inspection even of previously loaded records");
        }finally{for(var player:List.of(p,ordinary)){CharacterService.forget(player.getUuid());ctx.getWorld().getServer().getPlayerManager().remove(player);}}
        ctx.complete();
    }
}
