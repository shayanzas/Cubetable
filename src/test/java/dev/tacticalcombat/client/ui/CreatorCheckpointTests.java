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
        check(CreatorFlow.Step.values().length==11,"Unified sections include details, overview and finish");
        check(Arrays.stream(CreatorFlow.Step.values()).noneMatch(s->s.title.contains("Method")),"No method stage in presentation");
        for(int[] size:List.of(new int[]{320,180},new int[]{480,257},new int[]{640,360},new int[]{854,480},new int[]{1280,720},new int[]{1920,1080})) {
            var frame=UiLayout.frame(size[0],size[1],720);var l=CreatorFlow.layout(frame);
            check(l.navigation().fits(size[0],size[1])&&l.content().fits(size[0],size[1]),"Creator regions fit "+Arrays.toString(size));
            check(!l.navigation().overlaps(l.content()),"Separate navigation and content");
            if(l.summary().width()>0)check(l.summary().fits(size[0],size[1])&&!l.summary().overlaps(l.content()),"Summary region independent");
            for(var step:List.of(CreatorFlow.Step.IDENTITY,CreatorFlow.Step.CLASS,CreatorFlow.Step.BACKGROUND))for(boolean sub:List.of(false,true)) {
                var c=CreatorFlow.catalogLayout(l.content(),step,sub);
                var rects=List.of(c.name(),c.subraces(),c.search(),c.grid(),c.details(),c.choose(),c.custom()).stream().filter(r->r.width()>0).toList();
                for(var r:rects)check(r.fits(size[0],size[1])&&r.y()+r.height()<=l.content().y()+l.content().height(),"Usable catalog region fits");
                for(int i=0;i<rects.size();i++)for(int j=i+1;j<rects.size();j++)check(!rects.get(i).overlaps(rects.get(j)),"Controls and descriptions never overlap");
            }
        }
        check(CreatorFlow.recordedRace("Elf","High Elf").equals("Elf — High Elf"),"Conditional subrace preserves identity schema");
        check(CreatorFlow.baseRace("Elf — High Elf").equals("Elf")&&CreatorFlow.baseRace("High Elf").equals("Elf"),"Legacy and combined identities understood");
        check(CreatorFlow.subraces("Tiefling").isEmpty(),"No BG3 proprietary tiefling variants imported");
        boolean invalid=false;try{CreatorFlow.recordedRace("Elf","Drow");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"Non-SRD subrace cannot masquerade as supported choice");
        for(var c:Srd2014Catalog.CLASSES){check(CreatorIcons.REGIONS.containsKey(c.id()),"Class has actual icon "+c.id());check(CreatorLore.classDetails(c).contains("not applied automatically"),"Class guidance cannot imply auto-grants");}
        var atlas=ImageIO.read(Path.of("src/main/resources/assets/tacticalcombat/textures/gui/creator/icons.png").toFile());
        var extras=ImageIO.read(Path.of("src/main/resources/assets/tacticalcombat/textures/gui/creator/class-extra.png").toFile());
        for(var e:CreatorIcons.REGIONS.entrySet()){
            var im=e.getKey().equals("paladin")||e.getKey().equals("sorcerer")?extras:atlas;var r=e.getValue();
            check(r.x()>=0&&r.y()>=0&&r.x()+r.width()<=im.getWidth()&&r.y()+r.height()<=im.getHeight(),"Sprite bounds "+e.getKey());
            boolean ink=false;for(int y=r.y();y<r.y()+r.height()&&!ink;y++)for(int x=r.x();x<r.x()+r.width();x++)if((im.getRGB(x,y)>>>24)>128){ink=true;break;}check(ink,"Sprite has visible artwork "+e.getKey());
        }
        check(CreatorIcons.REGIONS.keySet().stream().noneMatch(s->s.contains("elf")||s.contains("dwarf")||s.contains("race")),"No replacement race assets");
        var dir=Files.createTempDirectory("creator-checkpoint2");var repo=new CharacterRepository(dir.resolve("campaigns.json"));var library=new LibraryRepository(dir.resolve("library.json"));
        var api=new UiApiService(repo,library,(op,a)->{throw new IllegalArgumentException("No world adapter");});var owner=new CharacterRepository.Principal(UUID.randomUUID(),false);
        var create=api.handle(owner,LIBRARY_CREATE,args("profile","srd_5_1","mode","GUIDED","sheet",null));check(create.code().equals("OK"),"Unified creation uses existing authorized operation");var draft=create.data();String id=text(draft,"id");
        var identity=SheetSections.read(draft.getAsJsonObject("sheet"),"identity");identity.addProperty("species",CreatorFlow.recordedRace("Elf","High Elf"));identity.addProperty("characterClass","Fighter");identity.addProperty("background","Acolyte");identity.addProperty("biography","First line\nSecond line\n\nA personal story.");
        var saved=api.handle(owner,LIBRARY_UPDATE,args("character",id,"revision",revision(draft),"section","identity","data",identity));check(saved.code().equals("OK"),"Combined identity saved without schema change");draft=DraftReplies.snapshot(saved.data(),id);
        check(api.handle(owner,LIBRARY_UPDATE,args("character",id,"revision",1,"section","identity","data",identity)).code().equals("CONFLICT"),"Stale update still rejected");
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
        System.out.println("Creator checkpoint 2 checks passed: "+count);
    }
}
