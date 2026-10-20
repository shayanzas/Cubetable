package dev.tacticalcombat.client.ui;

/** Explicit operations survive layout rebuilds, but never a campaign/access/screen lifetime change. */
public final class WorkspaceMutationGate {
    public record Ticket(String campaign,long epoch) {}
    private long epoch;private Ticket pending;
    public Ticket begin(String campaign){if(pending!=null)return null;pending=new Ticket(campaign,++epoch);return pending;}
    public boolean matches(Ticket ticket,String campaign){return ticket!=null&&ticket.equals(pending)&&ticket.campaign().equals(campaign);}
    public boolean complete(Ticket ticket,String campaign){if(!matches(ticket,campaign))return false;pending=null;return true;}
    public boolean pending(){return pending!=null;}
    public void invalidate(){pending=null;epoch++;}
}
