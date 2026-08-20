package com.cookiebuild.skywars.loot;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/** Category-driven loot contract with deterministic guarantees for every chest tier. */
public final class LootTable {
    public enum Family {
        BLOCKS,
        FOOD,
        MELEE,
        ARMOR,
        RANGED,
        UTILITY,
        PREMIUM,
        BONUS
    }

    public record Entry(Material material, int minAmount, int maxAmount, int weight,
            Family family, Set<ChestTier> tiers, boolean premium) {
        public Entry {
            if (material == null || minAmount < 1 || maxAmount < minAmount || weight < 1
                    || family == null || tiers == null || tiers.isEmpty()) {
                throw new IllegalArgumentException("Invalid loot entry");
            }
            tiers = Set.copyOf(tiers);
        }

        public boolean middleOnly() {
            return tiers.equals(Set.of(ChestTier.MID));
        }
    }

    public record Roll(List<ItemStack> items, Set<Family> families, boolean containsPremium) {
        public Roll {
            items = List.copyOf(items);
            families = Set.copyOf(families);
        }
    }

    public record Selection(List<Entry> entries, Set<Family> families, boolean containsPremium) {
        public Selection {
            entries = List.copyOf(entries);
            families = Set.copyOf(families);
        }
    }

    private static final Set<ChestTier> ALL = EnumSet.allOf(ChestTier.class);
    private static final Set<ChestTier> MID = EnumSet.of(ChestTier.MID);

    private static final List<Entry> ENTRIES = List.of(
            entry(Material.OAK_PLANKS, 20, 32, 10, Family.BLOCKS, ALL),
            entry(Material.COBBLESTONE, 20, 32, 8, Family.BLOCKS, ALL),
            entry(Material.COOKED_BEEF, 4, 8, 8, Family.FOOD, ALL),
            entry(Material.BREAD, 5, 10, 6, Family.FOOD, ALL),
            entry(Material.GOLDEN_CARROT, 3, 6, 4, Family.FOOD, MID),
            entry(Material.STONE_SWORD, 1, 1, 10, Family.MELEE, ALL),
            entry(Material.IRON_SWORD, 1, 1, 6, Family.MELEE, MID),
            premium(Material.DIAMOND_SWORD, 1, 1, 3, Family.MELEE),
            entry(Material.LEATHER_CHESTPLATE, 1, 1, 7, Family.ARMOR, ALL),
            entry(Material.CHAINMAIL_HELMET, 1, 1, 6, Family.ARMOR, ALL),
            entry(Material.CHAINMAIL_BOOTS, 1, 1, 6, Family.ARMOR, ALL),
            entry(Material.IRON_HELMET, 1, 1, 5, Family.ARMOR, MID),
            entry(Material.IRON_LEGGINGS, 1, 1, 4, Family.ARMOR, MID),
            premium(Material.DIAMOND_CHESTPLATE, 1, 1, 2, Family.ARMOR),
            entry(Material.EGG, 8, 16, 8, Family.RANGED, ALL),
            entry(Material.SNOWBALL, 8, 16, 8, Family.RANGED, ALL),
            entry(Material.BOW, 1, 1, 5, Family.RANGED, MID),
            entry(Material.FISHING_ROD, 1, 1, 6, Family.UTILITY, ALL),
            entry(Material.STONE_PICKAXE, 1, 1, 5, Family.UTILITY, ALL),
            entry(Material.TNT, 2, 4, 5, Family.UTILITY, MID),
            entry(Material.FLINT_AND_STEEL, 1, 1, 4, Family.UTILITY, MID),
            premium(Material.ENDER_PEARL, 2, 3, 8, Family.PREMIUM),
            premium(Material.GOLDEN_APPLE, 1, 2, 6, Family.PREMIUM),
            premium(Material.DIAMOND_SWORD, 1, 1, 3, Family.PREMIUM),
            premium(Material.DIAMOND_CHESTPLATE, 1, 1, 2, Family.PREMIUM),
            premium(Material.ENDER_PEARL, 1, 2, 7, Family.BONUS),
            premium(Material.GOLDEN_APPLE, 1, 1, 6, Family.BONUS),
            entry(Material.TNT, 2, 4, 5, Family.BONUS, MID),
            entry(Material.BOW, 1, 1, 4, Family.BONUS, MID));

