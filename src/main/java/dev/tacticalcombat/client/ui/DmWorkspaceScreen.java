package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.client.*;
import dev.tacticalcombat.checks.CharacterChecks;
import dev.tacticalcombat.dice.*;
import dev.tacticalcombat.net.DiceRequestPayload;
import dev.tacticalcombat.uiapi.UiApiService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;
import java.util.function.*;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** One authorized five-tab workspace. Discovery, checks, bindings, stages and combat retain their original gateways. */
public final class DmWorkspaceScreen extends RpgScreen {
    private final DmWorkspaceModel model=new DmWorkspaceModel();
    private JsonObject context,data,detail,encounters,stages,checkTargets,inbox;
    private CharacterCanvas canvas;
    private RpgButton manualRoll,stageSave;
    private final List<WorkspaceSelect> selects=new ArrayList<>();
    private int y,page,inboxPage,ticks,refreshAt;
    private boolean fresh,compact=true,refreshPending,dragging;
    private int dx,dy;private String confirmedText="",confirmedTitle="";private Runnable confirmed;
    private String workflow="REQUEST",category="ABILITY",check="str",mode="NORMAL",visibility="DM_ONLY",purpose="",dc="",operation="",stageName="Encounter stage";
    private String die="D20",quantity="1",modifier="0";private long manualCooldown;
    private UUID stageOperation=UUID.randomUUID();private String placementToken="";
    private DmWorkspaceModel.Layout layout;
    public DmWorkspaceScreen(Screen parent){super("DM Workspace",parent);}
    @Override protected void init(){super.init();ClientRulesWatch.close(this);busy=false;fresh=false;clearData();shell();reload();}
    private void clearData(){context=data=detail=encounters=stages=checkTargets=inbox=null;placementToken="";confirmed=null;}
    private boolean available(){return fresh&&!busy&&context!=null;}
    private void watch(JsonObject snapshot){if(!snapshot.has("panelToken"))return;ClientRulesWatch.panel(this,UUID.fromString(text(snapshot,"panelToken")),revoked->{if(client.currentScreen!=this)return;generation++;busy=false;fresh=false;confirmed=null;placementToken="";if(revoked){clearData();model.revoke();operation="";message="Campaign manager access changed. Refresh to verify access.";build();}else{message="Server state changed; updating authorized data…";refreshPending=true;refreshAt=Math.max(refreshAt,ticks+15);}});}
    private void fetch(UiApiService.Operation op,JsonObject args,Consumer<JsonObject> accept){request(op,args,r->{
        if(!r.code().equals("OK")){
            fresh=false;confirmed=null;
            if(r.code().equals("FORBIDDEN")||op==DM_QUICK){clearData();ClientRulesWatch.close(this);model.revoke();operation="";}
            else if(op==CHECK_TARGETS||op==CHECK_INBOX){checkTargets=null;inbox=null;}
            message=op+": "+r.code()+" · "+r.message()+" Refresh to retry this read.";build();return;
        }
        if(op!=DM_QUICK&&r.data().has("campaign")&&!text(r.data(),"campaign").equals(model.campaign)){
            generation++;fresh=false;clearData();operation="";message="Campaign changed. Refresh to verify current access.";build();return;
        }
        watch(r.data());accept.accept(r.data());
    });}
    private void reload(){
        if(busy)return;refreshPending=false;fresh=false;placementToken="";
        fetch(DM_QUICK,args("page",page),root->{if(!model.campaign.equals(text(root,"campaign"))){operation="";stageOperation=UUID.randomUUID();}model.campaign(text(root,"campaign"));context=root;
            switch(model.tab){
                case PARTY->{data=root;model.reconcile(array(data,"characters"),"character");String id=model.selections.getOrDefault(model.tab,"");if(id.isEmpty()){detail=null;loaded();}else fetch(QUICK_SHEET,args("character",id),s->{detail=s;loaded();});}
                case ACTORS->fetch(ENCOUNTER_DISCOVER,args("binding","ALL","kind","ALL"),s->{data=s;model.reconcile(array(s,"actors"),"actor");loaded();});
                case ENCOUNTERS->fetch(ENCOUNTER_LIST,args(),s->{encounters=s;fetch(ENCOUNTER_DISCOVER,args("binding","ALL","kind","ALL"),a->{data=a;model.reconcile(array(a,"actors"),"actor");loaded();});});
                case SCENE->fetch(ENCOUNTER_DISCOVER,args("binding","ALL","kind","ALL"),s->{data=s;model.reconcile(array(s,"actors"),"actor");fetch(STAGE_LIST,args(),a->{stages=a;loaded();});});
                case ROLLS->fetch(CHECK_TARGETS,args(),s->{checkTargets=s;model.targets.removeIf(id->DmWorkspaceModel.find(array(s,"characters"),"id",id)==null);model.recipients.removeIf(id->DmWorkspaceModel.find(array(s,"members"),"id",id)==null);fetch(CHECK_INBOX,args("page",inboxPage),a->{inbox=a;loaded();});});
            }
        });
    }
    private void loaded(){fresh=true;message="Campaign: "+text(context,"campaignName")+" · authorized DM view";build();}
    private void switchTab(DmWorkspaceModel.Tab tab){if(canvas!=null)model.scrolls.put(model.tab,canvas.position());generation++;busy=false;ClientRulesWatch.close(this);model.tab=tab;clearData();shell();reload();}
    private void shell(){
        if(canvas!=null)model.scrolls.put(model.tab,canvas.position());
        clearChildren();selects.clear();canvas=null;layout=DmWorkspaceModel.layout(width,height,compact);var f=layout.frame();int x=Math.clamp(f.x()+dx,0,width-f.width()),sy=Math.clamp(f.y()+dy,0,height-f.height());frame=new UiLayout.Rect(x,sy,f.width(),f.height());dx=x-f.x();dy=sy-f.y();
        button("Refresh","",frame.x()+frame.width()-128,frame.y()+6,64,18,()->{if(!busy){generation++;reload();}}).hint("Read-only refresh; never repeats a prior gameplay action");
        button(compact?"Expand":"Compact","",frame.x()+frame.width()-61,frame.y()+6,55,18,()->{compact=!compact;dx=dy=0;init();});
        int tw=(frame.width()-12)/5;for(var tab:DmWorkspaceModel.Tab.values())button(tab.name().substring(0,1)+tab.name().substring(1).toLowerCase(Locale.ROOT),"",frame.x()+6+tab.ordinal()*tw,frame.y()+37,tw-2,18,()->switchTab(tab)).selected(tab==model.tab);
        var search=search(frame.x()+6,frame.y()+60,Math.max(60,(frame.width()-18)/2),model.queries.getOrDefault(model.tab,""),value->{model.queries.put(model.tab,value);build();});search.setHeight(18);
        boolean nearby=model.tab==DmWorkspaceModel.Tab.ACTORS||model.tab==DmWorkspaceModel.Tab.SCENE||model.tab==DmWorkspaceModel.Tab.ENCOUNTERS;
        selectRoot("Sort",nearby?List.of(new WorkspaceSelect.Option("NAME","Name"),new WorkspaceSelect.Option("DISTANCE","Distance")):List.of(new WorkspaceSelect.Option("NAME","Name")),()->nearby?model.sort:"NAME",v->{model.sort=v;build();},frame.x()+frame.width()/2+3,frame.y()+60,frame.width()/2-9);
        if(model.tab==DmWorkspaceModel.Tab.ACTORS||model.tab==DmWorkspaceModel.Tab.SCENE||model.tab==DmWorkspaceModel.Tab.ENCOUNTERS){int cw=(frame.width()-18)/3;
            selectRoot("Binding",options("ALL","All","BOUND","Bound","UNBOUND","Unbound"),()->model.binding,v->{model.binding=v;build();},frame.x()+6,frame.y()+81,cw);
            selectRoot("Type",options("ALL","All","PLAYER","Player","NPC","NPC"),()->model.kind,v->{model.kind=v;build();},frame.x()+9+cw,frame.y()+81,cw);
            selectRoot("Ready",options("ALL","All","READY","Ready","BLOCKED","Blocked"),()->model.eligibility,v->{model.eligibility=v;build();},frame.x()+12+2*cw,frame.y()+81,cw);
        }
        button(ClientSrdCombat.hudEncounter()!=null?"Battlefield":"Back","",frame.x()+6,frame.y()+frame.height()-30,100,20,()->{if(ClientSrdCombat.hudEncounter()!=null)returnBattlefield();else close();}).hint("Return to combat without changing encounter, turn, camera or actor ownership");
        button("Full menu","",frame.x()+112,frame.y()+frame.height()-30,Math.min(100,frame.width()-118),20,()->client.setScreen(new HubScreen(this)));
        build();
    }
    private void returnBattlefield(){if(parent instanceof SrdBattlefieldScreen)client.setScreen(parent);else ClientSrdCombat.battlefield();}
    private static List<WorkspaceSelect.Option> options(String... pairs){var values=new ArrayList<WorkspaceSelect.Option>();for(int i=0;i<pairs.length;i+=2)values.add(new WorkspaceSelect.Option(pairs[i],pairs[i+1]));return values;}
    private WorkspaceSelect selectRoot(String label,List<WorkspaceSelect.Option> values,Supplier<String> value,Consumer<String> choose,int x,int y,int w){var select=addDrawableChild(new WorkspaceSelect(x,y,w,label,values,value,choose,new UiLayout.Rect(frame.x()+4,frame.y()+32,frame.width()-8,frame.height()-68)));selects.add(select);return select;}
    private int w(){return canvas.getWidth()-16;}
    private void line(String text,int color){canvas.wrapped(text,8,y,w(),color);y+=Math.max(1,textRenderer.wrapLines(Text.literal(text),w()).size())*12+5;}
    private void title(String text){line(text,RpgTheme.GOLD);}
    private RpgButton action(String title,String hint,boolean enabled,Runnable run){var b=canvas.control("action:"+y,new RpgButton(8,y,w(),22,title,"",()->{if(available())run.run();}).hint(hint));b.active=enabled&&available();y+=26;return b;}
    private void field(String key,String label,String value,int limit,Consumer<String> changed){line(label,RpgTheme.MUTED);var f=new TextFieldWidget(textRenderer,8,y,w(),18,Text.literal(label));f.setMaxLength(limit);f.setText(value);f.setChangedListener(changed);canvas.control(key,f);y+=24;}
    private void choose(String key,String label,List<WorkspaceSelect.Option> values,Supplier<String> value,Consumer<String> selected){
        var select=new WorkspaceSelect(8,y,w(),label,values,value,v->{if(available()){selected.accept(v);build();}},new UiLayout.Rect(canvas.getX(),canvas.getY(),canvas.getWidth(),canvas.getHeight()));
        select.active=available();canvas.control(key,select);y+=23;
    }

