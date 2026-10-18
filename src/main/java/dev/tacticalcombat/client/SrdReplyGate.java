package dev.tacticalcombat.client;
import java.util.UUID;
/** A late direct acknowledgement must not release a newer mutation's pending guard. */
public final class SrdReplyGate {
    private UUID request;
    public void expect(UUID id){request=id;}
    public void clear(){request=null;}
    public boolean matches(String id){return request!=null&&request.toString().equals(id);}
}
