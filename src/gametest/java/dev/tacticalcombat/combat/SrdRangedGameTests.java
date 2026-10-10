package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;
import static dev.tacticalcombat.combat.SrdTargetingGameTests.bind;

public class SrdRangedGameTests implements FabricGameTest {
    static JsonObject equipment(String weapon,String resource,int amount) {
        var sheet=SrdAuthoring.template();var data=SrdAuthoring.data(sheet);data.getAsJsonArray("weapons").add(weapon);
        data.getAsJsonArray("weapon_proficiencies").add(weapon);data.addProperty(resource,amount);return sheet;
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void thrownFinesseLongRangeMissConsumesOnePersistentDagger(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);var sheet=equipment("dagger","daggers",1);SrdAuthoring.data(sheet).getAsJsonObject("abilities").addProperty("dex",16);bind(f,a,sheet);
            var b=f.actor(8,1,true);var dice=new Dice(20,1,20,1);var e=begin(f,dice,a,b);
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.DAGGER,SrdRules.AttackMode.THROWN,SrdCharacter.Ability.DEX,false);
            check(preview.plan().attackModifier()==5 && preview.plan().flatDamage()==3 && preview.geometry().disadvantage(),"thrown finesse uses chosen Dex/proficiency and long range disadvantage");
            var result=e.confirm(f.dm,preview.id());check(!result.roll.hit() && CharacterService.srdStats(a,f.campaign).stats().daggers()==0,"accepted thrown miss consumes a dagger");
            check(new CharacterRepository(f.dir.resolve("state.json")).srdSnapshot(a.getUuid(),f.campaign).stats().daggers()==0,"thrown stock survives restart");
            e.endTurn(f.dm);e.endTurn(f.dm);
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.DAGGER,SrdRules.AttackMode.THROWN,SrdCharacter.Ability.DEX,false),"spent dagger cannot be thrown again");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void crossbowCloseHostileDisadvantageAndLoadingScope(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);bind(f,a,equipment("light_crossbow","bolts",2));var b=f.actor(2,1,true);
            var e=begin(f,new Dice(20,1,20,1),a,b);
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.LIGHT_CROSSBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,false),"crossbow requires two hands");
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.LIGHT_CROSSBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true);
            check(preview.geometry().disadvantage(),"visible conscious hostile within five feet penalizes ranged attack");
            e.confirm(f.dm,preview.id());check(CharacterService.srdStats(a,f.campaign).stats().bolts()==1,"one accepted shot consumes one bolt");
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.LIGHT_CROSSBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true),"no second loading attack in the same supported single-attack action");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void unseenKnownLocationsCancelAdvantageWithoutIgnoringTotalCover(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);bind(f,a,equipment("shortbow","arrows",1));var b=f.actor(8,1,true);a.setInvisible(true);b.setInvisible(true);
            var dice=new Dice(20,1,10,4);var e=begin(f,dice,a,b);
            for(int z=0;z<3;z++)for(int y=1;y<=2;y++)ctx.getWorld().setBlockState(f.origin.add(4,y,z),net.minecraft.block.Blocks.STONE.getDefaultState());
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true),"unseen is distinct from total cover; invisible creature behind wall cannot be directly targeted");
            for(int z=0;z<3;z++)for(int y=1;y<=2;y++)ctx.getWorld().setBlockState(f.origin.add(4,y,z),net.minecraft.block.Blocks.AIR.getDefaultState());
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true);
            check(preview.geometry().advantage() && preview.geometry().disadvantage(),"both unseen with explicitly public positions gives both sources");
            var result=e.confirm(f.dm,preview.id());check(result.roll.roll().faces().size()==1 && result.roll.hit(),"advantage/disadvantage cancel to one authoritative d20");
            check(CharacterService.srdStats(b,f.campaign).stats().hp()==6,"ranged damage uses SRD dice, not projectile callbacks");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void failedRangedSaveCommitsAmmoAndDamageTogetherAndKeepsMovement(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);bind(f,a,equipment("shortbow","arrows",1));var b=f.actor(8,1,true);
            var dice=new Dice(20,1,20,1,1);var e=begin(f,dice,a,b);
            var destination=new SrdMovement.Cell(f.origin.getX()+2,f.origin.getZ()+1,f.origin.getY()+1);e.move(f.dm,java.util.UUID.randomUUID(),destination);e.tick();
            var fail=new java.util.concurrent.atomic.AtomicBoolean(true);f.field.set(null,SrdSaveFault.repository(f.dir.resolve("state.json"),fail::get));
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true);var result=e.confirm(f.dm,preview.id());
            check(result.state==SrdEncounter.TransactionState.SAVE_FAILED && CharacterService.srdStats(a,f.campaign).stats().arrows()==1 && CharacterService.srdStats(b,f.campaign).stats().hp()==10,"failed single atomic write leaves both actors unchanged");
            fail.set(false);e.retry(f.dm);check(CharacterService.srdStats(a,f.campaign).stats().arrows()==0 && CharacterService.srdStats(b,f.campaign).stats().hp()==8,"same fixed critical commits stock and HP together");
            check(dice.rolls==5 && e.grid(f.dm).remainingFeet()==25,"retry does not reroll or reset spent movement after actor runtime revision");
            var reload=new CharacterRepository(f.dir.resolve("state.json"));check(reload.srdSnapshot(a.getUuid(),f.campaign).stats().arrows()==0 && reload.srdSnapshot(b.getUuid(),f.campaign).stats().hp()==8,"normal reload never sees half an action");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void maximumRangeAndUnknownResourcesFailWithoutSpending(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);var sheet=equipment("dagger","daggers",1);SrdAuthoring.data(sheet).getAsJsonArray("weapons").add("shortbow");bind(f,a,sheet);
            for(int x=15;x<=17;x++)for(int z=0;z<=2;z++)ctx.getWorld().setBlockState(f.origin.add(x,0,z),net.minecraft.block.Blocks.STONE.getDefaultState());
            var b=f.actor(16,1,true);var dice=new Dice(20,1);var e=begin(f,dice,a,b);
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.DAGGER,SrdRules.AttackMode.THROWN,SrdCharacter.Ability.STR,false),"beyond thrown maximum range rejected");
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.SHORTBOW,SrdRules.AttackMode.RANGED,SrdCharacter.Ability.DEX,true),"old sheets grant no implicit arrows");
            check(!e.actionUsed() && dice.rolls==2,"rejected range/stock causes no roll or action cost");
        }
        ctx.complete();
    }
}
