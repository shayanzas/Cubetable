package dev.tacticalcombat.client.dicevisual;

import com.google.gson.*;
import dev.tacticalcombat.net.DiceRollPayload;
import dev.tacticalcombat.uiapi.UiEvents;
import java.util.*;

/** Authorized, immutable presentation input. This class never generates or revises a server result. */
public record PresentationRoll(String identity,String title,String source,int sides,List<Integer> faces,List<Integer> kept,
                               int modifier,int total,String breakdown,Integer dc,Boolean success,boolean official) {
    public PresentationRoll {
        Objects.requireNonNull(identity);Objects.requireNonNull(title);Objects.requireNonNull(source);
        faces=List.copyOf(faces);kept=List.copyOf(kept);
        if(!Set.of(4,6,8,10,12,20,100).contains(sides)||faces.isEmpty()||faces.size()>10)throw new IllegalArgumentException("Invalid dice presentation");
        for(int face:faces)if(face<1||face>sides)throw new IllegalArgumentException("Invalid face");
        for(int index:kept)if(index<0||index>=faces.size())throw new IllegalArgumentException("Invalid kept index");
        if(new HashSet<>(kept).size()!=kept.size())throw new IllegalArgumentException("Repeated kept index");
        if(official&&(sides!=20||faces.size()<1||faces.size()>2||kept.size()!=1))throw new IllegalArgumentException("Invalid official check dice");
    }
    public static PresentationRoll manual(DiceRollPayload p,long sequence){
        var faces=Arrays.stream(p.results()).boxed().toList();var kept=new ArrayList<Integer>();
        if(p.keptIndex()>=0)kept.add(p.keptIndex());else for(int i=0;i<faces.size();i++)kept.add(i);
        return new PresentationRoll("manual:"+sequence,p.label().isBlank()?"Manual Roll":p.label(),"Manual Roll",p.type().sides,faces,kept,p.modifier(),p.total(),"Manual modifier: "+signed(p.modifier()),null,null,false);
    }
    public static PresentationRoll check(JsonObject result){
        if(result==null)return null; // restricted acknowledgement carries no result
        if(result.get("version").getAsInt()!=1)throw new IllegalArgumentException("Unsupported result version");
        String id=result.get("operation").getAsString(),mode=result.get("mode").getAsString();
        var faces=ints(result.getAsJsonArray("faces"));var kept=ints(result.getAsJsonArray("kept"));
        var key=result.getAsJsonObject("check");String category=key.get("category").getAsString(),name=key.get("id").getAsString();
        String display=switch(name){case "str"->"Strength";case "dex"->"Dexterity";case "con"->"Constitution";case "int"->"Intelligence";case "wis"->"Wisdom";case "cha"->"Charisma";default->name;};
        String title=display+switch(category){case "SAVE"->" Saving Throw";case "ABILITY"->" Ability Check";case "SKILL"->" Check";default->" Check";};
        String source=switch(result.get("source").getAsString()){case "DM_ADJUDICATION"->"DM-Adjudicated Roll";case "DM_REQUEST"->"DM-Requested Check";case "PLAYER_CHARACTER_CHECK"->"Player Character Check";default->"Character Check";};String purpose=result.has("purpose")?result.get("purpose").getAsString():"";
        if(!purpose.isBlank())title=purpose+" · "+title;
        var m=result.getAsJsonObject("modifier");int bonus=m.get("total").getAsInt();
        String breakdown=m.get("ability").getAsString().toUpperCase(Locale.ROOT)+" "+signed(m.get("abilityModifier").getAsInt())+
                " · proficiency "+signed(m.get("proficiencyBonus").getAsInt())+" × "+m.get("multiplier").getAsInt();
        Integer dc=result.has("dc")&&!result.get("dc").isJsonNull()?result.get("dc").getAsInt():null;
        Boolean success=result.has("success")&&!result.get("success").isJsonNull()?result.get("success").getAsBoolean():null;
        return new PresentationRoll(id,title,source+" · "+mode,result.get("sides").getAsInt(),faces,kept,bonus,result.get("total").getAsInt(),breakdown,dc,success,true);
    }
    public static PresentationRoll event(UUID id,String kind,String summary,UiEvents.Roll roll){
        if(roll==null)return null;
        String title=roll.purpose().isBlank()?summary:roll.purpose();
        return new PresentationRoll(id.toString(),title,kind,roll.sides(),roll.faces(),roll.kept(),roll.modifier(),roll.total(),"Modifier: "+signed(roll.modifier()),null,null,kind.equals("CHARACTER_CHECK"));
    }
    private static List<Integer> ints(JsonArray a){var out=new ArrayList<Integer>();for(var v:a)out.add(v.getAsInt());return out;}
    private static String signed(int value){return value>=0?"+"+value:Integer.toString(value);}
    public boolean retained(int index){return kept.contains(index);}
    public String calculation(){return "d"+sides+" "+faces+"  "+(modifier>=0?"+":"")+modifier+"  =  "+total;}
}
