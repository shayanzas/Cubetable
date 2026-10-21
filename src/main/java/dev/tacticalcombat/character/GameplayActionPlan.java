package dev.tacticalcombat.character;

import com.google.gson.*;
import dev.tacticalcombat.rules.srd.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Server-created, immutable weapon operation. Not a client payload or a complete character record. */
public record GameplayActionPlan(int version,UUID operation,UUID requester,UUID campaign,UUID encounter,long turn,
        Participant attacker,Participant target,Attack attack,SrdRules.AttackResult outcome,String fingerprint) {
    public static final int VERSION=1;
    public record Resources(int hp,int temporaryHp,int arrows,int bolts,int daggers) {
        public Resources {for(int n:new int[]{hp,temporaryHp,arrows,bolts,daggers})require(n>=0&&n<=1024,"Resource bounds");}
        public static Resources of(SrdCharacter c){return new Resources(c.hp(),c.temporaryHp(),c.arrows(),c.bolts(),c.daggers());}
    }
    public record Participant(UUID character,UUID actor,UUID controller,long revision,long runtimeRevision,SrdCharacter before,Resources after) {
        public Participant {Objects.requireNonNull(character);Objects.requireNonNull(actor);Objects.requireNonNull(controller);Objects.requireNonNull(before);Objects.requireNonNull(after);require(revision>0&&runtimeRevision>=0&&runtimeRevision<Long.MAX_VALUE,"Invalid participant revision");require(after.hp<=before.maximumHp(),"HP exceeds maximum");}
    }
    public record Attack(SrdEquipment.Weapon weapon,SrdRules.AttackMode mode,SrdCharacter.Ability ability,boolean twoHands,int cover,boolean advantage,boolean disadvantage) {
        public Attack {Objects.requireNonNull(weapon);Objects.requireNonNull(mode);Objects.requireNonNull(ability);require(cover==0||cover==2||cover==5,"Invalid cover");}
    }
    private record Identity(int version,UUID operation,UUID requester,UUID campaign,UUID encounter,long turn,Participant attacker,Participant target,Attack attack,SrdRules.AttackResult outcome) {}
    public GameplayActionPlan {
        if(version!=VERSION && version!=2)throw new UnsupportedOperationException("Unknown gameplay plan version");
        Objects.requireNonNull(operation);Objects.requireNonNull(requester);Objects.requireNonNull(campaign);Objects.requireNonNull(encounter);Objects.requireNonNull(attacker);Objects.requireNonNull(target);Objects.requireNonNull(attack);Objects.requireNonNull(outcome);
        require(turn>0&&!attacker.character.equals(target.character)&&!attacker.actor.equals(target.actor),"Invalid action scope");
        require(version!=2||attack.mode==SrdRules.AttackMode.MELEE,"Reaction requires a melee attack");
        // Reconstruct from fixed faces to verify decoding without asking RNG for another outcome.
        var faces=new ArrayDeque<Integer>(outcome.roll().faces());faces.addAll(outcome.damageFaces());
        var checked=SrdRules.attack(attacker.before,attack.weapon,attack.mode,attack.ability,attack.twoHands,target.before.armorClass()+attack.cover,attack.advantage,attack.disadvantage,sides->{require(!faces.isEmpty(),"Missing fixed dice");int n=faces.removeFirst();require(n>=1&&n<=sides,"Invalid fixed die");return n;});
        require(faces.isEmpty()&&checked.equals(outcome),"Outcome does not match fixed dice and inputs");
        require(attacker.after.equals(attackerAfter(attacker.before,attack)),"Invalid attacker resource changes");
        require(target.after.equals(targetAfter(target.before,outcome)),"Invalid defender resource changes");
        String expected=hash(new Identity(version,operation,requester,campaign,encounter,turn,attacker,target,attack,outcome));
        if(fingerprint==null)fingerprint=expected;else require(expected.equals(fingerprint),"Gameplay fingerprint mismatch");
    }
    public static GameplayActionPlan weapon(UUID operation,UUID requester,UUID campaign,UUID encounter,long turn,
            CharacterRepository.SrdSnapshot a,UUID ac,CharacterRepository.SrdSnapshot b,UUID bc,Attack attack,SrdRules.AttackResult outcome){
        require(a.campaign().equals(campaign)&&b.campaign().equals(campaign),"Wrong campaign");
        return new GameplayActionPlan(VERSION,operation,requester,campaign,encounter,turn,
            new Participant(a.character(),a.actor(),ac,a.revision(),a.runtimeRevision(),a.stats(),attackerAfter(a.stats(),attack)),
            new Participant(b.character(),b.actor(),bc,b.revision(),b.runtimeRevision(),b.stats(),targetAfter(b.stats(),outcome)),attack,outcome,null);
    }
    private static Resources attackerAfter(SrdCharacter c,Attack a){int arrows=c.arrows(),bolts=c.bolts(),daggers=c.daggers();if(a.mode==SrdRules.AttackMode.THROWN)daggers--;else if(a.mode==SrdRules.AttackMode.RANGED){if(a.weapon==SrdEquipment.Weapon.LIGHT_CROSSBOW)bolts--;else arrows--;}return new Resources(c.hp(),c.temporaryHp(),arrows,bolts,daggers);}
    private static Resources targetAfter(SrdCharacter c,SrdRules.AttackResult outcome){var health=SrdHealth.of(c).damage(List.of(outcome.damage()),c);return new Resources(health.current(),health.temporary(),c.arrows(),c.bolts(),c.daggers());}
    public List<Participant> participants(){return List.of(attacker,target);}
    /** Version 1 is a normal action; version 2 is one encounter-local reaction. */
    public int actionCost(){return version==1?1:0;}
    public int reactionCost(){return version==2?1:0;}
    public GameplayActionPlan asReaction(){return new GameplayActionPlan(2,operation,requester,campaign,encounter,turn,attacker,target,attack,outcome,null);}
    /** Future typed effects require a new plan version; v1 cannot carry arbitrary effects or clocks. */
    public int effectsVersion(){return 0;}
    private static String hash(Object value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical(new Gson().toJsonTree(value)).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
    private static String canonical(JsonElement value){if(value.isJsonObject()){var o=new JsonObject();value.getAsJsonObject().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e->o.add(e.getKey(),JsonParser.parseString(canonical(e.getValue()))));return o.toString();}if(value.isJsonArray()){var a=new JsonArray();var entries=value.getAsJsonArray().asList();if(entries.stream().allMatch(e->e.isJsonPrimitive()&&e.getAsJsonPrimitive().isString()))entries=entries.stream().sorted(java.util.Comparator.comparing(JsonElement::getAsString)).toList();entries.forEach(e->a.add(JsonParser.parseString(canonical(e))));return a.toString();}return value.toString();}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalArgumentException(why);}
}
