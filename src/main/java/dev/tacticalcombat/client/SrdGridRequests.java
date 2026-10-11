package dev.tacticalcombat.client;
import java.util.*;
/** Client presentation correlation only, never server authorization. Keeps one request, no backlog. */
public final class SrdGridRequests {
    private UUID encounter,request;
    public void expect(UUID encounter,UUID request) {this.encounter=Objects.requireNonNull(encounter);this.request=Objects.requireNonNull(request);}
    public void clear() {encounter=null;request=null;}
    /** Older v1 servers omit the additive identity and retain the legacy revision-checked behavior. */
    public boolean accepts(UUID encounter,String supplied) {
        return supplied==null || encounter.equals(this.encounter) && request!=null && request.toString().equals(supplied);
    }
}
