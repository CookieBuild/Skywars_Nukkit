package com.cookiebuild.skywars.kit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class KitManagerConcurrencyTest {
    @Test
    void coalescesMutationsPerPlayerAndAppliesCooldown() {
        KitManager manager = new KitManager(null);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(manager.tryBeginMutation(first));
        assertFalse(manager.tryBeginMutation(first));
        assertTrue(manager.tryBeginMutation(second));
        manager.finishMutation(first);
        assertFalse(manager.tryBeginMutation(first));
        manager.finishMutation(second);
    }

    @Test
    void releaseClearsMutationAndCooldownEvenWhenPlayerLeavesMidAction() {
        KitManager manager = new KitManager(null);
        UUID playerId = UUID.randomUUID();

        assertTrue(manager.tryBeginMutation(playerId));
        manager.release(playerId);
        manager.finishReleaseIfIdle(playerId); // profile-load completion may race before the UI finally block
        assertEquals(1, manager.trackedActionCount(), "the in-flight mutation must retain the release tombstone");
        manager.finishMutation(playerId);

        assertEquals(0, manager.trackedActionCount(), "quit must not leave a cooldown behind");
        assertTrue(manager.tryBeginMutation(playerId));
    }

    @Test
    void actionTrackingIsBoundedAcrossManyUniquePlayers() {
        KitManager manager = new KitManager(null);
        for (int index = 0; index < 5_000; index++) {
            UUID playerId = new UUID(0L, index + 1L);
            assertTrue(manager.tryBeginMutation(playerId));
            manager.finishMutation(playerId);
        }
        assertTrue(manager.trackedActionCount() <= 4_096);
        manager.clear();
        assertEquals(0, manager.trackedActionCount());
    }
}
