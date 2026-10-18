package dev.tacticalcombat.mixin.client;

import dev.tacticalcombat.client.SrdMotion;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Supplies walking parameters to vanilla models/features for players AND NPCs, without entity mutation. */
@Mixin(LivingEntityRenderer.class)
public abstract class SrdLivingRenderMixin {
    private static final String RENDER="render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V";
    @ModifyArgs(method=RENDER,at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/LivingEntityRenderer;setupTransforms(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/util/math/MatrixStack;FFFF)V"))
    private void cubetable$facing(Args args){var p=SrdMotion.rendering?SrdMotion.pose(args.get(0)):null;if(p!=null)args.set(3,p.yaw());}
    @ModifyArgs(method=RENDER,at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/model/EntityModel;animateModel(Lnet/minecraft/entity/Entity;FFF)V"))
    private void cubetable$animate(Args args){var p=SrdMotion.rendering?SrdMotion.pose(args.get(0)):null;if(p!=null){args.set(1,p.phase());args.set(2,p.strength());}}
    @ModifyArgs(method=RENDER,at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/model/EntityModel;setAngles(Lnet/minecraft/entity/Entity;FFFFF)V"))
    private void cubetable$angles(Args args){var p=SrdMotion.rendering?SrdMotion.pose(args.get(0)):null;if(p!=null){args.set(1,p.phase());args.set(2,p.strength());args.set(4,0f);}}
    @ModifyArgs(method=RENDER,at=@At(value="INVOKE",target="Lnet/minecraft/client/render/entity/feature/FeatureRenderer;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/Entity;FFFFFF)V"))
    private void cubetable$features(Args args){var p=SrdMotion.rendering?SrdMotion.pose(args.get(3)):null;if(p!=null){args.set(4,p.phase());args.set(5,p.strength());args.set(8,0f);}}
}
