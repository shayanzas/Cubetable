package dev.tacticalcombat.client.ui;
import com.google.gson.JsonObject;
import dev.tacticalcombat.uiapi.CreationRules;
/** Compact read-only container; choices remain in Skills and Expertise above it. */
public final class CreationSavingThrows {
    private CreationSavingThrows(){}
    public static int columns(int width){return width>=480?3:width>=280?2:1;}
    public static int height(int width){return 32+((6+columns(width)-1)/columns(width))*24;}
    public static void build(CharacterCanvas pane,JsonObject view,int x,int y,int width){int columns=columns(width),cw=(width-16-(columns-1)*8)/columns;pane.panel("Saving Throws",x,y,width,height(width));var saves=ProficiencyModel.checks(view,true);String clazz=CreationRules.clazz(view.getAsJsonObject("sheet"));for(int i=0;i<saves.size();i++){var c=saves.get(i);int sx=x+8+i%columns*(cw+8),sy=y+25+i/columns*24;String name=width<180?c.ability().toUpperCase(java.util.Locale.ROOT):c.name();String label=name+" "+((c.total()>=0?"+":"")+c.total());if(c.proficient())pane.boldLabel(label,sx,sy,cw,RpgTheme.GOLD);else pane.label(label,sx,sy,cw,RpgTheme.TEXT);pane.tooltip(sx,sy,cw,20,c.breakdown()+"\n"+(CreationRules.saves(clazz).contains(c.ability())?"Granted by "+clazz:"Recorded grants retained"));}}
}
