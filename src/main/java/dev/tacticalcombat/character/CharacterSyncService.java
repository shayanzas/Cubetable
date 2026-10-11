package dev.tacticalcombat.character;

import com.google.gson.*;
import java.util.*;

/** Permission-filtered request API. It never broadcasts sheets, accepts principals only from the server adapter. */
public final class CharacterSyncService {
    public static final int PAGE_SIZE=8;
    private static final Gson GSON=new Gson();
    private final CharacterRepository repo;
    public CharacterSyncService(CharacterRepository repo) { this.repo=repo; }
    public record Reply(String code,String message,JsonObject data) {}
    public Reply handle(CharacterRepository.Principal who,UUID selected,String operation,JsonObject args) {
        try {
            JsonObject data=new JsonObject();
            switch(operation) {
                case "campaigns" -> {
                    data.addProperty("srdAuthoringVersion",1);
                    var rows=new JsonArray();
                    int page=args.has("page")?args.get("page").getAsInt():0;
                    if(page<0 || page>8) throw new IllegalArgumentException("Invalid page");
                    long count=repo.state().campaigns().values().stream().filter(c->repo.member(who,c.id())).count();
                    data.addProperty("total",count); data.addProperty("page",page);
                    repo.state().campaigns().values().stream().filter(c->repo.member(who,c.id()))
                        .sorted(Comparator.comparing(CharacterRepository.Campaign::name).thenComparing(c->c.id().toString()))
                        .skip((long)page*PAGE_SIZE).limit(PAGE_SIZE)
                        .forEach(c->{var row=new JsonObject(); row.addProperty("id",c.id().toString()); row.addProperty("name",c.name());
                            row.addProperty("profile",c.profile()); row.addProperty("revision",c.revision());
                            row.addProperty("manager",repo.manages(who,c.id())); row.add("policy",GSON.toJsonTree(c.policy())); rows.add(row);});
                    data.add("campaigns",rows); if(selected!=null) data.addProperty("selected",selected.toString());
                }
                case "list" -> {
                    UUID campaign=id(args,"campaign"); if(!repo.member(who,campaign)) throw new IllegalArgumentException("Campaign permission denied");
                    int page=args.get("page").getAsInt(); if(page<0 || page>128) throw new IllegalArgumentException("Invalid page");
                    var chars=repo.state().characters().values().stream().filter(c->c.campaign().equals(campaign)
                        && (c.owner().equals(who.id()) || repo.manages(who,campaign)))
                        .sorted(Comparator.comparing(c->c.id().toString())).toList();
                    var rows=new JsonArray();
                    chars.stream().skip((long)page*PAGE_SIZE).limit(PAGE_SIZE).forEach(c->rows.add(summary(who,c)));
                    data.add("characters",rows); data.addProperty("page",page); data.addProperty("total",chars.size());
                }
                case "get" -> data=snapshot(who,id(args,"character"));
                case "create" -> {
                    if(selected==null) throw new IllegalArgumentException("Select a campaign first");
                    UUID id=repo.submit(who,selected,repo.campaign(selected).profile(),args.get("sheet").getAsString());
                    data=snapshot(who,id);
                }
                case "srd_create" -> {
                    if(selected==null || !repo.campaign(selected).profile().equals(dev.tacticalcombat.rules.RulesProfiles.SRD))
                        throw new IllegalArgumentException("Select an SRD 5.1 campaign to create an SRD character");
                    var template=dev.tacticalcombat.rules.srd.SrdAuthoring.template();
                    dev.tacticalcombat.rules.srd.SrdCharacterCodec.parseSheet(template.toString());
                    UUID id=repo.submit(who,selected,dev.tacticalcombat.rules.RulesProfiles.SRD,template.toString());
                    data=snapshot(who,id);
                }
                case "edit" -> {
                    UUID id=id(args,"character");
                    repo.edit(who,id,args.get("revision").getAsLong(),args.get("runtimeRevision").getAsLong(),args.getAsJsonObject("changes"));
                    data=snapshot(who,id);
                }
                case "approve" -> {
                    UUID id=id(args,"character"); repo.visible(who,id);
                    repo.approve(who,id,args.get("revision").getAsLong()); data=snapshot(who,id);
                }
                case "policy" -> {
                    UUID id=id(args,"campaign");
                    repo.policy(who,id,args.get("revision").getAsLong(),ApprovalPolicy.parse(args.getAsJsonObject("policy")));
                    data.add("policy",GSON.toJsonTree(repo.campaign(id).policy())); data.addProperty("revision",repo.campaign(id).revision());
                }
                default -> throw new IllegalArgumentException("Unknown character operation");
            }
            return new Reply("OK",operation+" successful",data);
        } catch(java.io.IOException e) {
            return new Reply("STORAGE_ERROR","Save failed; state was not published. Contact the server administrator.",new JsonObject());
        } catch(RuntimeException e) {
            String message=e.getMessage()==null?"Invalid character request":e.getMessage();
            String code=message.startsWith("Stale")?"CONFLICT":message.toLowerCase(Locale.ROOT).matches(".*(permission|owner|locked|forbidden|membership|join this campaign|only campaign).*" )?"FORBIDDEN":"REJECTED";
            return new Reply(code,message.substring(0,Math.min(message.length(),240)),new JsonObject());
        }
    }
    private JsonObject summary(CharacterRepository.Principal who,CharacterRepository.Character c) {
        return summary(who,c,repo.effectiveSheet(c));
    }
    private JsonObject summary(CharacterRepository.Principal who,CharacterRepository.Character c,JsonObject sheet) {
        var result=new JsonObject(); result.addProperty("id",c.id().toString()); result.addProperty("campaign",c.campaign().toString());
        result.addProperty("profile",c.profile()); result.addProperty("revision",c.revision()); result.addProperty("runtimeRevision",c.runtimeRevision());
        result.addProperty("approved",c.approved()); result.addProperty("manager",repo.manages(who,c.campaign()));
        result.addProperty("owner",c.owner().toString());
        String name=c.id().toString();
        if(sheet.has("text") && sheet.getAsJsonObject("text").has("name")) name=sheet.getAsJsonObject("text").get("name").getAsString();
        result.addProperty("name",name.substring(0,Math.min(80,name.length())));
        UUID actor=repo.binding(c.id()); if(actor!=null) result.addProperty("bound",actor.toString());
        return result;
    }
    public JsonObject snapshot(CharacterRepository.Principal who,UUID id) {
        var c=repo.visible(who,id);var sheet=repo.effectiveSheet(c);var result=summary(who,c,sheet);
        result.add("sheet",sheet); result.add("stats",GSON.toJsonTree(c.stats()));
        result.add("policy",GSON.toJsonTree(repo.campaign(c.campaign()).policy()));
        if(c.profile().equals(dev.tacticalcombat.rules.RulesProfiles.SRD)) {
            var srd=new JsonObject(); srd.addProperty("authoringVersion",1);
            srd.addProperty("worldCombatEnabled",dev.tacticalcombat.rules.RulesProfiles.get(c.profile()).worldCombat());
            try {
                var stats=repo.srdInputs(c);
                srd.addProperty("valid",true); srd.addProperty("hp",stats.hp()); srd.addProperty("maximumHp",stats.maximumHp());
                srd.addProperty("temporaryHp",stats.temporaryHp()); srd.addProperty("ac",stats.armorClass());
                srd.addProperty("speedFeet",stats.walkingSpeedFeet());
                srd.addProperty("initiativeModifier",stats.modifier(dev.tacticalcombat.rules.srd.SrdCharacter.Ability.DEX)+stats.initiativeBonus());
                boolean member=repo.member(new CharacterRepository.Principal(c.owner(),false),c.campaign());
                boolean ready=c.approved() && repo.binding(id)!=null && member && stats.hp()>0;
                srd.addProperty("characterReady",ready);
                srd.addProperty("reason",!member?"Owner is not a campaign member":!c.approved()?"Needs campaign approval":repo.binding(id)==null?"Needs server binding":stats.hp()==0?"Zero HP requires adjudication":"Character prerequisites satisfied; encounter activation is separate");
            } catch(RuntimeException e) {
                srd.addProperty("valid",false); srd.addProperty("characterReady",false);
                String error=e.getMessage()==null?"Invalid SRD character data":e.getMessage();
                srd.addProperty("reason",error.substring(0,Math.min(error.length(),240)));
            }
            result.add("srd",srd);
        }
        return result;
    }
    private static UUID id(JsonObject args,String key) { return UUID.fromString(args.get(key).getAsString()); }
}
