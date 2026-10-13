package dev.tacticalcombat.client.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.screen.narration.*;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;

/** Exactly nine equal race cards; labels wrap without truncation and share measured hitboxes. */
public final class IdentityRaceGrid extends ClickableWidget {
    private final IdentityLayout.Layout layout;
    private final Consumer<String> choose;
    private final String selected;
    private int focus;
    public IdentityRaceGrid(IdentityLayout.Layout layout,String selected,Consumer<String> choose){super(layout.grid().x(),layout.grid().y(),layout.grid().width(),layout.grid().height(),Text.literal("Race selection"));this.layout=layout;this.selected=selected;this.choose=choose;for(int i=0;i<9;i++)if(Srd2014Catalog.SPECIES.get(i).name().equals(selected))focus=i;}
    private UiLayout.Rect card(int i){var r=layout.cards().get(i);return new UiLayout.Rect(getX()+r.x()-layout.grid().x(),getY()+r.y()-layout.grid().y(),r.width(),r.height());}
    @Override protected void renderWidget(DrawContext g,int mx,int my,float delta){var font=MinecraftClient.getInstance().textRenderer;for(int i=0;i<9;i++){var r=card(i);var race=Srd2014Catalog.SPECIES.get(i);boolean active=selected.equals(race.name()),keyboard=isFocused()&&focus==i;RpgTheme.panel(g,r.x(),r.y(),r.width(),r.height(),active?RpgTheme.HOVER:RpgTheme.PANEL,active||keyboard);var lines=IdentityLayout.lines(race.name(),r.width()-12,font::getWidth);int y=r.y()+(r.height()-lines.size()*10)/2;for(String line:lines){g.drawText(font,line,r.x()+(r.width()-font.getWidth(line))/2,y,active?RpgTheme.GOLD:RpgTheme.TEXT,false);y+=10;}if(r.contains(mx,my))MinecraftClient.getInstance().currentScreen.setTooltip(Text.literal(race.name()+"\n"+IdentityModel.intro(race.name())));}}
    @Override public boolean mouseClicked(double x,double y,int button){if(button!=0)return false;for(int i=0;i<9;i++)if(card(i).contains(x,y)){focus=i;choose.accept(Srd2014Catalog.SPECIES.get(i).name());return true;}return false;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(!isFocused())return false;int delta=switch(key){case GLFW.GLFW_KEY_LEFT->-1;case GLFW.GLFW_KEY_RIGHT->1;case GLFW.GLFW_KEY_UP->-3;case GLFW.GLFW_KEY_DOWN->3;default->0;};if(delta!=0){focus=Math.clamp(focus+delta,0,8);return true;}if(key==GLFW.GLFW_KEY_ENTER||key==GLFW.GLFW_KEY_SPACE){choose.accept(Srd2014Catalog.SPECIES.get(focus).name());return true;}return false;}
    @Override protected void appendClickableNarrations(NarrationMessageBuilder b){b.put(NarrationPart.TITLE,Text.literal(Srd2014Catalog.SPECIES.get(focus).name()));b.put(NarrationPart.USAGE,Text.literal("Arrow keys browse nine race cards. Enter previews; Save or Continue keeps the selection."));}
}
