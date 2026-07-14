package com.cookiebuild.skywars.map;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.World;

public final class MapTemplate {
    private final String name;
    private final String archive;
    private final List<List<Double>> spawns;
    private final List<Double> waitingSpawn;
    private final List<Double> spectatorSpawn;
    private final double killY;
    private final double middleRadius;

    public MapTemplate(String name, String archive, List<List<Double>> spawns,
            List<Double> waitingSpawn, List<Double> spectatorSpawn,
            double killY, double middleRadius) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Map name is required");
        }
        if (archive == null || !archive.matches("[A-Za-z0-9._-]+\\.zip")) {
            throw new IllegalArgumentException("Map archive must be a simple .zip filename");
        }
        if (spawns == null || spawns.size() < 2) {
            throw new IllegalArgumentException("A SkyWars map needs at least two spawns");
        }
        spawns.forEach(coords -> requireCoordinates("spawn", coords));
        requireCoordinates("waiting-spawn", waitingSpawn);
        requireCoordinates("spectator-spawn", spectatorSpawn);
        if (!Double.isFinite(killY) || !Double.isFinite(middleRadius) || middleRadius <= 0) {
            throw new IllegalArgumentException("kill-y and middle-radius must be finite; radius must be positive");
        }
        this.name = name;
        this.archive = archive;
        this.spawns = spawns.stream().map(List::copyOf).toList();
        this.waitingSpawn = List.copyOf(waitingSpawn);
        this.spectatorSpawn = List.copyOf(spectatorSpawn);
        this.killY = killY;
        this.middleRadius = middleRadius;
    }

    private static void requireCoordinates(String field, List<Double> coords) {
        if (coords == null || coords.size() != 4 || coords.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException(field + " must contain finite [x, y, z, yaw] coordinates");
        }
    }

    public String getName() {
        return name;
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

    public double getMiddleRadius() {
        return middleRadius;
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
