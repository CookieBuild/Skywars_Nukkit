package com.cookiebuild.skywars.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;

import com.cookiebuild.cookiedough.utils.FileUtils;
import com.cookiebuild.cookiedough.utils.ZipUtils;
import com.cookiebuild.skywars.SkyWars;

public final class MapManager {
    private static final Map<String, MapTemplate> TEMPLATES = new LinkedHashMap<>();
    private static final Map<UUID, GameMap> LOADED_MAPS = new LinkedHashMap<>();
    private static String lastSelectedMap;

    private MapManager() {
    }

    public static void loadTemplates() {
        TEMPLATES.clear();
        ConfigurationSection maps = SkyWars.getInstance().getConfig().getConfigurationSection("maps");
        if (maps == null) {
            throw new IllegalStateException("No SkyWars maps are configured");
        }
        for (String mapName : maps.getKeys(false)) {
            try {
                TEMPLATES.put(mapName, readTemplate(maps, mapName));
            } catch (IllegalArgumentException error) {
                SkyWars.getInstance().getLogger().severe("Ignoring invalid SkyWars map " + mapName + ": " + error.getMessage());
            }
        }
        if (TEMPLATES.isEmpty()) {
            throw new IllegalStateException("No valid SkyWars map templates are configured");
        }
        SkyWars.getInstance().getLogger().info("Loaded " + TEMPLATES.size() + " SkyWars map configurations");
    }

    private static MapTemplate readTemplate(ConfigurationSection maps, String name) {
        String path = name + ".";
        return new MapTemplate(
                name,
                maps.getString(path + "archive", name + ".zip"),
                numberMatrix(maps.getList(path + "spawns"), path + "spawns"),
                numberList(maps.getList(path + "waiting-spawn"), path + "waiting-spawn"),
                numberList(maps.getList(path + "spectator-spawn"), path + "spectator-spawn"),
                maps.getDouble(path + "kill-y"),
                maps.getDouble(path + "middle-radius", 18.0));
    }

