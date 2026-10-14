package dev.tacticalcombat.client.ui;
/** Explicit one-shot continuation. Reloading a screen never manufactures another navigation intent. */
public final class DeferredCreatorAction {
    private Runnable action;
    public void queue(Runnable next){action=next;}
    public void queueReviewed(long expected,java.util.function.LongSupplier revision,Runnable apply,Runnable changed){queue(()->{if(revision.getAsLong()==expected)apply.run();else changed.run();});}
    public void clear(){action=null;}
    public boolean pending(){return action!=null;}
    public void run(){var next=action;clear();if(next!=null)next.run();}
}
