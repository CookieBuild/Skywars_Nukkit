package main.java;

import cn.nukkit.Player;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.item.Item;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import main.java.Data.dataBaseQuery;
import main.java.Data.getPlayerDataTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends PluginBase {

    public final String gameModeName = "SkyWars";

    public SkywarsGame game;
    public List<List<Vector3>> pedestals = new ArrayList<>();
    public boolean isDataBaseEnabled = false;
    public boolean isProxyEnabled = false;
    String gameMapName = "game";

    /**
     * Database credentials
     */
    private String address;
    private String databaseName;
    private String username;
    private String password;

    @Override
    public void onEnable() {
        Config config = this.getConfig();
        this.isDataBaseEnabled = config.getBoolean("database_enabled");
        this.isProxyEnabled = config.getBoolean("proxy_enabled");

        if (this.isDataBaseEnabled) {
            this.address = config.getString("database_address");
            this.databaseName = config.getString("database_name");
            this.username = config.getString("database_user");
            this.password = config.getString("database_password");
        }

        int numberOfMaps = config.getInt("maps");

        // Load all the coordinates
        for (int i = 0; i < numberOfMaps; i++) {
            int numberOfPlots = config.getInt("" + i + "_game_size");
            List<Vector3> plots = new ArrayList<>();
            for (int k = 0; k < numberOfPlots; k++) {
                String[] xyz = config.getString("" + i + "_plot_" + k).split(",");
                plots.add(new Vector3(Integer.parseInt(xyz[0].trim()) + 0.5, Integer.parseInt(xyz[1].trim()) + 0.5, Integer.parseInt(xyz[2].trim()) + 0.5));
            }
            pedestals.add(plots);
        }


        this.game = new SkywarsGame(new Random().nextInt(numberOfMaps), this.getServer(), this);

    }

    @EventHandler
    public void onPlayerCreated(PlayerCreationEvent event) {
        event.setPlayerClass(cbPlayer.class);
    }


    @EventHandler
    public void onPlayerPreLoginEvent(PlayerPreLoginEvent event) {
        if (this.game.state != Game.GAME_OPEN) {
            event.getPlayer().kick("A game is already running!");
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().setFoodEnabled(false);
        event.setJoinMessage("");

        if (isDataBaseEnabled) {
            this.getServer().getScheduler().scheduleTask(new getPlayerDataTask(event.getPlayer().getName(), address, databaseName, username, password), true);
        }

        this.onPlayerJoinGame((cbPlayer) event.getPlayer());
    }


    @EventHandler
    public void onPlayerKicked(PlayerKickEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (player.isInGame) {
            this.game.removePlayer(player);
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.setQuitMessage((String) null);
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (player.isInGame) {
            this.game.removePlayer(player);
        }
    }

    @EventHandler
    public void onEntityDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof cbPlayer) {
            cbPlayer player = (cbPlayer) event.getEntity();
            if (!player.isInGame) {
                event.setCancelled();
            } else {
                if (!this.game.hasStarted()) {
                    event.setCancelled();
                } else {
                    if (event instanceof EntityDamageByEntityEvent) {
                        if (((EntityDamageByEntityEvent) event).getDamager() instanceof cbPlayer) {
                            cbPlayer damager = (cbPlayer) ((EntityDamageByEntityEvent) event).getDamager();
                            player.lastHitPlayer = damager;
                        }
                    }
                }

                if (!event.isCancelled() && player.getHealth() - event.getFinalDamage() < 1f) {
                    event.setCancelled();
                    this.simulateDeath(player);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
    }

    @EventHandler
    public void onBlockPlaced(BlockPlaceEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (!player.isInGame) {
            event.setCancelled();
        } else {
            if (!this.game.hasStarted()) {
                event.setCancelled();
            }
        }
    }

    @EventHandler
    public void onBlockBreaked(BlockBreakEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (!player.isInGame) {
            event.setCancelled();
        } else {
            if (!this.game.hasStarted()) {
                event.setCancelled();
            }
        }
    }

    public void simulateDeath(cbPlayer player) {
        if (player.isInGame) {
            player.isInGame = false;
            this.game.removePlayer(player);
            player.sendMessage(TextFormat.RED + "> You have been eliminated !");


            for (Item item : player.getInventory().getContents().values()) {
                player.getLevel().dropItem(player.getLocation(), item);
            }

            if (player.lastHitPlayer != null && isDataBaseEnabled) {
                int coinsGiven = this.giveCoins(player.lastHitPlayer, 1);
                player.lastHitPlayer.sendMessage(TextFormat.GREEN + "[" + gameModeName + "] You earned " + coinsGiven + "coins for killing " + player.getNameTag().replace("\n", " "));

                player.lastHitPlayer = null;
            }

            player.getInventory().clearAll();

            player.setHealth(20);
            player.fireTicks = 0;
            player.setOnFire(0);
            player.removeAllEffects();
            player.getFoodData().setFoodLevel(20);
            player.setFoodEnabled(false);

            player.setGamemode(Player.SPECTATOR);

        }
    }


    public void onPlayerJoinGame(cbPlayer player) {
        //TODO

    }


    public void teleportToGame(cbPlayer player) {
        //TODO
    }


    /**
     * Add coin to the player data
     *
     * @param player
     * @param coins
     * @return The amount of coins given (depending on rank, bonus...)
     */
    public int giveCoins(cbPlayer player, int coins) {
        if (this.isDataBaseEnabled) {
            int coinMultiplier = 1;
            if (player.hasPermission("group.vip")) {
                coinMultiplier = 2;
            }
            if (player.hasPermission("group.vip+")) {
                coinMultiplier = 3;
            }
            if (player.hasPermission("group.legend")) {
                coinMultiplier = 4;
            }
            if (player.hasPermission("group.titan")) {
                coinMultiplier = 5;
            }

            int amount = coins * coinMultiplier;

            player.coins += amount;
            player.storedPlayerData.coins += amount;
            String query = "UPDATE data set playerCoins = (playerCoins + " + amount + ") where playerName = '" + player.getName() + "' ;";
            this.getServer().getScheduler().scheduleTask(new dataBaseQuery(query, address, databaseName, username, password), true);
            return amount;

        }
        return coins;
    }


}
