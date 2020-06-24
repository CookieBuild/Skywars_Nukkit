package main.java;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.utils.TextFormat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SkywarsGame extends Game {

    List<Item> fillingItems;

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
        fillingItems.add(Item.get(Item.IRON_SWORD));


        Item arrows = Item.get(Item.ARROW);
        arrows.setCount(6);
        fillingItems.add(arrows);
        fillingItems.add(Item.get(Item.BOW));

        fillingItems.add(Item.get(Item.ENDER_PEARL));
    }

    @Override
    public boolean isGameEnded() {
        if (this.hasStarted() && getPlayers().size() <= 1) {

            if (getPlayers().size() == 1) {
                cbPlayer winner = this.getPlayers().get(0);
                plugin.giveCoins(winner, 8);
                winner.sendMessage(TextFormat.GREEN + "> You won the game ! You received 8 coins");
            }

            for (Player p : this.server.getOnlinePlayers().values()) {

                if (this.plugin.isProxyEnabled) {
                    ((cbPlayer) p).proxyTransfer("BbLobby-1");
                } else {
                    p.kick("End of game.");
                }


            }

            for (Player p : this.server.getOnlinePlayers().values()) {
                p.kick("End of game.");
                // We kick again if there are still a proxy player
            }
            // Game has ended. Everyone is gone, time to reset
            this.resetGame();

            // Unload + reload to reset map
            this.server.unloadLevel(this.server.getLevelByName("game-" + this.gameNumber), true);
            this.server.loadLevel("game-" + this.gameNumber);

            return true;
        } else {
            return false;
        }

    }

    private int refillChests() {
        int numberOfChests = 0;
        Random random = new Random();
        for (BlockEntity blockEntity : this.server.getLevelByName("game").getBlockEntities().values()) {
            if (blockEntity instanceof BlockEntityChest) {
                numberOfChests++;

                Inventory inventory = ((BlockEntityChest) blockEntity).getInventory();
                for (int i = 0; i > inventory.getSize(); i++) {
                    if (random.nextDouble() > 0.6) {
                        inventory.setItem(i, fillingItems.get(random.nextInt(fillingItems.size())));
                    }
                }

            }
        }


        return numberOfChests;
    }

}
