package dev.tacticalcombat.client.ui;
import com.google.gson.JsonObject;
/** Rejects partial mutation/read replies before publishing them as canonical drafts. */
public final class DraftReplies {
    private DraftReplies() {}
    public static JsonObject snapshot(JsonObject data,String id) {
        if(data==null || !id.equals(ManagementState.text(data,"id")) || !data.has("sheet") || !data.get("sheet").isJsonObject() || !data.has("revision") || !data.has("stage"))
            throw new IllegalArgumentException("Incomplete character snapshot; retained previous draft");
        WizardModel.statuses(data); // Validate sections before publishing.
        return data.deepCopy();
    }
    public static JsonObject validationFromSnapshot(JsonObject snapshot){if(!snapshot.has("errors"))return null;return validation(ManagementState.args("errors",snapshot.get("errors").deepCopy()));}
    public static JsonObject validation(JsonObject data) {
        if(data==null || !data.has("errors") || !data.get("errors").isJsonArray())
            throw new IllegalArgumentException("Incomplete validation result");
        return data.deepCopy();
    }
}
