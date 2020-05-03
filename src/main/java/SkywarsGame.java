package main.java;

import cn.nukkit.Server;

public class SkywarsGame extends Game {
    public SkywarsGame(int gameNumber, Server server, Main plugin) {
        super(gameNumber, server, plugin);
    }

    @Override
    public boolean isGameEnded() {
        return false;
    }
}
