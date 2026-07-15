package com.cookiebuild.skywars.kit;

import java.util.Arrays;
import java.util.List;

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
            sender.sendMessage("This command is only available to players.");
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
        if (args[0].equalsIgnoreCase("buy")) {
            if (kitManager.isUnlocked(player.getUniqueId(), kit)) {
                player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.already_unlocked", kit.displayName()), NamedTextColor.YELLOW));
            } else if (kitManager.purchase(player.getUniqueId(), kit)) {
                kitManager.select(player.getUniqueId(), kit);
                player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.unlocked_selected", kit.displayName()), NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.need_coins", kit.price(), kit.displayName()), NamedTextColor.RED));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("select")) {
            if (kitManager.select(player.getUniqueId(), kit)) {
                player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.selected", kit.displayName()), NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.unlock_first", kit.displayName()), NamedTextColor.RED));
            }
            return true;
        }
        showCatalog(player);
        return true;
    }

    public void showCatalog(Player player) {
        kitSelectionUI.open(player);
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
