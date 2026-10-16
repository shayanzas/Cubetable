package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.uiapi.SheetSections;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** Shared compact authorized projection view. No locally invented combat quantities. */
public final class CompactSheet {
    public static int draw(CharacterCanvas canvas,JsonObject data,int y){
        int width=canvas.getWidth()-16;var identity=SheetSections.read(data.getAsJsonObject("sheet"),"identity");
        canvas.skinHead(text(data,"owner"),8,y,28);canvas.boldLabel(text(data,"name"),44,y,width-36,RpgTheme.GOLD);
        canvas.label(PlayerUiModel.raceLabel(text(identity,"species"))+" · "+text(identity,"characterClass"),44,y+14,width-36,RpgTheme.TEXT);y+=36;
        canvas.label(flag(data,"boundToSelf")?"Bound to your player":"DM inspection · selection does not change bindings",8,y,width,RpgTheme.MUTED);y+=18;
        if(!data.has("rules")||!flag(data.getAsJsonObject("rules"),"valid")){canvas.wrapped("Rules projection unavailable. Review saved inputs in the full sheet.",8,y,width,RpgTheme.MUTED);return y+36;}
        var stats=DashboardModel.combat(data,false);int columns=width>=280?3:2,cell=width/columns;
        for(int i=0;i<stats.size();i++){var stat=stats.get(i);int x=8+(i%columns)*cell,sy=y+(i/columns)*42;canvas.panel(stat.label(),x,sy,cell-4,38);canvas.label(stat.value(),x+7,sy+21,cell-18,RpgTheme.TEXT);canvas.tooltip(x,sy,cell-4,38,stat.authority());}y+=((stats.size()+columns-1)/columns)*42+4;
        int abilityColumns=width>=288?6:3,aw=width/abilityColumns;int i=0;for(String key:List.of("str","dex","con","int","wis","cha")){int x=8+(i%abilityColumns)*aw,sy=y+(i/abilityColumns)*42;canvas.panel(key.toUpperCase(Locale.ROOT),x,sy,aw-3,38);canvas.label(DashboardModel.score(data,key)+" "+String.format(Locale.ROOT,"%+d",DashboardModel.modifier(data,key)),x+5,sy+21,aw-10,RpgTheme.TEXT);i++;}y+=((6+abilityColumns-1)/abilityColumns)*42+4;
        var r=data.getAsJsonObject("rules");if(flag(r,"levelMismatch")){canvas.wrapped(text(r,"levelNotice"),8,y,width,RpgTheme.GOLD);y+=32;}
        var weapons=new ArrayList<String>();for(var weapon:array(r.getAsJsonObject("inputs"),"weapons"))weapons.add(weapon.getAsString().toLowerCase(Locale.ROOT).replace('_',' '));
        String attacks=weapons.isEmpty()?"No recorded weapons":String.join(", ",weapons);canvas.wrapped("Attacks: "+attacks,8,y,width,RpgTheme.TEXT);y+=Math.max(1,net.minecraft.client.MinecraftClient.getInstance().textRenderer.wrapLines(net.minecraft.text.Text.literal("Attacks: "+attacks),width).size())*12+6;
        canvas.label("Stock · "+text(r,"arrows")+" arrows · "+text(r,"bolts")+" bolts · "+text(r,"daggers")+" daggers",8,y,width,RpgTheme.MUTED);return y+20;
    }
    private CompactSheet(){}
}
