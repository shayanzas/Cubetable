package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.uiapi.GuidedRaceRules;

public final class CheckpointJ15Tests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    public static void run(){
        for(var entry:GuidedRaceRules.SUBRACES.entrySet())for(String sub:entry.getValue())check(PlayerUiModel.raceLabel(entry.getKey()+" — "+sub).equals(sub),"supported redundant parent simplified");
        for(String label:new String[]{"Dwarf — Base Traits","Custom Dwarf — Hill Dwarf","Dwarf — Homebrew","Human","Elf — Hill Dwarf"})check(PlayerUiModel.raceLabel(label).equals(label),"custom/base/mismatched identity preserved");
        check(!QuickPanelLayout.pagination(0,false),"single-page library hides pagination");check(QuickPanelLayout.pagination(0,true),"first of multiple pages retains Next");check(QuickPanelLayout.pagination(1,false),"last page retains Previous");
        for(int[] size:new int[][]{{320,180},{427,240},{640,360},{854,480},{1920,1080},{2560,1440}}){var panel=QuickPanelLayout.frame(size[0],size[1]);check(panel.x()>=0&&panel.y()>=0&&panel.x()+panel.width()<=size[0]&&panel.y()+panel.height()<=size[1],"panel remains inside GUI viewport");check(panel.width()<=360&&panel.height()<=420,"compact bounds do not expand into full dashboard");if(size[0]>=640)check(size[0]-panel.width()>=280,"world remains visible beside panel");}
        System.out.println("J1.5 usability checks passed: "+count);
    }
}
