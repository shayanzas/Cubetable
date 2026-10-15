package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.uiapi.SheetSections;
import java.util.*;
import java.util.function.ToIntFunction;
import static dev.tacticalcombat.client.ui.ManagementState.text;

/** Measured identity lines reserve their own height before any score/stat panel. */
public record SheetHeader(List<Line> lines,int height) {
    public record Line(String text,int x,int y,int width,boolean emphasized){}
    public SheetHeader {lines=List.copyOf(lines);}
    public static SheetHeader layout(JsonObject snapshot,int width,ToIntFunction<String> measure){int w=width-12,y=4;var lines=new ArrayList<Line>();var identity=SheetSections.read(snapshot.getAsJsonObject("sheet"),"identity");var xp=DashboardModel.xp(snapshot.getAsJsonObject("sheet"));String[] base={text(snapshot,"name")+" · Level "+xp.level()+" · "+xp.value()+" XP",shown(text(identity,"species"))+" · "+shown(text(identity,"characterClass"))+(text(identity,"subclass").isBlank()?"":" / "+text(identity,"subclass")),"Age: "+shown(text(identity,"age"))+" · Height: "+shown(text(identity,"height"))+" · "+shown(text(identity,"alignment"))};var paragraphs=new ArrayList<>(Arrays.asList(base));String notice=DashboardModel.levelNotice(snapshot);if(!notice.isEmpty())paragraphs.add(notice);for(int p=0;p<paragraphs.size();p++){int x=p<2?29:7,available=Math.max(1,w-x-7);for(String line:wrap(paragraphs.get(p),available,measure)){lines.add(new Line(line,x,y,available,p==0));y+=12;}}return new SheetHeader(lines,y+3);}
    public static List<String> wrap(String text,int width,ToIntFunction<String> measure){var out=new ArrayList<String>();String remaining=text;while(!remaining.isEmpty()){int end=0;while(end<remaining.length()&&measure.applyAsInt(remaining.substring(0,end+1))<=width)end++;if(end==remaining.length()){out.add(remaining);break;}end=Math.max(1,end);int space=remaining.lastIndexOf(' ',end);if(space>0)end=space;out.add(remaining.substring(0,end).stripTrailing());remaining=remaining.substring(end).stripLeading();}return out.isEmpty()?List.of(""):List.copyOf(out);}
    private static String shown(String s){return s.isBlank()?"Not set":s;}
}
