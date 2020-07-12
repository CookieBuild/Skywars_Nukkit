package main.java.Listeners;

import cn.nukkit.block.BlockChest;
import cn.nukkit.block.BlockTNT;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.level.WeatherChangeEvent;
import main.java.Main;
import main.java.cbPlayer;

public class LevelEventsListener implements Listener {

    private Main plugin;

    public LevelEventsListener(Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWeatherChange(WeatherChangeEvent event) {
        event.setCancelled();
    }


    @EventHandler
    public void onBlockPlaced(BlockPlaceEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (!player.isInGame) {
            event.setCancelled();
        } else {
            if (!this.plugin.game.hasStarted()) {
                event.setCancelled();
            } else {
                if (event.getBlock() instanceof BlockTNT) {
                    this.plugin.getServer().getScheduler().scheduleDelayedTask(() -> {
                        ((BlockTNT) event.getBlock()).prime(80, player);
                    }, 1);
                }
            }
        }
    }


    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        cbPlayer player = (cbPlayer) event.getPlayer();
        if (!player.isInGame) {
            event.setCancelled();
        } else {
            if (!this.plugin.game.hasStarted()) {
                event.setCancelled();
            } else {
                if (event.getBlock() instanceof BlockChest) {
                    BlockChest blockChest = (BlockChest) event.getBlock();
                    event.setCancelled();
                    event.getPlayer().sendMessage("Can't break chests yet! Working on it...");
                }
            }
        }
    }
}
