package dev.tacticalcombat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;

/** Depth-tested Stage B anchor overlay. No see-through geometry or Tactical grid changes. */
public final class SrdGridRenderer {
    private SrdGridRenderer() {}
    public static void render(WorldRenderContext ctx) {
        if(SrdCamera.activeEntity(net.minecraft.client.MinecraftClient.getInstance())==null)return;
        SrdWorldPicker.projection(ctx.projectionMatrix());
        var grid=ClientSrdCombat.grid; var matrices=ctx.matrixStack();
        var client=net.minecraft.client.MinecraftClient.getInstance();
        var focus=client.world==null?null:client.world.getEntityById(ClientSrdCombat.focusEntity);
        if(matrices==null) return;
        var camera=ctx.camera().getPos(); matrices.push(); matrices.translate(-camera.x,-camera.y,-camera.z);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc(); RenderSystem.disableCull(); RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest(); RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var buffer=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);
        var matrix=matrices.peek().getPositionMatrix();
        if(grid!=null) for(var cell:grid.reachable().keySet()) {
            float x=cell.x(),z=cell.z(),y=(float)cell.y()+.035f;
            int color=cell.equals(grid.start())?0xAAFFFFFF:cell.equals(ClientSrdCombat.selected)?0xBBFFD060:ClientSrdCombat.movement.pathCells(ClientSrdCombat.selected).contains(cell)?0x99D9BB54:0x66376EF5;
            buffer.vertex(matrix,x+.03f,y,z+.03f).color(color);
            buffer.vertex(matrix,x+.97f,y,z+.03f).color(color);
            buffer.vertex(matrix,x+.97f,y,z+.97f).color(color);
            buffer.vertex(matrix,x+.03f,y,z+.97f).color(color);
        }
        // Public participants only; colors describe control/turn, not invented alliance rules.
        for(var value:ClientSrdCombat.actors()) {
            var a=value.getAsJsonObject();var actor=client.world.getEntityById(a.get("entity").getAsInt());
            if(actor==null || !actor.getUuidAsString().equals(a.get("id").getAsString()))continue;
            float x=(float)actor.getX(),z=(float)actor.getZ(),y=(float)actor.getY()+.045f;
            int color=a.get("id").equals(ClientSrdCombat.state.get("active"))?0xCCFFD060:actor==client.player?0xCCFFFFFF:a.get("controlled").getAsBoolean()?0xAA55AADD:0xAAEE6655;
            buffer.vertex(matrix,x-.4f,y,z-.4f).color(color);buffer.vertex(matrix,x+.4f,y,z-.4f).color(color);
            buffer.vertex(matrix,x+.4f,y,z+.4f).color(color);buffer.vertex(matrix,x-.4f,y,z+.4f).color(color);
        }
        if(focus!=null) {
            float x=(float)focus.getX(),z=(float)focus.getZ(),y=(float)focus.getY()+.04f;
            buffer.vertex(matrix,x-.48f,y,z-.48f).color(0xAAFF5544);buffer.vertex(matrix,x+.48f,y,z-.48f).color(0xAAFF5544);
            buffer.vertex(matrix,x+.48f,y,z+.48f).color(0xAAFF5544);buffer.vertex(matrix,x-.48f,y,z+.48f).color(0xAAFF5544);
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.disableBlend(); matrices.pop();
    }
}
