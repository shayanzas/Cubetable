package dev.tacticalcombat.character;

import java.nio.file.Path;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Development-only failure at the production atomic replacement boundary. */
public final class SrdSaveFault {
    public static CharacterRepository repository(Path path,BooleanSupplier fail) throws IOException {
        return new CharacterRepository(new AtomicJsonFile(path,destination->{
            if(destination.equals(path) && fail.getAsBoolean()) throw new IOException("Injected primary replacement interruption");
        }));
    }
}
