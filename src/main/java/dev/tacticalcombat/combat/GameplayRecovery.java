package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.uiapi.UiApiService;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;
import static dev.tacticalcombat.uiapi.UiApiService.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Explicit DM review after encounter closure. Never resumes movement or applies a stored roll. */
public final class GameplayRecovery {
    public static JsonObject handle(ServerPlayerEntity player,UiApiService.Operation operation,JsonObject args)throws java.io.IOException{
        require(player.getServer().isOnThread(),"Server thread required");var repo=CharacterService.repository();var who=CharacterService.principal(player);UUID campaign=CharacterService.selected(player);
        require(repo.manages(who,campaign),"Campaign manager permission required");var out=new JsonObject();
        if(operation==Operation.GAMEPLAY_CANCEL){
            fields(args,"operation reason");var receipt=repo.visibleReceipt(who,id(args,"operation"));require(receipt.plan().campaign().equals(campaign),"Selected campaign permission required");
            require(SrdNetworking.find(receipt.plan().encounter())==null&&receipt.plan().participants().stream().noneMatch(p->CombatManager.isInCombat(p.actor())),"End the encounter before resolving its prepared operation");
            var saved=repo.cancelGameplay(who,receipt.plan().operation(),string(args,"reason"));out.addProperty("status",saved.status().name());return out;
        }
        require(operation==Operation.GAMEPLAY_RECOVERY,"Unsupported recovery operation");fields(args,"page");int page=integer(args,"page");require(page>=0&&page<128,"Invalid recovery page");
        var receipts=repo.state().receipts().values().stream().filter(r->r.plan().campaign().equals(campaign)).sorted(Comparator.comparing((GameplayReceipt r)->r.status()!=GameplayReceipt.Status.PREPARED).thenComparing(r->r.plan().operation().toString())).toList();var rows=new JsonArray();
        for(var receipt:receipts.stream().skip(page*8L).limit(8).toList()){
            var plan=receipt.plan();var row=new JsonObject();row.addProperty("operation",plan.operation().toString());row.addProperty("status",receipt.status().name());row.addProperty("weapon",plan.attack().weapon().name());row.addProperty("mode",plan.attack().mode().name());row.addProperty("turn",plan.turn());row.addProperty("encounter",plan.encounter().toString());
            row.addProperty("attacker",name(repo.character(plan.attacker().character()).sheet()));row.addProperty("target",name(repo.character(plan.target().character()).sheet()));
            row.addProperty("outcome",(plan.outcome().hit()?"Hit":"Miss")+"; fixed d20 "+plan.outcome().roll().faces()+" total "+plan.outcome().roll().total()+"; target HP "+plan.target().before().hp()+" → "+plan.target().after().hp());
            row.addProperty("resolution",receipt.resolution());row.addProperty("canCancel",receipt.status()==GameplayReceipt.Status.PREPARED&&SrdNetworking.find(plan.encounter())==null&&plan.participants().stream().noneMatch(p->CombatManager.isInCombat(p.actor())));rows.add(row);
        }
        EncounterLauncher.panel(player,out,campaign,null,true,false);out.add("operations",rows);out.addProperty("hasMore",receipts.size()>(page+1)*8);out.addProperty("stored",repo.state().receipts().size());out.addProperty("limit",CharacterRepository.MAX_GAMEPLAY_RECEIPTS);return out;
    }
}
