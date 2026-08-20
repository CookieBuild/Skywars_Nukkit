package com.cookiebuild.skywars.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

class LootTableTest {
    @Test
    void everySeededIslandChestHasTheCompetitiveMinimumContract() {
        for (int seed = 0; seed < 10_000; seed++) {
            LootTable.Selection roll = LootTable.select(ChestTier.ISLAND, 5, new Random(seed));

            assertEquals(EnumSet.of(LootTable.Family.BLOCKS, LootTable.Family.MELEE,
                    LootTable.Family.FOOD, LootTable.Family.ARMOR, LootTable.Family.UTILITY), roll.families());
            assertFalse(roll.containsPremium());
            assertUniqueMaterials(roll);
        }
    }

    @Test
    void intermediateChestsCannotUnlockPowerThatIslandChestsDoNotHave() {
        Set<Material> islandMaterials = eligibleMaterials(ChestTier.ISLAND);
        Set<Material> intermediateMaterials = eligibleMaterials(ChestTier.INTERMEDIATE);
        assertEquals(islandMaterials, intermediateMaterials,
                "legacy map chest-count differences must not unlock exclusive iron, bow or explosive gear");

        for (int seed = 0; seed < 10_000; seed++) {
            LootTable.Selection roll = LootTable.select(ChestTier.INTERMEDIATE, 6, new Random(seed));

            assertEquals(EnumSet.of(LootTable.Family.BLOCKS, LootTable.Family.MELEE,
                    LootTable.Family.FOOD, LootTable.Family.ARMOR, LootTable.Family.UTILITY),
                    roll.families());
            assertFalse(roll.containsPremium());
            assertUniqueMaterials(roll);
        }
    }

    @Test
    void everySeededMiddleChestContainsPremiumLootAndNoDuplicateEquipment() {
        Set<Material> observedPremium = new HashSet<>();
        for (int seed = 0; seed < 10_000; seed++) {
            LootTable.Selection roll = LootTable.select(ChestTier.MID, 8, new Random(seed));

            assertTrue(roll.containsPremium());
            assertTrue(roll.families().contains(LootTable.Family.PREMIUM));
            assertUniqueMaterials(roll);
            roll.entries().stream().map(LootTable.Entry::material)
                    .filter(material -> material == Material.ENDER_PEARL || material == Material.GOLDEN_APPLE
                            || material == Material.DIAMOND_SWORD || material == Material.DIAMOND_CHESTPLATE)
                    .forEach(observedPremium::add);
        }
        assertEquals(Set.of(Material.ENDER_PEARL, Material.GOLDEN_APPLE,
                Material.DIAMOND_SWORD, Material.DIAMOND_CHESTPLATE), observedPremium);
    }

    private static void assertUniqueMaterials(LootTable.Selection roll) {
        Set<Material> materials = new HashSet<>();
        roll.entries().forEach(item -> assertTrue(materials.add(item.material()),
                () -> "duplicate material " + item.material()));
    }

    private static Set<Material> eligibleMaterials(ChestTier tier) {
        Set<Material> materials = new HashSet<>();
        LootTable.entries().stream()
                .filter(entry -> entry.tiers().contains(tier))
                .map(LootTable.Entry::material)
                .forEach(materials::add);
        return materials;
    }
}
