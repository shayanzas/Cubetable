package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.client.ui.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Small scoped recovery UI. All operations use the existing SRD networking/transaction boundary. */
public final class SrdRecoveryScreen extends RpgScreen {
    private final SrdRecoveryReview review=new SrdRecoveryReview();
    private CharacterCanvas canvas;private int y;private String target="",amount="1",confirmation="";
    private SrdRecoveryReview.Scope rendered;private String renderedPending="",lastServerMessage="";
    public SrdRecoveryScreen(Screen parent){super("Paused encounter recovery",parent);}
    private SrdRecoveryReview.Scope scope(){return new SrdRecoveryReview.Scope(ClientSrdCombat.encounter,ClientSrdCombat.revision,flag(ClientSrdCombat.state,"manager"),text(ClientSrdCombat.state,"state"));}
    private void line(String text){canvas.wrapped(text,8,y,canvas.getWidth()-16,RpgTheme.TEXT);y+=Math.max(1,textRenderer.wrapLines(Text.literal(text),canvas.getWidth()-16).size())*12+6;}
    private void action(String title,boolean enabled,Runnable run){var b=canvas.control("action:"+y,new RpgButton(8,y,canvas.getWidth()-16,22,title,"",run));b.active=enabled;y+=26;}
    private boolean ready(){return scope().manager()&&scope().state().equals("PAUSED")&&ClientSrdCombat.pending.isEmpty();}
    @Override protected void background(net.minecraft.client.gui.DrawContext g){
        RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),0xEE191D23,false);
        RpgTheme.text(g,textRenderer,"PAUSED ENCOUNTER RECOVERY",frame.x()+10,frame.y()+12,frame.width()-20,RpgTheme.GOLD);
    }
    @Override protected int maximumWidth(){return 460;}
    @Override protected void init(){super.init();build();}
    private void build(){
        clearChildren();canvas=new CharacterCanvas(new UiLayout.Rect(frame.x()+6,frame.y()+36,frame.width()-12,Math.max(30,frame.height()-78)));addDrawableChild(canvas);y=4;
        rendered=scope();renderedPending=ClientSrdCombat.pending;
        int column=(frame.width()-24)/3;
        button("Back","",frame.x()+8,frame.y()+frame.height()-32,column,20,this::close);
        button("Battlefield","",frame.x()+12+column,frame.y()+frame.height()-32,column,20,()->client.setScreen(new SrdBattlefieldScreen()));
        button("Refresh","",frame.x()+16+column*2,frame.y()+frame.height()-32,column,20,()->{review.clear();ClientSrdCombat.refreshReadOnly();build();}).active=ClientSrdCombat.pending.isEmpty();
        if(rendered.encounter()==null){line("Encounter ended. Committed HP and resources remain saved.");return;}
        line(text(ClientSrdCombat.state,"reason"));
        if(!rendered.manager()){line("Read-only status. A current campaign manager must adjudicate recovery.");return;}
        if(!rendered.state().equals("PAUSED")){line("This encounter is not paused. Return to the battlefield.");return;}
        if(!ClientSrdCombat.pending.isEmpty()){line("Awaiting server confirmation. No operation is retried automatically.");return;}
        if(review.current(scope())){
            line(confirmation);action("Confirm",ready(),()->{String op=review.operation();var args=review.consume(scope(),!ClientSrdCombat.pending.isEmpty());if(args!=null){ClientSrdCombat.send(op,args);build();}});
            action("Cancel",true,()->{review.clear();build();});return;
        }
        var recovery=ClientSrdCombat.state.has("recovery")?ClientSrdCombat.state.getAsJsonObject("recovery"):new JsonObject();
        if(SrdRecoveryReview.mayHeal(ClientSrdCombat.state)){
            line("Healing saves D&D HP. Combat stays paused: explicitly end this encounter to resume exploration. No death saves or resurrection are automated.");
            for(var value:ClientSrdCombat.actors()){
                var actor=value.getAsJsonObject();String id=text(actor,"id");
                action((id.equals(target)?"✓ ":"")+text(actor,"name")+" · "+SrdBattlefieldModel.hp(actor),ready(),()->{target=id;build();});
            }
            line("Healing amount: whole number 1–1024; server caps at maximum HP.");
            var field=new TextFieldWidget(textRenderer,8,y,canvas.getWidth()-16,20,Text.literal("Healing amount"));field.setMaxLength(4);field.setText(amount);field.setChangedListener(s->amount=s);canvas.control("amount",field);y+=26;
            action("Review healing…",ready(),()->{
                int n=SrdRecoveryReview.amount(amount);var actor=SrdBattlefieldModel.actor(ClientSrdCombat.actors(),target);
                if(n==0||actor==null){message="Choose a current actor and enter an amount from 1 to 1024.";return;}
                review.prepare(scope(),"heal",target,n);confirmation="Heal "+text(actor,"name")+" by "+n+" HP (capped by the server)? This will not resume combat.";build();
            });
        }else{
            line("Healing is unavailable. Resolve the saved attack or review its durable operation; do not reroll or use healing to resolve a save failure.");
            action("Review / retry saved attack…",ready()&&flag(recovery,"canRetry"),()->{review.prepare(scope(),"retry",null,0);confirmation="Retry persistence using the existing fixed attack dice? No new attack is rolled. Permission or revision conflicts may still require transaction review.";build();});
            action("Gameplay transaction review",true,()->client.setScreen(new GameplayRecoveryScreen(this)));
        }
        action("End encounter…",ready(),()->{review.prepare(scope(),"end",null,0);confirmation="End this controlled encounter? Committed HP/resources remain saved. Unresolved prepared operations remain subject to transaction review; ending does not reroll or cancel them.";build();});
    }
    @Override public void tick(){super.tick();if(!lastServerMessage.equals(ClientSrdCombat.message)){lastServerMessage=ClientSrdCombat.message;message=lastServerMessage;}if(!Objects.equals(rendered,scope())||!renderedPending.equals(ClientSrdCombat.pending)){review.clear();build();}}
    @Override public void removed(){review.clear();super.removed();}
}
