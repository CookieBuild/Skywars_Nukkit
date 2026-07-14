package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class CombatAttributionTrackerTest {
    @Test
    void consumesRecentHitOnlyOnce() {
        AtomicLong clock = new AtomicLong(1_000L);
        CombatAttributionTracker tracker = new CombatAttributionTracker(10_000L, clock::get);
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        tracker.record(victim, attacker);

        assertEquals(attacker, tracker.consume(victim));
        assertNull(tracker.consume(victim));
    }

    @Test
    void expiresOldHitAndRemovesAttackerReferences() {
        AtomicLong clock = new AtomicLong(1_000L);
        CombatAttributionTracker tracker = new CombatAttributionTracker(10_000L, clock::get);
        UUID victim = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();

        tracker.record(victim, attacker);
        clock.set(11_001L);
        assertNull(tracker.consume(victim));

        tracker.record(victim, attacker);
        tracker.remove(attacker);
        assertNull(tracker.consume(victim));
    }
}
