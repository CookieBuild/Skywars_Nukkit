package com.cookiebuild.skywars.kit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class SkyWarsKitTest {
    @Test
    void launchKitsAreSidegradesAndOnlyScoutIsFree() {
        assertTrue(SkyWarsKit.SCOUT.defaultUnlocked());
        assertEquals(0, SkyWarsKit.SCOUT.price());

        for (SkyWarsKit kit : SkyWarsKit.values()) {
            assertFalse(kit.items().isEmpty());
            assertNotNull(kit.description());
            assertTrue(kit.items().stream().noneMatch(item -> item.material() == Material.DIAMOND_CHESTPLATE));
            if (kit != SkyWarsKit.SCOUT) {
                assertFalse(kit.defaultUnlocked());
                assertTrue(kit.price() >= 100 && kit.price() <= 150);
            }
        }
    }

    @Test
    void namesAreResolvedCaseInsensitively() {
        assertEquals(SkyWarsKit.ARMORER, SkyWarsKit.fromName("armorer"));
        assertEquals(SkyWarsKit.HEALER, SkyWarsKit.fromName("Healer"));
    }
}
