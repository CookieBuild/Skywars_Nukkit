package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WaitingStatusTest {
    @Test
    void reportsMissingPlayersBeforeCountdown() {
        WaitingStatus status = WaitingStatus.from(1, 12, 2, 0, 30);

        assertFalse(status.isCountingDown());
        assertEquals(1, status.morePlayersNeeded());
        assertEquals(-1, status.secondsRemaining());
    }

    @Test
    void reportsSameCountdownShapeAsOtherGames() {
        WaitingStatus status = WaitingStatus.from(2, 12, 2, 3, 10);

        assertTrue(status.isCountingDown());
        assertEquals(7, status.secondsRemaining());
        assertEquals(2, status.players());
        assertEquals(12, status.capacity());
    }
}
