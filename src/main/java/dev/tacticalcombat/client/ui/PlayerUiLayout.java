package dev.tacticalcombat.client.ui;
import java.util.*;
/** Scroll-content geometry; the viewport clips all overflow and leaves the common footer clear. */
public final class PlayerUiLayout {
 private PlayerUiLayout(){}
 public static UiLayout.Rect viewport(UiLayout.Rect frame){return new UiLayout.Rect(frame.x()+12,frame.y()+36,frame.width()-24,Math.max(12,frame.height()-74));}
 public static int hubColumns(int width){return width>=380?2:1;}
 public static int tileHeight(int width){return width<180?128:82;}
 public static int overviewHeight(int width){return width<400?216:166;}
 public static List<UiLayout.Rect> hubTiles(int width){int columns=hubColumns(width),w=(width-12-(columns-1)*10)/columns,top=overviewHeight(width)+22;var out=new ArrayList<UiLayout.Rect>();for(int i=0;i<6;i++)out.add(new UiLayout.Rect(6+i%columns*(w+10),top+i/columns*(tileHeight(w)+10),w,tileHeight(w)));return List.copyOf(out);}
 public record CharacterCard(int height,int metadataTop,int profileTop,UiLayout.Rect primary,UiLayout.Rect manage){}
 public static CharacterCard characterCard(int width,int nameLines,int metadataLines){int meta=Math.max(56,35+nameLines*11),profile=meta+metadataLines*12+5,top=profile+24,ah=actionHeight(width);boolean stacked=width<350;int bw=stacked?width-20:(width-26)/2;var primary=new UiLayout.Rect(10,top,bw,ah);var manage=new UiLayout.Rect(stacked?10:16+bw,stacked?top+ah+6:top,bw,ah);return new CharacterCard(manage.y()+manage.height()+10,meta,profile,primary,manage);}
 public static int libraryColumns(int width){return width>=700?2:1;}
 public static int actionHeight(int width){return width<350?32:22;}
}
