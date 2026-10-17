package dev.tacticalcombat.client.dicevisual;

import com.google.gson.JsonParser;
import dev.tacticalcombat.character.AtomicJsonFile;
import dev.tacticalcombat.uiapi.SheetSections;
import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;
import java.nio.file.Path;

/** Local-only presentation preferences; no campaign/world schema change. */
public record DicePresentationSettings(int version,Mode manualMode,Mode checkMode,double speed,boolean reducedMotion,
                                       boolean sound,double volume,double holdSeconds,double size) {
    public enum Mode { QUICK, CINEMATIC }
    public DicePresentationSettings {
        if(version!=1||manualMode==null||checkMode==null||!Double.isFinite(speed)||speed<.5||speed>2||
           !Double.isFinite(volume)||volume<0||volume>1||!Double.isFinite(holdSeconds)||holdSeconds<1||holdSeconds>8||
           !Double.isFinite(size)||size<.7||size>1.4)throw new IllegalArgumentException("Invalid dice presentation settings");
    }
    public static DicePresentationSettings defaults(){return new DicePresentationSettings(1,Mode.QUICK,Mode.CINEMATIC,1,false,true,.55,3,1);}
    public DicePresentationSettings withModes(Mode manual,Mode check){return new DicePresentationSettings(1,manual,check,speed,reducedMotion,sound,volume,holdSeconds,size);}
    public DicePresentationSettings withSpeed(double v){return new DicePresentationSettings(1,manualMode,checkMode,v,reducedMotion,sound,volume,holdSeconds,size);}
    public DicePresentationSettings withReduced(boolean v){return new DicePresentationSettings(1,manualMode,checkMode,speed,v,sound,volume,holdSeconds,size);}
    public DicePresentationSettings withSound(boolean v){return new DicePresentationSettings(1,manualMode,checkMode,speed,reducedMotion,v,volume,holdSeconds,size);}
    public DicePresentationSettings withVolume(double v){return new DicePresentationSettings(1,manualMode,checkMode,speed,reducedMotion,sound,v,holdSeconds,size);}
    public DicePresentationSettings withHold(double v){return new DicePresentationSettings(1,manualMode,checkMode,speed,reducedMotion,sound,volume,v,size);}
    public DicePresentationSettings withSize(double v){return new DicePresentationSettings(1,manualMode,checkMode,speed,reducedMotion,sound,volume,holdSeconds,v);}
    private static Path path(){return FabricLoader.getInstance().getConfigDir().resolve("tacticalcombat/dice-presentation.json");}
    public static DicePresentationSettings load()throws IOException{return new AtomicJsonFile(path()).read(DicePresentationSettings::decode,defaults());}
    public void save()throws IOException{new AtomicJsonFile(path()).write(SheetSections.JSON.toJson(this),DicePresentationSettings::decode);}
    private static DicePresentationSettings decode(String json){if(json.length()>2048)throw new IllegalArgumentException("Oversize dice preferences");return SheetSections.parse(JsonParser.parseString(json),DicePresentationSettings.class);}
}
