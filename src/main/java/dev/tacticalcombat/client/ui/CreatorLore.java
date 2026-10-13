package dev.tacticalcombat.client.ui;

import java.util.*;

/** Original, limited SRD 5.1 summaries. These descriptions never grant mechanics. */
public final class CreatorLore {
    private CreatorLore() {}
    public static String classDetails(Srd2014Catalog.Choice c) {
        String features=FeatureReference.classFeatures(c.id()).stream().map(e->"Level "+e.level()+": "+e.name()+" — "+e.description()).reduce((a,b)->a+"\n"+b).orElse("Custom class; discuss rules with your DM.");
        return c.name()+"\n\n"+FeatureReference.classProficiencies(c.id())+"\n"+features+"\n\n"+c.summary()+"\n"+c.guidance()+"\nChoose "+Srd2014Catalog.classSkillCount(c.name())+" class skills in Proficiencies.\n\nSRD 2014 reference. Benefits are not applied automatically. Level 1–3 introduction only.";
    }
    public static String details(CreatorFlow.Step step,Srd2014Catalog.Choice c) {
        if(step==CreatorFlow.Step.CLASS)return classDetails(c);
        String traits=step==CreatorFlow.Step.IDENTITY?FeatureReference.raceTraits(c.name(),"").stream().map(e->e.name()+": "+e.description()).reduce((a,b)->a+"\n"+b).orElse("Custom race: discuss traits with your DM."):"";
        return c.name()+"\n\n"+c.summary()+"\n\n"+traits+"\n\n"+c.guidance()+"\n\nSRD 2014 reference. Record benefits with your DM. Choosing this entry does not change combat statistics or apply ability increases.";
    }
}
