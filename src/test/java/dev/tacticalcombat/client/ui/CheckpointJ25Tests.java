package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.combat.NearbyActors;
import dev.tacticalcombat.dice.*;
import java.util.*;

public final class CheckpointJ25Tests {
    private static int count;
    private static void check(boolean value,String why){count++;if(!value)throw new AssertionError(why);}
    public static void run(){
        // Every legitimate Minecraft GUI minimum/scale: toolbar stays above vanilla hotbar/health region.
        for(int[] size:new int[][]{{320,240},{426,240},{480,270},{640,360},{960,540},{1920,1080}}){var r=DiceToolbarLayout.bounds(size[0],size[1]);check(r.fits(size[0],size[1]),"Dice toolbar in bounds");check(r.y()+r.height()<=size[1]-66,"Vanilla control strip unobscured");int inner=r.width()-12;check(86+DiceToolbarLayout.modeWidth(inner)<=inner-56,"Modifier, mode and Roll controls do not overlap");}
        for(var binding:NearbyActors.Binding.values())for(var kind:NearbyActors.Kind.values())for(boolean bound:List.of(false,true))for(boolean eligible:List.of(false,true))for(boolean player:List.of(false,true)){boolean expected=(kind==NearbyActors.Kind.ALL||(kind==NearbyActors.Kind.PLAYER)==player)&&switch(binding){case ALL->true;case BOUND->bound;case UNBOUND->!bound;case ELIGIBLE->eligible;};check(NearbyActors.matches(binding,kind,bound,eligible,player)==expected,"All server filter combinations");}
        for(var die:DiceType.values()){var normal=DiceSpec.parse("2d"+die.sides+"-3");check(normal!=null&&normal.type()==die&&normal.count()==2&&normal.modifier()==-3,"Every toolbar die uses existing DiceSpec");}
        for(String invalid:List.of("0d20","11d6","d3","d20+1000","2d20+foo"))check(DiceSpec.parse(invalid)==null,"Malformed dice expression denied");
        System.out.println("J2.5 gameplay UI checks passed: "+count);
    }
}
