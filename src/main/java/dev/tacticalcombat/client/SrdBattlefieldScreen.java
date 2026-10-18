package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.client.ui.RpgButton;
import dev.tacticalcombat.rules.srd.SrdMovement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Transparent SRD interaction surface. All mutations still use server preview/confirmation and turn identity. */
public final class SrdBattlefieldScreen extends Screen {
    private static final int GOLD=0xFFE3BD70,TEXT=0xFFE3E5E8,MUTED=0xFFADB5BF;
    private final SrdBattlefieldModel model=new SrdBattlefieldModel();
    private static int stepTicks=4;
    private SrdBattlefieldModel.Layout layout;
    private RpgButton move,attack,confirm,end,dash,previewButton,confirmAttack;
    private final List<SrdAttackChoices.Choice> choices=new ArrayList<>();
    private int choiceIndex,choicePage,scroll;
    private boolean history,options;
    private String built="";
    private String dismissedToast="";
    private long lastSeenTurn=-1;
    private UUID lastEncounter;
    private JsonObject shownPreview;
    private String seenResult="";
    public SrdBattlefieldScreen(){super(Text.literal("Cubetable tactical battlefield"));}
    public boolean shouldPause(){return false;}
    public void renderBackground(DrawContext g,int x,int y,float delta){}
    private RpgButton button(String label,String hint,int x,int y,int w,int h,Runnable action){
        var b=new RpgButton(x,y,w,h,label,"",action).hint(hint);addDrawableChild(b);return b;
    }
    private void panel(DrawContext g,SrdBattlefieldModel.Rect r){g.fill(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),0xD0181C22);g.drawBorder(r.x(),r.y(),r.w(),r.h(),0xFF54504A);g.fill(r.x()+1,r.y()+1,r.x()+r.w()-1,r.y()+2,0xFF8D7650);}
    private void line(DrawContext g,String text,int x,int y,int w,int color){g.drawText(textRenderer,textRenderer.trimToWidth(text,Math.max(1,w)),x,y,color,false);}
    private String actorId(){return ClientSrdCombat.state.has("active")?ClientSrdCombat.state.get("active").getAsString():"";}
    private JsonObject target(){return SrdBattlefieldModel.actor(ClientSrdCombat.actors(),model.target);}
    private String name(JsonObject a){return a==null?"Choose an actor":a.get("name").getAsString();}
    private String type(JsonObject a){
        if(a==null||client.world==null)return "Actor";
        var e=client.world.getEntityById(a.get("entity").getAsInt());
        if(e==client.player)return "Your character";
        return e==null?"Actor":e.getType().getName().getString();
    }
    private boolean ready(){return ClientSrdCombat.canControl()&&!ClientSrdCombat.moving()&&ClientSrdCombat.pending.isEmpty();}
    private String unavailable(){return !ClientSrdCombat.canControl()?"Only the authorized current controller can act":ClientSrdCombat.moving()?"Wait for the accepted route to finish":!ClientSrdCombat.pending.isEmpty()?"Waiting for server: "+ClientSrdCombat.pending:"";}
    private void tool(SrdBattlefieldModel.Tool value){model.tool=value;options=history=false;ClientSrdCombat.preview=null;scroll=0;rebuild();}
    protected void init(){layout=SrdBattlefieldModel.layout(width,height);rebuild();}
    private void rebuild(){
        if(layout==null)return;
        clearChildren();previewButton=confirmAttack=null;
        var bar=layout.bar();boolean twoRows=layout.narrow();int cols=twoRows?4:7,h=twoRows?18:22,gap=3,w=(bar.w()-10-gap*(cols-1))/cols;
        String[] titles={"Move","Attack","Confirm move","End Turn","History","More","Dash"};
        Runnable[] actions={()->tool(SrdBattlefieldModel.Tool.MOVE),()->tool(SrdBattlefieldModel.Tool.ATTACK),this::confirmMove,()->{ClientSrdCombat.send("endturn");ClientSrdCombat.preview=null;},()->{history=!history;options=false;scroll=0;rebuild();},()->{options=!options;history=false;scroll=0;rebuild();},this::dash};
        for(int i=0;i<7;i++){
            var b=button(titles[i],i==3?"End this SRD turn; never automatic":i==4?"Recent authorized movement, turns and outcomes":"",bar.x()+5+(i%cols)*(w+gap),bar.y()+4+(i/cols)*(h+2),w,h,actions[i]);
            if(i==0)move=b;if(i==1)attack=b;if(i==2)confirm=b;if(i==3)end=b.primary(true);if(i==6)dash=b;
        }
        var cam=layout.camera();int cw=(cam.w()-8)/2;
        if(!(height<240&&contextVisible())) {
        button("Center","Follow the active actor again",cam.x()+4,cam.y()+4,cam.w()-8,18,()->SrdCamera.pan=Vec3d.ZERO);
        button("−","Zoom out",cam.x()+4,cam.y()+24,cw-1,18,()->SrdCamera.zoomTarget=Math.min(40,SrdCamera.zoomTarget+2));
        button("+","Zoom in",cam.x()+5+cw,cam.y()+24,cw-1,18,()->SrdCamera.zoomTarget=Math.max(8,SrdCamera.zoomTarget-2));
        if(cam.h()>54)button("Help","Wheel zoom · middle drag orbit/tilt · WASD pan. Camera occluders fade; solids still block picking.",cam.x()+4,cam.y()+44,cam.w()-8,18,()->{options=true;history=false;rebuild();});
        }
        var r=layout.context();int x=r.x()+6,y=r.y()+5,inner=r.w()-12;
        if(history){
            button("Close history","History is cleared on permission changes and disconnect",x,y,inner,18,()->{history=false;rebuild();});return;
        }
        if(options){
            button("Details / DM…","Optional advanced controls and adjudication",x,y,inner,18,()->client.setScreen(new SrdCombatScreen(this)));
            button("Refresh state","Read-only reconciliation; never repeats your last action",x,y+21,inner,18,()->ClientSrdCombat.send("state"));
            int py=y+43;int pw=(inner-9)/4;
            for(int i=0;i<4;i++){final int pace=new int[]{1,2,4,6}[i];button(new String[]{"Fast","Brisk","Walk","Slow"}[i],pace+" server ticks per square; changes timing, never distance cost",x+i*(pw+3),py,pw,18,()->{stepTicks=pace;rebuild();}).selected(stepTicks==pace);}
            return;
        }
        if(model.tool!=SrdBattlefieldModel.Tool.ATTACK||target()==null)return;
        if(ClientSrdCombat.preview!=null){
            int by=r.y()+r.h()-23;
            confirmAttack=button("Confirm attack","Spends the server-validated action and resources, including on a miss",x,by,(inner-3)/2,18,this::confirmAttack).primary(true);
            button("Cancel","Cancel the preview; no resource cost",x+(inner+3)/2,by,(inner-3)/2,18,this::cancelContext);return;
        }
        choices.clear();var a=ClientSrdCombat.current();
        if(a!=null&&a.has("weapons"))for(var weapon:a.getAsJsonArray("weapons"))choices.addAll(SrdAttackChoices.forWeapon(weapon.getAsString(),a.has("monsterAbility")?a.get("monsterAbility").getAsString():null));
        int capacity=Math.max(1,(r.h()-67)/20),pages=Math.max(1,(choices.size()+capacity-1)/capacity);choicePage=Math.floorMod(choicePage,pages);
        for(int i=choicePage*capacity;i<Math.min(choices.size(),(choicePage+1)*capacity);i++){
            final int index=i;var c=choices.get(i);String label=c.weapon().name().replace('_',' ')+" · "+c.ability()+" · "+(c.mode().name().equals("MELEE")?"melee":c.mode().name().toLowerCase(Locale.ROOT))+(c.twoHands()?" · 2H":"");
            button(label,"Supported selection; the server checks equipment, shield, stock, range and target",x,y+25+(i-choicePage*capacity)*20,inner,18,()->{choiceIndex=index;rebuild();}).selected(i==choiceIndex);
        }
        int by=r.y()+r.h()-23;
        previewButton=button("Preview","No cost: request an authoritative attack preview",x,by,(inner-3)/2,18,this::requestPreview);
        button("Options "+(choicePage+1)+"/"+pages,"More supported weapon/ability/hand choices",x+(inner+3)/2,by,(inner-3)/2,18,()->{choicePage++;rebuild();});
    }
    private void dash(){
        if(!SrdBattlefieldActions.canDash(ClientSrdCombat.state,ClientSrdCombat.pending))return;
        ClientSrdCombat.send("dash",SrdBattlefieldActions.dash(ClientSrdCombat.state,ClientSrdCombat.revision,ClientSrdCombat.pending));
        ClientSrdCombat.preview=null;model.tool=SrdBattlefieldModel.Tool.MOVE;rebuild();
    }
    private void confirmMove(){
        var cell=ClientSrdCombat.selected;if(!ready()||cell==null||ClientSrdCombat.movement.select(cell).costFeet().orElse(0)<=0)return;
        var args=new JsonObject();args.addProperty("x",cell.x());args.addProperty("y",cell.y());args.addProperty("z",cell.z());args.addProperty("stepTicks",stepTicks);
        ClientSrdCombat.selected=null;ClientSrdCombat.send("move",args);
    }
    private void requestPreview(){
        if(!ready()||target()==null||model.target.equals(actorId())||choices.isEmpty())return;
        var c=choices.get(Math.floorMod(choiceIndex,choices.size()));
        try{ClientSrdCombat.send("preview",SrdBattlefieldActions.preview(ClientSrdCombat.state,model.target,c));}
        catch(IllegalArgumentException invalid){ClientSrdCombat.message=invalid.getMessage();}

    }
    private void confirmAttack(){
        if(!ready()||ClientSrdCombat.preview==null)return;
        var args=SrdBattlefieldActions.confirmation(ClientSrdCombat.preview);ClientSrdCombat.preview=null;ClientSrdCombat.send("confirm",args);rebuild();
    }
    private void cancelContext(){model.cancel();ClientSrdCombat.preview=null;ClientSrdCombat.selected=ClientSrdCombat.hover=null;ClientSrdCombat.focusEntity=-1;history=options=false;scroll=0;rebuild();}
    public void tick(){
        if(!ClientSrdCombat.active())return;
        model.reconcile(String.valueOf(ClientSrdCombat.encounter),ClientSrdCombat.turn,ClientSrdCombat.canControl(),ClientSrdCombat.actors());
        if(lastSeenTurn!=ClientSrdCombat.turn||!Objects.equals(lastEncounter,ClientSrdCombat.encounter)){ClientSrdCombat.focusEntity=-1;choiceIndex=choicePage=scroll=0;history=options=false;lastSeenTurn=ClientSrdCombat.turn;lastEncounter=ClientSrdCombat.encounter;}
        if(!ClientSrdCombat.lastResultId.isEmpty()&&!ClientSrdCombat.lastResultId.equals(seenResult)){
            seenResult=ClientSrdCombat.lastResultId;model.cancel();history=options=false;scroll=0;
        }
        String key=model.target+model.tool+ClientSrdCombat.canControl();
        if(!key.equals(built)||shownPreview!=ClientSrdCombat.preview){built=key;shownPreview=ClientSrdCombat.preview;rebuild();}
        boolean ready=ready();move.active=ready;attack.active=ready&&!ClientSrdCombat.state.get("actionUsed").getAsBoolean();
        dash.active=SrdBattlefieldActions.canDash(ClientSrdCombat.state,ClientSrdCombat.pending);
        dash.setMessage(Text.literal("Dash"+(ClientSrdCombat.state.has("dashFeet")?" +"+ClientSrdCombat.state.get("dashFeet"):"")));
        dash.hint(!ready?unavailable():dash.active?"Dash: +"+ClientSrdCombat.state.get("dashFeet")+" ft this turn; spends your normal action; does not move you":"Normal action unavailable");
        confirm.active=ready&&ClientSrdCombat.selected!=null&&ClientSrdCombat.movement.select(ClientSrdCombat.selected).costFeet().orElse(0)>0;
        end.active=ClientSrdCombat.canControl()&&ClientSrdCombat.pending.isEmpty();
        move.selected(model.tool==SrdBattlefieldModel.Tool.MOVE);attack.selected(model.tool==SrdBattlefieldModel.Tool.ATTACK);
        move.hint(ready?"Click terrain; inspect cost, then Confirm move":unavailable());attack.hint(!ready?unavailable():attack.active?"Select a visible combatant for a supported attack":"Action already spent or reserved");
        confirm.hint(confirm.active?"Commit this selected server-reachable path":!ready?unavailable():"Choose a reachable destination first");end.hint(end.active?"End this SRD turn":"Waiting for the server or not your controlled turn");
        if(previewButton!=null)previewButton.active=ready&&!ClientSrdCombat.state.get("actionUsed").getAsBoolean()&&!model.target.equals(actorId())&&!choices.isEmpty();
        if(confirmAttack!=null)confirmAttack.active=ready;
        if(!SrdCamera.engaged())return;
        long h=client.getWindow().getHandle();double f=(down(h,GLFW.GLFW_KEY_W)?1:0)-(down(h,GLFW.GLFW_KEY_S)?1:0),r=(down(h,GLFW.GLFW_KEY_D)?1:0)-(down(h,GLFW.GLFW_KEY_A)?1:0);
        double yaw=Math.toRadians(SrdCamera.yawTarget);var pan=SrdCamera.pan.add((-Math.sin(yaw)*f+Math.cos(yaw)*r)*.4,0,(Math.cos(yaw)*f+Math.sin(yaw)*r)*.4);SrdCamera.pan=pan.length()>48?pan.normalize().multiply(48):pan;
    }
    private boolean down(long h,int key){return InputUtil.isKeyPressed(h,key);}
    private boolean toastVisible(){return !history&&(!contextVisible()||layout.toast().x()+layout.toast().w()<layout.context().x());}
    private boolean contextVisible(){return history||options||target()!=null;}
    private boolean overPanel(double x,double y){return layout.status().contains(x,y)||layout.camera().contains(x,y)||layout.bar().contains(x,y)||contextVisible()&&layout.context().contains(x,y);}
    private void pick(double x,double y,boolean click){
        if(overPanel(x,y)||!SrdCamera.engaged()){ClientSrdCombat.hover=null;ClientSrdCombat.unavailableHover=null;return;}
        var hit=SrdWorldPicker.pick(client,x,y);ClientSrdCombat.hover=hit.cell();ClientSrdCombat.unavailableHover=model.tool==SrdBattlefieldModel.Tool.MOVE?hit.unavailable():null;
        if(!click||!ClientSrdCombat.pending.isEmpty())return;
        if(hit.actor()!=null){model.target=hit.actor();ClientSrdCombat.selected=null;ClientSrdCombat.preview=null;history=options=false;scroll=0;var a=target();ClientSrdCombat.focusEntity=a==null?-1:a.get("entity").getAsInt();rebuild();}
        else if(ready()&&model.tool==SrdBattlefieldModel.Tool.MOVE){ClientSrdCombat.selected=hit.cell();ClientSrdCombat.preview=null;}
    }
    public void render(DrawContext g,int mx,int my,float delta){
        pick(mx,my,false);var a=ClientSrdCombat.current();var r=layout.status();boolean compactContext=height<240&&contextVisible();
        drawActorLabels(g);
        if(!compactContext){panel(g,r);
        // Original pixel silhouette; no external portrait assets or guessed private class identity.
        int icon=a!=null&&a.get("controlled").getAsBoolean()?GOLD:0xFF9BAEBE;
        g.fill(r.x()+7,r.y()+9,r.x()+14,r.y()+16,icon);g.fill(r.x()+4,r.y()+18,r.x()+17,r.y()+24,icon);
        line(g,"CURRENT TURN · Round "+(ClientSrdCombat.state.has("round")?ClientSrdCombat.state.get("round"):"—"),r.x()+23,r.y()+5,r.w()-28,GOLD);
        line(g,name(a)+" · "+type(a),r.x()+23,r.y()+17,r.w()-28,TEXT);
        line(g,SrdBattlefieldModel.hp(a)+" · Init "+(a==null?"—":a.get("initiative")),r.x()+6,r.y()+30,r.w()-12,TEXT);
        String resources=ClientSrdCombat.active()?ClientSrdCombat.state.get("remainingFeet")+" ft · Action "+(ClientSrdCombat.state.get("actionUsed").getAsBoolean()?"spent":"ready")+" · Bonus unsupported":"No active encounter";
        if(ClientSrdCombat.state.has("state")&&!ClientSrdCombat.state.get("state").getAsString().equals("ACTIVE"))resources=ClientSrdCombat.state.get("state").getAsString()+" · "+ClientSrdCombat.state.get("reason").getAsString();
        line(g,resources,r.x()+6,r.y()+42,r.w()-12,MUTED);
        var next=SrdBattlefieldModel.next(ClientSrdCombat.state);if(r.h()>60)line(g,"UP NEXT  "+name(next)+(next==null?"":" · Init "+next.get("initiative")),r.x()+6,r.y()+55,r.w()-12,GOLD);
        panel(g,layout.camera());}
        panel(g,layout.bar());
        if(!layout.narrow())line(g,ClientSrdCombat.canControl()?"Your controlled turn · Wheel zoom · Middle drag orbit · WASD pan":"Inspect only · Waiting for the authorized controller",layout.bar().x()+8,layout.bar().y()+30,layout.bar().w()-16,MUTED);
        drawMovement(g,mx,my);
        if(contextVisible())drawContext(g);
        var toast=ClientSrdCombat.activity.toast(ClientSrdCombat.ticks);
        if(toast!=null&&!toast.id().equals(dismissedToast)&&toastVisible()){var box=layout.toast();{
            panel(g,box);var lines=textRenderer.wrapLines(Text.literal(toast.text()),box.w()-24);for(int i=0;i<Math.min(3,lines.size());i++)g.drawText(textRenderer,lines.get(i),box.x()+6,box.y()+5+i*10,TEXT,false);
            line(g,"×",box.x()+box.w()-13,box.y()+4,10,GOLD);
        }}
        super.render(g,mx,my,delta);
    }
    private void drawActorLabels(DrawContext g){
        if(client.world==null)return;
        for(var value:ClientSrdCombat.actors()){
            var a=value.getAsJsonObject();var e=client.world.getEntityById(a.get("entity").getAsInt());
            if(e==null||!e.getUuidAsString().equals(a.get("id").getAsString()))continue;
            var point=SrdWorldPicker.label(client,e);if(point==null||overPanel(point[0],point[1]))continue;
            String prefix=a.get("id").getAsString().equals(actorId())?(e==client.player?"TURN · SELF · ":"TURN · "):e==client.player?"SELF · ":a.get("controlled").getAsBoolean()?"CONTROL · ":"ACTOR · ";
            String text=textRenderer.trimToWidth(prefix+name(a),150);int w=textRenderer.getWidth(text),x=MathHelper.clamp(point[0]-w/2,2,Math.max(2,width-w-2)),y=MathHelper.clamp(point[1],2,height-12);
            g.fill(x-3,y-2,x+w+3,y+10,0xC0181C22);g.drawText(textRenderer,text,x,y,a.get("id").getAsString().equals(actorId())?GOLD:TEXT,false);
        }
    }
    private void drawMovement(DrawContext g,int mx,int my){
        if(!ClientSrdCombat.active())return;
        String info;
        var cell=ClientSrdCombat.selected!=null?ClientSrdCombat.selected:ClientSrdCombat.hover;
        if(!ClientSrdCombat.pending.isEmpty())info="Server pending: "+ClientSrdCombat.pending+" · More → Refresh state";
        else if(ClientSrdCombat.moving())info="Route in progress · completed steps consume movement";
        else if(ClientSrdCombat.activity.toast(ClientSrdCombat.ticks)!=null&&ClientSrdCombat.activity.toast(ClientSrdCombat.ticks).id().startsWith("denied:"))info=ClientSrdCombat.activity.toast(ClientSrdCombat.ticks).text();
        else if(cell!=null){var choice=ClientSrdCombat.movement.select(cell);info="Route "+choice.costFeet().orElse(0)+" ft  →  "+choice.remainingFeet()+" ft left"+(ClientSrdCombat.selected==null?" · Click to select":" · Confirm move");}
        else if(ClientSrdCombat.unavailableHover!=null)info="Unavailable destination — choose highlighted reachable ground";
        else if(!ClientSrdCombat.canControl())info="Inspect only — not your controlled turn";
        else if(ClientSrdCombat.state.get("remainingFeet").getAsInt()<5)info="Movement exhausted · Attack or End Turn";
        else if(ClientSrdCombat.grid==null)info="Reachable grid pending/unavailable · "+ClientSrdCombat.message;
        else info=model.tool==SrdBattlefieldModel.Tool.ATTACK?"Choose a visible actor, then Preview":"Move: click highlighted terrain · Solid or unhighlighted destinations unavailable";
        int w=Math.min(width-12,textRenderer.getWidth(info)+12),y=layout.bar().y()-15;g.fill(6,y,6+w,y+12,0xD0181C22);line(g,info,12,y+2,w-12,GOLD);
    }
    private void drawContext(DrawContext g){
        var r=layout.context();panel(g,r);int x=r.x()+6,y=r.y()+6,w=r.w()-12;
        if(history){
            var entries=ClientSrdCombat.activity.newest();var lines=new ArrayList<net.minecraft.text.OrderedText>();for(var e:entries){lines.addAll(textRenderer.wrapLines(Text.literal(e.text()),w));lines.add(Text.literal(" ").asOrderedText());}
            drawScrolled(g,lines,x,y+23,w,r.h()-30);return;
        }
        if(options){
            if(r.h()>145){line(g,"PACE: "+stepTicks+" ticks / square",x,y+67,w,GOLD);line(g,"Bonus actions / abilities: not implemented",x,y+81,w,MUTED);line(g,"Wheel zoom · middle drag orbit/tilt",x,y+97,w,MUTED);line(g,"WASD pan · right-click / Esc cancel",x,y+109,w,MUTED);line(g,"Camera occluders fade; solids block picking",x,y+121,w,MUTED);}return;
        }
        line(g,name(target())+" · "+type(target()),x,y,w,GOLD);
        if(model.tool!=SrdBattlefieldModel.Tool.ATTACK){
            line(g,SrdBattlefieldModel.hp(target()),x,y+16,w,TEXT);line(g,model.target.equals(actorId())?"Current actor":"Visible combatant · affiliation unspecified",x,y+30,w,MUTED);line(g,"Choose Attack below for supported actions",x,y+44,w,MUTED);return;
        }
        if(ClientSrdCombat.preview==null){line(g,"Choose attack · "+(model.target.equals(actorId())?"cannot target self":ClientSrdCombat.canControl()?"server validates":"inspect only"),x,y+13,w,MUTED);return;}
        var p=ClientSrdCombat.preview;var text=new ArrayList<String>();
        text.add(name(ClientSrdCombat.current())+" → "+name(target()));text.add(p.get("weapon").getAsString().replace('_',' '));text.add("Attack: "+p.get("attack").getAsString());text.add("Range: "+p.get("range").getAsString());text.add("Damage: "+p.get("damage").getAsString()+" "+p.get("type").getAsString());text.add("Cover: "+p.get("cover").getAsString());text.add(p.get("cost").getAsString());text.add(p.get("stock").getAsString());
        var lines=new ArrayList<net.minecraft.text.OrderedText>();for(var t:text)lines.addAll(textRenderer.wrapLines(Text.literal(t),w));drawScrolled(g,lines,x,y+16,w,r.h()-45);
    }
    private void drawScrolled(DrawContext g,List<net.minecraft.text.OrderedText> lines,int x,int y,int w,int h){
        int capacity=Math.max(1,h/10);scroll=MathHelper.clamp(scroll,0,Math.max(0,lines.size()-capacity));
        g.enableScissor(x,y,x+w,y+Math.max(0,h));for(int i=scroll;i<Math.min(lines.size(),scroll+capacity);i++)g.drawText(textRenderer,lines.get(i),x,y+(i-scroll)*10,TEXT,false);g.disableScissor();
        if(lines.size()>capacity)line(g,"↕",x+w-10,y,10,GOLD);
    }
    public boolean mouseClicked(double x,double y,int button){
        if(button==1){cancelContext();return true;}
        if(super.mouseClicked(x,y,button))return true;
        if(button!=0)return false;
        var toast=ClientSrdCombat.activity.toast(ClientSrdCombat.ticks);if(toast!=null&&toastVisible()&&!toast.id().equals(dismissedToast)&&layout.toast().contains(x,y)){dismissedToast=toast.id();return true;}
        if(overPanel(x,y))return true;pick(x,y,true);return true;
    }
    public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button!=2)return super.mouseDragged(x,y,button,dx,dy);SrdCamera.yawTarget+=(float)dx*.4f;SrdCamera.pitch=MathHelper.clamp(SrdCamera.pitch+(float)dy*.25f,25,80);return true;}
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(contextVisible()&&layout.context().contains(x,y)){scroll-=Math.signum(vertical)*2;return true;}SrdCamera.zoomTarget=MathHelper.clamp(SrdCamera.zoomTarget-vertical*2,8,40);return true;}
    public boolean keyPressed(int key,int scan,int modifiers){
        if(key==GLFW.GLFW_KEY_ESCAPE){if(contextVisible()||ClientSrdCombat.selected!=null||ClientSrdCombat.preview!=null){cancelContext();return true;}client.setScreen(new GameMenuScreen(true));return true;}
        if(client.options.chatKey.matchesKey(key,scan)){client.setScreen(new ChatScreen(""));return true;}
        if(client.options.commandKey.matchesKey(key,scan)){client.setScreen(new ChatScreen("/"));return true;}
        if(client.options.inventoryKey.matchesKey(key,scan)&&client.player!=null){client.setScreen(new InventoryScreen(client.player));return true;}
        if(TacticalCombatClient.QUICK_SHEET_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.QuickPanelScreen(this,false));return true;}
        if(TacticalCombatClient.DM_QUICK_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.DmWorkspaceScreen(this));return true;}
        if(TacticalCombatClient.SHEET_KEY.matchesKey(key,scan)){CharacterSheetScreen.requestOpen();return true;}
        if(TacticalCombatClient.END_TURN_KEY.matchesKey(key,scan)){if(end.active)ClientSrdCombat.send("endturn");return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
