package dev.tacticalcombat.client;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.*;
import dev.tacticalcombat.client.ui.UiRequestPump;
import java.util.*;
import java.util.concurrent.atomic.*;
import static dev.tacticalcombat.client.SrdRecoveryReview.*;

public final class SrdRollsRecoveryTests {
    private static int count;
    private static void check(boolean value,String message){count++;if(!value)throw new AssertionError(message);}
    public static void run(){
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),hidden=UUID.randomUUID();var looked=new HashSet<UUID>();
        java.util.function.Function<UUID,String> resolve=id->{looked.add(id);return "Member";};
        var data=new JsonObject();data.addProperty("owner",a.toString());var members=new JsonArray();
        var row=new JsonObject();row.addProperty("id",b.toString());row.addProperty("name","Player");members.add(row);members.add(a.toString());data.add("members",members);
        var sheet=new JsonObject();sheet.addProperty("controller",hidden.toString());data.add("sheet",sheet);data.addProperty("purpose",hidden.toString());
        var before=data.deepCopy();var names=AuthorizedNames.collect(data,resolve);
        check(looked.equals(Set.of(a,b))&&names.size()==2,"resolve only explicit authorized identities");check(data.equals(before)&&members.get(0).isJsonObject(),"member shape and entire read model preserved");
        for(String bad:List.of("null","true","42","[]","{}","{\"id\":\""+hidden+"\"}","{\"id\":\""+hidden+"\",\"name\":[]}","{\"id\":\""+hidden+"\",\"name\":\"X\",\"controller\":\""+hidden+"\"}","\"bad uuid\"","\"1-1-1-1-1\"")){
            var test=new JsonObject();var rows=new JsonArray();rows.add(JsonParser.parseString(bad));test.add("members",rows);looked.clear();check(AuthorizedNames.collect(test,resolve).isEmpty()&&looked.isEmpty(),"malformed/generic identities neither throw nor enumerate: "+bad);
        }
        for(String field:List.of("owner","controller","offered","dms")){var test=new JsonObject();test.add(field,row.deepCopy());looked.clear();check(AuthorizedNames.collect(test,resolve).isEmpty()&&looked.isEmpty(),"object identity accepted only by structured members contract");test.addProperty(field,a.toString());check(AuthorizedNames.collect(test,resolve).has(a.toString()),"legacy primitive field remains supported");}
        for(String field:List.of("sheet","history","origin","policy","playerNames")){var test=new JsonObject();test.add(field,data);looked.clear();check(AuthorizedNames.collect(test,resolve).isEmpty()&&looked.isEmpty(),"private/decorative subtree excluded");}
        var large=new JsonObject();var ids=new JsonArray();for(int i=0;i<400;i++)ids.add(UUID.randomUUID().toString());large.add("members",ids);looked.clear();check(AuthorizedNames.collect(large,resolve).size()==256&&looked.size()==256,"lookup and output bound");
        check(AuthorizedNames.collect(data,id->"x".repeat(200)).get(a.toString()).getAsString().length()==80,"name bound");
        var failure=new NullPointerException("SECRET_DC=19; token; private sheet");failure.initCause(new IllegalStateException("SECRET_RESULT"));
        String diagnostic=UiDiagnostics.context("CHECK_TARGETS",failure);check(diagnostic.contains("CHECK_TARGETS")&&diagnostic.contains("NullPointerException")&&diagnostic.contains(" at ")&&!diagnostic.contains("SECRET")&&!diagnostic.contains("token"),"unexpected diagnostic includes code context without messages or cause contents");check(!UiDiagnostics.context("SECRET_REQUEST_JSON",failure).contains("SECRET"),"untrusted operation cannot enter diagnostic");
        var faultService=new UiApiService(null,null,(op,args)->{throw new NullPointerException("PRIVATE_DICE_SECRET");});
        var reply=faultService.handle(null,UiApiService.Operation.CHECK_TARGETS,new JsonObject());
        check(reply.code().equals("SERVER_ERROR")&&reply.data().isEmpty()&&!reply.message().contains("PRIVATE")&&!reply.message().contains("NullPointer"),"production service translates unexpected fault to opaque bounded response");
        var wrapped=new RuntimeException("private record data",new java.lang.reflect.InvocationTargetException(new IllegalArgumentException("private invalid value")));
        check(UiDiagnostics.recordValidation(wrapped)&&!UiDiagnostics.recordValidation(failure),"Gson record validation recognized without masking unexpected faults");
        var state=JsonParser.parseString("{\"manager\":true,\"state\":\"PAUSED\",\"recovery\":{\"canHeal\":true}}").getAsJsonObject();check(mayHeal(state),"manager paused committed state offers healing");state.getAsJsonObject("recovery").addProperty("canHeal",false);check(!mayHeal(state),"uncommitted save failure cannot offer healing");state.getAsJsonObject("recovery").addProperty("canHeal",true);state.addProperty("manager",false);check(!mayHeal(state),"read-only viewer cannot heal");
        for(String value:List.of("0","-1","1025","99999999999999999","1.5","","NaN"))check(amount(value)==0,"invalid healing input disabled");for(String value:List.of("1","4","1024"))check(amount(value)==Integer.parseInt(value),"positive supported amount");
        var scope=new Scope(a,10,true,"PAUSED");var review=new SrdRecoveryReview();review.prepare(scope,"heal",b.toString(),4);check(review.consume(scope,true)==null,"pending blocks dispatch");var sent=review.consume(scope,false);check(sent.get("amount").getAsInt()==4&&sent.get("revision").getAsLong()==10&&sent.get("target").getAsString().equals(b.toString()),"confirmation retains stable target amount reviewed revision");check(review.consume(scope,false)==null,"double click cannot send twice");
        review.prepare(scope,"heal",b.toString(),4);review.clear();check(review.consume(scope,false)==null,"cancel sends nothing");
        for(var changed:List.of(new Scope(b,10,true,"PAUSED"),new Scope(a,11,true,"PAUSED"),new Scope(a,10,false,"PAUSED"),new Scope(a,10,true,"ACTIVE"),new Scope(a,10,true,"ENDED"))){review.prepare(scope,"heal",b.toString(),4);check(review.consume(changed,false)==null,"changed authority/snapshot invalidates confirmation");}
        // Actual paced UI pump ignores delayed responses after campaign/screen generation invalidation.
        var epoch=new AtomicInteger(1);var accepted=new AtomicInteger();var sends=new ArrayList<UiApiService.Operation>();var callbacks=new ArrayList<java.util.function.Consumer<UiApiService.Reply>>();
        var pump=new UiRequestPump(new UiRequestPump.Transport(){public boolean supported(){return true;}public void send(UiApiService.Operation op,JsonObject args,java.util.function.Consumer<UiApiService.Reply> reply){sends.add(op);callbacks.add(reply);}});
        pump.enqueue(UiApiService.Operation.CHECK_TARGETS,new JsonObject(),()->epoch.get()==1,r->accepted.incrementAndGet());pump.tick();epoch.incrementAndGet();callbacks.getFirst().accept(new UiApiService.Reply("OK","",data));check(accepted.get()==0,"delayed old campaign response cannot revive targets");
        pump.enqueue(UiApiService.Operation.CHECK_INBOX,new JsonObject(),()->true,r->accepted.incrementAndGet());for(int i=0;i<10;i++)pump.tick();callbacks.getLast().accept(new UiApiService.Reply("SERVER_ERROR","bounded error",new JsonObject()));for(int i=0;i<50;i++)pump.tick();check(sends.equals(List.of(UiApiService.Operation.CHECK_TARGETS,UiApiService.Operation.CHECK_INBOX)),"read failure never auto prepares executes or retries");
        System.out.println("SRD rolls recovery tests passed: "+count);
    }
}
