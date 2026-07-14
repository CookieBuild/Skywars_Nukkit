package com.cookiebuild.skywars.kit;

import java.util.List;
import java.util.Locale;

import org.bukkit.Material;

public enum SkyWarsKit {
    SCOUT("Scout", 0, true,
            "Fast opening pressure, but no bonus armor.",
            List.of(new KitItem(Material.STONE_SWORD, 1), new KitItem(Material.EGG, 4))),
    ARMORER("Armorer", 150, false,
            "Safer armor start, but no bonus weapon or blocks.",
            List.of(new KitItem(Material.CHAINMAIL_CHESTPLATE, 1), new KitItem(Material.IRON_HELMET, 1))),
    BUILDER("Builder", 100, false,
            "More bridge blocks and a pickaxe, but weak direct combat.",
            List.of(new KitItem(Material.OAK_PLANKS, 32), new KitItem(Material.STONE_PICKAXE, 1))),
    HEALER("Healer", 125, false,
            "One recovery opportunity, but no weapon or armor advantage.",
            List.of(new KitItem(Material.GOLDEN_APPLE, 1), new KitItem(Material.COOKED_BEEF, 4)));

    public record KitItem(Material material, int amount) {
        public KitItem {
            if (material == null || amount < 1) {
                throw new IllegalArgumentException("Invalid kit item");
            }
        }
    }

    private final String displayName;
    private final int price;
    private final boolean defaultUnlocked;
    private final String description;
    private final List<KitItem> items;

    SkyWarsKit(String displayName, int price, boolean defaultUnlocked, String description, List<KitItem> items) {
        this.displayName = displayName;
        this.price = price;
        this.defaultUnlocked = defaultUnlocked;
        this.description = description;
        this.items = List.copyOf(items);
    }

    public String displayName() {
        return displayName;
    }

    public int price() {
        return price;
    }

    public boolean defaultUnlocked() {
        return defaultUnlocked;
    }

    public String description() {
        return description;
    }

    public List<KitItem> items() {
        return items;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static SkyWarsKit fromName(String value) {
        if (value == null) {
            return null;
        }
        for (SkyWarsKit kit : values()) {
            if (kit.name().equalsIgnoreCase(value) || kit.displayName.equalsIgnoreCase(value)) {
                return kit;
            }
        }
        return null;
    }
}
