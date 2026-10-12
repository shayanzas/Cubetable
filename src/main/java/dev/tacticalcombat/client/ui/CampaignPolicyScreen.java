package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class CampaignPolicyScreen extends ManagementScreen {
    private final String id;
    private JsonObject campaign;
    public CampaignPolicyScreen(Screen parent,String id) {super("Campaign approval policy",parent);this.id=id;}
    @Override protected void refresh() {if(busy) return;fresh=false;clearRows();read(CAMPAIGN_GET,args("campaign",id),data->{campaign=data;display();});}
    private void display() {
        clearRows();var policy=campaign.getAsJsonObject("policy");boolean manage=manager(campaign);
        info("Policy applies to future changes","No existing character is silently approved or converted. DM edits retain existing backend rules.");
        toggle("New characters need approval","requireNewApproval",policy,manage);
        toggle("Edited characters need renewed approval","requireEditApproval",policy,manage);
        toggle("Lock owner editing","ownerLocked",policy,manage);
        var fields=new LinkedHashSet<String>();for(var f:array(policy,"ownerFields")) fields.add(f.getAsString());
        var supported=new LinkedHashMap<String,String>();supported.put("text.*","Name and text");supported.put("values.*","Manual numeric fields");supported.put("collections.*","Collection sections, including supported combat data");supported.put("kind","Sheet layout kind");supported.put("theme","Sheet theme");supported.put("ui","Sheet layout preferences");
        for(String field:fields) supported.putIfAbsent(field,"Existing field rule: "+field);
        for(var entry:supported.entrySet()) row((fields.contains(entry.getKey())?"Allowed: ":"Blocked: ")+entry.getValue(),"Owner field permission",manage,"Only campaign managers change policy",()->{
            var next=campaign.getAsJsonObject("policy").deepCopy();var current=new LinkedHashSet<String>();for(var f:array(next,"ownerFields")) current.add(f.getAsString());if(!current.remove(entry.getKey())) current.add(entry.getKey());var values=new JsonArray();current.forEach(values::add);next.add("ownerFields",values);change(next);
        });
    }
    private void toggle(String label,String key,JsonObject policy,boolean allowed) {row(label+": "+(flag(policy,key)?"Yes":"No"),"Change server-controlled policy",allowed,"Only campaign managers change policy",()->{var next=campaign.getAsJsonObject("policy").deepCopy();next.addProperty(key,!flag(next,key));change(next);});}
    private void change(JsonObject policy) {long expected=revision(campaign);confirm("Change campaign policy?","Existing characters keep their current approval and identity. Review this policy before applying:\n"+policy.toString(),()->mutate(CAMPAIGN_POLICY,args("campaign",id,"revision",expected,"policy",policy),d->refresh()));}
}
