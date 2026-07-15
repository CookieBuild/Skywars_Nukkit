package com.cookiebuild.skywars.map;

import java.util.ArrayList;
import java.util.List;

/** Deterministic 11x11 fallback floor for recovered maps without a lobby cage. */
public final class TemporaryWaitingPlatform {
    private static final int RADIUS = 5;

    private TemporaryWaitingPlatform() { }

    public static List<BlockPosition> blocks(int centerX, int floorY, int centerZ) {
        List<BlockPosition> result = new ArrayList<>((RADIUS * 2 + 1) * (RADIUS * 2 + 1));
        for (int x = centerX - RADIUS; x <= centerX + RADIUS; x++) {
            for (int z = centerZ - RADIUS; z <= centerZ + RADIUS; z++) {
                result.add(new BlockPosition(x, floorY, z));
            }
        }
        return List.copyOf(result);
    }

    public record BlockPosition(int x, int y, int z) { }
}
