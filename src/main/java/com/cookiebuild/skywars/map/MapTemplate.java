package com.cookiebuild.skywars.map;

import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.World;

import com.cookiebuild.skywars.loot.ChestTier;

public final class MapTemplate {
    private final String name;
    private final String displayName;
    private final String archive;
    private final List<List<Double>> spawns;
    private final List<Double> waitingSpawn;
    private final List<Double> spectatorSpawn;
    private final double killY;
    private final double islandChestRadius;
    private final Set<BlockPosition> midChests;

    public MapTemplate(String name, String archive, List<List<Double>> spawns,
            List<Double> waitingSpawn, List<Double> spectatorSpawn,
            double killY, double middleRadius) {
        this(name, name, archive, spawns, waitingSpawn, spectatorSpawn, killY, middleRadius, Set.of());
    }

    public MapTemplate(String name, String displayName, String archive, List<List<Double>> spawns,
            List<Double> waitingSpawn, List<Double> spectatorSpawn,
            double killY, double middleRadius) {
        this(name, displayName, archive, spawns, waitingSpawn, spectatorSpawn, killY, middleRadius, Set.of());
    }

    public MapTemplate(String name, String displayName, String archive, List<List<Double>> spawns,
            List<Double> waitingSpawn, List<Double> spectatorSpawn,
            double killY, double islandChestRadius, Set<BlockPosition> midChests) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }
        if (archive == null || !archive.matches("[A-Za-z0-9._-]+\\.zip")) {
            throw new IllegalArgumentException("Map archive must be a simple .zip filename");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Map display name is required");
        }
        if (spawns == null || spawns.size() < 2) {
            throw new IllegalArgumentException("A SkyWars map needs at least two spawns");
        }
        spawns.forEach(coords -> requireCoordinates("spawn", coords));
        requireCoordinates("waiting-spawn", waitingSpawn);
        requireCoordinates("spectator-spawn", spectatorSpawn);
        if (!Double.isFinite(killY) || !Double.isFinite(islandChestRadius) || islandChestRadius <= 0) {
            throw new IllegalArgumentException("kill-y and island-chest-radius must be finite; radius must be positive");
        }
        if (midChests == null || midChests.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("mid-chests must contain valid block coordinates");
        }
        this.name = name;
        this.displayName = displayName;
        this.archive = archive;
        this.spawns = spawns.stream().map(List::copyOf).toList();
        this.waitingSpawn = List.copyOf(waitingSpawn);
        this.spectatorSpawn = List.copyOf(spectatorSpawn);
        this.killY = killY;
        this.islandChestRadius = islandChestRadius;
        this.midChests = Set.copyOf(midChests);
    }

    private static void requireCoordinates(String field, List<Double> coords) {
        if (coords == null || coords.size() != 4 || coords.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException(field + " must contain finite [x, y, z, yaw] coordinates");
        }
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getArchive() {
        return archive;
    }

    public int getCapacity() {
        return spawns.size();
    }

    public double getKillY() {
        return killY;
    }

    public double getIslandChestRadius() {
        return islandChestRadius;
    }

    public Set<BlockPosition> getMidChests() {
        return midChests;
    }

    public ChestTier getChestTier(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Chest location is required");
        }
        if (midChests.contains(BlockPosition.of(location))) {
            return ChestTier.MID;
        }
        double radiusSquared = islandChestRadius * islandChestRadius;
        boolean island = getSpawns(location.getWorld()).stream()
                .anyMatch(spawn -> spawn.distanceSquared(location) <= radiusSquared);
        return island ? ChestTier.ISLAND : ChestTier.INTERMEDIATE;
    }

    public Location getSpawn(World world, int index) {
        return location(world, spawns.get(Math.floorMod(index, spawns.size())));
    }

    public List<Location> getSpawns(World world) {
        return spawns.stream().map(coords -> location(world, coords)).toList();
    }

    public Location getWaitingSpawn(World world) {
        return location(world, waitingSpawn);
    }

    public Location getSpectatorSpawn(World world) {
        return location(world, spectatorSpawn);
    }

    private static Location location(World world, List<Double> coords) {
        return new Location(world, coords.get(0), coords.get(1), coords.get(2), coords.get(3).floatValue(), 0.0f);
    }
}
