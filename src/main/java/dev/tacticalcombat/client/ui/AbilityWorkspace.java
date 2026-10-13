package dev.tacticalcombat.client.ui;

import java.util.*;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Same-tab score controls; unchanged effective-score/server contract. */
public final class AbilityWorkspace {
    public static final Map<String,String> NAMES=Map.of("str","Strength","dex","Dexterity","con","Constitution","int","Intelligence","wis","Wisdom","cha","Charisma");
    public record Layout(int columns,int stride,int top) {}
    public static Layout layout(int width){return new Layout(width>=440?3:width>=180?2:1,34,32);}
    private AbilityWorkspace() {}
    public static void build(CharacterCanvas pane,TextRenderer font,CreatorDraftState state,Runnable rebuild,boolean pending) {
        int w=pane.getWidth()-12,x=6,bw=(w-8)/3;
        String[] labels={"Standard array","Point buy","Manual / external"};
        WizardModel.ScoreMethod[] modes={WizardModel.ScoreMethod.STANDARD_ARRAY,WizardModel.ScoreMethod.POINT_BUY,WizardModel.ScoreMethod.MANUAL};
        for(int i=0;i<3;i++){var mode=modes[i];var b=new RpgButton(x+i*(bw+4),2,bw,18,labels[i],"",()->{if(pending)return;if(mode==WizardModel.ScoreMethod.STANDARD_ARRAY)state.standardArray();else state.method=mode;rebuild.run();});b.selected(state.method==mode||mode==WizardModel.ScoreMethod.MANUAL&&state.method==WizardModel.ScoreMethod.EXTERNAL_ROLLS).hint(labels[i]);pane.control("mode:"+i,b);}
        var geometry=layout(w);int cols=geometry.columns(),cw=(w-(cols-1)*6)/cols;
        for(int i=0;i<CreatorDraftState.ABILITIES.size();i++){
            String key=CreatorDraftState.ABILITIES.get(i);int fx=x+i%cols*(cw+6),y=geometry.top()+i/cols*geometry.stride();
            pane.icon(key,fx,y,12);pane.label(NAMES.get(key),fx+14,y,cw-14,RpgTheme.GOLD);
            int inputWidth=Math.max(18,cw-64);var field=new TextFieldWidget(font,fx+18,y+12,inputWidth,17,Text.literal(NAMES.get(key)));field.setMaxLength(2);field.setText(state.scores.get(key));field.setChangedListener(v->state.scores.put(key,v));pane.control(key,field);
            pane.control("minus:"+key,new RpgButton(fx,y+12,16,17,"−","",()->adjust(state,key,-1,field,pending)).accessible("Decrease "+NAMES.get(key)));
            pane.control("plus:"+key,new RpgButton(fx+20+inputWidth,y+12,16,17,"+","",()->adjust(state,key,1,field,pending)).accessible("Increase "+NAMES.get(key)));
            pane.liveLabel(()->modifier(state.scores.get(key)),fx+40+inputWidth,y+15,24,RpgTheme.TEXT);
        }
        int bottom=geometry.top()+((6+cols-1)/cols)*geometry.stride()+3;
        pane.control("roll",new RpgButton(x,bottom,w,18,"In-game ability rolls unavailable","",()->{}).hint("Generic manual dice has no character/ability-generation authorization, six-roll assignment or durable anti-reroll contract.")).active=false;
        pane.label("No racial increases are applied. Saved combat scores are effective values.",x,bottom+24,w,RpgTheme.MUTED);
        pane.label("Point buy plans base scores. Enter racial adjustments manually only in Manual mode.",x,bottom+38,w,RpgTheme.MUTED);
        String saved=CreatorDraftState.ABILITIES.stream().map(k->k.toUpperCase(Locale.ROOT)+" "+CreatorDraftState.savedScore(state.snapshot(),k)).reduce((a,b)->a+" · "+b).orElse("");pane.label("Saved effective: "+saved,x,bottom+52,w,RpgTheme.MUTED);
    }
    public static String status(CreatorDraftState state){try{var values=state.scoreValues();return state.method==WizardModel.ScoreMethod.POINT_BUY?WizardModel.pointRemaining(values)+" / 27 points remaining":(state.method==WizardModel.ScoreMethod.MANUAL||state.method==WizardModel.ScoreMethod.EXTERNAL_ROLLS?"Manual/external · unverified · ":"")+"Scores valid · racial increases not automatic";}catch(RuntimeException invalid){return invalid.getMessage();}}
    private static String modifier(String value){try{int m=WizardModel.modifier(Integer.parseInt(value));return (m>=0?"+":"")+m;}catch(RuntimeException invalid){return "—";}}
    private static void adjust(CreatorDraftState state,String key,int delta,TextFieldWidget field,boolean pending){if(pending)return;try{int value=Integer.parseInt(state.scores.get(key));int lo=state.method==WizardModel.ScoreMethod.POINT_BUY?8:1,hi=state.method==WizardModel.ScoreMethod.POINT_BUY?15:30;field.setText(Integer.toString(Math.clamp(value+delta,lo,hi)));}catch(NumberFormatException ignored){}}
}
