package com.cookiebuild.skywars.game;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.lobby.LobbyManager;
import com.cookiebuild.cookiedough.lobby.LobbyScoreboard;
import com.cookiebuild.cookiedough.model.Match;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerState;
import com.cookiebuild.cookiedough.service.MatchService;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.kit.SkyWarsKit;
import com.cookiebuild.skywars.loot.LootTable;
import com.cookiebuild.skywars.map.GameMap;
import com.cookiebuild.skywars.map.MapManager;
import com.cookiebuild.skywars.map.MapTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

public final class SkyWarsGame extends Game {
    private static final int MAX_RUNNING_SECONDS = 12 * 60;
    private static final int BORDER_SHRINK_START_SECONDS = 6 * 60;

    private final GameMap map;
    private final MatchService matchService = new MatchService(null);
    private final MinigameProgressionService progression = CookieDough.createMinigameProgressionService();
    private final SkyWarsStats stats = new SkyWarsStats();
    private final SkyWarsScoreboard scoreboard = new SkyWarsScoreboard();
    private final Set<UUID> participantIds = new LinkedHashSet<>();
    private final Map<UUID, CookiePlayer> participantPlayers = new LinkedHashMap<>();
    private final Map<UUID, Integer> spawnAssignments = new HashMap<>();
    private final Set<Integer> assignedSpawns = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Set<String> filledChests = new HashSet<>();
    private final Set<String> placedBlocks = new HashSet<>();
    private final Map<UUID, Integer> placements = new HashMap<>();
    private final Map<UUID, Integer> survivalSeconds = new HashMap<>();

    private Match match;
    private int runningSeconds;
    private boolean borderShrinking;
    private boolean outcomeHandled;
    private boolean cleanupStarted;
    private BukkitTask cleanupTask;

    public SkyWarsGame() {
        super("SkyWars");
        START_DELAY_SECONDS = 30;
        QUICK_START_DELAY_SECONDS = 10;
        try {
            MapTemplate template = MapManager.selectTemplate();
            this.map = MapManager.loadMap(getGameId(), template);
            setCapacity(template.getCapacity());
        } catch (IOException | RuntimeException error) {
            throw new IllegalStateException("SkyWars map preparation failed: " + error.getMessage(), error);
        }
    }

    @Override
    public void registerANewGame() {
        // This must stay a constant-time promotion. Constructing a SkyWarsGame
        // here synchronously loads a world and used to freeze the queue for 3-4s.
        SkyWars.activateNextGame();
    }

