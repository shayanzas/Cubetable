package dev.tacticalcombat.character;

import java.util.Set;

/** Server-owned policy. Changing it never retroactively approves a draft. Locks apply to owners, not campaign DMs. */
public record ApprovalPolicy(boolean requireNewApproval, boolean requireEditApproval, boolean ownerLocked,
                             Set<String> ownerFields) {
    public ApprovalPolicy {
        ownerFields=Set.copyOf(ownerFields);
        if(ownerFields.size()>64 || ownerFields.stream().anyMatch(f->!CharacterEdits.validRule(f)))
            throw new IllegalArgumentException("Invalid owner-editable field policy");
    }
    public static ApprovalPolicy parse(com.google.gson.JsonObject json) {
        if(!json.keySet().equals(Set.of("requireNewApproval","requireEditApproval","ownerLocked","ownerFields")))
            throw new IllegalArgumentException("Policy requires all four explicit fields");
        for(String key:Set.of("requireNewApproval","requireEditApproval","ownerLocked"))
            if(!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isBoolean())
                throw new IllegalArgumentException("Policy flags must be booleans");
        if(!json.get("ownerFields").isJsonArray() || json.getAsJsonArray("ownerFields").size()>64)
            throw new IllegalArgumentException("Invalid owner field list");
        var fields=new java.util.HashSet<String>();
        for(var entry:json.getAsJsonArray("ownerFields")) {
            if(!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString() || !fields.add(entry.getAsString()))
                throw new IllegalArgumentException("Invalid or duplicate owner field");
        }
        return new ApprovalPolicy(json.get("requireNewApproval").getAsBoolean(),json.get("requireEditApproval").getAsBoolean(),json.get("ownerLocked").getAsBoolean(),fields);
    }
    public static ApprovalPolicy secureDefault() {
        return new ApprovalPolicy(true,true,false,Set.of("text.*","values.*","collections.*","kind","theme","ui"));
    }
    public boolean permits(String field) {
        int dot=field.indexOf('.');
        return !ownerLocked && (ownerFields.contains(field) || dot>0 && ownerFields.contains(field.substring(0,dot)+".*"));
    }
}
