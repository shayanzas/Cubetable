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
    private final WorkspaceMutationGate mutations=new WorkspaceMutationGate();
    private boolean fresh,compact=true,refreshPending,dragging,rollAdvanced;
    private String selectedStage="",selectedEncounter="",selectedRoll="",liveStamp="";
    private int inboxFirst,inboxSelected;private RpgList inboxList;
    private final Map<String,RpgList> lists=new HashMap<>();private final Map<String,int[]> listPositions=new HashMap<>();
    private int dx,dy;private String confirmedText="",confirmedTitle="";private Runnable confirmed;
    private String workflow="REQUEST",category="ABILITY",check="str",mode="NORMAL",visibility="DM_ONLY",purpose="",dc="",operation="",stageName="Encounter stage";
    private String rollQuery="",preparedLabel="",manualMode="NORMAL";
    private String die="D20",quantity="1",modifier="0";private long manualCooldown;
    private UUID stageOperation=UUID.randomUUID();private String placementToken="";
    private DmWorkspaceModel.Layout layout;
    public DmWorkspaceScreen(Screen parent){super("DM Workspace",parent);}
    @Override protected void init(){super.init();ClientRulesWatch.close(this);busy=mutations.pending();fresh=false;clearData();shell();reload();}
    private void clearData(){context=data=detail=encounters=stages=checkTargets=inbox=null;placementToken="";confirmed=null;}
    private void confidential(){mutations.invalidate();busy=false;inboxList=null;listPositions.clear();lists.clear();rollAdvanced=false;rollQuery="";preparedLabel="";operation="";purpose=dc="";visibility="DM_ONLY";category="ABILITY";check="str";mode="NORMAL";workflow="REQUEST";stageName="Encounter stage";selectedStage=selectedEncounter=selectedRoll="";inboxPage=inboxFirst=inboxSelected=0;stageOperation=UUID.randomUUID();model.targets.clear();model.recipients.clear();}
    private boolean available(){return fresh&&!busy&&!mutations.pending()&&context!=null;}
    private void watch(JsonObject snapshot){if(!snapshot.has("panelToken"))return;ClientRulesWatch.panel(this,UUID.fromString(text(snapshot,"panelToken")),revoked->{if(client.currentScreen!=this)return;generation++;if(!mutations.pending())busy=false;fresh=false;confirmed=null;placementToken="";model.invalidateDiscovery();if(revoked){clearData();model.revoke();confidential();message="Campaign manager access changed. Refresh to verify access.";build();}else{message="Server state changed; updating authorized data…";refreshPending=true;refreshAt=Math.max(refreshAt,ticks+15);}});}
    private void fetch(UiApiService.Operation op,JsonObject args,Consumer<JsonObject> accept){request(op,args,r->{
        if(!r.code().equals("OK")){
            fresh=false;confirmed=null;
            if(r.code().equals("FORBIDDEN")){clearData();ClientRulesWatch.close(this);model.revoke();confidential();}
            else if(op==CHECK_TARGETS||op==CHECK_INBOX){checkTargets=null;inbox=null;}
            message=op+": "+r.code()+" · "+r.message()+" Refresh to retry this read.";build();return;
        }
        if(op!=DM_QUICK&&r.data().has("campaign")&&!text(r.data(),"campaign").equals(model.campaign)){
            generation++;fresh=false;clearData();model.revoke();confidential();message="Campaign changed. Refresh to verify current access.";build();return;
        }
        watch(r.data());accept.accept(r.data());
    });}
    private void reload(){
        if(busy||mutations.pending())return;refreshPending=false;fresh=false;placementToken="";model.invalidateDiscovery();
        fetch(DM_QUICK,args("page",page),root->{if(!model.campaign.equals(text(root,"campaign"))){confidential();}model.campaign(text(root,"campaign"));context=root;
            switch(model.tab){
                case PARTY->{data=root;model.reconcile(array(data,"characters"),"character");String id=model.selections.getOrDefault(model.tab,"");if(id.isEmpty()){detail=null;loaded();}else fetch(QUICK_SHEET,args("character",id),s->{detail=s;loaded();});}
                case ACTORS->discover(this::loaded);
                case ENCOUNTERS->fetch(ENCOUNTER_LIST,args(),s->{encounters=s;discover(this::loaded);});
                case SCENE->discover(()->fetch(STAGE_LIST,args(),a->{stages=a;if(DmWorkspaceModel.find(array(a,"stages"),"id",selectedStage)==null)selectedStage="";loaded();}));
                case ROLLS->fetch(CHECK_TARGETS,args(),s->{checkTargets=s;model.targets.removeIf(id->DmWorkspaceModel.find(array(s,"characters"),"id",id)==null);model.recipients.removeIf(id->DmWorkspaceModel.find(array(s,"members"),"id",id)==null);fetch(CHECK_INBOX,args("page",inboxPage),a->{inbox=a;loaded();});});
            }
        });
    }
    private void discover(Runnable done){var query=model.discovery();data=null;fetch(ENCOUNTER_DISCOVER,query.args(),snapshot->{if(!model.applyDiscovery(query,snapshot)){message="Stale or mismatched discovery ignored. Refresh to verify the campaign and filters.";fresh=false;build();return;}data=snapshot;done.run();});}
    private void loaded(){fresh=true;message=model.selectionNotice.isEmpty()?"Campaign: "+text(context,"campaignName")+" · current authorized snapshot":model.selectionNotice;build();}
    private void switchTab(DmWorkspaceModel.Tab tab){if(mutations.pending()){message="Awaiting server confirmation; tabs remain available afterward.";return;}if(canvas!=null)model.scrolls.put(model.tab,canvas.position());canvas=null;generation++;busy=false;ClientRulesWatch.close(this);model.tab(tab);clearData();shell();reload();}
    private void discoveryFilter(String binding,String kind){if(mutations.pending())return;model.binding=binding;model.kind=kind;model.invalidateDiscovery();generation++;busy=false;fresh=false;placementToken="";confirmed=null;data=null;model.scrolls.put(model.tab,0);shell();reload();}
    private void shell(){
        if(canvas!=null)model.scrolls.put(model.tab,canvas.position());
        clearChildren();selects.clear();canvas=null;layout=DmWorkspaceModel.layout(width,height,compact,model.tab);var f=layout.frame();int x=Math.clamp(f.x()+dx,0,width-f.width()),sy=Math.clamp(f.y()+dy,0,height-f.height());frame=new UiLayout.Rect(x,sy,f.width(),f.height());dx=x-f.x();dy=sy-f.y();
        button("Refresh","",frame.x()+frame.width()-112,frame.y()+4,58,18,()->{if(!busy&&!mutations.pending()){generation++;reload();}}).hint("Read-only refresh; never repeats a gameplay action");
        button(compact?"+ Size":"− Size","",frame.x()+frame.width()-51,frame.y()+4,45,18,()->{compact=!compact;dx=dy=0;init();});
        int tw=(frame.width()-12)/5;for(var tab:DmWorkspaceModel.Tab.values())button(tab.name().substring(0,1)+tab.name().substring(1).toLowerCase(Locale.ROOT),"",frame.x()+6+tab.ordinal()*tw,frame.y()+26,tw-2,18,()->switchTab(tab)).selected(tab==model.tab);
        if(model.tab!=DmWorkspaceModel.Tab.ROLLS){
            int queryW=Math.max(60,frame.width()-122);var search=search(frame.x()+6,frame.y()+49,queryW,model.queries.getOrDefault(model.tab,""),value->{model.queries.put(model.tab,value);build();});search.setHeight(18);
            selectRoot("Sort",DmWorkspaceModel.nearby(model.tab)?options("NAME","Name","DISTANCE","Distance"):options("NAME","Name"),()->model.sort,v->{model.sort=v;build();},frame.x()+frame.width()-111,frame.y()+49,105);
        }
        if(DmWorkspaceModel.nearby(model.tab)){int cw=(frame.width()-20)/3;
            selectRoot("Show",options("BOUND","Bound","UNBOUND","Unbound","ALL","All"),()->model.binding,v->discoveryFilter(v,model.kind),frame.x()+6,frame.y()+69,cw);
            selectRoot("Type",options("ALL","All","PLAYER","Players","NPC","NPCs"),()->model.kind,v->discoveryFilter(model.binding,v),frame.x()+10+cw,frame.y()+69,cw);
            selectRoot("State",options("ALL","All","READY","Ready","BLOCKED","Blocked"),()->model.eligibility,v->{model.eligibility=v;build();},frame.x()+14+2*cw,frame.y()+69,cw);
        }
        button(ClientSrdCombat.hudEncounter()!=null?"Battlefield":"Back","",frame.x()+6,frame.y()+frame.height()-30,90,18,()->{if(ClientSrdCombat.hudEncounter()!=null)returnBattlefield();else close();}).hint("Return without changing turn, camera or actor ownership");
        button("Full menu","",frame.x()+100,frame.y()+frame.height()-30,75,18,()->client.setScreen(new HubScreen(this)));
        build();
    }
    private void returnBattlefield(){if(parent instanceof SrdBattlefieldScreen)client.setScreen(parent);else ClientSrdCombat.battlefield();}
    private static List<WorkspaceSelect.Option> options(String... pairs){var values=new ArrayList<WorkspaceSelect.Option>();for(int i=0;i<pairs.length;i+=2)values.add(new WorkspaceSelect.Option(pairs[i],pairs[i+1]));return values;}
    private WorkspaceSelect selectRoot(String label,List<WorkspaceSelect.Option> values,Supplier<String> value,Consumer<String> choose,int x,int y,int w){var select=addDrawableChild(new WorkspaceSelect(x,y,w,label,values,value,choose,new UiLayout.Rect(frame.x()+4,frame.y()+26,frame.width()-8,frame.height()-62)));selects.add(select);return select;}
    private int w(){return canvas.getWidth()-16;}
    private void line(String text,int color){canvas.wrapped(text,8,y,w(),color);y+=Math.max(1,textRenderer.wrapLines(Text.literal(text),w()).size())*12+3;}
    private void title(String text){line(text,RpgTheme.GOLD);}
    private RpgButton action(String title,String hint,boolean enabled,Runnable run){var b=canvas.control("action:"+y,new RpgButton(8,y,w(),20,title,"",()->{if(available())run.run();}).hint(hint));b.active=enabled&&available();y+=23;return b;}
    private void field(String key,String label,String value,int limit,Consumer<String> changed){line(label,RpgTheme.MUTED);var f=new TextFieldWidget(textRenderer,8,y,w(),18,Text.literal(label));f.setMaxLength(limit);f.setText(value);f.setChangedListener(changed);canvas.control(key,f);y+=24;}
    private void choose(String key,String label,List<WorkspaceSelect.Option> values,Supplier<String> value,Consumer<String> selected){
        var select=new WorkspaceSelect(8,y,w(),label,values,value,v->{if(available()){selected.accept(v);build();}},new UiLayout.Rect(canvas.getX(),canvas.getY(),canvas.getWidth(),canvas.getHeight()));
        select.active=available();canvas.control(key,select);y+=23;
    }

    private void confirm(String title,String detail,Runnable run){confirmedTitle=title;confirmedText=detail;confirmed=run;build();}
    private void mutate(UiApiService.Operation op,JsonObject payload,Consumer<JsonObject> done){
        if(!available())return;fresh=false;busy=true;String campaign=model.campaign;var ticket=mutations.begin(campaign);if(ticket==null)return;
        // A resize changes layout generation, not the identity of an explicit pending operation.
        UiRequestQueue.enqueue(op,payload,()->client!=null&&client.currentScreen==this&&mutations.matches(ticket,model.campaign),reply->{
            if(!mutations.complete(ticket,model.campaign))return;busy=false;
            if(!reply.code().equals("OK")){confirmed=null;placementToken="";model.invalidateDiscovery();
                if(reply.code().equals("FORBIDDEN")){clearData();ClientRulesWatch.close(this);model.revoke();confidential();}
                else if(model.tab==DmWorkspaceModel.Tab.ROLLS){checkTargets=inbox=null;}
                message=op+": "+reply.code()+" · "+reply.message()+" Refresh to reconcile; no mutation is repeated automatically.";fresh=false;build();return;
            }
            if(reply.data().has("campaign")&&!text(reply.data(),"campaign").equals(campaign)){clearData();model.revoke();confidential();message="Campaign changed. Refresh to verify access.";build();return;}
            fresh=true;message=reply.message();try{done.accept(reply.data());if(client.currentScreen==this&&context==null&&!busy)reload();}
            catch(RuntimeException invalid){fresh=false;confirmed=null;placementToken="";message=op+": Invalid server response. Refresh to reconcile; no operation is repeated.";build();}
        });
    }
    private void build(){
        if(frame==null)return;for(var e:lists.entrySet())listPositions.put(e.getKey(),new int[]{e.getValue().firstVisible(),e.getValue().selectedIndex()});lists.clear();if(inboxList!=null){inboxFirst=inboxList.firstVisible();inboxSelected=inboxList.selectedIndex();inboxList=null;}boolean ownFocus=canvas!=null&&getFocused()==canvas;String focusKey=canvas==null?"":canvas.focusedKey();var cursors=canvas==null?Map.<String,Integer>of():canvas.cursors();if(ownFocus)setFocused(null);int offset=canvas==null?model.scrolls.getOrDefault(model.tab,0):canvas.position();if(canvas!=null)remove(canvas);
        manualRoll=stageSave=null;canvas=new CharacterCanvas(new UiLayout.Rect(frame.x()+6,frame.y()+layout.body().y()-layout.frame().y(),frame.width()-12,layout.body().height()));addDrawableChild(canvas); // selectors render above this clipped content
        for(var select:selects){remove(select);addDrawableChild(select);}y=4;
        if(context==null){line(busy?"Loading authorized campaign context…":"Refresh to verify selected campaign manager access.",RpgTheme.MUTED);return;}
        if(confirmed!=null){title(confirmedTitle);line(confirmedText,RpgTheme.TEXT);action("Confirm","Server revalidates authority and current state",true,()->{var run=confirmed;confirmed=null;run.run();});action("Cancel","No operation is sent",true,()->{confirmed=null;build();});canvas.position(0);if(ownFocus)setFocused(canvas);return;}
        canvas.boldLabel(DmWorkspaceModel.title(model.tab),8,y,w(),RpgTheme.GOLD);y+=15;
        canvas.label(DmWorkspaceModel.description(model.tab),8,y,w(),RpgTheme.MUTED);y+=18;
        switch(model.tab){case PARTY->party();case ACTORS->actors();case ENCOUNTERS->encounters();case SCENE->scene();case ROLLS->rolls();}
        canvas.position(offset);canvas.cursors(cursors);if(ownFocus){setFocused(canvas);canvas.focus(focusKey);}
    }
    private record Command(String title,String hint,boolean enabled,Runnable run) {}
    private Command command(String title,String hint,boolean enabled,Runnable run){return new Command(title,hint,enabled,run);}
    private void commands(Command... values){int columns=w()<360?2:Math.min(3,values.length),cw=(w()-4*(columns-1))/columns;for(int i=0;i<values.length;i++){var c=values[i];int row=i/columns,col=i%columns;var b=canvas.control("cmd:"+y+":"+i,new RpgButton(8+col*(cw+4),y+row*22,cw,19,c.title(),"",()->{if(available())c.run().run();}).hint(c.hint()));b.active=c.enabled()&&available();}y+=((values.length+columns-1)/columns)*22+2;}
    private void roster(String id,String name,String secondary,String status,String glyph,String hint,boolean selected,boolean checked,boolean showToggle,boolean eligible,Runnable inspect,Runnable toggle){
        var row=canvas.control("row:"+id,new WorkspaceRosterRow(8,y,w(),name,secondary,status,glyph,hint,selected,checked,showToggle,eligible,()->{if(available())inspect.run();},()->{if(available())toggle.run();}));row.active=available();y+=WorkspaceRosterRow.HEIGHT;
    }
    private void participantSummary(){
        line("Selected "+model.participants.size()+" / 16 · "+String.join(", ",model.participants.stream().map(model::participantName).toList()),RpgTheme.TEXT);
        if(!model.selectedPresent(data)&&!model.participants.isEmpty())line("Some selected actors are outside this snapshot. Return to Bound / All and refresh before Start or placement.",RpgTheme.MUTED);
        if(!model.selectionNotice.isEmpty())line(model.selectionNotice,RpgTheme.MUTED);
    }
    private void party(){
        if(data==null){line("Loading party…",RpgTheme.MUTED);return;}
        if(detail!=null){title("Selected · "+text(detail,"name"));var rules=detail.getAsJsonObject("rules");if(rules!=null&&flag(rules,"valid"))line("HP "+text(rules,"hp")+" / "+text(rules,"maximumHp")+" · temp "+text(rules,"temporaryHp")+" · AC "+text(rules,"ac")+" · level "+text(rules,"effectiveLevel"),RpgTheme.TEXT);
            action("Full Character Sheet","Advanced character details and administration",true,()->client.setScreen(PlayerNavigation.sheet(this,new DashboardSource(false,text(detail,"id")))));
            action("Abilities, Skills & Saves","Server-calculated check options",true,()->client.setScreen(new CharacterRollMenuScreen(this,detail)));
            if(!flag(detail,"approved"))action("Approve character…","Only unbound records can be approved; no ownership change",text(detail,"bound").isEmpty(),()->{var a=args("character",text(detail,"id"),"revision",revision(detail));confirm("Approve "+text(detail,"name")+"?","Server approval and revision rules remain in force.",()->mutate(CHARACTER_APPROVE,a,r->reload()));});
        }
        title("Campaign roster · page "+(page+1));var rows=model.filter(array(data,"characters"),"character");if(rows.isEmpty())line("No matching campaign records on this page.",RpgTheme.MUTED);
        for(var c:rows){String id=text(c,"character");String status=(flag(c,"approved")?"Approved":"Review")+" · "+(flag(c,"bound")?"Bound":"Unbound");
            roster("party:"+id,text(c,"name"),text(c,"description")+" · "+text(c,"health")+(c.has("ac")?" · AC "+text(c,"ac"):""),status,text(c,"kind").equals("NPC")?"N":"P",status+" · "+text(c,"controllerName")+" · "+text(c,"conditions"),model.selections.getOrDefault(model.tab,"").equals(id),false,false,false,()->{model.selections.put(model.tab,id);detail=null;generation++;fresh=false;fetch(QUICK_SHEET,args("character",id),snapshot->{detail=snapshot;fresh=true;build();});},()->{});
        }
        commands(command("Previous page","",page>0,()->{page--;detail=null;reload();}),command("Next page","",flag(data,"hasMore"),()->{page++;detail=null;reload();}),command("Manage roster","Assignments, approval and binding",true,()->client.setScreen(new CampaignRosterScreen(this,text(context,"campaign")))));
    }
    private void toggleParticipant(JsonObject actor){model.toggle(actor);build();}
    private void start(){if(data==null||!model.startable(data)||!available())return;var a=args("discovery",model.discoveryToken);var ids=new JsonArray();model.participants.forEach(ids::add);a.add("actors",ids);
        confirm("Start controlled encounter?",String.join(", ",model.participants.stream().map(model::participantName).toList())+". Server rechecks bindings, location, token and campaign revision.",()->{if(data==null||!model.startable(data)||!model.discoveryToken.equals(text(a,"discovery"))){message="Selection changed. Refresh and review again.";build();return;}mutate(ENCOUNTER_START,a,r->{model.clearParticipants();model.invalidateDiscovery();client.setScreen(SrdCombatScreen.entry(this));});});}
    private void actorDetails(){var actor=DmWorkspaceModel.find(array(data,"actors"),"actor",model.selections.getOrDefault(model.tab,""));if(actor==null)return;
        String id=text(actor,"actor");title("Selected · "+text(actor,"name"));line(text(actor,"type")+" · "+text(actor,"position")+" · "+(flag(actor,"bound")?"Bound: "+text(actor,"characterName"):"Unbound"),RpgTheme.TEXT);line(text(actor,"reason"),flag(actor,"eligible")?RpgTheme.GOLD:RpgTheme.MUTED);
        if(actor.has("monsterSupport"))canvas.tooltip(8,y-15,w(),15,text(actor,"monsterSupport"));
        commands(command(model.participants.contains(id)?"Remove selection":"Add to encounter",text(actor,"reason"),flag(actor,"eligible")||model.participants.contains(id),()->toggleParticipant(actor)),command("Bind character…","Requires an unbound supported nearby NPC; existing bindings stay intact",flag(actor,"canBind"),()->client.setScreen(new ActorBindingScreen(this,id))),command("Prepare creature…","Review supported SRD monster before creation/binding",flag(actor,"canBind"),()->client.setScreen(new MonsterPreparationScreen(this,id))));
    }
    private void actorRows(){
        var rows=model.filter(array(data,"actors"),"actor");
        if(rows.isEmpty())line(DmWorkspaceModel.empty(model.binding,!array(data,"actors").isEmpty()),RpgTheme.MUTED);
        for(var actor:rows){String id=text(actor,"actor");String status=flag(actor,"eligible")?"Ready":"Blocked";
            roster(id,text(actor,"name"),(flag(actor,"bound")?text(actor,"characterName"):"Unbound "+text(actor,"kind"))+" · "+(flag(actor,"eligible")?"Ready":text(actor,"reason")),text(actor,"distance")+"m · "+status,text(actor,"kind").equals("Player")?"P":"N",text(actor,"type")+" · "+text(actor,"position")+" · "+text(actor,"reason"),model.selections.getOrDefault(model.tab,"").equals(id),model.participants.contains(id),true,flag(actor,"eligible"),()->{model.selections.put(model.tab,id);build();},()->toggleParticipant(actor));if(model.selections.getOrDefault(model.tab,"").equals(id))actorDetails();}
    }
    private void discoverySummary(){long ready=array(data,"actors").asList().stream().filter(a->flag(a.getAsJsonObject(),"eligible")).count();line(model.binding+" nearby: "+array(data,"actors").size()+" · ready: "+ready+" · "+text(data,"radius")+"-block snapshot · selected "+model.participants.size()+" / 16",RpgTheme.TEXT);if(flag(data,"truncated"))line("Nearby actor list limited; move closer or adjust filters and refresh.",RpgTheme.MUTED);}
    private void actors(){if(data==null){line(busy?"Loading nearby actors…":"Discovery unavailable. Refresh to retry.",RpgTheme.MUTED);return;}discoverySummary();
        commands(command("Assemble encounter","Selection does not start combat",true,()->switchTab(DmWorkspaceModel.Tab.ENCOUNTERS)),command("Find unbound NPCs","Fresh Unbound discovery",true,()->discoveryFilter("UNBOUND","ALL")));
        actorRows();
    }
    private void encounters(){
        if(encounters==null||data==null){line(busy?"Loading encounter and participant snapshot…":"Encounter data unavailable. Refresh to retry.",RpgTheme.MUTED);return;}
        var live=ClientSrdCombat.workspaceState();
        if(text(live,"campaign").equals(text(context,"campaign"))){var actor=SrdBattlefieldModel.actor(array(live,"actors"),text(live,"active"));
            title(text(live,"state")+" · round "+text(live,"round")+" · "+(actor==null?"Initiative pending":text(actor,"name")));
            line(array(live,"actors").size()+" participants · movement "+text(live,"remainingFeet")+" ft · action "+(flag(live,"actionUsed")?"spent/reserved":"available"),RpgTheme.TEXT);
            var ordered=SrdBattlefieldPresentation.initiative(live);for(int i=0;i<ordered.size();i++){var a=ordered.get(i);canvas.label((text(live,"active").equals(text(a,"id"))?"▶ ":"")+text(a,"name")+" · initiative "+text(a,"initiative")+" · "+SrdBattlefieldModel.hp(a),8,y,w(),RpgTheme.TEXT);y+=14;}
            if(text(live,"state").equals("PAUSED"))line(text(live,"reason"),RpgTheme.GOLD);
            commands(command("Battlefield","Return without changing combat",true,this::returnBattlefield),command((live.has("recovery")&&flag(live.getAsJsonObject("recovery"),"canRetry")?"Saved attack review…":"Heal / adjudicate…"),"K-C: explicit paused adjudication; healing never auto-resumes",text(live,"state").equals("PAUSED")&&flag(live,"manager"),()->client.setScreen(new SrdRecoveryScreen(this))));
        }
        title("Existing encounters");if(array(encounters,"encounters").isEmpty())line("No controlled encounters in the selected campaign.",RpgTheme.MUTED);
        for(var value:array(encounters,"encounters")){var e=value.getAsJsonObject();String id=text(e,"encounter");roster("encounter:"+id,text(e,"state"),text(e,"actors"),"Select to manage","E",text(e,"reason"),selectedEncounter.equals(id),false,false,false,()->{selectedEncounter=id;build();},()->{});}
        var selected=DmWorkspaceModel.find(array(encounters,"encounters"),"encounter",selectedEncounter);
        if(selected!=null){line(text(selected,"reason"),RpgTheme.MUTED);String id=text(selected,"encounter");commands(command("Watch encounter","Read-only; no initiative advance",true,()->fetch(ENCOUNTER_WATCH,args("encounter",id),r->{fresh=true;build();})),command("End encounter…","Explicit confirmation; committed resources stay saved",true,()->confirm("End controlled encounter?",text(selected,"actors")+". Committed health remains saved; unresolved transactions still require review.",()->mutate(ENCOUNTER_END,args("encounter",id),r->reload()))));}
        title("Start new encounter");discoverySummary();participantSummary();
        commands(command("Start Encounter…","Needs 2–16 ready actors in current discovery",model.startable(data),this::start),command("Prepare creature","Actors: bind or prepare unbound creatures",true,()->switchTab(DmWorkspaceModel.Tab.ACTORS)),command("Transaction review","Existing authorized J2 recovery",true,()->client.setScreen(new GameplayRecoveryScreen(this))));
        actorRows();
    }
    private void scene(){
        if(data==null||stages==null){line(busy?"Loading scene snapshot…":"Scene data unavailable. Refresh to retry.",RpgTheme.MUTED);return;}
        discoverySummary();int size=Math.min(190,w());var rows=model.filter(array(data,"actors"),"actor");
        var map=canvas.control("scene-map",new WorkspaceSceneMap(8,y,size,data.get("originX").getAsDouble(),data.get("originZ").getAsDouble(),rows,model.participants,model.selections.getOrDefault(model.tab,""),id->{if(available()){model.selections.put(model.tab,id);build();}}));map.active=available();
        if(w()>size+130){canvas.label("Gold: selected participant",size+16,y+12,w()-size-10,RpgTheme.GOLD);canvas.label("Numbers: actor roster below",size+16,y+28,w()-size-10,RpgTheme.TEXT);canvas.label("+ Discovery origin · North up",size+16,y+44,w()-size-10,RpgTheme.MUTED);canvas.label(text(data,"location"),size+16,y+60,w()-size-10,RpgTheme.MUTED);}y+=size+6;
        participantSummary();line("Selection order maps to saved slots; sorting never changes slot order.",RpgTheme.MUTED);
        actorRows();
        title("Saved scenes · placement templates");line("Save/capture records positions. Validate previews; Activate explicitly moves. Start Combat is separate.",RpgTheme.MUTED);
        field("stageName","New scene name",stageName,64,value->stageName=value);stageSave=action("Save Scene here","Save an empty template; no movement or combat",!stageName.isBlank(),()->mutate(STAGE_CREATE,args("stage",stageOperation,"name",stageName,"description",""),r->{stageOperation=UUID.randomUUID();reload();}));
        if(array(stages,"stages").isEmpty())line("No saved stage templates in this campaign.",RpgTheme.MUTED);
        for(var value:array(stages,"stages")){var stage=value.getAsJsonObject();String id=text(stage,"id");roster("stage:"+id,text(stage,"name"),array(stage,"slots").size()+" slots · "+text(stage,"dimension"),"Saved scene","S",text(stage,"description"),selectedStage.equals(id),false,false,false,()->{selectedStage=id;placementToken="";build();},()->{});}
        var stage=DmWorkspaceModel.find(array(stages,"stages"),"id",selectedStage);if(stage!=null){
            title(text(stage,"name"));int slot=0;for(var value:array(stage,"slots")){var entry=value.getAsJsonObject();canvas.label("Slot "+(++slot)+" · "+text(entry,"role")+" · "+text(entry,"x")+", "+text(entry,"y")+", "+text(entry,"z"),8,y,w(),RpgTheme.TEXT);y+=14;}
            var ids=new JsonArray();model.participants.forEach(ids::add);String id=text(stage,"id");var payload=args("stage",id,"revision",stage.get("revision").getAsLong(),"actors",ids);boolean current=model.selectedPresent(data);
            commands(command("Capture selection…","Replaces saved slots; no movement",current&&!ids.isEmpty(),()->confirm("Capture current placements?",text(stage,"name")+" · "+String.join(", ",model.participants.stream().map(model::participantName).toList())+" in selection order. No actor moves.",()->mutate(STAGE_CAPTURE,payload,r->reload()))),command("Validate placements","Read-only validation; no movement",current&&!ids.isEmpty()&&ids.size()==array(stage,"slots").size(),()->mutate(STAGE_VALIDATE,payload,r->{placementToken=text(r,"token");confirm("Activate Saved Placements?","Move "+text(r,"count")+" actors to the reviewed slots in selected order. Server revalidates bounds, safe terrain and bindings. This does not start combat.",()->mutate(STAGE_ACTIVATE,args("token",placementToken),done->{placementToken="";reload();}));})),command("Edit scene…","Existing revision-safe stage editor",true,()->client.setScreen(new EncounterStagesScreen(this,id))));
            commands(command("Delete scene…","Deletes template only",true,()->confirm("Delete "+text(stage,"name")+"?","No character or entity will be deleted.",()->mutate(STAGE_DELETE,args("stage",id,"revision",stage.get("revision").getAsLong()),r->reload()))),command("Combat management","Start requires separate explicit confirmation",true,()->switchTab(DmWorkspaceModel.Tab.ENCOUNTERS)));
        }
    }
    private void rolls(){
        if(checkTargets==null||inbox==null){line(busy?"Loading authorized targets and inbox…":"Roll data unavailable. Refresh above; no check executes.",RpgTheme.MUTED);return;}
        commands(command("Request Player Check","Online bound player controllers",true,()->rollWorkflow("REQUEST")),command("DM Character Roll","Prepare then execute an approved character check",true,()->rollWorkflow("ADJUDICATED")),command("Manual Dice","Existing nearby/encounter audience",true,()->rollWorkflow("MANUAL")));
        title(workflow.equals("MANUAL")?"Manual Dice":workflow.equals("REQUEST")?"Request Player Check":"DM Character Roll");
        if(workflow.equals("MANUAL")){manual();}else{
            var keys=new ArrayList<WorkspaceSelect.Option>();for(var key:CharacterChecks.keys())if(key.category().name().equals(category))keys.add(new WorkspaceSelect.Option(key.id(),DmWorkspaceModel.checkLabel(key.id())));
            choosePair("category","Category",options("ABILITY","Ability","SKILL","Skill","SAVE","Saving throw"),()->category,v->{category=v;check=CharacterChecks.keys().stream().filter(k->k.category().name().equals(v)).findFirst().orElseThrow().id();},"check","Check",keys,()->check,v->check=v);
            choose("mode","Roll mode",options("NORMAL","Normal","ADVANTAGE","Advantage","DISADVANTAGE","Disadvantage"),()->mode,v->mode=v);
            field("purpose","Purpose",purpose,80,v->purpose=v);
            action((rollAdvanced?"−":"+")+" Audience / hidden DC · "+visibility+(dc.isBlank()?"":" · DC set"),"These settings apply even when collapsed; DC/outcome remain DM-only",true,()->{rollAdvanced=!rollAdvanced;build();});
            if(rollAdvanced){choose("visibility","Audience",options("DM_ONLY","DM only","CAMPAIGN","Campaign","RECIPIENTS","Specific recipients"),()->visibility,v->visibility=v);field("dc","Hidden DC (optional; DM only)",dc,3,v->dc=v);
                if(visibility.equals("RECIPIENTS")){for(var value:array(checkTargets,"members")){var member=value.getAsJsonObject();String id=text(member,"id");roster("member:"+id,text(member,"name"),"Explicit result recipient",model.recipients.contains(id)?"Selected":"","P","Current DMs also receive results",false,model.recipients.contains(id),true,true,()->toggleRecipient(id),()->toggleRecipient(id));}}
            }
            title(workflow.equals("REQUEST")?"Online bound targets · selected "+model.targets.size()+" / 16":"Approved target · choose one");
            if(array(checkTargets,"characters").size()>8)field("targetQuery","Find character",rollQuery,80,value->{rollQuery=value;build();});
            var choices=new ArrayList<HubState.Choice>();int eligible=0;for(var target:array(checkTargets,"characters")){var c=target.getAsJsonObject();boolean ready=!workflow.equals("REQUEST")||flag(c,"requestEligible");if(!ready)continue;eligible++;String id=text(c,"id");if(!text(c,"name").toLowerCase(Locale.ROOT).contains(rollQuery.toLowerCase(Locale.ROOT)))continue;
                choices.add(new HubState.Choice(id,(model.targets.contains(id)?"✓ ":"□ ")+text(c,"name"),workflow.equals("REQUEST")?"Online bound controller":"Server-authorized character",true,"Select target; no check executes",0));
            }
            if(eligible==0)line(workflow.equals("REQUEST")?"No eligible online bound player controllers. Use DM Character Roll for supported NPC adjudication.":"No eligible approved character checks in this campaign.",RpgTheme.MUTED);
            else choiceList("targets",choices,3,choice->toggleTarget(choice.id()));
            commands(command(workflow.equals("REQUEST")?"Send Request…":"Prepare DM Roll","Server validates current source/revision and chosen audience",!model.targets.isEmpty()&&operation.isEmpty(),this::sendCheck),command("Roll / Retrieve","Execute or retrieve the SAME prepared operation",!operation.isEmpty(),()->execute(operation)));
            if(!operation.isEmpty()){line("Saved preparation: "+preparedLabel+". Roll / Retrieve uses this same operation, regardless of form edits. Ten-minute session ticket.",RpgTheme.GOLD);action("Forget local reference","Does not cancel or reroll the server operation; inbox stays available",true,()->{operation="";preparedLabel="";build();});}
        }
        rollInbox();
        commands(command("Advanced checks…","Existing grouped/adjudication screen",true,()->client.setScreen(new DmCheckScreen(this))),command("Dice settings…","Local animation, audio and accessibility",true,()->client.setScreen(new DicePresentationScreen(this))));
    }
    private void rollWorkflow(String next){if(!workflow.equals(next)){workflow=next;model.targets.clear();}build();}
    private void toggleTarget(String id){if(!model.targets.remove(id)){if(workflow.equals("ADJUDICATED"))model.targets.clear();if(model.targets.size()<16)model.targets.add(id);}build();}
    private void toggleRecipient(String id){if(!model.recipients.remove(id)&&model.recipients.size()<16)model.recipients.add(id);build();}
    private void choiceList(String key,List<HubState.Choice> rows,int capacity,Consumer<HubState.Choice> selected){
        if(rows.isEmpty()){line("No matching entries.",RpgTheme.MUTED);return;}
        int height=Math.min(capacity,rows.size())*34;var list=new RpgList(8,y,w(),height,34,choice->{if(available())selected.accept(choice);});list.rows(rows);var position=listPositions.getOrDefault(key,new int[]{0,0});list.restorePosition(position[0],position[1]);canvas.control(key,list);lists.put(key,list);y+=height+5;
    }
    private void rollInbox(){
        title("Requests & results · page "+(inboxPage+1));line("10-minute session records. Inbox refresh never rolls or replays dice.",RpgTheme.MUTED);
        var choices=new ArrayList<HubState.Choice>();
        for(var value:array(inbox,"rolls")){var view=value.getAsJsonObject();var ticket=view.getAsJsonObject("ticket");var spec=ticket.getAsJsonObject("spec");var actor=DmWorkspaceModel.find(array(checkTargets,"characters"),"id",text(ticket,"character"));
            String name=(actor==null?"Character":text(actor,"name"))+" · "+text(spec.getAsJsonObject("check"),"category")+" "+DmWorkspaceModel.checkLabel(text(spec.getAsJsonObject("check"),"id"))+" · "+text(view,"status");
            String summary=view.has("result")&&!view.get("result").isJsonNull()?DmWorkspaceModel.roll(view.getAsJsonObject("result")):text(spec,"purpose")+" · "+text(spec,"visibility");choices.add(new HubState.Choice(text(ticket,"id"),name,summary,true,"Select to inspect; no roll executes",0));
        }
        if(choices.isEmpty())line("No authorized requests or results on this page.",RpgTheme.MUTED);
        else{int h=Math.min(4,choices.size())*34;var list=new RpgList(8,y,w(),h,34,choice->{if(available()){selectedRoll=choice.id();build();}});list.rows(choices);list.restorePosition(inboxFirst,inboxSelected);canvas.control("inbox",list);inboxList=list;y+=h+5;}
        var selected=array(inbox,"rolls").asList().stream().map(JsonElement::getAsJsonObject).filter(v->text(v.getAsJsonObject("ticket"),"id").equals(selectedRoll)).findFirst().orElse(null);
        if(selected!=null){var ticket=selected.getAsJsonObject("ticket");line(text(ticket.getAsJsonObject("spec"),"purpose")+" · "+text(selected,"status"),RpgTheme.GOLD);
            if(selected.has("result")&&!selected.get("result").isJsonNull())line(DmWorkspaceModel.roll(selected.getAsJsonObject("result")),RpgTheme.TEXT);
            else if(text(selected,"status").equals("PENDING")){line("Awaiting "+DmWorkspaceModel.member(array(checkTargets,"members"),text(ticket,"executor")),RpgTheme.MUTED);commands(command("Roll requested check","Same authorized request; no new preparation",client.player!=null&&text(ticket,"executor").equals(client.player.getUuidAsString()),()->execute(text(ticket,"id"))),command("Dismiss request…","Terminal dismissal; no roll",true,()->confirm("Dismiss this pending request?","This request will not roll and cannot be reopened.",()->mutate(CHECK_DISMISS,args("operation",text(ticket,"id")),r->reload()))));}
            else line("No authorized result data. Restricted, expired or dismissed rolls are never guessed.",RpgTheme.MUTED);
        }
        commands(command("Previous results","Read-only",inboxPage>0,()->{inboxPage--;selectedRoll="";inboxFirst=inboxSelected=0;reload();}),command("Next results","Read-only",flag(inbox,"hasMore"),()->{inboxPage++;selectedRoll="";inboxFirst=inboxSelected=0;reload();}));
    }
    private void choosePair(String key,String label,List<WorkspaceSelect.Option> options,Supplier<String> current,Consumer<String> changed,String key2,String label2,List<WorkspaceSelect.Option> options2,Supplier<String> current2,Consumer<String> changed2){
        if(w()<360){choose(key,label,options,current,changed);choose(key2,label2,options2,current2,changed2);return;}
        int half=(w()-4)/2;var bounds=new UiLayout.Rect(canvas.getX(),canvas.getY(),canvas.getWidth(),canvas.getHeight());
        var first=new WorkspaceSelect(8,y,half,label,options,current,value->{if(available()){changed.accept(value);build();}},bounds);first.active=available();canvas.control(key,first);
        var second=new WorkspaceSelect(12+half,y,half,label2,options2,current2,value->{if(available()){changed2.accept(value);build();}},bounds);second.active=available();canvas.control(key2,second);y+=23;
    }
    private void sendCheck(){
        try{Integer threshold=dc.isBlank()?null:Integer.valueOf(dc);var recipients=new JsonArray();if(visibility.equals("RECIPIENTS"))model.recipients.forEach(recipients::add);var spec=args("category",category,"key",check,"mode",mode,"purpose",purpose,"visibility",visibility,"recipients",recipients,"dc",threshold);
            if(workflow.equals("REQUEST")){var ids=new JsonArray();model.targets.forEach(ids::add);confirm("Send player check request?",model.targets.size()+" online bound character(s) · "+category+" "+DmWorkspaceModel.checkLabel(check)+" · "+visibility+". Identical requests reuse their group for 10 minutes.",()->mutate(CHECK_REQUEST,args("characters",ids,"spec",spec),r->reload()));}
            else{var c=DmWorkspaceModel.find(array(checkTargets,"characters"),"id",model.targets.iterator().next());if(c==null){message="Target unavailable; refresh first.";return;}mutate(CHECK_PREPARE,args("character",text(c,"id"),"revision",revision(c),"spec",spec,"adjudicated",true),r->{operation=text(r,"operation");preparedLabel=text(spec,"category")+" "+DmWorkspaceModel.checkLabel(text(spec,"key"))+" · "+text(spec,"mode")+" · "+text(spec,"visibility");message="Prepared. Choose Roll / retrieve to execute this same operation.";build();});}
        }catch(NumberFormatException invalid){message="Enter a whole hidden DC or leave it blank.";}
    }
    private void execute(String id){mutate(CHECK_EXECUTE,args("operation",id),r->{if(r.has("result")&&!r.get("result").isJsonNull()){DiceAnimation.enqueueCheck(r.getAsJsonObject("result"));message=DmWorkspaceModel.roll(r.getAsJsonObject("result"));}else message="Accepted; result restricted to authorized recipients.";if(operation.equals(id)){operation="";preparedLabel="";}reload();});}
    private void manual(){
        choose("die","Manual die",Arrays.stream(DiceType.values()).map(d->new WorkspaceSelect.Option(d.name(),d.label)).toList(),()->die,v->die=v);
        choose("quantity","Quantity",java.util.stream.IntStream.rangeClosed(1,10).mapToObj(i->new WorkspaceSelect.Option(""+i,""+i)).toList(),()->quantity,v->quantity=v);
        if(die.equals("D20")&&quantity.equals("1"))choose("manualMode","Manual D20 mode",options("NORMAL","Normal","ADVANTAGE","Advantage","DISADVANTAGE","Disadvantage"),()->manualMode,v->manualMode=v);
        field("modifier","Manual modifier (not an authenticated character bonus)",modifier,4,s->modifier=s);
        manualRoll=action("Roll manual dice","Nearby/encounter audience from existing DiceService; no character authority",ticks>=manualCooldown,()->{try{int n=Integer.parseInt(modifier);if(n< -999||n>999)throw new NumberFormatException();if(!ClientPlayNetworking.canSend(DiceRequestPayload.ID)){message="Server dice unavailable";return;}ClientPlayNetworking.send(new DiceRequestPayload("DM manual dice",DiceType.valueOf(die),Integer.parseInt(quantity),n,die.equals("D20")&&quantity.equals("1")?(manualMode.equals("ADVANTAGE")?1:manualMode.equals("DISADVANTAGE")?2:0):0));manualCooldown=ticks+20;message="Requested server manual roll";build();}catch(NumberFormatException bad){message="Manual modifier must be -999 to 999.";}});canvas.liveLabel(()->"Manual result: "+DiceToolbar.result(),8,y,w(),RpgTheme.TEXT);y+=20;line("Manual rolls use the existing nearby/encounter audience; they are not secret DM character checks.",RpgTheme.MUTED);
    }
    @Override protected void background(DrawContext g){RpgTheme.panel(g,frame.x(),frame.y(),frame.width(),frame.height(),0xDD191D23,false);RpgTheme.text(g,textRenderer,"DM · Campaign: "+(context==null?(busy?"loading…":"unavailable"):text(context,"campaignName")),frame.x()+8,frame.y()+8,frame.width()-126,RpgTheme.GOLD);}
    @Override public void tick(){super.tick();ticks++;if(data!=null&&data.has("dimension")&&client.world!=null&&!text(data,"dimension").equals(client.world.getRegistryKey().getValue().toString())){data=null;model.invalidateDiscovery();model.clearParticipants();confirmed=null;fresh=false;message="World changed. Refresh discovery before selecting actors.";build();}if(stageSave!=null)stageSave.active=available()&&!stageName.isBlank();if(manualRoll!=null)manualRoll.active=available()&&ticks>=manualCooldown;if(refreshPending&&!busy&&!mutations.pending()&&ticks>=refreshAt){refreshAt=ticks+20;reload();}else if(model.tab==DmWorkspaceModel.Tab.ENCOUNTERS&&fresh&&!busy&&!mutations.pending()&&confirmed==null&&ticks%20==0){String stamp=ClientSrdCombat.workspaceState().toString();if(!stamp.equals(liveStamp)){liveStamp=stamp;build();}}else if(model.tab==DmWorkspaceModel.Tab.ROLLS&&fresh&&!busy&&!mutations.pending()&&confirmed==null&&ticks%100==0&&(canvas==null||!canvas.editingText()))fetch(CHECK_INBOX,args("page",inboxPage),s->{inbox=s;fresh=true;build();});}
    @Override public boolean mouseClicked(double x,double y,int button){for(var s:selects)if(s.isOpen()){if(s.isMouseOver(x,y)){setFocused(s);return s.mouseClicked(x,y,button);}s.dismiss();return true;}if(button==0&&x>=frame.x()&&x<frame.x()+frame.width()-116&&y>=frame.y()&&y<frame.y()+24){dragging=true;return true;}return super.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double mx,double my){if(dragging&&button==0){dx+=(int)mx;dy+=(int)my;shell();return true;}return super.mouseDragged(x,y,button,mx,my);}
    @Override public boolean mouseReleased(double x,double y,int button){dragging=false;return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){for(var s:selects)if(s.isOpen())return s.mouseScrolled(x,y,horizontal,vertical);return super.mouseScrolled(x,y,horizontal,vertical);}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(canvas!=null&&getFocused()==canvas&&canvas.editingText()){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){canvas.setFocused(false);setFocused(null);return true;}return canvas.keyPressed(key,scan,modifiers);}if(TacticalCombatClient.DM_QUICK_KEY.matchesKey(key,scan)&&!(getFocused() instanceof TextFieldWidget)){close();return true;}for(var s:selects)if(s.isOpen()&&key==org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE){s.dismiss();return true;}return super.keyPressed(key,scan,modifiers);}
    @Override public void removed(){mutations.invalidate();busy=false;ClientRulesWatch.close(this);fresh=false;clearData();super.removed();}
}
