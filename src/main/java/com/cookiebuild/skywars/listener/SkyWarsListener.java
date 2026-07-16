package com.cookiebuild.skywars.listener;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;

import com.cookiebuild.cookiedough.game.Game;
import com.cookiebuild.cookiedough.game.GameManager;
import com.cookiebuild.cookiedough.game.GameState;
import com.cookiebuild.cookiedough.player.CookiePlayer;
import com.cookiebuild.cookiedough.player.PlayerManager;
import com.cookiebuild.skywars.SkyWars;
import com.cookiebuild.skywars.game.CombatAttributionTracker;
import com.cookiebuild.skywars.game.SkyWarsGame;

public final class SkyWarsListener implements Listener {
    private final CombatAttributionTracker combat = new CombatAttributionTracker(10_000L, System::currentTimeMillis);

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        SkyWarsGame game = playerGame(event.getPlayer());
        if (game == null || !game.ownsWorld(event.getBlock().getWorld())) {
            return;
        }
        if (game.getState() != GameState.RUNNING || !game.isAlive(event.getPlayer())
                || event.getBlock().getType() == Material.CHEST
                || event.getBlock().getType() == Material.TRAPPED_CHEST
                || !game.consumePlacedBlock(event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        SkyWarsGame game = playerGame(event.getPlayer());
        if (game == null || !game.ownsWorld(event.getBlock().getWorld())) {
            return;
        }
        if (game.getState() != GameState.RUNNING || !game.isAlive(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        game.recordBlockPlaced(event.getPlayer());
        game.trackPlacedBlock(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        SkyWarsGame game = playerGame(victim);
        if (game == null || !game.ownsWorld(victim.getWorld())) {
            return;
        }
        if (game.getState() != GameState.RUNNING || !game.isAlive(victim)) {
            event.setCancelled(true);
            return;
        }

        Player attacker = null;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            attacker = resolveAttacker(byEntity.getDamager());
            if (attacker == null || attacker.equals(victim) || playerGame(attacker) != game || !game.isAlive(attacker)) {
                event.setCancelled(true);
                return;
            }
            combat.record(victim.getUniqueId(), attacker.getUniqueId());
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID
                || victim.getHealth() - event.getFinalDamage() <= 0.0) {
            event.setCancelled(true);
            if (attacker == null) {
                attacker = attributedAttacker(victim, game);
            }
            game.eliminate(victim, attacker, event.getCause() == EntityDamageEvent.DamageCause.VOID
                    ? SkyWars.message(victim, "skywars.eliminated.void")
                    : SkyWars.message(victim, "skywars.eliminated.defeated"));
        }
    }

    private Player resolveAttacker(Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile) {
            ProjectileSource source = projectile.getShooter();
            return source instanceof Player player ? player : null;
        }
        if (entity instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplosion(EntityExplodeEvent event) {
        SkyWarsGame game = worldGame(event.getLocation());
        if (game == null) {
            return;
        }
        // TNT keeps its combat/knockback value but cannot destroy the immutable
        // template. Only blocks placed by players during this match may break.
        event.blockList().removeIf(block -> !game.consumePlacedBlock(block.getLocation()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || sameBlock(event.getFrom(), event.getTo())) {
            return;
        }
        Player player = event.getPlayer();
        SkyWarsGame game = playerGame(player);
        if (game == null || !game.ownsWorld(player.getWorld())) {
            return;
        }
        if (game.getState() == GameState.OPEN) {
            Location anchor = game.getPregameAnchor(player);
            if (anchor != null && (event.getTo().getWorld() != anchor.getWorld()
                    || event.getTo().distanceSquared(anchor) > 16.0)) {
                event.setTo(anchor);
            }
            return;
        }
        if (event.getTo().getY() >= game.getKillY()) {
            return;
        }
        if (game.isAlive(player)) {
            game.eliminate(player, attributedAttacker(player, game),
                    SkyWars.message(player, "skywars.eliminated.void"));
        } else if (player.getGameMode() == GameMode.SPECTATOR) {
            Location anchor = game.getPregameAnchor(player);
            if (anchor != null) {
                player.teleport(anchor.clone().add(0, 12, 0));
            }
        }
    }

    private static boolean sameBlock(Location from, Location to) {
        return from.getWorld() == to.getWorld()
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    private Player attributedAttacker(Player victim, SkyWarsGame game) {
        UUID attackerId = combat.consume(victim.getUniqueId());
        Player attacker = attackerId == null ? null : Bukkit.getPlayer(attackerId);
        return attacker != null && playerGame(attacker) == game && game.isAlive(attacker) ? attacker : null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        SkyWarsGame game = playerGame(player);
        if (game == null) {
            return;
        }
        Inventory inventory = event.getInventory();
        Location location = inventory.getLocation();
        if (location == null || !game.ownsWorld(location.getWorld())) {
            return;
        }
        if (inventory.getType() != org.bukkit.event.inventory.InventoryType.CHEST) {
            return;
        }
        if (game.getState() != GameState.RUNNING || !game.isAlive(player)) {
            event.setCancelled(true);
            return;
        }
        game.fillChest(inventory, location, player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onKitSelector(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getItem() == null || !event.getItem().hasItemMeta()
                || !event.getItem().getItemMeta().getPersistentDataContainer().has(
                        SkyWars.getInstance().getKitSelectorKey(), PersistentDataType.BYTE)) {
            return;
        }
        SkyWarsGame game = playerGame(event.getPlayer());
        if (game == null || game.hasStarted()) {
            return;
        }
        event.setCancelled(true);
        SkyWars.getInstance().getKitSelectionUI().open(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        SkyWarsGame game = playerGame(player);
        if (game != null && game.getState() != GameState.RUNNING) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        combat.remove(event.getPlayer().getUniqueId());
        // CookieDough may already have removed the wrapper; use each roster as truth.
        for (Game candidate : GameManager.getGames()) {
            if (!(candidate instanceof SkyWarsGame game)) {
                continue;
            }
            CookiePlayer tracked = game.getPlayers().stream()
                    .filter(value -> value.getPlayer().getUniqueId().equals(event.getPlayer().getUniqueId()))
                    .findFirst().orElse(null);
            if (tracked != null) {
                game.removePlayer(tracked, "disconnect");
                return;
            }
        }
    }

    public void clear() {
        combat.clear();
    }

    private SkyWarsGame playerGame(Player player) {
        CookiePlayer cookiePlayer = PlayerManager.getPlayer(player);
        if (cookiePlayer != null && GameManager.getGameOfPlayer(cookiePlayer) instanceof SkyWarsGame game) {
            return game;
        }
        for (Game candidate : GameManager.getGames()) {
            if (candidate instanceof SkyWarsGame game && game.getPlayers().stream()
                    .anyMatch(value -> value.getPlayer().getUniqueId().equals(player.getUniqueId()))) {
                return game;
            }
        }
        return null;
    }

    private SkyWarsGame worldGame(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        return GameManager.getGames().stream()
                .filter(SkyWarsGame.class::isInstance)
                .map(SkyWarsGame.class::cast)
                .filter(game -> game.ownsWorld(location.getWorld()))
                .findFirst().orElse(null);
    }
}
