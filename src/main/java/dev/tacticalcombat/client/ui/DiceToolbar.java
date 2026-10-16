package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.net.DiceRollPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.fabricmc.loader.api.FabricLoader;
import dev.tacticalcombat.character.AtomicJsonFile;
import java.util.*;

/** Passive pinned presentation never owns a cursor or intercepts movement keys. */
public final class DiceToolbar {
    public record Preferences(int version,boolean pinned){public Preferences{if(version!=1)throw new IllegalArgumentException("Unsupported dice toolbar preferences");}}
    private static Preferences preferences;private static String result="No manual result yet";
    public static boolean pinned(){if(preferences==null){try{preferences=new AtomicJsonFile(path()).read(s->new com.google.gson.Gson().fromJson(s,Preferences.class),new Preferences(1,false));}catch(Exception failure){preferences=new Preferences(1,false);}}return preferences.pinned();}
    private static java.nio.file.Path path(){return FabricLoader.getInstance().getConfigDir().resolve("tacticalcombat/dice-toolbar.json");}
    public static boolean pin(boolean value){try{var next=new Preferences(1,value);new AtomicJsonFile(path()).write(new com.google.gson.Gson().toJson(next),s->new com.google.gson.Gson().fromJson(s,Preferences.class));preferences=next;return true;}catch(Exception failure){return false;}}
    public static UiLayout.Rect bounds(int width,int height){return DiceToolbarLayout.bounds(width,height);}
    public static void receive(DiceRollPayload roll){var mc=MinecraftClient.getInstance();if(mc.player!=null&&roll.roller().equals(mc.player.getGameProfile().getName())&&roll.label().equals("Manual dice"))result=roll.type().label+" "+Arrays.toString(roll.results())+(roll.keep()==1?" · highest":roll.keep()==2?" · lowest":"")+" "+String.format(Locale.ROOT,"%+d",roll.modifier())+" = "+roll.total();}
    public static String result(){return result;}
    public static void clear(){result="No manual result yet";}
    public static void render(DrawContext g,RenderTickCounter tick){var mc=MinecraftClient.getInstance();if(mc.player==null||mc.options.hudHidden||mc.currentScreen!=null||!pinned())return;var r=bounds(g.getScaledWindowWidth(),g.getScaledWindowHeight());RpgTheme.panel(g,r.x(),r.y()+r.height()-30,r.width(),30,0xDA191D23,false);RpgTheme.text(g,mc.textRenderer,"MANUAL DICE · "+dev.tacticalcombat.client.TacticalCombatClient.DICE_TOOLBAR_KEY.getBoundKeyLocalizedText().getString()+" to interact",r.x()+7,r.y()+r.height()-25,r.width()-14,RpgTheme.GOLD);RpgTheme.text(g,mc.textRenderer,result,r.x()+7,r.y()+r.height()-12,r.width()-14,RpgTheme.TEXT);}
    private DiceToolbar(){}
}
