package dev.tacticalcombat.client;
import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import java.util.*;

public final class SrdWorldInteractionTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    public static void run(){
        var scheduler=new SrdGridRefresh();UUID e=UUID.randomUUID();
        check(scheduler.due(e,1,1,true,0),"initial discovery");scheduler.requested(e,1,1,0);
        for(int t=1;t<22;t++)check(!scheduler.due(e,2,1,true,t),"movement revisions coalesce within server rate limit");
        check(scheduler.due(e,2,1,true,22),"new revision refreshes without mutating movement");scheduler.requested(e,2,1,22);
        check(!scheduler.due(e,2,1,true,1000),"rejected/pending identical discovery never loops");
        check(!scheduler.due(e,3,1,false,1000),"moving/exhausted/unauthorized cannot discover");
        check(scheduler.due(e,3,2,true,1000),"turn transition discovers new actor grid");scheduler.clear();check(scheduler.due(e,2,1,true,0),"disconnect clears correlation");
        var bottom=new SrdMovement.Cell(2,3,1);var top=new SrdMovement.Cell(2,3,5);var cells=List.of(bottom,top);
        check(SrdWorldPick.cell(cells,2.5,10,3.5,0,-1,0,20).equals(top),"closest elevated surface wins");
        check(SrdWorldPick.cell(cells,2.5,10,3.5,0,-1,0,3)==null,"solid obstruction blocks picking through wall");
        check(SrdWorldPick.cell(cells,2.5,4,3.5,0,-1,0,20).equals(bottom),"upper floor behind ray not selected");
        check(SrdWorldPick.cell(cells,9,10,3.5,0,-1,0,20)==null,"cannot synthesize unprovided terrain");
        check(SrdWorldPick.cell(cells,2.5,10,3.5,0,1,0,20)==null,"upward ray cannot choose floor behind camera");
        check(SrdWorldPick.cell(cells,2.5,10,3.5,0,-1,0,Double.NaN)==null,"invalid ray rejected");
        check(SrdWorldPick.cell(List.of(new SrdMovement.Cell(-3,-2,1.5)),-2.5,4,-1.5,0,-1,0,5)!=null,"negative coordinates and half-height slab");
        var state=JsonParser.parseString("{\"state\":\"ACTIVE\",\"dimension\":\"minecraft:overworld\",\"active\":\"npc\",\"actors\":[{\"id\":\"pc\",\"name\":\"Player\",\"controlled\":true},{\"id\":\"npc\",\"name\":\"Husk\",\"controlled\":true}]}").getAsJsonObject();
        check(SrdViewPolicy.current(state).get("name").getAsString().equals("Husk")&&SrdViewPolicy.canControl(state),"DM follows and controls authoritative NPC, not own PC");
        check(SrdViewPolicy.cameraEligible(state,"minecraft:overworld",false),"SRD camera eligible");
        check(SrdViewPolicy.participant(state,"pc")&&!SrdViewPolicy.participant(state,"observer"),"only actual participating player gets movement input lock");
        String capabilities=SrdViewPolicy.permissions(state);
        check(!SrdViewPolicy.cameraEligible(state,"minecraft:the_nether",false),"no cross-dimension camera");
        check(!SrdViewPolicy.cameraEligible(state,"minecraft:overworld",true),"Tactical camera takes priority");
        SrdViewPolicy.current(state).addProperty("controlled",false);check(!SrdViewPolicy.canControl(state),"revocation blocks NPC control");
        check(!capabilities.equals(SrdViewPolicy.permissions(state)),"revocation invalidates cached private result presentation");
        SrdViewPolicy.current(state).remove("controlled");check(!SrdViewPolicy.canControl(state),"missing capability fails closed");
        state.addProperty("active","pc");check(SrdViewPolicy.canControl(state),"turn switches back to authorized PC");
        state.addProperty("state","PAUSED");check(!SrdViewPolicy.canControl(state),"zero HP pause disables normal actions");
        state.addProperty("state","ENDED");check(!SrdViewPolicy.cameraEligible(state,"minecraft:overworld",false),"end releases camera");
        check(SrdViewPolicy.current(new JsonObject())==null&&!SrdViewPolicy.canControl(new JsonObject()),"disconnect empty state safe");
        var unarmed=SrdAttackChoices.forWeapon("UNARMED",null);check(unarmed.size()==1&&unarmed.getFirst().mode()==SrdRules.AttackMode.MELEE&&unarmed.getFirst().ability()==SrdCharacter.Ability.STR&&!unarmed.getFirst().twoHands(),"unarmed cannot cycle impossible configurations");
        check(SrdAttackChoices.forWeapon("SHORTBOW",null).stream().allMatch(c->c.mode()==SrdRules.AttackMode.RANGED&&c.ability()==SrdCharacter.Ability.DEX&&c.twoHands()),"bow valid shape");
        check(SrdAttackChoices.forWeapon("DAGGER",null).size()==4,"finesse/thrown combinations retained");
        check(SrdAttackChoices.forWeapon("SCIMITAR","DEX").size()==1,"reviewed NPC ability enforced in choices");
        var result=JsonParser.parseString("{\"actor\":\"pc\",\"target\":\"npc\",\"hit\":true,\"critical\":false,\"total\":16,\"chosen\":14,\"modifier\":2,\"rolledDamage\":3,\"damageType\":\"BLUDGEONING\"}").getAsJsonObject();
        String privateText=SrdAttackFeedback.describe(result,state.getAsJsonArray("actors"));check(privateText.contains("Player → Husk")&&privateText.contains("HP private")&&!privateText.contains("ZERO HP"),"hidden target resources never inferred");
        result.addProperty("hpBefore",3);result.addProperty("hpAfter",0);result.addProperty("temporaryBefore",0);result.addProperty("temporaryAfter",0);result.addProperty("zeroHp",true);
        String text=SrdAttackFeedback.describe(result,state.getAsJsonArray("actors"));check(text.contains("3 → 0")&&text.contains("paused for DM")&&text.contains("no automatic death"),"authorized zero HP explicit without invented death rules");
        System.out.println("SRD world interaction checks passed: "+count);
    }
}
