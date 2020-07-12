package main.java;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.Listener;
import cn.nukkit.event.inventory.InventoryOpenEvent;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.event.player.PlayerItemHeldEvent;
import cn.nukkit.inventory.InventoryType;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.TextFormat;
import main.java.CustomInterface.screens.AllServersScreen;
import main.java.Data.dataBaseQuery;
import main.java.Listeners.LevelEventsListener;
import main.java.Listeners.PlayerEventsListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Main extends PluginBase implements Listener {

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


        this.getServer().getPluginManager().registerEvents(new PlayerEventsListener(this, address, databaseName, username, password), this);
        this.getServer().getPluginManager().registerEvents(new LevelEventsListener(this), this);
        this.getServer().getPluginManager().registerEvents(this, this);

        this.game = new SkywarsGame(new Random().nextInt(numberOfMaps), this.getServer(), this);

        this.getServer().getScheduler().scheduleRepeatingTask(() -> {
            this.game.tick();
        }, 20);

        this.getServer().getScheduler().scheduleRepeatingTask(this::sendPopups, 10);
    }


    @EventHandler
    public void onItemHeldEvent(PlayerItemHeldEvent event) {
        if (event.getPlayer().isSpectator() && this.isProxyEnabled) {
            if (event.getItem().getId() == Item.COMPASS) {
                event.getPlayer().showFormWindow(new AllServersScreen(TextFormat.GREEN + "Go back to lobby", "Press the button to leave the game"));
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (event.getInventory().getType() == InventoryType.CHEST) {
            if (event.getInventory().getHolder() instanceof BlockEntityChest) {
                this.game.fillChest((BlockEntityChest) event.getInventory().getHolder());
            }
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.NORMAL)
    public void onFormResponse(PlayerFormRespondedEvent event) {
        if (event.getResponse() == null || event.getWindow() == null) return;

        if ((event.getWindow() instanceof AllServersScreen)) {

            ((AllServersScreen) event.getWindow()).onResponse(event);
        }

    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof cbPlayer)) {
            return false;
        }
        switch (command.getName()) {
            case "/coins":
                sender.sendMessage(TextFormat.GREEN + "> You have " + TextFormat.YELLOW + (((cbPlayer) sender).getCoins()) + TextFormat.GREEN + " coins!");
                return true;
            default:
                break;
        }
        return super.onCommand(sender, command, label, args);
    }

    public void sendPopups() {
        String playerCount = this.getServer().getOnlinePlayers().size() + "/" + game.Capacity;

        for (Player player : this.getServer().getOnlinePlayers().values()) {


            if (this.game.state == Game.GAME_OPEN) {
                String text = TextFormat.RED + (game.startTimer > 0 ? " Starting in " + (game.START_DELAY - game.startTimer) + " " : " Waiting for players to join... ") + TextFormat.BLUE + playerCount;
                player.sendPopup(text);
            } else {

                String text = "" + TextFormat.YELLOW + game.getPlayers().size() + " " + TextFormat.GREEN + "players remaining";
                player.sendPopup(text);
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
                player.lastHitPlayer.sendMessage(TextFormat.GREEN + "[" + gameModeName + "] You earned " + coinsGiven + " coins for killing " + player.getDisplayName());

                for (Player p : this.getServer().getOnlinePlayers().values()) {
                    p.sendMessage(TextFormat.GREEN + "> " + TextFormat.RESET + player.lastHitPlayer.getDisplayName() + TextFormat.GREEN + " killed " + TextFormat.RESET + player.getDisplayName() + TextFormat.GREEN + "!");
                }

                player.lastHitPlayer = null;
            } else {
                for (Player p : this.getServer().getOnlinePlayers().values()) {
                    p.sendMessage(TextFormat.GREEN + "> " + TextFormat.RESET + player.getDisplayName() + TextFormat.GREEN + " fell off!");
                }
            }


            player.getInventory().clearAll();

            player.setHealth(20);
            player.fireTicks = 0;
            player.setOnFire(0);
            player.removeAllEffects();
            player.getFoodData().setFoodLevel(20);
            player.setFoodEnabled(false);

            player.setGamemode(Player.SPECTATOR);
            player.sendTitle("You are now spectating");

            Item compass = Item.get(Item.COMPASS);
            compass.setCustomName("Go back to lobby");
            player.getInventory().setItem(8, compass);

            if (player.getY() <= 0) {
                player.teleport(player.getPosition().add(0, 60 - player.getY()));
            }

        }
    }


    public void onPlayerJoinGame(cbPlayer player) {
        game.addPlayer(player);
        player.setCheckMovement(false);
        if (game.getPlayers().size() == game.Capacity) {
            game.startGame();
        }
    }


    public void teleportToGame(cbPlayer player) {
        player.teleport(Location.fromObject(this.pedestals.get(this.game.gameNumber).get(this.game.getPlayers().indexOf(player)), this.getServer().getLevelByName(gameMapName)));
        player.setFoodEnabled(false);
        player.removeAllEffects();
        player.setHealth(20);
        player.setNameTag(player.getDisplayName() + "\n" + (player.getHealth() + "❤"));
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
