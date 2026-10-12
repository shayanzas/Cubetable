package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** One bounded roster traversal. Never publishes mixed or partial roster generations. */
public final class PartyPages {
    private final LinkedHashMap<String,JsonObject> rows=new LinkedHashMap<>();
    private String token="";
    private int page,total;
    private boolean restarted;
    public int page(){return page;}
    public boolean loading(){return page>0;}
    public boolean restarted(){return restarted;}
    public void clear(){rows.clear();token="";page=0;total=0;restarted=false;}
    public JsonObject accept(JsonObject reply){
        restarted=false;
        // Older servers expose only one page. Do not invent an unbounded pagination contract.
        if(!reply.has("rosterToken")){clear();return reply.deepCopy();}
        String incoming=text(reply,"rosterToken");int received=reply.get("page").getAsInt(),count=reply.get("total").getAsInt();
        var members=array(reply,"members");
        if(incoming.isBlank()||count<0||count>1024||received!=page||members.size()>8)throw new IllegalArgumentException("Invalid party page");
        if(page>0&&(!token.equals(incoming)||total!=count)){clear();restarted=true;return null;}
        if(page==0){rows.clear();token=incoming;total=count;}
        for(var e:members){var row=e.getAsJsonObject();String id=text(row,"character");if(id.isBlank()||rows.putIfAbsent(id,row.deepCopy())!=null)throw new IllegalArgumentException("Duplicate party identity");}
        if(rows.size()>total)throw new IllegalArgumentException("Party count changed");
        if(flag(reply,"hasMore")){if(members.size()!=8||++page>=128)throw new IllegalArgumentException("Party pagination limit");return null;}
        if(rows.size()!=total)throw new IllegalArgumentException("Incomplete party snapshot");
        var out=reply.deepCopy();var combined=new JsonArray();rows.values().forEach(combined::add);out.add("members",combined);clear();return out;
    }
}