    private static List<List<Double>> numberMatrix(List<?> source, String field) {
        if (source == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        List<List<Double>> result = new ArrayList<>();
        for (Object row : source) {
            if (!(row instanceof List<?> values)) {
                throw new IllegalArgumentException(field + " must be a list of coordinate lists");
            }
            result.add(numberList(values, field));
        }
        return result;
    }

    private static List<Double> numberList(List<?> source, String field) {
        if (source == null) {
            throw new IllegalArgumentException(field + " is missing");
        }
        List<Double> result = new ArrayList<>();
        for (Object value : source) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException(field + " contains a non-number");
            }
            result.add(number.doubleValue());
        }
        return result;
    }

    public static synchronized MapTemplate selectTemplate() {
        if (TEMPLATES.isEmpty()) {
            throw new IllegalStateException("No SkyWars map templates are loaded");
        }
        List<MapTemplate> available = TEMPLATES.values().stream()
                .filter(template -> new File("skywars_maps", template.getArchive()).isFile())
                .toList();
        if (available.isEmpty()) {
            throw new IllegalStateException("No configured SkyWars map archive exists in skywars_maps/");
        }
        List<MapTemplate> candidates = available.stream()
                .filter(template -> available.size() == 1 || !template.getName().equals(lastSelectedMap))
                .toList();
        MapTemplate selected = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        lastSelectedMap = selected.getName();
        return selected;
    }

    public static GameMap loadMap(UUID gameId, MapTemplate template) throws IOException {
        File archive = new File("skywars_maps", template.getArchive());
        if (!archive.isFile()) {
            throw new IOException("Missing SkyWars map archive: " + archive.getAbsolutePath());
        }
        NamespacedKey worldKey = new NamespacedKey(SkyWars.getInstance(), gameId.toString());
        if (Bukkit.getWorld(worldKey) != null || LOADED_MAPS.containsKey(gameId)) {
            throw new IOException("SkyWars world is already loaded for " + gameId);
        }

        File destination = worldDirectory(worldKey);
        if (destination.exists()) {
            FileUtils.deleteDirectory(destination);
        }
        try {
            ZipUtils.unzip(archive, destination);
            removeTransientWorldFiles(destination.toPath());
            validateExtractedWorld(destination.toPath());
            World world = WorldCreator.ofKey(worldKey)
                    .environment(World.Environment.NORMAL)
                    .generateStructures(false)
                    .generator(new VoidChunkGenerator())
                    .createWorld();
            if (world == null) {
                throw new IOException("Paper returned no world for " + worldKey);
            }
            Path expected = destination.toPath().toAbsolutePath().normalize();
            Path actual = world.getWorldFolder().toPath().toAbsolutePath().normalize();
            if (!actual.equals(expected)) {
                Bukkit.unloadWorld(world, false);
                throw new IOException("Paper loaded SkyWars map from unexpected path " + actual);
            }
            world.setAutoSave(false);
            world.setStorm(false);
            world.setThundering(false);
            world.setTime(6000L);
            world.setGameRule(GameRules.ADVANCE_TIME, false);
            world.setGameRule(GameRules.ADVANCE_WEATHER, false);
            world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
            world.setGameRule(GameRules.KEEP_INVENTORY, false);
            world.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
            world.setGameRule(GameRules.SPAWN_MOBS, false);
            world.setGameRule(GameRules.SPAWN_MONSTERS, false);
            validateSpawnTerrain(world, template);
            GameMap map = new GameMap(template, world);
            LOADED_MAPS.put(gameId, map);
            return map;
        } catch (IOException | RuntimeException error) {
            World partial = Bukkit.getWorld(worldKey);
            if (partial != null && partial.getPlayers().isEmpty()) {
                Bukkit.unloadWorld(partial, false);
            }
            if (destination.exists()) {
                try {
                    FileUtils.deleteDirectory(destination);
                } catch (IOException cleanupError) {
                    error.addSuppressed(cleanupError);
                }
            }
            if (error instanceof IOException ioError) {
                throw ioError;
            }
            throw new IOException("Could not load SkyWars world " + worldKey, error);
        }
    }

    public static boolean unloadMap(UUID gameId) {
        GameMap map = LOADED_MAPS.get(gameId);
        if (map == null) {
            return true;
        }
        World world = Bukkit.getWorld(map.world().getKey());
        if (world != null) {
            if (!world.getPlayers().isEmpty()) {
                SkyWars.getInstance().getLogger().warning("Cannot unload " + world.getKey() + ": players remain");
                return false;
            }
            if (!Bukkit.unloadWorld(world, false)) {
                SkyWars.getInstance().getLogger().warning("Paper refused to unload " + world.getKey());
                return false;
            }
        }
        try {
            FileUtils.deleteDirectory(map.world().getWorldFolder());
            LOADED_MAPS.remove(gameId, map);
            return true;
        } catch (IOException error) {
            SkyWars.getInstance().getLogger().severe("Could not delete SkyWars world: " + error.getMessage());
            return false;
        }
    }

    public static boolean unloadAll() {
        boolean success = true;
        for (UUID gameId : new ArrayList<>(LOADED_MAPS.keySet())) {
            success &= unloadMap(gameId);
        }
        return success;
    }

    private static File worldDirectory(NamespacedKey key) throws IOException {
        World overworld = Bukkit.getWorld(NamespacedKey.minecraft("overworld"));
        if (overworld == null) {
            throw new IOException("minecraft:overworld must be loaded before SkyWars maps");
        }
        Path folder = overworld.getWorldFolder().toPath().toAbsolutePath().normalize();
        Path keyedSuffix = Path.of("dimensions", "minecraft", "overworld");
        if (folder.endsWith(keyedSuffix)) {
            folder = folder.getParent().getParent().getParent();
        }
        return folder.resolve("dimensions").resolve(key.getNamespace()).resolve(key.getKey()).toFile();
    }

    private static void removeTransientWorldFiles(Path directory) throws IOException {
        Files.deleteIfExists(directory.resolve("session.lock"));
        Files.deleteIfExists(directory.resolve("uid.dat"));
    }

    static void validateExtractedWorld(Path directory) throws IOException {
        if (!Files.isRegularFile(directory.resolve("level.dat"))) {
            throw new IOException("Map archive must contain level.dat at its root (nested world folders are unsupported)");
        }
        try (var files = Files.walk(directory)) {
            if (files.noneMatch(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".mca"))) {
                throw new IOException("Map archive contains no region files and would create an empty arena");
            }
        }
    }

    private static void validateSpawnTerrain(World world, MapTemplate template) throws IOException {
        for (org.bukkit.Location spawn : template.getSpawns(world)) {
            boolean terrainFound = false;
            for (int offset = 1; offset <= 5; offset++) {
                if (world.getBlockAt(spawn.getBlockX(), spawn.getBlockY() - offset, spawn.getBlockZ())
                        .getType().isSolid()) {
                    terrainFound = true;
                    break;
                }
            }
            if (!terrainFound) {
                throw new IOException("No solid island terrain below configured spawn "
                        + spawn.getBlockX() + "," + spawn.getBlockY() + "," + spawn.getBlockZ());
            }
        }
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
    }
}