    @Override
    public synchronized boolean addPlayer(CookiePlayer cookiePlayer) {
        if (cookiePlayer == null || map.world() == null || getState() != GameState.OPEN) {
            return false;
        }
        Player player = cookiePlayer.getPlayer();
        if (!super.addPlayer(cookiePlayer)) {
            return false;
        }

        int spawn = claimSpawn();
        if (spawn < 0) {
            super.removePlayer(cookiePlayer, "no_spawn");
            return false;
        }
        UUID playerId = player.getUniqueId();
        // MatchService resolves this identifier inside its own transaction; admission
        // remains non-blocking because CookieDough already proved player data ready.
        participantIds.add(playerId);
        participantPlayers.put(playerId, cookiePlayer);
        spawnAssignments.put(playerId, spawn);
        alive.add(playerId);
        stats.register(playerId);
        try {
            teleportToGame(cookiePlayer);
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.choose"), NamedTextColor.YELLOW));
            player.showTitle(Title.title(
                    Component.text(SkyWars.message(player, "skywars.waiting.title"), NamedTextColor.GOLD,
                            TextDecoration.BOLD),
                    Component.text(SkyWars.message(player, "skywars.waiting.subtitle"), NamedTextColor.YELLOW),
                    Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(3), Duration.ofMillis(500))));
            return true;
        } catch (RuntimeException error) {
            participantIds.remove(playerId);
            participantPlayers.remove(playerId);
            spawnAssignments.remove(playerId);
            assignedSpawns.remove(spawn);
            alive.remove(playerId);
            super.removePlayer(cookiePlayer, "admission_failed");
            cookiePlayer.setState(PlayerState.LOBBY);
            SkyWars.getInstance().getLogger().severe("Rolled back failed SkyWars admission: " + error.getMessage());
            return false;
        }
    }

    private int claimSpawn() {
        for (int index = 0; index < getCapacity(); index++) {
            if (assignedSpawns.add(index)) {
                return index;
            }
        }
        return -1;
    }

    @Override
    protected void teleportToGame(CookiePlayer cookiePlayer) {
        Player player = cookiePlayer.getPlayer();
        cookiePlayer.resetPlayer();
        player.setFallDistance(0);
        if (getState() == GameState.RUNNING) {
            Integer spawn = spawnAssignments.get(player.getUniqueId());
            if (spawn == null) {
                throw new IllegalStateException("Player has no SkyWars island assignment");
            }
            player.teleport(map.template().getSpawn(map.world(), spawn));
            player.setGameMode(GameMode.SURVIVAL);
            SkyWarsKit kit = SkyWars.getInstance().getKitManager().equip(player);
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.equipped",
                    kit.displayName(), SkyWars.message(player, "skywars.kit." + kit.key() + ".description")), NamedTextColor.AQUA));
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            player.teleport(map.template().getWaitingSpawn(map.world()));
            player.getInventory().setItem(0, kitSelector());
        }
    }

    private ItemStack kitSelector() {
        ItemStack selector = new ItemStack(Material.COOKIE);
        ItemMeta meta = selector.getItemMeta();
        meta.displayName(Component.text("Kit Selector", NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text("Right-click to select your kit", NamedTextColor.GRAY),
                Component.text("Java inventory or Bedrock form", NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(SkyWars.getInstance().getKitSelectorKey(), PersistentDataType.BYTE, (byte) 1);
        selector.setItemMeta(meta);
        return selector;
    }

    @Override
    public void startGame() {
        if (map.world() == null || getPlayers().size() < getMinimumPlayers()) {
            return;
        }
        prepareBorder();
        super.startGame();
        try {
            match = matchService.startMatchByPlayerIds("SkyWars", participantIds);
        } catch (RuntimeException error) {
            match = null;
            SkyWars.getInstance().getLogger().severe("SkyWars will continue without match telemetry: " + error.getMessage());
        }
    }

    private void prepareBorder() {
        List<Location> spawns = map.template().getSpawns(map.world());
        double centerX = spawns.stream().mapToDouble(Location::getX).average().orElse(0.0);
        double centerZ = spawns.stream().mapToDouble(Location::getZ).average().orElse(0.0);
        double radius = spawns.stream().mapToDouble(location -> Math.hypot(location.getX() - centerX, location.getZ() - centerZ))
                .max().orElse(50.0);
        WorldBorder border = map.world().getWorldBorder();
        border.setCenter(centerX, centerZ);
        border.setSize(Math.max(64.0, radius * 2.0 + 32.0));
        border.setDamageAmount(1.0);
        border.setDamageBuffer(2.0);
    }

    @Override
    public void tick() {
        super.tick();
        updateDisplay();
        if (getState() != GameState.RUNNING) {
            return;
        }
        runningSeconds++;
        if (!borderShrinking && runningSeconds >= BORDER_SHRINK_START_SECONDS) {
            borderShrinking = true;
            map.world().getWorldBorder().changeSize(16.0, MAX_RUNNING_SECONDS - BORDER_SHRINK_START_SECONDS);
            getPlayers().forEach(player -> player.getPlayer().sendMessage(Component.text(
                    SkyWars.message(player.getPlayer(), "skywars.sudden_death"), NamedTextColor.RED)));
        }
        if (runningSeconds >= MAX_RUNNING_SECONDS) {
            endGame(selectTimeoutWinner());
        } else {
            checkWinner();
        }
    }

    private CookiePlayer selectTimeoutWinner() {
        return alive.stream().map(participantPlayers::get).filter(java.util.Objects::nonNull)
                .max(java.util.Comparator
                        .comparingDouble((CookiePlayer player) -> player.getPlayer().getHealth())
                        .thenComparingInt(player -> stats.snapshot(player.getPlayer().getUniqueId()).kills()))
                .orElse(null);
    }

    private void updateDisplay() {
        String stateKey = switch (getState()) {
            case LOADING -> "skywars.state.loading";
            case OPEN -> "skywars.state.waiting";
            case STARTING -> "skywars.state.starting";
            case RUNNING -> "skywars.state.running";
            case FINISHED -> "skywars.state.finished";
        };
        int timeLeft = Math.max(0, MAX_RUNNING_SECONDS - runningSeconds);
        for (CookiePlayer cookiePlayer : getPlayers()) {
            Player player = cookiePlayer.getPlayer();
            String state = SkyWars.message(player, stateKey);
            if (getState() == GameState.OPEN) {
                WaitingStatus waiting = WaitingStatus.from(getPlayers().size(), getCapacity(), getMinimumPlayers(),
                        getStartTimer(), inQuickStart ? QUICK_START_DELAY_SECONDS : START_DELAY_SECONDS);
                String status = waiting.isCountingDown()
                        ? SkyWars.message(player, "skywars.waiting.starting", waiting.secondsRemaining(),
                                waiting.players(), waiting.capacity())
                        : SkyWars.message(player, "skywars.waiting.players", waiting.players(), waiting.capacity(),
                                waiting.morePlayersNeeded());
                player.sendActionBar(Component.text(status, waiting.isCountingDown()
                        ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            } else {
                player.sendActionBar(Component.text(state + " · " + alive.size() + " alive", NamedTextColor.YELLOW));
            }
            SkyWarsStats.Snapshot snapshot = stats.snapshot(player.getUniqueId());
            scoreboard.update(player, List.of(
                    "§6Map: §f" + map.template().getName(),
                    "§6State: §f" + state,
                    " ",
                    "§6Alive: §a" + alive.size() + "/" + participantIds.size(),
                    "§6Kills: §a" + snapshot.kills(),
                    "§6Time: §f" + String.format("%d:%02d", timeLeft / 60, timeLeft % 60)));
        }
    }

    public boolean isAlive(Player player) {
        return player != null && alive.contains(player.getUniqueId());
    }

    public boolean ownsWorld(World world) {
        return world != null && world.getKey().equals(map.world().getKey());
    }

    public Location getPregameAnchor(Player player) {
        if (getState() == GameState.OPEN) {
            return map.template().getWaitingSpawn(map.world());
        }
        Integer spawn = spawnAssignments.get(player.getUniqueId());
        return spawn == null ? null : map.template().getSpawn(map.world(), spawn);
    }

    public double getKillY() {
        return map.template().getKillY();
    }

    public void eliminate(Player victim, Player attacker, String reason) {
        UUID victimId = victim.getUniqueId();
        if (getState() != GameState.RUNNING || !alive.remove(victimId)) {
            return;
        }
        UUID attackerId = attacker != null && alive.contains(attacker.getUniqueId())
                ? attacker.getUniqueId() : null;
        stats.recordElimination(victimId, attackerId);
        placements.put(victimId, alive.size() + 1);
        survivalSeconds.put(victimId, runningSeconds);
        CookiePlayer cookiePlayer = participantPlayers.get(victimId);
        if (cookiePlayer != null) {
            cookiePlayer.setState(PlayerState.SPECTATING);
        }
        victim.getInventory().clear();
        victim.getInventory().setArmorContents(null);
        victim.setFireTicks(0);
        victim.setGameMode(GameMode.SPECTATOR);
        victim.teleport(map.template().getSpectatorSpawn(map.world()));
        victim.showTitle(Title.title(
                Component.text("ELIMINATED", NamedTextColor.RED, TextDecoration.BOLD),
                Component.text(reason, NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(250), Duration.ofSeconds(2), Duration.ofMillis(500))));
        for (CookiePlayer participant : getPlayers()) {
            participant.getPlayer().sendMessage(Component.text(attacker == null
                    ? SkyWars.message(participant.getPlayer(), "skywars.broadcast.fell", victim.getName())
                    : SkyWars.message(participant.getPlayer(), "skywars.broadcast.killed", victim.getName(), attacker.getName()),
                    NamedTextColor.YELLOW));
        }
        checkWinner();
    }

    private void checkWinner() {
        if (getState() != GameState.RUNNING) {
            return;
        }
        if (alive.size() <= 1) {
            CookiePlayer winner = alive.stream().map(participantPlayers::get).findFirst().orElse(null);
            endGame(winner);
        }
    }

    private void endGame(CookiePlayer winner) {
        if (outcomeHandled || getState() == GameState.FINISHED) {
            return;
        }
        outcomeHandled = true;
        setState(GameState.FINISHED);
        UUID winnerId = winner == null ? null : winner.getPlayer().getUniqueId();
        if (winnerId != null) {
            placements.put(winnerId, 1);
            survivalSeconds.put(winnerId, runningSeconds);
        }
        // A timeout can end while several players are still alive. Record a
        // deterministic runner-up placement instead of leaving those players at 0.
        for (UUID survivorId : alive) {
            if (!survivorId.equals(winnerId)) {
                placements.putIfAbsent(survivorId, winnerId == null ? 1 : 2);
                survivalSeconds.putIfAbsent(survivorId, runningSeconds);
            }
        }
        for (Map.Entry<UUID, CookiePlayer> entry : participantPlayers.entrySet()) {
            Player player = entry.getValue().getPlayer();
            if (!player.isOnline()) {
                continue;
            }
            boolean won = entry.getKey().equals(winnerId);
            player.showTitle(Title.title(
                    Component.text(SkyWars.message(player, won ? "skywars.outcome.victory" : "skywars.outcome.game_over"),
                            won ? NamedTextColor.GOLD : NamedTextColor.RED, TextDecoration.BOLD),
                    Component.text(winner == null
                            ? SkyWars.message(player, "skywars.outcome.draw")
                            : SkyWars.message(player, "skywars.outcome.winner", winner.getPlayer().getName()), NamedTextColor.GRAY)));
        }
        offerReplay();
        persistOutcome(winnerId, false, true);
        cleanupTask = Bukkit.getScheduler().runTaskLater(SkyWars.getInstance(), this::cleanup, 8L * 20L);
    }

    private void persistOutcome(UUID winnerId, boolean interrupted, boolean rewardPlayers) {
        List<MatchService.Performance> performances = participantIds.stream().map(playerId -> {
            SkyWarsStats.Snapshot snapshot = stats.snapshot(playerId);
            return new MatchService.Performance(playerId, snapshot.kills(), snapshot.deaths(), 0, Map.of(
                    "map", map.template().getName(),
                    "placement", placements.getOrDefault(playerId, 0),
                    "survivalSeconds", survivalSeconds.getOrDefault(playerId, runningSeconds),
                    "chestsOpened", snapshot.chestsOpened(),
                    "blocksPlaced", snapshot.blocksPlaced(),
                    "interrupted", interrupted));
        }).toList();
        if (match != null) {
            try {
                List<UUID> winners = winnerId == null ? List.of() : List.of(winnerId);
                matchService.completeMatchByWinnerIds(match, winners, performances);
            } catch (RuntimeException error) {
                SkyWars.getInstance().getLogger().severe("Could not persist SkyWars match outcome: " + error.getMessage());
            }
        }
        if (!rewardPlayers) {
            return;
        }
        String sourceId = match == null ? getGameId().toString() : match.getId().toString();
        for (UUID playerId : participantIds) {
            SkyWarsStats.Snapshot snapshot = stats.snapshot(playerId);
            boolean won = playerId.equals(winnerId);
            int rewardedKills = Math.min(snapshot.kills(),
                    SkyWars.getInstance().getConfig().getInt("rewards.rewarded-kills-cap", 5));
            int coins = SkyWars.getInstance().getConfig().getInt("rewards.participation-coins", 5)
                    + rewardedKills * SkyWars.getInstance().getConfig().getInt("rewards.kill-coins", 2)
                    + (won ? SkyWars.getInstance().getConfig().getInt("rewards.victory-coins", 25) : 0);
            int xp = SkyWars.getInstance().getConfig().getInt("rewards.participation-xp", 15)
                    + rewardedKills * SkyWars.getInstance().getConfig().getInt("rewards.kill-xp", 10)
                    + (won ? SkyWars.getInstance().getConfig().getInt("rewards.victory-xp", 100) : 0);
            try {
                progression.applyReward(playerId, "skywars", xp, coins, "match:" + sourceId + ":skywars-reward");
                LobbyScoreboard.invalidatePlayerCache(playerId);
                CookieDough.getInstance().getGoalTracker().recordMatch(playerId, "SkyWars", won, snapshot.kills());
                Player player = Bukkit.getPlayer(playerId);
                if (player != null) {
                    player.sendMessage(Component.text(SkyWars.message(player, "skywars.reward", coins, xp), NamedTextColor.GREEN));
                }
            } catch (RuntimeException error) {
                SkyWars.getInstance().getLogger().warning("Could not reward SkyWars player " + playerId + ": " + error.getMessage());
            }
        }
    }

    public boolean fillChest(Inventory inventory, Location location, Player opener) {
        if (getState() != GameState.RUNNING || inventory == null || location == null || !ownsWorld(location.getWorld())) {
            return false;
        }
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        if (!filledChests.add(key)) {
            return false;
        }
        boolean middle = map.template().getSpawns(map.world()).stream()
                .allMatch(spawn -> spawn.distanceSquared(location) > map.template().getMiddleRadius() * map.template().getMiddleRadius());
        int rolls = SkyWars.getInstance().getConfig().getInt(middle ? "loot.middle-rolls" : "loot.normal-rolls", middle ? 8 : 5);
        List<ItemStack> loot = LootTable.roll(middle, rolls, ThreadLocalRandom.current());
        inventory.clear();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }
        java.util.Collections.shuffle(slots);
        for (int index = 0; index < Math.min(loot.size(), slots.size()); index++) {
            inventory.setItem(slots.get(index), loot.get(index));
        }
        stats.recordChest(opener.getUniqueId());
        return true;
    }

    public void recordBlockPlaced(Player player) {
        stats.recordBlockPlaced(player.getUniqueId());
    }

    public void trackPlacedBlock(Location location) {
        placedBlocks.add(blockKey(location));
    }

    public boolean consumePlacedBlock(Location location) {
        return placedBlocks.remove(blockKey(location));
    }

    public boolean isPlacedBlock(Location location) {
        return placedBlocks.contains(blockKey(location));
    }

    private static String blockKey(Location location) {
        return location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    @Override
    public boolean isGameEnded() {
        return getState() == GameState.FINISHED;
    }

    @Override
    public boolean addPlayerToAvailableTeam(CookiePlayer player) {
        return addPlayer(player);
    }

    @Override
    public void removePlayer(CookiePlayer cookiePlayer) {
        removePlayer(cookiePlayer, "left_game");
    }

    @Override
    public synchronized void removePlayer(CookiePlayer cookiePlayer, String reason) {
        if (cookiePlayer == null || !getPlayers().contains(cookiePlayer)) {
            return;
        }
        UUID playerId = cookiePlayer.getPlayer().getUniqueId();
        boolean running = getState() == GameState.RUNNING;
        if (running && alive.remove(playerId)) {
            stats.recordElimination(playerId, null);
            placements.put(playerId, alive.size() + 1);
            survivalSeconds.put(playerId, runningSeconds);
        }
        super.removePlayer(cookiePlayer, reason);
        Integer spawn = spawnAssignments.remove(playerId);
        if (spawn != null) {
            assignedSpawns.remove(spawn);
        }
        scoreboard.remove(cookiePlayer.getPlayer());
        if (!running) {
            participantIds.remove(playerId);
            participantPlayers.remove(playerId);
            alive.remove(playerId);
        } else {
            checkWinner();
        }
    }

    public void shutdown() {
        if (getState() == GameState.RUNNING && !outcomeHandled) {
            outcomeHandled = true;
            persistOutcome(null, true, false);
        }
        setState(GameState.FINISHED);
        cleanup();
    }

    private void cleanup() {
        if (cleanupStarted) {
            return;
        }
        cleanupStarted = true;
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        for (CookiePlayer cookiePlayer : new ArrayList<>(getPlayers())) {
            Player player = cookiePlayer.getPlayer();
            try {
                if (player.isOnline()) {
                    LobbyManager.teleportPlayerToLobby(cookiePlayer);
                }
            } catch (RuntimeException error) {
                World fallback = Bukkit.getWorld(org.bukkit.NamespacedKey.minecraft("overworld"));
                if (fallback != null && player.isOnline()) {
                    cookiePlayer.resetPlayer();
                    cookiePlayer.setState(PlayerState.LOBBY);
                    player.teleport(fallback.getSpawnLocation());
                }
            } finally {
                if (getPlayers().contains(cookiePlayer)) {
                    super.removePlayer(cookiePlayer, "game_cleanup");
                }
                scoreboard.remove(player);
            }
        }
        if (!MapManager.unloadMap(getGameId())) {
            SkyWars.getInstance().getLogger().warning("SkyWars map cleanup remains pending for " + getGameId());
        }
        alive.clear();
        participantIds.clear();
        participantPlayers.clear();
        spawnAssignments.clear();
        assignedSpawns.clear();
        filledChests.clear();
        placedBlocks.clear();
        placements.clear();
        survivalSeconds.clear();
        stats.clear();
        scoreboard.clear();
        GameManager.removeGame(this);
        SkyWars.requestStandbyRefill();
    }
}
