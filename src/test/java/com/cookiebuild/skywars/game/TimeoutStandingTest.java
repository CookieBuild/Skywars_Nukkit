package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class TimeoutStandingTest {
    @Test
    void ranksKillsThenHealthThenMiddleControlThenChestActivity() {
        UUID fighter = UUID.fromString("00000000-0000-0000-0000-000000000003");
        UUID survivor = UUID.fromString("00000000-0000-0000-0000-000000000001");
        assertEquals(fighter, TimeoutStanding.winner(List.of(
                new TimeoutStanding(survivor, 1, 20.0, 4, 10),
                new TimeoutStanding(fighter, 2, 1.0, 0, 1))));
    }

    @Test
    void returnsADrawForAPerfectTieRegardlessOfRosterOrder() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        assertEquals(null, TimeoutStanding.winner(List.of(
                new TimeoutStanding(second, 1, 10.0, 1, 2),
                new TimeoutStanding(first, 1, 10.0, 1, 2))));
        assertEquals(null, TimeoutStanding.winner(List.of(
                new TimeoutStanding(first, 1, 10.0, 1, 2),
                new TimeoutStanding(second, 1, 10.0, 1, 2))));
    }
}
