package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.client.ui.RpgButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import java.util.*;

/** Authorized transient decision, independent of current-turn control. Never sends from render/init. */
public final class SrdReactionScreen extends Screen {
    private final Screen parent;
    private String built="";
    private final long initialFailure=ClientSrdCombat.failureSerial;
    private int offset,x,y,w,h;
    public SrdReactionScreen(Screen parent){super(Text.literal("Opportunity attack"));this.parent=parent instanceof SrdReactionScreen?null:parent;}
    public boolean shouldPause(){return false;}
    public void renderBackground(DrawContext g,int x,int y,float delta){}
    private JsonObject prompt(){return ClientSrdCombat.state.has("opportunity")?ClientSrdCombat.state.getAsJsonObject("opportunity"):null;}
    private boolean ready(){return prompt()!=null&&ClientSrdCombat.pending.isEmpty()&&ClientSrdCombat.state.get("state").getAsString().equals("ACTIVE");}
    private JsonObject scope(){var p=prompt();var a=new JsonObject();a.add("campaign",ClientSrdCombat.state.get("campaign"));a.add("actor",p.get("reactor"));a.add("target",p.get("target"));a.add("trigger",p.get("id"));a.addProperty("revision",ClientSrdCombat.revision);return a;}
    protected void init(){rebuild();}
    private void add(String label,int row,Runnable run,boolean enabled){var b=new RpgButton(x+6,y+row,w-12,18,label,"",run);if(label.startsWith("Confirm")&&ClientSrdCombat.reactionPreview!=null){var p=ClientSrdCombat.reactionPreview;b.hint(p.get("weapon").getAsString()+"; "+p.get("rollMode").getAsString()+"; attack +"+p.get("modifier")+"; damage "+p.get("damage").getAsString()+"; reach "+p.get("reach")+"; cover "+p.get("cover").getAsString()+"; one reaction, normal action unchanged");}b.active=enabled;addDrawableChild(b);}
    private void rebuild(){clearChildren();w=Math.min(290,width-12);h=Math.min(205,height-12);x=(width-w)/2;y=(height-h)/2;var p=prompt();if(p==null)return;
        long reviewed=ClientSrdCombat.revision;String trigger=p.get("id").getAsString();
        boolean ready=ready();var preview=ClientSrdCombat.reactionPreview;
        if(preview!=null){add("Confirm — spend reaction",h-64,()->{if(ready()&&ClientSrdCombat.revision==reviewed&&prompt().get("id").getAsString().equals(trigger)){var a=scope();a.add("intent",preview.get("id"));ClientSrdCombat.send("reaction_confirm",a);rebuild();}},ready);}
        else{var choices=p.getAsJsonArray("choices");int count=Math.max(1,Math.min(3,(h-105)/21));offset=Math.max(0,Math.min(offset,Math.max(0,choices.size()-count)));
            for(int i=0;i<count&&offset+i<choices.size();i++){var choice=choices.get(offset+i).getAsJsonObject();add(choice.get("weapon").getAsString().replace('_',' ')+" · "+choice.get("ability").getAsString()+(choice.get("twoHands").getAsBoolean()?" · 2H":""),58+i*21,()->{if(ready()&&ClientSrdCombat.revision==reviewed&&prompt().get("id").getAsString().equals(trigger)){var a=scope();choice.entrySet().forEach(e->a.add(e.getKey(),e.getValue()));ClientSrdCombat.send("reaction_preview",a);rebuild();}},ready);}
        }
        add("Decline — keep reaction",h-43,()->{if(ready()&&ClientSrdCombat.revision==reviewed&&prompt().get("id").getAsString().equals(trigger)){ClientSrdCombat.send("reaction_decline",scope());rebuild();}},ready);
        add("Back (decision remains pending)",h-22,this::close,true);
    }
    public void tick(){if(prompt()==null){close();return;}String next=ClientSrdCombat.revision+":"+ClientSrdCombat.pending+":"+ClientSrdCombat.reactionPreview;if(!next.equals(built)){built=next;rebuild();}}
    private String name(JsonElement id){var a=SrdBattlefieldModel.actor(ClientSrdCombat.actors(),id.getAsString());return a==null?"Actor":a.get("name").getAsString();}
    public void render(DrawContext g,int mx,int my,float delta){var p=prompt();if(p==null)return;g.fill(x,y,x+w,y+h,0xDC181C22);g.drawBorder(x,y,w,h,0xFFE3BD70);
        var lines=new ArrayList<String>();lines.add("OPPORTUNITY ATTACK · "+name(p.get("reactor")));lines.add(name(p.get("target"))+" is leaving reach");lines.add("Movement waits · ~"+Math.max(0,(p.get("ticksRemaining").getAsLong()-(ClientSrdCombat.ticks-ClientSrdCombat.opportunityReceivedTick)+19)/20)+"s to server timeout/decline");
        if(ClientSrdCombat.failureSerial>initialFailure)lines.set(2,"Rejected: "+ClientSrdCombat.lastFailure);
        var preview=ClientSrdCombat.reactionPreview;if(preview!=null){lines.add(preview.get("weapon").getAsString().replace('_',' ')+" · "+preview.get("rollMode").getAsString());lines.add("Attack +"+preview.get("modifier")+" · damage "+preview.get("damage").getAsString());lines.add("Reach "+preview.get("reach")+" ft · cover "+preview.get("cover").getAsString());lines.add("One reaction; normal action unchanged");}
        if(!ClientSrdCombat.pending.isEmpty())lines.add("Awaiting server confirmation");
        int top=y+6;g.enableScissor(x+4,y+4,x+w-4,y+h-66);for(var line:lines){g.drawText(textRenderer,textRenderer.trimToWidth(line,w-12),x+6,top,0xFFE3E5E8,false);top+=11;}g.disableScissor();super.render(g,mx,my,delta);if(ClientSrdCombat.failureSerial>initialFailure&&my>=y&&my<y+45)g.drawTooltip(textRenderer,Text.literal(ClientSrdCombat.lastFailure),mx,my);
    }
    public boolean mouseScrolled(double x,double y,double horizontal,double vertical){offset-=(int)Math.signum(vertical);rebuild();return true;}
    public void close(){if(client!=null)client.setScreen(ClientSrdCombat.active()?parent:null);}
}
