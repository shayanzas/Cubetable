package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Optional multiline narrative. Retained widgets keep text, selection and caret on resize. */
public final class CharacterDetailsScreen extends RpgScreen {
    private static final List<String> KEYS=List.of("appearance","biography","personality","ideals","bonds","flaws","alignment","age","height");
    private final String character;
    private JsonObject baseline,snapshot;
    private static final class NarrativeBox extends EditBoxWidget {
        NarrativeBox(net.minecraft.client.font.TextRenderer font,int x,int y,int w,int h,String field){super(font,x,y,w,h,Text.literal("Tell your story…"),Text.literal(field));}
        double scroll(){return getScrollY();}
        void scroll(double value){setScrollY(value);}
    }
    private final Map<String,NarrativeBox> editors=new HashMap<>();
    private final Map<String,String> values=new LinkedHashMap<>();
    private String field="biography",reviewed;
    private boolean dirty,fresh;
    public CharacterDetailsScreen(Screen parent,JsonObject draft){super("Character Details · optional",parent);character=text(draft,"id");baseline=draft.deepCopy();var i=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");for(String k:KEYS)values.put(k,text(i,k));}
    @Override protected void init(){super.init();clearChildren();busy=false;fresh=false;int x=frame.x()+12,w=frame.width()-24;
        for(int i=0;i<KEYS.size();i++){String key=KEYS.get(i);int bw=(w-10)/3;button(Character.toUpperCase(key.charAt(0))+key.substring(1),"",x+i%3*(bw+5),frame.y()+36+i/3*18,bw,17,()->{field=key;init();}).selected(field.equals(key));}
        var box=editors.get(field);int caret=-1,anchor=-1;double scroll=0;if(box!=null&&box.getWidth()!=w){var old=((dev.tacticalcombat.mixin.client.NarrativeEditBoxAccessor)(EditBoxWidget)box).cubetable$editBox();caret=old.getCursor();anchor=((dev.tacticalcombat.mixin.client.NarrativeSelectionAccessor)old).cubetable$anchor();scroll=box.scroll();box=null;}
        if(box==null){box=new NarrativeBox(textRenderer,x,frame.y()+94,w,Math.max(28,frame.height()-134),field);box.setMaxLength(Set.of("alignment","age","height").contains(field)?128:2048);box.setText(values.get(field));String key=field;box.setChangeListener(v->{values.put(key,v);dirty=true;});editors.put(field,box);}else box.setDimensionsAndPosition(w,Math.max(28,frame.height()-134),x,frame.y()+94);
        if(caret>=0){var edit=((dev.tacticalcombat.mixin.client.NarrativeEditBoxAccessor)(EditBoxWidget)box).cubetable$editBox();edit.moveCursor(net.minecraft.client.input.CursorMovement.ABSOLUTE,anchor);edit.setSelecting(true);edit.moveCursor(net.minecraft.client.input.CursorMovement.ABSOLUTE,caret);edit.setSelecting(false);box.scroll(scroll);}
        box.scroll(box.scroll());addDrawableChild(box);setInitialFocus(box);footer();button("Save details","",frame.x()+frame.width()-120,frame.y()+frame.height()-30,108,20,this::save);
        request(LIBRARY_GET,args("character",character),r->{if(r.code().equals("OK")){snapshot=DraftReplies.snapshot(r.data(),character);fresh=true;message="Optional story fields. Your text stays here until saved or discarded.";}});
    }
    private void save(){if(!fresh||busy)return;if(ManagementState.needsEditReview(baseline,snapshot)){String version=snapshot.toString();client.setScreen(new RpgConfirmationScreen(this,"Review changed character?","Your character changed while these details were open. Keep your staged story and apply it to the reviewed character?\n\nYour story: "+values+"\n\nSaved details: "+SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity"),()->reviewed=version));return;}
        var i=SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity");values.forEach(i::addProperty);request(LIBRARY_UPDATE,args("character",character,"revision",revision(snapshot),"section","identity","data",i),r->{if(r.code().equals("OK")){dirty=false;client.setScreen(parent);}else{fresh=false;message=r.message()+" Text retained. Reopen after reviewing the saved character.";}});
    }
    @Override public void tick(){super.tick();if(reviewed!=null&&!busy){String v=reviewed;reviewed=null;if(fresh&&snapshot.toString().equals(v)){baseline=snapshot.deepCopy();save();}else message="Character changed again. Review before saving your retained text.";}}
    @Override public void close(){if(busy)return;if(dirty)client.setScreen(new RpgConfirmationScreen(this,"Discard unsaved story?","Saved character details remain unchanged.",()->client.setScreen(parent)));else super.close();}
}
