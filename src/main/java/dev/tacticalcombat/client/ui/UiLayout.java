package dev.tacticalcombat.client.ui;

import java.util.*;

/** Pure logical-pixel geometry shared by rendering, input and headless layout tests. */
public final class UiLayout {
    public record Rect(int x,int y,int width,int height) {
        public boolean contains(double px,double py) {return px>=x && py>=y && px<x+width && py<y+height;}
        public boolean overlaps(Rect b) {return x<b.x+b.width && x+width>b.x && y<b.y+b.height && y+height>b.y;}
        public boolean fits(int w,int h) {return x>=0 && y>=0 && width>0 && height>0 && x+width<=w && y+height<=h;}
    }
    private UiLayout() {}
    public static Rect frame(int width,int height,int maximum) {int margin=width<360?6:12;int w=Math.min(maximum,width-2*margin);return new Rect((width-w)/2,8,w,height-16);}
    public static List<Rect> hubTiles(Rect frame) {
        int gap=6,columns=2,rows=3;
        if(frame.width<330 || frame.height<222) return List.of();
        int available=frame.height-120,h=Math.min(66,(available-gap*(rows-1))/rows),w=(frame.width-24-gap*(columns-1))/columns;
        var result=new ArrayList<Rect>();for(int i=0;i<6;i++) result.add(new Rect(frame.x+12+(i%columns)*(w+gap),frame.y+90+(i/columns)*(h+gap),w,h));return List.copyOf(result);
    }
    public static Optional<Rect> pauseButton(int width,int height,List<Rect> controls) {
        if(width<160 || height<100) return Optional.empty();
        var candidates=new ArrayList<Rect>();
        if(!controls.isEmpty()) {
            var first=controls.getFirst();int bottom=controls.stream().mapToInt(r->r.y+r.height).max().orElse(0);
            candidates.add(new Rect(first.x,bottom+4,first.width,20));
            candidates.add(new Rect(first.x-100,first.y,94,20));
            candidates.add(new Rect(first.x+first.width+6,first.y,94,20));
        }
        candidates.add(new Rect(8,height-28,94,20));candidates.add(new Rect(8,8,94,20));
        for(var r:candidates) if(r.fits(width-4,height-4) && controls.stream().noneMatch(r::overlaps)) return Optional.of(r);
        return Optional.empty(); // A fully occupied third-party layout must not be obscured.
    }
}
