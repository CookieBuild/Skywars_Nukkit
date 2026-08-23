package com.cookiebuild.skywars.kit;

import java.util.Arrays;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.skywars.game.SkyWarsGame;
import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.ui.SkyWarsKitSelectionUI;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public final class KitCommand implements CommandExecutor, TabCompleter {
    private final KitManager kitManager;
    private final SkyWarsKitSelectionUI kitSelectionUI;

    public KitCommand(KitManager kitManager, SkyWarsKitSelectionUI kitSelectionUI) {
        this.kitManager = kitManager;
        this.kitSelectionUI = kitSelectionUI;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(SkyWars.message(null, "skywars.command.player_only"));
            return true;
        }
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer == null || !(GameManager.getGameOfPlayer(cookiePlayer) instanceof SkyWarsGame game)
                || game.hasStarted()) {
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.only_waiting"), NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            showCatalog(player);
            return true;
        }
        SkyWarsKit kit = SkyWarsKit.fromName(args[1]);
        if (kit == null) {
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.unknown"), NamedTextColor.RED));
            showCatalog(player);
            return true;
        }
        boolean purchase = args[0].equalsIgnoreCase("buy");
        boolean select = args[0].equalsIgnoreCase("select");
        if (purchase || select) {
            mutateAsync(player, kit, purchase);
            return true;
        }
        showCatalog(player);
        return true;
    }

    public void showCatalog(Player player) {
        kitSelectionUI.open(player);
    }

    private void mutateAsync(Player player, SkyWarsKit kit, boolean purchase) {
        var playerId = player.getUniqueId();
        if (!kitManager.tryBeginMutation(playerId)) {
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.busy"), NamedTextColor.YELLOW));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(SkyWars.getInstance(), () -> {
            String key;
            NamedTextColor color;
            try {
                KitManager.Profile profile = kitManager.loadProfile(playerId);
                if (purchase && profile.isUnlocked(kit)) {
                    key = "skywars.kit.already_unlocked";
                    color = NamedTextColor.YELLOW;
                } else if (purchase && kitManager.purchase(playerId, kit) && kitManager.select(playerId, kit)) {
                    key = "skywars.kit.unlocked_selected";
                    color = NamedTextColor.GREEN;
                } else if (purchase) {
                    key = "skywars.kit.need_coins";
                    color = NamedTextColor.RED;
                } else if (kitManager.select(playerId, kit)) {
                    key = "skywars.kit.selected";
                    color = NamedTextColor.GREEN;
                } else {
                    key = "skywars.kit.unlock_first";
                    color = NamedTextColor.RED;
                }
            } catch (RuntimeException error) {
                SkyWars.getInstance().getLogger().warning(
                        "Could not update SkyWars kit for " + playerId + ": " + error.getMessage());
                key = purchase ? "skywars.kit.purchase_failed" : "skywars.kit.selection_failed";
                color = NamedTextColor.RED;
            } finally {
                kitManager.finishMutation(playerId);
            }
            String resultKey = key;
            NamedTextColor resultColor = color;
            Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
                if (!player.isOnline()) return;
                String message = resultKey.equals("skywars.kit.need_coins")
                        ? SkyWars.message(player, resultKey, kit.price(), SkyWars.kitName(player, kit))
                        : SkyWars.message(player, resultKey, SkyWars.kitName(player, kit));
                player.sendMessage(Component.text(message, resultColor));
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("select", "buy").stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2) {
            return Arrays.stream(SkyWarsKit.values()).map(SkyWarsKit::key)
                    .filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
