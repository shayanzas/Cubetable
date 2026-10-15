package dev.tacticalcombat.client;

import dev.tacticalcombat.net.*;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import java.util.*;
import java.util.function.Consumer;

/** One active screen token. Late notifications cannot reopen or overwrite another screen. */
public final class ClientRulesWatch {
    private record Watch(Object owner,UUID token,boolean library,UUID character,String stamp,Consumer<Boolean> changed) {}
    private static Watch active;
    private ClientRulesWatch() {}
    public static void watch(Object owner,boolean library,UUID character,String stamp,Consumer<Boolean> changed){
        if(!ClientPlayNetworking.canSend(RulesWatchPayload.ID))return;
        if(active!=null&&active.owner==owner&&active.library==library&&active.character.equals(character)&&active.stamp.equals(stamp))return;
        UUID token=active!=null&&active.owner==owner?active.token:UUID.randomUUID();
        active=new Watch(owner,token,library,character,stamp,changed);
        ClientPlayNetworking.send(new RulesWatchPayload(token,library,character,stamp,false));
    }
    public static void panel(Object owner,UUID token,Consumer<Boolean> changed){close(owner);active=new Watch(owner,token,false,new UUID(0,0),"",changed);}
    public static void receive(RulesChangedPayload message){
        var watch=active;if(watch==null||!watch.token.equals(message.token()))return;
        if(message.revoked())active=null;
        watch.changed.accept(message.revoked());
    }
    public static void close(Object owner){var watch=active;if(watch==null||watch.owner!=owner)return;active=null;if(ClientPlayNetworking.canSend(RulesWatchPayload.ID))ClientPlayNetworking.send(new RulesWatchPayload(watch.token,watch.library,watch.character,"",true));}
    public static void clear(){active=null;}
}
