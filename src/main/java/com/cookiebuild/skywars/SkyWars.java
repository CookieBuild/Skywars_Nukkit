package com.cookiebuild.skywars;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.UUID;
import java.util.concurrent.CompletionException;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.game.BukkitArenaPreparationScheduler;
import com.cookiebuild.cookiedough.game.ArenaPreparationPipeline;
import com.cookiebuild.cookiedough.game.StandbyArenaService;
import com.cookiebuild.cookiedough.game.StandbyRefillPolicy;
import com.cookiebuild.skywars.game.SkyWarsGame;
import com.cookiebuild.skywars.kit.KitCommand;
import com.cookiebuild.skywars.kit.KitManager;
import com.cookiebuild.skywars.kit.SkyWarsKit;
import com.cookiebuild.skywars.listener.SkyWarsListener;
import com.cookiebuild.skywars.map.MapManager;
import com.cookiebuild.skywars.ui.SkyWarsKitSelectionUI;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SkyWars extends JavaPlugin {
    private static final String MESSAGE_BUNDLE = "skywars_messages";
    private static final String UNAVAILABLE_MESSAGE = "Text unavailable";
    private static final Locale BRAZILIAN_PORTUGUESE = Locale.of("pt", "BR");
    private static final ResourceBundle.Control NO_SYSTEM_LOCALE_FALLBACK =
            ResourceBundle.Control.getNoFallbackControl(ResourceBundle.Control.FORMAT_PROPERTIES);
    private static SkyWars instance;
    private KitManager kitManager;
    private KitCommand kitCommand;
    private SkyWarsKitSelectionUI kitSelectionUI;
    private SkyWarsListener gameListener;
    private NamespacedKey kitSelectorKey;
    private StandbyArenaService<MapManager.PreparedMap, SkyWarsGame> arenas;
    private boolean shuttingDown;

    public static SkyWars getInstance() {
        return instance;
    }

    public static boolean registerNewGame() {
        return instance != null && !instance.shuttingDown && instance.arenas.request(0L);
    }

    /** Promotes an already loaded world without doing I/O on the queue tick. */
    public static void activateNextGame() {
        if (instance == null || instance.shuttingDown) {
            return;
        }
        instance.arenas.activateNext();
    }

    public static void requestStandbyRefill() {
        if (instance != null && !instance.shuttingDown) {
            instance.arenas.request(StandbyRefillPolicy.RUNTIME_DELAY_TICKS);
        }
    }

    private MapManager.PreparedMap planArena() {
        try {
            return MapManager.plan(UUID.randomUUID(), MapManager.selectTemplate());
        } catch (java.io.IOException error) {
            throw new CompletionException(error);
        }
    }

    private static MapManager.PreparedMap prepareArenaIo(MapManager.PreparedMap plan) {
        try {
            return MapManager.prepareIo(plan);
        } catch (java.io.IOException error) {
            throw new CompletionException(error);
        }
    }

    private static ArenaPreparationPipeline.WorldLoad<SkyWarsGame> loadArena(
            MapManager.PreparedMap prepared) {
        return MapManager.loadPreparedAsync(prepared).map(map -> {
            try {
                return new SkyWarsGame(prepared.gameId(), map);
            } catch (RuntimeException error) {
                if (!MapManager.discardLoadedWorld(prepared.gameId())) {
                    SkyWars.getInstance().getLogger().warning(
                            "Could not unload partially constructed SkyWars arena " + prepared.gameId());
                }
                throw error;
            }
        });
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
        arenas = StandbyArenaService.asynchronous(
                "SkyWars", new BukkitArenaPreparationScheduler(this), this::planArena,
                SkyWars::prepareArenaIo, SkyWars::loadArena, MapManager::discardPrepared,
                () -> GameManager.getGames().stream().filter(SkyWarsGame.class::isInstance)
                        .anyMatch(game -> game.getState() == GameState.OPEN),
                GameManager::addGame, SkyWarsGame::shutdown, getLogger(), true);

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
        if (arenas != null) arenas.shutdown();
        for (Game game : new ArrayList<>(GameManager.getGames())) {
            if (game instanceof SkyWarsGame skyWarsGame) {
                skyWarsGame.shutdown();
            }
        }
        if (gameListener != null) {
            gameListener.clear();
        }
        if (kitManager != null) {
            kitManager.clear();
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
        Locale locale = player == null ? Locale.ENGLISH : player.locale();
        return messageForLocale(locale, key, arguments);
    }

    static String messageForLocale(Locale locale, String key, Object... arguments) {
        Locale effectiveLocale = locale == null ? Locale.ENGLISH : locale;
        List<Locale> candidates = new ArrayList<>();
        candidates.add(effectiveLocale);
        if ("pt".equals(effectiveLocale.getLanguage())) candidates.add(BRAZILIAN_PORTUGUESE);
        if (!effectiveLocale.getLanguage().isBlank()) candidates.add(Locale.of(effectiveLocale.getLanguage()));
        candidates.add(Locale.ENGLISH);

        for (Locale candidate : candidates.stream().distinct().toList()) {
            try {
                ResourceBundle bundle = ResourceBundle.getBundle(MESSAGE_BUNDLE, candidate,
                        SkyWars.class.getClassLoader(), NO_SYSTEM_LOCALE_FALLBACK);
                if (!bundle.containsKey(key)) continue;
                String value = bundle.getString(key);
                for (int index = 0; index < arguments.length; index++) {
                    value = value.replace("{" + index + "}", String.valueOf(arguments[index]));
                }
                return value;
            } catch (MissingResourceException ignored) {
                // Continue through the explicit language and English fallback chain.
            }
        }
        return UNAVAILABLE_MESSAGE;
    }

    public static String kitName(Player player, SkyWarsKit kit) {
        Locale locale = player == null ? Locale.ENGLISH : player.locale();
        return kitNameForLocale(locale, kit);
    }

    static String kitNameForLocale(Locale locale, SkyWarsKit kit) {
        if (kit == null) return UNAVAILABLE_MESSAGE;
        return messageForLocale(locale, "skywars.kit." + kit.key() + ".name");
    }
}
