package main.java;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.TextFormat;
import de.dytanic.cloudnet.ext.bridge.BridgeHelper;
import de.dytanic.cloudnet.ext.bridge.nukkit.NukkitCloudNetHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SkywarsGame extends Game {

    List<Item> normalItems;
    List<Item> normalArmor;
    List<Item> rareItems;

    List<BlockEntityChest> chestsFilled = new ArrayList<>();

    boolean isEndedAlready = false;

    public SkywarsGame(int gameNumber, Server server, Main plugin) {
        super(gameNumber, server, plugin);

        normalItems = new ArrayList<Item>();
        normalArmor = new ArrayList<Item>();
        rareItems = new ArrayList<Item>();

        normalArmor.add(Item.get(Item.LEATHER_CAP));
        normalArmor.add(Item.get(Item.LEATHER_TUNIC));
        normalArmor.add(Item.get(Item.LEATHER_PANTS));
        normalArmor.add(Item.get(Item.LEATHER_BOOTS));

        normalArmor.add(Item.get(Item.IRON_HELMET));
        normalArmor.add(Item.get(Item.IRON_CHESTPLATE));
        normalArmor.add(Item.get(Item.IRON_LEGGINGS));
        normalArmor.add(Item.get(Item.IRON_BOOTS));
        normalArmor.add(Item.get(Item.CHAIN_HELMET));
        normalArmor.add(Item.get(Item.CHAIN_CHESTPLATE));
        normalArmor.add(Item.get(Item.CHAIN_LEGGINGS));
        normalArmor.add(Item.get(Item.CHAIN_BOOTS));


        normalArmor.add(Item.get(Item.GOLD_HELMET));
        normalArmor.add(Item.get(Item.GOLD_CHESTPLATE));
        normalArmor.add(Item.get(Item.GOLD_LEGGINGS));
        normalArmor.add(Item.get(Item.GOLD_BOOTS));

        normalItems.add(Item.get(Item.WOODEN_SWORD));
        normalItems.add(Item.get(Item.GOLDEN_SWORD));
        normalItems.add(Item.get(Item.IRON_SWORD));


        normalItems.add(Item.get(Item.WOODEN_AXE));
        normalItems.add(Item.get(Item.IRON_AXE));
        normalItems.add(Item.get(Item.DIAMOND_AXE));

        normalItems.add(Item.get(Item.IRON_PICKAXE));
        normalItems.add(Item.get(Item.DIAMOND_PICKAXE));

        normalItems.add(Item.get(Item.STEAK));
        normalItems.add(Item.get(Item.BREAD));
        normalItems.add(Item.get(Item.CARROT));


        Item arrows = Item.get(Item.ARROW);
        arrows.setCount(6);
        normalItems.add(arrows);
        normalItems.add(Item.get(Item.BOW));


        Item block1 = Item.get(Item.PLANKS);
        block1.setCount(63);
        normalItems.add(block1);

        Item block2 = Item.get(Item.WOOL);
        block2.setCount(63);
        normalItems.add(block2);


        Item block3 = Item.get(Item.STONE);
        block3.setCount(63);
        normalItems.add(block3);

        Item block4 = Item.get(Item.COBBLESTONE);
        block4.setCount(63);
        normalItems.add(block4);

        rareItems.add(Item.get(Item.TRIDENT));
        rareItems.add(Item.get(Item.TNT));
        rareItems.add(Item.get(Item.GOLDEN_APPLE));
        rareItems.add(Item.get(Item.ELYTRA));
        rareItems.add(Item.get(Item.ENDER_PEARL));
        rareItems.add(Item.get(ItemBlock.DIAMOND_HELMET));
        rareItems.add(Item.get(Item.DIAMOND_CHESTPLATE));
        rareItems.add(Item.get(Item.DIAMOND_LEGGINGS));
        rareItems.add(Item.get(Item.DIAMOND_BOOTS));
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
                winner.sendTitle(TextFormat.GREEN + "> You won the game !", "You received " + coinsGiven + " coins");
                winner.getLevel().addSound(winner.getLocation(), Sound.RANDOM_LEVELUP);
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
                        inventory.setItem(i, normalItems.get(random.nextInt(normalItems.size())));
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


        boolean isAMiddleChest = true;
        for (Vector3 plot : this.plugin.pedestals.get(this.gameNumber)) {
            if (plot.distance(blockEntity.getLocation()) < 10) {
                isAMiddleChest = false;
            }
        }


        for (int i = 0; i < inventory.getSize(); i++) {
            double nextDouble = random.nextDouble();
            if (nextDouble > 0.8) {
                Item item = normalItems.get(random.nextInt(normalItems.size()));
                item.setCount(Integer.max(1, random.nextInt(item.count + 1)));
                inventory.setItem(i, item);
            } else if (nextDouble < 0.2) {
                inventory.setItem(i, normalArmor.get(random.nextInt(normalArmor.size())));
            }


            if (random.nextDouble() > 0.9 && isAMiddleChest) {
                inventory.setItem(i, rareItems.get(random.nextInt(rareItems.size())));
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
