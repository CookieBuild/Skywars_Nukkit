package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import java.util.Set;

import com.cookiebuild.skywars.loot.ChestTier;
import com.cookiebuild.skywars.loot.LootTable;

import org.junit.jupiter.api.Test;

class SkyWarsStatsTest {
    @Test
    void recordsCombatAndContributionWithoutCreditingSelfKills() {
        SkyWarsStats stats = new SkyWarsStats();
        UUID victim = UUID.randomUUID();
        UUID killer = UUID.randomUUID();

        stats.recordElimination(victim, killer);
        stats.recordChest(killer, ChestTier.MID,
                Set.of(LootTable.Family.BLOCKS, LootTable.Family.PREMIUM), 42);
        stats.recordBlockPlaced(killer);
        stats.recordElimination(killer, killer, "combat");

        SkyWarsStats.Snapshot killerStats = stats.snapshot(killer);
        assertEquals(1, killerStats.kills());
        assertEquals(1, killerStats.deaths());
        assertEquals(1, killerStats.chestsOpened());
        assertEquals(1, killerStats.blocksPlaced());
        assertEquals(1, killerStats.chestTiers().get(ChestTier.MID));
        assertEquals(1, killerStats.lootFamilies().get(LootTable.Family.PREMIUM));
        assertEquals(42, killerStats.timeToFirstChestSeconds());
        assertEquals(42, killerStats.timeToMidSeconds());
        assertEquals("combat", killerStats.deathCause());

        SkyWarsStats.Snapshot victimStats = stats.snapshot(victim);
        assertEquals(0, victimStats.kills());
        assertEquals(1, victimStats.deaths());
    }
}
