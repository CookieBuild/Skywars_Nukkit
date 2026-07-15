package com.cookiebuild.skywars.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

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
}
