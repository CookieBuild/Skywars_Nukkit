package com.cookiebuild.skywars;

import java.util.ArrayList;
import java.util.Objects;

import org.bukkit.NamespacedKey;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.cookiebuild.cookiedough.CookieDough;
import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.skywars.game.SkyWarsGame;
import com.cookiebuild.skywars.kit.KitCommand;
import com.cookiebuild.skywars.kit.KitManager;
import com.cookiebuild.skywars.listener.SkyWarsListener;
import com.cookiebuild.skywars.map.MapManager;
import com.cookiebuild.cookiedough.utils.LocaleManager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class SkyWars extends JavaPlugin {
    private static SkyWars instance;
    private KitManager kitManager;
    private KitCommand kitCommand;
    private SkyWarsListener gameListener;
    private NamespacedKey kitSelectorKey;

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

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
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
        kitCommand = new KitCommand(kitManager);
        gameListener = new SkyWarsListener();
        getServer().getPluginManager().registerEvents(gameListener, this);

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

    @Override
    public void onDisable() {
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