    private LootTable() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Roll roll(ChestTier tier, int rolls, RandomGenerator random) {
        Selection selection = select(tier, rolls, random);
        List<ItemStack> items = new ArrayList<>(selection.entries().size() + 1);
        Set<Material> usedMaterials = new LinkedHashSet<>();
        for (Entry selected : selection.entries()) {
            ItemStack item = new ItemStack(selected.material(), random.nextInt(
                    selected.minAmount(), selected.maxAmount() + 1));
            if (selected.material() == Material.BOW && tier == ChestTier.MID) {
                item.addEnchantment(Enchantment.POWER, 1);
            }
            items.add(item);
            usedMaterials.add(selected.material());
            if (selected.material() == Material.BOW && usedMaterials.add(Material.ARROW)) {
                items.add(new ItemStack(Material.ARROW, tier == ChestTier.MID ? 12 : 8));
            }
        }
        return new Roll(items, selection.families(), selection.containsPremium());
    }

    /** Pure seeded selection used for balance simulations without a running Paper registry. */
    public static Selection select(ChestTier tier, int rolls, RandomGenerator random) {
        if (tier == null || random == null) {
            throw new IllegalArgumentException("tier and random are required");
        }
        List<Family> plan = plan(tier);
        if (rolls < plan.size()) {
            throw new IllegalArgumentException(tier + " chests need at least " + plan.size() + " category rolls");
        }

        List<Entry> selectedEntries = new ArrayList<>(rolls);
        Set<Material> usedMaterials = new LinkedHashSet<>();
        Set<Family> families = EnumSet.noneOf(Family.class);
        boolean premium = false;
        for (int index = 0; index < rolls; index++) {
            Family family = index < plan.size() ? plan.get(index) : Family.BONUS;
            Entry selected = weightedEntry(tier, family, usedMaterials, random);
            if (selected == null) {
                continue;
            }
            selectedEntries.add(selected);
            usedMaterials.add(selected.material());
            families.add(family);
            premium |= selected.premium();
        }
        if (tier == ChestTier.MID && !premium) {
            throw new IllegalStateException("Middle chest generation violated its premium guarantee");
        }
        return new Selection(selectedEntries, families, premium);
    }

    private static List<Family> plan(ChestTier tier) {
        return switch (tier) {
            case ISLAND -> List.of(Family.BLOCKS, Family.MELEE, Family.FOOD, Family.ARMOR, Family.UTILITY);
            // Legacy maps contain between 0 and 29 non-island, non-middle chests. They deliberately
            // share the island contract so a map cannot grant exclusive ranged or iron gear simply
            // because its author placed more transitional chests. Only the eight authored MID chests
            // unlock the stronger pool.
            case INTERMEDIATE -> List.of(Family.BLOCKS, Family.MELEE, Family.FOOD, Family.ARMOR,
                    Family.UTILITY);
            case MID -> List.of(Family.BLOCKS, Family.FOOD, Family.MELEE, Family.ARMOR,
                    Family.RANGED, Family.UTILITY, Family.PREMIUM, Family.BONUS);
        };
    }

    private static Entry weightedEntry(ChestTier tier, Family family, Set<Material> used,
            RandomGenerator random) {
        List<Entry> candidates = ENTRIES.stream()
                .filter(entry -> entry.family() == family && entry.tiers().contains(tier)
                        && !used.contains(entry.material()))
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }
        int ticket = random.nextInt(candidates.stream().mapToInt(Entry::weight).sum());
        for (Entry entry : candidates) {
            ticket -= entry.weight();
            if (ticket < 0) {
                return entry;
            }
        }
        return candidates.getLast();
    }

    private static Entry entry(Material material, int min, int max, int weight,
            Family family, Set<ChestTier> tiers) {
        return new Entry(material, min, max, weight, family, tiers, false);
    }

    private static Entry premium(Material material, int min, int max, int weight, Family family) {
        return new Entry(material, min, max, weight, family, MID, true);
    }
}
