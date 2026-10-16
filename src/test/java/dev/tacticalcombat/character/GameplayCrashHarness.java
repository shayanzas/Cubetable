package dev.tacticalcombat.character;

import java.nio.file.Path;
import java.util.UUID;

/** Child JVM fault probe: no shutdown hooks or graceful repository/server close. */
public final class GameplayCrashHarness {
    public static void main(String[] args)throws Exception{
        var repo=new CharacterRepository(Path.of(args[0]));var dm=new CharacterRepository.Principal(UUID.fromString(args[1]),true);
        var plan=GameplayTransactionTests.plan(repo,dm,UUID.fromString(args[2]),UUID.fromString(args[3]),UUID.fromString(args[4]),true,true);
        repo.prepareGameplay(dm,plan);if(args[5].equals("commit"))repo.commitGameplay(dm,plan.operation(),plan.fingerprint());Runtime.getRuntime().halt(71);
    }
}
