package dev.tacticalcombat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;

/** Depth-tested Stage B anchor overlay. No see-through geometry or Tactical grid changes. */
public final class SrdGridRenderer {
    private SrdGridRenderer() {}
    public static void render(WorldRenderContext ctx) {
        var grid=ClientSrdCombat.grid; var matrices=ctx.matrixStack();
        if(grid==null || matrices==null || grid.reachable().isEmpty()) return;
        var camera=ctx.camera().getPos(); matrices.push(); matrices.translate(-camera.x,-camera.y,-camera.z);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest(); RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var buffer=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        var matrix=matrices.peek().getPositionMatrix();
        for(var cell:grid.reachable().keySet()) {
            float x=cell.x(),z=cell.z(),y=(float)cell.y()+.035f;
            int color=cell.equals(grid.start())?0x88FFFFFF:0x66376EF5;
            buffer.vertex(matrix,x+.03f,y,z+.03f).color(color);
            buffer.vertex(matrix,x+.97f,y,z+.03f).color(color);
            buffer.vertex(matrix,x+.97f,y,z+.97f).color(color);
            buffer.vertex(matrix,x+.03f,y,z+.97f).color(color);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); matrices.pop();
    }
}
