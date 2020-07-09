package main.java.CustomInterface.screens;

import cn.nukkit.event.EventHandler;
import cn.nukkit.event.player.PlayerFormRespondedEvent;
import cn.nukkit.form.window.FormWindowSimple;
import main.java.CustomInterface.elements.ServerButton;
import main.java.cbPlayer;

public class AllServersScreen extends FormWindowSimple {
    public AllServersScreen(String title, String content) {
        super(title, content);


        int mbPlayerCount = 0;
        int bbPlayerCount = 0;
        int swPlayerCount = 0;
        int twPlayerCount = 0;
        int dmPlayerCount = 0;
        int kpPlayerCount = 0;


        addButton(new ServerButton("Lobby", "Lobby-1", 19138));
        //    addButton(new ServerButton("MicroBattles Lobby ", "bb.cookie-build.com", 19133));
        //    addButton(new ServerButton("Skywars " + swPlayerCount + " playing", "mb.cookie-build.com", 19134));
        //  addButton(new ServerButton("TurfWars " + twPlayerCount + " playing", "mb.cookie-build.com", 19135));
        // addButton(new ServerButton("Domination " + dmPlayerCount + " playing", "mb.cookie-build.com", 19137));
        //addButton(new ServerButton("MicroBattles " + mbPlayerCount + " playing", "mb.cookie-build.com", 19132));
    }

    @EventHandler
    public void onResponse(PlayerFormRespondedEvent event) {
        event.getPlayer().getServer().getScheduler().scheduleTask(event.getPlayer().getServer().getPluginManager().getPlugin("Skywars"), () -> {
            int clickedButtonId = getResponse().getClickedButtonId();

            if (clickedButtonId == 0) { // Buy button
                //  event.getPlayer().transfer(new InetSocketAddress("pe.cookie-build.com", 19138));
                ((cbPlayer) event.getPlayer()).proxyTransfer("Lobby-1");
            }
        }, true);


    }

}
