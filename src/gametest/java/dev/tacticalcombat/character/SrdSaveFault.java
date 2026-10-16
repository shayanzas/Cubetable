package dev.tacticalcombat.character;

import java.nio.file.Path;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/** Development-only failure at the production atomic replacement boundary. */
public final class SrdSaveFault {
    public static CharacterRepository failFinalCommit(Path path,BooleanSupplier armed)throws IOException{
        var primaryWrites=new java.util.concurrent.atomic.AtomicInteger();
        return new CharacterRepository(new AtomicJsonFile(path,destination->{if(destination.equals(path)&&primaryWrites.incrementAndGet()>=2&&armed.getAsBoolean())throw new IOException("Injected final commit replacement interruption");}));
    }
    public static CharacterRepository repository(Path path,BooleanSupplier fail) throws IOException {
        return new CharacterRepository(new AtomicJsonFile(path,destination->{
            if(destination.equals(path) && fail.getAsBoolean()) throw new IOException("Injected primary replacement interruption");
        }));
    }
}
