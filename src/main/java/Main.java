package main.java;

import cn.nukkit.event.EventHandler;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.player.*;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import main.java.Data.dataBaseQuery;
import main.java.Data.getPlayerDataTask;

import java.util.ArrayList;
import java.util.List;

public class Main extends PluginBase {

    public SkywarsGame game;
    public List<Vector3> pedestals = new ArrayList<>();
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
    public void onEnable(){
        Config config = this.getConfig();
        this.isDataBaseEnabled = config.getBoolean("database_enabled");
        this.isProxyEnabled = config.getBoolean("proxy_enabled");

        if (this.isDataBaseEnabled) {
            this.address = config.getString("database_address");
            this.databaseName = config.getString("database_name");
            this.username = config.getString("database_user");
            this.password = config.getString("database_password");
        }

        this.game = new SkywarsGame(0, this.getServer(), this);

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
    public void onEntityDamaged(EntityDamageEvent event){
        if(event.getEntity() instanceof cbPlayer){
            cbPlayer player = (cbPlayer) event.getEntity();
            if (!player.isInGame) {
                event.setCancelled();
            } else {
                if (!this.game.hasStarted()) {
                    event.setCancelled();
                }
            }
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event){

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


    public void onPlayerJoinGame(cbPlayer player){
        //TODO

    }


    public void teleportToGame(cbPlayer player){
        //TODO
    }


    public void giveCoins(cbPlayer player, int coins) {
        if (this.isDataBaseEnabled) {
            player.coins += coins;
            player.storedPlayerData.coins += coins;
            String query = "UPDATE data set playerCoins = (playerCoins + " + coins + ") where playerName = '" + player.getName() + "' ;";
            this.getServer().getScheduler().scheduleTask(new dataBaseQuery(query, address, databaseName, username, password), true);
        }
    }


}
