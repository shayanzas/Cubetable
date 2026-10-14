package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Dashboard entry uses the same allocation workspace and atomic server contract as the creator. */
public final class AbilityScoreScreen extends RpgScreen {
    private final String character;
    private final CreatorDraftState state;
    private CharacterCanvas pane;
    private boolean fresh;
    private Runnable resumed;
    public AbilityScoreScreen(Screen parent,JsonObject draft){super("Ability Points",parent);character=text(draft,"id");state=new CreatorDraftState(DraftReplies.snapshot(draft,character));}
    @Override protected int maximumWidth(){return 980;}
    @Override protected void init(){super.init();clearChildren();pane=null;busy=false;fresh=false;footer();button("Refresh","",frame.x()+frame.width()-204,frame.y()+frame.height()-30,90,20,this::reload);button("Save Scores","",frame.x()+frame.width()-108,frame.y()+frame.height()-30,96,20,this::save);build();reload();}
    private void build(){int offset=pane==null?0:pane.position();String focus=pane==null?"":pane.focusedKey();var cursors=pane==null?Map.<String,Integer>of():pane.cursors();if(pane!=null)remove(pane);pane=addDrawableChild(new CharacterCanvas(new UiLayout.Rect(frame.x()+12,frame.y()+36,frame.width()-24,Math.max(24,frame.height()-74))));AbilityWorkspace.build(pane,textRenderer,state,this::build,busy);pane.liveLabel(()->AbilityWorkspace.status(state),6,21,pane.getWidth()-12,RpgTheme.MUTED);pane.position(offset);pane.cursors(cursors);if(!focus.isEmpty()){pane.focus(focus);setInitialFocus(pane);}}
    private void reload(){if(busy)return;fresh=false;request(LIBRARY_GET,args("character",character),r->{if(r.code().equals("OK")){state.accept(DraftReplies.snapshot(r.data(),character),false);fresh=true;build();message=state.scoresDirty()?"Unsaved scores retained.":"Saved scores and method loaded.";}else message=r.message()+" Entered scores retained.";});}
    private void save(){if(!fresh||busy)return;if(state.reviewRequired()){long expected=revision(state.snapshot());client.setScreen(new RpgConfirmationScreen(this,"Review changed scores?","Server data changed. Keep these scores after reviewing the latest character?",()->{if(revision(state.snapshot())==expected){state.reviewed();resumed=this::save;}}));return;}try{var scores=new JsonObject();state.scoreValues().forEach(scores::addProperty);fresh=false;request(LIBRARY_UPDATE,args("character",character,"revision",revision(state.snapshot()),"section","abilityAllocation","data",args("abilities",scores,"method",state.method.name())),r->{if(r.code().equals("OK")){state.accept(DraftReplies.snapshot(r.data(),character),true);fresh=true;if(state.scoresDirty()){message="Sent scores saved; newer entries remain unsaved.";build();}else client.setScreen(parent);}else message=r.message()+" Scores retained; refresh before retrying.";});}catch(RuntimeException invalid){message=invalid.getMessage();messageColor=RpgTheme.ERROR;}}
    @Override public void tick(){super.tick();if(resumed!=null&&!busy&&fresh){var next=resumed;resumed=null;next.run();}}
    @Override public void close(){if(busy)return;if(state.scoresDirty())client.setScreen(new RpgConfirmationScreen(this,"Discard score edits?","Saved scores remain unchanged.",()->client.setScreen(parent)));else super.close();}
}
