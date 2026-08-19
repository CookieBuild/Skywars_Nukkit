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

    /** Two-block-high glass guardrail that makes the generated deck safe to explore. */
    public static List<BlockPosition> guardrails(int centerX, int floorY, int centerZ) {
        List<BlockPosition> result = new ArrayList<>((RADIUS * 2 + 1) * 8);
        for (int y = floorY + 1; y <= floorY + 2; y++) {
            for (int offset = -RADIUS; offset <= RADIUS; offset++) {
                result.add(new BlockPosition(centerX - RADIUS, y, centerZ + offset));
                result.add(new BlockPosition(centerX + RADIUS, y, centerZ + offset));
                if (offset != -RADIUS && offset != RADIUS) {
                    result.add(new BlockPosition(centerX + offset, y, centerZ - RADIUS));
                    result.add(new BlockPosition(centerX + offset, y, centerZ + RADIUS));
                }
            }
        }
        return List.copyOf(result);
    }

    public record BlockPosition(int x, int y, int z) { }
}
