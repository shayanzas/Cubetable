package dev.tacticalcombat.client.ui;

import com.google.gson.JsonObject;
import dev.tacticalcombat.uiapi.UiApiService;
import static dev.tacticalcombat.client.ui.ManagementState.*;
import static dev.tacticalcombat.uiapi.UiApiService.Operation.*;

/** Exact operation envelope for one library source or one campaign copy. */
public record DashboardSource(boolean library,String character) {
    public UiApiService.Operation read(){return library?LIBRARY_GET:CHARACTER_GET;}
    public UiApiService.Operation edit(){return library?LIBRARY_UPDATE:CHARACTER_EDIT;}
    public JsonObject readArgs(){return args("character",character);}
    public JsonObject sectionArgs(JsonObject latest,String section,JsonObject data){return library?args("character",character,"revision",revision(latest),"section",section,"data",data):args("character",character,"revision",revision(latest),"runtimeRevision",latest.get("runtimeRevision").getAsLong(),"section",section,"data",data);}
    public JsonObject manualArgs(JsonObject latest,dev.tacticalcombat.uiapi.RpgRecords.Change change){return args("scope",library?"LIBRARY":"CAMPAIGN","character",character,"revision",revision(latest),"runtimeRevision",library?0:latest.get("runtimeRevision").getAsLong(),"change",dev.tacticalcombat.uiapi.SheetSections.JSON.toJsonTree(change));}
}
