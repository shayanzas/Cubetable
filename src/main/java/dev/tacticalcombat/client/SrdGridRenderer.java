package dev.tacticalcombat.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;

/** Bounded, depth-tested SRD indicators. Never creates reachable terrain or invents affiliations. */
public final class SrdGridRenderer {
    private SrdGridRenderer() {}
    private static void quad(BufferBuilder b,Matrix4f m,float x,float y,float z,float w,float d,int color){
        b.vertex(m,x,y,z).color(color);b.vertex(m,x+w,y,z).color(color);b.vertex(m,x+w,y,z+d).color(color);b.vertex(m,x,y,z+d).color(color);
    }
    private static void outline(BufferBuilder b,Matrix4f m,float x,float y,float z,float size,float thickness,int color){
        quad(b,m,x,y,z,size,thickness,color);quad(b,m,x,y,z+size-thickness,size,thickness,color);
        quad(b,m,x,y,z,thickness,size,color);quad(b,m,x+size-thickness,y,z,thickness,size,color);
    }
    public static void render(WorldRenderContext ctx) {
        var client=net.minecraft.client.MinecraftClient.getInstance();
        if(SrdCamera.activeEntity(client)==null)return;
        SrdWorldPicker.projection(ctx.projectionMatrix());
        var matrices=ctx.matrixStack();if(matrices==null)return;
        var grid=ClientSrdCombat.grid;var camera=ctx.camera().getPos();matrices.push();matrices.translate(-camera.x,-camera.y,-camera.z);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.depthMask(false);RenderSystem.enableDepthTest();RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        var buffer=Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS,VertexFormats.POSITION_COLOR);var matrix=matrices.peek().getPositionMatrix();
        var destination=ClientSrdCombat.selected!=null?ClientSrdCombat.selected:ClientSrdCombat.hover;
        var path=ClientSrdCombat.movement.pathCells(destination);
        if(grid!=null)for(var cell:grid.reachable().keySet()) {
            float x=cell.x()+.04f,z=cell.z()+.04f,y=(float)cell.y()+.035f;
            boolean selected=cell.equals(destination),start=cell.equals(grid.start()),route=path.contains(cell);
            int color=start?0xDDFFFFFF:selected?0xFFFFD070:route?0xDDD0AD60:0x665DA7B6;
            outline(buffer,matrix,x,y,z,.92f,selected?.07f:.025f,color);
            if(route&&!start)quad(buffer,matrix,x+.32f,y,z+.32f,.28f,.28f,color);
        }
        // Red surface marker is an unavailable hover cue only; it is never a movement destination.
        var invalid=ClientSrdCombat.unavailableHover;
        if(invalid!=null&&ClientSrdCombat.canControl()&&!ClientSrdCombat.moving())outline(buffer,matrix,(float)Math.floor(invalid.x)+.15f,(float)invalid.y+.04f,(float)Math.floor(invalid.z)+.15f,.7f,.045f,0xCCCF7C73);
        for(var value:ClientSrdCombat.actors()) {
            var a=value.getAsJsonObject();var actor=client.world.getEntityById(a.get("entity").getAsInt());
            if(actor==null||!actor.getUuidAsString().equals(a.get("id").getAsString()))continue;
            var shown=SrdMotion.position(actor,ctx.tickCounter().getTickDelta(false));
            float x=(float)shown.x,z=(float)shown.z,y=(float)shown.y+.05f;
            boolean active=a.get("id").equals(ClientSrdCombat.state.get("active")),selected=actor.getId()==ClientSrdCombat.focusEntity;
            // Neutral other combatants; control is not an alliance or hostility declaration.
            int color=active?0xFFE3BD70:actor==client.player?0xEEFFFFFF:a.get("controlled").getAsBoolean()?0xCC62B9C5:0xCCAFADB2;
            outline(buffer,matrix,x-.44f,y,z-.44f,.88f,active?.06f:.035f,color);
            if(SrdCombatEffects.hit(a.get("id").getAsString(),ClientSrdCombat.ticks))outline(buffer,matrix,x-.58f,y+.015f,z-.58f,1.16f,.065f,0xFFE5A66A);
            if(active&&ClientSrdCombat.moving())outline(buffer,matrix,x-.48f,y+.008f,z-.48f,.96f,.035f,0xFF62B9C5);
            if(actor.getId()==ClientSrdCombat.hoverEntity&&!selected)outline(buffer,matrix,x-.5f,y+.007f,z-.5f,1f,.025f,0xBBD4B0F2);
            if(selected)outline(buffer,matrix,x-.55f,y+.005f,z-.55f,1.1f,.045f,0xFFD4B0F2);
            if(active){quad(buffer,matrix,x-.12f,y,z-.62f,.24f,.12f,color);quad(buffer,matrix,x-.12f,y,z+.5f,.24f,.12f,color);}
        }
        BufferRenderer.drawWithGlobalProgram(buffer.end());RenderSystem.depthMask(true);RenderSystem.enableCull();RenderSystem.disableBlend();matrices.pop();
    }
}