    private void confirm(String title,String detail,Runnable run){confirmedTitle=title;confirmedText=detail;confirmed=run;build();}
    private void mutate(UiApiService.Operation op,JsonObject a,Consumer<JsonObject> done){if(!available())return;fresh=false;request(op,a,r->{if(!r.code().equals("OK")){confirmed=null;placementToken="";if(r.code().equals("FORBIDDEN")){clearData();ClientRulesWatch.close(this);model.revoke();operation="";}else if(model.tab==DmWorkspaceModel.Tab.ROLLS){checkTargets=inbox=null;}message=op+": "+r.code()+" · "+r.message()+" Refresh to reconcile; no mutation is repeated automatically.";fresh=false;build();return;}fresh=true;message=r.message();done.accept(r.data());});}
    private void build(){
        if(frame==null)return;boolean ownFocus=canvas!=null&&getFocused()==canvas;String focusKey=canvas==null?"":canvas.focusedKey();var cursors=canvas==null?Map.<String,Integer>of():canvas.cursors();if(ownFocus)setFocused(null);int offset=canvas==null?model.scrolls.getOrDefault(model.tab,0):canvas.position();if(canvas!=null)remove(canvas);
        manualRoll=stageSave=null;canvas=new CharacterCanvas(new UiLayout.Rect(frame.x()+6,frame.y()+102,frame.width()-12,Math.max(12,frame.height()-140)));addDrawableChild(canvas); // selectors render above this clipped content
        for(var select:selects){remove(select);addDrawableChild(select);}y=4;
        if(context==null){line(busy?"Loading authorized campaign context…":"Refresh to verify selected campaign manager access.",RpgTheme.MUTED);return;}
        if(confirmed!=null){title(confirmedTitle);line(confirmedText,RpgTheme.TEXT);action("Confirm","Server revalidates authority and current state",true,()->{var run=confirmed;confirmed=null;run.run();});action("Cancel","No operation is sent",true,()->{confirmed=null;build();});canvas.position(0);if(ownFocus)setFocused(canvas);return;}
        switch(model.tab){case PARTY->party();case ACTORS->actors();case ENCOUNTERS->encounters();case SCENE->scene();case ROLLS->rolls();}
        canvas.position(offset);canvas.cursors(cursors);if(ownFocus){setFocused(canvas);canvas.focus(focusKey);}
    }
    private void party(){
        if(data==null){line("Loading party…",RpgTheme.MUTED);return;}
        if(detail!=null){title("Selected · "+text(detail,"name"));var rules=detail.getAsJsonObject("rules");if(rules!=null&&flag(rules,"valid"))line("HP "+text(rules,"hp")+" / "+text(rules,"maximumHp")+" · temp "+text(rules,"temporaryHp")+" · AC "+text(rules,"ac")+" · level "+text(rules,"effectiveLevel"),RpgTheme.TEXT);
            action("Full Character Sheet","Advanced character details and administration",true,()->client.setScreen(PlayerNavigation.sheet(this,new DashboardSource(false,text(detail,"id")))));
            action("Abilities, Skills & Saves","Server-calculated check options",true,()->client.setScreen(new CharacterRollMenuScreen(this,detail)));
            if(!flag(detail,"approved"))action("Approve character…","Only unbound records can be approved; no ownership change",text(detail,"bound").isEmpty(),()->{var a=args("character",text(detail,"id"),"revision",revision(detail));confirm("Approve "+text(detail,"name")+"?","Server approval and revision rules remain in force.",()->mutate(CHARACTER_APPROVE,a,r->reload()));});
        }
        title("Party · campaign characters (page "+(page+1)+")");var rows=model.filter(array(data,"characters"),"character");if(rows.isEmpty())line("No matching characters on this page.",RpgTheme.MUTED);
        for(var c:rows){String id=text(c,"character");canvas.panel("",8,y,w(),82);if(text(c,"kind").equals("NPC"))canvas.marker(18,y+12,16,false);else canvas.skinHead(text(c,"owner"),14,y+8,24);canvas.boldLabel(text(c,"name"),44,y+6,w()-44,RpgTheme.GOLD);canvas.label(text(c,"description").isBlank()?text(c,"kind"):text(c,"description"),44,y+20,w()-44,RpgTheme.TEXT);canvas.label(text(c,"health")+(c.has("temporaryHp")?" · temp "+text(c,"temporaryHp"):"")+(c.has("ac")?" · AC "+text(c,"ac"):""),14,y+36,w()-14,RpgTheme.TEXT);canvas.label((flag(c,"approved")?"Approved":"Needs approval")+" · "+(flag(c,"bound")?"Bound":"Unbound")+" · "+text(c,"controllerName"),14,y+49,w()-14,RpgTheme.MUTED);canvas.label(text(c,"conditions").isBlank()?"No recorded conditions":text(c,"conditions"),14,y+62,w()-14,RpgTheme.MUTED);y+=86;
            action(model.selections.getOrDefault(model.tab,"").equals(id)?"Selected · "+text(c,"name"):"Inspect "+text(c,"name"),"Inline authorized overview; no binding/ownership change",true,()->{model.selections.put(model.tab,id);detail=null;if(canvas!=null)canvas.position(0);generation++;fresh=false;fetch(QUICK_SHEET,args("character",id),s->{detail=s;fresh=true;build();});});}
        if(page>0)action("Previous party page","",true,()->{page--;detail=null;reload();});if(flag(data,"hasMore"))action("Next party page","",true,()->{page++;detail=null;reload();});action("Advanced roster management","Assignments, controller changes, binding and approval review",true,()->client.setScreen(new CampaignRosterScreen(this,text(context,"campaign"))));
    }
    private void toggleParticipant(JsonObject actor){String id=text(actor,"actor");if(!model.participants.remove(id)&&model.participants.size()<16)model.participants.add(id);build();}
    private void start(){if(data==null)return;var a=args("discovery",text(data,"discovery"));var ids=new JsonArray();model.participants.forEach(ids::add);a.add("actors",ids);confirm("Start controlled encounter?",model.participants.size()+" selected actors. The server rechecks every binding, safe anchor and current campaign revision.",()->mutate(ENCOUNTER_START,a,r->{model.participants.clear();client.setScreen(SrdCombatScreen.entry(this));}));}
    private void actorRows(){
        var rows=model.filter(array(data,"actors"),"actor");if(rows.isEmpty())line("No actors match the filters in this discovery snapshot.",RpgTheme.MUTED);
        for(var a:rows){String id=text(a,"actor");boolean selected=model.selections.getOrDefault(model.tab,"").equals(id);
            action((model.participants.contains(id)?"✓ ":"")+text(a,"name")+" · "+text(a,"distance")+"m",text(a,"type")+" · "+text(a,"reason"),true,()->{model.selections.put(model.tab,id);build();});
            line(text(a,"kind")+" · "+(flag(a,"bound")?"Bound: "+text(a,"characterName"):"Unbound")+" · "+(flag(a,"eligible")?"Ready":text(a,"reason")),flag(a,"eligible")?RpgTheme.TEXT:RpgTheme.MUTED);
            if(selected){line("Minecraft: "+text(a,"type")+" · Position "+text(a,"position"),RpgTheme.TEXT);if(a.has("monsterSupport"))line(text(a,"monsterSupport"),RpgTheme.MUTED);
                action(model.participants.contains(id)?"Remove participant":"Add participant",flag(a,"eligible")?"Selection only; does not move actors or begin combat":text(a,"reason"),flag(a,"eligible"),()->toggleParticipant(a));
                if(flag(a,"canBind")){action("Bind approved campaign character…","Server-discovered unbound NPC only",true,()->client.setScreen(new ActorBindingScreen(this,id)));action("Prepare from SRD monster…","Review source stats/support before explicit creation, approval and binding",true,()->client.setScreen(new MonsterPreparationScreen(this,id)));}
            }
        }
    }
    private void actors(){if(data==null){line("Loading nearby actors…",RpgTheme.MUTED);return;}title("Nearby actors · "+text(data,"radius")+" block radius · "+array(data,"actors").size()+" found");line("Discovery origin: "+text(data,"location")+" · "+text(data,"dimension")+(flag(data,"truncated")?" · list limited; move closer and refresh":""),RpgTheme.MUTED);action("Start selected encounter ("+model.participants.size()+")…","Select 2–16 ready actors; explicit confirmation required",model.participants.size()>=2,this::start);actorRows();}
    private void encounters(){
        if(encounters==null||data==null){line("Loading encounters and eligible participants…",RpgTheme.MUTED);return;}
        title("Encounter dashboard");line("Ready nearby: "+array(data,"actors").asList().stream().filter(a->flag(a.getAsJsonObject(),"eligible")).count()+" · selected: "+model.participants.size(),RpgTheme.TEXT);
        var live=ClientSrdCombat.workspaceState();if(text(live,"campaign").equals(text(context,"campaign"))){title("Watched encounter · "+text(live,"state")+" · round "+text(live,"round"));line("Movement "+text(live,"remainingFeet")+" ft · action "+(flag(live,"actionUsed")?"spent / reserved":"available"),RpgTheme.TEXT);for(var v:array(live,"actors")){var a=v.getAsJsonObject();line((text(live,"active").equals(text(a,"id"))?"CURRENT TURN · ":"")+text(a,"name")+" · initiative "+text(a,"initiative")+" · "+SrdBattlefieldModel.hp(a),RpgTheme.TEXT);}if(text(live,"state").equals("PAUSED")&&flag(live,"manager"))action("Paused encounter: heal / review / end…","Explicit manager adjudication; healing does not resume combat",true,()->client.setScreen(new SrdRecoveryScreen(this)));action("Return to tactical battlefield","Preserve SRD state and camera",true,this::returnBattlefield);}
        if(array(encounters,"encounters").isEmpty())line("No active controlled encounter in the selected campaign.",RpgTheme.MUTED);
        for(var v:array(encounters,"encounters")){var e=v.getAsJsonObject();String id=text(e,"encounter");title(text(e,"state")+" · "+text(e,"actors"));line(text(e,"reason"),RpgTheme.MUTED);action("Inspect initiative / watch encounter","Read-only watch; does not start or advance combat",true,()->fetch(ENCOUNTER_WATCH,args("encounter",id),r->{fresh=true;build();}));action("End this encounter…","Persistent HP and resources remain committed",true,()->confirm("End controlled encounter?",text(e,"actors")+". Prepared transactions remain subject to J2 recovery.",()->mutate(ENCOUNTER_END,args("encounter",id),r->reload())));}
        action("Start selected participants ("+model.participants.size()+")…","Select ready actors below or in Actors / Scene",model.participants.size()>=2,this::start);actorRows();action("Gameplay Transaction Review","Advanced J2 recovery; no automatic resource replay",true,()->client.setScreen(new GameplayRecoveryScreen(this)));action("Saved encounter stages…","Templates and placement are separate from combat activation",true,()->client.setScreen(new EncounterStagesScreen(this)));
    }
    private void scene(){
        if(data==null||stages==null){line("Loading discovery area and saved templates…",RpgTheme.MUTED);return;}
        title("Scene preparation · "+text(data,"location"));line("Discover → select participants → save/validate placements → confirm placement → start encounter separately.",RpgTheme.MUTED);
        int size=Math.min(300,w()),mx=8+(w()-size)/2,my=y;canvas.panel("N ↑ · 32-block discovery area",mx,my,size,size);double ox=data.get("originX").getAsDouble(),oz=data.get("originZ").getAsDouble();canvas.marker(mx+size/2-3,my+size/2-3,7,true);
        var actorList=model.filter(array(data,"actors"),"actor");int number=0;for(var a:actorList){int px=mx+(int)DmWorkspaceModel.sceneCoordinate(a.get("x").getAsDouble(),ox,size),pz=my+(int)DmWorkspaceModel.sceneCoordinate(a.get("z").getAsDouble(),oz,size);String id=text(a,"actor");int index=++number;canvas.marker(px-3,pz-3,7,flag(a,"eligible"));canvas.label(index+" · "+text(a,"name"),Math.clamp(px+5,mx+6,mx+size-96),Math.clamp(pz-5,my+20,my+size-13),90,flag(a,"eligible")?RpgTheme.GOLD:RpgTheme.MUTED);canvas.tooltip(px-5,pz-5,12,12,text(a,"name")+" · "+text(a,"reason"));canvas.control("map:"+id,new RpgButton(px-4,pz-4,9,9,"","",()->{if(available()){model.selections.put(model.tab,id);build();}}).round().selected(model.selections.getOrDefault(model.tab,"").equals(id)).hint(text(a,"name")+" · "+text(a,"reason")));}y+=size+8;
        action("Start selected participants ("+model.participants.size()+")…","Browsing/selecting this map never moves an actor",model.participants.size()>=2,this::start);
        title("Selected placement order");int selectedIndex=0;for(var id:model.participants){var actor=DmWorkspaceModel.find(array(data,"actors"),"actor",id);line((++selectedIndex)+" · "+(actor==null?"Unavailable actor":text(actor,"name")),RpgTheme.TEXT);}
        title("Saved stages · reusable location and placement templates");line("A stage is not an active encounter. Capturing saves positions; Validate only previews. Activate explicitly moves actors, then combat still requires Start.",RpgTheme.MUTED);
        field("stageName","New stage name at your current location",stageName,64,s->stageName=s);stageSave=action("Save empty stage here","No actors move and no combat begins",!stageName.isBlank(),()->mutate(STAGE_CREATE,args("stage",stageOperation,"name",stageName,"description",""),r->{stageOperation=UUID.randomUUID();reload();}));
        for(var v:array(stages,"stages")){var s=v.getAsJsonObject();String id=text(s,"id");title(text(s,"name")+" · "+array(s,"slots").size()+" placement slots");line(text(s,"dimension")+" · "+text(s,"x")+", "+text(s,"y")+", "+text(s,"z"),RpgTheme.MUTED);int slot=0;for(var vv:array(s,"slots")){var ss=vv.getAsJsonObject();line("Slot "+(++slot)+": "+text(ss,"role")+" · "+text(ss,"x")+", "+text(ss,"y")+", "+text(ss,"z"),RpgTheme.TEXT);}
            var ids=new JsonArray();model.participants.forEach(ids::add);var a=args("stage",id,"revision",s.get("revision").getAsLong(),"actors",ids);
            action("Capture selected current placements…","Replaces slots without moving actors",!model.participants.isEmpty(),()->confirm("Replace saved placements?",text(s,"name")+" · "+model.participants.size()+" actors in selection order. No movement.",()->mutate(STAGE_CAPTURE,a,r->reload())));
            action("Validate selected actors for these slots","Selection order maps to displayed slot order; no movement yet",ids.size()==array(s,"slots").size()&&!ids.isEmpty(),()->mutate(STAGE_VALIDATE,a,r->{placementToken=text(r,"token");confirm("Activate validated stage placements?","Move "+text(r,"count")+" actors to the displayed saved slots. Maximum 16 blocks each; server revalidates. No combat starts.",()->mutate(STAGE_ACTIVATE,args("token",placementToken),done->{placementToken="";reload();}));}));
            action("Edit roles / template details…","Advanced revision-safe stage editor",true,()->client.setScreen(new EncounterStagesScreen(this,id)));action("Delete stage…","Does not delete actors or characters",true,()->confirm("Delete "+text(s,"name")+"?","Delete only this saved template.",()->mutate(STAGE_DELETE,args("stage",id,"revision",s.get("revision").getAsLong()),r->reload())));
        }
        title("Discovered actors · choose participants in slot order");actorRows();
    }
    private void rolls(){
        if(checkTargets==null||inbox==null){line(busy?"Loading authorized check targets and inbox…":"Roll data unavailable. Use Refresh above; no check will execute.",RpgTheme.MUTED);return;}
        title("Roll console · server-owned outcomes");
        choose("workflow","Workflow",options("REQUEST","Request player checks","ADJUDICATED","DM-adjudicated character check","MANUAL","Manual dice"),()->workflow,v->{workflow=v;model.targets.clear();});
        if(workflow.equals("MANUAL")){manual();}else{
            choose("category","Check category",options("ABILITY","Ability check","SKILL","Skill check","SAVE","Saving throw"),()->category,v->{category=v;check=CharacterChecks.keys().stream().filter(k->k.category().name().equals(v)).findFirst().orElseThrow().id();});
            var keys=new ArrayList<WorkspaceSelect.Option>();for(var key:CharacterChecks.keys())if(key.category().name().equals(category))keys.add(new WorkspaceSelect.Option(key.id(),DmWorkspaceModel.checkLabel(key.id())));choose("check","Check",keys,()->check,v->check=v);
            choose("mode","Roll mode (explicit DM choice)",options("NORMAL","Normal","ADVANTAGE","Advantage","DISADVANTAGE","Disadvantage"),()->mode,v->mode=v);
            choose("visibility","Result audience · DC/outcome always DM-only",options("DM_ONLY","DM only","CAMPAIGN","Campaign","RECIPIENTS","Specific recipients"),()->visibility,v->visibility=v);
            field("purpose","Purpose (requests with identical settings reuse the group for 10 minutes)",purpose,80,s->purpose=s);field("dc","Hidden DC (optional)",dc,3,s->dc=s);
            title("Select "+(workflow.equals("REQUEST")?"online bound player characters (up to 16)":"one approved character"));
            for(var c:model.filter(array(checkTargets,"characters"),"id")){String id=text(c,"id");boolean eligible=!workflow.equals("REQUEST")||flag(c,"requestEligible");action((model.targets.contains(id)?"✓ ":"□ ")+text(c,"name"),eligible?"Server rechecks current character/controller and permissions":"Player requests require an online bound controller",eligible,()->{if(!model.targets.remove(id)){if(workflow.equals("ADJUDICATED"))model.targets.clear();if(model.targets.size()<16)model.targets.add(id);}build();});}
            if(visibility.equals("RECIPIENTS")){title("Explicit result recipients");for(var v:array(checkTargets,"members")){var m=v.getAsJsonObject();String id=text(m,"id");action((model.recipients.contains(id)?"✓ ":"□ ")+text(m,"name"),"Current DMs also receive results",true,()->{if(!model.recipients.remove(id)&&model.recipients.size()<16)model.recipients.add(id);build();});}}
            action(workflow.equals("REQUEST")?"Send check requests":"Prepare DM roll","No combat effects; server validates modifiers and visibility",!model.targets.isEmpty(),this::sendCheck);
            if(!operation.isEmpty())action("Roll / retrieve prepared operation","Reuses the same server operation; does not create another roll",true,()->execute(operation));
        }
        title("Pending requests & accepted results · page "+(inboxPage+1));line("Session records expire after 10 minutes. Refresh/history browsing never rolls or replays an animation.",RpgTheme.MUTED);
        for(var v:array(inbox,"rolls")){var view=v.getAsJsonObject();var t=view.getAsJsonObject("ticket");var spec=t.getAsJsonObject("spec");var actor=DmWorkspaceModel.find(array(checkTargets,"characters"),"id",text(t,"character"));title((actor==null?"Character":text(actor,"name"))+" · "+text(spec.getAsJsonObject("check"),"category")+" "+text(spec.getAsJsonObject("check"),"id")+" · "+text(view,"status"));line(text(spec,"purpose")+" · "+text(spec,"visibility"),RpgTheme.MUTED);
            if(view.has("result")&&!view.get("result").isJsonNull())line(DmWorkspaceModel.roll(view.getAsJsonObject("result")),RpgTheme.TEXT);
            else if(text(view,"status").equals("PENDING")){line("Awaiting "+DmWorkspaceModel.member(array(checkTargets,"members"),text(t,"executor")),RpgTheme.TEXT);if(client.player!=null&&text(t,"executor").equals(client.player.getUuidAsString()))action("Roll requested check","Execute this same authorized operation",true,()->execute(text(t,"id")));action("Dismiss pending request","Terminal dismissal; no roll or resource cost",true,()->mutate(CHECK_DISMISS,args("operation",text(t,"id")),r->reload()));}else line("No authorized result data is available.",RpgTheme.MUTED);
        }
        if(inboxPage>0)action("Previous results","",true,()->{inboxPage--;reload();});if(flag(inbox,"hasMore"))action("Next results","",true,()->{inboxPage++;reload();});action("Advanced check requests / adjudication…","Full existing grouped workflow",true,()->client.setScreen(new DmCheckScreen(this)));action("Dice animation / audio settings…","Local presentation only",true,()->client.setScreen(new DicePresentationScreen(this)));
    }
    private void sendCheck(){
        try{Integer threshold=dc.isBlank()?null:Integer.valueOf(dc);var recipients=new JsonArray();if(visibility.equals("RECIPIENTS"))model.recipients.forEach(recipients::add);var spec=args("category",category,"key",check,"mode",mode,"purpose",purpose,"visibility",visibility,"recipients",recipients,"dc",threshold);
            if(workflow.equals("REQUEST")){var ids=new JsonArray();model.targets.forEach(ids::add);mutate(CHECK_REQUEST,args("characters",ids,"spec",spec),r->reload());}
            else{var c=DmWorkspaceModel.find(array(checkTargets,"characters"),"id",model.targets.iterator().next());if(c==null){message="Target unavailable; refresh first.";return;}mutate(CHECK_PREPARE,args("character",text(c,"id"),"revision",revision(c),"spec",spec,"adjudicated",true),r->{operation=text(r,"operation");message="Prepared. Choose Roll / retrieve to execute this same operation.";build();});}
        }catch(NumberFormatException invalid){message="Enter a whole hidden DC or leave it blank.";}
    }
    private void execute(String id){mutate(CHECK_EXECUTE,args("operation",id),r->{if(r.has("result")&&!r.get("result").isJsonNull()){DiceAnimation.enqueueCheck(r.getAsJsonObject("result"));message=DmWorkspaceModel.roll(r.getAsJsonObject("result"));}else message="Accepted; result restricted to authorized recipients.";operation="";reload();});}
    private void manual(){
        choose("die","Manual die",Arrays.stream(DiceType.values()).map(d->new WorkspaceSelect.Option(d.name(),d.label)).toList(),()->die,v->die=v);
        choose("quantity","Quantity",java.util.stream.IntStream.rangeClosed(1,10).mapToObj(i->new WorkspaceSelect.Option(""+i,""+i)).toList(),()->quantity,v->quantity=v);
        if(die.equals("D20")&&quantity.equals("1"))choose("manualMode","Manual D20 mode",options("NORMAL","Normal","ADVANTAGE","Advantage","DISADVANTAGE","Disadvantage"),()->mode,v->mode=v);
        field("modifier","Manual modifier (not an authenticated character bonus)",modifier,4,s->modifier=s);
        manualRoll=action("Roll manual dice","Nearby/encounter audience from existing DiceService; no character authority",ticks>=manualCooldown,()->{try{int n=Integer.parseInt(modifier);if(n< -999||n>999)throw new NumberFormatException();if(!ClientPlayNetworking.canSend(DiceRequestPayload.ID)){message="Server dice unavailable";return;}ClientPlayNetworking.send(new DiceRequestPayload("DM manual dice",DiceType.valueOf(die),Integer.parseInt(quantity),n,die.equals("D20")&&quantity.equals("1")?(mode.equals("ADVANTAGE")?1:mode.equals("DISADVANTAGE")?2:0):0));manualCooldown=ticks+20;message="Requested server manual roll";build();}catch(NumberFormatException bad){message="Manual modifier must be -999 to 999.";}});canvas.liveLabel(()->"Manual result: "+DiceToolbar.result(),8,y,w(),RpgTheme.TEXT);y+=20;line("Manual rolls use the existing nearby/encounter audience; they are not secret DM character checks.",RpgTheme.MUTED);
    }
    @Override protected void background(DrawContext g){RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),0xDD191D23,false);RpgTheme.text(g,textRenderer,"DM WORKSPACE · drag title to move",frame.x()+8,frame.y()+8,frame.width()-142,RpgTheme.GOLD);RpgTheme.text(g,textRenderer,"Campaign: "+(context==null?"verifying…":text(context,"campaignName")),frame.x()+8,frame.y()+24,frame.width()-16,RpgTheme.TEXT);}
    @Override public void tick(){super.tick();ticks++;if(stageSave!=null)stageSave.active=available()&&!stageName.isBlank();if(manualRoll!=null)manualRoll.active=available()&&ticks>=manualCooldown;if(refreshPending&&!busy&&ticks>=refreshAt){refreshAt=ticks+20;reload();}else if(model.tab==DmWorkspaceModel.Tab.ENCOUNTERS&&fresh&&!busy&&ticks%20==0)build();else if(model.tab==DmWorkspaceModel.Tab.ROLLS&&fresh&&!busy&&confirmed==null&&ticks%100==0&&(canvas==null||!canvas.editingText()))fetch(CHECK_INBOX,args("page",inboxPage),s->{inbox=s;fresh=true;build();});}
    @Override public boolean mouseClicked(double x,double y,int button){for(var s:selects)if(s.isOpen()){if(s.isMouseOver(x,y)){setFocused(s);return s.mouseClicked(x,y,button);}s.dismiss();return true;}if(button==0&&x>=frame.x()&&x<frame.x()+frame.width()-140&&y>=frame.y()&&y<frame.y()+20){dragging=true;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double mx,double my){if(dragging&&button==0){dx+=(int)mx;dy+=(int)my;shell();return true;}return super.mouseDragged(x,y,button,mx,my);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){for(var s:selects)if(s.isOpen())return s.mouseScrolled(x,y,horizontal,vertical);return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(canvas!=null&&getFocused()==canvas&&canvas.editingText()){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){canvas.setFocused(false);setFocused(null);return true;}return canvas.keyPressed(key,scan,modifiers);}if(TacticalCombatClient.DM_QUICK_KEY.matchesKey(key,scan)&&!(getFocused() instanceof TextFieldWidget)){close();return true;}for(var s:selects)if(s.isOpen()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){s.dismiss();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void removed(){ClientRulesWatch.close(this);fresh=false;clearData();super.removed();}
}
