package com.cookiebuild.skywars.loot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class LootTableTest {
    @Test
    void normalIslandPoolExcludesMiddlePowerItems() {
        assertTrue(LootTable.entries().stream().anyMatch(entry -> entry.material() == Material.OAK_PLANKS));
        assertTrue(LootTable.entries().stream().filter(LootTable.Entry::middleOnly)
                .anyMatch(entry -> entry.material() == Material.ENDER_PEARL));
        assertFalse(LootTable.entries().stream().filter(entry -> !entry.middleOnly())
                .anyMatch(entry -> entry.material() == Material.DIAMOND_CHESTPLATE));
    }
}
