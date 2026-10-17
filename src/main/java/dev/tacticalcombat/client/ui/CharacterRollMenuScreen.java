package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Check availability and modifiers are supplied by the authenticated server endpoint. */
public final class CharacterRollMenuScreen extends CheckScreen {
    private final String character;private final boolean own;private int section;
    public CharacterRollMenuScreen(Screen parent,JsonObject snapshot){super("Abilities, Skills & Saves",parent);character=text(snapshot,"id");own=flag(snapshot,"boundToSelf");}
    @Override protected void controls(){button(List.of("Abilities","Skills","Saves").get(section),"",frame.x()+112,frame.y()+frame.height()-38,Math.max(90,frame.width()-124),22,()->{section=(section+1)%3;refresh();});}
    @Override protected void refresh(){if(busy)return;clearRows();read(CHECK_OPTIONS,own?args():args("character",character),data->{
        info("Character Check","Server-calculated ordinary checks. Features, conditions and custom adjustments are not automated. Requested advantage is explicit adjudication.");
        for(var v:array(data,"checks")){var c=v.getAsJsonObject();if(!text(c,"category").equals(List.of("ABILITY","SKILL","SAVE").get(section)))continue;boolean available=flag(c,"available");String detail=available?CheckRollScreen.breakdown(c.getAsJsonObject("modifier")):text(c,"reason");row(text(c,"id"),detail,available,detail,()->client.setScreen(new CheckRollScreen(this,text(data,"character"),c, !own&&flag(data,"manager"))));}
        row("Roll Requests & Results","Pending DM requests and authorized session outcomes",true,"",()->client.setScreen(new CheckInboxScreen(this)));
        row("Open Manual Dice","Free server die roll with user-entered modifier",true,"",()->client.setScreen(new DiceToolbarScreen(this,true)));filter();
    });}
}
