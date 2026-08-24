package com.cookiebuild.skywars.game;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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
import com.cookiebuild.cookiedough.game.PlayerActivitySnapshot;
import com.cookiebuild.cookiedough.game.ReconnectableGame;
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
import com.cookiebuild.skywars.loot.ChestTier;
import com.cookiebuild.skywars.map.GameMap;
import com.cookiebuild.skywars.map.MapManager;
import com.cookiebuild.skywars.map.MapTemplate;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;

public final class SkyWarsGame extends Game implements ReconnectableGame {
    private static final long RECONNECT_GRACE_MILLIS = Duration.ofSeconds(60).toMillis();
    private final GameMap map;
    private final SkyWarsPacing pacing;
    private final MatchService matchService = new MatchService(null);
    private final MinigameProgressionService progression = CookieDough.createMinigameProgressionService();
    private final SkyWarsStats stats = new SkyWarsStats();
    private final SkyWarsScoreboard scoreboard = new SkyWarsScoreboard();
    private final Set<UUID> participantIds = new LinkedHashSet<>();
    private final Map<UUID, CookiePlayer> participantPlayers = new LinkedHashMap<>();
    private final Map<UUID, Integer> spawnAssignments = new HashMap<>();
    private final Set<Integer> assignedSpawns = new HashSet<>();
    private final Set<UUID> alive = new HashSet<>();
    private final Map<String, Integer> filledChests = new HashMap<>();
    private final Set<String> placedBlocks = new HashSet<>();
    private final Map<UUID, Integer> placements = new HashMap<>();
    private final Map<UUID, Integer> survivalSeconds = new HashMap<>();
    private final Map<UUID, Integer> buildLimitWarnings = new HashMap<>();
    private final Map<UUID, Long> disconnectedAt = new HashMap<>();
    private final Map<UUID, PlayerActivitySnapshot> reconnectSnapshots = new HashMap<>();
    private final Set<UUID> trackerGuidancePlayers = new HashSet<>();

    private CompletableFuture<Match> matchFuture = CompletableFuture.completedFuture(null);
    private int runningSeconds;
    private int chestGeneration;
    private final int buildCeilingY;
    private boolean borderShrinking;
    private boolean refillTriggered;
    private boolean trackersGiven;
    private boolean timedOut;
    private boolean outcomeHandled;
    private boolean cleanupStarted;
    private BukkitTask cleanupTask;

    public SkyWarsGame(UUID gameId, GameMap preparedMap) {
        super("SkyWars", gameId);
        START_DELAY_SECONDS = 30;
        QUICK_START_DELAY_SECONDS = 10;
        this.map = java.util.Objects.requireNonNull(preparedMap, "preparedMap");
        MapTemplate template = map.template();
        setCapacity(template.getCapacity());
        this.pacing = readPacing();
        this.buildCeilingY = (int) Math.floor(template.getSpawns(map.world()).stream()
                .mapToDouble(Location::getY).max().orElse(64.0)) + pacing.buildCeilingOffset();
    }

