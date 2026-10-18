package dev.tacticalcombat.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import dev.tacticalcombat.rules.srd.SrdMovement;

/** Uses the rendered projection and camera rotation. Solids occlude picking, even when visually faded. */
public final class SrdWorldPicker {
    private static final Matrix4f inverseProjection=new Matrix4f(),projection=new Matrix4f();
    private static boolean ready;
    public record Pick(String actor,SrdMovement.Cell cell,Vec3d unavailable) {}
    public static void projection(Matrix4f matrix){projection.set(matrix);inverseProjection.set(matrix).invert();ready=true;}
    /** Visible entity labels only, with solid-terrain occlusion independent of camera fade. */
    public static int[] label(MinecraftClient mc,net.minecraft.entity.Entity entity){
        if(!ready||mc.world==null||mc.player==null)return null;
        var camera=mc.gameRenderer.getCamera();var eye=entity.getPos().add(0,entity.getHeight()*.85,0);
        var hit=mc.world.raycast(new RaycastContext(camera.getPos(),eye,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        if(hit.getType()==HitResult.Type.BLOCK)return null;
        var relative=entity.getPos().add(0,entity.getHeight()+.3,0).subtract(camera.getPos());
        var v=new Vector3f((float)relative.x,(float)relative.y,(float)relative.z).rotate(new Quaternionf(camera.getRotation()).conjugate());
        var clip=new Vector4f(v,1).mul(projection);if(clip.w<=0)return null;
        float x=clip.x/clip.w,y=clip.y/clip.w;if(Math.abs(x)>1||Math.abs(y)>1)return null;
        return new int[]{(int)((x+1)*.5*mc.getWindow().getScaledWidth()),(int)((1-y)*.5*mc.getWindow().getScaledHeight())};
    }
    public static Pick pick(MinecraftClient mc,double x,double y){
        if(!ready || mc.world==null || mc.player==null || !SrdCamera.engaged())return new Pick(null,null,null);
        var camera=mc.gameRenderer.getCamera();var origin=camera.getPos();
        var v=new Vector4f((float)(2*x/mc.getWindow().getScaledWidth()-1),(float)(1-2*y/mc.getWindow().getScaledHeight()),-1,1).mul(inverseProjection);
        var direction=new Vector3f(v.x/v.w,v.y/v.w,v.z/v.w).normalize().rotate(camera.getRotation());
        var d=new Vec3d(direction.x,direction.y,direction.z);var end=origin.add(d.multiply(96));
        var hit=mc.world.raycast(new RaycastContext(origin,end,RaycastContext.ShapeType.COLLIDER,RaycastContext.FluidHandling.NONE,mc.player));
        double distance=hit.getType()==HitResult.Type.BLOCK?hit.getPos().distanceTo(origin):96;
        String selected=null;
        for(var value:ClientSrdCombat.actors()){
            var a=value.getAsJsonObject();var entity=mc.world.getEntityById(a.get("entity").getAsInt());
            if(entity==null || !entity.getUuidAsString().equals(a.get("id").getAsString()))continue;
            var point=entity.getBoundingBox().raycast(origin,end);
            if(point.isPresent() && point.get().distanceTo(origin)<distance){distance=point.get().distanceTo(origin);selected=a.get("id").getAsString();}
        }
        var grid=ClientSrdCombat.grid;
        var cell=selected==null && grid!=null?SrdWorldPick.cell(grid.reachable().keySet(),origin.x,origin.y,origin.z,d.x,d.y,d.z,distance):null;
        return new Pick(selected,cell,selected==null&&cell==null&&hit.getType()==HitResult.Type.BLOCK&&hit.getSide()==net.minecraft.util.math.Direction.UP?hit.getPos():null);
    }
}
