package dev.tacticalcombat.client.ui;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

/** Native editing/clipboard/keyboard semantics with a centered numerical rendering. */
public final class CenteredScoreField extends TextFieldWidget {
    private final TextRenderer font;
    public CenteredScoreField(TextRenderer font,int x,int y,int width,int height,Text title){super(font,x,y,width,height,title);this.font=font;setMaxLength(2);setTextPredicate(s->s.matches("[0-9]{0,2}"));}
    private int scale(){return height>=22?2:1;}
    @Override public void renderWidget(DrawContext g,int mx,int my,float delta){RpgTheme.panel(g,getX(),getY(),width,height,RpgTheme.PANEL,isFocused());String value=getText();int scale=scale(),tx=getX()+(width-font.getWidth(value)*scale)/2,ty=getY()+(height-9*scale)/2;g.getMatrices().push();g.getMatrices().translate(tx,ty,0);g.getMatrices().scale(scale,scale,1);g.drawText(font,value,0,0,active?RpgTheme.GOLD:RpgTheme.MUTED,false);g.getMatrices().pop();if(isFocused()){if(!getSelectedText().isEmpty())g.fill(tx,ty+9*scale,tx+font.getWidth(value)*scale,ty+9*scale+1,RpgTheme.GOLD);int cx=tx+font.getWidth(value.substring(0,Math.min(getCursor(),value.length())))*scale;g.fill(cx,ty-1,cx+1,ty+9*scale,RpgTheme.TEXT);}}
    @Override public void onClick(double x,double y){String value=getText();double tx=getX()+(width-font.getWidth(value)*scale())/2.0;int cursor=0;while(cursor<value.length()&&x>tx+font.getWidth(value.substring(0,cursor+1))*scale()-font.getWidth(value.substring(cursor,cursor+1))*scale()/2.0)cursor++;setCursor(cursor,false);}
}
