package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import net.minecraft.client.gui.screen.Screen;
import dev.tacticalcombat.client.ClientRulesWatch;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Informational recorded totals only; official check execution belongs to J2.5D. */
public final class CharacterRollMenuScreen extends ManagementScreen {
    private final String character;private final boolean own;private int section;
    public CharacterRollMenuScreen(Screen parent,JsonObject snapshot){super("Abilities, Skills & Saves",parent);character=text(snapshot,"id");own=flag(snapshot,"boundToSelf");}
    @Override protected void controls(){button(List.of("Abilities","Skills","Saves").get(section),"",frame.x()+112,frame.y()+frame.height()-38,Math.max(90,frame.width()-124),22,()->{section=(section+1)%3;refresh();});}
    @Override protected void refresh(){if(busy)return;fresh=false;clearRows();read(QUICK_SHEET,own?args():args("character",character),data->{
        if(data.has("empty")){info("No bound character",text(data,"empty"));filter();return;}
        ClientRulesWatch.panel(this,UUID.fromString(text(data,"panelToken")),revoked->{generation++;busy=false;fresh=false;clearRows();filter();message="Access or character changed. Refresh to recheck.";});
        info("Informational totals","Official character checks arrive in J2.5D. Normal, advantage and disadvantage are currently available only as clearly labeled manual dice rolls.");
        if(section==0)for(String key:List.of("str","dex","con","int","wis","cha"))info(key.toUpperCase(Locale.ROOT)+" "+String.format(Locale.ROOT,"%+d",DashboardModel.modifier(data,key)),"Score "+DashboardModel.score(data,key)+" · server-projected ability modifier");
        else for(var check:DashboardModel.checks(data,section==2))info(check.name()+" "+check.display(),check.breakdown());
        row("Open Manual Dice","Uses a manually entered modifier; not an official character check",true,"Server-generated free roll",()->client.setScreen(new DiceToolbarScreen(this,true)));filter();
    });}
    @Override public void removed(){ClientRulesWatch.close(this);super.removed();}
}
