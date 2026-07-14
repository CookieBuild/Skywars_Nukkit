package com.cookiebuild.skywars.game;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SkyWarsStats {
    public record Snapshot(int kills, int deaths, int chestsOpened, int blocksPlaced) {
    }

    private static final class MutableStats {
        private int kills;
        private int deaths;
        private int chestsOpened;
        private int blocksPlaced;
    }

    private final Map<UUID, MutableStats> stats = new HashMap<>();

    public void register(UUID playerId) {
        stats.computeIfAbsent(playerId, ignored -> new MutableStats());
    }

    public void recordElimination(UUID victim, UUID killer) {
        register(victim);
        stats.get(victim).deaths++;
        if (killer != null && !killer.equals(victim)) {
            register(killer);
            stats.get(killer).kills++;
        }
    }

    public void recordChest(UUID playerId) {
        register(playerId);
        stats.get(playerId).chestsOpened++;
    }

    public void recordBlockPlaced(UUID playerId) {
        register(playerId);
        stats.get(playerId).blocksPlaced++;
    }

    public Snapshot snapshot(UUID playerId) {
        MutableStats value = stats.getOrDefault(playerId, new MutableStats());
        return new Snapshot(value.kills, value.deaths, value.chestsOpened, value.blocksPlaced);
    }

    public void clear() {
        stats.clear();
    }
}
