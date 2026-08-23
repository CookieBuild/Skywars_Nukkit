package com.cookiebuild.skywars.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.game.SkyWarsGame;
import com.cookiebuild.skywars.kit.KitManager;
import com.cookiebuild.skywars.kit.SkyWarsKit;
import com.cookiebuild.skywars.ui.bedrock.BedrockKitSelectionUI;
import com.cookiebuild.skywars.ui.bedrock.BedrockUIHelper;
import com.cookiebuild.cookiedough.ui.MenuLore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

/** One selector/shop entry point with native Java and Bedrock presentations. */
public final class SkyWarsKitSelectionUI implements Listener {
    private final KitManager kitManager;
    private final NamespacedKey kitKey;
    private final NamespacedKey actionKey;
    private final BedrockKitSelectionUI bedrockUI;
    private final Set<UUID> menuLoads = ConcurrentHashMap.newKeySet();

    public SkyWarsKitSelectionUI(KitManager kitManager) {
        this.kitManager = kitManager;
        this.kitKey = new NamespacedKey(SkyWars.getInstance(), "kit_menu_entry");
        this.actionKey = new NamespacedKey(SkyWars.getInstance(), "kit_menu_action");
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
        if (!menuLoads.add(playerId)) {
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.loading"), NamedTextColor.YELLOW));
            return;
        }
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
            } finally {
                menuLoads.remove(playerId);
            }
        });
    }

    private KitMenuSnapshot loadSnapshot(UUID playerId) {
        KitManager.Profile profile = kitManager.loadProfile(playerId);
        return new KitMenuSnapshot(profile.level(), profile.experience(), profile.nextLevelExperience(),
                profile.coins(), KitMenuSnapshot.entries(profile::isUnlocked, profile.selected()));
    }

    private void renderJava(Player player, KitMenuSnapshot snapshot) {
        KitMenuHolder holder = new KitMenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 27,
                Component.text(SkyWars.message(player, "skywars.kit.catalog"), NamedTextColor.GOLD));
        holder.bind(inventory);
        inventory.setItem(4, playerInfo(player, snapshot));
        int[] slots = {10, 12, 14, 16};
        for (int index = 0; index < snapshot.entries().size() && index < slots.length; index++) {
            inventory.setItem(slots[index], kitItem(player, snapshot.entries().get(index)));
        }
        player.openInventory(inventory);
    }

    private ItemStack playerInfo(Player player, KitMenuSnapshot snapshot) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(SkyWars.message(player, "skywars.kit.progression"), NamedTextColor.AQUA));
        meta.lore(List.of(
                Component.text(SkyWars.message(player, "skywars.kit.level", snapshot.level()), NamedTextColor.YELLOW),
                Component.text(SkyWars.message(player, "skywars.kit.coins", snapshot.coins()), NamedTextColor.GOLD),
                Component.text(SkyWars.message(player, "skywars.kit.xp", snapshot.experience(),
                        snapshot.nextLevelExperience()), NamedTextColor.GREEN)));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack kitItem(Player player, KitMenuSnapshot.Entry entry) {
        ItemStack item = new ItemStack(material(entry.kit()));
        ItemMeta meta = item.getItemMeta();
        NamedTextColor color = entry.selected() ? NamedTextColor.GOLD
                : entry.unlocked() ? NamedTextColor.GREEN : NamedTextColor.YELLOW;
        meta.displayName(Component.text((entry.selected() ? "★ " : "")
                + SkyWars.kitName(player, entry.kit()), color));
        List<Component> lore = new ArrayList<>();
        lore.add(MenuLore.detail(SkyWars.message(player,
                "skywars.kit." + entry.kit().key() + ".description")));
        lore.add(Component.empty());
        if (entry.selected()) lore.add(Component.text(SkyWars.message(player, "skywars.kit.action.selected"), NamedTextColor.GOLD));
        else if (entry.unlocked()) lore.add(Component.text(SkyWars.message(player, "skywars.kit.action.select"), NamedTextColor.GREEN));
        else lore.add(Component.text(SkyWars.message(player, "skywars.kit.action.purchase", entry.kit().price()),
                NamedTextColor.YELLOW));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, entry.kit().key());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING,
                (entry.unlocked() ? KitMenuAction.SELECT : KitMenuAction.PURCHASE).name());
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
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof KitMenuHolder holder)
                || !holder.playerId().equals(player.getUniqueId())) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        KitMenuAction action = KitMenuAction.from(clicked.getItemMeta().getPersistentDataContainer()
                .get(actionKey, PersistentDataType.STRING));
        if (action == KitMenuAction.BACK) {
            player.closeInventory();
            open(player);
            return;
        }
        String value = clicked.getItemMeta().getPersistentDataContainer().get(kitKey, PersistentDataType.STRING);
        SkyWarsKit kit = SkyWarsKit.fromName(value);
        if (kit == null) return;
        player.closeInventory();
        if (action == KitMenuAction.PURCHASE) {
            renderJavaConfirmation(player, kit);
            return;
        }
        if (action != KitMenuAction.SELECT && action != KitMenuAction.CONFIRM_PURCHASE) return;
        chooseKit(player, new KitMenuSnapshot.Entry(kit, false, false));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof KitMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menuLoads.remove(event.getPlayer().getUniqueId());
        bedrockUI.invalidate(event.getPlayer());
    }

    private void renderJavaConfirmation(Player player, SkyWarsKit kit) {
        KitMenuHolder holder = new KitMenuHolder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, 9,
                Component.text(SkyWars.message(player, "skywars.kit.confirm.title"), NamedTextColor.GOLD));
        holder.bind(inventory);
        ItemStack confirm = actionItem(Material.LIME_CONCRETE,
                SkyWars.message(player, "skywars.kit.confirm.buy", kit.price()),
                KitMenuAction.CONFIRM_PURCHASE, kit);
        ItemStack back = actionItem(Material.RED_CONCRETE, SkyWars.message(player, "skywars.ui.back"),
                KitMenuAction.BACK, null);
        inventory.setItem(3, confirm);
        inventory.setItem(5, back);
        player.openInventory(inventory);
    }

    private ItemStack actionItem(Material material, String name, KitMenuAction action, SkyWarsKit kit) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, action == KitMenuAction.CONFIRM_PURCHASE
                ? NamedTextColor.GREEN : NamedTextColor.RED));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action.name());
        if (kit != null) {
            meta.getPersistentDataContainer().set(kitKey, PersistentDataType.STRING, kit.key());
        }
        item.setItemMeta(meta);
        return item;
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
        if (!kitManager.tryBeginMutation(playerId)) {
            player.sendMessage(Component.text(SkyWars.message(player, "skywars.kit.busy"), NamedTextColor.YELLOW));
            return;
        }
        Bukkit.getScheduler().runTaskAsynchronously(SkyWars.getInstance(), () -> {
            try {
                KitManager.Profile profile = kitManager.loadProfile(playerId);
                boolean alreadyUnlocked = profile.isUnlocked(entry.kit());
                boolean success = alreadyUnlocked
                        ? kitManager.select(playerId, entry.kit())
                        : kitManager.purchase(playerId, entry.kit()) && kitManager.select(playerId, entry.kit());
                Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
                    if (!player.isOnline()) return;
                    String key = success
                            ? alreadyUnlocked ? "skywars.kit.selected" : "skywars.kit.unlocked_selected"
                            : "skywars.kit.purchase_failed";
                    player.sendMessage(Component.text(SkyWars.message(player, key,
                                    SkyWars.kitName(player, entry.kit())),
                            success ? NamedTextColor.GREEN : NamedTextColor.RED));
                    if (!success) open(player);
                });
            } catch (RuntimeException error) {
                SkyWars.getInstance().getLogger().warning(
                        "Could not update SkyWars kit for " + playerId + ": " + error.getMessage());
                Bukkit.getScheduler().runTask(SkyWars.getInstance(), () -> {
                    if (player.isOnline()) player.sendMessage(Component.text(
                            SkyWars.message(player, "skywars.kit.purchase_failed",
                                    SkyWars.kitName(player, entry.kit())),
                            NamedTextColor.RED));
                });
            } finally {
                kitManager.finishMutation(playerId);
            }
        });
    }

    private boolean isWaitingPlayer(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        return cookiePlayer != null
                && GameManager.getGameOfPlayer(cookiePlayer) instanceof SkyWarsGame game
                && !game.hasStarted();
    }
}
