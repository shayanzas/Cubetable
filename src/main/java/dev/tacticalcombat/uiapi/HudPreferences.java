package dev.tacticalcombat.uiapi;
import com.google.gson.*;
import dev.tacticalcombat.character.AtomicJsonFile;
import java.nio.file.Path;
import java.io.IOException;
import static dev.tacticalcombat.uiapi.SheetSections.require;
/** Client-local layout only. Coordinates are fractions of available screen size. */
public record HudPreferences(int version,Panel party,Panel initiative,Panel dice,Panel events,int fontSize,double animationSpeed,boolean reducedMotion,boolean visible) {
    public record Panel(double x,double y,double scale,boolean visible) {
        public Panel {require(Double.isFinite(x) && Double.isFinite(y) && x>=0 && x<=1 && y>=0 && y<=1 && Double.isFinite(scale) && scale>=.5 && scale<=2,"Invalid panel layout");}
    }
    public HudPreferences {require(version==1 && party!=null && initiative!=null && dice!=null && events!=null && fontSize>=8 && fontSize<=32 && Double.isFinite(animationSpeed) && animationSpeed>=.25 && animationSpeed<=4,"Invalid HUD settings");}
    public static HudPreferences defaults() {return new HudPreferences(1,new Panel(.02,.2,1,true),new Panel(.5,.02,1,true),new Panel(.5,.7,1,true),new Panel(.02,.75,1,true),12,1,false,true);}
    public static HudPreferences load(Path path) throws IOException {return new AtomicJsonFile(path).read(HudPreferences::decode,defaults());}
    public void save(Path path) throws IOException {new AtomicJsonFile(path).write(SheetSections.JSON.toJson(this),HudPreferences::decode);}
    private static HudPreferences decode(String json) {require(json.length()<=4096,"HUD settings too large");dev.tacticalcombat.character.CharacterRepository.checkDepth(json,8);var o=JsonParser.parseString(json).getAsJsonObject();if(o.get("version").getAsInt()!=1) throw new UnsupportedOperationException("Unsupported HUD settings");return SheetSections.parse(o,HudPreferences.class);}
}
