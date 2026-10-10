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
    private int mode;
    private int rosterPage;
    private long renderedRevision=-2;
    private JsonObject renderedPreview;
    private UUID renderedEncounter;
    private SrdMovement.Cell selected,hover;
    private List<SrdMovement.Cell> cells=List.of();
    private final List<String> tieChoice=new ArrayList<>();
    public SrdCombatScreen() { super(Text.literal("SRD controlled encounter")); }
    @Override public boolean shouldPause() { return false; }
    private int left() { return (width-Math.min(440,width-16))/2; }
    private int panel() { return Math.min(440,width-16); }
    private void button(String text,int x,int y,int w,Runnable run) { addDrawableChild(ButtonWidget.builder(Text.literal(text),b->run.run()).dimensions(x,y,w,20).build()); }
    @Override protected void init() {
        clearChildren(); int x=left(),w=panel(),half=(w-4)/2;
        button("Refresh",x,height-24,half,()->ClientSrdCombat.send("state"));
        button("Close",x+half+4,height-24,half,this::close);
        if(ClientSrdCombat.encounter==null) return;
        button(List.of("Movement / DM →","Status / initiative →","Attack controls →").get(page),x,46,w,()->{page=(page+1)%3;selected=null;init();});
        if(page==0) attackControls(x,w,half); else if(page==1) movementControls(x,w,half);
        else button("Next actors",x,height-48,w,()->{rosterPage++;init();});
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
        if(ClientSrdCombat.preview!=null) {
            button("Confirm attack (spends action)",x,142,w,()->{
                var args=new JsonObject(); args.addProperty("intent",ClientSrdCombat.preview.get("id").getAsString()); ClientSrdCombat.preview=null; ClientSrdCombat.send("confirm",args); init();
            });
            button("Cancel preview",x,166,w,()->{ClientSrdCombat.preview=null;init();});
            return;
        }
        button("Weapon: "+weapon(),x,70,w,()->{weaponIndex++;ClientSrdCombat.preview=null;init();});
        int third=(w-8)/3;
        button(dex?"DEX":"STR",x,94,third,()->{dex=!dex;ClientSrdCombat.preview=null;init();});
        button(twoHands?"Two hands":"One hand",x+third+4,94,third,()->{twoHands=!twoHands;ClientSrdCombat.preview=null;init();});
        button(List.of("Melee","Ranged","Thrown").get(mode),x+2*third+8,94,third,()->{mode=(mode+1)%3;ClientSrdCombat.preview=null;init();});
        button("Target: "+(target()==null?"none":target().get("name").getAsString()+" #"+target().get("entity")),x,118,w,()->{targetIndex++;ClientSrdCombat.preview=null;init();});
        button("Request attack preview",x,142,w,()->{
            if(target()==null) return;
            var args=new JsonObject(); args.addProperty("target",target().get("id").getAsString()); args.addProperty("weapon",weapon()); args.addProperty("ability",dex?"DEX":"STR"); args.addProperty("twoHands",twoHands);args.addProperty("mode",List.of("MELEE","RANGED","THROWN").get(mode)); ClientSrdCombat.send("preview",args);
        });
        button("End turn",x,height-48,w,()->ClientSrdCombat.send("endturn"));
    }
    private void movementControls(int x,int w,int half) {
        button("Request reachable grid",x,70,half,()->ClientSrdCombat.send("grid"));
        button("Cancel route",x+half+4,70,half,()->ClientSrdCombat.send("cancel"));
        button("Move to selected square",x,height-48,w,()->{
            if(selected==null) return; var args=new JsonObject(); args.addProperty("x",selected.x()); args.addProperty("z",selected.z()); args.addProperty("y",selected.y()); ClientSrdCombat.send("move",args); selected=null;
        });
        if(ClientSrdCombat.state.get("manager").getAsBoolean()) {
            button("Retry saved attack",x,94,half,()->ClientSrdCombat.send("retry"));
            button("End encounter",x+half+4,94,half,()->client.setScreen(new net.minecraft.client.gui.screen.ConfirmScreen(yes->{if(yes)ClientSrdCombat.send("end");client.setScreen(this);},Text.literal("End controlled encounter?"),Text.literal("Persistent D&D HP is retained. A failed save remains unresolved."))));
        }
        if(ClientSrdCombat.state.get("state").getAsString().equals("PENDING")) {
            button("Next tied actor: "+(target()==null?"none":target().get("name").getAsString()),x,118,w,()->{targetIndex++;init();});
            button("Append to tie order",x,142,half,()->{if(target()!=null&&!tieChoice.contains(target().get("id").getAsString()))tieChoice.add(target().get("id").getAsString());});
            button("Submit / clear",x+half+4,142,half,()->{var args=new JsonObject();var order=new JsonArray();tieChoice.forEach(order::add);args.add("order",order);ClientSrdCombat.send("tie",args);tieChoice.clear();});
        }
    }
    @Override public void tick() {
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
        cells=grid.reachable().keySet().stream().filter(c->Math.abs(c.x()-start.x())<=10 && Math.abs(c.z()-start.z())<=6).sorted(Comparator.comparingDouble(SrdMovement.Cell::y)).toList(); hover=null;
        var path=selected==null?List.<SrdMovement.Cell>of():ClientSrdCombat.movement.select(selected).path();
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
