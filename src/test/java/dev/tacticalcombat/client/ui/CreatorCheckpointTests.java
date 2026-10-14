package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.uiapi.*;
import java.util.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

public final class CreatorCheckpointTests {
    private static int count;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);count++;}
    public static void run()throws Exception {
        check(CreatorFlow.Step.values().length==9,"Nine focused creator sections");
        check(Arrays.stream(CreatorFlow.Step.values()).noneMatch(s->s.title.contains("Method")),"No method stage in presentation");
        for(int[] size:List.of(new int[]{320,180},new int[]{480,257},new int[]{640,360},new int[]{854,480},new int[]{1280,720},new int[]{1920,1080})) {
            var frame=UiLayout.frame(size[0],size[1],720);var l=CreatorFlow.layout(frame);
            check(l.navigation().fits(size[0],size[1])&&l.content().fits(size[0],size[1]),"Creator regions fit "+Arrays.toString(size));
            check(!l.navigation().overlaps(l.content()),"Separate navigation and content");
            if(l.summary().width()>0)check(l.summary().fits(size[0],size[1])&&!l.summary().overlaps(l.content()),"Summary region independent");
            for(var step:List.of(CreatorFlow.Step.IDENTITY,CreatorFlow.Step.CLASS))for(boolean sub:List.of(false,true)) {
                var c=CreatorFlow.catalogLayout(l.content(),step,sub);
                var rects=List.of(c.name(),c.subraces(),c.search(),c.grid(),c.details(),c.choose(),c.custom()).stream().filter(r->r.width()>0).toList();
                for(var r:rects)check(r.fits(size[0],size[1])&&r.y()+r.height()<=l.content().y()+l.content().height(),"Usable catalog region fits");
                for(int i=0;i<rects.size();i++)for(int j=i+1;j<rects.size();j++)check(!rects.get(i).overlaps(rects.get(j)),"Controls and descriptions never overlap");
            }
        }
        for(int[] size:List.of(new int[]{240,135},new int[]{320,180},new int[]{480,257},new int[]{854,480},new int[]{1280,720})){var frame=UiLayout.frame(size[0],size[1],980);var l=CharacterShell.layout(frame);var footer=new UiLayout.Rect(frame.x(),frame.y()+frame.height()-30,frame.width(),20);for(var r:List.of(l.rail(),l.content(),l.summary()))check(r.fits(size[0],size[1])&&!r.overlaps(footer),"Actual shell clears footer including scale-8-sized fallback");check(!l.rail().overlaps(l.content())&&!l.summary().overlaps(l.content()),"Rail, summary and workspace are separate");int available=l.content().width()-12;var scores=AbilityWorkspace.layout(available,l.content().height());check(scores.columns()==1&&scores.stride()>=28,"Six horizontal ability rows use safe spacing");if(size[1]>=360)check(scores.top()+5*scores.stride()+scores.stride()-4<=l.content().height(),"Six ability rows fit normal-height workspace");}
        check(CreatorFlow.recordedRace("Elf","High Elf").equals("Elf — High Elf"),"Conditional subrace preserves identity schema");
        check(CreatorFlow.baseRace("Elf — High Elf").equals("Elf")&&CreatorFlow.baseRace("High Elf").equals("Elf"),"Legacy and combined identities understood");
        check(CreatorFlow.subraces("Tiefling").isEmpty(),"No BG3 proprietary tiefling variants imported");
        boolean invalid=false;try{CreatorFlow.recordedRace("Elf","Drow");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"Non-SRD subrace cannot masquerade as supported choice");
        for(var c:Srd2014Catalog.CLASSES){check(CreatorIcons.REGIONS.containsKey(c.id()),"Class has actual icon "+c.id());check(CreatorLore.classDetails(c).contains("not applied automatically"),"Class guidance cannot imply auto-grants");}
        var manifest=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/tacticalcombat/textures/gui/creator/sprites.json"))).getAsJsonObject().getAsJsonObject("sprites");
        long visible=0;
        for(var e:CreatorIcons.REGIONS.entrySet()){
            var im=ImageIO.read(Path.of("src/main/resources/assets/tacticalcombat/textures/gui/creator/sprites/"+e.getKey()+".png").toFile());var r=e.getValue();
            check(r.width()==im.getWidth()&&r.height()==im.getHeight(),"Manifest uses actual texture dimensions "+e.getKey());
            boolean gutter=true,unchanged=true;var meta=manifest.getAsJsonObject(e.getKey());var bounds=meta.getAsJsonArray("sourceBounds");var sourceImage=ImageIO.read(Path.of("src/main/resources/assets/tacticalcombat/textures/gui/creator/"+meta.get("source").getAsString()).toFile());int ink=0;for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){int alpha=im.getRGB(x,y)>>>24;if(x<2||y<2||x>=im.getWidth()-2||y>=im.getHeight()-2)gutter&=alpha==0;if(alpha>0){ink++;unchanged&=im.getRGB(x,y)==sourceImage.getRGB(bounds.get(0).getAsInt()+x-2,bounds.get(1).getAsInt()+y-2);}}check(gutter&&unchanged,"Transparent gutter and original RGBA preserved "+e.getKey());
            check(ink==manifest.getAsJsonObject(e.getKey()).get("pixels").getAsInt(),"All extracted artwork remains present "+e.getKey());visible+=ink;
            for(int size:List.of(12,16,28,40)){var slot=new UiLayout.Rect(10,20,size,size);var fitted=CreatorIcons.fitted(r,slot,1);check(fitted.x()>=slot.x()+1&&fitted.y()>=slot.y()+1&&fitted.x()+fitted.width()<=slot.x()+size-1&&fitted.y()+fitted.height()<=slot.y()+size-1,"Full motif stays inside uniform target slot");check(Math.abs(2*fitted.x()+fitted.width()-(2*slot.x()+size))<=1&&Math.abs(2*fitted.y()+fitted.height()-(2*slot.y()+size))<=1,"Geometric centering is independent of class");}
        }
        long original=0;for(String file:List.of("icons.png","class-extra.png")){var im=ImageIO.read(Path.of("src/main/resources/assets/tacticalcombat/textures/gui/creator/"+file).toFile());for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++)if((im.getRGB(x,y)>>>24)>0)original++;}
        check(visible==manifest.entrySet().stream().mapToLong(e->e.getValue().getAsJsonObject().get("pixels").getAsLong()).sum(),"All selected sprite-source pixels survive packaging");
        check(manifest.entrySet().stream().filter(e->e.getValue().getAsJsonObject().get("source").getAsString().equals("icons.png")||e.getValue().getAsJsonObject().get("source").getAsString().equals("class-extra.png")).count()==43,"43 original assets remain in use; five explicitly corrected class assets");
        check(CreatorIcons.REGIONS.keySet().stream().noneMatch(s->s.contains("elf")||s.contains("dwarf")||s.contains("race")),"No replacement race assets");
        var dir=Files.createTempDirectory("creator-checkpoint2");var repo=new CharacterRepository(dir.resolve("campaigns.json"));var library=new LibraryRepository(dir.resolve("library.json"));
        var api=new UiApiService(repo,library,(op,a)->{throw new IllegalArgumentException("No world adapter");});var owner=new CharacterRepository.Principal(UUID.randomUUID(),false);
        var create=api.handle(owner,LIBRARY_CREATE,args("profile","srd_5_1","mode","GUIDED","sheet",dev.tacticalcombat.rules.srd.SrdAuthoring.template().toString()));check(create.code().equals("OK"),"Unified creation uses existing authorized operation");var draft=create.data();String id=text(draft,"id");
        var identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");identity.addProperty("species",CreatorFlow.recordedRace("Elf","High Elf"));identity.addProperty("characterClass","Fighter");identity.addProperty("background","Acolyte");identity.addProperty("biography","First line\nSecond line\n\nA personal story.");
        var saved=api.handle(owner,LIBRARY_UPDATE,args("character",id,"revision",revision(draft),"section","identity","data",identity));check(saved.code().equals("OK"),"Combined identity saved without schema change");draft=DraftReplies.snapshot(saved.data(),id);
        check(api.handle(owner,LIBRARY_UPDATE,args("character",id,"revision",1,"section","identity","data",identity)).code().equals("CONFLICT"),"Stale update still rejected");
        var buffers=new CreatorDraftState(draft);buffers.fields.put("age","100");var external=draft.deepCopy();external.addProperty("revision",revision(draft)+1);var externalIdentity=SheetSections.read(external.getAsJsonObject("sheet"),"identity");externalIdentity.addProperty("height","Tall");external.add("sheet",SheetSections.update(external.getAsJsonObject("sheet"),"identity",externalIdentity));
        buffers.accept(external,false);check(buffers.fields.get("age").equals("100")&&buffers.fields.get("height").equals("Tall"),"Refresh merges untouched fields while retaining local edits");check(buffers.reviewRequired(),"External revision requires explicit review before saving local intent");buffers.reviewed();check(!buffers.reviewRequired(),"Review anchors the exact refreshed revision");
        buffers.fields.put("name","Newer typing");var ack=external.deepCopy();ack.addProperty("name","Previously sent name");ack.addProperty("revision",revision(external)+1);buffers.accept(ack,true);check(buffers.fields.get("name").equals("Newer typing")&&buffers.dirty(),"Own acknowledgement never erases input typed after dispatch");buffers.standardArray();check(new HashSet<>(buffers.scoreValues().values()).equals(new HashSet<>(WizardModel.STANDARD_ARRAY)),"On-tab standard array assigns each value once");buffers.method=WizardModel.ScoreMethod.POINT_BUY;for(String key:CreatorDraftState.ABILITIES)buffers.scores.put(key,"8");check(WizardModel.pointRemaining(buffers.scoreValues())==27,"Point-buy starts with full budget");buffers.scores.put("str","16");try{buffers.scoreValues();throw new AssertionError("Point-buy accepted 16");}catch(IllegalArgumentException expected){count++;}
        var ids=new HashSet<String>();for(var c:Srd2014Catalog.CLASSES)for(var entry:FeatureReference.classFeatures(c.id())){check(ids.add(entry.id())&&entry.source().contains("2014")&&entry.level()>=1&&entry.level()<=3,"Versioned unique SRD feature metadata");check(entry.detail().contains("no effect is applied"),"Inspection cannot imply execution");}
        check(FeatureReference.classFeatures("fighter").stream().anyMatch(e->e.name().equals("Second Wind")&&e.formula().equals("1d10 + fighter level")&&e.recovery().equals("Short or long rest")),"Second Wind uses 2014 formula and recovery");check(FeatureReference.classFeatures("barbarian").stream().anyMatch(e->e.name().equals("Frenzy")&&e.description().contains("exhaustion")),"Frenzy retains 2014 exhaustion, not BG3 rules");
        var validate=api.handle(owner,LIBRARY_VALIDATE,args("character",id));check(validate.code().equals("OK")&&!validate.data().has("sheet"),"Validate stays a separate result internally");
        check(CreatorSheet.sections(draft,validate.data()).stream().anyMatch(s->s.title().equals("Character Details")&&s.text().contains("First line\nSecond line")),"Review keeps multiline story content");
        check(CreatorSheet.sections(draft,null).stream().anyMatch(s->s.title().equals("Equipment · manual")),"Review uses typed sheet section panels");
        check(Files.readString(Path.of("src/main/resources/tacticalcombat.client.mixins.json")).contains("NarrativeEditBoxAccessor"),"GUI caret accessor is registered");
        check(CreatorSheet.summary(draft,true,validate.data()).contains("Elf — High Elf"),"Full review retains actual saved identity after validation");
        String before=repo.state().toString();var finish=api.handle(owner,LIBRARY_FINALIZE,args("character",id,"revision",revision(draft)));check(finish.code().equals("OK"),"Finish follows validation using saved revision");var complete=DraftReplies.snapshot(finish.data(),id);
        check(CreatorSheet.summary(complete,true,null).contains("Campaign approval and binding are separate"),"Finished review makes authority distinction explicit");
        check(repo.state().toString().equals(before)&&repo.state().characters().isEmpty(),"Personal completion changes no campaign/combat state");
        check(new LibraryRepository(dir.resolve("library.json")).get(owner,UUID.fromString(id)).sheet().equals(complete.get("sheet").toString()),"Combined identity survives library restart");
        check(CreatorFlow.errorStep("inventory")==CreatorFlow.Step.EQUIPMENT&&CreatorFlow.errorStep("combat")==CreatorFlow.Step.ABILITIES,"Actionable finish issue destinations");
        System.out.println("Creator checkpoint 3 checks passed: "+count);
    }
}
