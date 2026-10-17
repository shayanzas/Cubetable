package dev.tacticalcombat.client.dicevisual;

/** Seconds-based visual motion. Final pose is a continuous limit of the tumble. */
public final class RollMotion {
    public enum Phase { INTRO, TUMBLE, LAND, REVEAL, DISMISS }
    public record Pose(Phase phase,double x,double y,double lift,double rx,double ry,double rz,double shadow,double opacity) {}
    public static Pose at(double elapsed,double tumble,double hold,boolean reduced,int slot){
        double land=.20;double reveal=tumble+land;double finish=reveal+hold;double fade=.25;
        if(reduced)return new Pose(elapsed<hold?Phase.REVEAL:Phase.DISMISS,0,0,0,0,0,0,1,Math.clamp((hold+fade-elapsed)/fade,0,1));
        if(elapsed<.13){double q=Math.clamp(elapsed/.13,0,1);return new Pose(Phase.INTRO,(1-q)*(-55-slot*8)-20-slot*4,-(1-q)*26,5+11+12*(1-q),9+3*(1-q)+slot*.7,7+2*(1-q)+slot*.6,5+2*(1-q)+slot*.4,.75-.5*(1-q),1);}
        if(elapsed<tumble){double q=Math.clamp((elapsed-.13)/Math.max(.1,tumble-.13),0,1);double remain=(1-q)*(1-q);double bounced=Math.abs(Math.sin(q*2.7*Math.PI));return new Pose(Phase.TUMBLE,(1-q)*(-20-slot*4),0,remain*(16+42*bounced),remain*(9+slot*.7),remain*(7+slot*.6),remain*(5+slot*.4),1-Math.min(.25,remain*(.25+bounced*.4)),1);}
        if(elapsed<reveal){double q=(elapsed-tumble)/land;double decay=(1-q)*(1-q),rebound=decay*Math.sin(q*Math.PI*2);return new Pose(Phase.LAND,0,0,6*Math.abs(rebound),.14*rebound,.10*rebound,.08*rebound,1-.25*Math.abs(rebound),1);}
        if(elapsed<finish)return new Pose(Phase.REVEAL,0,0,0,0,0,0,1,1);
        return new Pose(Phase.DISMISS,0,0,0,0,0,0,1,Math.clamp((finish+fade-elapsed)/fade,0,1));
    }
    private RollMotion(){}
}
