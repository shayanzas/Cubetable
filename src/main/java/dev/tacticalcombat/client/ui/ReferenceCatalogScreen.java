package dev.tacticalcombat.client.ui;

import dev.tacticalcombat.reference.SrdReferenceCatalog;
import net.minecraft.client.gui.screen.Screen;
import java.util.*;

/** Local public reference data. Reading a stat block never binds or activates it. */
public final class ReferenceCatalogScreen extends ManagementScreen {
    private String category;
    public ReferenceCatalogScreen(Screen parent){this(parent,"");}
    public ReferenceCatalogScreen(Screen parent,String category){super("SRD 5.1 Reference · Read Only",parent);this.category=category;}
    @Override protected void init(){super.init();if(!dev.tacticalcombat.client.ClientUiApi.supported())refresh();}
    @Override protected void controls(){if(!category.isEmpty())button("Categories","",frame.x()+112,frame.y()+frame.height()-38,Math.max(100,frame.width()-124),22,()->{category="";refresh();});}
    private static String sourceNotice(SrdReferenceCatalog catalog){var source=catalog.document().getAsJsonArray("sources").get(0).getAsJsonObject();return source.get("attribution").getAsString()+"\n\nChanges: "+source.get("changes").getAsString()+"\n\nVerification: "+source.get("licenseVerification").getAsString()+"\n\nCoverage: "+catalog.entries().size()+" source-referenced entries across all requested categories. Spells, conditions, weapon and armor tables are checked against their full source inventories. Monsters, magic items, feature progression and other categories remain bounded batches; see the coverage report.";}
    @Override protected void refresh(){fresh=true;clearRows();try{var catalog=SrdReferenceCatalog.get();if(category.isEmpty()){for(var c:SrdReferenceCatalog.CATEGORIES.stream().sorted().toList()){long count=catalog.entries().stream().filter(e->e.category().equals(c)).count();if(count==0)continue;row(c.replace('-',' ')+" · "+count,"Reference entries · population coverage documented separately",count>0,"No verified entries in this batch",()->{category=c;client.setScreen(this);});}row("Attribution & Source Coverage","CC BY 4.0 · structured adaptation · no gameplay activation",true,"",()->client.setScreen(new InfoScreen(this,"SRD 5.1 attribution","Source and verification limits",sourceNotice(catalog))));}else for(var e:catalog.entries().stream().filter(e->e.category().equals(category)).sorted(Comparator.comparing(SrdReferenceCatalog.Entry::name)).toList())row(e.name(),e.category().equals("spells")?"Level "+e.data().get("level")+" · "+e.data().get("school").getAsString():"Reference only · source pages "+e.pages(),true,"Not executable gameplay",()->client.setScreen(new InfoScreen(this,e.name(),"2014 SRD 5.1 · Reference only",e.detail())));message="Search this reference category. No saved character values are changed.";}catch(RuntimeException failure){fresh=false;info("Catalog unavailable",failure.getMessage());}filter();}
}
