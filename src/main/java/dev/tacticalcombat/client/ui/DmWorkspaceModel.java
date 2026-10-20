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
    public String binding="BOUND",kind="ALL",eligibility="ALL",sort="NAME",campaign="";
    private record Filters(String binding,String kind,String eligibility,String sort) {}
    private final EnumMap<Tab,Filters> filters=new EnumMap<>(Tab.class);
    private final Map<String,JsonObject> participantRows=new HashMap<>();
    public String discoveryToken="",selectionNotice="";private long discoveryGeneration;
    public record DiscoveryQuery(String campaign,String binding,String kind,long generation) {
        public JsonObject args(){return ManagementState.args("binding",binding,"kind",kind);}
    }
    public static boolean nearby(Tab tab){return tab==Tab.ACTORS||tab==Tab.ENCOUNTERS||tab==Tab.SCENE;}
    public void tab(Tab next){filters.put(tab,new Filters(binding,kind,eligibility,sort));tab=next;var f=filters.getOrDefault(next,new Filters("BOUND","ALL","ALL","NAME"));binding=f.binding();kind=f.kind();eligibility=f.eligibility();sort=f.sort();invalidateDiscovery();}
    public void invalidateDiscovery(){discoveryGeneration++;discoveryToken="";}
    public DiscoveryQuery discovery(){invalidateDiscovery();return new DiscoveryQuery(campaign,binding,kind,discoveryGeneration);}
    public boolean applyDiscovery(DiscoveryQuery query,JsonObject response){
        if(!text(response,"campaign").equals(campaign)||query.generation()!=discoveryGeneration||!query.campaign().equals(campaign)||!query.binding().equals(binding)||!query.kind().equals(kind))return false;
        var rows=array(response,"actors");discoveryToken=text(response,"discovery");selectionNotice="";
        int removed=0;for(var id:new ArrayList<>(participants)){
            var row=find(rows,"actor",id);var old=participantRows.get(id);
            boolean inScope=old!=null&&(binding.equals("ALL")||flag(old,"bound")==binding.equals("BOUND"))&&(kind.equals("ALL")||text(old,"kind").equalsIgnoreCase(kind));
            boolean changed=row!=null&&old!=null&&(!text(row,"character").equals(text(old,"character"))||!Objects.equals(row.get("characterRevision"),old.get("characterRevision")));
            // Absence from a truncated list cannot prove removal; retain intent, disable execution.
            if(changed||row!=null&&!flag(row,"eligible")||row==null&&inScope&&!flag(response,"truncated")){
                participants.remove(id);participantRows.remove(id);removed++;
                if(row!=null)selectionNotice=text(row,"name")+": "+(changed?"Associated character changed; select this actor again to review":text(row,"reason"));
            }else if(row!=null)participantRows.put(id,row.deepCopy());
        }
        if(removed>0)selectionNotice="Removed "+removed+" changed/unavailable selection(s). "+selectionNotice;
        if(find(rows,"actor",selections.getOrDefault(tab,""))==null)selections.remove(tab);
        return true;
    }
    public void toggle(JsonObject actor){String id=text(actor,"actor");if(participants.remove(id)){participantRows.remove(id);return;}if(flag(actor,"eligible")&&participants.size()<16){participants.add(id);participantRows.put(id,actor.deepCopy());}}
    public void clearParticipants(){participants.clear();participantRows.clear();}
    public String participantName(String id){var row=participantRows.get(id);return row==null?"Unavailable actor":text(row,"name");}
    public boolean selectedPresent(JsonObject snapshot){if(snapshot==null||discoveryToken.isEmpty()||!discoveryToken.equals(text(snapshot,"discovery")))return false;return participants.stream().allMatch(id->find(array(snapshot,"actors"),"actor",id)!=null);}
    public boolean startable(JsonObject snapshot){return participants.size()>=2&&participants.size()<=16&&selectedPresent(snapshot)&&participants.stream().allMatch(id->flag(find(array(snapshot,"actors"),"actor",id),"eligible"));}
    public void revoke(){participants.clear();participantRows.clear();targets.clear();recipients.clear();selections.clear();queries.clear();scrolls.clear();filters.clear();binding="BOUND";kind=eligibility="ALL";sort="NAME";campaign="";selectionNotice="";invalidateDiscovery();}

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
    public static String roll(JsonObject result){String source=switch(text(result,"source")){case "PLAYER_CHARACTER_CHECK"->"Character check";case "DM_ADJUDICATION"->"DM Character Roll";case "DM_REQUEST"->"DM-requested check";default->"Accepted character roll";};
        var kept=new ArrayList<String>();var faces=array(result,"faces");for(var index:array(result,"kept")){int i=index.getAsInt();if(i>=0&&i<faces.size())kept.add(faces.get(i).getAsString());}
        return source+" · "+text(result,"mode")+" · dice "+faces+(!kept.isEmpty()?" · kept "+String.join(", ",kept):"")+(result.has("modifier")&&!result.get("modifier").isJsonNull()?" · validated modifier "+text(result.getAsJsonObject("modifier"),"total"):"")+" · total "+text(result,"total")+(result.has("dc")&&!result.get("dc").isJsonNull()?" · DC "+text(result,"dc")+" · "+(flag(result,"success")?"Success":"Failure"):"");
    }
    public record Layout(UiLayout.Rect frame,UiLayout.Rect body,int filterY) {}
    public static String title(Tab tab){return switch(tab){case PARTY->"Campaign Characters";case ACTORS->"Nearby Actors / Prepare";case ENCOUNTERS->"Combat Management";case SCENE->"Battlefield Setup";case ROLLS->"Checks & Dice";};}
    public static String description(Tab tab){return switch(tab){case PARTY->"Campaign records, approval, controllers and sheets.";case ACTORS->"Prepare and bind nearby creatures; choose Unbound for new NPCs.";case ENCOUNTERS->"Select prepared participants, start and manage controlled combat.";case SCENE->"Saved placements and nearby positions; combat starts separately.";case ROLLS->"Request player checks, roll as DM, or use separate manual dice.";};}
    public static String empty(String binding,boolean filtered){return filtered?"No actors match these filters in this snapshot.":switch(binding){case "BOUND"->"No bound actors nearby. Switch to Unbound to prepare a creature.";case "UNBOUND"->"No unbound actors in this discovery snapshot.";default->"No actors in this discovery snapshot.";};}
    public static Layout layout(int width,int height,boolean compact){return layout(width,height,compact,Tab.PARTY);}
    public static Layout layout(int width,int height,boolean compact,Tab tab){int w=Math.min(compact?700:920,width-16),h=Math.min(compact?500:620,height-16);var f=new UiLayout.Rect((width-w)/2,(height-h)/2,w,h);int top=tab==Tab.ROLLS?49:nearby(tab)?89:69;return new Layout(f,new UiLayout.Rect(f.x()+6,f.y()+top,w-12,Math.max(12,h-top-38)),f.y()+49);}
}
