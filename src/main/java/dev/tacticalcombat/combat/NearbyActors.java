package dev.tacticalcombat.combat;

import net.minecraft.entity.LivingEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import java.util.*;

/** Distance ordering precedes filtering/truncation; UUID breaks equal-distance ties. */
public final class NearbyActors {
    public enum Binding { ALL, BOUND, UNBOUND, ELIGIBLE }
    public enum Kind { ALL, PLAYER, NPC }
    public static List<LivingEntity> sorted(ServerPlayerEntity player,int radius){
        var actors=new ArrayList<>(player.getServerWorld().getEntitiesByClass(LivingEntity.class,player.getBoundingBox().expand(radius),e->e.isAlive()&&!e.isSpectator()&&e.squaredDistanceTo(player)<=radius*radius));
        if(!actors.contains(player))actors.add(player);
        actors.sort(Comparator.comparingDouble((LivingEntity e)->e.squaredDistanceTo(player)).thenComparing(e->e.getUuid().toString()));return actors;
    }
    public static boolean matches(Binding filter,Kind kind,boolean bound,boolean eligible,boolean player){
        return (kind==Kind.ALL||(kind==Kind.PLAYER)==player)&&(switch(filter){case ALL->true;case BOUND->bound;case UNBOUND->!bound;case ELIGIBLE->eligible;});
    }
    private NearbyActors(){}
}
