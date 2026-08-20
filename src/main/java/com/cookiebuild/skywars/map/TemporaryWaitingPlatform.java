package com.cookiebuild.skywars.map;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;

/** Deterministic cookie-themed fallback podium for recovered maps without a lobby cage. */
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

    /** Four corner pillars and an open roof frame make the deck read as a deliberate pregame cage. */
    public static List<BlockPosition> frame(int centerX, int floorY, int centerZ) {
        List<BlockPosition> result = new ArrayList<>();
        for (int x : List.of(centerX - RADIUS, centerX + RADIUS)) {
            for (int z : List.of(centerZ - RADIUS, centerZ + RADIUS)) {
                for (int y = floorY + 1; y <= floorY + 4; y++) {
                    result.add(new BlockPosition(x, y, z));
                }
            }
        }
        for (int offset = -RADIUS; offset <= RADIUS; offset++) {
            result.add(new BlockPosition(centerX + offset, floorY + 4, centerZ - RADIUS));
            result.add(new BlockPosition(centerX + offset, floorY + 4, centerZ + RADIUS));
            if (offset != -RADIUS && offset != RADIUS) {
                result.add(new BlockPosition(centerX - RADIUS, floorY + 4, centerZ + offset));
                result.add(new BlockPosition(centerX + RADIUS, floorY + 4, centerZ + offset));
            }
        }
        return List.copyOf(new java.util.LinkedHashSet<>(result));
    }

    public static Material floorMaterial(String mapName, int centerX, int centerZ, BlockPosition block) {
        int dx = block.x() - centerX;
        int dz = block.z() - centerZ;
        if (Math.abs(dx) == RADIUS || Math.abs(dz) == RADIUS) {
            return Material.POLISHED_BLACKSTONE_BRICKS;
        }
        if (dx * dx + dz * dz <= 16) {
            int chip = Math.floorMod(dx * 31 + dz * 17 + mapName.hashCode(), 11);
            return chip <= 1 ? Material.BROWN_CONCRETE : Material.ORANGE_TERRACOTTA;
        }
        return Material.LIGHT_BLUE_STAINED_GLASS;
    }

    public static Material accentMaterial(String mapName) {
        return switch (mapName) {
            case "legacy-0" -> Material.ORANGE_STAINED_GLASS;
            case "legacy-1" -> Material.LIGHT_BLUE_STAINED_GLASS;
            case "legacy-2" -> Material.LIME_STAINED_GLASS;
            case "legacy-4" -> Material.YELLOW_STAINED_GLASS;
            default -> Material.CYAN_STAINED_GLASS;
        };
    }

    public record BlockPosition(int x, int y, int z) { }
}
