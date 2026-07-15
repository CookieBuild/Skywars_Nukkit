package com.cookiebuild.skywars.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.cookiedough.model.MinigameProgression;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.cookiedough.service.MinigameProgressionService;
import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.game.SkyWarsGame;
import com.cookiebuild.skywars.kit.KitManager;
import com.cookiebuild.skywars.kit.SkyWarsKit;
import com.cookiebuild.skywars.ui.bedrock.BedrockKitSelectionUI;
import com.cookiebuild.skywars.ui.bedrock.BedrockUIHelper;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** One selector/shop entry point with native Java and Bedrock presentations. */
public final class SkyWarsKitSelectionUI implements Listener {
    private static final Component TITLE = Component.text("SkyWars Kits", NamedTextColor.GOLD);

    private final KitManager kitManager;
    private final NamespacedKey kitKey;
    private final BedrockKitSelectionUI bedrockUI;

    public SkyWarsKitSelectionUI(KitManager kitManager) {
        this.kitManager = kitManager;
        this.kitKey = new NamespacedKey(SkyWars.getInstance(), "kit_menu_entry");
        this.bedrockUI = new BedrockKitSelectionUI(this::chooseKit, this::open);
    }

    public void open(Player player) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> open(player));
            return;
        }
        if (!isWaitingPlayer(player)) {
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.only_waiting"),
                    NamedTextColor.RED));
            return;
        }
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(SkyWars.getInstance(), () -> {
            try {
                KitMenuSnapshot snapshot = loadSnapshot(playerId);
                Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
                    if (!player.isOnline() || !isWaitingPlayer(player)) return;
                    if (BedrockUIHelper.isBedrockPlayer(player)) bedrockUI.open(player, snapshot);
                    else renderJava(player, snapshot);
                });
            } catch (RuntimeException error) {
                SkyWars.getInstance().getLogger().warning(
                        "Could not load SkyWars kit menu for " + playerId + ": " + error.getMessage());
                Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
                    if (player.isOnline()) player.sendMessage(Component.text(
                            SkyWars.message(player, "skywars.kit.menu_failed"), NamedTextColor.RED));
                });
            }
        });
    }

    private KitMenuSnapshot loadSnapshot(UUID playerId) {
        MinigameProgressionService progression = new MinigameProgressionService(null);
        MinigameProgression stats = progression.getOrCreateStats(playerId, KitManager.MINIGAME_KEY);
        SkyWarsKit selected = kitManager.selected(playerId);
        return new KitMenuSnapshot(stats.getLevel(), stats.getExperience(), stats.getExperienceForNextLevel(),
                progression.getCoins(playerId, KitManager.MINIGAME_KEY),
                KitMenuSnapshot.entries(kit -> kitManager.isUnlocked(playerId, kit), selected));
    }

    private void renderJava(Player player, KitMenuSnapshot snapshot) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        inventory.setItem(4, playerInfo(snapshot));
        int[] slots = {10, 12, 14, 16};
        for (int index = 0; index < snapshot.entries().size() && index < slots.length; index++) {
            inventory.setItem(slots[index], kitItem(player, snapshot.entries().get(index)));
        }
        player.openInventory(inventory);
    }

    private ItemStack playerInfo(KitMenuSnapshot snapshot) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Your SkyWars progression", NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text("Level: " + snapshot.level(), NamedTextColor.YELLOW),
                Component.text("Coins: " + snapshot.coins(), NamedTextColor.GOLD),
                Component.text("XP: " + snapshot.experience() + "/" + snapshot.nextLevelExperience(),
                        NamedTextColor.GREEN)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack kitItem(Player player, KitMenuSnapshot.Entry entry) {
        ItemStack item = new ItemStack(material(entry.kit()));
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = entry.selected() ? NamedTextColor.GOLD
                : entry.unlocked() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        meta.displayName(Component.text((entry.selected() ? "★ " : "") + entry.kit().displayName(), color));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(SkyWars.message(player,
                "skywars.kit." + entry.kit().key() + ".description"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (entry.selected()) lore.add(Component.text("Selected", NamedTextColor.GOLD));
        else if (entry.unlocked()) lore.add(Component.text("Click to select", NamedTextColor.GREEN));
        else lore.add(Component.text("Click to purchase · " + entry.kit().price() + " coins",
                NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, entry.kit().key());
        item.setItemMeta(meta);
        return item;
    }

    private Material material(SkyWarsKit kit) {
        return switch (kit) {
            case SCOUT -> Material.STONE_SWORD;
            case ARMORER -> Material.CHAINMAIL_CHESTPLATE;
            case BUILDER -> Material.OAK_PLANKS;
            case HEALER -> Material.GOLDEN_APPLE;
        };
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(TITLE)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String value = clicked.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        SkyWarsKit kit = SkyWarsKit.fromName(value);
        if (kit == null) return;
        player.closeInventory();
        chooseKit(player, new KitMenuSnapshot.Entry(kit, false, false));
    }

    private void chooseKit(Player player, KitMenuSnapshot.Entry entry) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> chooseKit(player, entry));
            return;
        }
        if (!isWaitingPlayer(player)) {
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.only_waiting"),
                    NamedTextColor.RED));
            return;
        }
        UUID playerId = player.getUniqueId();
        Bukkit.getScheduler().runTaskAsynchronously(SkyWars.getInstance(), () -> {
            boolean alreadyUnlocked = kitManager.isUnlocked(playerId, entry.kit());
            boolean success = alreadyUnlocked
                    ? kitManager.select(playerId, entry.kit())
                    : kitManager.purchase(playerId, entry.kit()) && kitManager.select(playerId, entry.kit());
            Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
                if (!player.isOnline()) return;
                String key = success
                        ? alreadyUnlocked ? "skywars.kit.selected" : "skywars.kit.unlocked_selected"
                        : "skywars.kit.purchase_failed";
                player.sendMessage(Component.text(SkyWars.message(player, key, entry.kit().displayName()),
                        success ? NamedTextColor.GREEN : NamedTextColor.RED));
                if (!success) open(player);
            });
        });
    }

    private boolean isWaitingPlayer(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        return cookiePlayer != null
                && GameManager.getGameOfPlayer(cookiePlayer) instanceof SkyWarsGame game
                && !game.hasStarted();
    }
}
