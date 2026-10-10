package dev.tacticalcombat.mixin;

import dev.tacticalcombat.grid.SrdTerrain;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe chunk mutations even when callers suppress neighbor updates. No tracking exists without pending grids. */
@Mixin(WorldChunk.class)
public abstract class GridBlockChangeMixin {
    @Shadow public abstract World getWorld();
    @Inject(method="setBlockState",at=@At("RETURN"))
    private void changed(BlockPos pos,BlockState state,boolean moved,CallbackInfoReturnable<BlockState> cir) {
        if(!getWorld().isClient && cir.getReturnValue()!=null) SrdTerrain.changed(getWorld(),pos);
    }
}
