package dev.tacticalcombat.character;

import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.function.Function;

/** Same-directory, fsynced replacement with a validated last-good backup. Never silently resets bad data. */
public final class AtomicJsonFile {
    private final Path path;
    @FunctionalInterface interface BeforeReplace { void run(Path destination) throws IOException; }
    private final BeforeReplace beforeReplace;
    private boolean recovered;
    public boolean recoveredFromBackup() { return recovered; }
    public AtomicJsonFile(Path path) { this(path,destination->{}); }
    AtomicJsonFile(Path path,BeforeReplace beforeReplace) { this.path=path; this.beforeReplace=beforeReplace; }
    public <T> T read(Function<String,T> decode, T empty) throws IOException {
        if(!Files.exists(path) && !Files.exists(backup())) return empty;
        Exception primary;
        try { return decode.apply(readBounded(path)); }
        catch(UnsupportedOperationException newer) { throw new IOException("Unsupported save version; refusing rollback",newer); }
        catch(Exception e) { primary=e; }
        try { T result=decode.apply(readBounded(backup())); recovered=true; return result; }
        catch(Exception backup) { throw new IOException("Primary and backup unreadable: "+path,primary); }
    }
    private Path backup() { return path.resolveSibling(path.getFileName()+".bak"); }
    private String readBounded(Path file) throws IOException {
        if(Files.size(file)>16*1024*1024) throw new IOException("Save exceeds 16 MiB");
        return Files.readString(file,StandardCharsets.UTF_8);
    }
    public void write(String json, Function<String,?> validate) throws IOException {
        validate.apply(json);
        if(json.getBytes(StandardCharsets.UTF_8).length>16*1024*1024) throw new IOException("Save exceeds 16 MiB");
        Files.createDirectories(path.getParent());
        // Do not replace a valid backup with corrupt primary bytes recovered on the last read.
        if(Files.exists(path)) {
            String old=readBounded(path);
            boolean valid=true;
            try { validate.apply(old); } catch(RuntimeException invalid) { valid=false; }
            if(valid) replace(backup(),old);
        }
        replace(path,json);
    }
    private void replace(Path destination,String text) throws IOException {
        Path temp=Files.createTempFile(path.getParent(),".cubetable-", ".tmp");
        try {
            Files.writeString(temp,text,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            try(FileChannel channel=FileChannel.open(temp,StandardOpenOption.WRITE)) { channel.force(true); }
            // Refuse an unsafe fallback on filesystems lacking atomic replacement.
            beforeReplace.run(destination);
            Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
}
