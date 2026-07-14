package com.cookiebuild.skywars.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/** Balanced, data-free loot policy. World chests are populated lazily once. */
public final class LootTable {
    public record Entry(Material material, int minAmount, int maxAmount, int weight, boolean middleOnly) {
        public Entry {
            if (material == null || minAmount < 1 || maxAmount < minAmount || weight < 1) {
                throw new IllegalArgumentException("Invalid loot entry");
            }
        }
    }

    private static final List<Entry> ENTRIES = List.of(
            new Entry(Material.OAK_PLANKS, 16, 32, 14, false),
            new Entry(Material.COBBLESTONE, 16, 32, 12, false),
            new Entry(Material.COOKED_BEEF, 3, 8, 8, false),
            new Entry(Material.EGG, 4, 12, 7, false),
            new Entry(Material.SNOWBALL, 4, 12, 7, false),
            new Entry(Material.ARROW, 4, 10, 6, false),
            new Entry(Material.BOW, 1, 1, 3, false),
            new Entry(Material.FISHING_ROD, 1, 1, 3, false),
            new Entry(Material.STONE_SWORD, 1, 1, 8, false),
            new Entry(Material.IRON_SWORD, 1, 1, 4, false),
            new Entry(Material.STONE_PICKAXE, 1, 1, 5, false),
            new Entry(Material.IRON_PICKAXE, 1, 1, 3, false),
            new Entry(Material.LEATHER_HELMET, 1, 1, 5, false),
            new Entry(Material.LEATHER_CHESTPLATE, 1, 1, 5, false),
            new Entry(Material.CHAINMAIL_HELMET, 1, 1, 4, false),
            new Entry(Material.CHAINMAIL_BOOTS, 1, 1, 4, false),
            new Entry(Material.IRON_HELMET, 1, 1, 3, false),
            new Entry(Material.IRON_LEGGINGS, 1, 1, 3, false),
            new Entry(Material.ENDER_PEARL, 1, 2, 7, true),
            new Entry(Material.GOLDEN_APPLE, 1, 2, 4, true),
            new Entry(Material.DIAMOND_SWORD, 1, 1, 2, true),
            new Entry(Material.DIAMOND_CHESTPLATE, 1, 1, 1, true),
            new Entry(Material.TNT, 1, 3, 4, true),
            new Entry(Material.FLINT_AND_STEEL, 1, 1, 3, true));

    private LootTable() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static List<ItemStack> roll(boolean middle, int rolls, RandomGenerator random) {
        if (rolls < 0 || random == null) {
            throw new IllegalArgumentException("rolls must be non-negative and random is required");
        }
        List<Entry> candidates = ENTRIES.stream().filter(entry -> middle || !entry.middleOnly()).toList();
        int totalWeight = candidates.stream().mapToInt(Entry::weight).sum();
        List<ItemStack> result = new ArrayList<>(rolls);
        for (int roll = 0; roll < rolls; roll++) {
            int ticket = random.nextInt(totalWeight);
            Entry selected = candidates.getFirst();
            for (Entry entry : candidates) {
                ticket -= entry.weight();
                if (ticket < 0) {
                    selected = entry;
                    break;
                }
            }
            int amount = random.nextInt(selected.minAmount(), selected.maxAmount() + 1);
            ItemStack item = new ItemStack(selected.material(), amount);
            if (selected.material() == Material.BOW && middle && random.nextInt(4) == 0) {
                item.addEnchantment(Enchantment.POWER, 1);
            }
            result.add(item);
        }
        return result;
    }
}
