package dev.tacticalcombat.client.ui;
/** A modal resolves exactly once, even under repeated mouse and keyboard input. */
public final class ConfirmationDecision {
    private boolean resolved;
    public boolean cancel(){return resolve();}
    public boolean confirm(){return resolve();}
    private boolean resolve(){if(resolved)return false;resolved=true;return true;}
}
