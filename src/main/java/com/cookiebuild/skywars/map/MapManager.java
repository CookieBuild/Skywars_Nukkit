package com.cookiebuild.skywars.map;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;
import io.papermc.paper.math.Position;

import com.cookiebuild.cookiedough.utils.FileUtils;
import com.cookiebuild.cookiedough.utils.ZipUtils;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline.WorldLoad;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline.PreparedFilesInUseException;
import com.cookiebuild.skywars.SkyWars;

public final class MapManager {
    private static final Logger LOGGER = Logger.getLogger(MapManager.class.getName());
    private static final long VALIDATION_BUDGET_MILLIS = 250L;

    public record PreparedMap(UUID gameId, MapTemplate template, NamespacedKey worldKey,
            File archive, File destination) { }
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
                maps.getString(path + "display-name", defaultDisplayName(name)),
                maps.getString(path + "archive", name + ".zip"),
                numberMatrix(maps.getList(path + "spawns"), path + "spawns"),
                numberList(maps.getList(path + "waiting-spawn"), path + "waiting-spawn"),
                numberList(maps.getList(path + "spectator-spawn"), path + "spectator-spawn"),
                maps.getDouble(path + "kill-y"),
                maps.getDouble(path + "island-chest-radius", maps.getDouble(path + "middle-radius", 18.0)),
                chestPositions(maps.getList(path + "mid-chests"), name));
    }

    private static Set<BlockPosition> chestPositions(List<?> source, String mapName) {
        List<?> configured = source == null ? LegacyChestLayouts.midChests(mapName) : source;
        Set<BlockPosition> result = new java.util.LinkedHashSet<>();
        for (List<Double> coordinate : numberMatrix(configured, mapName + ".mid-chests")) {
            if (coordinate.size() != 3 || coordinate.stream().anyMatch(value -> value != Math.rint(value))) {
                throw new IllegalArgumentException(mapName + ".mid-chests must contain integer [x, y, z] coordinates");
            }
            if (!result.add(new BlockPosition(coordinate.get(0).intValue(), coordinate.get(1).intValue(),
                    coordinate.get(2).intValue()))) {
                throw new IllegalArgumentException(mapName + ".mid-chests contains a duplicate coordinate");
            }
        }
        if (result.size() != LegacyChestLayouts.MID_CHESTS_PER_MAP) {
            throw new IllegalArgumentException(mapName + " must declare exactly "
                    + LegacyChestLayouts.MID_CHESTS_PER_MAP + " middle chests");
        }
        return Set.copyOf(result);
    }

    static String defaultDisplayName(String name) {
        return switch (name) {
            case "legacy-0" -> "Cookie Ring";
            case "legacy-1" -> "Cloud Circuit";
            case "legacy-2" -> "Crumb Canyon";
            case "legacy-4" -> "Golden Orbit";
            default -> name;
        };
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
        PreparedMap prepared = prepareIo(plan(gameId, template));
        try {
            return loadPrepared(prepared);
        } catch (IOException error) {
            discardPrepared(prepared);
            throw error;
        }
    }

    public static PreparedMap plan(UUID gameId, MapTemplate template) throws IOException {
        requirePrimaryThread("planned");
        File archive = new File("skywars_maps", template.getArchive());
        if (!archive.isFile()) {
            throw new IOException("Missing SkyWars map archive: " + archive.getAbsolutePath());
        }
        NamespacedKey worldKey = new NamespacedKey(SkyWars.getInstance(), gameId.toString());
        if (Bukkit.getWorld(worldKey) != null || LOADED_MAPS.containsKey(gameId)) {
            throw new IOException("SkyWars world is already loaded for " + gameId);
        }

        File destination = worldDirectory(worldKey);
        return new PreparedMap(gameId, template, worldKey, archive, destination);
    }

    public static PreparedMap prepareIo(PreparedMap prepared) throws IOException {
        File destination = prepared.destination();
        if (destination.exists()) {
            FileUtils.deleteDirectory(destination);
        }
        try {
            ZipUtils.unzip(prepared.archive(), destination);
            removeTransientWorldFiles(destination.toPath());
            validateExtractedWorld(destination.toPath());
            return prepared;
        } catch (IOException | RuntimeException error) {
            discardPrepared(prepared);
            if (error instanceof IOException ioError) throw ioError;
            throw new IOException("Could not prepare SkyWars world", error);
        }
    }

    public static GameMap loadPrepared(PreparedMap prepared) throws IOException {
        requirePrimaryThread("loaded");
        UUID gameId = prepared.gameId();
        MapTemplate template = prepared.template();
        NamespacedKey worldKey = prepared.worldKey();
        try {
            World world = createPreparedWorld(prepared);
            validateSpawnTerrain(world, template);
            GameMap map = new GameMap(template, world);
            LOADED_MAPS.put(gameId, map);
            return map;
        } catch (IOException | RuntimeException error) {
            World partial = Bukkit.getWorld(worldKey);
            if (partial != null && partial.getPlayers().isEmpty()) {
                Bukkit.unloadWorld(partial, false);
            }
            if (error instanceof IOException ioError) {
                throw ioError;
            }
            throw new IOException("Could not load SkyWars world " + worldKey, error);
        }
    }

    /**
     * Loads the world shell on the server thread, lets Paper read only the chunks
     * touched by validation asynchronously, then validates and registers the map
     * in Paper's guaranteed main-thread completion callback.
     */
    public static WorldLoad<GameMap> loadPreparedAsync(PreparedMap prepared) {
        requirePrimaryThread("loaded");
        CompletableFuture<GameMap> result = new CompletableFuture<>();
        final World world;
        long createStartedAt = System.nanoTime();
        try {
            world = createPreparedWorld(prepared);
        } catch (Throwable error) {
            failAsyncLoad(prepared, result, error, elapsedMillis(createStartedAt), 0L, 0L);
            return WorldLoad.nonCancellable(result);
        }
        long createWorldMillis = elapsedMillis(createStartedAt);

        Set<ChunkCoordinate> chunks = validationChunks(prepared.template());
        long preloadStartedAt = System.nanoTime();
        CompletableFuture<?>[] chunkLoads;
        try {
            chunkLoads = chunks.stream()
                    .map(chunk -> world.getChunkAtAsync(chunk.x(), chunk.z(), true))
                    .toArray(CompletableFuture<?>[]::new);
        } catch (Throwable error) {
            failAsyncLoad(prepared, result, error, createWorldMillis,
                    elapsedMillis(preloadStartedAt), 0L);
            return WorldLoad.nonCancellable(result);
        }

        AtomicBoolean cancelled = new AtomicBoolean();
        CompletableFuture<Void> aggregate = CompletableFuture.allOf(chunkLoads);
        aggregate.whenComplete((ignored, preloadError) -> {
            if (cancelled.get()) return;
            // Paper 26.1.2 guarantees async chunk futures complete on the main thread.
            if (!Bukkit.isPrimaryThread()) {
                result.completeExceptionally(new PreparedFilesInUseException(
                        "Paper completed SkyWars chunks off the server thread",
                        new IllegalStateException("Async chunk completion violated the Paper contract")));
                return;
            }
            long chunkPreloadMillis = elapsedMillis(preloadStartedAt);
            if (preloadError != null) {
                failAsyncLoad(prepared, result,
                        new IOException("Could not preload SkyWars validation chunks", preloadError),
                        createWorldMillis, chunkPreloadMillis, 0L);
                return;
            }

            long validationStartedAt = System.nanoTime();
            try {
                validateSpawnTerrain(world, prepared.template());
                GameMap map = new GameMap(prepared.template(), world);
                LOADED_MAPS.put(prepared.gameId(), map);
                long validationMillis = elapsedMillis(validationStartedAt);
                logAsyncLoadTimings(prepared, createWorldMillis, chunkPreloadMillis, validationMillis, null);
                result.complete(map);
            } catch (Throwable error) {
                failAsyncLoad(prepared, result, error, createWorldMillis, chunkPreloadMillis,
                        elapsedMillis(validationStartedAt));
            }
        });
        return WorldLoad.cancellable(result,
                () -> cancelAsyncLoad(prepared, result, aggregate, chunkLoads, cancelled));
    }

    private static boolean cancelAsyncLoad(PreparedMap prepared, CompletableFuture<GameMap> result,
            CompletableFuture<Void> aggregate,
            CompletableFuture<?>[] chunkLoads, AtomicBoolean cancelled) {
        requirePrimaryThread("cancelled");
        if (!cancelled.compareAndSet(false, true)) return result.isDone();
        aggregate.cancel(false);
        for (CompletableFuture<?> chunkLoad : chunkLoads) chunkLoad.cancel(false);

        CancellationException cancellation = new CancellationException(
                "SkyWars arena load cancelled for " + prepared.gameId());
        boolean unloaded = true;
        World partial = Bukkit.getWorld(prepared.worldKey());
        if (partial != null && (!partial.getPlayers().isEmpty() || !Bukkit.unloadWorld(partial, false))) {
            unloaded = false;
        }
        Throwable completionError = unloaded ? cancellation : new PreparedFilesInUseException(
                "Could not unload cancelled SkyWars world " + prepared.worldKey(), cancellation);
        result.completeExceptionally(completionError);
        return unloaded && result.isDone();
    }

    private static World createPreparedWorld(PreparedMap prepared) throws IOException {
        MapTemplate template = prepared.template();
        NamespacedKey worldKey = prepared.worldKey();
        org.bukkit.Location forcedSpawn = template.getWaitingSpawn(null);
        World world = WorldCreator.ofKey(worldKey)
                .environment(World.Environment.NORMAL)
                .generateStructures(false)
                // Recovered level.dat files do not consistently contain a usable
                // SpawnX/Y/Z. Avoid Paper's synchronous safe-spawn scan.
                .forcedSpawnPosition(Position.block(
                        forcedSpawn.getBlockX(), forcedSpawn.getBlockY(), forcedSpawn.getBlockZ()),
                        forcedSpawn.getYaw(), forcedSpawn.getPitch())
                .generator(new VoidChunkGenerator())
                .createWorld();
        if (world == null) throw new IOException("Paper returned no world for " + worldKey);

        Path expected = prepared.destination().toPath().toAbsolutePath().normalize();
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
        return world;
    }

    public static void discardPrepared(PreparedMap prepared) {
        try {
            if (prepared.destination().exists()) FileUtils.deleteDirectory(prepared.destination());
        } catch (IOException error) {
            LOGGER.warning("Could not delete prepared SkyWars files: " + error.getMessage());
        }
    }

    /** Unload a world registered by loadPrepared when game construction fails; filesystem cleanup stays async. */
    public static boolean discardLoadedWorld(UUID gameId) {
        GameMap map = LOADED_MAPS.get(gameId);
        if (map == null) return true;
        World world = Bukkit.getWorld(map.world().getKey());
        if (world != null && (!world.getPlayers().isEmpty() || !Bukkit.unloadWorld(world, false))) return false;
        LOADED_MAPS.remove(gameId, map);
        return true;
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

    private static void requirePrimaryThread(String action) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("SkyWars worlds must be " + action + " on the server thread");
        }
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
            requireSolidTerrain(world, spawn, "island spawn");
        }
        ensureSafeWaitingArea(world, template);
    }

    private static Set<ChunkCoordinate> validationChunks(MapTemplate template) {
        Set<ChunkCoordinate> chunks = new LinkedHashSet<>();
        for (org.bukkit.Location spawn : template.getSpawns(null)) {
            chunks.add(ChunkCoordinate.fromBlock(spawn.getBlockX(), spawn.getBlockZ()));
        }
        org.bukkit.Location waiting = template.getWaitingSpawn(null);
        chunks.add(ChunkCoordinate.fromBlock(waiting.getBlockX(), waiting.getBlockZ()));
        int floorY = waiting.getBlockY() - 1;
        for (TemporaryWaitingPlatform.BlockPosition block
                : TemporaryWaitingPlatform.blocks(waiting.getBlockX(), floorY, waiting.getBlockZ())) {
            chunks.add(ChunkCoordinate.fromBlock(block.x(), block.z()));
        }
        for (TemporaryWaitingPlatform.BlockPosition block
                : TemporaryWaitingPlatform.guardrails(waiting.getBlockX(), floorY, waiting.getBlockZ())) {
            chunks.add(ChunkCoordinate.fromBlock(block.x(), block.z()));
        }
        for (TemporaryWaitingPlatform.BlockPosition block
                : TemporaryWaitingPlatform.frame(waiting.getBlockX(), floorY, waiting.getBlockZ())) {
            chunks.add(ChunkCoordinate.fromBlock(block.x(), block.z()));
        }
        return chunks;
    }

    private static void failAsyncLoad(PreparedMap prepared, CompletableFuture<GameMap> result,
            Throwable error, long createWorldMillis, long chunkPreloadMillis, long validationMillis) {
        World partial = Bukkit.getWorld(prepared.worldKey());
        Throwable completionError = error;
        if (partial != null) {
            if (!partial.getPlayers().isEmpty() || !Bukkit.unloadWorld(partial, false)) {
                completionError = new PreparedFilesInUseException(
                        "Could not unload failed SkyWars world " + prepared.worldKey(), error);
            }
        }
        logAsyncLoadTimings(prepared, createWorldMillis, chunkPreloadMillis, validationMillis, completionError);
        result.completeExceptionally(completionError);
    }

    private static void logAsyncLoadTimings(PreparedMap prepared, long createWorldMillis, long chunkPreloadMillis,
            long validationMillis, Throwable failure) {
        String timings = "SkyWars arena " + prepared.gameId()
                + " (create_world_ms=" + createWorldMillis
                + ", chunk_preload_ms=" + chunkPreloadMillis
                + ", validation_ms=" + validationMillis + ")";
        if (failure == null) LOGGER.info("Prepared " + timings);
        else LOGGER.warning("Failed to prepare " + timings + ": " + failure.getMessage());
        if (createWorldMillis > VALIDATION_BUDGET_MILLIS) {
            LOGGER.warning("SkyWars world creation exceeded the " + VALIDATION_BUDGET_MILLIS
                    + "ms main-thread budget (create_world_ms=" + createWorldMillis + ")");
        }
        if (validationMillis > VALIDATION_BUDGET_MILLIS) {
            LOGGER.warning("SkyWars validation exceeded the " + VALIDATION_BUDGET_MILLIS
                    + "ms main-thread budget (validation_ms=" + validationMillis + ")");
        }
    }

    private static long elapsedMillis(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private static void ensureSafeWaitingArea(World world, MapTemplate template) throws IOException {
        org.bukkit.Location waiting = template.getWaitingSpawn(world);
        if (hasSolidTerrain(world, waiting)) return;

        int floorY = waiting.getBlockY() - 1;
        for (TemporaryWaitingPlatform.BlockPosition block
                : TemporaryWaitingPlatform.blocks(waiting.getBlockX(), floorY, waiting.getBlockZ())) {
            world.getBlockAt(block.x(), block.y(), block.z()).setType(
                    TemporaryWaitingPlatform.floorMaterial(template.getName(), waiting.getBlockX(),
                            waiting.getBlockZ(), block), false);
        }
        for (TemporaryWaitingPlatform.BlockPosition block
                : TemporaryWaitingPlatform.guardrails(waiting.getBlockX(), floorY, waiting.getBlockZ())) {
            world.getBlockAt(block.x(), block.y(), block.z()).setType(
                    TemporaryWaitingPlatform.accentMaterial(template.getName()), false);
        }
        for (TemporaryWaitingPlatform.BlockPosition block
                : TemporaryWaitingPlatform.frame(waiting.getBlockX(), floorY, waiting.getBlockZ())) {
            world.getBlockAt(block.x(), block.y(), block.z()).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
        }
        requireSolidTerrain(world, waiting, "generated temporary waiting spawn");
        SkyWars.getInstance().getLogger().warning("Generated the cookie-themed waiting podium for "
                + template.getDisplayName() + " because the archived lobby platform is missing");
    }

    private static void requireSolidTerrain(World world, org.bukkit.Location location, String label)
            throws IOException {
        if (hasSolidTerrain(world, location)) return;
        throw new IOException("No solid terrain below configured " + label + " "
                + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ());
    }

    private static boolean hasSolidTerrain(World world, org.bukkit.Location location) {
        for (int offset = 1; offset <= 5; offset++) {
            if (world.getBlockAt(location.getBlockX(), location.getBlockY() - offset, location.getBlockZ())
                    .getType().isSolid()) return true;
        }
        return false;
    }

    private record ChunkCoordinate(int x, int z) {
        private static ChunkCoordinate fromBlock(int blockX, int blockZ) {
            return new ChunkCoordinate(Math.floorDiv(blockX, 16), Math.floorDiv(blockZ, 16));
        }
    }

    private static final class VoidChunkGenerator extends ChunkGenerator {
    }
}
