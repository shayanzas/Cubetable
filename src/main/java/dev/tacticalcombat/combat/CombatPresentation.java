package dev.tacticalcombat.combat;

import dev.tacticalcombat.character.CharacterService;
import dev.tacticalcombat.uiapi.UiEvents;
import net.minecraft.entity.LivingEntity;
import java.util.*;

/** Observes completed vanilla health changes only; never grants permission or changes health. */
public final class CombatPresentation {
    private CombatPresentation() {}
    public static void health(Combat encounter,LivingEntity entity,float before,float absorption) {
        if(encounter==null || encounter.campaign()==null || entity.getWorld().isClient)return;
        try {
            float after=entity.getHealth(),absorbed=absorption-entity.getAbsorptionAmount();
            float loss=before-after;
            if(loss==0 && absorbed<=0)return;
            var recipients=new HashSet<UUID>();recipients.add(entity.getUuid());
            var repo=CharacterService.repository();var id=repo.state().bindings().get(entity.getUuid());
            if(id!=null){var c=repo.character(id);recipients.add(c.owner());recipients.add(repo.controller(id));}
            UUID mutation=UUID.randomUUID();
            UiEvents.SESSION.safePublish(mutation,encounter.campaign(),encounter.id(),0,loss<0?UiEvents.Kind.HEALING:UiEvents.Kind.DAMAGE,List.of(entity.getUuid()),
                loss<0?"Tactical vanilla healing applied: "+(-loss):"Tactical vanilla HP loss: "+loss+"; absorption spent: "+Math.max(0,absorbed),null,UiEvents.Visibility.RECIPIENTS_AND_DM,recipients);
            if(before>0 && after<=0)UiEvents.SESSION.safePublish(UiEvents.derived(mutation,"zero"),encounter.campaign(),encounter.id(),0,UiEvents.Kind.ZERO_HP,List.of(entity.getUuid()),"Tactical vanilla zero HP",null,UiEvents.Visibility.RECIPIENTS_AND_DM,recipients);
        }catch(RuntimeException failure){dev.tacticalcombat.TacticalCombatMod.LOGGER.warn("Could not record Tactical health event",failure);}
    }
}
