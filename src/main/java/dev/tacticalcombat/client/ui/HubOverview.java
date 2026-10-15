package dev.tacticalcombat.client.ui;
/** Selected record and the player's physical binding remain independent facts. */
public final class HubOverview {
 private HubOverview(){}
 public static String approval(HubState s){if(s.status!=HubState.Status.READY)return s.status==HubState.Status.LOADING?"Loading character…":"Character unavailable";return s.character==null&&s.bound==null?"No character selected":s.characterApproved?"Character approved":"Character awaiting approval";}
 public static String binding(HubState s){if(s.status!=HubState.Status.READY)return "Binding not confirmed";if(s.bound!=null)return s.character==null||s.bound.equals(s.character)?"Your entity: character bound":"Your entity: another character bound";return s.characterBound?"Selected character bound to another entity":"Your entity: no character bound";}
 public static String encounter(HubState s){if(s.status!=HubState.Status.READY)return "Encounter status not confirmed";return s.inCombat?"Encounter in progress · selection locked":"No encounter in progress";}
}
