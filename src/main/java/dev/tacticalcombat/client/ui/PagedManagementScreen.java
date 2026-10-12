package dev.tacticalcombat.client.ui;

import net.minecraft.client.gui.screen.Screen;

public abstract class PagedManagementScreen extends ManagementScreen {
    protected int page;
    private RpgButton previous,next;
    protected PagedManagementScreen(String title,Screen parent) {super(title,parent);}
    @Override protected boolean paged() {return true;}
    @Override protected void controls() {
        int x=frame.x()+12,y=frame.y()+frame.height()-56,w=frame.width()-24;
        previous=button("‹ Previous","",x,y,86,20,()->{if(!busy && page>0) {page--;refresh();}});
        next=button("Next ›","",x+w-86,y,86,20,()->{if(!busy) {page++;refresh();}});
        pageControls(x+92,y,w-184);
    }
    protected void pageControls(int x,int y,int width) {}
    protected void pagination(boolean more) {previous.active=page>0;next.active=more;}
    protected void loadingPage() {fresh=false;clearRows();pagination(false);}
}
