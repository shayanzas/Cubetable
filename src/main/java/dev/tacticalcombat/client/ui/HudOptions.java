package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.character.AtomicJsonFile;
import dev.tacticalcombat.uiapi.*;
import com.google.gson.*;
import java.io.IOException;
import java.nio.file.Path;

/** Additional local preferences alongside the unchanged version-1 HudPreferences file. */
public record HudOptions(int version,HudPreferences.Panel notifications,int logFont,int partyFont,int diceFont,boolean outsideCombat,boolean notificationsVisible,boolean sound,boolean legacyDice,boolean diceChat,int notificationTicks,int bubbleTicks) {
    public HudOptions {SheetSections.require(version==1&&notifications!=null&&logFont>=8&&logFont<=16&&partyFont>=8&&partyFont<=16&&diceFont>=8&&diceFont<=16&&notificationTicks>=20&&notificationTicks<=200&&bubbleTicks>=20&&bubbleTicks<=200,"Invalid HUD options");}
    public static HudOptions defaults(){return new HudOptions(1,new HudPreferences.Panel(.98,.18,1,true),8,9,10,false,true,false,false,false,80,80);}
    public static HudOptions load(Path path)throws IOException{return new AtomicJsonFile(path).read(HudOptions::decode,defaults());}
    public void save(Path path)throws IOException{new AtomicJsonFile(path).write(SheetSections.JSON.toJson(this),HudOptions::decode);}
    private static HudOptions decode(String value){SheetSections.require(value.length()<4096,"HUD options too large");dev.tacticalcombat.character.CharacterRepository.checkDepth(value,8);return SheetSections.parse(JsonParser.parseString(value),HudOptions.class);}
}
