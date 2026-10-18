package dev.tacticalcombat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.*;

/** SRD presentation adapter. Tactical owns priority and its camera state is never modified here. */
public final class SrdCamera {
    private static float blend,previousBlend,yaw,previousYaw;
    static float yawTarget,pitch=55;
    private static double zoom=18,previousZoom=18;
    static double zoomTarget=18;
    private static Vec3d focus=Vec3d.ZERO,previousFocus=Vec3d.ZERO;
    static Vec3d pan=Vec3d.ZERO;
    private static String followed="";
    private static boolean engaged;
    private SrdCamera() {}
    public static void clear(){blend=previousBlend=0;engaged=false;followed="";pan=Vec3d.ZERO;}
    public static Entity activeEntity(MinecraftClient client) {
        if(!ClientSrdCombat.active() || client.world==null || !SrdViewPolicy.cameraEligible(ClientSrdCombat.state,client.world.getRegistryKey().getValue().toString(),ClientCombatState.active))return null;
        var a=ClientSrdCombat.current();if(a==null)return null;
        var entity=client.world.getEntityById(a.get("entity").getAsInt());
        return entity!=null && entity.getUuidAsString().equals(a.get("id").getAsString())?entity:null;
    }
    public static boolean engaged(){return engaged;}
    public static void tick(MinecraftClient client) {
        var entity=activeEntity(client);
        boolean want=entity!=null && !ClientCombatState.active && client.player!=null;
        previousBlend=blend;blend=MathHelper.clamp(blend+(want?.04f:-.04f),0,1);
        if(!want){engaged=false;return;}
        if(!engaged){focus=previousFocus=client.player.getEyePos();yaw=previousYaw=client.player.getYaw();yawTarget=Math.round(yaw/45)*45;pan=Vec3d.ZERO;}
        engaged=true;
        String identity=ClientSrdCombat.encounter+":"+entity.getUuidAsString();
        if(!identity.equals(followed)){followed=identity;pan=Vec3d.ZERO;}
        previousFocus=focus;focus=focus.lerp(SrdMotion.position(entity,1).add(0,.9,0).add(pan),.2);
        previousYaw=yaw;yaw+=MathHelper.wrapDegrees(yawTarget-yaw)*.25f;
        previousZoom=zoom;zoom+=(zoomTarget-zoom)*.25;
    }
    private static boolean tactical(){return ClientCombatState.active || ClientCombatState.cameraBlend(1)>0;}
    public static float cameraBlend(float delta){if(tactical())return ClientCombatState.cameraBlend(delta);float t=MathHelper.lerp(delta,previousBlend,blend);return t*t*(3-2*t);}
    public static float lerpedYaw(float delta){return tactical()?ClientCombatState.lerpedYaw(delta):previousYaw+MathHelper.wrapDegrees(yaw-previousYaw)*delta;}
    public static float pitch(){return tactical()?ClientCombatState.camPitch:pitch;}
    public static double lerpedZoom(float delta){return tactical()?ClientCombatState.lerpedZoom(delta):MathHelper.lerp(delta,previousZoom,zoom);}
    public static Vec3d lerpedFocus(float delta){return tactical()?ClientCombatState.lerpedFocus(delta):previousFocus.lerp(focus,delta);}
    public static Entity followedEntity(MinecraftClient client){return tactical()?ClientCombatState.activeEntity(client):activeEntity(client);}
}
