package com.cookiebuild.skywars.ui.bedrock;

import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;

/** Keeps Java-only development servers functional when Floodgate is absent. */
public final class BedrockUIHelper {
    private static final boolean FLOODGATE_AVAILABLE;

    static {
        boolean available;
        try {
            Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            available = true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            available = false;
        }
        FLOODGATE_AVAILABLE = available;
    }

    private BedrockUIHelper() { }

    public static boolean isBedrockPlayer(Player player) {
        if (!FLOODGATE_AVAILABLE) return false;
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(player.getUniqueId());
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }
}
