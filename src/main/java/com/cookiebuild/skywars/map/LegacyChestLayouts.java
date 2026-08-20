package com.cookiebuild.skywars.map;

import java.util.List;
import java.util.Map;

/**
 * Built-in metadata for the versioned legacy archives. Config may repeat these
 * values, while this copy keeps upgraded servers safe when an existing config
 * predates authored chest tiers.
 */
final class LegacyChestLayouts {
    static final int MID_CHESTS_PER_MAP = 8;

    private static final Map<String, List<List<Integer>>> MID_CHESTS = Map.of(
            "legacy-0", List.of(
                    point(87, 45, 640), point(87, 45, 650),
                    point(90, 57, 645), point(100, 62, 641),
                    point(100, 62, 649), point(110, 57, 645),
                    point(113, 45, 640), point(113, 45, 650)),
            "legacy-1", List.of(
                    point(-1605, 51, -799), point(-1599, 40, -799),
                    point(-1598, 47, -809), point(-1589, 49, -802),
                    point(-1589, 49, -796), point(-1580, 47, -790),
                    point(-1579, 40, -799), point(-1574, 50, -799)),
            "legacy-2", List.of(
                    point(-493, 43, 367), point(-487, 43, 357),
                    point(-487, 43, 377), point(-477, 41, 367),
                    point(-477, 51, 367), point(-467, 43, 357),
                    point(-467, 43, 377), point(-461, 43, 367)),
            "legacy-4", List.of(
                    point(183, 67, 203), point(185, 69, 203),
                    point(186, 67, 200), point(186, 67, 206),
                    point(186, 69, 202), point(186, 69, 204),
                    point(187, 69, 203), point(189, 67, 203)));

    private LegacyChestLayouts() {
    }

    static List<?> midChests(String mapName) {
        List<List<Integer>> positions = MID_CHESTS.get(mapName);
        if (positions == null) {
            throw new IllegalArgumentException(mapName + ".mid-chests is missing");
        }
        return positions;
    }

    private static List<Integer> point(int x, int y, int z) {
        return List.of(x, y, z);
    }
}
