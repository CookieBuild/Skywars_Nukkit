package Skywars;

import Skywars.Data.PlayerData;
import cn.nukkit.Player;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.ScriptCustomEventPacket;
import cn.nukkit.utils.TextFormat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.net.InetSocketAddress;


/**
 * Created by Guillaume on 17/01/2017.
 * ▒█▀▀█ █▀▀█ █▀▀█ █░█ ░▀░ █▀▀ 　 ▒█▀▀█ █░░█ ░▀░ █░░ █▀▀▄
 * ▒█░░░ █░░█ █░░█ █▀▄ ▀█▀ █▀▀ 　 ▒█▀▀▄ █░░█ ▀█▀ █░░ █░░█
 * ▒█▄▄█ ▀▀▀▀ ▀▀▀▀ ▀░▀ ▀▀▀ ▀▀▀ 　 ▒█▄▄█ ░▀▀▀ ▀▀▀ ▀▀▀ ▀▀▀░
 */
public class cbPlayer extends Player {


    public boolean isInGame = false;
    public int vip = -1;
    public cbPlayer lastHitPlayer = null;
    public boolean isFetchingData = true;
    public int coins = 0;

    //public Party party, pendingInvite;
    public PlayerData storedPlayerData;

    public cbPlayer(SourceInterface interfaz, Long clientID, InetSocketAddress socketAddress) {
        super(interfaz, clientID, socketAddress);
    }

    /**
     * Transfer a player through Waterdog proxy
     *
     * @param destination
     * @return
     */
    public boolean proxyTransfer(String destination) {
        ScriptCustomEventPacket pk = new ScriptCustomEventPacket();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        DataOutputStream a = new DataOutputStream(out);
        try {
            a.writeUTF("Connect");
            a.writeUTF(destination);
            pk.eventName = "bungeecord:main";
            pk.eventData = out.toByteArray();
            this.dataPacket(pk);
        } catch (Exception e) {
            this.getServer().getLogger().warning("Error while transferring ( PLAYER: " + this.getName() + " | DEST: " + destination + " )");
            this.getServer().getLogger().logException(e);
            return false;
        }
        return true;
    }

    public void loginComplete() {
        this.getServer().getScheduler().scheduleDelayedTask(() -> {
            this.sendMessage(TextFormat.GREEN + "Welcome to " + TextFormat.AQUA + "Cookie " + TextFormat.YELLOW + "Build" + TextFormat.DARK_BLUE + " SkyWars!");

        }, 80);
    }

    public int getCoins() {
        return coins;
    }



}