    private static SkyWarsPacing readPacing() {
        var config = SkyWars.getInstance().getConfig();
        return new SkyWarsPacing(
                config.getInt("gameplay.max-seconds", 360),
                config.getInt("gameplay.border-start-seconds", 90),
                config.getInt("gameplay.refill-seconds", 150),
                config.getInt("gameplay.tracker-seconds", 180),
                config.getInt("gameplay.build-ceiling-offset", 24));
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
        SkyWars.getInstance().getKitManager().preload(playerId);
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
            teleportPlayerSafely(player, map.template().getSpawn(map.world(), spawn));
            player.setGameMode(GameMode.SURVIVAL);
            SkyWarsKit kit = SkyWars.getInstance().getKitManager().equip(player);
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.equipped",
                    SkyWars.kitName(player, kit),
                    SkyWars.message(player, "skywars.kit." + kit.key() + ".description")), NamedTextColor.AQUA));
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            teleportPlayerSafely(player, map.template().getWaitingSpawn(map.world()));
            player.getInventory().setItem(0, kitSelector(player));
        }
    }

    @Override
    public boolean supportsSpectating() {
        return true;
    }

    @Override
    protected Location spectatorDestination(CookiePlayer cookiePlayer) {
        return map == null || map.world() == null ? null : map.template().getSpectatorSpawn(map.world());
    }

    private ItemStack kitSelector(Player player) {
        ItemStack selector = new ItemStack(Material.COOKIE);
        ItemMeta meta = selector.getItemMeta();
        meta.displayName(Component.text(SkyWars.message(player, "skywars.kit.selector"), NamedTextColor.GOLD));
        meta.lore(List.of(
                Component.text(SkyWars.message(player, "skywars.kit.selector.use"), NamedTextColor.GRAY),
                Component.text(SkyWars.message(player, "skywars.kit.selector.platforms"), NamedTextColor.GRAY)));
        meta.getPersistentDataContainer().set(SkyWars.getInstance().getKitSelectorKey(), PersistentDataType.BYTE, (byte) 1);
        selector.setItemMeta(meta);
        return selector;
    }

    @Override
    public void startGame() {
        if (map.world() == null || getPlayers().size() < getMinimumPlayers()) {
            return;
        }
        List<UUID> pendingProfiles = participantIds.stream()
                .filter(playerId -> !SkyWars.getInstance().getKitManager().isProfileReady(playerId)).toList();
        if (!pendingProfiles.isEmpty()) {
            pendingProfiles.forEach(SkyWars.getInstance().getKitManager()::preload);
            getPlayers().forEach(player -> player.getPlayer().sendMessage(Component.text(
                    SkyWars.message(player.getPlayer(), "skywars.kit.waiting_profile"), NamedTextColor.YELLOW)));
            return;
        }
        assignCompactSpawns();
        prepareBorder();
        super.startGame();
        Set<UUID> startingPlayers = Set.copyOf(participantIds);
        matchFuture = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(SkyWars.getInstance(), () -> {
            try {
                matchFuture.complete(matchService.startMatchByPlayerIds("SkyWars", startingPlayers));
            } catch (RuntimeException error) {
                SkyWars.getInstance().getLogger().severe(
                        "SkyWars will continue without match telemetry: " + error.getMessage());
                matchFuture.complete(null);
            }
        });
    }

    private void assignCompactSpawns() {
        List<Location> authored = map.template().getSpawns(map.world());
        List<CompactSpawnSelector.Point> points = authored.stream()
                .map(location -> new CompactSpawnSelector.Point(
                        location.getX(), location.getY(), location.getZ()))
                .toList();
        List<Integer> selected = CompactSpawnSelector.select(
                points, participantIds.size(), getGameId().hashCode());
        assignedSpawns.clear();
        spawnAssignments.clear();
        int index = 0;
        for (UUID playerId : participantIds) {
            int spawn = selected.get(index++);
            spawnAssignments.put(playerId, spawn);
            assignedSpawns.add(spawn);
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
        expireReconnectReservations();
        runningSeconds++;
        if (!borderShrinking && runningSeconds >= pacing.borderStartSeconds()) {
            borderShrinking = true;
            map.world().getWorldBorder().changeSize(16.0, pacing.maxSeconds() - pacing.borderStartSeconds());
            getPlayers().forEach(player -> player.getPlayer().sendMessage(Component.text(
                    SkyWars.message(player.getPlayer(), "skywars.sudden_death"), NamedTextColor.RED)));
        }
        if (!refillTriggered && runningSeconds >= pacing.refillSeconds()) {
            refillTriggered = true;
            chestGeneration++;
            getPlayers().forEach(player -> player.getPlayer().sendMessage(Component.text(
                    SkyWars.message(player.getPlayer(), "skywars.refill"), NamedTextColor.AQUA)));
        }
        if (!trackersGiven && (runningSeconds >= pacing.trackerSeconds() || alive.size() <= 2)) {
            trackersGiven = true;
            giveTrackers();
        }
        if (trackersGiven) {
            updateTrackerTargets();
        }
        if (runningSeconds >= pacing.maxSeconds()) {
            timedOut = true;
            endGame(selectTimeoutWinner());
        } else {
            checkWinner();
        }
    }

    private CookiePlayer selectTimeoutWinner() {
        UUID winnerId = TimeoutStanding.winner(alive.stream().map(participantPlayers::get)
                .filter(java.util.Objects::nonNull)
                .map(player -> {
                    UUID playerId = player.getPlayer().getUniqueId();
                    SkyWarsStats.Snapshot snapshot = stats.snapshot(playerId);
                    return new TimeoutStanding(playerId, snapshot.kills(), player.getPlayer().getHealth(),
                            snapshot.chestTiers().getOrDefault(ChestTier.MID, 0), snapshot.chestsOpened());
                }).toList());
        return winnerId == null ? null : participantPlayers.get(winnerId);
    }

    private void giveTrackers() {
        for (UUID playerId : alive) {
            CookiePlayer cookiePlayer = participantPlayers.get(playerId);
            if (cookiePlayer == null || !cookiePlayer.getPlayer().isOnline()) continue;
            Player player = cookiePlayer.getPlayer();
            TrackerDelivery.Mode mode = TrackerDelivery.mode(
                    player.getInventory().contains(Material.COMPASS), player.getInventory().firstEmpty());
            if (mode == TrackerDelivery.Mode.GIVE_ITEM) {
                player.getInventory().setItem(player.getInventory().firstEmpty(), trackerItem(player));
            } else if (mode == TrackerDelivery.Mode.GUIDANCE) {
                trackerGuidancePlayers.add(playerId);
            }
            String key = mode == TrackerDelivery.Mode.GUIDANCE
                    ? "skywars.tracker.guidance_enabled" : "skywars.tracker.enabled";
            player.sendMessage(Component.text(SkyWars.message(player, key),
                    NamedTextColor.AQUA));
        }
    }

    private ItemStack trackerItem(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        ItemMeta meta = compass.getItemMeta();
        meta.displayName(Component.text(SkyWars.message(player, "skywars.tracker.name"), NamedTextColor.AQUA));
        meta.lore(List.of(Component.text(SkyWars.message(player, "skywars.tracker.description"),
                NamedTextColor.GRAY)));
        compass.setItemMeta(meta);
        return compass;
    }

    private void updateTrackerTargets() {
        for (UUID playerId : alive) {
            CookiePlayer source = participantPlayers.get(playerId);
            if (source == null || !source.getPlayer().isOnline()) continue;
            Player player = source.getPlayer();
            alive.stream().filter(other -> !other.equals(playerId)).map(participantPlayers::get)
                    .filter(java.util.Objects::nonNull).map(CookiePlayer::getPlayer).filter(Player::isOnline)
                    .min(java.util.Comparator.comparingDouble(other -> other.getLocation()
                            .distanceSquared(player.getLocation())))
                    .ifPresent(target -> {
                        player.setCompassTarget(target.getLocation());
                        if (!player.getInventory().contains(Material.COMPASS)
                                && player.getInventory().firstEmpty() >= 0) {
                            player.getInventory().setItem(player.getInventory().firstEmpty(), trackerItem(player));
                            trackerGuidancePlayers.remove(playerId);
                            player.sendMessage(Component.text(SkyWars.message(
                                    player, "skywars.tracker.enabled"), NamedTextColor.AQUA));
                        } else if (trackerGuidancePlayers.contains(playerId)) {
                            double deltaX = target.getLocation().getX() - player.getLocation().getX();
                            double deltaZ = target.getLocation().getZ() - player.getLocation().getZ();
                            TrackerDelivery.Direction direction = TrackerDelivery.relativeDirection(
                                    player.getLocation().getYaw(), deltaX, deltaZ);
                            int distance = (int) Math.round(Math.hypot(deltaX, deltaZ));
                            player.sendActionBar(Component.text(SkyWars.message(player, "skywars.tracker.guidance",
                                    SkyWars.message(player, "skywars.tracker.direction."
                                            + direction.name().toLowerCase(java.util.Locale.ROOT)),
                                    distance), NamedTextColor.AQUA));
                        }
                    });
        }
    }

    private void updateDisplay() {
        String stateKey = switch (getState()) {
            case LOADING -> "skywars.state.loading";
            case OPEN -> "skywars.state.waiting";
            case STARTING -> "skywars.state.starting";
            case RUNNING -> "skywars.state.running";
            case FINISHED -> "skywars.state.finished";
        };
        int timeLeft = Math.max(0, pacing.maxSeconds() - runningSeconds);
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
                player.sendActionBar(Component.text(SkyWars.message(player, "skywars.action.running",
                        state, alive.size()), NamedTextColor.YELLOW));
            }
            SkyWarsStats.Snapshot snapshot = stats.snapshot(player.getUniqueId());
            String objective = SkyWars.message(player, SkyWarsObjective.messageKey(snapshot));
            scoreboard.update(player, List.of(
                    "§6" + SkyWars.message(player, "skywars.scoreboard.map") + ": §f" + map.template().getDisplayName(),
                    "§6" + SkyWars.message(player, "skywars.scoreboard.state") + ": §f" + state,
                    " ",
                    "§e" + SkyWars.message(player, "skywars.scoreboard.objective") + ": §f" + objective,
                    "§6" + SkyWars.message(player, "skywars.scoreboard.alive") + ": §a" + alive.size() + "/" + participantIds.size(),
                    "§6" + SkyWars.message(player, "skywars.scoreboard.kills") + ": §a" + snapshot.kills(),
                    "§6" + SkyWars.message(player, "skywars.scoreboard.time") + ": §f"
                            + String.format("%d:%02d", timeLeft / 60, timeLeft % 60)));
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
        eliminate(victim, attacker, reason, "unknown");
    }

    public void eliminate(Player victim, Player attacker, String reason, String cause) {
        UUID victimId = victim.getUniqueId();
        if (getState() != GameState.RUNNING || !alive.remove(victimId)) {
            return;
        }
        UUID attackerId = attacker != null && alive.contains(attacker.getUniqueId())
                ? attacker.getUniqueId() : null;
        stats.recordElimination(victimId, attackerId, cause);
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
                Component.text(SkyWars.message(victim, "skywars.outcome.eliminated"),
                        NamedTextColor.RED, TextDecoration.BOLD),
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
        // A timeout can end while several players are still alive. Record tied
        // survivors instead of leaving them without a placement.
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
        Set<UUID> persistedParticipants = Set.copyOf(participantIds);
        List<MatchService.Performance> performances = createPerformances(persistedParticipants, interrupted);
        Map<UUID, SkyWarsStats.Snapshot> snapshots = persistedParticipants.stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(playerId -> playerId, stats::snapshot));
        CompletableFuture<Match> pendingMatch = matchFuture;
        int completedAtSeconds = runningSeconds;
        Bukkit.getScheduler().runTaskAsynchronously(SkyWars.getInstance(), () -> {
            Match durableMatch = null;
            try {
                durableMatch = pendingMatch.get(5, TimeUnit.SECONDS);
            } catch (Exception error) {
                logWarning("SkyWars match start did not complete: " + error.getMessage());
            }
            if (durableMatch != null) {
                try {
                    List<UUID> winners = winnerId == null ? List.of() : List.of(winnerId);
                    matchService.completeMatchByWinnerIds(durableMatch, winners, performances);
                } catch (RuntimeException error) {
                    SkyWars.getInstance().getLogger().severe(
                            "Could not persist SkyWars match outcome: " + error.getMessage());
                }
            }
            if (!rewardPlayers) return;
            String sourceId = durableMatch == null ? getGameId().toString() : durableMatch.getId().toString();
            for (UUID playerId : persistedParticipants) {
                SkyWarsStats.Snapshot snapshot = snapshots.get(playerId);
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
                    progression.applyReward(playerId, "skywars", xp, coins,
                            "match:" + sourceId + ":skywars-reward");
                    CookieDough.getInstance().getGoalTracker().recordMatch(
                            playerId, "SkyWars", won, snapshot.kills());
                    Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
                        LobbyScoreboard.invalidatePlayerCache(playerId);
                        Player player = Bukkit.getPlayer(playerId);
                        if (player != null) {
                            player.sendMessage(Component.text(SkyWars.message(player, "skywars.reward", coins, xp),
                                    NamedTextColor.GREEN));
                        }
                    });
                } catch (RuntimeException error) {
                    SkyWars.getInstance().getLogger().warning(
                            "Could not reward SkyWars player " + playerId + " after " + completedAtSeconds
                                    + "s: " + error.getMessage());
                }
            }
        });
    }

    private List<MatchService.Performance> createPerformances(Set<UUID> persistedParticipants, boolean interrupted) {
        return persistedParticipants.stream().map(playerId -> {
            SkyWarsStats.Snapshot snapshot = stats.snapshot(playerId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("map", map.template().getName());
            metadata.put("placement", placements.getOrDefault(playerId, 0));
            metadata.put("survivalSeconds", survivalSeconds.getOrDefault(playerId, runningSeconds));
            metadata.put("chestsOpened", snapshot.chestsOpened());
            metadata.put("blocksPlaced", snapshot.blocksPlaced());
            metadata.put("chestIsland", snapshot.chestTiers().getOrDefault(ChestTier.ISLAND, 0));
            metadata.put("chestIntermediate", snapshot.chestTiers().getOrDefault(ChestTier.INTERMEDIATE, 0));
            metadata.put("chestMid", snapshot.chestTiers().getOrDefault(ChestTier.MID, 0));
            for (LootTable.Family family : LootTable.Family.values()) {
                metadata.put("loot" + family.name().charAt(0) + family.name().substring(1).toLowerCase(
                        java.util.Locale.ROOT), snapshot.lootFamilies().getOrDefault(family, 0));
            }
            metadata.put("timeToFirstChestSeconds", snapshot.timeToFirstChestSeconds());
            metadata.put("timeToMidSeconds", snapshot.timeToMidSeconds());
            metadata.put("deathCause", snapshot.deathCause());
            metadata.put("refillTriggered", refillTriggered);
            metadata.put("timeout", timedOut);
            metadata.put("interrupted", interrupted);
            return new MatchService.Performance(playerId, snapshot.kills(), snapshot.deaths(), 0, metadata);
        }).toList();
    }

    public boolean fillChest(Inventory inventory, Location location, Player opener) {
        if (getState() != GameState.RUNNING || inventory == null || location == null || !ownsWorld(location.getWorld())) {
            return false;
        }
        String key = location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
        if (filledChests.getOrDefault(key, -1) == chestGeneration) {
            return false;
        }
        ChestTier tier = map.template().getChestTier(location);
        int defaultRolls = switch (tier) {
            case ISLAND -> 5;
            case INTERMEDIATE -> 6;
            case MID -> 8;
        };
        int rolls = SkyWars.getInstance().getConfig().getInt(
                "loot." + tier.name().toLowerCase(java.util.Locale.ROOT) + "-rolls", defaultRolls);
        long seed = getGameId().getMostSignificantBits() ^ getGameId().getLeastSignificantBits()
                ^ ((long) key.hashCode() << 32) ^ chestGeneration;
        java.util.Random random = new java.util.Random(seed);
        LootTable.Roll generated = LootTable.roll(tier, rolls, random);
        filledChests.put(key, chestGeneration);
        List<ItemStack> loot = generated.items();
        inventory.clear();
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            slots.add(slot);
        }
        java.util.Collections.shuffle(slots, random);
        for (int index = 0; index < Math.min(loot.size(), slots.size()); index++) {
            inventory.setItem(slots.get(index), loot.get(index));
        }
        stats.recordChest(opener.getUniqueId(), tier, generated.families(), runningSeconds);
        return true;
    }

    public void recordBlockPlaced(Player player) {
        stats.recordBlockPlaced(player.getUniqueId());
    }

    public boolean canPlaceBlock(Player player, Location location) {
        if (location.getBlockY() <= buildCeilingY) return true;
        Integer previous = buildLimitWarnings.put(player.getUniqueId(), runningSeconds);
        if (previous == null || previous != runningSeconds) {
            player.sendActionBar(Component.text(SkyWars.message(player, "skywars.build_limit"), NamedTextColor.RED));
        }
        return false;
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
        if (cookiePlayer == null) {
            return;
        }
        UUID playerId = cookiePlayer.getPlayer().getUniqueId();
        if (getSpectators().stream().anyMatch(viewer ->
                viewer.getPlayer().getUniqueId().equals(playerId))) {
            super.removePlayer(cookiePlayer, reason);
            scoreboard.remove(cookiePlayer.getPlayer());
            return;
        }
        if (!getPlayers().contains(cookiePlayer)) return;
        if (getState() == GameState.RUNNING && "disconnect".equalsIgnoreCase(reason)
                && alive.contains(playerId)) {
            if (!disconnectedAt.containsKey(playerId)) {
                disconnectedAt.put(playerId, System.currentTimeMillis());
                reconnectSnapshots.put(playerId, PlayerActivitySnapshot.capture(cookiePlayer.getPlayer()));
            }
            scoreboard.remove(cookiePlayer.getPlayer());
            return;
        }
        boolean running = getState() == GameState.RUNNING;
        if (running && alive.remove(playerId)) {
            stats.recordElimination(playerId, null, reason == null ? "disconnect" : reason);
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

    @Override
    public boolean hasReconnectReservation(UUID playerId) {
        Long disconnected = disconnectedAt.get(playerId);
        return getState() == GameState.RUNNING && disconnected != null && alive.contains(playerId)
                && System.currentTimeMillis() - disconnected <= RECONNECT_GRACE_MILLIS;
    }

    @Override
    public synchronized boolean reconnect(CookiePlayer cookiePlayer) {
        UUID playerId = cookiePlayer.getPlayer().getUniqueId();
        PlayerActivitySnapshot snapshot = reconnectSnapshots.get(playerId);
        Integer spawn = spawnAssignments.get(playerId);
        if (!hasReconnectReservation(playerId) || snapshot == null || spawn == null
                || !snapshot.relocate(cookiePlayer.getPlayer(), map.template().getSpawn(map.world(), spawn))
                || !restorePlayerAfterReconnect(cookiePlayer)) {
            return false;
        }
        snapshot.applyState(cookiePlayer.getPlayer());
        participantPlayers.put(playerId, cookiePlayer);
        disconnectedAt.remove(playerId);
        reconnectSnapshots.remove(playerId);
        cookiePlayer.setState(PlayerState.IN_GAME);
        return true;
    }

    private void expireReconnectReservations() {
        long now = System.currentTimeMillis();
        for (UUID playerId : List.copyOf(disconnectedAt.keySet())) {
            Long disconnected = disconnectedAt.get(playerId);
            if (disconnected == null || now - disconnected <= RECONNECT_GRACE_MILLIS) continue;
            disconnectedAt.remove(playerId);
            reconnectSnapshots.remove(playerId);
            CookiePlayer reserved = getPlayers().stream()
                    .filter(player -> player.getPlayer().getUniqueId().equals(playerId)).findFirst().orElse(null);
            if (reserved != null) super.removePlayer(reserved, "reconnect_expired");
            if (alive.remove(playerId)) {
                stats.recordElimination(playerId, null, "disconnect");
                placements.put(playerId, alive.size() + 1);
                survivalSeconds.put(playerId, runningSeconds);
            }
        }
    }

    public void shutdown() {
        try {
            if (getState() == GameState.RUNNING && !outcomeHandled) {
                outcomeHandled = true;
                persistInterruptedOutcome();
            }
        } catch (RuntimeException error) {
            logWarning("Interrupted SkyWars persistence failed before cleanup: " + error.getMessage());
        } finally {
            setState(GameState.FINISHED);
            cleanup();
        }
    }

    private void persistInterruptedOutcome() {
        Set<UUID> persistedParticipants = Set.copyOf(participantIds);
        List<MatchService.Performance> performances = createPerformances(persistedParticipants, true);
        CompletableFuture<Match> pendingMatch = matchFuture;
        boolean flushed = BoundedAsyncFlush.runAndAwait(() -> {
            try {
                Match durableMatch = pendingMatch.get(1500, TimeUnit.MILLISECONDS);
                if (durableMatch != null) {
                    new MatchService(null).completeMatchByWinnerIds(durableMatch, Set.of(), performances);
                }
            } catch (Exception error) {
                logWarning("Could not persist interrupted SkyWars match: " + error.getMessage());
            }
        }, Duration.ofSeconds(2));
        if (!flushed) logWarning("Interrupted SkyWars persistence exceeded the 2 second shutdown budget");
    }

    private void logWarning(String message) {
        SkyWars plugin = SkyWars.getInstance();
        if (plugin != null) plugin.getLogger().warning(message);
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
                try {
                    if (getPlayers().contains(cookiePlayer)) {
                        super.removePlayer(cookiePlayer, "game_cleanup");
                    }
                } catch (RuntimeException error) {
                    logWarning("Could not remove SkyWars player during cleanup: " + error.getMessage());
                }
                try {
                    scoreboard.remove(player);
                } catch (RuntimeException error) {
                    logWarning("Could not clear SkyWars scoreboard during cleanup: " + error.getMessage());
                }
            }
        }
        ejectSpectatorsToLobby();
        try {
            if (!MapManager.unloadMap(getGameId())) {
                logWarning("SkyWars map cleanup remains pending for " + getGameId());
            }
        } catch (RuntimeException error) {
            logWarning("SkyWars map cleanup failed for " + getGameId() + ": " + error.getMessage());
        } finally {
            alive.clear();
            participantIds.clear();
            participantPlayers.clear();
            spawnAssignments.clear();
            assignedSpawns.clear();
            filledChests.clear();
            placedBlocks.clear();
            placements.clear();
            survivalSeconds.clear();
            buildLimitWarnings.clear();
            trackerGuidancePlayers.clear();
            disconnectedAt.clear();
            reconnectSnapshots.clear();
            stats.clear();
            scoreboard.clear();
            GameManager.removeGame(this);
            SkyWars.requestStandbyRefill();
        }
    }
}
