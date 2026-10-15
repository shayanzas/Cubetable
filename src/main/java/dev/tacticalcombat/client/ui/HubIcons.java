package dev.tacticalcombat.client.ui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
import java.util.Set;
/** H-only navigation artwork; existing class/race asset mappings remain untouched. */
public final class HubIcons {
 public static final Set<String> KEYS=Set.of("campaigns","characters","encounters","dm","tabletop","settings");
 private HubIcons(){}
 public static void draw(DrawContext g,String key,int x,int y,int size){if(!KEYS.contains(key))return;g.drawTexture(Identifier.of("tacticalcombat","textures/gui/hub/"+key+".png"),x,y,size,size,2f,2f,512,512,516,516);}
}
