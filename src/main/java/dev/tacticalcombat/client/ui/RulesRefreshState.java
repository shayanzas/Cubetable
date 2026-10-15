package dev.tacticalcombat.client.ui;

/** Coalesces notifications without discarding unsaved edits or repeatedly retrying failed reads. */
public final class RulesRefreshState {
    private long notices,read;
    private boolean blocked;
    public void changed(){notices++;blocked=false;}
    public long begin(){blocked=false;return notices;}
    public void completed(long observed){read=Math.max(read,observed);}
    public void failed(){blocked=true;}
    public void revoked(){read=notices;blocked=true;}
    public boolean pending(){return notices>read;}
    public boolean shouldRefresh(boolean busy,boolean unsaved){return pending()&&!blocked&&!busy&&!unsaved;}
}
