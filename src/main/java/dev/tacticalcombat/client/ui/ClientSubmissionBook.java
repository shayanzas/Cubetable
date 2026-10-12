package dev.tacticalcombat.client.ui;

import net.fabricmc.loader.api.FabricLoader;
import java.io.IOException;

/** One fixed client-local journal; stores retry identities only, never private sheets or server authority. */
public final class ClientSubmissionBook {
    private static SubmissionBook book;
    private ClientSubmissionBook() {}
    public static SubmissionBook get() {
        if(book==null) try {book=new SubmissionBook(FabricLoader.getInstance().getConfigDir().resolve("tacticalcombat/ui-submissions.json"));}
        catch(IOException failed) {throw new IllegalStateException("Submission journal unavailable. Inspect known campaign copies; resolve local storage before retrying.",failed);}
        return book;
    }
}
