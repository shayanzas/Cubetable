package dev.tacticalcombat.client;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;

/** Stage A integration surface. Transparent world picking; final compact HUD design belongs to Stage B. */
public final class SrdBattlefieldScreen extends Screen {
    private String inspected;
    private static int stepTicks=4;
    private ButtonWidget move,attack,end;
    private long seen=-1;
    public SrdBattlefieldScreen(){super(Text.literal("SRD battlefield"));}
    public boolean shouldPause(){return false;}
    public void renderBackground(DrawContext g,int x,int y,float delta){}
    private ButtonWidget button(String label,int x,int y,int w,Runnable action){return addDrawableChild(ButtonWidget.builder(Text.literal(label),b->action.run()).dimensions(x,y,w,20).build());}
    protected void init(){
        ClientSrdCombat.focusEntity=-1;
        int w=Math.min(120,(width-24)/3),x=(width-3*w-8)/2;
        move=button("Confirm move",x,height-26,w,()->{
            var c=ClientSrdCombat.selected;if(c==null || !ClientSrdCombat.canControl())return;
            var args=new JsonObject();args.addProperty("x",c.x());args.addProperty("y",c.y());args.addProperty("z",c.z());args.addProperty("stepTicks",stepTicks);
            ClientSrdCombat.selected=null;ClientSrdCombat.send("move",args);
        });
        attack=button("Attack preview…",x+w+4,height-26,w,()->{if(inspected!=null)SrdCombatScreen.attackTarget(inspected);});
        end=button("End turn",x+2*w+8,height-26,w,()->ClientSrdCombat.send("endturn"));
        button("Details / DM",width-112,8,104,()->client.setScreen(new SrdCombatScreen(this)));
        button("Recenter",width-112,32,104,()->SrdCamera.pan=Vec3d.ZERO).setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Camera occluders fade in tactical view. Solid terrain still blocks picking.")));
        button("Refresh grid",width-112,56,104,()->ClientSrdCombat.send("grid"));
        button("Pace: "+stepTicks+" ticks/step",width-112,80,104,()->{stepTicks=stepTicks==1?2:stepTicks==2?4:stepTicks==4?6:1;clearChildren();init();});
    }
    public void tick(){
        if(seen!=ClientSrdCombat.turn){inspected=null;ClientSrdCombat.focusEntity=-1;seen=ClientSrdCombat.turn;}
        move.active=ClientSrdCombat.canControl()&&!ClientSrdCombat.moving()&&ClientSrdCombat.selected!=null&&ClientSrdCombat.movement.select(ClientSrdCombat.selected).costFeet().orElse(0)>0;
        attack.active=ClientSrdCombat.canControl()&&!ClientSrdCombat.moving()&&!ClientSrdCombat.state.get("actionUsed").getAsBoolean()&&inspected!=null&&!inspected.equals(ClientSrdCombat.state.get("active").getAsString());
        end.active=ClientSrdCombat.canControl();
        if(!SrdCamera.engaged())return;
        long h=client.getWindow().getHandle();double f=(down(h,GLFW.GLFW_KEY_W)?1:0)-(down(h,GLFW.GLFW_KEY_S)?1:0),r=(down(h,GLFW.GLFW_KEY_D)?1:0)-(down(h,GLFW.GLFW_KEY_A)?1:0);
        double yaw=Math.toRadians(SrdCamera.yawTarget);var pan=SrdCamera.pan.add((-Math.sin(yaw)*f+Math.cos(yaw)*r)*.4,0,(Math.cos(yaw)*f+Math.sin(yaw)*r)*.4);
        SrdCamera.pan=pan.length()>48?pan.normalize().multiply(48):pan;
    }
    private boolean down(long h,int key){return InputUtil.isKeyPressed(h,key);}
    public void render(DrawContext g,int mx,int my,float delta){
        var a=ClientSrdCombat.current();int panel=Math.max(60,width-128);
        g.fill(4,4,panel,58,0xB0171C26);
        g.drawText(textRenderer,textRenderer.trimToWidth("CURRENT TURN: "+(a==null?"Awaiting initiative":a.get("name").getAsString()),panel-12),10,10,0xFFFFD060,false);
        if(ClientSrdCombat.active()){
            String status=ClientSrdCombat.state.get("state").getAsString()+" | Round "+ClientSrdCombat.state.get("round")+" | "+ClientSrdCombat.state.get("remainingFeet")+" ft | "+(ClientSrdCombat.canControl()?"You control this turn":"Inspect only");
            g.drawText(textRenderer,textRenderer.trimToWidth(status,panel-12),10,24,0xFFFFFFFF,false);
            String moveState=ClientSrdCombat.moving()?"Route executing — wait for refreshed grid":ClientSrdCombat.state.get("remainingFeet").getAsInt()<5?"Movement exhausted for this turn":ClientSrdCombat.grid==null?"Grid unavailable: "+ClientSrdCombat.message:"Click terrain to preview; then Confirm move";
            g.drawText(textRenderer,textRenderer.trimToWidth(moveState,panel-12),10,38,0xFFD0D7DF,false);
        }
        String detail=ClientSrdCombat.selected==null?"Wheel: zoom | Middle drag: orbit | WASD: pan": "Route: "+ClientSrdCombat.movement.select(ClientSrdCombat.selected).costFeet().orElse(0)+" ft; remaining "+ClientSrdCombat.movement.select(ClientSrdCombat.selected).remainingFeet()+" ft";
        g.drawText(textRenderer,textRenderer.trimToWidth(detail,width-16),8,height-42,0xFFFFD060,false);
        if(inspected!=null)for(var entry:ClientSrdCombat.actors()){
            var v=entry.getAsJsonObject();if(v.get("id").getAsString().equals(inspected))g.drawText(textRenderer,textRenderer.trimToWidth("Selected: "+v.get("name").getAsString()+(v.has("hp")?" | HP "+v.get("hp")+"/"+v.get("maximumHp"):" | HP private"),width-16),8,106,0xFFFFFFFF,false);
        }
        if(!ClientSrdCombat.lastResult.isEmpty()) {
            var lines=textRenderer.wrapLines(Text.literal(ClientSrdCombat.lastResult),Math.max(40,width-24));
            int count=Math.min(5,lines.size()),y=height-50-count*10;
            g.fill(4,y-3,width-4,height-48,0xC0171C26);
            for(int i=0;i<count;i++)g.drawText(textRenderer,lines.get(i),10,y+i*10,0xFFE4D3AC,false);
        }
        super.render(g,mx,my,delta);
    }
    public boolean mouseClicked(double x,double y,int button){
        if(super.mouseClicked(x,y,button))return true;
        if(button!=0 || !SrdCamera.engaged())return false;
        var pick=SrdWorldPicker.pick(client,x,y);
        if(pick.actor()!=null){inspected=pick.actor();ClientSrdCombat.selected=null;for(var a:ClientSrdCombat.actors())if(a.getAsJsonObject().get("id").getAsString().equals(inspected))ClientSrdCombat.focusEntity=a.getAsJsonObject().get("entity").getAsInt();return true;}
        if(ClientSrdCombat.canControl()&&!ClientSrdCombat.moving()){ClientSrdCombat.selected=pick.cell();return true;}
        return false;
    }
    public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button!=2)return super.mouseDragged(x,y,button,dx,dy);SrdCamera.yawTarget+=(float)dx*.4f;SrdCamera.pitch=MathHelper.clamp(SrdCamera.pitch+(float)dy*.25f,25,80);return true;}
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){SrdCamera.zoomTarget=MathHelper.clamp(SrdCamera.zoomTarget-vertical*2,8,40);return true;}
    public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){client.setScreen(new GameMenuScreen(true));return true;}
        if(client.options.chatKey.matchesKey(key,scan)){client.setScreen(new ChatScreen(""));return true;}
        if(client.options.commandKey.matchesKey(key,scan)){client.setScreen(new ChatScreen("/"));return true;}
        if(client.options.inventoryKey.matchesKey(key,scan)&&client.player!=null){client.setScreen(new InventoryScreen(client.player));return true;}
        if(TacticalCombatClient.QUICK_SHEET_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.QuickPanelScreen(this,false));return true;}
        if(TacticalCombatClient.DM_QUICK_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.DmWorkspaceScreen(this));return true;}
        if(TacticalCombatClient.SHEET_KEY.matchesKey(key,scan)){CharacterSheetScreen.requestOpen();return true;}
        if(TacticalCombatClient.END_TURN_KEY.matchesKey(key,scan)){TacticalCombatClient.sendEndTurn();return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
