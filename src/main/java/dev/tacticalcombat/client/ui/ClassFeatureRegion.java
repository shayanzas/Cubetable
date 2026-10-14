package dev.tacticalcombat.client.ui;

/** Isolated level-feature composition: a future clipped surface can replace this adapter. */
public final class ClassFeatureRegion {
    private ClassFeatureRegion(){}
    public static void build(CharacterCanvas pane,String clazz,UiLayout.Rect region,java.util.function.Consumer<FeatureReference.Entry> inspect){int level=-1,y=region.y();for(var entry:FeatureReference.classFeatures(clazz)){if(entry.level()!=level){level=entry.level();pane.label("Level "+level,region.x(),y,region.width(),RpgTheme.GOLD);y+=16;}CreatorWorkspace.feature(pane,entry,region.x(),y,region.width(),inspect);y+=21;}}
}
