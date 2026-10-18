package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.SrdMovement;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Separate compact control panel; preserves Tactical's camera/HUD and existing sheet screens. */
public final class SrdCombatScreen extends Screen {
    private int weaponIndex,targetIndex,page;
    private boolean dex,twoHands;
    private int mode,choiceIndex;
    private int rosterPage;
    private long renderedRevision=-2;
    private JsonObject renderedPreview;
    private UUID renderedEncounter;
    private SrdMovement.Cell selected,hover;
    private List<SrdMovement.Cell> cells=List.of();
    private final List<String> tieChoice=new ArrayList<>();
    private final Screen parent;
    private boolean battlefieldOnReady;
    public SrdCombatScreen() {this(null);}
    public SrdCombatScreen(Screen parent) {super(Text.literal("SRD controlled encounter"));this.parent=parent;page=ClientSrdCombat.state.has("state")&&ClientSrdCombat.state.get("state").getAsString().equals("PENDING")?1:2;}
    public static Screen entry(Screen parent) {
        var screen=new SrdCombatScreen(parent);screen.battlefieldOnReady=true;return screen;
    }
    static void attackTarget(String id) {
        var screen=new SrdCombatScreen(new SrdBattlefieldScreen());screen.page=0;
        for(int i=0;i<ClientSrdCombat.actors().size();i++)if(ClientSrdCombat.actors().get(i).getAsJsonObject().get("id").getAsString().equals(id))screen.targetIndex=i;
        net.minecraft.client.MinecraftClient.getInstance().setScreen(screen);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(TacticalCombatClient.QUICK_SHEET_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.QuickPanelScreen(this,false));return true;}
        if(TacticalCombatClient.DM_QUICK_KEY.matchesKey(key,scan)){client.setScreen(new dev.tacticalcombat.client.ui.QuickPanelScreen(this,true));return true;}
        return super.keyPressed(key,scan,modifiers);
    }
    @Override public void close() {client.setScreen(parent);}
    /** Foreground is drawn before super.render; skip its late default background blur. */
    @Override public void renderBackground(DrawContext g,int mx,int my,float delta) { }
    @Override public boolean shouldPause() { return false; }
    private int left() { return (width-Math.min(440,width-16))/2; }
    private int panel() { return Math.min(440,width-16); }
    private ButtonWidget button(String text,int x,int y,int w,Runnable run) { return addDrawableChild(ButtonWidget.builder(Text.literal(text),b->run.run()).dimensions(x,y,w,20).build()); }
    @Override protected void init() {
        clearChildren(); int x=left(),w=panel(),half=(w-4)/2;
        button("Refresh",x,height-24,half,()->ClientSrdCombat.send("state"));
        button("Battlefield",x+half+4,height-24,half,()->client.setScreen(new SrdBattlefieldScreen()));
        if(ClientSrdCombat.encounter==null) return;
        button("End turn",x+half+4,height-48,half,()->ClientSrdCombat.send("endturn")).active=ClientSrdCombat.canControl();
        button(List.of("Movement / DM →","Status / initiative →","Attack controls →").get(page),x,46,w,()->{page=(page+1)%3;selected=null;init();});
        if(page==0 && ClientSrdCombat.canControl()) attackControls(x,w,half); else if(page==1) movementControls(x,w,half);
        else button("Next actors",x,height-48,half,()->{rosterPage++;init();});
    }
    private List<String> weapons() {
        var actor=ClientSrdCombat.current(); if(actor==null || !actor.has("weapons")) return List.of("UNARMED");
        return actor.getAsJsonArray("weapons").asList().stream().map(JsonElement::getAsString).distinct().toList();
    }
    private JsonObject target() {
        var actors=ClientSrdCombat.actors(); if(actors.isEmpty()) return null;
        var target=actors.get(Math.floorMod(targetIndex,actors.size())).getAsJsonObject(); ClientSrdCombat.focusEntity=target.get("entity").getAsInt();return target;
    }
    private String weapon() { var weapons=weapons(); return weapons.get(Math.floorMod(weaponIndex,weapons.size())); }
    private void attackControls(int x,int w,int half) {
        var current=ClientSrdCombat.current();if(current!=null&&current.has("monsterAbility"))dex=current.get("monsterAbility").getAsString().equals("DEX");
        if(ClientSrdCombat.preview!=null) {
            button("Confirm attack (spends action)",x,142,w,()->{
                var args=new JsonObject(); args.addProperty("intent",ClientSrdCombat.preview.get("id").getAsString()); ClientSrdCombat.preview=null; ClientSrdCombat.send("confirm",args); init();
            });
            button("Cancel preview",x,166,w,()->{ClientSrdCombat.preview=null;init();});
            return;
        }
        button("Weapon: "+weapon(),x,70,w,()->{weaponIndex++;ClientSrdCombat.preview=null;init();});
        var choices=SrdAttackChoices.forWeapon(weapon(),current!=null&&current.has("monsterAbility")?current.get("monsterAbility").getAsString():null);
        var choice=choices.get(Math.floorMod(choiceIndex,choices.size()));dex=choice.ability()==dev.tacticalcombat.rules.srd.SrdCharacter.Ability.DEX;
        twoHands=choice.twoHands();mode=List.of("MELEE","RANGED","THROWN").indexOf(choice.mode().name());
        button(choice.mode()+" / "+choice.ability()+" / "+(twoHands?"two hands":"one hand"),x,94,w,()->{choiceIndex++;ClientSrdCombat.preview=null;init();});
        button("Target: "+(target()==null?"none":target().get("name").getAsString()+" #"+target().get("entity")),x,118,w,()->{targetIndex++;ClientSrdCombat.preview=null;init();});
        button("Request attack preview",x,142,w,()->{
            if(target()==null) return;
            var args=new JsonObject(); args.addProperty("target",target().get("id").getAsString()); args.addProperty("weapon",weapon()); args.addProperty("ability",dex?"DEX":"STR"); args.addProperty("twoHands",twoHands);args.addProperty("mode",List.of("MELEE","RANGED","THROWN").get(mode)); ClientSrdCombat.send("preview",args);
        });
    }
    private void movementControls(int x,int w,int half) {
        button("Request reachable grid",x,70,half,()->ClientSrdCombat.send("grid")).active=ClientSrdCombat.canControl()&&!ClientSrdCombat.moving();
        button("Cancel route",x+half+4,70,half,()->ClientSrdCombat.send("cancel")).active=ClientSrdCombat.canControl();
        button("Move selected square",x,height-48,half,()->{
            if(selected==null) return; var args=new JsonObject(); args.addProperty("x",selected.x()); args.addProperty("z",selected.z()); args.addProperty("y",selected.y()); ClientSrdCombat.send("move",args); selected=null;
        }).active=ClientSrdCombat.canControl()&&!ClientSrdCombat.moving();
        if(ClientSrdCombat.state.get("manager").getAsBoolean()) {
            button("Retry saved attack",x,94,half,()->ClientSrdCombat.send("retry"));
            button("End encounter",x+half+4,94,half,()->client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(yes->{if(yes)ClientSrdCombat.send("end");client.setScreen(this);},Text.literal("End controlled encounter?"),Text.literal("Persistent D&D HP is retained. A failed save remains unresolved."))));
        }
        if(ClientSrdCombat.state.get("state").getAsString().equals("PENDING")) {
            button("Next tied actor: "+(target()==null?"none":target().get("name").getAsString()),x,118,w,()->{targetIndex++;init();});
            button("Append to tie order ("+tieChoice.size()+")",x,142,half,()->{if(target()!=null&&!tieChoice.contains(target().get("id").getAsString()))tieChoice.add(target().get("id").getAsString());init();});
            button("Submit / clear",x+half+4,142,half,()->{var args=new JsonObject();var order=new JsonArray();tieChoice.forEach(order::add);args.add("order",order);ClientSrdCombat.send("tie",args);tieChoice.clear();init();}).setTooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Choose each actor from one equal-initiative group in the desired order, then Submit. A DM resolves NPC/mixed ties. Every tied player must submit the same order for a player-only tie. See Status / initiative for totals.")));
        }
    }
    @Override public void tick() {
        if(battlefieldOnReady && ClientSrdCombat.active() && ClientSrdCombat.state.get("state").getAsString().equals("ACTIVE")) {
            client.setScreen(new SrdBattlefieldScreen());return;
        }
        if(renderedRevision!=ClientSrdCombat.revision || renderedPreview!=ClientSrdCombat.preview || !Objects.equals(renderedEncounter,ClientSrdCombat.encounter)) {
            renderedRevision=ClientSrdCombat.revision; renderedPreview=ClientSrdCombat.preview; renderedEncounter=ClientSrdCombat.encounter; selected=null; init();
        }
    }
    @Override public void render(DrawContext g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0xEE171C26); int x=left();
        g.drawText(textRenderer,"SRD 5.1 — public-position test",x,8,0xFFE4BC6C,false);
        String status=ClientSrdCombat.encounter==null?"No encounter":ClientSrdCombat.state.get("state").getAsString()+" | round "+ClientSrdCombat.state.get("round")+" | "+ClientSrdCombat.state.get("remainingFeet")+" ft left";
        g.drawText(textRenderer,textRenderer.trimToWidth(status,panel()),x,22,0xFFFFFFFF,false);
        g.drawText(textRenderer,textRenderer.trimToWidth(ClientSrdCombat.message,panel()),x,34,0xFFFFD090,false);
        if(page==0 && ClientSrdCombat.preview!=null) {
            var p=ClientSrdCombat.preview;
            g.drawText(textRenderer,p.get("weapon").getAsString(),x,72,0xFFFFFFFF,false);
            g.drawText(textRenderer,p.get("attack").getAsString()+"; "+p.get("range").getAsString(),x,86,0xFFFFFFFF,false);
            g.drawText(textRenderer,p.get("damage").getAsString()+" "+p.get("type").getAsString()+" ("+p.get("minimum")+"–"+p.get("maximum")+")",x,100,0xFFFFFFFF,false);
            g.drawText(textRenderer,"Cover: "+p.get("cover").getAsString()+"; costs one action",x,114,0xFFFFFFFF,false);
            g.drawText(textRenderer,textRenderer.trimToWidth(p.get("stock").getAsString(),panel()),x,128,0xFFFFFFFF,false);
        }
        if(page==1 && ClientSrdCombat.grid!=null) drawGrid(g,mx,my);
        if(page==2 && ClientSrdCombat.encounter!=null) {
            g.drawText(textRenderer,"Action: "+(ClientSrdCombat.state.get("actionUsed").getAsBoolean()?"spent / reserved":"available"),x,72,0xFFFFFFFF,false);
            var actors=ClientSrdCombat.actors();int capacity=Math.max(1,(height-145)/34);int pages=Math.max(1,(actors.size()+capacity-1)/capacity);
            int from=Math.floorMod(rosterPage,pages)*capacity;
            for(int i=from;i<Math.min(from+capacity,actors.size());i++) {
                var actor=actors.get(i).getAsJsonObject();int y=90+(i-from)*34;
                String line=(actor.get("id").getAsString().equals(ClientSrdCombat.state.get("active").getAsString())?"> ":"  ")+actor.get("name").getAsString()+" | initiative "+actor.get("initiative");
                g.drawText(textRenderer,textRenderer.trimToWidth(line,panel()),x,y,0xFFFFFFFF,false);
                String hp=actor.has("hp")?"HP "+actor.get("hp")+"/"+actor.get("maximumHp")+" +"+actor.get("temporaryHp")+" temp; arrows "+actor.get("arrows")+" bolts "+actor.get("bolts")+" daggers "+actor.get("daggers"):"Private character resources";
                g.drawText(textRenderer,textRenderer.trimToWidth(hp,panel()),x,y+13,0xFFADBBD0,false);
            }
        }
        super.render(g,mx,my,delta);
    }
    private void drawGrid(DrawContext g,int mx,int my) {
        var grid=ClientSrdCombat.grid; var start=grid.start(); int size=10,cx=width/2,cy=(height+120)/2;
        cells=ClientSrdCombat.movement.localWindow();hover=null;
        var path=ClientSrdCombat.movement.pathCells(selected);
        for(var cell:cells) {
            int px=cx+(cell.x()-start.x())*size,py=cy+(cell.z()-start.z())*size;
            if(py<120 || py+size>height-70) continue;
            g.fill(px,py,px+size-1,py+size-1,path.contains(cell)?0xFFD9BB54:0xFF326B98);
            if(mx>=px && mx<px+size && my>=py && my<py+size) hover=cell;
        }
        var focus=hover==null?selected:hover;
        if(focus!=null) { var choice=ClientSrdCombat.movement.select(focus); g.drawText(textRenderer,"Cost "+choice.costFeet().orElse(0)+" ft; remaining "+choice.remainingFeet()+" ft",left(),height-65,0xFFFFFFFF,false); }
        else g.drawText(textRenderer,"Local grid window; "+(grid.truncated()?"search truncated":"click square, then Move"),left(),height-65,0xFFFFFFFF,false);
    }
    @Override public boolean mouseClicked(double x,double y,int button) { if(page==1 && hover!=null && button==0) { selected=hover; return true; } return super.mouseClicked(x,y,button); }
}
