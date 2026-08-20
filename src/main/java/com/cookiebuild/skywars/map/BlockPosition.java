package com.cookiebuild.skywars.map;

import org.bukkit.Location;

/** Exact block coordinate used to bind gameplay metadata to immutable map assets. */
public record BlockPosition(int x, int y, int z) {
    public static BlockPosition of(Location location) {
        return new BlockPosition(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
