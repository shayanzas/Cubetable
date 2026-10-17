package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.srd.*;
import dev.tacticalcombat.uiapi.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.io.IOException;
import static dev.tacticalcombat.uiapi.UiApiService.*;
import static dev.tacticalcombat.uiapi.SheetSections.require;

/** Server world gate for atomic campaign NPC preparation/approval/binding. Never guesses statistics from entity type. */
public final class MonsterPreparation {
    public static JsonObject handle(ServerPlayerEntity p,Operation op,JsonObject args)throws IOException {
        var repo=CharacterService.repository();var who=CharacterService.principal(p);var campaign=CharacterService.selected(p);
        require(repo.manages(who,campaign),"Only campaign DMs prepare NPCs");
        fields(args,op==Operation.MONSTER_OPTIONS?"actor":"actor source operation");
        var entity=p.getServerWorld().getEntity(id(args,"actor"));
        require(entity instanceof MobEntity&&entity.isAlive()&&!entity.isRemoved()&&entity.squaredDistanceTo(p)<=32*32&&!entity.hasVehicle()&&!entity.hasPassengers(),"Loaded living NPC within 32 blocks required");
        require(!CombatManager.isInCombat(entity),"NPC already claimed by combat");
        var bound=CharacterService.bound((MobEntity)entity);var out=new JsonObject();
        out.addProperty("actorName",entity.getName().getString());out.addProperty("entityType",net.minecraft.registry.Registries.ENTITY_TYPE.getId(entity.getType()).toString());
        if(op==Operation.MONSTER_OPTIONS){require(bound==null,"NPC already bound");var rows=new JsonArray();for(var profile:MonsterProfiles.ALL){MonsterProfiles.verify(profile);var row=new Gson().toJsonTree(profile).getAsJsonObject();rows.add(row);}out.add("monsters",rows);}
        else {var operation=id(args,"operation");require(bound==null||bound.id().equals(operation),"NPC already bound to another character");var result=repo.prepareMonster(who,campaign,operation,string(args,"source"),entity.getUuid());out.addProperty("character",result.toString());out.addProperty("bound",entity.getUuid().equals(repo.binding(result)));out.addProperty("approved",repo.character(result).approved());out.addProperty("limitations",MonsterProfiles.get(string(args,"source")).limitations());}
        return out;
    }
    private MonsterPreparation(){}
}
