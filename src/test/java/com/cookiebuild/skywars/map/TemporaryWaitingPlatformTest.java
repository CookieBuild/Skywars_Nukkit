package com.cookiebuild.skywars.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class TemporaryWaitingPlatformTest {
    @Test
    void createsAUniqueElevenByElevenFloorAroundTheConfiguredSpawn() {
        List<TemporaryWaitingPlatform.BlockPosition> blocks = TemporaryWaitingPlatform.blocks(-476, 65, 367);

        assertEquals(121, blocks.size());
        assertEquals(121, new HashSet<>(blocks).size());
        assertTrue(blocks.contains(new TemporaryWaitingPlatform.BlockPosition(-476, 65, 367)));
        assertTrue(blocks.contains(new TemporaryWaitingPlatform.BlockPosition(-481, 65, 362)));
        assertTrue(blocks.contains(new TemporaryWaitingPlatform.BlockPosition(-471, 65, 372)));
    }

    @Test
    void surroundsTheFallbackDeckWithAUniqueTwoBlockGuardrail() {
        List<TemporaryWaitingPlatform.BlockPosition> blocks =
                TemporaryWaitingPlatform.guardrails(-476, 65, 367);

        assertEquals(80, blocks.size());
        assertEquals(80, new HashSet<>(blocks).size());
        assertTrue(blocks.contains(new TemporaryWaitingPlatform.BlockPosition(-481, 66, 367)));
        assertTrue(blocks.contains(new TemporaryWaitingPlatform.BlockPosition(-471, 67, 367)));
    }

    @Test
    void buildsAFramedCookiePodiumWithPerMapAccents() {
        List<TemporaryWaitingPlatform.BlockPosition> frame =
                TemporaryWaitingPlatform.frame(100, 70, 200);

        assertEquals(52, frame.size());
        assertEquals(52, new HashSet<>(frame).size());
        Material center = TemporaryWaitingPlatform.floorMaterial(
                "legacy-0", 100, 200, new TemporaryWaitingPlatform.BlockPosition(100, 70, 200));
        assertTrue(center == Material.BROWN_CONCRETE || center == Material.ORANGE_TERRACOTTA);
        assertEquals(Material.YELLOW_STAINED_GLASS,
                TemporaryWaitingPlatform.accentMaterial("legacy-4"));
    }
}
