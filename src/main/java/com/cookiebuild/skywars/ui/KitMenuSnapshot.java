package com.cookiebuild.skywars.ui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import com.cookiebuild.skywars.kit.SkyWarsKit;

/** Immutable data used by both the Java inventory and Bedrock form. */
public record KitMenuSnapshot(int level, int experience, int nextLevelExperience, int coins,
        List<Entry> entries) {
    public KitMenuSnapshot {
        entries = List.copyOf(entries);
    }

    public static List<Entry> entries(Predicate<SkyWarsKit> isUnlocked, SkyWarsKit selected) {
        return Arrays.stream(SkyWarsKit.values())
                .map(kit -> new Entry(kit, isUnlocked.test(kit), kit == selected))
                .toList();
    }

    public record Entry(SkyWarsKit kit, boolean unlocked, boolean selected) {
        public String action() {
            return unlocked ? "select" : "purchase";
        }
    }
}
