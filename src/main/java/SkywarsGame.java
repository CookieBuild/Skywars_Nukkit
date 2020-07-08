package main.java;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.utils.TextFormat;
import de.dytanic.cloudnet.ext.bridge.BridgeHelper;
import de.dytanic.cloudnet.ext.bridge.nukkit.NukkitCloudNetHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SkywarsGame extends Game {

    List<Item> fillingItems;

    List<BlockEntityChest> chestsFilled = new ArrayList<>();

    boolean isEndedAlready = false;

    public SkywarsGame(int gameNumber, Server server, Main plugin) {
        super(gameNumber, server, plugin);

        fillingItems = new ArrayList<Item>();

        fillingItems.add(Item.get(Item.LEATHER_CAP));
        fillingItems.add(Item.get(Item.LEATHER_TUNIC));
        fillingItems.add(Item.get(Item.LEATHER_PANTS));
        fillingItems.add(Item.get(Item.LEATHER_BOOTS));

        fillingItems.add(Item.get(Item.IRON_HELMET));
        fillingItems.add(Item.get(Item.IRON_CHESTPLATE));
        fillingItems.add(Item.get(Item.IRON_LEGGINGS));
        fillingItems.add(Item.get(Item.IRON_BOOTS));

        fillingItems.add(Item.get(Item.DIAMOND_HELMET));
        fillingItems.add(Item.get(Item.DIAMOND_CHESTPLATE));
        fillingItems.add(Item.get(Item.DIAMOND_LEGGINGS));
        fillingItems.add(Item.get(Item.DIAMOND_BOOTS));

        fillingItems.add(Item.get(Item.GOLD_HELMET));
        fillingItems.add(Item.get(Item.GOLD_CHESTPLATE));
        fillingItems.add(Item.get(Item.GOLD_LEGGINGS));
        fillingItems.add(Item.get(Item.GOLD_BOOTS));

        fillingItems.add(Item.get(Item.WOODEN_SWORD));
        fillingItems.add(Item.get(Item.GOLDEN_SWORD));
        fillingItems.add(Item.get(Item.IRON_SWORD));


        fillingItems.add(Item.get(Item.WOODEN_AXE));
        fillingItems.add(Item.get(Item.IRON_AXE));
        fillingItems.add(Item.get(Item.DIAMOND_AXE));

        fillingItems.add(Item.get(Item.IRON_PICKAXE));
        fillingItems.add(Item.get(Item.DIAMOND_PICKAXE));

        fillingItems.add(Item.get(Item.STEAK));
        fillingItems.add(Item.get(Item.BREAD));
        fillingItems.add(Item.get(Item.CARROT));
        fillingItems.add(Item.get(Item.GOLDEN_APPLE));

        Item arrows = Item.get(Item.ARROW);
        arrows.setCount(6);
        fillingItems.add(arrows);
        fillingItems.add(Item.get(Item.BOW));


        Item block1 = Item.get(Item.PLANKS);
        block1.setCount(32);
        fillingItems.add(block1);

        Item block2 = Item.get(Item.WOOL);
        block2.setCount(32);
        fillingItems.add(block2);

        Item block3 = Item.get(Item.STONE);
        block3.setCount(32);
        fillingItems.add(block3);

        fillingItems.add(Item.get(Item.ENDER_PEARL));
    }

    @Override
    public void addPlayer(cbPlayer player) {
        super.addPlayer(player);
        if (startTimer == 0 && this.getPlayers().size() >= 2) {
            startTimer += 1;
            this.server.getLogger().info("Starting game start cooldown...");
        }
    }

    @Override
    public void startGame() {
        super.startGame();
        NukkitCloudNetHelper.setState("RUNNING");
        BridgeHelper.updateServiceInfo();
        /*
        for (Vector3 plot : this.plugin.pedestals.get(this.gameNumber)) {
            this.server.getLevelByName(this.plugin.gameMapName).setBlock(plot.add(0, -1), Block.get(Block.AIR));
        }*/

    }

    @Override
    public boolean isGameEnded() {
        if (this.hasStarted() && getPlayers().size() <= 1 && !isEndedAlready) {
            isEndedAlready = true;
            if (getPlayers().size() == 1) {

                cbPlayer winner = this.getPlayers().get(0);

                for (Player p : this.server.getOnlinePlayers().values()) {
                    p.sendMessage(TextFormat.GREEN + "> The winner is " + winner.getDisplayName() + "!");
                }


                int coinsGiven = plugin.giveCoins(winner, 8);
                winner.sendMessage(TextFormat.GREEN + "> You won the game ! You received " + coinsGiven + " coins");
                this.getPlayers().remove(winner);
            }

            for (Player p : this.server.getOnlinePlayers().values()) {

                if (this.plugin.isProxyEnabled) {

                    this.server.getScheduler().scheduleDelayedTask(() -> {
                        ((cbPlayer) p).proxyTransfer("Lobby-1");
                    }, 60);
                    // We kick again if there are still a proxy player


                } else {

                    this.server.getScheduler().scheduleDelayedTask(() -> {
                        p.kick("End of game.");
                    }, 60);
                    // We kick again if there are still a proxy player

                }


            }


            // Game has ended. Everyone is gone, time to reset

            this.server.getScheduler().scheduleDelayedTask(() -> {
                this.resetGame();
            }, 80);
            return true;
        } else {
            return false;
        }

    }

    private int refillChests() {
        int numberOfChests = 0;
        Random random = new Random();
        int numverOfBlockEntites = 0;
        for (BlockEntity blockEntity : this.server.getLevelByName(this.plugin.gameMapName).getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntityChest) {
                numberOfChests++;

                Inventory inventory = ((BlockEntityChest) blockEntity).getInventory();
                for (int i = 0; i > inventory.getSize(); i++) {
                    if (random.nextDouble() > 0.8) {
                        inventory.setItem(i, fillingItems.get(random.nextInt(fillingItems.size())));
                    }
                }

            }
        }


        return numberOfChests;
    }


    public void fillChest(BlockEntityChest blockEntity) {
        if (chestsFilled.contains(blockEntity)) {
            return;
        }
        chestsFilled.add(blockEntity);
        Random random = new Random();
        Inventory inventory = ((BlockEntityChest) blockEntity).getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (random.nextDouble() > 0.6) {
                inventory.setItem(i, fillingItems.get(random.nextInt(fillingItems.size())));
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.hasStarted()) {
            this.isGameEnded();
        }
    }

    @Override
    public void resetGame() {
        super.resetGame();

        isEndedAlready = false;
        if (!this.plugin.gameMapName.equals("game")) { // If it's not first game since boot
            this.server.unloadLevel(this.server.getLevelByName(this.plugin.gameMapName), true);
        }


        this.gameNumber = new Random().nextInt(this.plugin.pedestals.size());
        this.Capacity = this.plugin.pedestals.get(this.gameNumber).size();

        this.server.setMaxPlayers(this.Capacity);

        this.plugin.gameMapName = "game-" + this.gameNumber;
        this.server.getLogger().info("Game in creation! " + this.plugin.gameMapName);
        this.server.loadLevel(this.plugin.gameMapName);
        Level level = this.server.getLevelByName(this.plugin.gameMapName);
        level.setTime(6000);
        level.stopTime();
        level.setRaining(false);
//        for(int x = 0; x < 1000; x+=16){
//            for(int z = 0; z < 1000; z+=16){
//                level.loadChunk(x,z,false);
//            }
//        }

        int chestFilled = this.refillChests();
        this.server.getLogger().info("Game " + this.gameNumber + " ready! refilled " + chestFilled + " chests!");
        if (chestsFilled != null) {
            chestsFilled.clear();
        }

        for (Player p : this.server.getOnlinePlayers().values()) {
            p.kick("End of game.");
            // We kick again if there are still a proxy player
        }
//
//        chestsFilled.clear();
        NukkitCloudNetHelper.setState("OPEN");
        NukkitCloudNetHelper.setMaxPlayers(Capacity);
        BridgeHelper.updateServiceInfo();

    }
}
