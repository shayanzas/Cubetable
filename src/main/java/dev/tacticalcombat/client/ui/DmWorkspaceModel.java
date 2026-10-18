package dev.tacticalcombat.client.ui;
import com.google.gson.*;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** UI identities/filtering only; each data refresh and mutation is authorized by the existing endpoints. */
public final class DmWorkspaceModel {
    public enum Tab { PARTY, ACTORS, ENCOUNTERS, SCENE, ROLLS }
    public Tab tab=Tab.PARTY;
    public final EnumMap<Tab,String> queries=new EnumMap<>(Tab.class),selections=new EnumMap<>(Tab.class);
    public final EnumMap<Tab,Integer> scrolls=new EnumMap<>(Tab.class);
    public final LinkedHashSet<String> participants=new LinkedHashSet<>(),targets=new LinkedHashSet<>(),recipients=new LinkedHashSet<>();
    public String binding="ALL",kind="ALL",eligibility="ALL",sort="NAME",campaign="";
    public void revoke(){participants.clear();targets.clear();recipients.clear();selections.clear();campaign="";}
    public void campaign(String id){if(!campaign.equals(id)){revoke();campaign=id;}}
    public List<JsonObject> filter(JsonArray rows,String idKey){
        String query=queries.getOrDefault(tab,"").trim().toLowerCase(Locale.ROOT);var result=new ArrayList<JsonObject>();
        for(var v:rows){var r=v.getAsJsonObject();
            if(!query.isEmpty()&&!List.of("name","type","characterName","conditions","kind","reason").stream().map(k->text(r,k).toLowerCase(Locale.ROOT)).anyMatch(s->s.contains(query)))continue;
            if(idKey.equals("actor")){
                if(binding.equals("BOUND")&&!flag(r,"bound")||binding.equals("UNBOUND")&&flag(r,"bound"))continue;
                if(!kind.equals("ALL")&&!text(r,"kind").equalsIgnoreCase(kind))continue;
                if(eligibility.equals("READY")&&!flag(r,"eligible")||eligibility.equals("BLOCKED")&&flag(r,"eligible"))continue;
            }result.add(r);
        }
        if(sort.equals("DISTANCE")&&idKey.equals("actor"))result.sort(Comparator.comparingDouble(r->r.has("distance")?r.get("distance").getAsDouble():Double.MAX_VALUE));
        else result.sort(Comparator.comparing(r->text(r,"name"),String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }
    public static JsonObject find(JsonArray rows,String key,String id){for(var v:rows)if(text(v.getAsJsonObject(),key).equals(id))return v.getAsJsonObject();return null;}
    public void reconcile(JsonArray rows,String key){
        var ids=new HashSet<String>();for(var v:rows)ids.add(text(v.getAsJsonObject(),key));
        if(!ids.contains(selections.get(tab)))selections.remove(tab);
        if(key.equals("actor"))participants.removeIf(id-> {var a=find(rows,key,id);return a==null||!flag(a,"eligible");});
    }
    public static double sceneCoordinate(double value,double origin,int size){return size/2d+(value-origin)*(size-24)/64d;}
    public static String member(JsonArray members,String id){var m=find(members,"id",id);return m==null?"Offline/unknown member":text(m,"name");}
    public static String checkLabel(String id){return switch(id){case "str"->"Strength";case "dex"->"Dexterity";case "con"->"Constitution";case "int"->"Intelligence";case "wis"->"Wisdom";case "cha"->"Charisma";default->id;};}
    public static String roll(JsonObject result){String source=switch(text(result,"source")){case "PLAYER_CHARACTER_CHECK"->"Character check";case "DM_ADJUDICATION"->"DM-adjudicated check";case "DM_REQUEST"->"DM-requested check";default->"Accepted character roll";};return source+" · "+text(result,"mode")+" · dice "+result.get("faces")+(result.has("modifier")?" · validated modifier "+text(result.getAsJsonObject("modifier"),"total"):"")+" · total "+text(result,"total")+(result.has("dc")&&!result.get("dc").isJsonNull()?" · DC "+text(result,"dc")+" · "+(flag(result,"success")?"Success":"Failure"):"");}
    public record Layout(UiLayout.Rect frame,UiLayout.Rect body,int filterY) {}
    public static Layout layout(int width,int height,boolean compact){int w=Math.min(compact?680:860,width-16),h=Math.min(compact?420:530,height-16);var f=new UiLayout.Rect((width-w)/2,(height-h)/2,w,h);return new Layout(f,new UiLayout.Rect(f.x()+6,f.y()+102,w-12,Math.max(12,h-140)),f.y()+60);}
}
