package com.cookiebuild.skywars.game;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.cookiebuild.skywars.loot.ChestTier;
import com.cookiebuild.skywars.loot.LootTable;

class SkyWarsObjectiveTest {
    @Test
    void guidesThePlayerFromFirstChestToCenterAndSurvival() {
        assertEquals("skywars.objective.open_chest", SkyWarsObjective.messageKey(snapshot(0, 0, 0)));
        assertEquals("skywars.objective.reach_center", SkyWarsObjective.messageKey(snapshot(1, 0, 0)));
        assertEquals("skywars.objective.survive", SkyWarsObjective.messageKey(snapshot(1, 1, 0)));
        assertEquals("skywars.objective.survive", SkyWarsObjective.messageKey(snapshot(1, 0, 1)));
    }

    private static SkyWarsStats.Snapshot snapshot(int chestsOpened, int blocksPlaced, int midChests) {
        Map<ChestTier, Integer> tiers = midChests == 0
                ? Map.of() : Map.of(ChestTier.MID, midChests);
        return new SkyWarsStats.Snapshot(0, 0, chestsOpened, blocksPlaced, tiers,
                Map.<LootTable.Family, Integer>of(), -1, -1, "alive");
    }
}
