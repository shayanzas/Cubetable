package dev.tacticalcombat.combat;

import com.google.gson.*;
import dev.tacticalcombat.character.*;
import dev.tacticalcombat.rules.RulesProfiles;
import dev.tacticalcombat.rules.srd.*;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.test.*;
import static dev.tacticalcombat.combat.SrdEncounterGameTests.*;
import static dev.tacticalcombat.combat.SrdLiveGameTests.*;

public class SrdTargetingGameTests implements FabricGameTest {
    private static void bind(Fixture f,LivingEntity entity,JsonObject sheet) throws Exception {
        var id=f.repo.submit(f.dm,f.campaign,RulesProfiles.SRD,sheet.toString()); f.repo.approve(f.dm,id,1);f.repo.bind(f.dm,id,2,entity.getUuid(),null);
    }
    private static JsonObject fighter() {
        var sheet=SrdAuthoring.template();var data=SrdAuthoring.data(sheet);data.addProperty("reach_feet",30);
        data.getAsJsonArray("weapons").add("longsword");data.getAsJsonArray("weapon_proficiencies").add("longsword");return sheet;
    }
    private static SrdTargeting.Cover cover(Fixture f,LivingEntity a,LivingEntity b) {
        return SrdTargeting.inspect(a,CharacterService.srdStats(a,f.campaign).stats(),b,CharacterService.srdStats(b,f.campaign).stats()).cover();
    }
    private static void wall(Fixture f,int height) {
        for(int z=0;z<=3;z++) for(int y=1;y<=height;y++) f.ctx.getWorld().setBlockState(f.origin.add(4,y,z),Blocks.STONE.getDefaultState());
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void collisionCoverCategoriesAndLateWallRevalidation(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);bind(f,a,fighter());var b=f.actor(5,1,true);
            check(cover(f,a,b)==SrdTargeting.Cover.NONE,"open geometry no cover");
            wall(f,1);check(cover(f,a,b)==SrdTargeting.Cover.HALF,"low wall gives half cover via collision shape samples: "+cover(f,a,b));
            for(int z=0;z<=3;z++) ctx.getWorld().setBlockState(f.origin.add(4,2,z),Blocks.STONE_SLAB.getDefaultState());
            check(cover(f,a,b)==SrdTargeting.Cover.THREE_QUARTERS,"one-and-half block wall gives three-quarters cover: "+cover(f,a,b));
            wall(f,2);check(cover(f,a,b)==SrdTargeting.Cover.TOTAL,"solid full wall gives total cover");
            for(int z=0;z<=3;z++) for(int y=1;y<=2;y++)ctx.getWorld().setBlockState(f.origin.add(4,y,z),Blocks.AIR.getDefaultState());
            var dice=new Dice(20,1);var e=begin(f,dice,a,b);
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.LONGSWORD,SrdCharacter.Ability.STR,false);
            wall(f,2);rejects(()->e.confirm(f.dm,preview.id()),"late wall rejects stale confirmation");check(!e.actionUsed() && dice.rolls==2,"blocked confirmation spends no action and rolls no dice");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void liveHalfCoverChangesAcAndDefenseConsumesTempHpOnce(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);bind(f,a,fighter());var b=f.actor(5,1,false);
            var sheet=SrdAuthoring.template();var data=SrdAuthoring.data(sheet);data.addProperty("temporary_hp",2);data.getAsJsonArray("resistances").add("slashing");bind(f,b,sheet);
            wall(f,1);var dice=new Dice(20,1,9,20,4,4);var e=begin(f,dice,a,b);
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.LONGSWORD,SrdCharacter.Ability.STR,false);
            check(preview.geometry().cover()==SrdTargeting.Cover.HALF,"private preview contains public cover but no target AC");
            var miss=e.confirm(f.dm,preview.id());check(!miss.roll.hit() && miss.roll.roll().total()==11,"base AC10 becomes12; total11 misses and spends action");
            e.endTurn(f.dm);e.endTurn(f.dm);
            preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.LONGSWORD,SrdCharacter.Ability.STR,false);var hit=e.confirm(f.dm,preview.id());
            check(hit.roll.critical() && hit.roll.damageFaces().size()==2 && hit.roll.damage().amount()==8,"critical doubles weapon dice in actual encounter");
            var after=CharacterService.srdStats(b,f.campaign).stats();check(after.hp()==8 && after.temporaryHp()==0,"8 slashing halves to4 then absorbs2 temp; persisted HP8");
        }
        ctx.complete();
    }
    @GameTest(templateName=EMPTY_STRUCTURE)
    public void liveEquipmentValidationAndIntrinsicDisadvantage(TestContext ctx) throws Exception {
        try(var f=new Fixture(ctx)) {
            var a=f.actor(1,1,false);var sheet=fighter();var data=SrdAuthoring.data(sheet);data.addProperty("armor","plate");data.addProperty("size","small");data.getAsJsonArray("weapons").add("greatsword");bind(f,a,sheet);
            var b=f.actor(2,1,true);var dice=new Dice(20,1,20,1);var e=begin(f,dice,a,b);
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.GREATSWORD,SrdCharacter.Ability.STR,false),"two-handed weapon rejects one-handed intent");
            rejects(()->e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.LONGSWORD,SrdCharacter.Ability.DEX,false),"non-finesse Dex intent rejected");
            var preview=e.preview(f.dm,b.getUuid(),SrdEquipment.Weapon.GREATSWORD,SrdCharacter.Ability.STR,true);
            check(preview.plan().disadvantage(),"Small heavy weapon and nonproficient armor impose disadvantage once");
            var result=e.confirm(f.dm,preview.id());check(result.roll.roll().faces().size()==2 && result.roll.roll().chosen()==1 && !result.roll.hit(),"server uses low d20 and does not stack disadvantage dice");
        }
        ctx.complete();
    }
}
