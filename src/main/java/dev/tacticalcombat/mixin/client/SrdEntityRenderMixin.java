package dev.tacticalcombat.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.tacticalcombat.client.SrdMotion;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Render-only translation including features/shadow; never changes collision or network position. */
@Mixin(EntityRenderDispatcher.class)
public abstract class SrdEntityRenderMixin {
    @Shadow public Camera camera;
    @WrapMethod(method="render")
    private void cubetable$step(Entity entity,double x,double y,double z,float yaw,float delta,MatrixStack matrices,VertexConsumerProvider consumers,int light,Operation<Void> original){
        var pose=SrdMotion.rendering?SrdMotion.pose(entity):null;
        if(pose==null){original.call(entity,x,y,z,yaw,delta,matrices,consumers,light);return;}
        matrices.push();
        try{var p=pose.point();var c=camera.getPos();matrices.translate(p.x()-c.x-x,p.y()-c.y-y,p.z()-c.z-z);
            original.call(entity,x,y,z,yaw,delta,matrices,consumers,light);
        }finally{matrices.pop();}
    }
}
