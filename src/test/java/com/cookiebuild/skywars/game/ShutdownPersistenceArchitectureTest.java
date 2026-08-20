package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ShutdownPersistenceArchitectureTest {
    @Test
    void runningDisableUsesFreshTransactionWithoutSchedulerAndAlwaysCleansUp() throws IOException {
        Path path = Path.of("src/main/java/com/cookiebuild/skywars/game/SkyWarsGame.java");
        if (!Files.exists(path)) path = Path.of("SkyWars").resolve(path);
        String game = Files.readString(path);
        String flush = game.substring(game.indexOf("private void persistInterruptedOutcome"),
                game.indexOf("private void logWarning"));
        String shutdown = game.substring(game.indexOf("public void shutdown()"),
                game.indexOf("private void persistInterruptedOutcome"));
        String cleanup = game.substring(game.indexOf("private void cleanup()"));

        assertTrue(flush.contains("BoundedAsyncFlush.runAndAwait"));
        assertTrue(flush.contains("new MatchService(null)"));
        assertFalse(flush.contains("getScheduler"));
        assertTrue(shutdown.contains("finally"));
        assertTrue(shutdown.contains("cleanup();"));
        assertTrue(cleanup.contains("MapManager.unloadMap"));
        assertTrue(cleanup.contains("participantIds.clear()"));
        assertTrue(cleanup.contains("GameManager.removeGame(this)"));
        assertTrue(cleanup.contains("finally"));
    }
}
