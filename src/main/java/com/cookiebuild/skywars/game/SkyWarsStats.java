package com.cookiebuild.skywars.game;

import java.util.HashMap;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.cookiebuild.skywars.loot.ChestTier;
import com.cookiebuild.skywars.loot.LootTable;

public final class SkyWarsStats {
    public record Snapshot(int kills, int deaths, int chestsOpened, int blocksPlaced,
            Map<ChestTier, Integer> chestTiers, Map<LootTable.Family, Integer> lootFamilies,
            int timeToFirstChestSeconds, int timeToMidSeconds, String deathCause) {
        public Snapshot {
            chestTiers = Map.copyOf(chestTiers);
            lootFamilies = Map.copyOf(lootFamilies);
        }
    }

    private static final class MutableStats {
        private int kills;
        private int deaths;
        private int chestsOpened;
        private int blocksPlaced;
        private final Map<ChestTier, Integer> chestTiers = new EnumMap<>(ChestTier.class);
        private final Map<LootTable.Family, Integer> lootFamilies = new EnumMap<>(LootTable.Family.class);
        private int timeToFirstChestSeconds = -1;
        private int timeToMidSeconds = -1;
        private String deathCause = "alive";
    }

    private final Map<UUID, MutableStats> stats = new HashMap<>();

    public void register(UUID playerId) {
        stats.computeIfAbsent(playerId, ignored -> new MutableStats());
    }

    public void recordElimination(UUID victim, UUID killer) {
        recordElimination(victim, killer, "unknown");
    }

    public void recordElimination(UUID victim, UUID killer, String cause) {
        register(victim);
        MutableStats victimStats = stats.get(victim);
        victimStats.deaths++;
        victimStats.deathCause = cause == null || cause.isBlank() ? "unknown" : cause;
        if (killer != null && !killer.equals(victim)) {
            register(killer);
            stats.get(killer).kills++;
        }
    }

    public void recordChest(UUID playerId) {
        recordChest(playerId, ChestTier.ISLAND, Set.of(), -1);
    }

    public void recordChest(UUID playerId, ChestTier tier, Set<LootTable.Family> families, int elapsedSeconds) {
        register(playerId);
        MutableStats value = stats.get(playerId);
        value.chestsOpened++;
        value.chestTiers.merge(tier, 1, Integer::sum);
        families.forEach(family -> value.lootFamilies.merge(family, 1, Integer::sum));
        if (value.timeToFirstChestSeconds < 0 && elapsedSeconds >= 0) {
            value.timeToFirstChestSeconds = elapsedSeconds;
        }
        if (tier == ChestTier.MID && value.timeToMidSeconds < 0 && elapsedSeconds >= 0) {
            value.timeToMidSeconds = elapsedSeconds;
        }
    }

    public void recordBlockPlaced(UUID playerId) {
        register(playerId);
        stats.get(playerId).blocksPlaced++;
    }

    public Snapshot snapshot(UUID playerId) {
        MutableStats value = stats.getOrDefault(playerId, new MutableStats());
        return new Snapshot(value.kills, value.deaths, value.chestsOpened, value.blocksPlaced,
                value.chestTiers, value.lootFamilies, value.timeToFirstChestSeconds,
                value.timeToMidSeconds, value.deathCause);
    }

    public void clear() {
        stats.clear();
    }
}
