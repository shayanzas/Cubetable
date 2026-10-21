package dev.tacticalcombat.combat;

public final class SrdReactionTests {
    private static int count;
    private static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
    private static void rejects(Runnable r){boolean denied=false;try{r.run();}catch(IllegalArgumentException expected){denied=true;}check(denied,"invalid transition rejected");}
    public static void run(){
        var a=new SrdReactionBudget();var b=new SrdReactionBudget();check(a.available()&&b.available(),"available before first turn");a.reserve();check(!a.available()&&a.state()==SrdReactionBudget.State.RESERVED,"pending reserves");rejects(a::reserve);rejects(a::refresh);a.commit();check(a.state()==SrdReactionBudget.State.SPENT,"committed spent");rejects(a::reserve);rejects(a::commit);b.refresh();check(!a.available()&&b.available(),"other actor cannot refresh spent reaction");a.refresh();check(a.available(),"own turn refresh");rejects(a::commit);
        for(int reach:new int[]{5,10,15,20})for(int before=0;before<=30;before+=5)for(int after=0;after<=30;after+=5)check(SrdReactionBudget.leavesReach(before,after,reach)==(before<=reach&&after>reach),"only departure crosses supported reach");
        check(!SrdReactionBudget.leavesReach(5,5.00000001,5),"epsilon avoids fake boundary");
        var state=com.google.gson.JsonParser.parseString("{\"state\":\"ACTIVE\",\"active\":\"actor\",\"campaign\":\"campaign\",\"canDisengage\":true,\"actors\":[{\"id\":\"actor\",\"controlled\":true}]}").getAsJsonObject();
        check(dev.tacticalcombat.client.SrdBattlefieldActions.canDisengage(state,""),"authorized Disengage advertised");
        check(dev.tacticalcombat.client.SrdBattlefieldActions.disengage(state,7,"").keySet().equals(java.util.Set.of("campaign","actor","revision")),"client sends scope only, no movement grant or cost");
        for(var op:java.util.List.of("move","reaction_confirm","reaction_preview","disengage"))check(!dev.tacticalcombat.client.SrdBattlefieldActions.canDisengage(state,op),"pending protects all actions");
        state.getAsJsonArray("actors").get(0).getAsJsonObject().addProperty("controlled",false);check(!dev.tacticalcombat.client.SrdBattlefieldActions.canDisengage(state,""),"revocation disables Disengage");
        check(!dev.tacticalcombat.client.SrdBattlefieldActions.canDisengage(new com.google.gson.JsonObject(),""),"disconnect/legacy states disabled");
        for(int width:new int[]{256,320,426,640,700,854})for(int height:new int[]{144,180,240,480}){
            var bar=dev.tacticalcombat.client.SrdBattlefieldPresentation.layout(width,height).bar();int cols=width<700?5:10,w=(bar.w()-8-(cols-1)*3)/cols;
            for(int index=0;index<10;index++)check(bar.x()+4+(index%cols)*(w+3)+w<=width&&bar.y()+3+(index/cols)*21+18<=height,"all ten commands remain within representative viewport");
        }
        System.out.println("SRD reaction foundation checks passed: "+count);
    }
}
