package main.java;

import cn.nukkit.Player;
import cn.nukkit.Server;

public class SkywarsGame extends Game {
    public SkywarsGame(int gameNumber, Server server, Main plugin) {
        super(gameNumber, server, plugin);
    }

    @Override
    public boolean isGameEnded() {
        if (this.hasStarted() && getPlayers().size() <= 1) {

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
}
