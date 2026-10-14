package dev.tacticalcombat.client.ui;

import com.google.gson.*;
import dev.tacticalcombat.uiapi.SheetSections;
import static dev.tacticalcombat.client.ui.ManagementState.*;

/** A partial acknowledgement must never replace an authorized full sheet. */
public final class SnapshotReplies {
    private SnapshotReplies() {}
    public static JsonObject checked(DashboardSource source,JsonObject data){if(source.library())return DraftReplies.snapshot(data,source.character());if(data==null||!source.character().equals(text(data,"id"))||!data.has("sheet")||!data.get("sheet").isJsonObject()||!data.has("revision")||!data.has("runtimeRevision"))throw new IllegalArgumentException("Character response is incomplete; refresh before editing.");SheetSections.validate(data.getAsJsonObject("sheet"));return data.deepCopy();}
}
