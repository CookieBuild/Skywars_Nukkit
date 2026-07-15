package com.cookiebuild.skywars;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.Duration;

import org.bukkit.NamespacedKey;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.StandbyGamePool;
import com.cookiebuild.cookiedough.game.StandbyRefillGate;
import com.cookiebuild.skywars.game.SkyWarsGame;
import com.cookiebuild.skywars.kit.KitCommand;
import com.cookiebuild.skywars.kit.KitManager;
import com.cookiebuild.skywars.listener.SkyWarsListener;
import com.cookiebuild.skywars.map.MapManager;
import com.cookiebuild.skywars.ui.SkyWarsKitSelectionUI;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SkyWars extends JavaPlugin {
    private static final int STANDBY_GAME_TARGET = 3;
    private static final long STANDBY_REFILL_RETRY_TICKS = 20L * 5L;
    private static SkyWars instance;
    private KitManager kitManager;
    private KitCommand kitCommand;
    private SkyWarsKitSelectionUI kitSelectionUI;
    private SkyWarsListener gameListener;
    private NamespacedKey kitSelectorKey;
    private final StandbyGamePool<SkyWarsGame> standbyGames =
            new StandbyGamePool<>(STANDBY_GAME_TARGET);
    private final StandbyRefillGate standbyRefillGate =
            new StandbyRefillGate(Duration.ofSeconds(30));
    private boolean standbyRefillScheduled;
    private boolean shuttingDown;

    public static SkyWars getInstance() {
        return instance;
    }

    public static boolean registerNewGame() {
        try {
            SkyWarsGame game = new SkyWarsGame();
            GameManager.addGame(game);
            instance.getLogger().info("Registered SkyWars game " + game.getGameId());
            return true;
        } catch (RuntimeException error) {
            instance.getLogger().warning("SkyWars remains unavailable until a valid map archive is installed: "
                    + error.getMessage());
            return false;
        }
    }

    /** Promotes an already loaded world without doing I/O on the queue tick. */
    public static void activateNextGame() {
        if (instance == null || instance.shuttingDown) {
            return;
        }
        SkyWarsGame game = instance.standbyGames.poll();
        if (game == null) {
            instance.getLogger().warning("No preloaded SkyWars standby is available; "
                    + "the next arena will be prepared once gameplay is idle");
            requestStandbyRefill();
            return;
        }
        GameManager.addGame(game);
        instance.getLogger().info("Activated preloaded SkyWars game " + game.getGameId()
                + " (standby remaining=" + instance.standbyGames.size() + ")");
        requestStandbyRefill();
    }

    public static void requestStandbyRefill() {
        if (instance == null || instance.shuttingDown || instance.standbyRefillScheduled
                || !instance.standbyGames.needsRefill()) {
            return;
        }
        instance.standbyRefillScheduled = true;
        instance.getServer().getScheduler().runTaskLater(instance, () -> {
            if (instance == null || instance.shuttingDown) {
                return;
            }
            instance.standbyRefillScheduled = false;
            if (!instance.isSafeToRefill()) {
                requestStandbyRefill();
                return;
            }
            instance.preloadStandbyGames();
            instance.activatePreparedGameIfMissing();
            if (instance.standbyGames.needsRefill()) {
                requestStandbyRefill();
            }
        }, STANDBY_REFILL_RETRY_TICKS);
    }

    private boolean isSafeToRefill() {
        // WorldCreator is synchronous and may take several seconds even when no
        // match is running. Do not impose that pause on lobby users either, and
        // require a sustained empty interval so reconnects cannot race a refill.
        boolean activeGameplay = GameManager.getGames().stream().anyMatch(game -> !game.getPlayers().isEmpty()
                || game.getState() == GameState.STARTING
                || game.getState() == GameState.RUNNING);
        return standbyRefillGate.canRefill(!Bukkit.getOnlinePlayers().isEmpty(), activeGameplay);
    }

    private void preloadStandbyGames() {
        while (!shuttingDown && standbyGames.needsRefill()) {
            long startedAt = System.nanoTime();
            try {
                SkyWarsGame game = new SkyWarsGame();
                if (!standbyGames.offer(game)) {
                    game.shutdown();
                    break;
                }
                getLogger().info("Preloaded SkyWars standby " + game.getGameId()
                        + " (" + standbyGames.size() + "/" + standbyGames.targetSize()
                        + ", load_ms=" + elapsedMillis(startedAt) + ")");
            } catch (RuntimeException error) {
                getLogger().warning("Could not preload SkyWars standby: " + error.getMessage());
                break;
            }
        }
    }

    private void activatePreparedGameIfMissing() {
        boolean hasOpenGame = GameManager.getGames().stream()
                .filter(SkyWarsGame.class::isInstance)
                .anyMatch(game -> game.getState() == GameState.OPEN);
        if (!hasOpenGame) {
            activateNextGame();
        }
    }

    private static long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        migrateConfig();
        LocaleManager.registerBundle("skywars_messages");
        kitSelectorKey = new NamespacedKey(this, "kit_selector");
        try {
            MapManager.loadTemplates();
        } catch (RuntimeException error) {
            getLogger().severe("SkyWars configuration is invalid: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        kitManager = new KitManager(CookieDough.createMinigameProgressionService());
        kitSelectionUI = new SkyWarsKitSelectionUI(kitManager);
        kitCommand = new KitCommand(kitManager, kitSelectionUI);
        gameListener = new SkyWarsListener();
        getServer().getPluginManager().registerEvents(gameListener, this);
        getServer().getPluginManager().registerEvents(kitSelectionUI, this);

        PluginCommand skywars = Objects.requireNonNull(getCommand("skywars"));
        skywars.setExecutor((sender, command, label, args) -> {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command is only available to players.");
                return true;
            }
            CookieDough.getInstance().getLobbyManager().requestGame(player, "SkyWars");
            return true;
        });
        PluginCommand swkit = Objects.requireNonNull(getCommand("swkit"));
        swkit.setExecutor(kitCommand);
        swkit.setTabCompleter(kitCommand);

        if (!registerNewGame()) {
            getLogger().warning("Plugin enabled without an open game; NPC/Quick Play will not offer an empty arena.");
        } else {
            // World creation is intentionally completed before Paper reports the
            // server ready. Countdown ticks only promote these prepared games.
            preloadStandbyGames();
        }
    }

    private void migrateConfig() {
        int version = getConfig().getInt("config-version", 0);
        if (version >= 2) return;
        List<?> configuredSpawns = getConfig().getList("maps.legacy-1.spawns");
        if (configuredSpawns == null || configuredSpawns.isEmpty()) {
            throw new IllegalStateException("Cannot migrate missing legacy-1 spawns");
        }
        List<Object> migratedSpawns = new ArrayList<>(configuredSpawns);
        migratedSpawns.set(0, List.of(-1589.5, 48.5, -870.5, -1.0));
        getConfig().set("maps.legacy-1.spawns", migratedSpawns);
        getConfig().set("maps.legacy-2.spectator-spawn", List.of(-475.5, 60.0, 367.5, 0.0));
        getConfig().set("config-version", 2);
        saveConfig();
        getLogger().info("Migrated recovered SkyWars coordinates to config version 2");
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        standbyGames.drain();
        for (Game game : new ArrayList<>(GameManager.getGames())) {
            if (game instanceof SkyWarsGame skyWarsGame) {
                skyWarsGame.shutdown();
            }
        }
        if (gameListener != null) {
            gameListener.clear();
        }
        if (!MapManager.unloadAll()) {
            getLogger().warning("Some SkyWars world directories could not be cleaned up");
        }
        instance = null;
    }

    public KitManager getKitManager() {
        return kitManager;
    }

    public KitCommand getKitCommand() {
        return kitCommand;
    }

    public SkyWarsKitSelectionUI getKitSelectionUI() {
        return kitSelectionUI;
    }

    public NamespacedKey getKitSelectorKey() {
        return kitSelectorKey;
    }

    public static String message(Player player, String key, Object... arguments) {
        java.util.Locale locale = player == null ? java.util.Locale.ENGLISH : player.locale();
        String registered = LocaleManager.getMessage("skywars_messages", key, locale, arguments);
        if (!registered.equals(key)) {
            return registered;
        }
        // LocaleManager lives in CookieDough's plugin classloader. Explicitly use
        // this module's classloader so module-owned bundles also work on Paper.
        try {
            java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle(
                    "skywars_messages", locale, SkyWars.class.getClassLoader());
            String value = bundle.getString(key);
            for (int index = 0; index < arguments.length; index++) {
                value = value.replace("{" + index + "}", String.valueOf(arguments[index]));
            }
            return value;
        } catch (java.util.MissingResourceException ignored) {
            return key;
        }
    }
}
