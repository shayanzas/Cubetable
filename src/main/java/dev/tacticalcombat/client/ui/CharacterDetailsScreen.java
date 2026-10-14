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
    static final List<String> KEYS=List.of("appearance","biography","personality","ideals","bonds","flaws");
    private final String character;
    private final DashboardSource source;
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
    public CharacterDetailsScreen(Screen parent,JsonObject draft){this(parent,draft,new DashboardSource(true,text(draft,"id")));}
    public CharacterDetailsScreen(Screen parent,JsonObject draft,DashboardSource source){super(source.library()?"Character Details":"Character Background",parent);this.source=source;character=text(draft,"id");baseline=draft.deepCopy();var i=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");for(String k:KEYS)values.put(k,text(i,k));}
    @Override protected void init(){super.init();clearChildren();busy=false;fresh=false;int x=frame.x()+12,w=frame.width()-24;boolean compact=frame.height()<170||w<240;int editorY=frame.y()+(compact?58:76),editorHeight=Math.max(1,frame.height()-(compact?98:116));
        if(compact)button("Editing: "+Character.toUpperCase(field.charAt(0))+field.substring(1),"",x,frame.y()+36,w,18,()->{if(!busy)client.setScreen(new ChoiceScreen(this,"Character Details field",KEYS,k->Character.toUpperCase(k.charAt(0))+k.substring(1),k->{field=k;client.setScreen(this);}));});else for(int i=0;i<KEYS.size();i++){String key=KEYS.get(i);int bw=(w-10)/3;button(Character.toUpperCase(key.charAt(0))+key.substring(1),"",x+i%3*(bw+5),frame.y()+36+i/3*18,bw,17,()->{if(!busy){field=key;init();}}).selected(field.equals(key));}
        var box=editors.get(field);int caret=-1,anchor=-1;double scroll=0;if(box!=null&&box.getWidth()!=w){var old=((dev.tacticalcombat.mixin.client.NarrativeEditBoxAccessor)(EditBoxWidget)box).cubetable$editBox();caret=old.getCursor();anchor=((dev.tacticalcombat.mixin.client.NarrativeSelectionAccessor)old).cubetable$anchor();scroll=box.scroll();box=null;}
        if(box==null){box=new NarrativeBox(textRenderer,x,editorY,w,editorHeight,field);box.setMaxLength(Set.of("alignment","age","height").contains(field)?128:2048);box.setText(values.get(field));String key=field;box.setChangeListener(v->{values.put(key,v);dirty=true;});editors.put(field,box);}else box.setDimensionsAndPosition(w,editorHeight,x,editorY);
        if(caret>=0){var edit=((dev.tacticalcombat.mixin.client.NarrativeEditBoxAccessor)(EditBoxWidget)box).cubetable$editBox();edit.moveCursor(net.minecraft.client.input.CursorMovement.ABSOLUTE,anchor);edit.setSelecting(true);edit.moveCursor(net.minecraft.client.input.CursorMovement.ABSOLUTE,caret);edit.setSelecting(false);box.scroll(scroll);}
        box.scroll(box.scroll());addDrawableChild(box);setInitialFocus(box);footer();button("Save details","",frame.x()+frame.width()-120,frame.y()+frame.height()-30,108,20,this::save);
        wizardRail(CreatorFlow.Step.DETAILS,()->!dirty);
        request(source.read(),source.readArgs(),r->{if(r.code().equals("OK")){snapshot=SnapshotReplies.checked(source,r.data());fresh=true;message="Optional story fields. Your text stays here until saved or discarded.";}});
    }
    private void save(){if(!fresh||busy)return;if(ManagementState.needsEditReview(baseline,snapshot)){String version=snapshot.toString();client.setScreen(new RpgConfirmationScreen(this,"Review changed character?","Your character changed while these details were open. Keep your staged story and apply it to the reviewed character?\n\nYour story: "+values+"\n\nSaved details: "+SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity"),()->reviewed=version));return;}
        var sent=Map.copyOf(values);var i=SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity");sent.forEach(i::addProperty);fresh=false;request(source.edit(),source.sectionArgs(snapshot,"identity",i),r->{if(r.code().equals("OK")){snapshot=SnapshotReplies.checked(source,r.data());baseline=snapshot.deepCopy();fresh=true;if(!values.equals(sent)){dirty=true;message="Submitted text saved. Your newer typing is still unsaved.";}else{dirty=false;if(parent instanceof CharacterWizardScreen)message="Character details saved.";else client.setScreen(parent);}}else{fresh=false;message=r.message()+" Text retained. Reopen after reviewing the saved character.";}});
    }
    @Override public void tick(){super.tick();if(reviewed!=null&&!busy){String v=reviewed;reviewed=null;if(fresh&&snapshot.toString().equals(v)){baseline=snapshot.deepCopy();save();}else message="Character changed again. Review before saving your retained text.";}}
    @Override public void close(){if(busy)return;if(dirty)client.setScreen(new RpgConfirmationScreen(this,"Discard unsaved story?","Saved character details remain unchanged.",()->returnFromWizard(CreatorFlow.Step.DETAILS)));else if(parent instanceof CharacterWizardScreen)returnFromWizard(CreatorFlow.Step.DETAILS);else super.close();}
}
