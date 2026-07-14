package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class SkyWarsStatsTest {
    @Test
    void recordsCombatAndContributionWithoutCreditingSelfKills() {
        SkyWarsStats stats = new SkyWarsStats();
        UUID victim = UUID.randomUUID();
        UUID killer = UUID.randomUUID();

        stats.recordElimination(victim, killer);
        stats.recordChest(killer);
        stats.recordBlockPlaced(killer);
        stats.recordElimination(killer, killer);

        assertEquals(new SkyWarsStats.Snapshot(1, 1, 1, 1), stats.snapshot(killer));
        assertEquals(new SkyWarsStats.Snapshot(0, 1, 0, 0), stats.snapshot(victim));
    }
}
