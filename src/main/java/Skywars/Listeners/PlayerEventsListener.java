package Skywars.Listeners;

import Skywars.Data.getPlayerDataTask;
import Skywars.Game;
import Skywars.Main;
import Skywars.cbPlayer;
import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.level.Sound;

import java.util.ArrayList;
import java.util.Collection;

public class PlayerEventsListener implements Listener {

    private Main plugin;

    /**
     * Database credentials
     */
    private String address;
    private String databaseName;
    private String username;
    private String password;
    //TODO : Reorganize! Credentials should not be here

    public PlayerEventsListener(Main plugin, String address, String databaseName, String username, String password) {
        this.plugin = plugin;
        this.address = address;
        this.databaseName = databaseName;
        this.username = username;
        this.password = password;
    }

    @EventHandler
    public void onPlayerCreated(PlayerCreationEvent event) {
        event.setPlayerClass(cbPlayer.class);
    }


    @EventHandler
    public void onPlayerPreLoginEvent(PlayerPreLoginEvent event) {
        if (this.plugin.game.state != Game.GAME_OPEN || this.plugin.game.getPlayers().size() >= this.plugin.game.Capacity) {
            event.getPlayer().kick("A game is already running!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().setFoodEnabled(false);
        event.getPlayer().getSkin().setTrusted(true);
        if (this.plugin.isDataBaseEnabled) {
            this.plugin.getServer().getScheduler().scheduleTask(new getPlayerDataTask(event.getPlayer().getName(), this.address, this.databaseName, this.username, this.password), true);
        }

        this.plugin.onPlayerJoinGame((cbPlayer) event.getPlayer());
    }


    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
    }


    @EventHandler
    public void onHealthRegain(EntityRegainHealthEvent event) {
        if (this.plugin.game.hasStarted() && event.getEntity() instanceof cbPlayer) {
            this.plugin.getServer().getScheduler().scheduleDelayedTask(() -> {
                event.getEntity().setNameTag(((cbPlayer) event.getEntity()).getDisplayName() + "\n" + (event.getEntity().getHealth()) + "❤");
            }, 1);
        }
    }

    @EventHandler
    public void onEntityDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof cbPlayer) {
            cbPlayer player = (cbPlayer) event.getEntity();
            if (!player.isInGame) {
                event.setCancelled();
            } else {
                if (!this.plugin.game.hasStarted()) {
                    event.setCancelled();
                } else {

                    if (event instanceof EntityDamageByEntityEvent) {
                        if (((EntityDamageByEntityEvent) event).getDamager() instanceof cbPlayer) {
                            player.lastHitPlayer = (cbPlayer) ((EntityDamageByEntityEvent) event).getDamager();
                            if (event.getCause() == EntityDamageEvent.DamageCause.PROJECTILE) {
                                Collection<Player> playerCollection = new ArrayList<>();
                                playerCollection.add(player.lastHitPlayer);
                                player.lastHitPlayer.getLevel().addSound(player.lastHitPlayer.getLocation(), Sound.RANDOM_ORB, 1, 1, playerCollection);
                            }
                        }

                        this.plugin.getServer().getScheduler().scheduleDelayedTask(() -> {
                            player.setNameTag(player.getDisplayName() + "\n" + (player.getHealth()) + "❤");
                        }, 1);

                    }
                }

                if (!event.isCancelled() && player.getHealth() - event.getFinalDamage() < 1f) {
                    event.setCancelled();
                    this.plugin.simulateDeath(player);
                }
            }
        }
    }


    @EventHandler
    public void onPlayerKicked(PlayerKickEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (player.isInGame) {
            this.plugin.game.removePlayer(player);
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage((String) null);
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (player.isInGame) {
            this.plugin.game.removePlayer(player);
        }
    }
}
