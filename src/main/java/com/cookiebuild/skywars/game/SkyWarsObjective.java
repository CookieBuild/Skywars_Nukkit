package com.cookiebuild.skywars.game;

import com.cookiebuild.skywars.loot.ChestTier;

final class SkyWarsObjective {
    private SkyWarsObjective() {
    }

    static String messageKey(SkyWarsStats.Snapshot stats) {
        if (stats.chestsOpened() == 0) {
            return "skywars.objective.open_chest";
        }
        if (stats.blocksPlaced() == 0
                && stats.chestTiers().getOrDefault(ChestTier.MID, 0) == 0) {
            return "skywars.objective.reach_center";
        }
        return "skywars.objective.survive";
    }
}
