package dev.tacticalcombat.client;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;

/** One active actor, scoped to the authorized encounter/turn. No entity setters or packets. */
public final class SrdMotion {
    private static String scope="",actor="";
    private static SrdMotionTrack track;
    public static boolean rendering;
    private static boolean animated;
    private static double now(){return System.nanoTime()/1_000_000d;}
    public static void clear(){scope=actor="";track=null;animated=false;rendering=false;}
    private static SrdMotionTrack.Point point(JsonArray a){if(a.size()!=3)throw new IllegalArgumentException("Invalid step");return new SrdMotionTrack.Point(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    public static void accept(String encounter,JsonObject state){
        if(!state.get("state").getAsString().equals("ACTIVE")){clear();return;}
        var current=SrdViewPolicy.current(state);if(current==null){clear();return;}
        String next=encounter+":"+state.get("turn");
        var end=new SrdMotionTrack.Point(current.get("x").getAsDouble(),current.get("y").getAsDouble(),current.get("z").getAsDouble());
        var motion=state.has("motion")?state.getAsJsonObject("motion"):null;
        long seq=motion==null?0:motion.get("sequence").getAsLong();
        if(!next.equals(scope)||!actor.equals(current.get("id").getAsString())){
            animated=false;scope=next;actor=current.get("id").getAsString();var world=MinecraftClient.getInstance().world;var entity=world==null?null:world.getEntityById(current.get("entity").getAsInt());
            float yaw=entity instanceof net.minecraft.entity.LivingEntity living?living.bodyYaw:0;
            track=new SrdMotionTrack(end,seq,yaw);return; // Refresh/reconnect never replays history.
        }
        if(motion!=null){var to=point(motion.getAsJsonArray("to"));if(!to.equals(end)){clear();return;}
            animated=track.accept(seq,point(motion.getAsJsonArray("from")),to,motion.get("ticks").getAsInt(),now())||animated;}
    }
    public static SrdMotionTrack.Pose pose(Entity entity){
        var mc=MinecraftClient.getInstance();
        if(!animated||track==null||ClientCombatState.active||mc.world==null||entity.getWorld()!=mc.world||!entity.getUuidAsString().equals(actor)||SrdCamera.activeEntity(mc)!=entity)return null;
        return track.sample(now());
    }
    public static Vec3d position(Entity entity,float delta){var p=pose(entity);return p==null?entity.getLerpedPos(delta):new Vec3d(p.point().x(),p.point().y(),p.point().z());}
}
