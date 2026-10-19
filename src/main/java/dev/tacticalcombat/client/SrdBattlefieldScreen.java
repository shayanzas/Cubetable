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
import static dev.tacticalcombat.client.SrdBattlefieldModel.Rect;

/** World-first interaction: deliberate selection -> authoritative preview -> explicit confirmation. */
public final class SrdBattlefieldScreen extends Screen {
    private static final int GOLD=0xFFE3BD70,TEXT=0xFFE3E5E8,MUTED=0xFFADB5BF;
    private static int stepTicks=4;
    private final SrdBattlefieldInteraction context=new SrdBattlefieldInteraction();
    private final SrdBattlefieldPresentation.Orbit orbit=new SrdBattlefieldPresentation.Orbit();
    private SrdBattlefieldPresentation.Layout layout;
    private Rect popup,pacePopup,helpPopup;
    private final List<SrdAttackChoices.Choice> choices=new ArrayList<>();
    private boolean history,pace,help;
    private int choiceOffset,historyOffset,initiativeOffset;
    private String built="",seenResult="",feedback="";
    private double mouseX,mouseY;
    private long seenFailure;
    private String followedInitiative="";
    private boolean rightUiDown;
    private double rightUiX,rightUiY;
    private SrdBattlefieldInteraction.Scope scope(){return new SrdBattlefieldInteraction.Scope(String.valueOf(ClientSrdCombat.encounter),ClientSrdCombat.turn,actorId(),ClientSrdCombat.revision,SrdViewPolicy.permissions(ClientSrdCombat.state));}
    private String actorId(){return ClientSrdCombat.state.has("active")?ClientSrdCombat.state.get("active").getAsString():"";}
    private JsonObject target(){return SrdBattlefieldModel.actor(ClientSrdCombat.actors(),context.target());}
    private String name(JsonObject a){return a==null?"Actor":a.get("name").getAsString();}
    private String statuses(JsonObject a){return a==null?"":(a.has("dodging")&&a.get("dodging").getAsBoolean()?" · Dodge":"")+(a.has("prone")&&a.get("prone").getAsBoolean()?" · Prone":"");}
    private boolean ready(){return !orbit.active()&&ClientSrdCombat.canControl()&&!ClientSrdCombat.moving()&&ClientSrdCombat.pending.isEmpty();}
    private boolean attackReady(){return ready()&&!ClientSrdCombat.state.get("actionUsed").getAsBoolean()&&!context.target().equals(actorId());}
    private boolean manager(){return ClientSrdCombat.state.has("manager")&&ClientSrdCombat.state.get("manager").getAsBoolean();}
    private String unavailable(){return !ClientSrdCombat.active()?"Encounter ended":!ClientSrdCombat.state.get("state").getAsString().equals("ACTIVE")?"Encounter paused — DM adjudication required":!ClientSrdCombat.canControl()?"Not your controlled turn":!ClientSrdCombat.pending.isEmpty()?"Awaiting server: "+ClientSrdCombat.pending:ClientSrdCombat.moving()?"Movement in progress":"Action already spent or reserved";}
    public SrdBattlefieldScreen(){super(Text.literal("Cubetable tactical battlefield"));}
    public boolean shouldPause(){return false;}
    public void renderBackground(DrawContext g,int x,int y,float delta){}
    private void panel(DrawContext g,Rect r){g.fill(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),0xCF181C22);g.drawBorder(r.x(),r.y(),r.w(),r.h(),0xFF6B5B42);}
    private void line(DrawContext g,String value,int x,int y,int w,int color){g.drawText(textRenderer,textRenderer.trimToWidth(value,Math.max(1,w)),x,y,color,false);}
    private RpgButton button(String label,String hint,int x,int y,int w,int h,Runnable action,boolean active){
        var b=new RpgButton(x,y,w,h,label,"",action).hint(hint);b.active=active;addDrawableChild(b);return b;
    }
    protected void init(){layout=SrdBattlefieldPresentation.layout(width,height);rebuild();}
    public void removed(){orbit.reset();rightUiDown=false;super.removed();}
    private void cancel(){context.clear();ClientSrdCombat.cancelPreview();ClientSrdCombat.selected=ClientSrdCombat.hover=null;ClientSrdCombat.focusEntity=-1;feedback="";choiceOffset=0;pace=help=history=false;rebuild();}
    private void gatherChoices(){choices.clear();var a=ClientSrdCombat.current();if(a!=null&&a.has("weapons"))for(var weapon:a.getAsJsonArray("weapons"))for(var c:SrdAttackChoices.forWeapon(weapon.getAsString(),a.has("monsterAbility")?a.get("monsterAbility").getAsString():null))if(!choices.contains(c))choices.add(c);}
    private String choiceName(SrdAttackChoices.Choice c){return c.weapon().name().replace('_',' ')+" · "+c.mode().name().toLowerCase(Locale.ROOT)+" · "+c.ability()+(c.twoHands()?" · 2H":"");}
    private boolean validMove(){return ready()&&context.kind()==SrdBattlefieldInteraction.Kind.MOVE&&ClientSrdCombat.grid!=null&&ClientSrdCombat.gridRevision==ClientSrdCombat.revision&&ClientSrdCombat.movement.select(context.destination()).costFeet().orElse(0)>0;}
    private void rebuild(){
        if(layout==null)return;clearChildren();popup=pacePopup=helpPopup=null;gatherChoices();
        var bar=layout.bar();int cols=width<440?4:8,h=18,gap=3,w=(bar.w()-8-(cols-1)*gap)/cols;
        boolean prone=ClientSrdCombat.state.has("prone")&&ClientSrdCombat.state.get("prone").getAsBoolean();
        String[] labels={"Dash","Dodge",prone?"Stand "+ClientSrdCombat.state.get("standCost"):"Prone","End Turn","History","Pace: "+paceName(),"DM","?"};
        var bound=scope();
        for(int i=0;i<labels.length;i++){
            final int index=i;boolean enabled=switch(i){case 0->SrdBattlefieldActions.canDash(ClientSrdCombat.state,ClientSrdCombat.pending);case 1->SrdBattlefieldActions.canDodge(ClientSrdCombat.state,ClientSrdCombat.pending);case 2->SrdBattlefieldActions.canPosture(ClientSrdCombat.state,ClientSrdCombat.pending);case 3->ClientSrdCombat.canControl()&&ClientSrdCombat.pending.isEmpty();case 6->manager();default->true;};
            String hint=switch(i){case 0->enabled?"Spends normal action; adds "+ClientSrdCombat.state.get("dashFeet")+" ft. Does not move you":unavailable();case 1->enabled?"Spends normal action. Eligible incoming attacks have disadvantage; Dexterity saves advantage until next turn":unavailable();case 2->enabled?prone?"Stand: half-speed movement cost; no action":"Drop Prone: no action or movement cost":"Standing requires enough movement and positive speed; "+unavailable();case 3->enabled?"End this SRD turn":"Awaiting server or not your turn";case 4->"Recent authorized combat events";case 5->"Traversal timing only; movement costs stay the same";case 6->"Open the authorized DM Workspace";default->"Click ground to move; click actor to attack. Wheel zoom, right/middle drag orbit, WASD pan";};
            button(labels[i],hint,bar.x()+4+(i%cols)*(w+gap),bar.y()+3+(i/cols)*21,w,h,()->{
                if(index<4&&(!bound.equals(scope())||orbit.active()))return;
                switch(index){case 0->immediate("dash");case 1->immediate("dodge");case 2->immediate(prone?"stand":"drop_prone");case 3->{cancel();ClientSrdCombat.send("endturn");}case 4->{boolean open=!history;cancel();history=open;historyOffset=0;pace=help=false;rebuild();}case 5->{pace=!pace;help=history=false;rebuild();}case 6->client.setScreen(new dev.tacticalcombat.client.ui.DmWorkspaceScreen(this));case 7->{help=!help;pace=history=false;rebuild();}}
            },enabled);
        }
        var cam=layout.camera();boolean recovery=manager()&&ClientSrdCombat.state.has("state")&&ClientSrdCombat.state.get("state").getAsString().equals("PAUSED");
        button(recovery?"Recover":"Center",recovery?"Existing authorized DM recovery/adjudication controls":"Recenter on active actor",cam.x(),cam.y(),45,18,()->{if(recovery)client.setScreen(new SrdRecoveryScreen(this));else SrdCamera.pan=Vec3d.ZERO;},true);
        button("↻","Read-only refresh; never repeats a gameplay action",cam.x()+48,cam.y(),25,18,ClientSrdCombat::refreshReadOnly,true);
        if(pace){pacePopup=SrdBattlefieldPresentation.popup(bar.x()+bar.w()/2,bar.y(),142,92,width,height,bar);int[] values={1,2,4,6};String[] names={"Fast","Brisk","Walk","Slow"};for(int i=0;i<4;i++){int value=values[i];button(names[i]+(stepTicks==value?" ✓":""),value+" ticks per square; timing only",pacePopup.x()+4,pacePopup.y()+4+i*Math.min(21,(pacePopup.h()-8)/4),pacePopup.w()-8,Math.min(18,(pacePopup.h()-8)/4-2),()->{stepTicks=value;pace=false;rebuild();},true);}}
        if(help)helpPopup=SrdBattlefieldPresentation.popup(width/2,bar.y(),280,70,width,height,bar);
        if(pace||help||history)return;
        if(context.kind()==SrdBattlefieldInteraction.Kind.IDLE)return;
        int desired=context.kind()==SrdBattlefieldInteraction.Kind.MOVE?86:ClientSrdCombat.validPreview(context.target())?174:Math.min(165,70+Math.min(3,choices.size())*21);
        popup=SrdBattlefieldPresentation.popup(context.x(),context.y(),252,desired,width,height,bar);int x=popup.x()+5,y=popup.y()+popup.h()-23,inner=popup.w()-10;
        if(context.kind()==SrdBattlefieldInteraction.Kind.MOVE){button("Move","Commit this exact current server-reachable selection",x,y,(inner-3)/2,18,this::confirmMove,validMove());button("Cancel","No movement or action cost",x+(inner+3)/2,y,(inner-3)/2,18,this::cancel,true);return;}
        if(target()==null){cancel();return;}
        if(ClientSrdCombat.validPreview(context.target())){
            button("Confirm attack",ClientSrdCombat.preview.get("attack").getAsString()+"; "+ClientSrdCombat.preview.get("range").getAsString()+"; "+ClientSrdCombat.preview.get("cost").getAsString()+"; "+ClientSrdCombat.preview.get("stock").getAsString(),x,y,(inner-3)/2,18,this::confirmAttack,attackReady());
        }else if(attackReady()&&!choices.isEmpty()){
            if(choices.size()==1)button("Retry preview","Deliberate read-only validation; never automatic",x,y,(inner-3)/2,18,()->requestPreview(choices.getFirst()),true);
            else{
                int choiceTop=!feedback.isEmpty()&&popup.h()>=105?48:26;
                int capacity=Math.max(1,Math.min(3,(popup.h()-choiceTop-29)/21));choiceOffset=MathHelper.clamp(choiceOffset,0,Math.max(0,choices.size()-capacity));
                for(int i=0;i<capacity&&choiceOffset+i<choices.size();i++){var c=choices.get(choiceOffset+i);button(choiceName(c),choiceName(c)+"; server validates equipment, range and action",x,popup.y()+choiceTop+i*21,inner,18,()->requestPreview(c),true);}
            }
        }
        button("Cancel","Dismiss selection; no resource cost",x+(inner+3)/2,y,(inner-3)/2,18,this::cancel,true);
    }
    private String paceName(){return switch(stepTicks){case 1->"Fast";case 2->"Brisk";case 6->"Slow";default->"Walk";};}
    private void immediate(String op){if(!ready())return;var args=op.equals("dash")?SrdBattlefieldActions.dash(ClientSrdCombat.state,ClientSrdCombat.revision,ClientSrdCombat.pending):op.equals("dodge")?SrdBattlefieldActions.dodge(ClientSrdCombat.state,ClientSrdCombat.revision,ClientSrdCombat.pending):SrdBattlefieldActions.posture(ClientSrdCombat.state,ClientSrdCombat.revision,ClientSrdCombat.pending);cancel();ClientSrdCombat.send(op,args);}
    private void confirmMove(){if(!validMove()||!context.consumeMove(scope(),true))return;var c=context.destination();var args=new JsonObject();args.addProperty("x",c.x());args.addProperty("y",c.y());args.addProperty("z",c.z());args.addProperty("stepTicks",stepTicks);cancel();ClientSrdCombat.send("move",args);}
    private void requestPreview(SrdAttackChoices.Choice choice){if(!attackReady()||target()==null)return;try{feedback="";ClientSrdCombat.cancelPreview();ClientSrdCombat.send("preview",SrdBattlefieldActions.preview(ClientSrdCombat.state,context.target(),choice));rebuild();}catch(IllegalArgumentException rejected){feedback=rejected.getMessage();}}
    private void confirmAttack(){if(!attackReady()||!ClientSrdCombat.validPreview(context.target()))return;var args=SrdBattlefieldActions.confirmation(ClientSrdCombat.preview);cancel();ClientSrdCombat.send("confirm",args);}
    public void tick(){
        if(!ClientSrdCombat.active()){context.clear();orbit.reset();return;}
        if(seenFailure!=ClientSrdCombat.failureSerial){seenFailure=ClientSrdCombat.failureSerial;feedback=ClientSrdCombat.lastFailure;}
        var oldKind=context.kind();context.reconcile(scope());if(oldKind!=context.kind()){ClientSrdCombat.selected=null;ClientSrdCombat.focusEntity=-1;ClientSrdCombat.cancelPreview();feedback="";}
        if(!context.target().isEmpty()&&target()==null)cancel();
        if(context.kind()==SrdBattlefieldInteraction.Kind.MOVE&&ClientSrdCombat.grid!=null&&ClientSrdCombat.gridRevision==ClientSrdCombat.revision&&ClientSrdCombat.movement.select(context.destination()).status()!=SrdMovement.Reason.OK){cancel();ClientSrdCombat.activity.add("denied:grid:"+ClientSrdCombat.revision,"Destination no longer reachable — select again",ClientSrdCombat.ticks);}
        if(!ClientSrdCombat.lastResultId.isEmpty()&&!seenResult.equals(ClientSrdCombat.lastResultId)){seenResult=ClientSrdCombat.lastResultId;cancel();}
        String key=scope()+":"+context.kind()+context.target()+":"+ClientSrdCombat.pending+":"+(ClientSrdCombat.preview==null?"":ClientSrdCombat.preview.get("id"))+":"+ClientSrdCombat.message;
        if(!key.equals(built)){built=key;rebuild();}
        if(SrdCamera.engaged()){long handle=client.getWindow().getHandle();double f=(down(handle,GLFW.GLFW_KEY_W)?1:0)-(down(handle,GLFW.GLFW_KEY_S)?1:0),r=(down(handle,GLFW.GLFW_KEY_D)?1:0)-(down(handle,GLFW.GLFW_KEY_A)?1:0);var d=SrdBattlefieldPresentation.pan(SrdCamera.yawTarget,f,r);var p=SrdCamera.pan.add(d[0]*.4,0,d[1]*.4);SrdCamera.pan=p.length()>48?p.normalize().multiply(48):p;}
    }
    private boolean down(long handle,int key){return InputUtil.isKeyPressed(handle,key);}
    private boolean overUi(double x,double y){return layout.status().contains(x,y)||layout.initiative().contains(x,y)||layout.camera().contains(x,y)||layout.bar().contains(x,y)||history&&layout.history().contains(x,y)||popup!=null&&popup.contains(x,y)||pacePopup!=null&&pacePopup.contains(x,y)||helpPopup!=null&&helpPopup.contains(x,y);}
    private void pick(double x,double y,boolean click){
        if(overUi(x,y)||!SrdCamera.engaged()||orbit.active()){ClientSrdCombat.hover=null;ClientSrdCombat.hoverEntity=-1;ClientSrdCombat.unavailableHover=null;return;}
        var hit=SrdWorldPicker.pick(client,x,y);ClientSrdCombat.hover=hit.cell();var hovered=SrdBattlefieldModel.actor(ClientSrdCombat.actors(),hit.actor()==null?"":hit.actor());ClientSrdCombat.hoverEntity=hovered==null?-1:hovered.get("entity").getAsInt();ClientSrdCombat.unavailableHover=hit.unavailable();
        if(!click)return;
        ClientSrdCombat.cancelPreview();feedback="";pace=help=history=false;choiceOffset=0;
        if(hit.actor()!=null){ClientSrdCombat.selected=null;gatherChoices();boolean preview=context.actor(scope(),hit.actor(),choices.size(),ready()&&!ClientSrdCombat.state.get("actionUsed").getAsBoolean()&&!hit.actor().equals(actorId()),(int)x,(int)y);var a=target();ClientSrdCombat.focusEntity=a==null?-1:a.get("entity").getAsInt();rebuild();if(preview)requestPreview(choices.getFirst());}
        else if(ClientSrdCombat.grid!=null&&ClientSrdCombat.gridRevision==ClientSrdCombat.revision&&context.ground(scope(),hit.cell(),ClientSrdCombat.movement.select(hit.cell()),ready(),(int)x,(int)y)){ClientSrdCombat.selected=hit.cell();ClientSrdCombat.focusEntity=-1;rebuild();}
    }
    public void render(DrawContext g,int mx,int my,float delta){
        if(!ClientSrdCombat.active())return;mouseX=mx;mouseY=my;pick(mx,my,false);drawLabels(g);drawStatus(g);drawInitiative(g);panel(g,layout.bar());
        if(popup!=null)drawContext(g);if(pacePopup!=null)panel(g,pacePopup);
        if(history)drawHistory(g);
        if(helpPopup!=null){var r=helpPopup;panel(g,r);drawLines(g,List.of("Ground: select → Move / Cancel","Creature: choose attack → Confirm / Cancel","WASD pan · right/middle drag orbit · wheel zoom","Short right-click/Esc cancel · J character · DM shortcut"),r,5);}
        if(popup==null&&!overUi(mx,my)&&!orbit.active())drawHover(g,mx,my);
        var toast=ClientSrdCombat.activity.toast(ClientSrdCombat.ticks);if(toast!=null&&!history){var r=new Rect(5,layout.bar().y()-Math.min(42,height/4)-4,Math.min(340,width-10),Math.min(42,height/4));if(popup==null||!overlap(r,popup)){panel(g,r);var lines=textRenderer.wrapLines(Text.literal(toast.id().equals("attack:"+ClientSrdCombat.lastResultId)?ClientSrdCombat.lastResultSummary:SrdRecentActivity.category(toast.id())+" · "+toast.text()),r.w()-10);for(int i=0;i<Math.min((r.h()-6)/10,lines.size());i++)g.drawText(textRenderer,lines.get(i),r.x()+5,r.y()+4+i*10,TEXT,false);}}
        super.render(g,mx,my,delta);
    }
    private void drawStatus(DrawContext g){var r=layout.status();panel(g,r);var a=ClientSrdCombat.current();line(g,name(a)+statuses(a),r.x()+5,r.y()+4,r.w()-10,GOLD);boolean paused=ClientSrdCombat.state.has("state")&&!ClientSrdCombat.state.get("state").getAsString().equals("ACTIVE");line(g,paused?"Paused — DM adjudication required":r.h()<37?ClientSrdCombat.state.get("remainingFeet")+" ft · Action "+(ClientSrdCombat.state.get("actionUsed").getAsBoolean()?"spent":"ready"):SrdBattlefieldModel.hp(a),r.x()+5,r.y()+15,r.w()-10,TEXT);if(r.h()>37)line(g,ClientSrdCombat.state.get("remainingFeet")+" ft · Action "+(ClientSrdCombat.state.get("actionUsed").getAsBoolean()?"spent":"ready"),r.x()+5,r.y()+29,r.w()-10,MUTED);}
    private void drawInitiative(DrawContext g){var r=layout.initiative();panel(g,r);line(g,"Round "+(ClientSrdCombat.state.has("round")?ClientSrdCombat.state.get("round"):"—"),r.x()+4,r.y()+7,40,GOLD);var rows=SrdBattlefieldPresentation.initiative(ClientSrdCombat.state);int capacity=Math.max(1,(r.w()-70)/88),active=0;for(int i=0;i<rows.size();i++)if(rows.get(i).get("id").getAsString().equals(actorId()))active=i;if(!followedInitiative.equals(actorId())){followedInitiative=actorId();initiativeOffset=active;}initiativeOffset=Math.max(0,Math.min(initiativeOffset,Math.max(0,rows.size()-capacity)));for(int i=0;i<capacity&&initiativeOffset+i<rows.size();i++){var a=rows.get(initiativeOffset+i);boolean current=a.get("id").getAsString().equals(actorId());int x=r.x()+44+i*88;g.fill(x,r.y()+3,x+85,r.y()+r.h()-3,current?0x805C4D30:0x60292D32);line(g,(current?"› ":"")+name(a)+" "+a.get("initiative"),x+3,r.y()+7,79,current?GOLD:TEXT);}if(rows.size()>capacity)line(g,"↕",r.x()+r.w()-17,r.y()+7,12,GOLD);}
    private void drawContext(DrawContext g){panel(g,popup);var lines=new ArrayList<String>();if(context.kind()==SrdBattlefieldInteraction.Kind.MOVE){var s=ClientSrdCombat.movement.select(context.destination());lines.add("Move here?");lines.add(ClientSrdCombat.grid==null||ClientSrdCombat.gridRevision!=ClientSrdCombat.revision?"Updating route — confirmation disabled":"Cost "+s.costFeet().orElse(0)+" ft · "+s.remainingFeet()+" ft left");lines.add("Square "+context.destination().x()+", "+context.destination().z());}else{lines.add(name(target())+statuses(target()));if(ClientSrdCombat.validPreview(context.target())){var p=ClientSrdCombat.preview;lines.add(p.get("weapon").getAsString().replace('_',' '));lines.add(p.get("attack").getAsString()+" · "+p.get("rollMode").getAsString());lines.add(p.get("damage").getAsString()+" "+p.get("type").getAsString());lines.add(p.get("range").getAsString()+" · cover "+p.get("cover").getAsString());lines.add(p.get("cost").getAsString());lines.add(p.get("stock").getAsString());}else if(!ClientSrdCombat.pending.isEmpty())lines.add("Awaiting server: "+ClientSrdCombat.pending);else if(!attackReady()){lines.add(SrdBattlefieldModel.hp(target()));lines.add(unavailable());}else if(choices.isEmpty())lines.add("No supported attacks available");else if(choices.size()==1)lines.add(feedback.isEmpty()?"Preview needed — choose Retry preview":feedback);else if(!feedback.isEmpty()){if(popup.h()<105)lines.set(0,feedback);else lines.add(feedback);}}
        if(!feedback.isEmpty()&&ClientSrdCombat.validPreview(context.target()))lines.add(feedback);drawLines(g,lines,new Rect(popup.x(),popup.y(),popup.w(),Math.max(18,popup.h()-25)),5);
    }
    private void drawLines(DrawContext g,List<String> lines,Rect r,int padding){g.enableScissor(r.x()+padding,r.y()+padding,r.x()+r.w()-padding,r.y()+r.h()-padding);int y=r.y()+padding;for(var s:lines)for(var line:textRenderer.wrapLines(Text.literal(s),r.w()-padding*2)){g.drawText(textRenderer,line,r.x()+padding,y,TEXT,false);y+=10;}g.disableScissor();}
    private void drawHistory(DrawContext g){var r=layout.history();panel(g,r);var lines=new ArrayList<net.minecraft.text.OrderedText>();for(var e:ClientSrdCombat.activity.newest()){lines.addAll(textRenderer.wrapLines(Text.literal(SrdRecentActivity.category(e.id())+" · "+e.text()),r.w()-10));lines.add(Text.literal(" ").asOrderedText());}int visible=Math.max(1,(r.h()-10)/10);historyOffset=MathHelper.clamp(historyOffset,0,Math.max(0,lines.size()-visible));g.enableScissor(r.x()+4,r.y()+4,r.x()+r.w()-4,r.y()+r.h()-4);for(int i=historyOffset;i<Math.min(lines.size(),historyOffset+visible);i++)g.drawText(textRenderer,lines.get(i),r.x()+5,r.y()+5+(i-historyOffset)*10,TEXT,false);g.disableScissor();}

    private void drawHover(DrawContext g,int x,int y){String info=null;if(ClientSrdCombat.hover!=null){var s=ClientSrdCombat.movement.select(ClientSrdCombat.hover);info=s.costFeet().orElse(0)+" ft · "+s.remainingFeet()+" ft left";}else if(ClientSrdCombat.unavailableHover!=null){var v=ClientSrdCombat.unavailableHover;var cell=new SrdMovement.Cell((int)Math.floor(v.x),(int)Math.floor(v.z),v.y);var reasons=ClientSrdCombat.movement.select(cell).boundaryReasons();info=reasons.isEmpty()?"Unavailable — no authorized route":"Unavailable: "+reasons.toString().replace('_',' ');}if(info!=null){var r=SrdBattlefieldPresentation.popup(x,y,Math.min(245,textRenderer.getWidth(info)+12),27,width,height,layout.bar());panel(g,r);line(g,info,r.x()+5,r.y()+5,r.w()-10,GOLD);}}
    private void drawLabels(DrawContext g){if(client.world==null)return;var used=new ArrayList<Rect>();var rows=new ArrayList<JsonObject>();for(var row:ClientSrdCombat.actors())rows.add(row.getAsJsonObject());rows.sort(Comparator.comparingInt(a->a.get("id").getAsString().equals(actorId())?0:a.get("id").getAsString().equals(context.target())?1:2));for(var a:rows){var entity=client.world.getEntityById(a.get("entity").getAsInt());if(entity==null||!entity.getUuidAsString().equals(a.get("id").getAsString()))continue;var point=SrdWorldPicker.label(client,entity);if(point==null||overUi(point[0],point[1]))continue;boolean active=a.get("id").getAsString().equals(actorId());String text=(active?"› ":a.get("controlled").getAsBoolean()?"◇ ":"")+name(a)+statuses(a);text=textRenderer.trimToWidth(text,130);int w=textRenderer.getWidth(text),x=MathHelper.clamp(point[0]-w/2,3,Math.max(3,width-w-3)),y=MathHelper.clamp(point[1]-10,3,height-15);var box=new Rect(x-2,y-2,w+4,13);if(overlap(box,layout.status())||overlap(box,layout.initiative())||overlap(box,layout.bar())||popup!=null&&overlap(box,popup)||used.stream().anyMatch(r->overlap(r,box)))continue;used.add(box);g.fill(box.x(),box.y(),box.x()+box.w(),box.y()+box.h(),0x85181C22);g.drawText(textRenderer,text,x,y,active?GOLD:context.target().equals(a.get("id").getAsString())?0xFFD4B0F2:TEXT,false);}}
    private boolean overlap(Rect a,Rect b){return a.x()<b.x()+b.w()&&a.x()+a.w()>b.x()&&a.y()<b.y()+b.h()&&a.y()+a.h()>b.y();}
    public boolean mouseClicked(double x,double y,int button){
        if(button==0&&orbit.active())return true;
        if(button==1&&overUi(x,y)){rightUiDown=true;rightUiX=x;rightUiY=y;return true;}
        if(button==1||button==2)return orbit.begin(button,x,y,overUi(x,y));
        if(super.mouseClicked(x,y,button))return true;if(button!=0)return false;if(overUi(x,y))return true;pick(x,y,true);return true;
    }
    public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(orbit.drag(button,x,y)){SrdCamera.yawTarget+=(float)dx*.4f;SrdCamera.pitch=MathHelper.clamp(SrdCamera.pitch+(float)dy*.25f,25,80);BlockFade.markDirty();return true;}return orbit.active()||super.mouseDragged(x,y,button,dx,dy);}
    public boolean mouseReleased(double x,double y,int button){if(button==1&&rightUiDown){rightUiDown=false;if(Math.hypot(x-rightUiX,y-rightUiY)<5)cancel();return true;}if(button==1||button==2){boolean cancel=orbit.release(button);if(cancel)cancel();return true;}return super.mouseReleased(x,y,button);}
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(popup!=null&&popup.contains(x,y)&&choices.size()>1&&!ClientSrdCombat.validPreview(context.target())){choiceOffset-=Math.signum(vertical);rebuild();return true;}if(history&&layout.history().contains(x,y)){historyOffset-=Math.signum(vertical);return true;}if(layout.initiative().contains(x,y)){initiativeOffset-=Math.signum(vertical);return true;}SrdCamera.zoomTarget=MathHelper.clamp(SrdCamera.zoomTarget-vertical*2,8,40);return true;}
    public boolean keyPressed(int key,int scan,int modifiers){
        if(orbit.active()&&(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE||TacticalCombatClient.END_TURN_KEY.matchesKey(key,scan)))return true;
        if(key==GLFW.GLFW_KEY_ESCAPE){if(context.kind()!=SrdBattlefieldInteraction.Kind.IDLE||history||pace||help){history=false;cancel();return true;}client.setScreen(new GameMenuScreen(true));return true;}
        if(client.options.chatKey.matchesKey(key,scan)){client.setScreen(new ChatScreen(""));return true;}
        if(client.options.commandKey.matchesKey(key,scan)){client.setScreen(new ChatScreen("/"));return true;}
        if(client.options.inventoryKey.matchesKey(key,scan)&&client.player!=null){client.setScreen(new InventoryScreen(client.player));return true;}
        if(TacticalCombatClient.QUICK_SHEET_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.QuickPanelScreen(this,false));return true;}
        if(TacticalCombatClient.DM_QUICK_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.DmWorkspaceScreen(this));return true;}
        if(TacticalCombatClient.SHEET_KEY.matchesKey(key,scan)){CharacterSheetScreen.requestOpen();return true;}
        if(TacticalCombatClient.END_TURN_KEY.matchesKey(key,scan)){if(ClientSrdCombat.canControl()&&ClientSrdCombat.pending.isEmpty()){cancel();ClientSrdCombat.send("endturn");}return true;}
        return super.keyPressed(key,scan,modifiers);
    }
}
