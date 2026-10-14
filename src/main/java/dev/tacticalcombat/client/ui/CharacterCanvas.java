package dev.tacticalcombat.client.ui;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** One clipped contextual surface with real child controls, explicit focus and retained scroll. */
public final class CharacterCanvas extends ClickableWidget {
    private record Child(String key,ClickableWidget widget,int x,int y) {}
    private record Caption(java.util.function.Supplier<String> text,int x,int y,int width,int color) {}
    private record Glyph(String key,int x,int y,int size) {}
    private record Progress(int x,int y,int width,double fraction) {}
    private final List<Progress> progress=new ArrayList<>();
    public void progress(int x,int y,int width,double fraction){progress.add(new Progress(x,y,width,Math.clamp(fraction,0,1)));total=Math.max(total,y+8);}
    private record Head(String owner,int x,int y,int size) {}
    private final List<Head> heads=new ArrayList<>();
    public void skinHead(String owner,int x,int y,int size){heads.add(new Head(owner,x,y,size));total=Math.max(total,y+size);}
    private record Panel(int x,int y,int width,int height,String title) {}
    private final List<Panel> panels=new ArrayList<>();
    public void panel(String title,int x,int y,int width,int height){panels.add(new Panel(x,y,width,height,title));total=Math.max(total,y+height+6);}
    public void wrapped(String text,int x,int y,int width,int color){var font=MinecraftClient.getInstance().textRenderer;for(var line:font.getTextHandler().wrapLines(text,width,net.minecraft.text.Style.EMPTY)){label(line.getString(),x,y,width,color);y+=12;}}
    public boolean dismissPopup(){if(focused!=null&&focused.widget() instanceof AlignmentDropdown d&&d.isOpen()){d.dismiss();return true;}return false;}
    private final List<Glyph> glyphs=new ArrayList<>();
    private final List<Child> controls=new ArrayList<>();
    private final List<Caption> captions=new ArrayList<>();
    private Child focused;
    private int offset,total;
    public CharacterCanvas(UiLayout.Rect r){super(r.x(),r.y(),r.width(),r.height(),Text.literal("Character workspace"));}
    public <T extends ClickableWidget>T control(String key,T widget){controls.add(new Child(key,widget,widget.getX(),widget.getY()));total=Math.max(total,widget.getBottom()+4);return widget;}
    public void label(String text,int x,int y,int width,int color){captions.add(new Caption(()->text,x,y,width,color));total=Math.max(total,y+14);}
    public void liveLabel(java.util.function.Supplier<String> text,int x,int y,int width,int color){captions.add(new Caption(text,x,y,width,color));total=Math.max(total,y+14);}
    public void icon(String key,int x,int y,int size){glyphs.add(new Glyph(key,x,y,size));total=Math.max(total,y+size);}
    public Map<String,Integer> cursors(){var result=new HashMap<String,Integer>();for(var c:controls)if(c.widget() instanceof net.minecraft.client.gui.widget.TextFieldWidget f)result.put(c.key(),f.getCursor());return result;}
    public void cursors(Map<String,Integer> saved){for(var c:controls)if(c.widget() instanceof net.minecraft.client.gui.widget.TextFieldWidget f&&saved.containsKey(c.key()))f.setCursor(Math.min(saved.get(c.key()),f.getText().length()),false);}
    public int position(){return offset;}
    public void position(int value){offset=Math.clamp(value,0,Math.max(0,total-height));}
    public String focusedKey(){return focused==null?"":focused.key();}
    public void focus(String key){for(var child:controls)if(child.key().equals(key)){focusChild(child);return;}}
    private void focusChild(Child child){if(focused!=null&&focused!=child)focused.widget().setFocused(false);focused=child;if(child!=null){child.widget().setFocused(true);if(child.y()<offset)position(child.y());else if(child.y()+child.widget().getHeight()>offset+height)position(child.y()+child.widget().getHeight()-height);}}
    @Override public void setFocused(boolean value){super.setFocused(value);if(focused!=null)focused.widget().setFocused(value);}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;g.fill(getX(),getY(),getRight(),getBottom(),0xFF181C21);
        try(var clip=ClipScope.open(()->g.enableScissor(getX(),getY(),getRight(),getBottom()),g::disableScissor)){
            for(var panel:panels){int y=getY()+panel.y()-offset;RpgTheme.panel(g,getX()+panel.x(),y,panel.width(),panel.height(),RpgTheme.PANEL,false);if(!panel.title().isEmpty())RpgTheme.text(g,font,panel.title(),getX()+panel.x()+7,y+7,panel.width()-14,RpgTheme.GOLD);}
            for(var head:heads){var client=MinecraftClient.getInstance();if(client.player!=null){var skin=client.player.getSkinTextures();boolean local=head.owner().isBlank()||head.owner().equals(client.player.getUuidAsString());if(!local){try{var entry=client.getNetworkHandler()==null?null:client.getNetworkHandler().getPlayerListEntry(java.util.UUID.fromString(head.owner()));if(entry==null)continue;skin=entry.getSkinTextures();}catch(RuntimeException missing){continue;}}net.minecraft.client.gui.PlayerSkinDrawer.draw(g,skin,getX()+head.x(),getY()+head.y()-offset,head.size());}}
            for(var bar:progress){int y=getY()+bar.y()-offset;g.fill(getX()+bar.x(),y,getX()+bar.x()+bar.width(),y+4,0xFF343A43);g.fill(getX()+bar.x(),y,getX()+bar.x()+(int)(bar.width()*bar.fraction()),y+4,RpgTheme.GOLD);}
            for(var icon:glyphs)CreatorIcons.draw(g,icon.key(),getX()+icon.x(),getY()+icon.y()-offset,icon.size());
            for(var c:captions){int y=getY()+c.y()-offset;if(y+12>=getY()&&y<getBottom()){RpgTheme.text(g,font,c.text().get(),getX()+c.x(),y,c.width(),c.color());if(isMouseOver(mx,my)&&my>=y&&my<y+12&&mx>=getX()+c.x()&&mx<getX()+c.x()+c.width())MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(c.text().get()));}}
            for(var child:controls){var w=child.widget();w.setX(getX()+child.x());w.setY(getY()+child.y()-offset);w.visible=w.getBottom()>getY()&&w.getY()<getBottom();if(w.visible)w.render(g,isMouseOver(mx,my)?mx:-1000,isMouseOver(mx,my)?my:-1000,delta);}
        }
        if(total>height){int h=Math.max(8,height*height/total),y=getY()+(height-h)*offset/Math.max(1,total-height);g.fill(getRight()-2,y,getRight(),y+h,RpgTheme.GOLD);}
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(!isMouseOver(x,y))return false;if(focused!=null&&focused.widget() instanceof AlignmentDropdown d&&d.isOpen()&&!d.isMouseOver(x,y)){d.dismiss();return true;}for(int i=controls.size()-1;i>=0;i--){var child=controls.get(i);var w=child.widget();w.setX(getX()+child.x());w.setY(getY()+child.y()-offset);if(w.isMouseOver(x,y)){focusChild(child);if(w.mouseClicked(x,y,button))return true;}}setFocused(true);return true;}
    @Override public boolean mouseReleased(double x,double y,int button){return focused!=null&&focused.widget().mouseReleased(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return focused!=null&&focused.widget().mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(!isMouseOver(x,y))return false;if(focused!=null&&focused.widget() instanceof AlignmentDropdown dropdown&&dropdown.isOpen())return dropdown.mouseScrolled(x,y,horizontal,vertical);position(offset+(vertical>0?-20:20));return true;}
    @Override public boolean charTyped(char c,int mods){return focused!=null&&focused.widget().charTyped(c,mods);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_TAB&&!controls.isEmpty()){int i=focused==null?-1:controls.indexOf(focused);int next=i+((mods&GLFW.GLFW_MOD_SHIFT)!=0?-1:1);if(next<0||next>=controls.size())return false;focusChild(controls.get(next));return true;}if(focused!=null&&focused.widget().keyPressed(key,scan,mods))return true;if(key==GLFW.GLFW_KEY_PAGE_UP||key==GLFW.GLFW_KEY_PAGE_DOWN){position(offset+(key==GLFW.GLFW_KEY_PAGE_UP?-height:height));return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){if(focused!=null)focused.widget().appendNarrations(b);else b.put(NarrationPart.TITLE,Text.literal("Character workspace; Tab selects controls, wheel scrolls."));}
}
